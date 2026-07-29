package gj.cloud.ops.application.preview.dto;

import gj.cloud.ops.application.preview.analysis.AuthStrategy;
import gj.cloud.ops.application.preview.analysis.Block;
import gj.cloud.ops.application.preview.analysis.Capability;
import gj.cloud.ops.application.preview.analysis.GenerationMode;
import gj.cloud.ops.application.preview.analysis.PageDraft;
import gj.cloud.ops.application.preview.analysis.RegistryStatus;
import gj.cloud.ops.application.preview.binding.ApiBinding;
import gj.cloud.ops.application.preview.flow.FlowBlueprint;
import gj.cloud.ops.application.preview.planning.model.PagePlan;
import gj.cloud.ops.application.preview.scenario.ScenarioModels.CompiledScenario;
import gj.cloud.ops.application.preview.scenario.ScenarioModels.PreviewMode;

import java.util.List;
import java.util.Map;

// 배포 시점에 실제로 검증·렌더링된 Product Blueprint 상태를 읽기 전용으로 저장한다.
public record PreviewBlueprintSnapshot(
        String apiBaseUrl,
        List<Capability> capabilities,
        List<PageDraft> pages,
        AuthStrategy authStrategy,
        Map<String, List<Block>> pageBlocks,
        RegistryStatus status,
        PreviewAnalyzeRequest.Purpose purpose,
        List<PagePlan> pagePlans,
        List<FlowBlueprint> flows,
        List<ApiBinding> bindings,
        GenerationMode generationMode,
        String compilerVersion,
        String registryVersion,
        List<CompiledScenario> scenarios,
        PreviewMode previewMode
) {
    public PreviewBlueprintSnapshot {
        scenarios = scenarios == null ? List.of() : List.copyOf(scenarios);
        previewMode = previewMode == null ? PreviewMode.OPERATION_PREVIEW : previewMode;
    }

    // 기존 테스트/저장 코드 소스 호환용 생성자.
    public PreviewBlueprintSnapshot(
            String apiBaseUrl,
            List<Capability> capabilities,
            List<PageDraft> pages,
            AuthStrategy authStrategy,
            Map<String, List<Block>> pageBlocks,
            RegistryStatus status,
            PreviewAnalyzeRequest.Purpose purpose,
            List<PagePlan> pagePlans,
            List<FlowBlueprint> flows,
            List<ApiBinding> bindings
    ) {
        this(apiBaseUrl, capabilities, pages, authStrategy, pageBlocks, status, purpose, pagePlans, flows, bindings,
                GenerationMode.RULE_BASED, "2", "system-1", List.of(), PreviewMode.OPERATION_PREVIEW);
    }
}
