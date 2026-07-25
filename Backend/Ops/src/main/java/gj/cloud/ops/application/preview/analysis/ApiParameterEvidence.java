package gj.cloud.ops.application.preview.analysis;

public record ApiParameterEvidence(
        String name,
        String in,        // query | path | header
        String type,      // string | integer | boolean | array | ...
        boolean required
) {
}
