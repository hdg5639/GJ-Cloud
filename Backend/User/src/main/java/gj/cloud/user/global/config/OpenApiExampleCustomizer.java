package gj.cloud.user.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Swagger에 노출되는 사용자 API 입출력의 빈 example을 안전한 목업 값으로 보완한다.
 * DTO에 명시한 example은 항상 우선하며 관리자·내부 API에는 적용하지 않는다.
 */
@Component
public class OpenApiExampleCustomizer implements OpenApiCustomizer {

    private static final String EXAMPLE_UUID = "8b7e1f6a-3d5c-4a9b-8c2e-1f0a9d7c6b5e";
    private static final Set<String> EXCLUDED_PATH_PREFIXES = Set.of("/admin", "/internal");

    @Override
    public void customise(OpenAPI openApi) {
        if (openApi.getPaths() == null) {
            return;
        }
        Components components = openApi.getComponents();
        Map<String, Schema> schemas = components != null && components.getSchemas() != null
                ? components.getSchemas()
                : Map.of();

        openApi.getPaths().forEach((path, pathItem) -> {
            if (isExcluded(path)) {
                return;
            }
            addParameterExamples(pathItem.getParameters());
            for (Operation operation : pathItem.readOperations()) {
                addParameterExamples(operation.getParameters());
                addRequestBodyExamples(operation.getRequestBody(), components, schemas);
                addResponseExamples(operation.getResponses(), components, schemas);
            }
        });
    }

    private boolean isExcluded(String path) {
        return EXCLUDED_PATH_PREFIXES.stream()
                .anyMatch(prefix -> path.equals(prefix) || path.startsWith(prefix + "/"));
    }

    private void addParameterExamples(List<Parameter> parameters) {
        if (parameters == null) {
            return;
        }
        for (Parameter parameter : parameters) {
            if (parameter.getExample() != null
                    || (parameter.getExamples() != null && !parameter.getExamples().isEmpty())) {
                continue;
            }
            Object example = exampleFor(parameter.getName(), parameter.getSchema());
            if (example != null) {
                parameter.setExample(example);
            }
        }
    }

    private void addRequestBodyExamples(
            RequestBody requestBody,
            Components components,
            Map<String, Schema> schemas
    ) {
        if (requestBody == null) {
            return;
        }
        RequestBody resolved = resolveRequestBody(requestBody, components);
        Content content = resolved != null ? resolved.getContent() : null;
        if (content == null) {
            return;
        }
        content.values().forEach(mediaType ->
                enrichSchema(mediaType.getSchema(), "body", schemas, new HashSet<>()));
    }

    private RequestBody resolveRequestBody(RequestBody requestBody, Components components) {
        if (requestBody.get$ref() == null || components == null || components.getRequestBodies() == null) {
            return requestBody;
        }
        return components.getRequestBodies().getOrDefault(refName(requestBody.get$ref()), requestBody);
    }

    private void addResponseExamples(
            ApiResponses responses,
            Components components,
            Map<String, Schema> schemas
    ) {
        if (responses == null) {
            return;
        }
        responses.values().forEach(response -> {
            ApiResponse resolved = resolveResponse(response, components);
            Content content = resolved != null ? resolved.getContent() : null;
            if (content != null) {
                content.values().forEach(mediaType ->
                        enrichSchema(mediaType.getSchema(), "response", schemas, new HashSet<>()));
            }
        });
    }

    private ApiResponse resolveResponse(ApiResponse response, Components components) {
        if (response.get$ref() == null || components == null || components.getResponses() == null) {
            return response;
        }
        return components.getResponses().getOrDefault(refName(response.get$ref()), response);
    }

    private void enrichSchema(
            Schema<?> schema,
            String propertyName,
            Map<String, Schema> schemas,
            Set<String> visiting
    ) {
        if (schema == null) {
            return;
        }

        if (schema.getExample() == null) {
            Object example = exampleFor(propertyName, schema);
            if (example != null) {
                schema.setExample(example);
            }
        }

        if (schema.get$ref() != null) {
            String name = refName(schema.get$ref());
            Schema<?> referenced = schemas.get(name);
            if (referenced != null && visiting.add(name)) {
                enrichSchema(referenced, name, schemas, visiting);
                visiting.remove(name);
            }
        }

        enrichComposedSchemas(schema.getAllOf(), schemas, visiting);
        enrichComposedSchemas(schema.getOneOf(), schemas, visiting);
        enrichComposedSchemas(schema.getAnyOf(), schemas, visiting);

        if (schema.getProperties() != null) {
            schema.getProperties().forEach((name, property) ->
                    enrichSchema((Schema<?>) property, name, schemas, visiting));
        }
        if (schema.getItems() != null) {
            enrichSchema(schema.getItems(), singular(propertyName), schemas, visiting);
            if (schema.getExample() == null && schema.getItems().getExample() != null) {
                schema.setExample(List.of(schema.getItems().getExample()));
            }
        }
        if (schema.getAdditionalProperties() instanceof Schema<?> additionalSchema) {
            enrichSchema(additionalSchema, "value", schemas, visiting);
        }
    }

    private void enrichComposedSchemas(
            List<Schema> composedSchemas,
            Map<String, Schema> schemas,
            Set<String> visiting
    ) {
        if (composedSchemas == null) {
            return;
        }
        composedSchemas.forEach(schema -> enrichSchema(schema, "value", schemas, visiting));
    }

    private Object exampleFor(String rawName, Schema<?> schema) {
        if (schema == null) {
            return null;
        }
        if (schema.getEnum() != null && !schema.getEnum().isEmpty()) {
            return schema.getEnum().get(0);
        }

        String key = normalize(rawName);
        Object named = namedExample(key);
        if (named != null && isCompatible(named, schema)) {
            return named;
        }

        String format = schema.getFormat();
        if ("uuid".equals(format)) return EXAMPLE_UUID;
        if ("date-time".equals(format)) return "2026-08-12T12:00:00Z";
        if ("date".equals(format)) return "2026-08-12";
        if ("byte".equals(format)) return "eyJleGFtcGxlIjp0cnVlfQ==";
        if ("binary".equals(format)) return null;

        String type = schema.getType();
        if ("boolean".equals(type)) return true;
        if ("integer".equals(type)) return integerExample(key);
        if ("number".equals(type)) return 1.0;
        if ("string".equals(type)) {
            if (key.endsWith("id")) return key + "_example";
            if (key.contains("url")) return "https://api.example.test";
            return key.isBlank() || "body".equals(key) ? "example" : "example-" + key;
        }
        return null;
    }

    private boolean isCompatible(Object example, Schema<?> schema) {
        return switch (schema.getType() == null ? "" : schema.getType()) {
            case "string" -> example instanceof String;
            case "integer", "number" -> example instanceof Number;
            case "boolean" -> example instanceof Boolean;
            case "array" -> example instanceof List<?>;
            case "object" -> example instanceof Map<?, ?>;
            default -> true;
        };
    }

    private Object namedExample(String key) {
        return switch (key) {
            case "email", "requesteremail", "owneremail" -> "developer@example.test";
            case "password", "currentpassword" -> "Example-password-123!";
            case "newpassword" -> "New-example-password-456!";
            case "code" -> "123456";
            case "resettoken" -> "reset_token_example";
            case "refreshtoken" -> "refresh_token_example";
            case "accesstoken", "token" -> "access_token_example";
            case "authorization" -> "Bearer <access-token>";
            case "clientid" -> "example-service";
            case "clientsecret" -> "example-client-secret";
            case "targetservice", "audience" -> "user-service";
            case "scope" -> "profile:read";
            case "scopes" -> List.of("profile:read");
            case "vmid", "orgid", "memberid", "portid", "inquiryid", "tagid" -> EXAMPLE_UUID;
            case "targetid" -> "target_sample_app";
            case "deploymentid" -> "deployment_20260812_001";
            case "scenarioid" -> "scenario_checkout";
            case "suiteid" -> "suite_smoke";
            case "runid" -> "run_20260812_001";
            case "backupid" -> "backup_20260812_001";
            case "keyid" -> "key_my_laptop";
            case "serviceid" -> "commerce-api";
            case "deliveryid" -> "72f1a5f0-1234-4cde-9876-123456789abc";
            case "eventtype" -> "push";
            case "signature" -> "sha256=<webhook-signature>";
            case "name" -> "example-resource";
            case "vmname" -> "sample-vm";
            case "targetname" -> "sample-app";
            case "nickname" -> "감자개발자";
            case "title" -> "배포 설정 문의";
            case "description", "servicedescription" -> "예제 서비스의 기능과 사용 목적을 설명합니다.";
            case "content" -> "예시 입력 내용입니다.";
            case "query" -> "배포";
            case "category" -> "DEPLOYMENT";
            case "slug", "sourcearticleslug" -> "deployment-guide";
            case "sourcearticletitle" -> "배포 시작하기";
            case "repourl", "repositoryurl" -> "https://github.com/example/project.git";
            case "branch" -> "main";
            case "context" -> "backend";
            case "installpath" -> "/home/ubuntu/apps/sample-app";
            case "path" -> "/home/ubuntu/apps/sample-app/compose.yaml";
            case "parentpath" -> "/home/ubuntu/apps/sample-app";
            case "filename" -> "guide-image.webp";
            case "composecontent" -> "services:\n  app:\n    image: nginx:alpine\n    ports:\n      - \"8080:80\"\n";
            case "apidocsurl" -> "https://api.example.test/v3/api-docs";
            case "apidocscontent" -> "{\"openapi\":\"3.0.3\",\"info\":{\"title\":\"Example API\",\"version\":\"1.0.0\"},\"paths\":{}}";
            case "documentationpageurl" -> "https://docs.example.test/getting-started";
            case "apibaseurl" -> "https://api.example.test";
            case "scenariointent" -> "상품을 조회하고 장바구니에 담은 뒤 주문을 생성하는 흐름";
            case "selectedcapabilityids", "capabilityids" -> List.of("GET_/products", "POST_/orders");
            case "tags" -> List.of("deployment", "guide");
            case "emails" -> List.of("member@example.test");
            case "partoverrides" -> Map.of("home/hero", "hero-banner");
            case "headers" -> Map.of("X-API-Key", "example-api-key");
            case "timeframe" -> "hour";
            case "aftersequence" -> 0L;
            case "tail" -> 200;
            case "page" -> 1;
            case "size" -> 20;
            case "port", "internalport", "externalport" -> 8080;
            case "disksizegb", "disksize" -> 50;
            case "autodeploy", "enabled", "rememberme", "featured", "pinned" -> true;
            default -> null;
        };
    }

    private int integerExample(String key) {
        if (key.contains("port")) return 8080;
        if ("tail".equals(key)) return 200;
        if ("size".equals(key)) return 20;
        if ("page".equals(key)) return 1;
        return 1;
    }

    private String singular(String value) {
        String normalized = normalize(value);
        return normalized.endsWith("s") && normalized.length() > 1
                ? normalized.substring(0, normalized.length() - 1)
                : normalized;
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private String refName(String ref) {
        int slash = ref.lastIndexOf('/');
        return slash >= 0 ? ref.substring(slash + 1) : ref;
    }
}
