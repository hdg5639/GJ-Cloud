package gj.cloud.ops.application.deployment.repoanalysis;

import gj.cloud.ops.application.deployment.dto.ServiceCard;
import gj.cloud.ops.application.deployment.spec.ArtifactType;
import gj.cloud.ops.application.deployment.spec.BuildRunStrategy;
import gj.cloud.ops.application.deployment.spec.DeploymentMode;
import gj.cloud.ops.application.deployment.spec.RuntimeKind;
import gj.cloud.ops.application.deployment.spec.ServiceSpec;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// AI-Deployment-Pipeline.md 2절/18절 — 신고된 핵심 버그(순수 정적 사이트가 nodejs로 오분류되던 문제)에
// 대한 회귀 테스트. "index.html은 있는데 백엔드 매니페스트가 전혀 없음" 케이스는 반드시 AI 호출 없이
// (RuleBasedSpecInferrer 단독으로) STATIC으로 확정돼야 하고, 포트/헬스체크를 지어내면 안 된다.
class RuleBasedSpecInferrerTest {

    private final RuleBasedSpecInferrer inferrer = new RuleBasedSpecInferrer();

    @Test
    void plainStaticSiteResolvesWithoutAiAndNeverInventsNodejs() {
        DetectedFiles files = new DetectedFiles(false, false, false, false, false, false, false, false, true);
        RepositoryEvidence evidence = new RepositoryEvidence(".", files, new ManifestData(null, null, null),
                RepositoryEvidence.TYPE_UNKNOWN, RepositoryEvidence.CONFIDENCE_LOW, List.of(), List.of(), List.of(), "hash");
        ServiceCard card = new ServiceCard("web", "docker", ".", 3000, null, null, null, null, null, null, null, true, "portfolio");

        RuleBasedSpecInferrer.RuleBasedInferenceResult result = inferrer.infer(evidence, card);

        assertThat(result.resolved()).isTrue();
        ServiceSpec spec = result.spec();
        assertThat(spec.deploymentMode()).isEqualTo(DeploymentMode.ARTIFACT_ONLY);
        assertThat(spec.build().runtime()).isNotEqualTo(RuntimeKind.NODEJS);
        assertThat(spec.build().runtime()).isEqualTo(RuntimeKind.NONE);
        assertThat(spec.build().strategy()).isEqualTo(BuildRunStrategy.NONE);
        assertThat(spec.artifact().type()).isEqualTo(ArtifactType.STATIC_DIRECTORY);
        assertThat(spec.run().runtime()).isEqualTo(RuntimeKind.STATIC_SERVER);
        assertThat(spec.run().strategy()).isEqualTo(BuildRunStrategy.STATIC_SERVER);
        // 사용자가 card.expose=true로 요청했으니 노출은 되지만, containerPort는 nginx 표준 80으로 고정되지
        // 3000(원래 신고된 버그가 지어냈던 값) 같은 임의 포트가 아니어야 함
        assertThat(spec.run().containerPort()).isEqualTo(80);
        assertThat(spec.expose()).isNotNull();
        assertThat(spec.expose().enabled()).isTrue();
        assertThat(spec.expose().customSubdomain()).isEqualTo("portfolio");
    }

    @Test
    void plainStaticSiteWithExposeFalseHasNoExposeBlock() {
        DetectedFiles files = new DetectedFiles(false, false, false, false, false, false, false, false, true);
        RepositoryEvidence evidence = new RepositoryEvidence(".", files, new ManifestData(null, null, null),
                RepositoryEvidence.TYPE_UNKNOWN, RepositoryEvidence.CONFIDENCE_LOW, List.of(), List.of(), List.of(), "hash");
        ServiceCard card = new ServiceCard("web", "docker", ".", 3000, null, null, null, null, null, null, null, false, null);

        RuleBasedSpecInferrer.RuleBasedInferenceResult result = inferrer.infer(evidence, card);

        assertThat(result.resolved()).isTrue();
        assertThat(result.spec().expose()).isNull();
    }

    @Test
    void existingDockerfileIsAuthoritativeAndSkipsInvention() {
        DetectedFiles files = new DetectedFiles(true, false, false, false, false, false, false, false, false);
        RepositoryEvidence evidence = new RepositoryEvidence(".", files, new ManifestData(null, null, null),
                RepositoryEvidence.TYPE_UNKNOWN, RepositoryEvidence.CONFIDENCE_LOW, List.of(), List.of(), List.of(), "hash");
        ServiceCard card = new ServiceCard("web", "docker", ".", 8080, null, null, null, null, null, null, null, true, null);

        RuleBasedSpecInferrer.RuleBasedInferenceResult result = inferrer.infer(evidence, card);

        assertThat(result.resolved()).isTrue();
        assertThat(result.spec().build().strategy()).isEqualTo(BuildRunStrategy.DOCKERFILE);
        assertThat(result.detectedType()).isEqualTo(RepositoryEvidence.TYPE_DOCKERFILE);
        assertThat(result.confidence()).isEqualTo(RepositoryEvidence.CONFIDENCE_HIGH);
    }

    @Test
    void packageJsonWithoutStartOrBuildScriptIsUnresolvedNotGuessed() {
        DetectedFiles files = new DetectedFiles(false, false, true, false, false, false, false, false, false);
        PackageJsonInfo packageJsonInfo = new PackageJsonInfo("app", false, "module", Map.of(), java.util.Set.of(), java.util.Set.of(), false, null, Map.of());
        RepositoryEvidence evidence = new RepositoryEvidence(".", files, new ManifestData(packageJsonInfo, null, null),
                RepositoryEvidence.TYPE_UNKNOWN, RepositoryEvidence.CONFIDENCE_LOW, List.of(), List.of(), List.of(), "hash");
        ServiceCard card = new ServiceCard("web", "node", ".", 3000, null, null, null, null, null, null, null, true, null);

        RuleBasedSpecInferrer.RuleBasedInferenceResult result = inferrer.infer(evidence, card);

        assertThat(result.resolved()).isFalse();
        assertThat(result.unresolvedReasons()).isNotEmpty();
    }
}
