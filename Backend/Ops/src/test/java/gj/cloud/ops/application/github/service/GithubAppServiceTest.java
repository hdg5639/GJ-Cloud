package gj.cloud.ops.application.github.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import gj.cloud.ops.application.github.dto.GithubInstallationCompleteResponse;
import gj.cloud.ops.domain.github.entity.GithubInstallationEntity;
import gj.cloud.ops.domain.github.repository.GithubInstallationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GithubAppServiceTest {

    private static final String USER_ID = "user-1";
    private static final String VM_ID = "vm-1";
    private static final String STATE = "state-1";

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final GithubInstallationRepository installationRepository =
            mock(GithubInstallationRepository.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);

    private MockRestServiceServer githubApiServer;
    private MockRestServiceServer githubOAuthServer;
    private GithubAppService githubAppService;

    @BeforeEach
    void setUp() {
        RestClient.Builder githubApiBuilder = RestClient.builder().baseUrl("https://api.github.com");
        githubApiServer = MockRestServiceServer.bindTo(githubApiBuilder).build();
        RestClient.Builder githubOAuthBuilder = RestClient.builder().baseUrl("https://github.com");
        githubOAuthServer = MockRestServiceServer.bindTo(githubOAuthBuilder).build();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.getAndDelete("github-install-state:" + STATE))
                .thenReturn(USER_ID + "|" + VM_ID);
        when(installationRepository.findByInstallationIdAndUserId(101L, USER_ID))
                .thenReturn(Optional.empty());
        when(installationRepository.save(any(GithubInstallationEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        githubAppService = new GithubAppService(
                "1234",
                "gamjabox",
                "Iv1.client",
                "client-secret",
                "-----BEGIN PRIVATE KEY-----\\ntest\\n-----END PRIVATE KEY-----",
                new ObjectMapper(),
                redisTemplate,
                installationRepository,
                githubApiBuilder.build(),
                githubOAuthBuilder.build()
        );
    }

    @Test
    void exchangesAuthorizationCodeAsUrlEncodedForm() {
        githubOAuthServer.expect(requestTo("https://github.com/login/oauth/access_token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(
                        HttpHeaders.CONTENT_TYPE,
                        MediaType.APPLICATION_FORM_URLENCODED_VALUE))
                .andExpect(content().string(
                        "client_id=Iv1.client&client_secret=client-secret&code=authorization-code"))
                .andRespond(withSuccess(
                        "{\"access_token\":\"ghu_test\",\"token_type\":\"bearer\"}",
                        MediaType.APPLICATION_JSON));
        githubApiServer.expect(requestTo(
                        "https://api.github.com/user/installations?per_page=100&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer ghu_test"))
                .andRespond(withSuccess(
                        """
                        {
                          "installations": [
                            {
                              "id": 101,
                              "account": {
                                "login": "octocat",
                                "type": "User"
                              }
                            }
                          ]
                        }
                        """,
                        MediaType.APPLICATION_JSON));

        GithubInstallationCompleteResponse response =
                githubAppService.completeInstallation(
                        USER_ID, "authorization-code", STATE);

        assertThat(response.vmId()).isEqualTo(VM_ID);
        assertThat(response.installations()).singleElement()
                .satisfies(installation -> {
                    assertThat(installation.installationId()).isEqualTo(101L);
                    assertThat(installation.accountLogin()).isEqualTo("octocat");
                    assertThat(installation.accountType()).isEqualTo("User");
                });
        githubOAuthServer.verify();
        githubApiServer.verify();
    }

    @Test
    void createsWithProductionConstructorWithoutRestClientBuilderBean() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getBeanFactory().registerSingleton("objectMapper", new ObjectMapper());
            context.getBeanFactory().registerSingleton(
                    "redisTemplate", mock(StringRedisTemplate.class));
            context.getBeanFactory().registerSingleton(
                    "installationRepository", mock(GithubInstallationRepository.class));
            context.register(GithubAppService.class);

            context.refresh();

            assertThat(context.getBean(GithubAppService.class)).isNotNull();
        }
    }
}
