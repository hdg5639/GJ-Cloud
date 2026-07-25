package gj.cloud.ops.application.preview.analysis;

import gj.cloud.ops.application.deployment.repoanalysis.RepositoryEvidence;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

// GamjaBox_2.0_Key_Features.md 5·8절 규칙 — RuleBasedSpecInferrer와 같은 역할을 OpenAPI 오퍼레이션에
// 대해 수행. AI를 부르지 않고 경로/메서드/쿼리 파라미터 패턴만으로 확정 가능한 capability를 뽑는다.
@Component
public class CapabilityExtractor {

    private static final Set<String> SEARCH_PARAM_NAMES = Set.of("search", "keyword", "q", "query");
    private static final Set<String> SORT_PARAM_NAMES = Set.of("sort", "orderby", "order");
    private static final Set<String> PAGINATION_PARAM_NAMES =
            Set.of("page", "size", "limit", "offset", "pagesize", "perpage");
    private static final Set<String> LOGIN_TEXT_HINTS = Set.of("login", "signin", "sign-in", "authenticate");
    private static final Set<String> USERNAME_FIELD_HINTS =
            Set.of("email", "username", "userid", "loginid", "login_id", "user");
    private static final Set<String> PASSWORD_FIELD_HINTS = Set.of("password", "pw", "pwd");
    // 우선순위 순서 — accessToken처럼 구체적인 이름을 token 같은 범용 이름보다 먼저 확인한다.
    // 밑줄/대소문자 차이는 leafFieldName()에서 정규화 후 비교하므로 여기엔 정규화된 형태만 둔다.
    private static final List<String> ACCESS_TOKEN_FIELD_PRIORITY =
            List.of("accesstoken", "authtoken", "idtoken", "jwt", "token");
    // OpenApiNormalizer.ARRAY_ENVELOPE_KEYS와 같은 우선순위(순서 있는 리스트가 필요해 별도로 둠) —
    // collectionPath 후보 중 알려진 봉투 이름을 먼저 확인한다.
    private static final List<String> ARRAY_ENVELOPE_KEY_PRIORITY =
            List.of("content", "items", "data", "list", "results", "records", "rows", "elements", "result", "payload");
    private static final List<String> COUNT_FIELD_PRIORITY =
            List.of("totalelements", "totalcount", "total", "count");

    public List<Capability> extract(OpenApiEvidence evidence) {
        List<Capability> capabilities = new ArrayList<>();
        Optional<Capability> login = extractLoginCapability(evidence);
        login.ifPresent(capabilities::add);

        for (ApiOperationEvidence operation : evidence.operations()) {
            // 로그인으로 이미 분류된 오퍼레이션은 일반 CRUD 규칙("POST /auth/login" → 생성)으로
            // 또 잡히지 않게 제외한다 — 같은 엔드포인트가 두 capability로 중복되면 안 됨.
            if (login.isPresent() && isSameOperation(operation, login.get())) {
                continue;
            }
            extractCrudCapability(operation).ifPresent(capabilities::add);
        }
        return capabilities;
    }

    private boolean isSameOperation(ApiOperationEvidence operation, Capability capability) {
        return operation.method().equals(capability.method()) && operation.path().equals(capability.path());
    }

    private Optional<Capability> extractCrudCapability(ApiOperationEvidence operation) {
        String resourceName = resourceNameOf(operation.path());
        if (resourceName == null) {
            return Optional.empty();
        }
        boolean lastSegmentIsParam = lastSegmentIsParam(operation.path());

        CapabilityType type = switch (operation.method()) {
            case "GET" -> lastSegmentIsParam ? CapabilityType.DETAIL
                    : (operation.responseIsArray() ? CapabilityType.LIST : null);
            case "POST" -> lastSegmentIsParam ? null : CapabilityType.CREATE;
            case "PUT", "PATCH" -> lastSegmentIsParam ? CapabilityType.UPDATE : null;
            case "DELETE" -> lastSegmentIsParam ? CapabilityType.DELETE : null;
            default -> null;
        };
        if (type == null) {
            return Optional.empty();
        }

        List<String> evidenceLines = new ArrayList<>();
        evidenceLines.add(operation.method() + " " + operation.path()
                + (operation.operationId() != null ? " (operationId=" + operation.operationId() + ")" : ""));

        boolean hasSearch = false;
        boolean hasSort = false;
        boolean hasPagination = false;
        // 실제 쿼리 파라미터 이름을 보존한다 — "search"로 하드코딩해 보내면 keyword/q/query를 쓰는
        // API에서는 검색이 조용히 안 먹는다(렌더러가 이 이름을 그대로 요청에 실어야 함).
        String searchParam = null;
        if (type == CapabilityType.LIST) {
            for (ApiParameterEvidence param : operation.parameters()) {
                if (!"query".equalsIgnoreCase(param.in())) {
                    continue;
                }
                String name = param.name().toLowerCase();
                if (SEARCH_PARAM_NAMES.contains(name)) {
                    hasSearch = true;
                    if (searchParam == null) {
                        searchParam = param.name();
                    }
                }
                if (SORT_PARAM_NAMES.contains(name)) {
                    hasSort = true;
                }
                if (PAGINATION_PARAM_NAMES.contains(name)) {
                    hasPagination = true;
                }
            }
        }

        // LIST 응답에서 실제 배열/총개수 위치를 미리 추정해둔다 — 안 해두면 렌더러가 매 요청마다 런타임
        // 재귀 휴리스틱으로 다시 찾아야 하고, 응답에 배열 필드가 여러 개면 엉뚱한 걸 고를 수도 있다.
        String collectionPath = null;
        String totalCountPath = null;
        if (type == CapabilityType.LIST) {
            collectionPath = detectCollectionPath(operation.arrayFieldPaths());
            totalCountPath = detectTotalCountPath(operation.responseFieldPaths());
            if (collectionPath != null) {
                evidenceLines.add("응답 필드에서 목록 위치를 추정함: " + collectionPath);
            }
            if (totalCountPath != null) {
                evidenceLines.add("응답 필드에서 총 개수 위치를 추정함: " + totalCountPath);
            }
        }

        String confidence = operation.operationId() != null
                ? RepositoryEvidence.CONFIDENCE_HIGH
                : RepositoryEvidence.CONFIDENCE_MEDIUM;
        if (operation.operationId() == null) {
            evidenceLines.add("operationId가 없어 경로/메서드 패턴만으로 추정함");
        }

        List<String> fields = (type == CapabilityType.CREATE || type == CapabilityType.UPDATE)
                ? operation.requestBodyFields()
                : List.of();

        RiskLevel risk = defaultRiskFor(type);
        return Optional.of(new Capability(
                Capability.idOf(resourceName, type), resourceName, type,
                operation.operationId(), operation.path(), operation.method(),
                hasSearch, hasSort, hasPagination, confidence, evidenceLines, fields, null, searchParam,
                risk, defaultAutomationPolicyFor(risk), collectionPath, totalCountPath));
    }

    // auto-preview-design/05-capability-taxonomy.md §5 표의 축소판. IRREVERSIBLE·EXTERNAL_SIDE_EFFECT는
    // 여기서 절대 배정하지 않는다 — OpenAPI 메서드/경로만으로는 "복구 가능한 삭제"인지 "영구 삭제"인지
    // 구분할 근거가 없고, 근거 없이 확정하지 않는 게 이 분석기 전체의 원칙(Phase A 철학)이다.
    private RiskLevel defaultRiskFor(CapabilityType type) {
        return switch (type) {
            case LIST, DETAIL, LOGIN -> RiskLevel.SAFE;
            case CREATE, UPDATE -> RiskLevel.STATE_CHANGING;
            case DELETE -> RiskLevel.DESTRUCTIVE;
        };
    }

    // 05-capability-taxonomy.md §6 기본 매핑 표 그대로. 지금은 저장만 하고 실제로 실행을 막는 곳은
    // 없다 — MVP에 자동 실행 파이프라인 자체가 없기 때문(사용자가 항상 버튼을 직접 누름).
    private AutomationPolicy defaultAutomationPolicyFor(RiskLevel risk) {
        return switch (risk) {
            case SAFE -> AutomationPolicy.AUTO_SAFE;
            case STATE_CHANGING -> AutomationPolicy.USER_INITIATED;
            case DESTRUCTIVE -> AutomationPolicy.EXPLICIT_CONFIRMATION;
            case IRREVERSIBLE -> AutomationPolicy.TYPED_CONFIRMATION;
            case EXTERNAL_SIDE_EFFECT -> AutomationPolicy.DISABLED_IN_AUTO_TEST;
        };
    }

    // 문서 전체에서 로그인 오퍼레이션 후보를 찾는다 — 텍스트 힌트(경로/operationId/태그)와 요청 필드
    // (username-like + password-like)를 함께 확인해 신뢰도를 나눈다.
    private Optional<Capability> extractLoginCapability(OpenApiEvidence evidence) {
        Capability best = null;
        for (ApiOperationEvidence operation : evidence.operations()) {
            if (!"POST".equals(operation.method())) {
                continue;
            }
            boolean textHint = containsLoginHint(operation.path())
                    || containsLoginHint(operation.operationId())
                    || containsLoginHint(operation.summary())
                    || operation.tags().stream().anyMatch(this::containsLoginHint);
            boolean hasUsernameField = operation.requestBodyFields().stream()
                    .anyMatch(field -> matchesAny(field, USERNAME_FIELD_HINTS));
            boolean hasPasswordField = operation.requestBodyFields().stream()
                    .anyMatch(field -> matchesAny(field, PASSWORD_FIELD_HINTS));

            if (!textHint && !(hasUsernameField && hasPasswordField)) {
                continue;
            }

            List<String> evidenceLines = new ArrayList<>();
            evidenceLines.add("POST " + operation.path()
                    + (operation.operationId() != null ? " (operationId=" + operation.operationId() + ")" : ""));
            String confidence;
            if (hasUsernameField && hasPasswordField) {
                confidence = RepositoryEvidence.CONFIDENCE_HIGH;
                evidenceLines.add("요청 필드에서 아이디/비밀번호로 보이는 필드를 모두 확인함");
            } else if (textHint) {
                confidence = RepositoryEvidence.CONFIDENCE_LOW;
                evidenceLines.add("경로/operationId/태그에 로그인 관련 이름만 있고 요청 필드로는 확인하지 못함");
            } else {
                continue;
            }

            String accessTokenPath = detectAccessTokenPath(operation.responseFieldPaths());
            if (accessTokenPath != null) {
                evidenceLines.add("응답 필드에서 access token 위치를 추정함: " + accessTokenPath);
            }
            Capability candidate = new Capability(
                    "auth.login", "auth", CapabilityType.LOGIN,
                    operation.operationId(), operation.path(), operation.method(),
                    false, false, false, confidence, evidenceLines, operation.requestBodyFields(),
                    accessTokenPath, null, RiskLevel.SAFE, AutomationPolicy.AUTO_SAFE, null, null);
            if (best == null || isHigherConfidence(candidate.confidence(), best.confidence())) {
                best = candidate;
            }
        }
        return Optional.ofNullable(best);
    }

    private boolean isHigherConfidence(String candidate, String current) {
        return rank(candidate) > rank(current);
    }

    private int rank(String confidence) {
        return switch (confidence) {
            case RepositoryEvidence.CONFIDENCE_HIGH -> 2;
            case RepositoryEvidence.CONFIDENCE_MEDIUM -> 1;
            default -> 0;
        };
    }

    private boolean containsLoginHint(String text) {
        if (text == null) {
            return false;
        }
        String lower = text.toLowerCase();
        return LOGIN_TEXT_HINTS.stream().anyMatch(lower::contains);
    }

    private boolean matchesAny(String field, Set<String> hints) {
        String lower = field.toLowerCase();
        return hints.stream().anyMatch(lower::contains);
    }

    // 로그인 응답의 스칼라 필드 dot-path들(예: "data.accessToken") 중 이름이 access token처럼 보이는
    // 것을 찾는다. 우선순위 힌트를 순서대로 훑어 더 구체적인 이름을 먼저 확인하고, 같은 우선순위에서는
    // 더 얕은(중첩이 적은) 경로를 택한다. 못 찾으면 null — PreviewAnalysisService가 unresolved로 표시한다.
    private String detectAccessTokenPath(List<String> responseFieldPaths) {
        for (String hint : ACCESS_TOKEN_FIELD_PRIORITY) {
            String best = null;
            for (String path : responseFieldPaths) {
                if (!hint.equals(normalizeFieldName(leafSegment(path)))) {
                    continue;
                }
                if (best == null || pathDepth(path) < pathDepth(best)) {
                    best = path;
                }
            }
            if (best != null) {
                return best;
            }
        }
        return null;
    }

    // LIST 응답의 배열 필드 dot-path들(예: "data.content") 중 목록 봉투로 흔히 쓰이는 이름을 우선
    // 확인한다. 알려진 이름이 하나도 없어도 배열이 정확히 하나뿐이면 그게 목록이라고 확정한다(모호할
    // 근거가 없는 한 유일한 후보를 버릴 이유가 없음). 후보가 여러 개면서 이름도 모르면 null.
    private String detectCollectionPath(List<String> arrayFieldPaths) {
        for (String hint : ARRAY_ENVELOPE_KEY_PRIORITY) {
            String best = null;
            for (String path : arrayFieldPaths) {
                if (!hint.equals(normalizeFieldName(leafSegment(path)))) {
                    continue;
                }
                if (best == null || pathDepth(path) < pathDepth(best)) {
                    best = path;
                }
            }
            if (best != null) {
                return best;
            }
        }
        return arrayFieldPaths.size() == 1 ? arrayFieldPaths.get(0) : null;
    }

    // LIST 응답의 스칼라 필드 dot-path들 중 총 개수처럼 보이는 이름을 찾는다. detectAccessTokenPath와
    // 같은 우선순위 탐색 패턴 — Spring Data Page류는 배열(content)과 같은 깊이에 totalElements가 있는
    // 경우가 많아 responseFieldPaths(스칼라 전체)를 그대로 재사용한다.
    private String detectTotalCountPath(List<String> responseFieldPaths) {
        for (String hint : COUNT_FIELD_PRIORITY) {
            String best = null;
            for (String path : responseFieldPaths) {
                if (!hint.equals(normalizeFieldName(leafSegment(path)))) {
                    continue;
                }
                if (best == null || pathDepth(path) < pathDepth(best)) {
                    best = path;
                }
            }
            if (best != null) {
                return best;
            }
        }
        return null;
    }

    private String leafSegment(String dotPath) {
        int idx = dotPath.lastIndexOf('.');
        return idx >= 0 ? dotPath.substring(idx + 1) : dotPath;
    }

    private String normalizeFieldName(String name) {
        return name.toLowerCase().replace("_", "").replace("-", "");
    }

    private int pathDepth(String dotPath) {
        return (int) dotPath.chars().filter(c -> c == '.').count();
    }

    private boolean lastSegmentIsParam(String path) {
        List<String> segments = nonEmptySegments(path);
        return !segments.isEmpty() && segments.get(segments.size() - 1).startsWith("{");
    }

    // 경로의 마지막 비-파라미터 세그먼트를 리소스명으로 취급한다.
    // "/organizations/{id}/members" -> "members" (하위 리소스), "/organizations/{id}" -> "organizations".
    private String resourceNameOf(String path) {
        List<String> segments = nonEmptySegments(path);
        int idx = segments.size() - 1;
        if (idx >= 0 && segments.get(idx).startsWith("{")) {
            idx--;
        }
        return idx >= 0 ? segments.get(idx) : null;
    }

    private List<String> nonEmptySegments(String path) {
        return Arrays.stream(path.split("/")).filter(s -> !s.isBlank()).toList();
    }
}
