package gj.cloud.ops.application.preview.flow;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// GamjaBox_Auto_Preview_Workflow_Composition_Phase2_Change_Request.md §6/§17 — "Arbitrary JavaScript
// expressions must not be supported. Use a restricted expression language". 화이트리스트 정규식
// 하나로 스코프(form/route/context/steps/currentUser/row) + 점경로 세그먼트만 허용한다 — 함수 호출·연산자·
// 따옴표·괄호 등 임의 JS 문법은 패턴 자체에 없어 구조적으로 통과할 수 없다. 세그먼트가 실제로 가리키는
// step/response 필드가 존재하는지 같은 의미 검증은 API 체이닝(§8, WP-3)의 몫 — 여기서는 문법만 본다.
public record FlowExpression(String raw, Scope scope, List<String> path) {

    public enum Scope {
        FORM,
        ROUTE,
        CONTEXT,
        STEPS,
        CURRENT_USER,
        ROW
    }

    private static final Pattern PATTERN =
            Pattern.compile("^\\$(form|route|context|steps|currentUser|row)((?:\\.[A-Za-z0-9_-]+)*)$");

    public static Optional<FlowExpression> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        Matcher matcher = PATTERN.matcher(raw);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        Scope scope = toScope(matcher.group(1));
        String rest = matcher.group(2);
        List<String> path = rest.isEmpty() ? List.of() : List.of(rest.substring(1).split("\\."));
        return Optional.of(new FlowExpression(raw, scope, path));
    }

    // "$"로 시작하는 값만 표현식으로 취급한다 — 그 외 문자열(메시지 텍스트 등)은 리터럴로 그대로 둔다.
    public static boolean isExpressionLike(String value) {
        return value != null && value.startsWith("$");
    }

    private static Scope toScope(String token) {
        return switch (token) {
            case "form" -> Scope.FORM;
            case "route" -> Scope.ROUTE;
            case "context" -> Scope.CONTEXT;
            case "steps" -> Scope.STEPS;
            case "currentUser" -> Scope.CURRENT_USER;
            case "row" -> Scope.ROW;
            default -> throw new IllegalStateException("PATTERN이 통과시켰는데 알 수 없는 스코프: " + token);
        };
    }
}
