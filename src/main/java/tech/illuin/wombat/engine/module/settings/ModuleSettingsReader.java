package tech.illuin.wombat.engine.module.settings;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.fasterxml.jackson.databind.exc.ValueInstantiationException;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.illuin.wombat.core.module.ModuleSettings;
import tech.illuin.wombat.core.module.WombatModule;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.fasterxml.jackson.databind.node.JsonNodeFactory.instance;
import static com.fasterxml.jackson.databind.JsonNode.OverwriteMode.ALL;

/**
 * Reads the settings of the modules from {@code wombat.module.settings}: the properties given to a module make a
 * document, bound to its settings class by the mapper, then validated.
 * <p>
 * Every problem fails the startup, all of them at once: settings a module cannot use would otherwise leave it on its
 * defaults, e.g. the public APIs in place of a self-hosted one. Settings naming a module that is not detected are only
 * warned of, so that removing the jar of an extension does not keep the app from starting.
 */
public final class ModuleSettingsReader
{
    private final Map<String, Map<String, String>> settings;
    private final List<WombatModule> modules;
    private final ObjectReader reader;
    private final Validator validator;

    public static final String PREFIX = "wombat.module.settings";
    private static final PropertyNamingStrategies.NamingBase KEBAB_CASE = new PropertyNamingStrategies.KebabCaseStrategy();

    private static final Logger logger = LoggerFactory.getLogger(ModuleSettingsReader.class);

    /**
     * @param settings the properties given to each module, by module name then property path ({@code nested.name},
     *                 {@code list[0]})
     * @param mapper   a mapper naming properties in kebab-case, on which the settings classes are registered by module name
     */
    public ModuleSettingsReader(Map<String, Map<String, String>> settings, List<WombatModule> modules, ObjectMapper mapper, Validator validator)
    {
        this.settings = settings;
        this.modules = modules;
        this.reader = mapper.readerFor(ModuleSettings.class).with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        this.validator = validator;
    }

    /**
     * @return the settings of each module given some; a module given none applies its defaults
     * @throws ModuleSettingsException naming the key of each property a module cannot use
     */
    public List<ModuleSettings> read()
    {
        List<String> problems = new ArrayList<>();
        List<ModuleSettings> read = new ArrayList<>();
        this.settings.forEach((name, properties) -> {
            String key = PREFIX + ".\"" + name + "\"";
            Optional<WombatModule> module = this.modules.stream().filter(candidate -> candidate.type().name().equals(name)).findFirst();
            if (module.isEmpty())
                logger.warn("{} is ignored: no detected module goes by that name", key);
            else if (module.get().settingsClass().isEmpty())
                problems.add(key + ": module " + name + " takes no settings");
            else
                this.bind(key, document(name, properties), problems).ifPresent(read::add);
        });

        if (!problems.isEmpty())
            throw new ModuleSettingsException(problems);
        return read;
    }

    private Optional<ModuleSettings> bind(String key, ObjectNode document, List<String> problems)
    {
        try {
            ModuleSettings settings = this.reader.readValue(document);
            List<String> violations = this.validator.validate(settings).stream()
                .map(violation -> key + "." + KEBAB_CASE.translate(violation.getPropertyPath().toString()) + ": " + violation.getMessage())
                .toList();
            problems.addAll(violations);
            return violations.isEmpty() ? Optional.of(settings) : Optional.empty();
        }
        catch (JsonMappingException e) {
            problems.add(key + path(e) + ": " + reason(e));
        }
        catch (IOException e) {
            problems.add(key + ": " + e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * Turns the property paths into a tree, values left as text for the mapper to convert.
     */
    private static ObjectNode document(String name, Map<String, String> properties)
    {
        ObjectNode document = instance.objectNode().put("type", name);
        properties.forEach((path, value) -> {
            /* A YAML list also comes whole, comma-separated: its indexes keep the values holding commas */
            if (properties.keySet().stream().anyMatch(other -> other.startsWith(path + "[")))
                return;
            JsonPointer pointer = JsonPointer.compile("/" + path.replace("]", "").replace('[', '/').replace('.', '/'));
            if (pointer.last().mayMatchElement())
            {
                ArrayNode list = document.withArray(pointer.head(), ALL, true);
                while (list.size() <= pointer.last().getMatchingIndex())
                    list.addNull();
                list.set(pointer.last().getMatchingIndex(), value);
            }
            else
                document.withObject(pointer.head(), ALL, true).put(pointer.last().getMatchingProperty(), value);
        });
        return document;
    }

    private static String path(JsonMappingException e)
    {
        StringBuilder path = new StringBuilder();
        for (JsonMappingException.Reference reference : e.getPath())
            path.append(reference.getFieldName() != null ? "." + reference.getFieldName() : "[" + reference.getIndex() + "]");
        return path.toString();
    }

    private static String reason(JsonMappingException e)
    {
        if (e instanceof UnrecognizedPropertyException)
            return "not a setting of this module";
        /* A settings record checking its own values, e.g. a required one, says why in its exception */
        if (e instanceof ValueInstantiationException && e.getCause() != null)
            return e.getCause().getMessage();
        return e.getOriginalMessage();
    }
}
