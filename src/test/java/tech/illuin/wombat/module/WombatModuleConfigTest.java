package tech.illuin.wombat.module;

import org.junit.jupiter.api.Test;
import tech.illuin.wombat.core.module.WombatModule;
import tech.illuin.wombat.impact.kubernetes.KubernetesMetricRepository;
import tech.illuin.wombat.impact.llm.LLMModelMetricRepository;
import tech.illuin.wombat.module.api.ModuleRegistration;
import tech.illuin.wombat.module.kubernetes_api.KubernetesAPIModule;
import tech.illuin.wombat.module.llm_prometheus.LLMPrometheusModule;
import tech.illuin.wombat.module.llm_static.LLMStaticModule;
import tech.illuin.wombat.persistence.dialect.JsonPathDialect;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the producers directly rather than through CDI: the toggles are plain logic over
 * {@link WombatModuleProperties}, and the collaborators are never touched during instantiation.
 */
class WombatModuleConfigTest
{

    private final WombatModuleConfig config = new WombatModuleConfig();

    @Test
    void enabledByDefault_everyModuleIsRegistered()
    {
        assertTrue(this.kubernetes(all(true)).enabled());
        assertTrue(this.llmPrometheus(all(true)).enabled());
        assertTrue(this.config.provideLLMStaticModule(all(true)).enabled());
    }

    @Test
    void kubernetesApiDisabled_yieldsADisabledRegistrationCarryingNoModule()
    {
        ModuleRegistration registration = this.kubernetes(only("kubernetes-api", false));

        assertEquals("kubernetes-api", registration.id());
        assertFalse(registration.enabled());
        assertTrue(registration.modules().isEmpty());
    }

    @Test
    void llmPrometheusDisabled_yieldsADisabledRegistrationCarryingNoModule()
    {
        ModuleRegistration registration = this.llmPrometheus(only("llm-prometheus", false));

        assertEquals("llm-prometheus", registration.id());
        assertFalse(registration.enabled());
        assertTrue(registration.modules().isEmpty());
    }

    @Test
    void llmStaticDisabled_yieldsADisabledRegistrationCarryingNoModule()
    {
        ModuleRegistration registration = this.config.provideLLMStaticModule(only("llm-static", false));

        assertEquals("llm-static", registration.id());
        assertFalse(registration.enabled());
        assertTrue(registration.modules().isEmpty());
    }

    @Test
    void assembledModules_containOnlyTheEnabledOnes()
    {
        WombatModuleProperties properties = only("llm-prometheus", false);

        List<WombatModule> modules = this.config.provideWombatModules(List.of(
            this.kubernetes(properties),
            this.llmPrometheus(properties),
            this.config.provideLLMStaticModule(properties)
        ));

        assertEquals(2, modules.size());
        assertTrue(modules.stream().anyMatch(KubernetesAPIModule.class::isInstance));
        assertTrue(modules.stream().anyMatch(LLMStaticModule.class::isInstance));
        assertFalse(modules.stream().anyMatch(LLMPrometheusModule.class::isInstance));
    }

    @Test
    void assembledModules_areEmptyWhenEverythingIsDisabled()
    {
        WombatModuleProperties properties = all(false);

        List<WombatModule> modules = this.config.provideWombatModules(List.of(
            this.kubernetes(properties),
            this.llmPrometheus(properties),
            this.config.provideLLMStaticModule(properties)
        ));

        assertTrue(modules.isEmpty());
    }

    private ModuleRegistration kubernetes(WombatModuleProperties properties)
    {
        return this.config.provideKubernetesAPIModule(properties);
    }

    private ModuleRegistration llmPrometheus(WombatModuleProperties properties)
    {
        return this.config.provideLLMPrometheusModule(properties);
    }

    private static WombatModuleProperties all(boolean enabled)
    {
        return properties(enabled, enabled, enabled);
    }

    /** Every module enabled except the named one, so a test only states the toggle it cares about. */
    private static WombatModuleProperties only(String disabledModule, boolean enabled)
    {
        return properties(
            !"kubernetes-api".equals(disabledModule) || enabled,
            !"llm-prometheus".equals(disabledModule) || enabled,
            !"llm-static".equals(disabledModule) || enabled
        );
    }

    private static WombatModuleProperties properties(boolean kubernetesApi, boolean llmPrometheus, boolean llmStatic)
    {
        return new WombatModuleProperties()
        {
            @Override
            public ModuleProperties kubernetesApi()
            {
                return () -> kubernetesApi;
            }

            @Override
            public ModuleProperties llmPrometheus()
            {
                return () -> llmPrometheus;
            }

            @Override
            public ModuleProperties llmStatic()
            {
                return () -> llmStatic;
            }
        };
    }
}
