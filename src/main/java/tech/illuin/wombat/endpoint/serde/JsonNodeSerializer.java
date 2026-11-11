package tech.illuin.wombat.endpoint.serde;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import tools.jackson.databind.JsonNode;

import java.io.IOException;

/**
 * Jackson 2 serializer for Jackson 3 {@link JsonNode} so that nested nodes inside
 * {@code Response<JsonNode>} are serialized as proper JSON rather than internal metadata.
 */
public class JsonNodeSerializer extends JsonSerializer<JsonNode>
{
    @Override
    public void serialize(JsonNode node, JsonGenerator generator, SerializerProvider provider) throws IOException
    {
        generator.writeRawValue(node.toString());
    }
}
