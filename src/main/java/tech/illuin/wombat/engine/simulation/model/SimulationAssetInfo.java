package tech.illuin.wombat.engine.simulation.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.util.NameTransformer;
import com.fasterxml.jackson.databind.util.TokenBuffer;
import tech.illuin.wombat.core.asset.Asset;
import tech.illuin.wombat.engine.simulation.persistence.SimulationAssetEntity;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

/**
 * An asset held by a simulation, its definition unwrapped as an environment's asset's is. It belongs to no
 * environment: the one of its identity is internal, and left out.
 *
 * @param properties the definition, under the identity it is evaluated with
 * @param source     {@code null} for an asset written from scratch
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SimulationAssetInfo(
    @JsonUnwrapped @JsonSerialize(using = WithoutEnvironment.class) Asset properties,
    String simulationId,
    String name,
    String description,
    String notes,
    Source source,
    Instant createdAt,
    Instant updatedAt
) {
    /**
     * @param properties the definition, under the identity it is evaluated with
     * @param status     {@code null} without a source
     */
    public static SimulationAssetInfo from(SimulationAssetEntity entity, Asset properties, SourceStatus status)
    {
        return new SimulationAssetInfo(
            properties,
            entity.simulationId,
            entity.name,
            entity.description,
            entity.notes,
            entity.sourceAssetId == null ? null : new Source(entity.sourceAssetId, entity.sourceEnvironmentId, status, entity.syncedAt),
            entity.createdAt,
            entity.updatedAt
        );
    }

    /**
     * The asset a definition was copied from.
     *
     * @param environmentId the source's environment, as it was at copy time
     * @param syncedAt      when the definition was last taken from the source
     */
    public record Source(String assetId, String environmentId, SourceStatus status, Instant syncedAt) {}

    /* Writes the asset's fields in place, as @JsonUnwrapped does, but the environment of its identity: that one is
       unwrapped twice over, which a property's @JsonIgnoreProperties does not reach */
    static final class WithoutEnvironment extends JsonSerializer<Asset>
    {
        @Override
        public void serialize(Asset asset, JsonGenerator generator, SerializerProvider provider) throws IOException
        {
            ObjectNode node;
            try (TokenBuffer buffer = new TokenBuffer(generator.getCodec(), false)) {
                provider.defaultSerializeValue(asset, buffer);
                node = buffer.asParser().readValueAsTree();
            }
            node.remove("environmentId");
            for (Map.Entry<String, JsonNode> field : node.properties())
            {
                generator.writeFieldName(field.getKey());
                generator.writeTree(field.getValue());
            }
        }

        /* The type id is an existing property, written with the others */
        @Override
        public void serializeWithType(Asset asset, JsonGenerator generator, SerializerProvider provider, TypeSerializer typeSerializer) throws IOException
        {
            this.serialize(asset, generator, provider);
        }

        @Override
        public boolean isUnwrappingSerializer()
        {
            return true;
        }

        @Override
        public JsonSerializer<Asset> unwrappingSerializer(NameTransformer unwrapper)
        {
            return this;
        }
    }
}
