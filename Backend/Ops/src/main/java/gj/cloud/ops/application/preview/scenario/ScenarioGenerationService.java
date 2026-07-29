package gj.cloud.ops.application.preview.scenario;

import gj.cloud.ops.application.preview.analysis.Capability;
import gj.cloud.ops.application.preview.analysis.OpenApiEvidence;
import gj.cloud.ops.application.preview.dto.PreviewAnalyzeRequest.Purpose;
import gj.cloud.ops.application.preview.scenario.ScenarioModels.CompilationStatus;
import gj.cloud.ops.application.preview.scenario.ScenarioModels.PreviewMode;
import gj.cloud.ops.application.preview.scenario.ScenarioModels.ScenarioDiagnostic;
import gj.cloud.ops.application.preview.scenario.ScenarioModels.ScenarioGenerationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class ScenarioGenerationService {

    private final RuleBasedScenarioPlanner planner;
    private final ScenarioCompiler compiler;

    public ScenarioGenerationService(RuleBasedScenarioPlanner planner, ScenarioCompiler compiler) {
        this.planner = planner;
        this.compiler = compiler;
    }

    public ScenarioGenerationResult generate(
            OpenApiEvidence evidence,
            String serviceDescription,
            Purpose purpose,
            List<Capability> capabilities,
            PreviewMode requestedMode
    ) {
        long startedAt = System.nanoTime();
        RuleBasedScenarioPlanner.PlanningResult planning =
                planner.plan(evidence, serviceDescription, purpose, capabilities);
        ScenarioCompiler.CompilationResult compilation = compiler.compile(planning.plans(), capabilities);
        List<ScenarioDiagnostic> diagnostics = new ArrayList<>(compilation.diagnostics());
        planning.errors().forEach(error -> diagnostics.add(new ScenarioDiagnostic(
                null, null, ScenarioModels.DiagnosticStatus.UNSUPPORTED, error,
                ScenarioModels.ResolutionStrategy.MARK_AS_UNSUPPORTED, null)));

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
        return new ScenarioGenerationResult(planning.understanding(), planning.plans(),
                compilation.scenarios(), diagnostics, mode);
    }
}
