package tech.illuin.wombat.engine.secret.vault;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

public class VaultTestResource implements QuarkusTestResourceLifecycleManager
{
    public static final String PATH_PREFIX = "wombat-integration";

    private static final String ROOT_TOKEN = "wombat-integration-root";
    private static final String POLICY_NAME = "wombat-integration";
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final HttpClient httpClient = HttpClient.newHttpClient();

    private GenericContainer<?> vault;
    private Path resolverDirectory;

    @Override
    public Map<String, String> start()
    {
        this.vault = new GenericContainer<>(DockerImageName.parse("hashicorp/vault:1.17.5"))
            .withExposedPorts(8200)
            .withEnv("VAULT_DEV_ROOT_TOKEN_ID", ROOT_TOKEN)
            .withEnv("VAULT_DEV_LISTEN_ADDRESS", "0.0.0.0:8200")
            .withCommand("server", "-dev")
            .waitingFor(Wait.forHttp("/v1/sys/health").forPort(8200).forStatusCode(200))
            .withStartupTimeout(Duration.ofSeconds(90));
        this.vault.start();

        String vaultUrl = "http://%s:%d".formatted(this.vault.getHost(), this.vault.getMappedPort(8200));
        String scopedToken = createScopedToken(vaultUrl);
        provisionManualSecret(vaultUrl);
        createLowerPriorityResolverSources();

        return Map.of(
            "quarkus.vault.url", vaultUrl,
            "quarkus.vault.authentication.client-token", scopedToken,
            "quarkus.vault.kv-secret-engine-mount-path", "secret",
            "quarkus.vault.devservices.enabled", "false",
            "wombat.secret.directory.path", this.resolverDirectory.toString()
        );
    }

    @Override
    public void stop()
    {
        if (this.vault != null)
            this.vault.stop();
        if (this.resolverDirectory != null)
        {
            try
            {
                Files.deleteIfExists(this.resolverDirectory.resolve("priority"));
                Files.deleteIfExists(this.resolverDirectory.resolve("fallback-only"));
                Files.deleteIfExists(this.resolverDirectory);
            }
            catch (Exception exception)
            {
                throw new IllegalStateException("Could not clean the Vault resolver test sources", exception);
            }
        }
    }

    private void createLowerPriorityResolverSources()
    {
        try
        {
            this.resolverDirectory = Files.createTempDirectory(Path.of("target"), "vault-secret-resolver-");
            Files.writeString(this.resolverDirectory.resolve("priority"), "directory-priority");
            Files.writeString(this.resolverDirectory.resolve("fallback-only"), "directory-fallback");
        }
        catch (Exception exception)
        {
            throw new IllegalStateException("Could not prepare lower-priority Vault resolver sources", exception);
        }
    }

    private static String createScopedToken(String vaultUrl)
    {
        try
        {
            String policy = """
                path "secret/data/%s/*" {
                  capabilities = ["create", "update", "read"]
                }
                path "secret/delete/%s/*" {
                  capabilities = ["update"]
                }
                path "secret/metadata/%s" {
                  capabilities = ["list"]
                }
                path "secret/metadata/%s/*" {
                  capabilities = ["list", "read"]
                }
                """.formatted(PATH_PREFIX, PATH_PREFIX, PATH_PREFIX, PATH_PREFIX);
            request(vaultUrl, "PUT", "sys/policies/acl/" + POLICY_NAME, Map.of("policy", policy), ROOT_TOKEN);

            String response = request(vaultUrl, "POST", "auth/token/create", Map.of(
                "policies", new String[]{POLICY_NAME},
                "ttl", "1h",
                "renewable", false
            ), ROOT_TOKEN);
            JsonNode token = objectMapper.readTree(response).path("auth").path("client_token");
            if (!token.isTextual() || token.asText().isBlank())
                throw new IllegalStateException("Vault test setup did not return an application token");
            return token.asText();
        }
        catch (Exception exception)
        {
            throw new IllegalStateException("Could not configure the scoped Vault test identity", exception);
        }
    }

    private static void provisionManualSecret(String vaultUrl)
    {
        try
        {
            request(vaultUrl, "POST", "secret/data/" + PATH_PREFIX + "/manual/seed", Map.of(
                "data", Map.of("value", "manually-provisioned")
            ), ROOT_TOKEN);
        }
        catch (Exception exception)
        {
            throw new IllegalStateException("Could not provision the manual Vault test secret", exception);
        }
    }

    private static String request(String vaultUrl, String method, String path, Object body, String token) throws Exception
    {
        HttpRequest request = HttpRequest.newBuilder(URI.create(vaultUrl + "/v1/" + path))
            .header("X-Vault-Token", token)
            .header("Content-Type", "application/json")
            .method(method, HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
            .timeout(Duration.ofSeconds(10))
            .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300)
            throw new IllegalStateException("Vault test setup request failed with HTTP " + response.statusCode());
        return response.body();
    }
}
