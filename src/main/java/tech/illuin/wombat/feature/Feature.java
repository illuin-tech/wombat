package tech.illuin.wombat.feature;

import tech.illuin.wombat.commons.NodeMode;

import java.util.function.Predicate;

public enum Feature
{
    UI("ui", mode -> mode != NodeMode.worker),
    API_READ("api-read", mode -> mode != NodeMode.worker),
    API_WRITE("api-write", mode -> mode == NodeMode.aggregator),
    ADMIN("admin", mode -> mode != NodeMode.worker),
    ;

    private final String flag;
    private final Predicate<NodeMode> enabled;

    Feature(String flag, Predicate<NodeMode> enabled)
    {
        this.flag = flag;
        this.enabled = enabled;
    }

    public String flag()
    {
        return this.flag;
    }

    public boolean enabledIn(NodeMode mode)
    {
        return this.enabled.test(mode);
    }

    public String configKey()
    {
        return Keys.PREFIX + this.flag;
    }

    /**
     * Config keys set to whether a feature is enabled, see {@link FeatureConfigSourceFactory}. Endpoints are gated with
     * {@link io.quarkus.resteasy.reactive.server.EndpointDisabled} on them, so a disabled endpoint is neither served nor
     * listed in the OpenAPI document.
     */
    public static final class Keys
    {
        static final String PREFIX = "_internal.feature.";

        public static final String UI = PREFIX + "ui";
        public static final String API_READ = PREFIX + "api-read";
        public static final String API_WRITE = PREFIX + "api-write";
        public static final String ADMIN = PREFIX + "admin";
        /** The {@link #ADMIN} feature with a secret write mode: {@code @EndpointDisabled} only takes a single key. */
        public static final String ADMIN_SECRETS = PREFIX + "admin-secrets";

        private Keys() {}
    }
}
