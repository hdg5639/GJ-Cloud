package gj.cloud.ops.application.preview.blueprint.composition;

import gj.cloud.ops.application.preview.blueprint.composition.BlueprintCompositionModels.BlueprintCandidateOption;
import gj.cloud.ops.application.preview.blueprint.composition.BlueprintCompositionModels.BlueprintCompositionFinding;
import gj.cloud.ops.application.preview.blueprint.composition.BlueprintCompositionModels.BlueprintExclusiveGroup;
import gj.cloud.ops.application.preview.blueprint.composition.BlueprintCompositionModels.BlueprintSelection;
import gj.cloud.ops.application.preview.blueprint.composition.BlueprintCompositionModels.FindingSeverity;
import gj.cloud.ops.application.preview.blueprint.composition.BlueprintCompositionModels.SelectionMode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public final class BlueprintCompositionValidator {

    public static List<BlueprintCompositionFinding> validate(
            List<BlueprintExclusiveGroup> groups,
            List<BlueprintSelection> selections
    ) {
        List<BlueprintCompositionFinding> findings = new ArrayList<>();
        List<BlueprintExclusiveGroup> safeGroups = groups == null ? List.of() : groups;
        List<BlueprintSelection> safeSelections = selections == null ? List.of() : selections;
        Map<String, BlueprintExclusiveGroup> groupById = safeGroups.stream().collect(Collectors.toMap(
                BlueprintExclusiveGroup::id, value -> value, (left, right) -> left, LinkedHashMap::new));
        Map<String, List<BlueprintSelection>> selectionsByGroup = safeSelections.stream().collect(
                Collectors.groupingBy(
                        BlueprintSelection::groupId,
                        LinkedHashMap::new,
                        Collectors.toList()));

        for (BlueprintExclusiveGroup group : safeGroups) {
            List<BlueprintSelection> groupSelections =
                    selectionsByGroup.getOrDefault(group.id(), List.of());
            boolean required = group.selectionMode() != SelectionMode.OPTIONAL_ONE;
            if (groupSelections.isEmpty() && required) {
                findings.add(error("MISSING_SELECTION", group.id() + " 그룹의 선택이 없습니다.",
                        List.of(group.id()), List.of()));
                continue;
            }
            boolean allowsMany = group.selectionMode() == SelectionMode.PICK_MANY
                    || group.selectionMode() == SelectionMode.ORDER_MANY;
            if (!allowsMany && groupSelections.size() > 1) {
                findings.add(error("EXCLUSIVE_GROUP_OVERSELECTED",
                        group.id() + " 그룹은 하나의 파츠만 선택할 수 있습니다.",
                        List.of(group.id()), List.of(group.id())));
            }
            long distinctSelections = groupSelections.stream()
                    .map(BlueprintSelection::componentId)
                    .distinct()
                    .count();
            if (distinctSelections != groupSelections.size()) {
                findings.add(error("DUPLICATE_GROUP_SELECTION",
                        group.id() + " 그룹에 같은 파츠가 중복 선택되었습니다.",
                        List.of(group.id()), List.of(group.id())));
            }
            for (BlueprintSelection selection : groupSelections) {
                if (group.candidates().stream()
                        .noneMatch(candidate -> candidate.componentId().equals(selection.componentId()))) {
                    findings.add(error("CANDIDATE_OUT_OF_GROUP",
                            selection.componentId() + "은 " + group.id() + "의 후보가 아닙니다.",
                            List.of(group.id()), List.of(group.id())));
                }
            }
        }
        for (BlueprintSelection selection : safeSelections) {
            if (!groupById.containsKey(selection.groupId())) {
                findings.add(error("UNKNOWN_GROUP", "알 수 없는 선택 그룹: " + selection.groupId(),
                        List.of(selection.groupId()), List.of()));
            }
        }

        List<SelectedOption> selectedOptions = selectedOptions(groupById, safeSelections);
        duplicateComponentFindings(selectedOptions, findings);
        repeatedFamilyFindings(selectedOptions, findings);
        repeatedOverlayPatternFindings(selectedOptions, findings);
        repeatedLayoutPatternFindings(selectedOptions, findings);
        return List.copyOf(findings);
    }

    private static void duplicateComponentFindings(
            List<SelectedOption> selected,
            List<BlueprintCompositionFinding> findings
    ) {
        selected.stream().collect(Collectors.groupingBy(
                value -> value.group().pageId() + "/" + value.option().componentId(),
                LinkedHashMap::new,
                Collectors.toList()
        )).values().stream().filter(values -> values.size() > 1).forEach(values -> {
            List<String> groups = values.stream().map(value -> value.group().id()).toList();
            findings.add(warning("REPEATED_COMPONENT",
                    "같은 페이지에서 " + values.get(0).option().componentId() + " 파츠가 반복됩니다.",
                    groups, groups.subList(1, groups.size())));
        });
    }

    private static void repeatedFamilyFindings(
            List<SelectedOption> selected,
            List<BlueprintCompositionFinding> findings
    ) {
        selected.stream()
                .filter(value -> !value.option().baseComponent())
                .collect(Collectors.groupingBy(
                        value -> value.group().pageId() + "/" + value.option().family(),
                        LinkedHashMap::new,
                        Collectors.toList()
                )).values().stream().filter(values -> values.size() > 2).forEach(values -> {
                    List<String> groups = values.stream().map(value -> value.group().id()).toList();
                    findings.add(warning("REPEATED_FAMILY",
                            "같은 페이지에서 " + values.get(0).option().family() + " 계열 파츠가 과도하게 반복됩니다.",
                            groups, groups.subList(2, groups.size())));
                });
    }

    private static void repeatedOverlayPatternFindings(
            List<SelectedOption> selected,
            List<BlueprintCompositionFinding> findings
    ) {
        selected.stream()
                .filter(value -> value.group().slot() != null && value.group().slot().contains("overlay"))
                .filter(value -> value.option().implementationKind() != null)
                .collect(Collectors.groupingBy(
                        value -> value.option().implementationKind()
                                + "/" + Objects.toString(value.option().overlayPresentation(), "NONE"),
                        LinkedHashMap::new,
                        Collectors.toList()
                )).values().stream().filter(values -> values.size() > 2).forEach(values -> {
                    List<String> groups = values.stream().map(value -> value.group().id()).toList();
                    findings.add(warning("REPEATED_OVERLAY_PATTERN",
                            "같은 페이지의 " + values.get(0).option().implementationKind()
                                    + " 오버레이 패턴이 반복됩니다.",
                            groups, groups.subList(2, groups.size())));
                });
    }

    private static void repeatedLayoutPatternFindings(
            List<SelectedOption> selected,
            List<BlueprintCompositionFinding> findings
    ) {
        selected.stream()
                .filter(value -> "page.layout".equals(value.group().slot())
                        || "LAYOUT".equals(value.option().implementationKind()))
                .collect(Collectors.groupingBy(
                        value -> value.option().componentId(),
                        LinkedHashMap::new,
                        Collectors.toList()
                )).values().stream().filter(values -> values.size() > 2).forEach(values -> {
                    List<String> groups = values.stream().map(value -> value.group().id()).toList();
                    findings.add(warning("REPEATED_LAYOUT_PATTERN",
                            "여러 페이지에서 " + values.get(0).option().componentId()
                                    + " 레이아웃 패턴이 반복됩니다.",
                            groups, groups.subList(2, groups.size())));
                });
    }

    private static List<SelectedOption> selectedOptions(
            Map<String, BlueprintExclusiveGroup> groupById,
            List<BlueprintSelection> selections
    ) {
        List<SelectedOption> result = new ArrayList<>();
        for (BlueprintSelection selection : selections) {
            BlueprintExclusiveGroup group = groupById.get(selection.groupId());
            if (group == null) continue;
            group.candidates().stream()
                    .filter(candidate -> candidate.componentId().equals(selection.componentId()))
                    .findFirst()
                    .ifPresent(candidate -> result.add(new SelectedOption(group, candidate)));
        }
        return result;
    }

    private static BlueprintCompositionFinding warning(
            String code, String message, List<String> groups, List<String> reselectable
    ) {
        return new BlueprintCompositionFinding(FindingSeverity.WARNING, code, message, groups, reselectable);
    }

    private static BlueprintCompositionFinding error(
            String code, String message, List<String> groups, List<String> reselectable
    ) {
        return new BlueprintCompositionFinding(FindingSeverity.ERROR, code, message, groups, reselectable);
    }

    private record SelectedOption(BlueprintExclusiveGroup group, BlueprintCandidateOption option) {
    }

    private BlueprintCompositionValidator() {
    }
}
