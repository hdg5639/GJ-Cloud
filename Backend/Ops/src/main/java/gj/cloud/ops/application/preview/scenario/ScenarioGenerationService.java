package gj.cloud.ops.application.preview.scenario;

import gj.cloud.ops.application.preview.analysis.Capability;
import gj.cloud.ops.application.preview.analysis.OpenApiEvidence;
import gj.cloud.ops.application.preview.dto.PreviewAnalyzeRequest.Purpose;
import gj.cloud.ops.application.preview.scenario.ScenarioModels.CompilationStatus;
import gj.cloud.ops.application.preview.scenario.ScenarioModels.DiagnosticStatus;
import gj.cloud.ops.application.preview.scenario.ScenarioModels.PlanningSource;
import gj.cloud.ops.application.preview.scenario.ScenarioModels.PreviewMode;
import gj.cloud.ops.application.preview.scenario.ScenarioModels.ResolutionStrategy;
import gj.cloud.ops.application.preview.scenario.ScenarioModels.ScenarioDiagnostic;
import gj.cloud.ops.application.preview.scenario.ScenarioModels.ScenarioGenerationResult;
import gj.cloud.ops.application.preview.scenario.ScenarioModels.ScenarioPlan;
import gj.cloud.ops.application.preview.scenario.ScenarioModels.ServiceUnderstanding;
import gj.cloud.ops.application.preview.scenario.ai.AiScenarioPlanner;
import gj.cloud.ops.application.preview.scenario.ai.AiScenarioPlanner.PlanningAttempt;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class ScenarioGenerationService {

    private final RuleBasedScenarioPlanner planner;
    private final ScenarioCompiler compiler;
    private final AiScenarioPlanner aiScenarioPlanner;

    public ScenarioGenerationService(
            RuleBasedScenarioPlanner planner,
            ScenarioCompiler compiler,
            AiScenarioPlanner aiScenarioPlanner
    ) {
        this.planner = planner;
        this.compiler = compiler;
        this.aiScenarioPlanner = aiScenarioPlanner;
    }

    public ScenarioGenerationResult generate(
            String requesterUserId,
            OpenApiEvidence evidence,
            String serviceDescription,
            Purpose purpose,
            List<Capability> capabilities,
            PreviewMode requestedMode
    ) {
        long startedAt = System.nanoTime();
        RuleBasedScenarioPlanner.PlanningResult planning =
                planner.plan(evidence, serviceDescription, purpose, capabilities);
        ScenarioCompiler.CompilationResult ruleCompilation = compiler.compile(planning.plans(), capabilities);
        ScenarioCompiler.CompilationResult compilation = ruleCompilation;
        ServiceUnderstanding understanding = planning.understanding();
        List<ScenarioPlan> selectedPlans = planning.plans();
        PlanningSource planningSource = requestedMode == PreviewMode.OPERATION_PREVIEW
                ? PlanningSource.OPERATION_ONLY : PlanningSource.RULE_BASED;
        String promptVersion = null;
        List<ScenarioDiagnostic> diagnostics = new ArrayList<>();
        planning.errors().forEach(error -> diagnostics.add(new ScenarioDiagnostic(
                null, null, DiagnosticStatus.UNSUPPORTED, error,
                ResolutionStrategy.MARK_AS_UNSUPPORTED, null)));

        if (requestedMode != PreviewMode.OPERATION_PREVIEW) {
            PlanningAttempt attempt = aiScenarioPlanner.plan(
                    requesterUserId, evidence, serviceDescription, purpose, capabilities);
            promptVersion = attempt.promptVersion();
            attempt.proposal().errors().forEach(error -> diagnostics.add(new ScenarioDiagnostic(
                    null, null, DiagnosticStatus.PARTIALLY_SUPPORTED,
                    error, ResolutionStrategy.MARK_AS_UNSUPPORTED, null)));
            boolean confident = attempt.proposal().understanding() != null
                    && attempt.proposal().understanding().confidence() >= 0.55
                    && attempt.proposal().plans().stream().anyMatch(plan -> plan.confidence() >= 0.55);
            if (attempt.succeeded() && confident) {
                ScenarioCompiler.CompilationResult aiCompilation =
                        compiler.compile(attempt.proposal().plans(), capabilities);
                boolean hasExecutable = aiCompilation.scenarios().stream()
                        .anyMatch(scenario -> scenario.status() == CompilationStatus.EXECUTABLE);
                if (hasExecutable) {
                    understanding = attempt.proposal().understanding();
                    selectedPlans = attempt.proposal().plans();
                    compilation = aiCompilation;
                    planningSource = PlanningSource.LLM;
                } else {
                    diagnostics.addAll(aiCompilation.diagnostics());
                    diagnostics.add(new ScenarioDiagnostic(
                            null, null, DiagnosticStatus.PARTIALLY_SUPPORTED,
                            "AI 시나리오가 현재 OpenAPI에 대해 실행 불가능하여 규칙 기반 시나리오로 대체했습니다.",
                            ResolutionStrategy.DOWNGRADE_TO_READ_ONLY, null));
                }
            } else if (attempt.succeeded()) {
                diagnostics.add(new ScenarioDiagnostic(
                        null, null, DiagnosticStatus.PARTIALLY_SUPPORTED,
                        "AI 서비스 이해 신뢰도가 낮아 규칙 기반 시나리오를 사용했습니다.",
                        ResolutionStrategy.REQUEST_MANUAL_BINDING, null));
            }
        }
        diagnostics.addAll(compilation.diagnostics());

        long executable = compilation.scenarios().stream()
                .filter(scenario -> scenario.status() == CompilationStatus.EXECUTABLE).count();
        long partial = compilation.scenarios().stream()
                .filter(scenario -> scenario.status() == CompilationStatus.PARTIALLY_SUPPORTED).count();
        PreviewMode mode;
        if (requestedMode == PreviewMode.OPERATION_PREVIEW) {
            mode = PreviewMode.OPERATION_PREVIEW;
        } else if (executable > 0) {
            mode = PreviewMode.SCENARIO_PREVIEW;
        } else if (partial > 0) {
            mode = PreviewMode.INFERRED_SCENARIO_PREVIEW;
        } else {
            mode = PreviewMode.OPERATION_PREVIEW;
        }
        long unsupported = compilation.scenarios().stream()
                .filter(scenario -> scenario.status() == CompilationStatus.UNSUPPORTED).count();
        long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
        log.info("EVENT scenario.compile.succeeded mode={} planned={} executable={} partial={} unsupported={} "
                        + "diagnostics={} durationMs={}",
                mode, planning.plans().size(), executable, partial, unsupported, diagnostics.size(), durationMs);
        return new ScenarioGenerationResult(understanding, selectedPlans,
                compilation.scenarios(), diagnostics, mode, planningSource, promptVersion);
    }
}
