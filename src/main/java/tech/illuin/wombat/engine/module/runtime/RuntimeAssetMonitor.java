package tech.illuin.wombat.engine.module.runtime;

import tech.illuin.wombat.core.asset.AssetIdentity;
import tech.illuin.wombat.core.asset.type.ModuleType;
import tech.illuin.wombat.core.module.ProcessorFactory;
import tech.illuin.wombat.core.source.AssetMonitor;
import tech.illuin.wombat.core.source.WombatSource;

/**
 * The app's {@link AssetMonitor}: each call runs on the monitor of the current {@link CoreDeployment}. A subclass
 * because the core monitor is a class; every public method is overridden, so the parent's own state is never used.
 */
public class RuntimeAssetMonitor extends AssetMonitor
{
    private final CoreRuntime runtime;

    public RuntimeAssetMonitor(CoreRuntime runtime)
    {
        super(null, null, null);
        this.runtime = runtime;
    }

    @Override
    public void trigger()
    {
        this.runtime.current().monitor().trigger();
    }

    @Override
    public void refresh(AssetIdentity identity)
    {
        this.runtime.current().monitor().refresh(identity);
    }

    /** Sources are registered by {@link CoreDeploymentFactory}, from the deployed modules. */
    @Override
    public AssetMonitor register(ModuleType type, ProcessorFactory<WombatSource> factory)
    {
        throw new UnsupportedOperationException("Sources come from the deployed modules");
    }

    /** Only closes the parent's unused executor: the deployments are closed by {@link CoreRuntime}. */
    @Override
    public void close()
    {
        super.close();
    }
}
