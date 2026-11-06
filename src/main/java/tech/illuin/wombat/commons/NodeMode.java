package tech.illuin.wombat.commons;

public enum NodeMode
{
    /** Samples its environments into its own database and serves them: the single-instance setup. */
    STANDALONE,
    /** Stores and serves the metrics pushed by workers on {@code POST /api/workers/{worker}/metrics}, and samples nothing itself. */
    AGGREGATOR,
    /** Samples its environments and pushes the metrics to an aggregator instead of its own database. */
    WORKER,
}
