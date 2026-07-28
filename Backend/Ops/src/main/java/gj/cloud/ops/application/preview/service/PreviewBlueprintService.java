package gj.cloud.ops.application.preview.service;

import gj.cloud.ops.application.preview.analysis.Block;
import gj.cloud.ops.application.preview.analysis.Capability;
import gj.cloud.ops.application.preview.analysis.PageDraft;
import gj.cloud.ops.application.preview.analysis.PreviewBlockResolver;
import gj.cloud.ops.application.preview.blueprint.BlueprintCompiler;
import gj.cloud.ops.application.preview.blueprint.BlueprintPartSelector;
import gj.cloud.ops.application.preview.dto.PreviewAnalyzeRequest.Purpose;
import gj.cloud.ops.application.preview.planning.model.PagePlan;
import gj.cloud.ops.application.preview.planning.model.PagePlanMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

// 라이브 미리보기와 배포가 동일한 Block 계산 경로를 사용한다. 풍부한 PagePlan이 존재하면
// PagePlanMapper.toDrafts로 RESOURCE_DETAIL 의미까지 보존해 기존 Resolver/Compiler에 연결한다.
@Service
@RequiredArgsConstructor
public class PreviewBlueprintService {

    private final PreviewBlockResolver blockResolver;

    // 기본(파츠 미적용) Block 컴파일 — 배포 검증/스냅샷·실제 산출물이 쓰는 정본 경로. 여기에 파츠 선택을
    // 넣으면 배포가 아직 못 그리는 파츠 id가 CompatibilityValidator에 걸려 배포가 막힌다(실제로 겪음).
    // 파츠 선택은 포털 라이브 프리뷰 전용이라 PreviewController.blocks에서만 selectBlueprintParts로 얹는다.
    public Map<String, List<Block>> compilePageBlocks(List<PageDraft> pages, List<Capability> capabilities,
                                                       Purpose purpose) {
        return BlueprintCompiler.compile(blockResolver.resolveAll(pages, capabilities), purpose);
    }

    // Phase A: 라이브 프리뷰에서만 카테고리 기반 Blueprint 파츠로 componentId를 치환한다(배포는 미적용).
    public Map<String, List<Block>> selectBlueprintParts(Map<String, List<Block>> pageBlocks,
                                                         List<Capability> capabilities, Purpose purpose) {
        return BlueprintPartSelector.select(pageBlocks, capabilities, purpose);
    }

    public Map<String, List<Block>> compilePagePlanBlocks(List<PagePlan> pagePlans, List<Capability> capabilities,
                                                           Purpose purpose) {
        return compilePageBlocks(PagePlanMapper.toDrafts(pagePlans), capabilities, purpose);
    }
}
