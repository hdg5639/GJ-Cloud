package gj.cloud.ops.application.preview.analysis;

// auto-preview-design/03-slot-contract.md §3 — Slot 하나에 들어올 수 있는 Block 개수 제약.
public enum Cardinality {
    EXACTLY_ONE,
    ZERO_OR_ONE,
    ONE_OR_MORE,
    ZERO_OR_MORE;

    public boolean isSatisfiedBy(int count) {
        return switch (this) {
            case EXACTLY_ONE -> count == 1;
            case ZERO_OR_ONE -> count <= 1;
            case ONE_OR_MORE -> count >= 1;
            case ZERO_OR_MORE -> true;
        };
    }
}
