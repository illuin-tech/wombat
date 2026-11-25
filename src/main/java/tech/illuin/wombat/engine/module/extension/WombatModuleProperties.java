package tech.illuin.wombat.engine.module.extension;

import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

public interface WombatModuleProperties
{
    @WithName("extension")
    ExtensionProperties extension();

    @WithName("deployment")
    DeploymentProperties deployment();

    /**
     * The settings of the modules, by module name then property path, read by
     * {@link tech.illuin.wombat.engine.module.settings.ModuleSettingsReader}. An environment variable overrides a key
     * another source sets; alone, SmallRye cannot tell where its dots go.
     */
    @WithName("settings")
    Map<String, Map<String, String>> settings();

    interface ExtensionProperties
    {
        @WithName("path")
        @WithDefault("extensions")
        Path path();

        /**
         * Whether the modules of an extension jar seen for the first time go live at that startup, or wait for a
         * redeploy. The modules the app ships always go live, so that an upgrade leaves nothing unserved.
         */
        @WithName("auto-deploy")
        @WithDefault("true")
        boolean autoDeploy();
    }

    interface DeploymentProperties
    {
        /** How often an instance checks for a newer core deployment, and writes its heartbeat. */
        @WithName("poll-interval")
        @WithDefault("30s")
        Duration pollInterval();

        /**
         * Identifies this instance among the replicas: the {@code HOSTNAME} variable (the pod name in Kubernetes), else
         * {@code wombat}. Stable across restarts, so that a restarted instance takes its heartbeat back.
         */
        @WithName("instance-id")
        @WithDefault("${HOSTNAME:wombat}")
        String instanceId();
    }
}
