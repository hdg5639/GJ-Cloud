package gj.cloud.vm.application.port.dto;

public record SubdomainCheckResponse(boolean available, String reason) {

    public static SubdomainCheckResponse available() {
        return new SubdomainCheckResponse(true, null);
    }

    public static SubdomainCheckResponse unavailable(String reason) {
        return new SubdomainCheckResponse(false, reason);
    }
}
