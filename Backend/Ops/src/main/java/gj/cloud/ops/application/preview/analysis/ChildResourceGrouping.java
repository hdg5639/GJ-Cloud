package gj.cloud.ops.application.preview.analysis;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// 중첩 자식 리소스(예: "/machines/{machineId}/ports")를 부모 리소스로 묶는 규칙. 페이지 생성기가
// 자식을 별도 페이지로 떼어내면, 자식 오퍼레이션의 부모 경로 파라미터(machineId)를 채울 컨텍스트가
// 없어 런타임 요청이 "{machineId}" 그대로 나가 실패한다(실제로 겪음). 자식 capability를 부모 페이지
// capabilityIds에 함께 담으면 PreviewBlockResolver가 child-resource-list로 조립하고, 런타임이 선택된
// 부모의 id를 자식 요청에 넘겨 준다(AC-6). 두 생성기(PageDraftGenerator/RuleBasedPagePlanGenerator)가
// 같은 규칙을 쓰도록 여기 공용 유틸로 둔다.
public final class ChildResourceGrouping {

    private ChildResourceGrouping() {
    }

    // 자식 resourceName -> 부모 resourceName. 부모가 실제로 그룹에 존재할 때만 매핑한다(부모 리소스가
    // 없으면 자식은 그냥 최상위로 남긴다 — 안전 폴백).
    public static Map<String, String> parentByChild(Map<String, List<Capability>> byResource) {
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<Capability>> entry : byResource.entrySet()) {
            String parent = parentResourceOf(entry.getValue());
            if (parent != null && !parent.equals(entry.getKey()) && byResource.containsKey(parent)) {
                result.put(entry.getKey(), parent);
            }
        }
        return result;
    }

    // 부모 페이지에 담을 capabilityId 목록 — 부모 자신의 capability 뒤에 자식들의 capability를 이어붙인다.
    // (부모 것을 앞에 둬서 flow 생성기의 "첫 CREATE" 선택이 부모 CREATE를 고르게 한다.)
    public static List<String> capabilityIdsWithChildren(
            String parentResource,
            Map<String, List<Capability>> byResource,
            Map<String, String> parentByChild
    ) {
        List<Capability> combined = new ArrayList<>(byResource.getOrDefault(parentResource, List.of()));
        for (Map.Entry<String, String> childEntry : parentByChild.entrySet()) {
            if (parentResource.equals(childEntry.getValue())) {
                combined.addAll(byResource.getOrDefault(childEntry.getKey(), List.of()));
            }
        }
        return combined.stream().map(Capability::id).toList();
    }

    // 이 리소스의 capability 경로 중 하나라도 "/parent/{parentId}/resource" 모양이면 그 parent를 돌려준다.
    private static String parentResourceOf(List<Capability> capabilities) {
        for (Capability capability : capabilities) {
            String parent = parentResourceOfPath(capability.path());
            if (parent != null) {
                return parent;
            }
        }
        return null;
    }

    private static String parentResourceOfPath(String path) {
        if (path == null) {
            return null;
        }
        List<String> segments = new ArrayList<>();
        for (String segment : path.split("/")) {
            if (!segment.isEmpty()) {
                segments.add(segment);
            }
        }
        // 리소스 세그먼트 = 마지막 비-파라미터 세그먼트(경로 끝의 {id}는 건너뛴다).
        int idx = segments.size() - 1;
        if (idx >= 0 && segments.get(idx).startsWith("{")) {
            idx--;
        }
        // "/parent/{parentId}/resource" — resource 바로 앞이 파라미터이고, 그 앞이 비-파라미터면 그게 부모다.
        if (idx >= 2 && segments.get(idx - 1).startsWith("{") && !segments.get(idx - 2).startsWith("{")) {
            return segments.get(idx - 2);
        }
        return null;
    }
}
