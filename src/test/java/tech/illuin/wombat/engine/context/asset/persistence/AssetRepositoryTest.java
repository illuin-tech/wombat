package tech.illuin.wombat.engine.context.asset.persistence;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.illuin.wombat.engine.context.environment.persistence.EnvironmentEntity;
import tech.illuin.wombat.engine.context.environment.persistence.EnvironmentRepository;
import tech.illuin.wombat.core.asset.AssetIdentity;
import tech.illuin.wombat.module.llm_static.LLMStaticAsset;
import tech.illuin.wombat.module.llm_static.LLMStaticModule;
import tech.illuin.wombat.module.llm_static.LLMStaticProfile;
import tech.illuin.wombat.module.llm_static.scenario.ConstantScenario;
import tech.illuin.wombat.core.asset.profile.LLMProvider;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static tech.illuin.wombat.core.activity.commons.Country.FRANCE;

@QuarkusTest
class AssetRepositoryTest
{
    private static final String ENVIRONMENT = "asset-repo-env";
    private static final List<String> ASSETS = List.of("asset-active", "asset-disabled", "asset-deleted");

    @Inject AssetRepository repository;
    @Inject EnvironmentRepository environments;

    @BeforeEach
    void setUp()
    {
        this.clean();
        QuarkusTransaction.requiringNew().run(() -> {
            EnvironmentEntity environment = new EnvironmentEntity();
            environment.id = ENVIRONMENT;
            environment.name = ENVIRONMENT;
            this.environments.persist(environment);

            AssetEntity active = asset("asset-active", null, null);
            active.description = "Described";
            this.repository.persist(active);
            this.repository.persist(asset("asset-disabled", Instant.now(), null));
            this.repository.persist(asset("asset-deleted", null, Instant.now()));
        });
    }

    @AfterEach
    void clean()
    {
        QuarkusTransaction.requiringNew().run(() -> {
            for (String id : ASSETS)
                this.repository.findByIdOptional(id).ifPresent(this.repository::delete);
            this.environments.findByIdOptional(ENVIRONMENT).ifPresent(this.environments::delete);
        });
    }

    @Test
    void newColumnsRoundTrip()
    {
        AssetEntity found = QuarkusTransaction.requiringNew().call(() -> this.repository.findById("asset-active"));

        assertEquals("Described", found.description);
        assertTrue(found.provisioned);
        assertNull(found.disabledAt);
        assertNull(found.deletedAt);
    }

    @Test
    void byEnvironment_processingSkipsDisabled_apiKeepsThem()
    {
        List<String> active = QuarkusTransaction.requiringNew().call(() -> this.repository.findByEnvironment(ENVIRONMENT)).stream().map(a -> a.id).toList();
        List<String> visible = QuarkusTransaction.requiringNew().call(() -> this.repository.findVisibleByEnvironment(ENVIRONMENT)).stream().map(a -> a.id).toList();

        assertEquals(List.of("asset-active"), active);
        assertEquals(List.of("asset-active", "asset-disabled"), visible.stream().sorted().toList());
        assertEquals(2L, QuarkusTransaction.requiringNew().call(() -> this.repository.countVisibleByEnvironment(ENVIRONMENT)));
    }

    @Test
    void visibleLookupsIgnoreDeleted()
    {
        AssetEntity deleted = QuarkusTransaction.requiringNew().call(() -> this.repository.findById("asset-deleted"));
        AssetEntity disabled = QuarkusTransaction.requiringNew().call(() -> this.repository.findById("asset-disabled"));
        List<String> visible = QuarkusTransaction.requiringNew().call(() -> this.repository.findVisible()).stream().map(a -> a.id).toList();

        assertTrue(visible.containsAll(List.of("asset-active", "asset-disabled")));
        assertFalse(visible.contains("asset-deleted"));
        assertTrue(QuarkusTransaction.requiringNew().call(() -> this.repository.findVisibleById("asset-deleted")).isEmpty());
        assertTrue(QuarkusTransaction.requiringNew().call(() -> this.repository.findVisibleByUuid(deleted.uuid)).isEmpty());
        assertEquals("asset-disabled", QuarkusTransaction.requiringNew().call(() -> this.repository.findVisibleByUuid(disabled.uuid).orElseThrow()).id);
    }

    private static AssetEntity asset(String id, Instant disabledAt, Instant deletedAt)
    {
        AssetEntity entity = AssetEntity.from(ENVIRONMENT, new LLMStaticAsset(
            AssetIdentity.of(id, ENVIRONMENT, LLMStaticModule.TYPE),
            new LLMStaticProfile(LLMProvider.mistralai, "m", FRANCE, 1000, 500, List.of(new ConstantScenario()))
        ));
        entity.disabledAt = disabledAt;
        entity.deletedAt = deletedAt;
        return entity;
    }
}
