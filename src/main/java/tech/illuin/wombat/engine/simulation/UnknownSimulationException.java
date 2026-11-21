package tech.illuin.wombat.engine.simulation;

public class UnknownSimulationException extends RuntimeException
{
    public UnknownSimulationException(String simulationId)
    {
        super("Unknown simulation: " + simulationId);
    }
}
