package gj.cloud.ops.application.preview.analysis;

import gj.cloud.ops.application.preview.blueprint.BlueprintCategory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// 리소스명을 결정론적으로 BlueprintCategory로 분류한다(07-tag-dictionary.md 방식의 축소판).
// CapabilityExtractor가 status enum을 "선언된 근거"로만 판단하듯, 여기서도 알려진 명사 사전에 걸릴
// 때만 분류하고 근거가 없으면 null을 반환한다(→ BlueprintPartSelector가 기본 컴포넌트로 폴백).
// 이름은 단수/복수·대소문자·구분자를 정규화(소문자 + 영숫자만)한 뒤 토큰 포함으로 매칭한다.
public final class ResourceCategoryClassifier {

    // 카테고리별 리소스명 힌트(정규화된 형태). 순서는 우선순위 — 앞선 카테고리가 먼저 걸린다.
    // 여러 카테고리에 걸릴 수 있는 범용어(user 등)는 가장 특이도 높은 곳에만 둔다.
    private static final Map<BlueprintCategory, List<String>> NAME_HINTS = new LinkedHashMap<>();

    static {
        NAME_HINTS.put(BlueprintCategory.COMMERCE, List.of(
                "order", "product", "cart", "checkout", "invoice", "payment", "sku", "catalog", "shipment", "coupon"));
        NAME_HINTS.put(BlueprintCategory.OBSERVABILITY, List.of(
                "incident", "alert", "event", "log", "metric", "trace", "monitor", "healthcheck", "outage"));
        NAME_HINTS.put(BlueprintCategory.INFRASTRUCTURE, List.of(
                "machine", "server", "instance", "node", "vm", "cluster", "container", "deployment", "volume",
                "network", "host", "resource"));
        NAME_HINTS.put(BlueprintCategory.PROJECT, List.of(
                "task", "todo", "ticket", "issue", "project", "sprint", "board", "milestone", "epic", "story"));
        NAME_HINTS.put(BlueprintCategory.CONTENT, List.of(
                "article", "post", "page", "content", "document", "media", "asset", "comment", "review", "story"));
        NAME_HINTS.put(BlueprintCategory.CRM, List.of(
                "customer", "member", "account", "contact", "lead", "user", "person", "people", "subscriber",
                "employee", "team"));
        NAME_HINTS.put(BlueprintCategory.SETTINGS, List.of(
                "setting", "config", "preference", "permission", "role", "policy"));
    }

    public BlueprintCategory classify(Capability capability) {
        if (capability == null || capability.resourceName() == null) {
            return null;
        }
        String name = normalize(capability.resourceName());
        if (name.isEmpty()) {
            return null;
        }
        for (Map.Entry<BlueprintCategory, List<String>> entry : NAME_HINTS.entrySet()) {
            for (String hint : entry.getValue()) {
                if (name.contains(hint)) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }

    private String normalize(String value) {
        return value.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    private ResourceCategoryClassifier() {
    }

    private static final ResourceCategoryClassifier INSTANCE = new ResourceCategoryClassifier();

    public static ResourceCategoryClassifier getInstance() {
        return INSTANCE;
    }
}
