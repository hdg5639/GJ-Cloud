package gj.cloud.vm.application.port.dto;

public record SubdomainCheckResponse(boolean available, String reason) {

    public static SubdomainCheckResponse ok() {
        return new SubdomainCheckResponse(true, null);
    }

    public static SubdomainCheckResponse denied(String reason) {
        return new SubdomainCheckResponse(false, reason);
    }
}
