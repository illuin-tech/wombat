package tech.illuin.wombat.engine.simulation.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * How a copied asset stands against the asset it was copied from, compared on their definitions.
 */
public enum ParentStatus
{
    @JsonProperty("in-sync") IN_SYNC,
    /** Either side changed since the copy, or the last sync. */
    @JsonProperty("diverged") DIVERGED,
    /** The parent was deleted. */
    @JsonProperty("parent-removed") PARENT_REMOVED,
}
