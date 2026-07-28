package gj.cloud.ops.application.preview.planning.model;

// 페이지 route의 명명된 파라미터. source는 navigation/query/context처럼 확장 가능한 문자열로
// 유지하며, Patch Validator가 route의 :placeholder와 선언이 정확히 대응하는지 강제한다.
public record RouteParameter(
        String name,
        String source
) {
}
