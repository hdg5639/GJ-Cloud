package gj.cloud.user.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiExampleCustomizerTest {

    private final OpenApiExampleCustomizer customizer = new OpenApiExampleCustomizer();

    @Test
    void fillsPublicRequestAndParameterExamplesWithoutOverwritingExplicitValues() {
        ObjectSchema requestSchema = new ObjectSchema();
        requestSchema.addProperty("email", new StringSchema());
        requestSchema.addProperty("password", new StringSchema().example("keep-this-example"));
        requestSchema.addProperty("vmId", new StringSchema().format("uuid"));
        requestSchema.addProperty("inquiryId", new IntegerSchema());
        ObjectSchema responseDataSchema = new ObjectSchema();
        responseDataSchema.addProperty("nickname", new StringSchema());
        responseDataSchema.addProperty("createdAt", new StringSchema().format("date-time"));
        ObjectSchema responseSchema = responseWrapper("PublicResponse");

        Parameter page = new Parameter()
                .name("page")
                .in("query")
                .schema(new IntegerSchema());
        Operation operation = new Operation()
                .addParametersItem(page)
                .requestBody(requestBody("PublicRequest"))
                .responses(responses("ApiResponsePublicResponse"));
        OpenAPI openApi = new OpenAPI()
                .components(new Components()
                        .addSchemas("PublicRequest", requestSchema)
                        .addSchemas("PublicResponse", responseDataSchema)
                        .addSchemas("ApiResponsePublicResponse", responseSchema))
                .path("/users/example", new PathItem().post(operation));

        customizer.customise(openApi);

        assertThat(requestSchema.getProperties().get("email").getExample())
                .isEqualTo("developer@example.test");
        assertThat(requestSchema.getProperties().get("password").getExample())
                .isEqualTo("keep-this-example");
        assertThat(requestSchema.getProperties().get("vmId").getExample())
                .isEqualTo("8b7e1f6a-3d5c-4a9b-8c2e-1f0a9d7c6b5e");
        assertThat(requestSchema.getProperties().get("inquiryId").getExample()).isEqualTo(1);
        assertThat(page.getExample()).isEqualTo(1);
        assertThat(responseDataSchema.getProperties().get("nickname").getExample()).isEqualTo("감자개발자");
        assertThat(responseDataSchema.getProperties().get("createdAt").getExample())
                .isEqualTo("2026-08-12T12:00:00Z");
        Map<?, ?> responseExample = (Map<?, ?>) responseSchema.getExample();
        assertThat(responseExample.get("success")).isEqualTo(true);
        assertThat(responseExample.get("message")).isNull();
        assertThat(responseExample.get("errorCode")).isNull();
        Map<?, ?> responseData = (Map<?, ?>) responseExample.get("data");
        assertThat(responseData.get("nickname")).isEqualTo("감자개발자");
        assertThat(responseData.get("createdAt")).isEqualTo("2026-08-12T12:00:00Z");
    }

    @Test
    void rendersVoidResponseDataAndSuccessMetadataAsNull() {
        ObjectSchema responseSchema = responseWrapper(null);
        OpenAPI openApi = new OpenAPI()
                .components(new Components().addSchemas("ApiResponseVoid", responseSchema))
                .path("/auth/register", new PathItem().post(
                        new Operation().responses(responses("ApiResponseVoid"))));

        customizer.customise(openApi);

        Map<?, ?> responseExample = (Map<?, ?>) responseSchema.getExample();
        assertThat(responseExample.get("success")).isEqualTo(true);
        assertThat(responseExample.get("data")).isNull();
        assertThat(responseExample.get("message")).isNull();
        assertThat(responseExample.get("errorCode")).isNull();
    }

    @Test
    void skipsAdminAndInternalPaths() {
        ObjectSchema adminSchema = new ObjectSchema();
        adminSchema.addProperty("email", new StringSchema());
        ObjectSchema internalSchema = new ObjectSchema();
        internalSchema.addProperty("email", new StringSchema());
        OpenAPI openApi = new OpenAPI()
                .components(new Components()
                        .addSchemas("AdminRequest", adminSchema)
                        .addSchemas("InternalRequest", internalSchema))
                .path("/admin/users", new PathItem().post(
                        new Operation().requestBody(requestBody("AdminRequest"))))
                .path("/internal/users", new PathItem().post(
                        new Operation().requestBody(requestBody("InternalRequest"))));

        customizer.customise(openApi);

        assertThat(adminSchema.getProperties().get("email").getExample()).isNull();
        assertThat(internalSchema.getProperties().get("email").getExample()).isNull();
    }

    private RequestBody requestBody(String schemaName) {
        Schema<?> reference = new Schema<>().$ref("#/components/schemas/" + schemaName);
        return new RequestBody().content(new Content().addMediaType(
                "application/json", new MediaType().schema(reference)));
    }

    private ApiResponses responses(String schemaName) {
        Schema<?> reference = new Schema<>().$ref("#/components/schemas/" + schemaName);
        return new ApiResponses().addApiResponse("200", new ApiResponse()
                .description("성공")
                .content(new Content().addMediaType(
                        "application/json", new MediaType().schema(reference))));
    }

    private ObjectSchema responseWrapper(String dataSchemaName) {
        ObjectSchema wrapper = new ObjectSchema();
        wrapper.addProperty("success", new Schema<Boolean>().type("boolean"));
        wrapper.addProperty("data", dataSchemaName == null
                ? new ObjectSchema()
                : new Schema<>().$ref("#/components/schemas/" + dataSchemaName));
        wrapper.addProperty("message", new StringSchema());
        wrapper.addProperty("errorCode", new StringSchema());
        return wrapper;
    }
}
