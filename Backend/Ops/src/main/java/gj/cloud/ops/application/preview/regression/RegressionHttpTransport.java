package gj.cloud.ops.application.preview.regression;

import java.util.Map;

public interface RegressionHttpTransport {

    Response execute(Request request);

    record Request(
            String method,
            String url,
            Map<String, String> headers,
            Map<String, Object> body
    ) {
        public Request {
            headers = headers == null ? Map.of() : Map.copyOf(headers);
            body = body == null ? Map.of() : Map.copyOf(body);
        }
    }

    record Response(
            int status,
            Map<String, String> headers,
            Object body
    ) {
        public Response {
            headers = headers == null ? Map.of() : Map.copyOf(headers);
        }
    }
}
