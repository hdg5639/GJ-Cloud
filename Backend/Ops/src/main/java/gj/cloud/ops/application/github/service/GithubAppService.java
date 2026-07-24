package gj.cloud.ops.application.github.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import gj.cloud.ops.application.github.dto.GithubInstallUrlResponse;
import gj.cloud.ops.application.github.dto.GithubInstallationCompleteResponse;
import gj.cloud.ops.application.github.dto.GithubInstallationResponse;
import gj.cloud.ops.application.github.dto.GithubRepositoryAccess;
import gj.cloud.ops.application.github.dto.GithubRepositoryResponse;
import gj.cloud.ops.domain.github.entity.GithubInstallationEntity;
import gj.cloud.ops.domain.github.repository.GithubInstallationRepository;
import gj.cloud.ops.global.exception.OpsException;
import gj.cloud.ops.global.exception.enums.OpsErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class GithubAppService {

    private static final String STATE_KEY_PREFIX = "github-install-state:";
    private static final Duration STATE_TTL = Duration.ofMinutes(10);
    private static final String GITHUB_API_VERSION = "2022-11-28";

    private final String appId;
    private final String appSlug;
    private final String clientId;
    private final String clientSecret;
    private final String privateKeyPem;
    private final RestClient githubApi;
    private final RestClient githubOAuth;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;
    private final GithubInstallationRepository installationRepository;

    public GithubAppService(
            @Value("${ops.github.app-id:}") String appId,
            @Value("${ops.github.app-slug:}") String appSlug,
            @Value("${ops.github.client-id:}") String clientId,
            @Value("${ops.github.client-secret:}") String clientSecret,
            @Value("${ops.github.private-key:}") String privateKeyPem,
            ObjectMapper objectMapper,
            StringRedisTemplate redisTemplate,
            GithubInstallationRepository installationRepository
    ) {
        this.appId = appId;
        this.appSlug = appSlug;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.privateKeyPem = normalizePem(privateKeyPem);
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
        this.installationRepository = installationRepository;
        this.githubApi = RestClient.builder()
                .baseUrl("https://api.github.com")
                .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .defaultHeader(HttpHeaders.USER_AGENT, "GamjaBox-Ops")
                .defaultHeader("X-GitHub-Api-Version", GITHUB_API_VERSION)
                .build();
        this.githubOAuth = RestClient.builder()
                .baseUrl("https://github.com")
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.USER_AGENT, "GamjaBox-Ops")
                .build();
    }

    public GithubInstallUrlResponse createInstallUrl(String userId, String vmId) {
        requireConfigured();
        String state = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(STATE_KEY_PREFIX + state, userId + "|" + vmId, STATE_TTL);
        String url = "https://github.com/apps/" + URLEncoder.encode(appSlug, StandardCharsets.UTF_8)
                + "/installations/new?state=" + URLEncoder.encode(state, StandardCharsets.UTF_8);
        return new GithubInstallUrlResponse(url);
    }

    public GithubInstallationCompleteResponse completeInstallation(
            String userId, String code, String state
    ) {
        requireConfigured();
        String stateValue = redisTemplate.opsForValue().getAndDelete(STATE_KEY_PREFIX + state);
        if (stateValue == null) {
            throw new OpsException(OpsErrorCode.GITHUB_INSTALL_STATE_INVALID);
        }
        String[] stateParts = stateValue.split("\\|", 2);
        if (stateParts.length != 2 || !userId.equals(stateParts[0])) {
            throw new OpsException(OpsErrorCode.GITHUB_INSTALL_STATE_INVALID);
        }

        String userAccessToken = exchangeUserCode(code);
        List<JsonNode> accessibleInstallations = listUserInstallations(userAccessToken);
        if (accessibleInstallations.isEmpty()) {
            throw new OpsException(OpsErrorCode.GITHUB_INSTALLATION_NOT_FOUND);
        }
        List<GithubInstallationResponse> saved = accessibleInstallations.stream()
                .map(installation -> {
                    Long installationId = installation.path("id").asLong();
                    JsonNode account = installation.path("account");
                    String login = account.path("login").asText();
                    String type = account.path("type").asText("Unknown");
                    if (installationId == 0 || login.isBlank()) {
                        throw new OpsException(OpsErrorCode.GITHUB_INSTALLATION_NOT_FOUND);
                    }
                    GithubInstallationEntity entity = installationRepository.save(
                            installationRepository.findByInstallationIdAndUserId(
                                            installationId, userId)
                                    .map(existing -> existing.refreshed(login, type))
                                    .orElseGet(() -> GithubInstallationEntity.create(
                                            installationId, userId, login, type)));
                    return GithubInstallationResponse.from(entity);
                })
                .toList();
        return new GithubInstallationCompleteResponse(saved, stateParts[1]);
    }

    public List<GithubInstallationResponse> listInstallations(String userId) {
        return installationRepository.findAllByUserIdOrderByCreatedAtAsc(userId)
                .stream()
                .map(GithubInstallationResponse::from)
                .toList();
    }

    public List<GithubRepositoryResponse> listRepositories(String userId) {
        requireConfigured();
        List<GithubRepositoryResponse> repositories = new ArrayList<>();
        for (GithubInstallationEntity installation
                : installationRepository.findAllByUserIdOrderByCreatedAtAsc(userId)) {
            String token = createInstallationToken(installation.getInstallationId());
            for (JsonNode repository : listInstallationRepositories(token)) {
                repositories.add(toRepositoryResponse(installation.getInstallationId(), repository));
            }
        }
        return repositories;
    }

    public GithubRepositoryAccess resolveRepositoryAccess(
            String userId, Long installationId, Long repositoryId
    ) {
        requireConfigured();
        installationRepository.findByInstallationIdAndUserId(installationId, userId)
                .orElseThrow(() -> new OpsException(OpsErrorCode.GITHUB_INSTALLATION_NOT_FOUND));
        return resolveRepositoryAccess(installationId, repositoryId);
    }

    public GithubRepositoryAccess resolveRepositoryAccess(Long installationId, Long repositoryId) {
        requireConfigured();
        String token = createInstallationToken(installationId);
        JsonNode repository = getWithInstallationToken("/repositories/" + repositoryId, token);
        if (repository.path("id").asLong() == repositoryId) {
            return new GithubRepositoryAccess(
                    installationId,
                    repositoryId,
                    repository.path("full_name").asText(),
                    repository.path("clone_url").asText(),
                    repository.path("default_branch").asText("main"),
                    token
            );
        }
        throw new OpsException(OpsErrorCode.GITHUB_REPOSITORY_NOT_FOUND);
    }

    private List<JsonNode> listInstallationRepositories(String token) {
        List<JsonNode> repositories = new ArrayList<>();
        int page = 1;
        while (true) {
            JsonNode response = getWithInstallationToken(
                    "/installation/repositories?per_page=100&page=" + page, token);
            JsonNode items = response.path("repositories");
            if (!items.isArray()) {
                break;
            }
            int count = 0;
            for (JsonNode repository : items) {
                repositories.add(repository);
                count++;
            }
            if (count < 100) {
                break;
            }
            page++;
        }
        return repositories;
    }

    private String exchangeUserCode(String code) {
        try {
            JsonNode response = githubOAuth.post()
                    .uri("/login/oauth/access_token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "client_id", clientId,
                            "client_secret", clientSecret,
                            "code", code))
                    .retrieve()
                    .body(JsonNode.class);
            String token = response != null ? response.path("access_token").asText() : "";
            if (token.isBlank()) {
                throw new OpsException(OpsErrorCode.GITHUB_API_FAILED);
            }
            return token;
        } catch (OpsException e) {
            throw e;
        } catch (Exception e) {
            log.error("GitHub 사용자 코드 교환 실패: {}", e.getMessage());
            throw new OpsException(OpsErrorCode.GITHUB_API_FAILED);
        }
    }

    private List<JsonNode> listUserInstallations(String userAccessToken) {
        List<JsonNode> installations = new ArrayList<>();
        int page = 1;
        while (true) {
            JsonNode response = getWithUserToken(
                    "/user/installations?per_page=100&page=" + page, userAccessToken);
            JsonNode items = response.path("installations");
            if (!items.isArray()) {
                break;
            }
            int count = 0;
            for (JsonNode installation : items) {
                installations.add(installation);
                count++;
            }
            if (count < 100) {
                break;
            }
            page++;
        }
        return installations;
    }

    private GithubRepositoryResponse toRepositoryResponse(Long installationId, JsonNode repository) {
        return new GithubRepositoryResponse(
                repository.path("id").asLong(),
                installationId,
                repository.path("full_name").asText(),
                repository.path("clone_url").asText(),
                repository.path("default_branch").asText("main"),
                repository.path("private").asBoolean()
        );
    }

    private String createInstallationToken(Long installationId) {
        try {
            JsonNode response = githubApi.post()
                    .uri("/app/installations/{installationId}/access_tokens", installationId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + createAppJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{}")
                    .retrieve()
                    .body(JsonNode.class);
            String token = response != null ? response.path("token").asText() : "";
            if (token.isBlank()) {
                throw new OpsException(OpsErrorCode.GITHUB_API_FAILED);
            }
            return token;
        } catch (OpsException e) {
            throw e;
        } catch (Exception e) {
            log.error("GitHub installation token 발급 실패: installationId={}, error={}",
                    installationId, e.getMessage());
            throw new OpsException(OpsErrorCode.GITHUB_API_FAILED);
        }
    }

    private JsonNode getWithInstallationToken(String path, String token) {
        try {
            JsonNode body = githubApi.get()
                    .uri(path)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .body(JsonNode.class);
            return body != null ? body : objectMapper.createObjectNode();
        } catch (Exception e) {
            log.error("GitHub installation API 요청 실패: path={}, error={}", path, e.getMessage());
            throw new OpsException(OpsErrorCode.GITHUB_API_FAILED);
        }
    }

    private JsonNode getWithUserToken(String path, String token) {
        try {
            JsonNode body = githubApi.get()
                    .uri(path)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .body(JsonNode.class);
            return body != null ? body : objectMapper.createObjectNode();
        } catch (Exception e) {
            log.error("GitHub 사용자 설치 조회 실패: path={}, error={}", path, e.getMessage());
            throw new OpsException(OpsErrorCode.GITHUB_API_FAILED);
        }
    }

    private String createAppJwt() {
        try {
            Instant now = Instant.now();
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer(appId)
                    .issueTime(Date.from(now.minusSeconds(30)))
                    .expirationTime(Date.from(now.plusSeconds(9 * 60)))
                    .build();
            RSAKey rsaKey = (RSAKey) JWK.parseFromPEMEncodedObjects(privateKeyPem);
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
            jwt.sign(new RSASSASigner(rsaKey.toPrivateKey()));
            return jwt.serialize();
        } catch (Exception e) {
            log.error("GitHub App JWT 생성 실패: {}", e.getMessage());
            throw new OpsException(OpsErrorCode.GITHUB_APP_NOT_CONFIGURED);
        }
    }

    private void requireConfigured() {
        if (appId.isBlank() || appSlug.isBlank() || clientId.isBlank()
                || clientSecret.isBlank() || privateKeyPem.isBlank()) {
            throw new OpsException(OpsErrorCode.GITHUB_APP_NOT_CONFIGURED);
        }
    }

    private static String normalizePem(String value) {
        return value == null ? "" : value.replace("\\n", "\n").trim();
    }
}
