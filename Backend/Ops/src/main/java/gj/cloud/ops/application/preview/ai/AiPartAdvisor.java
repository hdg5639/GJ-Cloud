package gj.cloud.ops.application.preview.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.models.Reasoning;
import com.openai.models.ReasoningEffort;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.StructuredResponse;
import com.openai.models.responses.StructuredResponseCreateParams;
import com.openai.models.responses.StructuredResponseOutputMessage;
import gj.cloud.ops.application.preview.analysis.Block;
import gj.cloud.ops.application.preview.analysis.Capability;
import gj.cloud.ops.application.preview.blueprint.BlueprintPartRegistry;
import gj.cloud.ops.application.preview.dto.PreviewAnalyzeRequest.Purpose;
import gj.cloud.ops.domain.deployment.enums.AiCallKind;
import gj.cloud.ops.domain.preview.entity.AiPreviewGenerationLogEntity;
import gj.cloud.ops.domain.preview.repository.AiPreviewGenerationLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

// AiPagePlanner와 같은 원칙("AI는 제안, 적용·검증은 결정론") 위에서 동작하는 파츠 추천기. 스왑 가능한
// 각 Block(목록/상세/대시보드)에 대해, 그 Block의 kind/slot과 호환되는 "등록된" 파츠 후보만 모델에 주고
// 서비스 설명에 근거한 최적 componentId 하나 + 한국어 이유를 받는다. 모델 응답은 그대로 신뢰하지 않고,
// componentId가 해당 Block의 허용 후보(파츠 또는 현재 기본 컴포넌트)에 실제로 들어 있을 때만 남긴다.
// 통과분은 partOverrides("pageId/instanceId"→componentId)로 그대로 쓸 수 있어 Phase C 배관을 재사용한다.
@Slf4j
@Component
public class AiPartAdvisor {

    private static final String SYSTEM_PROMPT = """
            You recommend the best Blueprint UI part for each swappable block of a GamjaBox Auto Preview page.
            You receive the user's service description, the generation purpose, the involved capabilities, and a list
            of swappable blocks. Each block has a kind (COLLECTION, DETAIL, or DASHBOARD), the primary resource it
            represents, its current default componentId, and an allowedComponentIds list.

            For every block, choose exactly one componentId strictly from that block's allowedComponentIds list. The
            list already includes the current default component, so if the default is the best fit, return it. Base
            the choice on what the resource and service actually are (for example an incident/alert resource fits an
            incident or alert part, a customer resource fits a customer/directory part). Never invent a componentId,
            never use an id from a different block, and never return an id that is not in that block's allowedComponentIds.

            Give every suggestion a short Korean reason grounded in the service description and resource. Omit a block
            only when no allowed option is meaningfully appropriate. Return an empty suggestions list if nothing helps.
            """;

    private final OpenAIClient client;
    private final String model;
    private final ObjectMapper objectMapper;
    private final AiPreviewGenerationLogRepository logRepository;

    public AiPartAdvisor(
            OpenAIClient client,
            @Value("${ai.model.standard}") String model,
            ObjectMapper objectMapper,
            AiPreviewGenerationLogRepository logRepository
    ) {
        this.client = client;
        this.model = model;
        this.objectMapper = objectMapper;
        this.logRepository = logRepository;
    }

    public PartSuggestionResult suggest(
            String requesterUserId,
            String serviceDescription,
            Purpose purpose,
            List<Capability> capabilities,
            Map<String, List<Block>> pageBlocks
    ) {
        // 스왑 가능한 Block마다 kind/slot 호환 후보를 미리 계산한다(결정론). 후보가 없는 Block은 제안 대상 아님.
        Map<String, Capability> byId = indexCapabilities(capabilities);
        List<SwapDescriptor> descriptors = new ArrayList<>();
        Map<String, SwapDescriptor> byKey = new LinkedHashMap<>();
        if (pageBlocks != null) {
            for (Map.Entry<String, List<Block>> entry : pageBlocks.entrySet()) {
                String pageId = entry.getKey();
                if (entry.getValue() == null) {
                    continue;
                }
                for (Block block : entry.getValue()) {
                    SwapDescriptor descriptor = describe(pageId, block, byId);
                    if (descriptor != null) {
                        descriptors.add(descriptor);
                        byKey.put(descriptor.pageId() + "/" + descriptor.instanceId(), descriptor);
                    }
                }
            }
        }
        // 제안할 대상이 아예 없으면 모델을 호출하지 않는다(호출 로그도 남기지 않음).
        if (descriptors.isEmpty()) {
            return new PartSuggestionResult(List.of(), true);
        }

        long inputTokens = 0;
        long outputTokens = 0;
        boolean succeeded = false;
        try {
            String input = objectMapper.writeValueAsString(
                    new AdvisorInput(serviceDescription, purpose != null ? purpose.name() : null,
                            capabilitySummaries(capabilities), descriptors));
            AiCallResult call = callModel(input);
            inputTokens = call.inputTokens();
            outputTokens = call.outputTokens();

            List<PartSuggestion> validated = new ArrayList<>();
            List<PartSuggestion> raw = call.proposal().suggestions() == null
                    ? List.of() : call.proposal().suggestions();
            for (PartSuggestion suggestion : raw) {
                if (suggestion == null || suggestion.componentId() == null) {
                    continue;
                }
                SwapDescriptor descriptor = byKey.get(suggestion.pageId() + "/" + suggestion.instanceId());
                if (descriptor == null || !descriptor.allowedComponentIds().contains(suggestion.componentId())) {
                    continue; // 모델이 지어냈거나 이 Block과 호환되지 않는 componentId → 버린다.
                }
                validated.add(suggestion);
            }
            succeeded = true;
            return new PartSuggestionResult(validated, true);
        } catch (Exception e) {
            log.warn("Auto Preview AI 파츠 제안 실패: {}", e.getMessage());
            return new PartSuggestionResult(List.of(), false);
        } finally {
            logRepository.save(AiPreviewGenerationLogEntity.create(
                    requesterUserId, AiCallKind.PART_SUGGESTION, model, inputTokens, outputTokens, succeeded));
        }
    }

    // 이 Block이 스왑 대상이면(기본 컴포넌트가 파츠로 대체 가능) 허용 후보 목록과 함께 기술한다. 후보에는
    // kind/slot 호환 등록 파츠 + 현재 기본 컴포넌트 id("기본 유지")를 담는다. 대상이 아니면 null.
    private SwapDescriptor describe(String pageId, Block block, Map<String, Capability> byId) {
        var kind = BlueprintPartRegistry.kindOfBaseComponent(block.componentId());
        if (kind.isEmpty()) {
            return null;
        }
        List<String> allowed = new ArrayList<>();
        allowed.add(block.componentId()); // 현재 기본 = "기본 유지" 선택지
        for (BlueprintPartRegistry.BlueprintPart part : BlueprintPartRegistry.ALL) {
            if (part.kind() == kind.get() && part.acceptedSurfaces().contains(block.slot())
                    && part.supportsMode(block.mode())
                    && !allowed.contains(part.componentId())) {
                allowed.add(part.componentId());
            }
        }
        if (allowed.size() <= 1) {
            return null; // 대체 가능한 등록 파츠가 없으면 제안할 게 없다.
        }
        Capability primary = primaryCapability(block, byId);
        String resourceName = primary != null ? primary.resourceName() : null;
        return new SwapDescriptor(pageId, block.instanceId(), kind.get().name(), resourceName,
                block.componentId(), allowed);
    }

    private Capability primaryCapability(Block block, Map<String, Capability> byId) {
        return block.capabilityIds().stream()
                .filter(Objects::nonNull)
                .map(byId::get)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private Map<String, Capability> indexCapabilities(List<Capability> capabilities) {
        Map<String, Capability> byId = new LinkedHashMap<>();
        if (capabilities != null) {
            for (Capability capability : capabilities) {
                if (capability != null && capability.id() != null) {
                    byId.putIfAbsent(capability.id(), capability);
                }
            }
        }
        return byId;
    }

    private List<CapabilitySummary> capabilitySummaries(List<Capability> capabilities) {
        if (capabilities == null) {
            return List.of();
        }
        return capabilities.stream()
                .map(c -> new CapabilitySummary(c.id(), c.resourceName(),
                        c.type() != null ? c.type().name() : null))
                .toList();
    }

    private AiCallResult callModel(String userPrompt) {
        StructuredResponseCreateParams<PartSuggestionProposal> params = ResponseCreateParams.builder()
                .model(model)
                .reasoning(Reasoning.builder().effort(ReasoningEffort.LOW).build())
                .instructions(SYSTEM_PROMPT)
                .input(userPrompt)
                .text(PartSuggestionProposal.class)
                .build();
        StructuredResponse<PartSuggestionProposal> response = client.responses().create(params);
        PartSuggestionProposal output = response.output().stream()
                .filter(item -> item.message().isPresent())
                .flatMap(item -> item.message().get().content().stream())
                .filter(StructuredResponseOutputMessage.Content::isOutputText)
                .map(StructuredResponseOutputMessage.Content::asOutputText)
                .findFirst()
                .orElse(new PartSuggestionProposal(List.of()));
        long inputTokens = response.usage().map(usage -> usage.inputTokens()).orElse(0L);
        long outputTokens = response.usage().map(usage -> usage.outputTokens()).orElse(0L);
        return new AiCallResult(output, inputTokens, outputTokens);
    }

    private record SwapDescriptor(String pageId, String instanceId, String kind, String resourceName,
                                  String currentComponentId, List<String> allowedComponentIds) {
    }

    private record CapabilitySummary(String id, String resourceName, String type) {
    }

    private record AdvisorInput(String serviceDescription, String purpose,
                                List<CapabilitySummary> capabilities, List<SwapDescriptor> swappableBlocks) {
    }

    private record AiCallResult(PartSuggestionProposal proposal, long inputTokens, long outputTokens) {
    }
}
