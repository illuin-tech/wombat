package tech.illuin.wombat.engine.simulation;

public class UnknownSimulationAssetException extends RuntimeException
{
    public UnknownSimulationAssetException(String simulationId, String assetId)
    {
        super("Unknown asset " + assetId + " in simulation " + simulationId);
    }
}
