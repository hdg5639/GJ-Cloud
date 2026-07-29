package gj.cloud.ops.application.preview.flow;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Flow id는 표시용 식별자이며 실행 연결은 trigger(pageId/actionId)가 담당한다. 따라서 같은 action 이름을
 * 가진 서로 다른 capability가 한 페이지에 모여도 trigger는 보존한 채 id만 결정적으로 고유화할 수 있다.
 */
public final class FlowBlueprintIds {

    private static final int MAX_ID_LENGTH = 80;

    public static List<FlowBlueprint> ensureUnique(List<FlowBlueprint> flows) {
        if (flows == null || flows.isEmpty()) return List.of();
        Set<String> used = new LinkedHashSet<>();
        List<FlowBlueprint> result = new ArrayList<>();
        for (FlowBlueprint flow : flows) {
            if (flow == null || flow.id() == null || used.add(flow.id())) {
                result.add(flow);
                continue;
            }
            String identity = flow.trigger() == null
                    ? flow.id()
                    : Objects.toString(flow.trigger().pageId(), "")
                    + "::" + Objects.toString(flow.trigger().actionId(), "");
            String suffix = Integer.toUnsignedString(identity.hashCode(), 36);
            String base = truncate(flow.id(), MAX_ID_LENGTH - suffix.length() - 1);
            String candidate = base + "-" + suffix;
            int collision = 2;
            while (!used.add(candidate)) {
                String numberedSuffix = suffix + "-" + collision++;
                candidate = truncate(flow.id(), MAX_ID_LENGTH - numberedSuffix.length() - 1)
                        + "-" + numberedSuffix;
            }
            result.add(new FlowBlueprint(candidate, flow.trigger(), flow.steps()));
        }
        return List.copyOf(result);
    }

    private static String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, Math.max(1, maxLength));
    }

    private FlowBlueprintIds() {
    }
}
