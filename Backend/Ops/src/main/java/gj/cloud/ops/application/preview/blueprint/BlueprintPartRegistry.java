package gj.cloud.ops.application.preview.blueprint;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import gj.cloud.ops.application.preview.dto.PreviewAnalyzeRequest.Purpose;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.Set;

// Portal의 component-manifest.json이 유일한 정본이다. Gradle syncBlueprintManifest가 같은 파일을
// classpath에 복사하므로 Java 계약·선택기·Portal Registry가 별도 목록을 손으로 유지하지 않는다.
public final class BlueprintPartRegistry {

    private static final String MANIFEST_RESOURCE = "blueprint/component-manifest.json";

    public enum PartKind {
        COLLECTION,
        DETAIL,
        DASHBOARD,
        ACTIONS,
        OVERLAY,
        LAYOUT,
        NAVIGATION,
        FEEDBACK,
        THEME
    }

    public record BlueprintPart(
            String componentId,
            String implementationKind,
            PartKind kind,
            BlueprintCategory category,
            Set<String> acceptedSurfaces,
            Set<Purpose> preferredPurposes,
            Set<String> supportedModes,
            String label,
            String family,
            Set<String> tags,
            Set<String> states,
            String overlayPresentation,
            boolean autoSelectable
    ) {
        public boolean supportsMode(String mode) {
            return supportedModes.isEmpty() || (mode != null && supportedModes.contains(mode));
        }
    }

    private record ManifestPart(
            String componentId,
            String kind,
            String mountPoint,
            String category,
            List<String> acceptedSurfaces,
            List<String> preferredPurposes,
            List<String> supportedModes,
            String label,
            String family,
            List<String> tags,
            List<String> states,
            String overlayPresentation,
            boolean autoSelectable
    ) {
        BlueprintPart toPart() {
            return new BlueprintPart(
                    componentId,
                    kind,
                    PartKind.valueOf(mountPoint),
                    BlueprintCategory.valueOf(category),
                    Set.copyOf(acceptedSurfaces),
                    preferredPurposes.stream().map(Purpose::valueOf).collect(java.util.stream.Collectors.toUnmodifiableSet()),
                    Set.copyOf(supportedModes),
                    label,
                    family,
                    Set.copyOf(tags),
                    Set.copyOf(states),
                    overlayPresentation,
                    autoSelectable
            );
        }
    }

    public static final List<BlueprintPart> ALL = loadManifest();

    public static Optional<PartKind> kindOfBaseComponent(String baseComponentId) {
        return switch (baseComponentId) {
            case "resource-table", "resource-card-grid" -> Optional.of(PartKind.COLLECTION);
            case "detail-panel", "full-detail-page" -> Optional.of(PartKind.DETAIL);
            case "dashboard-view", "recent-activity-dashboard" -> Optional.of(PartKind.DASHBOARD);
            case "quick-action-button-group" -> Optional.of(PartKind.ACTIONS);
            case "create-edit-modal", "form-drawer", "delete-confirm-modal", "typed-confirm-modal" ->
                    Optional.of(PartKind.OVERLAY);
            case "default-layout" -> Optional.of(PartKind.LAYOUT);
            case "default-navigation" -> Optional.of(PartKind.NAVIGATION);
            case "default-feedback" -> Optional.of(PartKind.FEEDBACK);
            case "default-theme" -> Optional.of(PartKind.THEME);
            default -> Optional.empty();
        };
    }

    public static Optional<BlueprintPart> find(
            PartKind kind,
            BlueprintCategory category,
            String slot,
            Purpose purpose,
            String mode
    ) {
        return ALL.stream()
                .filter(BlueprintPart::autoSelectable)
                .filter(part -> part.kind() == kind)
                .filter(part -> part.category() == category)
                .filter(part -> part.acceptedSurfaces().contains(slot))
                .filter(part -> part.preferredPurposes().isEmpty()
                        || (purpose != null && part.preferredPurposes().contains(purpose)))
                .filter(part -> part.supportsMode(mode))
                .findFirst();
    }

    private static List<BlueprintPart> loadManifest() {
        ObjectMapper mapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        try (InputStream input = BlueprintPartRegistry.class.getClassLoader()
                .getResourceAsStream(MANIFEST_RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("Blueprint component manifest를 찾지 못했습니다: " + MANIFEST_RESOURCE);
            }
            List<ManifestPart> parts = mapper.readValue(input, new TypeReference<>() {});
            List<BlueprintPart> result = parts.stream().map(ManifestPart::toPart).toList();
            long uniqueIds = result.stream().map(BlueprintPart::componentId).distinct().count();
            if (uniqueIds != result.size()) {
                throw new IllegalStateException("Blueprint component manifest에 중복 componentId가 있습니다.");
            }
            return result;
        } catch (IOException e) {
            throw new IllegalStateException("Blueprint component manifest를 읽지 못했습니다.", e);
        }
    }

    private BlueprintPartRegistry() {
    }
}
