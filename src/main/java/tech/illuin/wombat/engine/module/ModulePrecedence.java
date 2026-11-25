package tech.illuin.wombat.engine.module;

import tech.illuin.wombat.core.asset.type.ServiceFamily;
import tech.illuin.wombat.engine.module.registry.ModuleMetadata.Feature;

import java.util.List;

/**
 * Which family module serves first, for each family and component, as the latest deployment holds them and as the next
 * one will: the first family module providing a component serves the assets of its family, after each asset's own
 * module; the next ones only serve those it does not accept. Asset-type modules are left out, as are the modules the
 * serving instance does not detect, whose components it cannot tell.
 *
 * @param changed whether the next deployment changes any of them
 */
public record ModulePrecedence(List<FamilyPrecedence> families, boolean changed)
{
    /**
     * @param features the components some family module of the family provides, in either deployment
     */
    public record FamilyPrecedence(ServiceFamily family, List<FeaturePrecedence> features) {}

    /**
     * @param deployed the family modules providing the component in the latest deployment, the one serving first first
     * @param next     the same in the next deployment: the enabled modules, in the order of the modules
     */
    public record FeaturePrecedence(Feature feature, List<String> deployed, List<String> next, boolean changed) {}
}
