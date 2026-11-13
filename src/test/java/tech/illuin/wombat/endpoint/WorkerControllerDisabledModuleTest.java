package tech.illuin.wombat.endpoint;

import com.fasterxml.jackson.databind.json.JsonMapper;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tech.illuin.wombat.core.activity.commons.Country;
import tech.illuin.wombat.core.asset.AssetIdentity;
import tech.illuin.wombat.core.asset.Environment;
import tech.illuin.wombat.core.asset.profile.Lifetime;
import tech.illuin.wombat.core.asset.profile.ServerProvider;
import tech.illuin.wombat.core.source.Period;
import tech.illuin.wombat.engine.context.persistence.AssetRepository;
import tech.illuin.wombat.engine.module.registry.ModuleMetadata;
import tech.illuin.wombat.engine.module.registry.ModuleRegistry;
import tech.illuin.wombat.engine.monitor.MonitoredEnvironments;
import tech.illuin.wombat.module.kubernetes_api.KubernetesAPIAsset;
import tech.illuin.wombat.module.kubernetes_api.KubernetesAPIModule;
import tech.illuin.wombat.module.kubernetes_api.KubernetesAPIServerProfile;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** An aggregator which disabled the kubernetes-api module: its assets are still declared, their metrics are skipped when pushed. */
@QuarkusTest
@TestProfile(DisabledModuleProfile.class)
class WorkerControllerDisabledModuleTest
{
    private static final Country FRANCE = Country.ofCode("FRA").orElseThrow();

    /* What a worker declares its environments with. */
    @Inject
    JsonMapper assetMapper;

    @Inject
    AssetRepository assets;

    @Inject
    ModuleRegistry modules;

    @AfterEach
    void tearDown() throws Exception
    {
        this.declare(new MonitoredEnvironments(Map.of()));
    }

    @Test
    void declare_acceptsAnAssetWhoseModuleIsDisabled() throws Exception
    {
        assertFalse(this.modules.getModule(KubernetesAPIModule.TYPE).map(ModuleMetadata::enabled).orElseThrow());

        this.declare(new MonitoredEnvironments(Map.of("east", new Environment("east", List.of(cluster("east", "east-disabled"))))));

        assertEquals("east", this.assets.findById("east.east-disabled").owner);
    }

    private void declare(MonitoredEnvironments environments) throws Exception
    {
        given().contentType(ContentType.JSON).body(this.assetMapper.writeValueAsString(environments))
            .when().put("/api/workers/east/environments")
            .then().statusCode(200);
    }

    private static KubernetesAPIAsset cluster(String environment, String id)
    {
        return new KubernetesAPIAsset(
            AssetIdentity.of(id, environment, id, KubernetesAPIModule.TYPE),
            "kubeconfig/" + environment + ".config.yaml", "default", Optional.empty(), Optional.empty(), Period.ZERO,
            new KubernetesAPIServerProfile(ServerProvider.aws, "c5.large", FRANCE, Lifetime.ofYears(5))
        );
    }
}
