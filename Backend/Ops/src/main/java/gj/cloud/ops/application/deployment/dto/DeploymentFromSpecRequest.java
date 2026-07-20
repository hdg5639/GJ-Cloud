package gj.cloud.ops.application.deployment.dto;

import gj.cloud.ops.application.deployment.spec.DeploymentSpec;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

// D-1 기본 템플릿 / D-3 AI 자동생성 공용 — spec만 다르게 만들어지고 이후 흐름은 동일 (F절 7·9단계)
public record DeploymentFromSpecRequest(
        @NotBlank String repoUrl,
        @NotBlank String branch,
        String patToken,
        @NotNull @Valid DeploymentSpec spec,
        // VM 파일시스템 절대경로 — 지정하면 현재 활성 release를 가리키는 심볼릭 링크가 이 경로에 생성됨.
        String installPath
) {
}
