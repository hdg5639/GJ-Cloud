package gj.cloud.ops.application.preview.custom;

import gj.cloud.ops.application.preview.analysis.ApiOperationEvidence;
import gj.cloud.ops.application.preview.analysis.OpenApiEvidence;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiFingerprintTest {

    @Test
    void ignoresDocumentOrderingButChangesWhenTheOperationContractChanges() {
        ApiOperationEvidence list = operation("/members", "GET", "listMembers");
        ApiOperationEvidence invite = operation("/members/invitations", "POST", "inviteMember");
        OpenApiEvidence first = evidence(List.of(list, invite));
        OpenApiEvidence reordered = evidence(List.of(invite, list));
        OpenApiEvidence changed = evidence(List.of(
                list,
                operation("/members/invitations", "PUT", "inviteMember")
        ));

        String fingerprint = OpenApiFingerprint.calculate(first, List.of());

        assertThat(fingerprint).hasSize(64);
        assertThat(OpenApiFingerprint.calculate(reordered, List.of())).isEqualTo(fingerprint);
        assertThat(OpenApiFingerprint.calculate(changed, List.of())).isNotEqualTo(fingerprint);
    }

    private OpenApiEvidence evidence(List<ApiOperationEvidence> operations) {
        return new OpenApiEvidence(
                "Member API", "1.0", List.of("https://api.example.com"),
                List.of(), operations, 0);
    }

    private ApiOperationEvidence operation(String path, String method, String operationId) {
        return new ApiOperationEvidence(
                path,
                method,
                operationId,
                operationId,
                List.of("members"),
                List.of(),
                List.of("role"),
                true,
                false,
                List.of("data.id", "data.status"),
                List.of()
        );
    }
}
