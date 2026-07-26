package gj.cloud.ops.application.preview.dto;

import gj.cloud.ops.application.preview.analysis.Block;

import java.util.List;
import java.util.Map;

public record PreviewBlocksResponse(
        Map<String, List<Block>> pageBlocks
) {
}
