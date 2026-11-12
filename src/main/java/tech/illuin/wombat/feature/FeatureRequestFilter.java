package tech.illuin.wombat.feature;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.container.ResourceInfo;
import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.resteasy.reactive.server.ServerRequestFilter;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class FeatureRequestFilter
{
    private final FeatureFlags features;
    private final Map<Method, Optional<Feature>> required = new ConcurrentHashMap<>();

    public FeatureRequestFilter(FeatureFlags features)
    {
        this.features = features;
    }

    @ServerRequestFilter
    public Uni<RestResponse<Void>> filter(ResourceInfo resource)
    {
        Optional<Feature> feature = this.required.computeIfAbsent(
            resource.getResourceMethod(),
            method -> requiredFor(method, resource.getResourceClass())
        );
        if (feature.isEmpty())
            return Uni.createFrom().nullItem();

        return this.features.isEnabled(feature.get()).map(enabled -> enabled ? null : RestResponse.notFound());
    }

    static Optional<Feature> requiredFor(Method method, Class<?> type)
    {
        RequiresFeature annotation = method.getAnnotation(RequiresFeature.class);
        if (annotation == null)
            annotation = type.getAnnotation(RequiresFeature.class);
        return Optional.ofNullable(annotation).map(RequiresFeature::value);
    }
}
