package gj.cloud.ops.api.controller;

import gj.cloud.ops.application.preview.dto.PreviewAnalysisResult;
import gj.cloud.ops.application.preview.dto.PreviewAnalyzeRequest;
import gj.cloud.ops.application.preview.service.PreviewAnalysisService;
import gj.cloud.ops.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Auto Preview (GamjaBox_2.0_Key_Features.md 1단계) Phase A — OpenAPI 문서를 결정론적으로 분석해
// capability/페이지 초안만 반환한다. 특정 VM에 종속된 동작이 아니라 로그인한 사용자면 누구나 호출
// 가능하고(SecurityConfig의 anyRequest().authenticated()로 이미 보호됨), 이 단계는 배포를 전혀 수행하지 않는다.
@Tag(name = "Preview", description = "Auto Preview — OpenAPI 기반 테스트 프론트 자동 생성")
@RestController
@RequestMapping("/ops/preview")
@RequiredArgsConstructor
public class PreviewController {

    private final PreviewAnalysisService previewAnalysisService;

    @Operation(summary = "OpenAPI 문서 분석", description = "OpenAPI 3.x 문서를 결정론적으로 분석해 capability와 페이지 초안을 반환합니다. 배포는 수행하지 않습니다.")
    @PostMapping("/analyze")
    public ApiResponse<PreviewAnalysisResult> analyze(@Valid @RequestBody PreviewAnalyzeRequest request) {
        return ApiResponse.ok(previewAnalysisService.analyze(request));
    }
}
