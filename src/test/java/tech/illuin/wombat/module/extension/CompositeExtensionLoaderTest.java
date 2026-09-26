package tech.illuin.wombat.module.extension;

import org.junit.jupiter.api.Test;
import tech.illuin.wombat.core.module.WombatModule;
import tech.illuin.wombat.module.kubernetes_simulated.KubernetesSimulatedModule;
import tech.illuin.wombat.module.llm_simulated.LLMSimulatedModule;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CompositeExtensionLoaderTest
{

    @Test
    void aggregatesModulesFromMultipleLoadersInOrder()
    {
        ExtensionLoader loader1 = mock(ExtensionLoader.class);
        ExtensionLoader loader2 = mock(ExtensionLoader.class);

        WombatModule mod1 = new KubernetesSimulatedModule();
        WombatModule mod2 = new LLMSimulatedModule();

        when(loader1.load()).thenReturn(List.of(mod1));
        when(loader2.load()).thenReturn(List.of(mod2));

        CompositeExtensionLoader composite = new CompositeExtensionLoader(loader1, loader2);
        List<WombatModule> result = composite.load();

        assertEquals(List.of(mod1, mod2), result);
    }

    @Test
    void propagatesCloseToAllUnderlyingLoaders() throws IOException
    {
        ExtensionLoader loader1 = mock(ExtensionLoader.class);
        ExtensionLoader loader2 = mock(ExtensionLoader.class);

        CompositeExtensionLoader composite = new CompositeExtensionLoader(List.of(loader1, loader2));
        composite.close();

        verify(loader1).close();
        verify(loader2).close();
    }
}
