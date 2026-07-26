package gj.cloud.ops.application.preview.service;

import gj.cloud.ops.application.preview.analysis.Block;
import gj.cloud.ops.application.preview.analysis.Capability;
import gj.cloud.ops.application.preview.analysis.PageDraft;
import gj.cloud.ops.application.preview.analysis.PreviewBlockResolver;
import gj.cloud.ops.application.preview.blueprint.BlueprintCompiler;
import gj.cloud.ops.application.preview.dto.PreviewAnalyzeRequest.Purpose;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

// Direction Recovery Change Request §13.1 — "frontend must not independently decide composition
// rules"를 만족시키려면 라이브 프리뷰(마법사)도 배포 아티팩트(PreviewDeployController)처럼 백엔드가
// 계산한 Block을 그대로 받아써야 한다. resolveAll+compile을 한 곳에 묶어 PreviewController(/blocks
// 엔드포인트)와 PreviewDeployController가 동일하게 재사용한다 — 두 곳이 각자 인라인으로 이 두 줄을
// 반복하다 어긋나는 일을 막는다.
@Service
@RequiredArgsConstructor
public class PreviewBlueprintService {

    private final PreviewBlockResolver blockResolver;

    public Map<String, List<Block>> compilePageBlocks(List<PageDraft> pages, List<Capability> capabilities, Purpose purpose) {
        return BlueprintCompiler.compile(blockResolver.resolveAll(pages, capabilities), purpose);
    }
}
