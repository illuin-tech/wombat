package tech.illuin.wombat.engine.module.extension;

import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

import java.nio.file.Path;
import java.time.Duration;

public interface WombatModuleProperties
{
    @WithName("extension")
    ExtensionProperties extension();

    @WithName("deployment")
    DeploymentProperties deployment();

    interface ExtensionProperties
    {
        @WithName("path")
        @WithDefault("extensions")
        Path path();
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
