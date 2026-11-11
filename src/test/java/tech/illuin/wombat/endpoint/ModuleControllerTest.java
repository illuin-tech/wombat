package tech.illuin.wombat.endpoint;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.oidc.Claim;
import io.quarkus.test.security.oidc.OidcSecurity;
import jakarta.ws.rs.NotFoundException;
import org.junit.jupiter.api.Test;
import tech.illuin.wombat.engine.module.library.kubernetes_simulated.KubernetesSimulatedModule;
import tech.illuin.wombat.engine.module.registry.ModuleMetadata;
import tech.illuin.wombat.engine.module.registry.ModuleRegistry;
import tech.illuin.wombat.engine.module.registry.schema.AssetSchemaRegistry;
import tech.illuin.wombat.security.PermissionAugmentor;
import tech.illuin.wombat.ui.AuthenticationEnabledTest;

import java.util.Optional;

import static io.restassured.RestAssured.given;
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
    void find_returnsModuleMetadata()
    {
        given()
            .when().get("/api/modules/" + MODULE_ID)
            .then()
            .statusCode(200)
            .body("status.message", is("Success"))
            .body("payload.type.name", is(MODULE_ID))
            .body("payload.enabled", is(true));
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
        when(registry.getModule(MODULE_ID)).thenReturn(Optional.of(new ModuleMetadata(module.type(), module.assetClass(), true)));
        when(schemaRegistry.getSchema(module.type())).thenReturn(Optional.empty());
        ModuleController controller = new ModuleController(registry, schemaRegistry);

        NotFoundException error = assertThrows(NotFoundException.class, () -> controller.getSchema(MODULE_ID));

        assertEquals("No module could be found for id " + MODULE_ID, error.getMessage());
    }
}