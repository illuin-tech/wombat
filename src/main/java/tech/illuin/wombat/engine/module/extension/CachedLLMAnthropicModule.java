package tech.illuin.wombat.engine.module.extension;

import tech.illuin.wombat.core.activity.WombatActivityResolver;
import tech.illuin.wombat.core.context.WombatContext;
import tech.illuin.wombat.engine.evaluation.CachedActivityResolver;
import tech.illuin.wombat.engine.module.extension.WombatModuleProperties.CacheProperties;
import tech.illuin.wombat.module.llm_anthropic.LLMAnthropicModule;

import java.util.Optional;

public class CachedLLMAnthropicModule extends LLMAnthropicModule
{
    private final CacheProperties properties;

    public CachedLLMAnthropicModule(CacheProperties properties)
    {
        this.properties = properties;
    }

    @Override
    public Optional<WombatActivityResolver> createActivityResolver(WombatContext context)
    {
        return super.createActivityResolver(context)
            .map(resolver -> new CachedActivityResolver(resolver, this.properties));
    }
}
