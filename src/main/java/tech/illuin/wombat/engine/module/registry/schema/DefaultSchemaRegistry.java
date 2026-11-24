package tech.illuin.wombat.engine.module.registry.schema;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.classmate.ResolvedType;
import com.github.victools.jsonschema.generator.*;
import com.github.victools.jsonschema.generator.impl.PropertySortUtils;
import com.github.victools.jsonschema.module.jackson.JacksonModule;
import com.github.victools.jsonschema.module.jackson.JacksonOption;
import com.github.victools.jsonschema.module.jakarta.validation.JakartaValidationModule;
import com.github.victools.jsonschema.module.jakarta.validation.JakartaValidationOption;
import tech.illuin.wombat.core.activity.commons.Country;
import tech.illuin.wombat.core.asset.profile.Lifetime;
import tech.illuin.wombat.core.asset.type.ModuleType;
import tech.illuin.wombat.core.secret.SecretKey;
import tech.illuin.wombat.core.source.Period;
import tech.illuin.wombat.engine.module.registry.ModuleRegistry;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.StreamSupport;

import static com.github.victools.jsonschema.generator.OptionPreset.PLAIN_JSON;
import static com.github.victools.jsonschema.generator.SchemaVersion.DRAFT_2020_12;

/**
 * Generates the JSON schema of each asset type's {@code properties}, as accepted by the asset endpoints: the identity
 * properties ({@code id}, {@code environmentId}, {@code type}) are left out since the server sets them.
 */
public class DefaultSchemaRegistry implements AssetSchemaRegistry
{
    /** Format of the string properties naming a secret, see {@link SecretKey} */
    public static final String SECRET_KEY_FORMAT = "secret-key";
    public static final String SECRET_KEY_PATTERN = "^[A-Za-z0-9_-]+$";
    private static final String LIFETIME_PATTERN = "^\\d+\\s*[dmyhDMYH]?$";
    private static final String PERIOD_PATTERN = "^\\d+\\s*[smhSMH]$";
    private static final Set<String> IDENTITY_PROPERTIES = Set.of("id", "environmentId", "type");
    /** Common to every asset, and the most detailed of its properties: always listed last */
    private static final String PROFILE_PROPERTY = "profile";

    private final ModuleRegistry moduleRegistry;
    private final SchemaGenerator schemaGenerator;
    private final Map<ModuleType, JsonNode> cache;

    public DefaultSchemaRegistry(ModuleRegistry moduleRegistry, ObjectMapper objectMapper)
    {
        this.moduleRegistry = moduleRegistry;
        this.schemaGenerator = createGenerator(objectMapper);
        this.cache = new ConcurrentHashMap<>();
    }

    @Override
    public Optional<JsonNode> getSchema(ModuleType type)
    {
        JsonNode node = this.cache.computeIfAbsent(type, k -> this.moduleRegistry.getModule(k)
            /* A module not detected here has no class to describe, and a family module defines no asset */
            .filter(mm -> mm.assetClass() != null)
            .map(mm -> withoutIdentity(this.schemaGenerator.generateSchema(mm.assetClass())))
            .orElse(null)
        );
        return Optional.ofNullable(node);
    }

    private static SchemaGenerator createGenerator(ObjectMapper objectMapper)
    {
        SchemaGeneratorConfigBuilder configBuilder = new SchemaGeneratorConfigBuilder(DRAFT_2020_12, PLAIN_JSON)
            .withObjectMapper(objectMapper)
            .with(new JacksonModule(
                JacksonOption.RESPECT_JSONPROPERTY_REQUIRED,
                JacksonOption.FLATTENED_ENUMS_FROM_JSONVALUE,
                JacksonOption.FLATTENED_ENUMS_FROM_JSONPROPERTY
            ))
            .with(new JakartaValidationModule(
                JakartaValidationOption.NOT_NULLABLE_FIELD_IS_REQUIRED,
                JakartaValidationOption.INCLUDE_PATTERN_EXPRESSIONS
            ))
            .with(Option.FLATTENED_ENUMS)
        ;
        configBuilder.forTypesInGeneral()
            .withCustomDefinitionProvider(DefaultSchemaRegistry::stringDefinition)
            /* Properties keep the order their record declares them in, which forms follow, rather than an alphabetical one */
            .withPropertySorter(PropertySortUtils.SORT_PROPERTIES_FIELDS_BEFORE_METHODS);
        configBuilder.forFields().withDefaultResolver(field -> resolveDefault(field, objectMapper));
        return new SchemaGenerator(configBuilder.build());
    }

    /**
     * Types serialized as a plain string through {@code @JsonValue}, or by the JSR-310 module, would otherwise be
     * described by their Java fields.
     */
    private static CustomDefinition stringDefinition(ResolvedType javaType, SchemaGenerationContext context)
    {
        Class<?> type = javaType.getErasedType();
        ObjectNode node = context.getGeneratorConfig().createObjectNode().put("type", "string");

        /* The format says it all, the class description would only clutter the property's own */
        if (type == SecretKey.class)
            return new CustomDefinition(
                node.put("format", SECRET_KEY_FORMAT).put("pattern", SECRET_KEY_PATTERN),
                CustomDefinition.DefinitionType.INLINE,
                CustomDefinition.AttributeInclusion.NO
            );
        if (type == Lifetime.class)
            node.put("pattern", LIFETIME_PATTERN);
        else if (type == Period.class)
            node.put("pattern", PERIOD_PATTERN);
        else if (type == Country.class)
        {
            ArrayNode oneOf = node.putArray("oneOf");
            Country.values().forEach(country -> oneOf.addObject()
                .put("const", country.alpha3())
                .put("title", country.displayName())
            );
        }
        else if (!ZoneId.class.isAssignableFrom(type))
            return null;

        return new CustomDefinition(node, CustomDefinition.DefinitionType.INLINE, CustomDefinition.AttributeInclusion.YES);
    }

    /**
     * @return the {@code @JsonProperty(defaultValue)} of the field, typed after the field so that a form can use it
     */
    private static Object resolveDefault(FieldScope field, ObjectMapper objectMapper)
    {
        JsonProperty annotation = field.getAnnotationConsideringFieldAndGetter(JsonProperty.class);
        if (annotation == null || annotation.defaultValue().isEmpty())
            return null;

        String value = annotation.defaultValue();
        Class<?> type = field.getType().getErasedType();
        if (type == int.class || type == Integer.class || type == long.class || type == Long.class)
            return Long.parseLong(value);
        if (type == double.class || type == Double.class || type == float.class || type == Float.class)
            return Double.parseDouble(value);
        if (type == boolean.class || type == Boolean.class)
            return Boolean.parseBoolean(value);
        if (type.isEnum())
        {
            /* Defaults are written either as the constant's name or as its JSON value, in any case: the schema lists the JSON values */
            for (Object constant : type.getEnumConstants())
            {
                String json = objectMapper.convertValue(constant, String.class);
                if (json.equalsIgnoreCase(value) || ((Enum<?>) constant).name().equalsIgnoreCase(value))
                    return json;
            }
        }
        return value;
    }

    /**
     * Merges every {@code allOf} into its parent, and removes the identity properties, which the generator inlined
     * from {@code @JsonUnwrapped} into the root.
     * <p>
     * The generator splits a property into {@code allOf} parts when its type and the property itself both carry a
     * description (or the root, when unwrapping the identity); the parts later in the list are the more specific
     * ones, so their attributes win. A flat schema is what form generators expect.
     */
    private static JsonNode withoutIdentity(ObjectNode schema)
    {
        flattenAllOf(schema);
        discriminateUnions(schema);
        if (schema.get("properties") instanceof ObjectNode properties)
        {
            properties.remove(IDENTITY_PROPERTIES);
            JsonNode profile = properties.remove(PROFILE_PROPERTY);
            if (profile != null)
                properties.set(PROFILE_PROPERTY, profile);
        }
        if (schema.get("required") instanceof ArrayNode required)
        {
            for (int i = required.size() - 1; i >= 0; i--)
                if (IDENTITY_PROPERTIES.contains(required.get(i).asText()))
                    required.remove(i);
            if (required.isEmpty())
                schema.remove("required");
        }
        return schema;
    }

    private static void flattenAllOf(JsonNode node)
    {
        node.forEach(DefaultSchemaRegistry::flattenAllOf);
        if (!(node instanceof ObjectNode object) || !(object.get("allOf") instanceof ArrayNode allOf))
            return;
        for (JsonNode part : allOf)
            if (!part.isObject())
                return;

        ObjectNode merged = object.objectNode();
        allOf.forEach(part -> merge(merged, (ObjectNode) part));
        object.remove("allOf");
        merge(merged, object);
        object.removeAll().setAll(merged);
    }

    /**
     * Turns the {@code anyOf} listing the subtypes of a {@code @JsonTypeInfo} hierarchy into a {@code oneOf}: each
     * subtype's discriminator property holds a distinct {@code const}, so exactly one of them matches. Each subtype
     * gets a title to pick it by, and its discriminator a default, so that a form fills it in.
     */
    private static void discriminateUnions(JsonNode node)
    {
        node.forEach(DefaultSchemaRegistry::discriminateUnions);
        if (!(node instanceof ObjectNode object) || !(object.get("anyOf") instanceof ArrayNode anyOf) || anyOf.isEmpty())
            return;

        Optional<String> discriminator = discriminator(anyOf);
        if (discriminator.isEmpty())
            return;

        for (JsonNode subtype : anyOf)
        {
            ObjectNode property = (ObjectNode) subtype.path("properties").path(discriminator.get());
            String value = property.get("const").asText();
            property.put("default", value);
            if (!subtype.has("title"))
                ((ObjectNode) subtype).put("title", humanize(value));
        }
        object.remove("anyOf");
        object.set("oneOf", anyOf);
        /* A new item, e.g. in a list of scenarios, starts as the first subtype rather than as no subtype at all */
        if (!object.has("default"))
            object.putObject("default").put(discriminator.get(), anyOf.get(0).at("/properties/" + discriminator.get() + "/const").asText());
    }

    /**
     * @return the property holding a {@code const} in every subtype
     */
    private static Optional<String> discriminator(ArrayNode subtypes)
    {
        List<String> candidates = new ArrayList<>();
        subtypes.get(0).path("properties").properties().forEach(entry -> {
            if (entry.getValue().path("const").isTextual())
                candidates.add(entry.getKey());
        });
        return candidates.stream()
            .filter(name -> StreamSupport.stream(subtypes.spliterator(), false)
                .allMatch(subtype -> subtype.path("properties").path(name).path("const").isTextual()))
            .findFirst();
    }

    /**
     * @return e.g. {@code User activity} for {@code user_activity}
     */
    private static String humanize(String value)
    {
        String words = value.replace('_', ' ').replace('-', ' ').trim();
        return words.isEmpty() ? value : Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }

    private static void merge(ObjectNode target, ObjectNode source)
    {
        source.properties().forEach(entry -> {
            JsonNode current = target.get(entry.getKey());
            /* The description of a patterned string type explains its syntax, e.g. "5y": keep it after the property's own */
            if (entry.getKey().equals("description") && current != null && target.has("pattern") && !current.equals(entry.getValue()))
                target.put("description", entry.getValue().asText() + " — " + lowercaseFirst(current.asText()));
            else if (entry.getKey().equals("properties") && current instanceof ObjectNode properties && entry.getValue().isObject())
                properties.setAll((ObjectNode) entry.getValue());
            else if (entry.getKey().equals("required") && current instanceof ArrayNode required && entry.getValue().isArray())
                entry.getValue().forEach(name -> {
                    if (!contains(required, name))
                        required.add(name);
                });
            else
                target.set(entry.getKey(), entry.getValue().deepCopy());
        });
    }

    private static String lowercaseFirst(String text)
    {
        return text.isEmpty() ? text : Character.toLowerCase(text.charAt(0)) + text.substring(1);
    }

    private static boolean contains(ArrayNode array, JsonNode value)
    {
        for (JsonNode item : array)
            if (item.equals(value))
                return true;
        return false;
    }
}
