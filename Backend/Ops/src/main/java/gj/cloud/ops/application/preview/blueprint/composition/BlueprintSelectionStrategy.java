package gj.cloud.ops.application.preview.blueprint.composition;

import gj.cloud.ops.application.preview.blueprint.composition.BlueprintCompositionModels.BlueprintExclusiveGroup;
import gj.cloud.ops.application.preview.blueprint.composition.BlueprintCompositionModels.BlueprintSelection;

import java.util.List;
import java.util.Map;

public interface BlueprintSelectionStrategy {

    String name();

    List<BlueprintSelection> select(
            List<BlueprintExclusiveGroup> groups,
            Map<String, String> preferredComponentByGroup
    );
}
