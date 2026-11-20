package tech.illuin.wombat.engine.context.asset;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import tech.illuin.wombat.core.asset.Asset;
import tech.illuin.wombat.core.asset.AssetIdentity;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Reads an asset out of its definition: the type-specific properties (e.g. its {@code profile}) in JSON, under an
 * identity given apart. Shared by everything that holds asset definitions edited through the API.
 */
@ApplicationScoped
public class AssetDefinitionReader
{
    private final AssetValidator assetValidator;
    private final Validator validator;
    private final JsonMapper assetMapper;

    public AssetDefinitionReader(AssetValidator assetValidator, Validator validator, JsonMapper assetMapper)
    {
        this.assetValidator = assetValidator;
        this.validator = validator;
        this.assetMapper = assetMapper;
    }

    /**
     * Reads then validates the asset, for a definition received through the API: its type must have a module here,
     * and the asset must meet its constraints.
     */
    public Asset readValid(AssetIdentity identity, ObjectNode properties) throws InvalidAssetException
    {
        Asset asset = this.read(identity, properties);
        this.validate(identity, asset);
        return asset;
    }

    /**
     * Reads the asset as is: {@code identity} replaces any identity the properties hold. Not validated, so that a
     * definition stored earlier can still be read once its module is gone; it is then an unrecognized asset.
     */
    public Asset read(AssetIdentity identity, ObjectNode properties) throws InvalidAssetException
    {
        ObjectNode node = properties.deepCopy();
        /* Under its JSON name only: given both the name and its kebab-case alias, the mapper fails on the duplicate */
        node.remove("environment-id");
        node.put("id", identity.id());
        node.put("environmentId", identity.environmentId());
        node.put("type", identity.assetType());

        try {
            return this.assetMapper.treeToValue(node, Asset.class);
        }
        catch (JsonProcessingException e) {
            throw new InvalidAssetException("Invalid properties for asset " + identity.id() + " of type " + identity.assetType() + ": " + e.getOriginalMessage());
        }
    }

    /* Named after the requested identity: a simulated asset reports a fixed one of its own */
    private void validate(AssetIdentity identity, Asset asset) throws InvalidAssetException
    {
        this.assetValidator.validate(asset);

        Set<ConstraintViolation<Asset>> violations = this.validator.validate(asset);
        if (!violations.isEmpty())
        {
            throw new InvalidAssetException("Invalid properties for asset " + identity.id() + ": " + violations.stream()
                .map(violation -> violation.getPropertyPath() + " " + violation.getMessage())
                .sorted()
                .collect(Collectors.joining(", ")));
        }
    }
}
