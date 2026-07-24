package gj.cloud.ops.application.deployment.repoanalysis;

public record PythonInfo(
        boolean fastapiDetected,
        boolean djangoDetected,
        boolean flaskDetected,
        boolean gunicornDetected,
        boolean uvicornDetected
) {
}
