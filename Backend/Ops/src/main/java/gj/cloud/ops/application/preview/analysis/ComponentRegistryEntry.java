package gj.cloud.ops.application.preview.analysis;

// auto-preview-design/09-registry-lifecycle.md §5 "Status와 Scope는 별개 축" — Contract 자체에
// 운영 상태를 섞지 않고(02-component-contract.md와의 관계 참고) 별도 Entry로 감싼다.
public record ComponentRegistryEntry(
        ComponentContract contract,
        RegistryStatus status,
        RegistryScope scope
) {
}
