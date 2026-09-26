package tech.illuin.wombat.module.extension;

import tech.illuin.wombat.core.asset.ServiceFamily;
import tech.illuin.wombat.core.asset.profile.LLMProfile;
import tech.illuin.wombat.core.asset.profile.Profile;
import tech.illuin.wombat.core.asset.profile.ServerProfile;
import tech.illuin.wombat.core.module.WombatModule;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.Optional;

/**
 * Verifies that an extension relying on the core default impact resolver of its family declares a profile type that
 * resolver can handle (e.g. {@link LLMProfile} for {@link ServiceFamily#LLM}); such assets would otherwise be skipped.
 */
final class ExtensionContractCheck
{
    private static final Map<ServiceFamily, Class<? extends Profile>> FAMILY_PROFILES = Map.of(
        ServiceFamily.LLM, LLMProfile.class,
        ServiceFamily.KUBERNETES_CONTAINER, ServerProfile.class
    );

    private ExtensionContractCheck() {}

    /**
     * @return a description of the contract violation, if the module's declared profile type can never satisfy the
     * profile contract expected by the default impact resolver of its family
     */
    static Optional<String> check(WombatModule module)
    {
        if (module.createImpactResolver().isPresent())
            return Optional.empty();

        Class<? extends Profile> required = FAMILY_PROFILES.get(module.type().family());
        if (required == null)
            return Optional.empty();

        Optional<Class<?>> declared = declaredProfileType(module);
        if (declared.isEmpty())
            return Optional.empty();

        Class<?> profileType = declared.get();
        if (required.isAssignableFrom(profileType))
            return Optional.empty();
        /* A non-final declared type (e.g. Profile itself) may still be implemented by a compatible class at runtime */
        if (!Modifier.isFinal(profileType.getModifiers()))
            return Optional.empty();

        return Optional.of(
            "Extension module " + module.getClass().getName() + " (asset-type " + module.type().name() + ", family " + module.type().family() + ")"
            + " relies on the default impact resolver, but its profile type " + profileType.getName()
            + " does not implement " + required.getName() + ": impact of its assets will be skipped"
        );
    }

    private static Optional<Class<?>> declaredProfileType(WombatModule module)
    {
        try {
            /* getMethod picks the most specific return type among covariant overrides */
            Method method = module.assetClass().getMethod("profile");
            return Optional.of(method.getReturnType());
        }
        catch (NoSuchMethodException e) {
            return Optional.empty();
        }
    }
}
