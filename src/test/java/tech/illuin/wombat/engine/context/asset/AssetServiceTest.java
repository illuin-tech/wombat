package tech.illuin.wombat.engine.context.asset;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.illuin.wombat.engine.context.asset.persistence.AssetConfigAction;
import tech.illuin.wombat.engine.context.asset.persistence.AssetConfigHistoryRepository;
import tech.illuin.wombat.engine.context.asset.persistence.AssetEntity;
import tech.illuin.wombat.engine.context.asset.persistence.AssetRepository;
import tech.illuin.wombat.engine.context.environment.EnvironmentConflictException;
import tech.illuin.wombat.engine.context.environment.EnvironmentService;
import tech.illuin.wombat.engine.context.environment.UnknownEnvironmentException;
import tech.illuin.wombat.engine.context.environment.persistence.EnvironmentRepository;
import tech.illuin.wombat.module.llm_static.LLMStaticAsset;
import tech.illuin.wombat.module.llm_static.LLMStaticModule;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Against the real mapper, validator and database, since an asset is read from its JSON properties.
 * The test profile provisions the environment {@code test}, holding the asset {@code test-llm}.
 */
@QuarkusTest
class AssetServiceTest
{
    private static final String ENVIRONMENT = "asset-service-env";
    private static final String TYPE = LLMStaticModule.TYPE.name();

    @Inject AssetService service;
    @Inject EnvironmentService environments;
    @Inject AssetRepository assetRepository;
    @Inject EnvironmentRepository environmentRepository;
    @Inject AssetConfigHistoryRepository historyRepository;
    @Inject JsonMapper mapper;

    @BeforeEach
    void setUp()
    {
        this.clean();
        this.environments.create(ENVIRONMENT, "Asset service", null, false);
    }

    @AfterEach
    void clean()
    {
        QuarkusTransaction.requiringNew().run(() -> {
            for (String id : List.of("user-llm", "other-llm"))
            {
                this.assetRepository.findByIdOptional(id).ifPresent(this.assetRepository::delete);
                this.historyRepository.findByAsset(id).forEach(this.historyRepository::delete);
            }
            this.environmentRepository.findByIdOptional(ENVIRONMENT).ifPresent(this.environmentRepository::delete);
        });
    }

    @Test
    void create_readsTheAssetFromItsProperties_andHistorizesIt()
    {
        AssetEntity created = this.create("user-llm", ENVIRONMENT);

        AssetEntity found = QuarkusTransaction.requiringNew().call(() -> this.assetRepository.findById("user-llm"));
        assertFalse(found.provisioned);
        assertNull(found.owner);
        assertEquals("User LLM", found.name);
        assertEquals("Described", found.description);
        assertEquals(TYPE, found.type);
        LLMStaticAsset asset = assertInstanceOf(LLMStaticAsset.class, found.properties);
        assertEquals("user-llm", asset.identity().id());
        assertEquals(ENVIRONMENT, asset.identity().environmentId());
        assertEquals(created.uuid, found.uuid);
        assertEquals(List.of(AssetConfigAction.CREATE), this.actions("user-llm"));
    }

    @Test
    void create_canTargetAProvisionedEnvironment()
    {
        assertEquals("test", this.create("user-llm", "test").environmentId);
    }

    @Test
    void create_failsOnAnUnknownEnvironment()
    {
        assertThrows(UnknownEnvironmentException.class, () -> this.create("user-llm", "nowhere"));
    }

    @Test
    void create_failsOnAnExistingAsset_provisionedOrNot()
    {
        this.create("user-llm", ENVIRONMENT);

        assertThrows(AssetConflictException.class, () -> this.create("user-llm", ENVIRONMENT));
        assertThrows(AssetConflictException.class, () -> this.create("test-llm", ENVIRONMENT));
    }

    @Test
    void create_failsOnAnUnknownType()
    {
        ObjectNode properties = this.properties();

        assertThrows(InvalidAssetException.class, () -> this.service.create("user-llm", ENVIRONMENT, "User LLM", "tech.illuin.unknown", null, false, properties));
    }

    @Test
    void create_failsOnInvalidProperties()
    {
        ObjectNode properties = this.mapper.createObjectNode();
        properties.putObject("profile").put("models", "not-a-list");

        assertThrows(InvalidAssetException.class, () -> this.service.create("user-llm", ENVIRONMENT, "User LLM", TYPE, null, false, properties));
    }

    @Test
    void create_takesOverASoftDeletedAsset()
    {
        String uuid = this.create("user-llm", ENVIRONMENT).uuid;
        this.service.delete("user-llm");

        AssetEntity created = this.create("user-llm", ENVIRONMENT);

        assertEquals(uuid, created.uuid);
        assertNull(created.deletedAt);
        assertEquals(List.of(AssetConfigAction.CREATE, AssetConfigAction.DELETE, AssetConfigAction.CREATE), this.actions("user-llm"));
    }

    @Test
    void update_replacesTheEditableFields_keepingTheIdentity()
    {
        this.create("user-llm", ENVIRONMENT);

        this.service.update("user-llm", "Renamed", null, true, this.properties());

        AssetEntity found = QuarkusTransaction.requiringNew().call(() -> this.assetRepository.findById("user-llm"));
        assertEquals("Renamed", found.name);
        assertEquals("Renamed", found.properties.identity().name());
        assertEquals(ENVIRONMENT, found.environmentId);
        assertNull(found.description);
        assertNotNull(found.disabledAt);
        assertEquals(List.of(AssetConfigAction.CREATE, AssetConfigAction.UPDATE), this.actions("user-llm"));
    }

    @Test
    void update_onlyHistorizesChangedProperties()
    {
        this.create("user-llm", ENVIRONMENT);

        this.service.update("user-llm", "User LLM", "Other", true, this.properties());

        assertEquals(List.of(AssetConfigAction.CREATE), this.actions("user-llm"));
    }

    @Test
    void updateAndDelete_failOnAProvisionedAsset()
    {
        assertThrows(AssetConflictException.class, () -> this.service.update("test-llm", "Test LLM", null, false, this.properties()));
        assertThrows(AssetConflictException.class, () -> this.service.delete("test-llm"));
    }

    @Test
    void delete_hidesTheAsset_andUnblocksItsEnvironment()
    {
        this.create("user-llm", ENVIRONMENT);
        assertThrows(EnvironmentConflictException.class, () -> this.environments.delete(ENVIRONMENT));

        this.service.delete("user-llm");

        assertThrows(UnknownAssetException.class, () -> this.service.get("user-llm"));
        assertTrue(this.service.list(Optional.of(ENVIRONMENT)).isEmpty());
        this.environments.delete(ENVIRONMENT);
        assertThrows(UnknownEnvironmentException.class, () -> this.environments.get(ENVIRONMENT));
    }

    @Test
    void list_filtersByEnvironment()
    {
        this.create("user-llm", ENVIRONMENT);
        this.create("other-llm", "test");

        assertEquals(List.of("user-llm"), this.service.list(Optional.of(ENVIRONMENT)).stream().map(a -> a.id).toList());
        assertTrue(this.service.list(Optional.empty()).stream().map(a -> a.id).toList().containsAll(List.of("user-llm", "other-llm", "test-llm")));
        assertThrows(UnknownEnvironmentException.class, () -> this.service.list(Optional.of("nowhere")));
    }

    private AssetEntity create(String id, String environmentId)
    {
        return this.service.create(id, environmentId, "User LLM", TYPE, "Described", false, this.properties());
    }

    /** The type-specific properties of the provisioned llm-static asset, as an API caller would send them. */
    private ObjectNode properties()
    {
        AssetEntity provisioned = QuarkusTransaction.requiringNew().call(() -> this.assetRepository.findById("test-llm"));
        ObjectNode node = this.mapper.valueToTree(provisioned.properties);
        node.remove(List.of("id", "environment-id", "environmentId", "name", "type"));
        return node;
    }

    private List<AssetConfigAction> actions(String id)
    {
        return QuarkusTransaction.requiringNew().call(() -> this.historyRepository.findByAsset(id)).stream()
            .map(history -> history.action)
            .toList();
    }
}
