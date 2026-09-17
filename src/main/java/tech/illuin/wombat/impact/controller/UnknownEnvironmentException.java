package tech.illuin.wombat.impact.controller;

public class UnknownEnvironmentException extends Exception
{
    public UnknownEnvironmentException(String environmentId)
    {
        super("Unknown environment: " + environmentId);
    }
}
