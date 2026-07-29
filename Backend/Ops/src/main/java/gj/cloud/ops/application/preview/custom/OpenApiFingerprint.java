package gj.cloud.ops.application.preview.custom;

import gj.cloud.ops.application.preview.analysis.ApiOperationEvidence;
import gj.cloud.ops.application.preview.analysis.Capability;
import gj.cloud.ops.application.preview.analysis.OpenApiEvidence;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.List;

public final class OpenApiFingerprint {

    public static String calculate(OpenApiEvidence evidence, List<Capability> capabilities) {
        StringBuilder canonical = new StringBuilder();
        append(canonical, evidence.title());
        append(canonical, evidence.version());
        evidence.operations().stream()
                .sorted(Comparator
                        .comparing(ApiOperationEvidence::path, Comparator.nullsFirst(String::compareTo))
                        .thenComparing(ApiOperationEvidence::method, Comparator.nullsFirst(String::compareTo))
                        .thenComparing(ApiOperationEvidence::operationId, Comparator.nullsFirst(String::compareTo)))
                .forEach(operation -> {
                    append(canonical, operation.path());
                    append(canonical, operation.method());
                    append(canonical, operation.operationId());
                    appendSorted(canonical, operation.requestBodyFields());
                    appendSorted(canonical, operation.responseFieldPaths());
                    appendSorted(canonical, operation.arrayFieldPaths());
                    operation.enumFields().stream()
                            .sorted(Comparator.comparing(
                                    ApiOperationEvidence.EnumFieldEvidence::path,
                                    Comparator.nullsFirst(String::compareTo)))
                            .forEach(field -> {
                                append(canonical, field.path());
                                appendSorted(canonical, field.values());
                            });
                });
        capabilities.stream()
                .sorted(Comparator.comparing(Capability::id))
                .forEach(capability -> {
                    append(canonical, capability.id());
                    append(canonical, capability.operationId());
                    append(canonical, capability.path());
                    append(canonical, capability.method());
                    appendSorted(canonical, capability.fields());
                    appendSorted(canonical, capability.dependencies());
                });
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", impossible);
        }
    }

    private static void appendSorted(StringBuilder target, List<String> values) {
        if (values == null) return;
        values.stream().sorted(Comparator.nullsFirst(String::compareTo))
                .forEach(value -> append(target, value));
    }

    private static void append(StringBuilder target, String value) {
        String safe = value == null ? "" : value;
        target.append(safe.length()).append(':').append(safe).append('|');
    }

    private OpenApiFingerprint() {
    }
}
