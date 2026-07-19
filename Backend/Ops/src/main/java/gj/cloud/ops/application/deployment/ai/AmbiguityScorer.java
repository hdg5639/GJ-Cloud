package gj.cloud.ops.application.deployment.ai;

import gj.cloud.ops.application.deployment.dto.GenerateDeploymentSpecRequest;
import gj.cloud.ops.application.deployment.repoanalysis.RepositoryEvidence;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

// AI-Deployment-Pipeline.md 9절 — "서비스 개수 >= 2면 무조건 escalated 모델" 같은 단순 카운트 대신,
// 실제로 모호함을 유발하는 신호들을 점수화해서 라우팅한다. 다만 이 프로젝트가 아직 갖고 있지 않은 신호
// (환경변수로 간접 지정된 포트, 멀티스테이지 Dockerfile 해석 필요성)는 데이터 소스가 없어 채점하지 않음 —
// 있지도 않은 근거로 점수를 지어내지 않는다(문서 자체의 "근거 날조 금지" 원칙과 동일하게 적용).
@Component
public class AmbiguityScorer {

    public int score(List<String> unresolvedContexts, Map<String, RepositoryEvidence> evidenceByContext,
                      GenerateDeploymentSpecRequest request) {
        int score = 0;

        boolean anyConflict = evidenceByContext.values().stream()
                .anyMatch(e -> e != null && e.conflicts() != null && !e.conflicts().isEmpty());
        if (anyConflict) {
            score += 3; // 사용자 입력이 저장소 분석 결과와 충돌
        }

        long distinctRuntimeCandidates = evidenceByContext.values().stream()
                .filter(e -> e != null && e.manifest() != null)
                .map(this::candidateRuntimeSignature)
                .distinct()
                .count();
        if (distinctRuntimeCandidates > 1) {
            score += 2; // 여러 런타임 후보가 동시에 존재
        }

        if (unresolvedContexts.size() > 1) {
            score += 2; // 해결되지 않은 배포 컨텍스트가 여러 개
        }

        boolean anyMultiModule = evidenceByContext.values().stream()
                .anyMatch(e -> e != null && e.manifest() != null && e.manifest().javaBuild() != null
                        && e.manifest().javaBuild().multiModule());
        if (anyMultiModule) {
            score += 2; // 워크스페이스/멀티모듈 구성
        }

        if (request.infrastructure() != null && !request.infrastructure().isEmpty()) {
            score += 1; // 인프라 의존성 존재
        }

        return score;
    }

    private String candidateRuntimeSignature(RepositoryEvidence evidence) {
        if (evidence.manifest().packageJson() != null) return "node";
        if (evidence.manifest().javaBuild() != null) return "java";
        if (evidence.manifest().python() != null) return "python";
        return "none";
    }
}
