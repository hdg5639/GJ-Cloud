package gj.cloud.ops.application.deployment.spec;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

// build/artifact/run을 분리한 스키마 (AI-Deployment-Pipeline.md 5절) — 기존 평면 runtime 필드는
// "빌드에 쓰인 런타임"과 "최종 실행 런타임"과 "산출물 종류"를 구분하지 못해 정적 사이트 같은 경우를
// 억지로 nodejs 등으로 분류하게 만들었음. buildCommand/startCommand 자유 문자열도 완전히 제거하고
// BuildRunStrategy 허용목록으로만 표현(6절 — AI가 임의 셸 명령을 생성할 수 없도록).
public record ServiceSpec(
        @NotBlank String name,
        @NotNull DeploymentMode deploymentMode,
        @NotNull @Valid BuildSpec build,
        @NotNull @Valid ArtifactSpec artifact,
        @NotNull @Valid RunSpec run,
        @NotBlank String context,
        @Valid ExposeSpec expose
) {
}
