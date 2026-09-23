package tech.illuin.wombat.module;

import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.arc.All;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Disposes;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import tech.illuin.wombat.core.WombatCore;
import tech.illuin.wombat.core.context.WombatContextProvider;
import tech.illuin.wombat.core.evaluation.AssetEvaluator;
import tech.illuin.wombat.core.module.WombatModule;
import tech.illuin.wombat.core.secret.SecretResolver;
import tech.illuin.wombat.core.source.persistence.WombatMetricPersister;
import tech.illuin.wombat.core.source.persistence.micrometer.MicrometerMetricPersister;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Assembles the SDK entry point out of the beans the app provides: the context to resolve, the impact-resolvers the
 * modules draw on, the persister sourced metrics are written through, and the modules {@link WombatModuleConfig}
 * decided to wire.
 */
@ApplicationScoped
public class WombatCoreConfig
{
    @Singleton
    public SecretResolver provideSecretResolver(
        @ConfigProperty(name = "wombat.secret.directory.path") Optional<String> directoryPath,
        @ConfigProperty(name = "wombat.secret.keystore.path") Optional<String> keyStorePath,
        @ConfigProperty(name = "wombat.secret.keystore.password") Optional<String> keyStorePassword,
        @ConfigProperty(name = "wombat.secret.keystore.type", defaultValue = "PKCS12") String keyStoreType
    ) {
        List<SecretResolver> resolvers = new ArrayList<>();
        if (keyStorePath.isPresent() && !keyStorePath.get().isBlank())
        {
            var builder = SecretResolver.keyStoreBuilder()
                .type(keyStoreType);
            keyStorePassword.ifPresent(builder::defaultKeyPassword);
            SecretResolver keyStoreResolver = builder
                .load(Path.of(keyStorePath.get()), keyStorePassword.orElse(null))
                .build();
            resolvers.add(keyStoreResolver);
        }
        if (directoryPath.isPresent() && !directoryPath.get().isBlank())
            resolvers.add(SecretResolver.directory(Path.of(directoryPath.get())));
        resolvers.add(SecretResolver.environment());

        return resolvers.size() == 1 ? resolvers.getFirst() : SecretResolver.composite(resolvers);
    }

    @Singleton
    public WombatMetricPersister provideMetricPersister(MeterRegistry registry)
    {
        // Routes each metric to the mapper matching its service-family, onto the registry WombatStepMeterRegistry
        // drains into the metric repositories the impact-resolvers read back from.
        return new MicrometerMetricPersister(registry);
    }

    @Singleton
    public WombatCore provideWombatCore(
        WombatContextProvider contextProvider,
        WombatMetricPersister persister,
        List<WombatModule> modules,
        @All List<WombatModuleConfig.DefaultHandlers> defaultHandlers
    ) {
        return new WombatCore(contextProvider, persister, modules, defaults -> defaultHandlers.forEach(
            dh -> defaults.register(
                dh.family(),
                dh.activityResolver(),
                dh.impactResolver(),
                dh.costResolver())
            )
        );
    }

    /**
     * Resolvers are registered per asset-type at creation, so a calculator keeps serving assets added later as long
     * as their type is already wired; a brand-new asset-type needs a fresh one.
     */
    @Singleton
    public AssetEvaluator provideImpactCalculator(WombatCore core)
    {
        return core.createEvaluator();
    }

    public void closeWombatCore(@Disposes WombatCore core)
    {
        core.close();
    }
}
