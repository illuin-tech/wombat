package tech.illuin.wombat.endpoint;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.oidc.Claim;
import io.quarkus.test.security.oidc.OidcSecurity;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import io.quarkus.security.identity.SecurityIdentity;
import tech.illuin.wombat.engine.module.ModuleService;
import tech.illuin.wombat.core.asset.type.ActivityRegime;
import tech.illuin.wombat.core.asset.type.AssetType;
import tech.illuin.wombat.core.asset.type.ServiceFamily;
import tech.illuin.wombat.engine.module.library.kubernetes_simulated.KubernetesSimulatedModule;
import tech.illuin.wombat.engine.module.registry.ModuleMetadata;
import tech.illuin.wombat.engine.module.registry.ModuleRegistry;
import tech.illuin.wombat.engine.module.persistence.ModuleEntity;
import tech.illuin.wombat.engine.module.persistence.ModuleRepository;
import tech.illuin.wombat.engine.module.runtime.CoreDeploymentService;
import tech.illuin.wombat.engine.module.runtime.CoreRuntime;
import tech.illuin.wombat.engine.module.registry.schema.AssetSchemaRegistry;
import tech.illuin.wombat.security.Caller;
import tech.illuin.wombat.security.Permission;
import tech.illuin.wombat.security.PermissionAugmentor;
import tech.illuin.wombat.security.Role;
import tech.illuin.wombat.security.apikey.ApiKeyAuthenticationMechanism;
import tech.illuin.wombat.security.apikey.ApiKeyService;
import tech.illuin.wombat.ui.AuthenticationEnabledTest;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@QuarkusTest
@TestProfile(AuthenticationEnabledTest.AuthenticationEnabledProfile.class)
class ModuleControllerTest
{
    private static final String MODULE_ID = KubernetesSimulatedModule.TYPE.name();
    private static final AssetType MISSING_TYPE = AssetType.of("acme", "removed-extension", ActivityRegime.MEASURED, ServiceFamily.LLM);
    private static final String MISSING_ID = MISSING_TYPE.name();

    @Inject
    ModuleRepository repository;

    @Inject
    ApiKeyService apiKeys;

    @Inject
    CoreRuntime runtime;

    @Inject
    CoreDeploymentService deployments;

    /* The registry and the core are shared by the tests of this profile: a test changing them restores them */
    @AfterEach
    void restoreTheRegistryAndTheCore()
    {
        QuarkusTransaction.requiringNew().run(() -> {
            this.repository.findById(MODULE_ID).enabled = true;
            this.repository.deleteById(MISSING_ID);
        });
        if (!this.runtime.current().types().contains(KubernetesSimulatedModule.TYPE))
            this.deployments.redeploy(null);
    }

    @Test
    @TestSecurity(user = "module-reader", roles = "wombat-users", augmentors = PermissionAugmentor.class)
    void list_returnsModuleMetadata()
    {
        given()
            .when().get("/api/modules")
            .then()
            .statusCode(200)
            .body("status.message", is("Success"))
            .body("payload.type.name", hasItem(MODULE_ID))
            .body("payload.find { it.type.name == '" + MODULE_ID + "' }.enabled", is(true));
    }

    @Test
    @TestSecurity(user = "module-reader", roles = "wombat-users", augmentors = PermissionAugmentor.class)
    void list_tellsTheRegistryStateFromTheDeployedOne()
    {
        given()
            .when().get("/api/modules")
            .then()
            .statusCode(200)
            .body("payload.find { it.type.name == '" + MODULE_ID + "' }.deployed", is(true))
            .body("payload.find { it.type.name == '" + MODULE_ID + "' }.pending", is(false))
            .body("payload.find { it.type.name == '" + MODULE_ID + "' }.missing", is(false))
            .body("payload.find { it.type.name == '" + MODULE_ID + "' }.source", is("core"))
            .body("payload.find { it.type.name == '" + MODULE_ID + "' }.assetCount", notNullValue());
    }

    @Test
    @TestSecurity(user = "boss", roles = "wombat-admins", augmentors = PermissionAugmentor.class)
    void update_changesTheRegistryAtOnce_andLeavesTheCoreUntilARedeploy()
    {
        given().contentType(ContentType.JSON).body("{\"enabled\": false}")
            .when().put("/api/modules/" + MODULE_ID)
            .then()
            .statusCode(200)
            .body("payload.enabled", is(false))
            .body("payload.deployed", is(true))
            .body("payload.pending", is(true));

        given()
            .when().get("/api/modules/" + MODULE_ID)
            .then()
            .statusCode(200)
            .body("payload.enabled", is(false))
            .body("payload.pending", is(true));

        assertEquals("boss", QuarkusTransaction.requiringNew().call(() -> this.repository.findById(MODULE_ID).updatedBy));
    }

    @Test
    @TestSecurity(user = "boss", roles = "wombat-admins", augmentors = PermissionAugmentor.class)
    void update_rejectsAPayloadWithoutTheFlag()
    {
        given().contentType(ContentType.JSON).body("{}")
            .when().put("/api/modules/" + MODULE_ID)
            .then()
            .statusCode(400);
    }

    @Test
    @TestSecurity(user = "boss", roles = "wombat-admins", augmentors = PermissionAugmentor.class)
    void aMissingModule_isListed_cannotBeToggled_andCanBeDeleted()
    {
        QuarkusTransaction.requiringNew().run(() -> this.repository.persist(ModuleEntity.registered(MISSING_TYPE)));

        given()
            .when().get("/api/modules")
            .then()
            .statusCode(200)
            .body("payload.find { it.type.name == '" + MISSING_ID + "' }.missing", is(true))
            .body("payload.find { it.type.name == '" + MISSING_ID + "' }.type.family", is("LLM"));
        given().contentType(ContentType.JSON).body("{\"enabled\": false}")
            .when().put("/api/modules/" + MISSING_ID)
            .then()
            .statusCode(409);
        given().when().get("/api/modules/" + MISSING_ID + "/schema").then().statusCode(404);

        given().when().delete("/api/modules/" + MISSING_ID).then().statusCode(204);
        given().when().get("/api/modules/" + MISSING_ID).then().statusCode(404);
    }

    @Test
    @TestSecurity(user = "boss", roles = "wombat-admins", augmentors = PermissionAugmentor.class)
    void aDetectedModule_cannotBeDeleted()
    {
        given().when().delete("/api/modules/" + MODULE_ID).then().statusCode(409);
    }

    @Test
    @TestSecurity(user = "boss", roles = "wombat-admins", augmentors = PermissionAugmentor.class)
    void writesOnAnUnknownModule_returnNotFound()
    {
        given().contentType(ContentType.JSON).body("{\"enabled\": false}")
            .when().put("/api/modules/does-not-exist")
            .then()
            .statusCode(404);
        given().when().delete("/api/modules/does-not-exist").then().statusCode(404);
    }

    @Test
    @TestSecurity(user = "module-reader", roles = "wombat-users", augmentors = PermissionAugmentor.class)
    void writesAreForbiddenToUsers()
    {
        given().contentType(ContentType.JSON).body("{\"enabled\": false}")
            .when().put("/api/modules/" + MODULE_ID)
            .then()
            .statusCode(403);
        given().when().delete("/api/modules/" + MODULE_ID).then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "module-reader", roles = "wombat-users", augmentors = PermissionAugmentor.class)
    void deployment_tellsTheLatestDeployment_andTheInstancesRunningIt()
    {
        given()
            .when().get("/api/modules/deployment")
            .then()
            .statusCode(200)
            .body("payload.latest.version", notNullValue())
            .body("payload.latest.id.length()", is(64))
            .body("payload.latest.modules", hasItem(MODULE_ID))
            .body("payload.instances.find { it.current }.status", is("ok"))
            .body("payload.instances.find { it.current }.detectedModules", hasItem(MODULE_ID))
            .body("payload.pending", empty())
            .body("payload.pollIntervalSeconds", is(30));
    }

    @Test
    @TestSecurity(user = "boss", roles = "wombat-admins", augmentors = PermissionAugmentor.class)
    void aToggleIsPendingUntilARedeploy_whichEveryInstanceThenRuns()
    {
        long before = this.runtime.current().version();
        given().contentType(ContentType.JSON).body("{\"enabled\": false}").when().put("/api/modules/" + MODULE_ID).then().statusCode(200);
        given()
            .when().get("/api/modules/deployment")
            .then()
            .statusCode(200)
            .body("payload.pending.type", contains(MODULE_ID));

        given()
            .when().post("/api/modules/deployment")
            .then()
            .statusCode(201)
            .body("payload.version", is((int) before + 1))
            .body("payload.id.length()", is(64))
            .body("payload.local.status", is("ok"))
            .body("payload.local.removed", contains(MODULE_ID));

        given()
            .when().get("/api/modules/" + MODULE_ID)
            .then()
            .statusCode(200)
            .body("payload.enabled", is(false))
            .body("payload.deployed", is(false))
            .body("payload.pending", is(false));
        given()
            .when().get("/api/modules/deployment")
            .then()
            .statusCode(200)
            .body("payload.pending", empty())
            .body("payload.instances.find { it.current }.deployedVersion", is((int) before + 1));
    }

    @Test
    @TestSecurity(user = "module-reader", roles = "wombat-users", augmentors = PermissionAugmentor.class)
    void aRedeployIsForbiddenToUsers()
    {
        given().when().post("/api/modules/deployment").then().statusCode(403);
    }

    @Test
    void aKey_togglesModulesOnlyWithTheManagePermission()
    {
        String manager = this.issue(Permission.Names.API_MODULE_MANAGE, Permission.Names.API_MODULE_READ);
        String reader = this.issue(Permission.Names.API_MODULE_READ);

        given().header(ApiKeyAuthenticationMechanism.HEADER, reader).contentType(ContentType.JSON).body("{\"enabled\": false}")
            .when().put("/api/modules/" + MODULE_ID)
            .then()
            .statusCode(403);
        given().header(ApiKeyAuthenticationMechanism.HEADER, manager).contentType(ContentType.JSON).body("{\"enabled\": false}")
            .when().put("/api/modules/" + MODULE_ID)
            .then()
            .statusCode(200)
            .body("payload.enabled", is(false));
    }

    @Test
    @TestSecurity(user = "module-reader", roles = "wombat-users", augmentors = PermissionAugmentor.class)
    void find_returnsModuleMetadata()
    {
        given()
            .when().get("/api/modules/" + MODULE_ID)
            .then()
            .statusCode(200)
            .body("status.message", is("Success"))
            .body("payload.type.name", is(MODULE_ID))
            .body("payload.enabled", is(true))
            .body("payload.creatable", is(false));
    }

    @Test
    @TestSecurity(user = "module-reader", roles = "wombat-users", augmentors = PermissionAugmentor.class)
    void getSchema_returnsSerializedSchemaJson()
    {
        given()
            .when().get("/api/modules/" + MODULE_ID + "/schema")
            .then()
            .statusCode(200)
            .body("status.message", is("Success"))
            .body("payload.$schema", notNullValue())
            .body("payload.properties.activity", notNullValue());
    }

    @Test
    @TestSecurity(user = "module-reader", roles = "wombat-users", augmentors = PermissionAugmentor.class)
    void unknownModule_returnsNotFound()
    {
        given()
            .when().get("/api/modules/does-not-exist")
            .then()
            .statusCode(404);
    }

    @Test
    @TestSecurity(user = "module-reader", roles = "wombat-users", augmentors = PermissionAugmentor.class)
    void schemaForUnknownModule_returnsNotFound()
    {
        given()
            .when().get("/api/modules/does-not-exist/schema")
            .then()
            .statusCode(404);
    }

    @Test
    @TestSecurity(user = "someone", roles = "unrelated", augmentors = PermissionAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "email", value = "someone@example.com"))
    void moduleEndpointsAreForbiddenWithoutTheRoleGrantedPermission()
    {
        given().when().get("/api/modules").then().statusCode(403);
        given().when().get("/api/modules/" + MODULE_ID).then().statusCode(403);
        given().when().get("/api/modules/" + MODULE_ID + "/schema").then().statusCode(403);
    }

    @Test
    void getSchema_forKnownModuleWithoutSchema_throwsNotFound()
    {
        KubernetesSimulatedModule module = new KubernetesSimulatedModule();
        ModuleRegistry registry = mock(ModuleRegistry.class);
        AssetSchemaRegistry schemaRegistry = mock(AssetSchemaRegistry.class);
        when(registry.getModule(MODULE_ID)).thenReturn(Optional.of(ModuleMetadata.core(module.type(), module.assetClass(), true)));
        when(schemaRegistry.getSchema(module.type())).thenReturn(Optional.empty());
        ModuleController controller = new ModuleController(mock(ModuleService.class), registry, schemaRegistry, mock(SecurityIdentity.class));

        NotFoundException error = assertThrows(NotFoundException.class, () -> controller.getSchema(MODULE_ID));

        assertEquals("No module could be found for id " + MODULE_ID, error.getMessage());
    }

    private String issue(String... permissions)
    {
        Caller admin = new Caller("https://idp", UUID.randomUUID().toString(), "admin", "admin@example.com", EnumSet.of(Role.ADMIN), Role.ADMIN.permissions());
        return this.apiKeys.create(admin, "modules", List.of(permissions), Instant.now().plus(Duration.ofDays(1))).token();
    }
}