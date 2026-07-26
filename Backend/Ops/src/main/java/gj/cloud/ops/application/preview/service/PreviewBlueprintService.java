package gj.cloud.ops.application.preview.service;

import gj.cloud.ops.application.preview.analysis.Block;
import gj.cloud.ops.application.preview.analysis.Capability;
import gj.cloud.ops.application.preview.analysis.PageDraft;
import gj.cloud.ops.application.preview.analysis.PreviewBlockResolver;
import gj.cloud.ops.application.preview.blueprint.BlueprintCompiler;
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

    public Map<String, List<Block>> compilePageBlocks(List<PageDraft> pages, List<Capability> capabilities,
                                                       Purpose purpose) {
        return BlueprintCompiler.compile(blockResolver.resolveAll(pages, capabilities), purpose);
    }

    public Map<String, List<Block>> compilePagePlanBlocks(List<PagePlan> pagePlans, List<Capability> capabilities,
                                                           Purpose purpose) {
        return compilePageBlocks(PagePlanMapper.toDrafts(pagePlans), capabilities, purpose);
    }
}
