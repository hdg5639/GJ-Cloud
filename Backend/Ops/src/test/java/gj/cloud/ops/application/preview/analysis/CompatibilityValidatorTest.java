package gj.cloud.ops.application.preview.analysis;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CompatibilityValidatorTest {

    private final PreviewBlockResolver resolver = new PreviewBlockResolver();

    @Test
    void loginWithPasswordFieldHasNoFindings() {
        Capability login = loginCapability(List.of("email", "password"));
        PageDraft page = new PageDraft("auth-login", "로그인", PageSkeletonType.AUTH_PAGE, List.of(login.id()));
        List<Block> blocks = resolver.resolve(page, List.of(login));

        assertThat(CompatibilityValidator.validate(page, blocks, List.of(login))).isEmpty();
    }

    @Test
    void loginWithoutPasswordLikeFieldIsFlagged() {
        Capability login = loginCapability(List.of("email", "otp"));
        PageDraft page = new PageDraft("auth-login", "로그인", PageSkeletonType.AUTH_PAGE, List.of(login.id()));
        List<Block> blocks = resolver.resolve(page, List.of(login));

        assertThat(CompatibilityValidator.validate(page, blocks, List.of(login)))
                .anyMatch(f -> f.contains("비밀번호로 보이는 필드가 없습니다"));
    }

    @Test
    void loginWithNoDetectedFieldsIsNotFlagged() {
        // fields를 못 찾은 경우(런타임에서 email/password로 기본 표시)까지 오탐하지 않는다.
        Capability login = loginCapability(List.of());
        PageDraft page = new PageDraft("auth-login", "로그인", PageSkeletonType.AUTH_PAGE, List.of(login.id()));
        List<Block> blocks = resolver.resolve(page, List.of(login));

        assertThat(CompatibilityValidator.validate(page, blocks, List.of(login))).isEmpty();
    }

    @Test
    void fullCrudPageHasNoFindings() {
        Capability list = capability("vms.list", "vms", CapabilityType.LIST);
        Capability detail = capability("vms.detail", "vms", CapabilityType.DETAIL);
        Capability create = capability("vms.create", "vms", CapabilityType.CREATE);
        Capability delete = capability("vms.delete", "vms", CapabilityType.DELETE);
        List<Capability> capabilities = List.of(list, detail, create, delete);
        PageDraft page = new PageDraft("vms-page", "Vms", PageSkeletonType.LIST_DETAIL,
                List.of("vms.list", "vms.detail", "vms.create", "vms.delete"));
        List<Block> blocks = resolver.resolve(page, capabilities);

        assertThat(CompatibilityValidator.validate(page, blocks, capabilities)).isEmpty();
    }

    private Capability loginCapability(List<String> fields) {
        return new Capability("auth.login", "auth", CapabilityType.LOGIN, "login", "/auth/login", "POST",
                false, false, false, "HIGH", List.of(), fields, "data.accessToken", null,
                RiskLevel.SAFE, AutomationPolicy.AUTO_SAFE, null, null);
    }

    private Capability capability(String id, String resourceName, CapabilityType type) {
        return new Capability(id, resourceName, type, null, "/" + resourceName, "GET",
                false, false, false, "HIGH", List.of(), List.of(), null, null,
                RiskLevel.SAFE, AutomationPolicy.AUTO_SAFE, null, null);
    }
}
