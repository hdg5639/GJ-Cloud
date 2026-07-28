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
        // ANALYTICS는 OBSERVABILITY 다음 — "metric"(운영 지표)은 OBSERVABILITY로, 분석/리포트성 명사만 여기.
        NAME_HINTS.put(BlueprintCategory.ANALYTICS, List.of(
                "analytics", "report", "kpi", "insight", "funnel", "cohort", "statistic", "measure"));
        NAME_HINTS.put(BlueprintCategory.INFRASTRUCTURE, List.of(
                "machine", "server", "instance", "node", "vm", "cluster", "container", "deployment", "volume",
                "network", "host", "resource"));
        NAME_HINTS.put(BlueprintCategory.PROJECT, List.of(
                "task", "todo", "ticket", "issue", "project", "sprint", "board", "milestone", "epic", "story"));
        NAME_HINTS.put(BlueprintCategory.CONTENT, List.of(
                "article", "post", "page", "content", "document", "media", "asset", "comment", "review", "story"));
        // ADMIN은 CRM 앞 — "adminUser"처럼 CRM 범용어("user")를 품은 관리 리소스가 ADMIN으로 걸리게 한다.
        NAME_HINTS.put(BlueprintCategory.ADMIN, List.of(
                "admin", "audit", "governance", "tenant", "organization"));
        NAME_HINTS.put(BlueprintCategory.CRM, List.of(
                "customer", "member", "account", "contact", "lead", "user", "person", "people", "subscriber",
                "employee", "team"));
        NAME_HINTS.put(BlueprintCategory.SETTINGS, List.of(
                "setting", "config", "preference", "permission", "role", "policy"));

        // ── Expansion Pack 신규 도메인(component-manifest.json 카테고리와 1:1). 위 기존 카테고리가
        //    선점한 범용어(invoice/payment→COMMERCE, event→OBSERVABILITY, ticket→PROJECT,
        //    employee→CRM, media/asset→CONTENT 등)는 피하고 각 도메인 고유 명사만 둔다. ──
        NAME_HINTS.put(BlueprintCategory.SECURITY, List.of(
                "security", "threat", "vulnerability", "firewall", "malware", "compliance", "cve", "breach",
                "siem", "soc"));
        NAME_HINTS.put(BlueprintCategory.SUPPORT, List.of(
                "support", "helpdesk", "faq", "complaint", "inquiry", "escalation"));
        NAME_HINTS.put(BlueprintCategory.FINANCE, List.of(
                "finance", "transaction", "ledger", "expense", "budget", "tax", "journal", "reconciliation"));
        NAME_HINTS.put(BlueprintCategory.BILLING, List.of(
                "billing", "subscription", "plan", "charge", "refund", "dunning"));
        NAME_HINTS.put(BlueprintCategory.INVENTORY, List.of(
                "inventory", "stock", "warehouse", "supply", "reorder", "lot", "bin"));
        NAME_HINTS.put(BlueprintCategory.LOGISTICS, List.of(
                "logistics", "delivery", "route", "fleet", "carrier", "dispatch", "freight"));
        NAME_HINTS.put(BlueprintCategory.BOOKING, List.of(
                "booking", "reservation", "appointment", "availability", "slot"));
        NAME_HINTS.put(BlueprintCategory.EVENTS, List.of(
                "venue", "session", "attendee", "registration", "agenda", "speaker", "rsvp"));
        NAME_HINTS.put(BlueprintCategory.EDUCATION, List.of(
                "course", "lesson", "student", "enrollment", "curriculum", "quiz", "assignment", "grade",
                "classroom"));
        NAME_HINTS.put(BlueprintCategory.HR, List.of(
                "candidate", "applicant", "recruit", "payroll", "leave", "onboarding", "timesheet", "headcount"));
        NAME_HINTS.put(BlueprintCategory.DEVELOPER, List.of(
                "apikey", "endpoint", "repository", "repo", "webhook", "sdk", "integration", "apiclient",
                "oauthapp"));
        NAME_HINTS.put(BlueprintCategory.AI, List.of(
                "model", "prompt", "dataset", "embedding", "inference", "training", "agent", "finetune",
                "completion"));
        NAME_HINTS.put(BlueprintCategory.IOT, List.of(
                "device", "sensor", "gateway", "telemetry", "firmware", "actuator"));
        NAME_HINTS.put(BlueprintCategory.REAL_ESTATE, List.of(
                "property", "listing", "lease", "rental", "apartment", "realestate", "unit"));
        NAME_HINTS.put(BlueprintCategory.COMMUNITY, List.of(
                "community", "forum", "thread", "discussion", "badge", "reputation", "group"));
        NAME_HINTS.put(BlueprintCategory.MARKETPLACE, List.of(
                "marketplace", "vendor", "seller", "storefront", "offer", "bid"));
        NAME_HINTS.put(BlueprintCategory.MEDIA, List.of(
                "video", "audio", "episode", "playlist", "channel", "broadcast", "podcast"));
        NAME_HINTS.put(BlueprintCategory.TRAVEL, List.of(
                "trip", "flight", "hotel", "itinerary", "destination"));
        NAME_HINTS.put(BlueprintCategory.LEGAL, List.of(
                "contract", "clause", "matter", "litigation", "legalcase", "nda", "casefile"));
        NAME_HINTS.put(BlueprintCategory.KNOWLEDGE, List.of(
                "knowledge", "wiki", "glossary", "handbook", "runbook"));
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
