package gj.cloud.auth.global.response;

public record ApiResponse<T>(
        boolean success,
        T data,
        String message,
        String errorCode
) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, null);
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(true, null, null, null);
    }

    public static ApiResponse<Void> fail(String message) {
        return new ApiResponse<>(false, null, message, null);
    }

    public static ApiResponse<Void> fail(String message, String errorCode) {
        return new ApiResponse<>(false, null, message, errorCode);
    }
}
