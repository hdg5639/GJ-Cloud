package gj.cloud.ops.application.preview.analysis;

import java.util.Map;
import java.util.stream.Collectors;

// auto-preview-design/09-registry-lifecycle.md §11 MVP 관계 결정 1 — "System Scope의 6개 고정
// Component를 OFFICIAL Entry로 등록". 별도 승인 절차 없이 처음부터 OFFICIAL인 이유: GamjaBox가 직접
// 관리하는 고정 소스코드 구현이라 §3 OFFICIAL 조건(문서·검토 존재, System Test 통과)을 이미 소스코드
// 자체가 만족하기 때문 — 사용 이벤트를 쌓아 VERIFIED를 거칠 대상이 아니다.
public final class ComponentRegistry {

    public static final Map<String, ComponentRegistryEntry> ALL = ComponentContracts.ALL.entrySet().stream()
            .collect(Collectors.toMap(
                    Map.Entry::getKey,
                    e -> new ComponentRegistryEntry(e.getValue(), RegistryStatus.OFFICIAL, RegistryScope.SYSTEM)));

    private ComponentRegistry() {
    }
}
