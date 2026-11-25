package tech.illuin.wombat.engine.module.registry;

import com.fasterxml.jackson.annotation.JsonProperty;
import tech.illuin.wombat.core.asset.Asset;
import tech.illuin.wombat.core.asset.SimulatedAsset;
import tech.illuin.wombat.core.asset.type.AssetType;
import tech.illuin.wombat.core.asset.type.FamilyType;
import tech.illuin.wombat.core.asset.type.ModuleType;
import tech.illuin.wombat.core.module.WombatModule;

import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Set;

/**
 * A registered module, as the registry knows it.
 *
 * @param type       for a module not detected here, as it advertised it when last detected
 * @param assetClass null when this instance does not detect the module, and for a family module which defines no asset
 * @param enabled    the registry state, shared by every instance
 * @param missing    whether no live instance detects the module any more, e.g. its extension jar was removed
 * @param source     whether it comes from the extension folder; null when this instance does not detect the module
 * @param ordering   the place of the module in the order of the modules, from 1, which the next core deployed follows:
 *                   the family modules of a family are tried in that order; null for a module not placed yet
 * @param provides   empty when this instance does not detect the module
 */
public record ModuleMetadata(
    ModuleType type,
    Class<? extends Asset> assetClass,
    boolean enabled,
    boolean missing,
    Source source,
    Integer ordering,
    Set<Feature> provides
) {
    /**
     * The order of the modules: by ordering, those with none last, then by name.
     */
    public static final Comparator<ModuleMetadata> ORDERING = Comparator.comparing(ModuleMetadata::ordering, Comparator.nullsLast(Comparator.naturalOrder()))
        .thenComparing(module -> module.type().name());

    /**
     * A module detected on this instance, not from the extension folder.
     */
    public static ModuleMetadata core(WombatModule module, boolean enabled, Integer ordering)
    {
        return new ModuleMetadata(module.type(), module.assetClass().orElse(null), enabled, false, Source.core, ordering, Feature.of(module));
    }

    /**
     * @return whether this instance detects the module, and so holds its classes
     */
    public boolean detectedHere()
    {
        return this.source != null;
    }

    /**
     * @return whether its assets can be processed here: the module is enabled, and detected here
     */
    public boolean active()
    {
        return this.enabled && this.detectedHere();
    }

    /**
     * @return whether assets of this type can be declared in an environment; simulated assets only exist within a
     *         simulation request, and a module not detected here takes no asset. The simulation sandbox holds the same
     *         types, so a new reason here also keeps a type out of it.
     */
    @JsonProperty("creatable")
    public boolean creatable()
    {
        return this.detectedHere() && this.assetClass != null && !SimulatedAsset.class.isAssignableFrom(this.assetClass);
    }

    @JsonProperty("kind")
    public Kind kind()
    {
        return Kind.of(this.type);
    }

    /**
     * Whether a module defines an asset-type, or serves a whole family without one.
     */
    public enum Kind
    {
        asset,
        family,
        ;

        public static Kind of(ModuleType type)
        {
            return switch (type)
            {
                case AssetType assetType -> asset;
                case FamilyType familyType -> family;
            };
        }
    }

    public enum Source
    {
        core,
        extension,
    }

    /**
     * What a module provides for its assets, or those of its family: sampling them, or resolving their activity,
     * impacts or cost. Two family modules of a family only compete for what they both provide.
     */
    public enum Feature
    {
        source,
        activity,
        impact,
        cost,
        ;

        public static Set<Feature> of(WombatModule module)
        {
            Set<Feature> features = EnumSet.noneOf(Feature.class);
            if (module.source().isPresent())
                features.add(source);
            if (module.activityResolver().isPresent())
                features.add(activity);
            if (module.impactResolver().isPresent())
                features.add(impact);
            if (module.costResolver().isPresent())
                features.add(cost);
            return Collections.unmodifiableSet(features);
        }
    }
}
