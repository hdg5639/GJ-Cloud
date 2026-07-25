package gj.cloud.ops.application.preview.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gj.cloud.ops.global.exception.OpsException;
import gj.cloud.ops.global.exception.enums.OpsErrorCode;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// GamjaBox_2.0_Key_Features.md 5절 — OpenAPI 문서에서 결정론적으로 뽑을 수 있는 정보(경로/메서드/파라미터/
// requestBody 필드/응답이 배열인지)를 AI 없이 추출하는지 검증. fetch(네트워크)는 거치지 않고
// parse()/extract()만 직접 호출한다(package-private으로 테스트를 위해 열어둠).
class OpenApiNormalizerTest {

    private final OpenApiNormalizer normalizer =
            new OpenApiNormalizer(new ObjectMapper(), new OpenApiDocumentSecurityValidator());

    private static final String VM_SERVICE_LIKE_DOC = """
            {
              "openapi": "3.0.1",
              "info": { "title": "vm-service", "version": "1.0" },
              "servers": [{ "url": "https://api.gamjabox.cloud" }],
              "components": {
                "securitySchemes": {
                  "bearerAuth": { "type": "http", "scheme": "bearer" }
                },
                "schemas": {
                  "LoginRequest": {
                    "type": "object",
                    "properties": { "email": { "type": "string" }, "password": { "type": "string" } }
                  },
                  "VmResponse": {
                    "type": "object",
                    "properties": { "id": { "type": "string" }, "name": { "type": "string" } }
                  }
                }
              },
              "paths": {
                "/auth/login": {
                  "post": {
                    "operationId": "login",
                    "summary": "로그인",
                    "requestBody": {
                      "content": { "application/json": { "schema": { "$ref": "#/components/schemas/LoginRequest" } } }
                    },
                    "responses": { "200": { "description": "ok" } }
                  }
                },
                "/vms": {
                  "get": {
                    "operationId": "listVms",
                    "security": [{ "bearerAuth": [] }],
                    "parameters": [
                      { "name": "search", "in": "query", "schema": { "type": "string" } },
                      { "name": "page", "in": "query", "schema": { "type": "integer" } }
                    ],
                    "responses": {
                      "200": {
                        "content": {
                          "application/json": {
                            "schema": { "type": "array", "items": { "$ref": "#/components/schemas/VmResponse" } }
                          }
                        }
                      }
                    }
                  },
                  "post": {
                    "operationId": "createVm",
                    "security": [{ "bearerAuth": [] }],
                    "requestBody": {
                      "content": { "application/json": { "schema": { "$ref": "#/components/schemas/VmResponse" } } }
                    },
                    "responses": { "201": { "description": "created" } }
                  }
                },
                "/vms/{id}": {
                  "parameters": [{ "name": "id", "in": "path", "required": true, "schema": { "type": "string" } }],
                  "get": {
                    "operationId": "getVm",
                    "security": [{ "bearerAuth": [] }],
                    "responses": {
                      "200": {
                        "content": { "application/json": { "schema": { "$ref": "#/components/schemas/VmResponse" } } }
                      }
                    }
                  },
                  "delete": {
                    "operationId": "deleteVm",
                    "security": [{ "bearerAuth": [] }],
                    "responses": { "204": { "description": "no content" } }
                  }
                }
              }
            }
            """;

    @Test
    void extractsOperationsSecuritySchemesAndArrayResponses() {
        JsonNode root = normalizer.parse(VM_SERVICE_LIKE_DOC.getBytes(StandardCharsets.UTF_8));
        OpenApiEvidence evidence = normalizer.extract(root);

        assertThat(evidence.title()).isEqualTo("vm-service");
        assertThat(evidence.securitySchemes()).hasSize(1);
        assertThat(evidence.securitySchemes().get(0).isBearer()).isTrue();
        assertThat(evidence.operations()).hasSize(5);

        ApiOperationEvidence listVms = findOperation(evidence, "GET", "/vms");
        assertThat(listVms.responseIsArray()).isTrue();
        assertThat(listVms.requiresAuth()).isTrue();
        assertThat(listVms.parameters()).extracting(ApiParameterEvidence::name)
                .contains("search", "page");

        ApiOperationEvidence login = findOperation(evidence, "POST", "/auth/login");
        assertThat(login.requestBodyFields()).containsExactlyInAnyOrder("email", "password");

        ApiOperationEvidence getVm = findOperation(evidence, "GET", "/vms/{id}");
        assertThat(getVm.hasPathParam()).isTrue();
        // path-item 레벨 공유 파라미터(id)가 오퍼레이션 파라미터에 병합되는지 확인
        assertThat(getVm.parameters()).extracting(ApiParameterEvidence::name).contains("id");
    }

    @Test
    void rejectsNonOpenApi3Documents() {
        String swagger2Doc = "{ \"swagger\": \"2.0\", \"info\": { \"title\": \"legacy\" }, \"paths\": {} }";
        JsonNode root = normalizer.parse(swagger2Doc.getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> normalizer.extract(root))
                .isInstanceOf(OpsException.class)
                .satisfies(e -> assertThat(((OpsException) e).getErrorCode())
                        .isEqualTo(OpsErrorCode.API_DOCS_UNSUPPORTED_VERSION));
    }

    private ApiOperationEvidence findOperation(OpenApiEvidence evidence, String method, String path) {
        return evidence.operations().stream()
                .filter(op -> op.method().equals(method) && op.path().equals(path))
                .findFirst()
                .orElseThrow();
    }
}
