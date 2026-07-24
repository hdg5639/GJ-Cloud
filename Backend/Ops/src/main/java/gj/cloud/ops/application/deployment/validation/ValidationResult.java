package gj.cloud.ops.application.deployment.validation;

import java.util.List;

public record ValidationResult(boolean valid, List<ValidationError> errors) {
    public static ValidationResult ok() {
        return new ValidationResult(true, List.of());
    }

    public static ValidationResult fail(List<ValidationError> errors) {
        return new ValidationResult(false, errors);
    }
}
