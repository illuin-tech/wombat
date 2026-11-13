package tech.illuin.wombat.engine.context.model;

import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.deser.DeserializationProblemHandler;
import com.fasterxml.jackson.databind.jsontype.TypeIdResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.illuin.wombat.core.asset.Asset;

import java.io.IOException;

public class UnrecognizedAssetProblemHandler extends DeserializationProblemHandler
{
    private static final Logger logger = LoggerFactory.getLogger(UnrecognizedAssetProblemHandler.class);
    @Override
    public JavaType handleUnknownTypeId(DeserializationContext ctx, JavaType baseType, String subTypeId, TypeIdResolver idResolver, String failureMsg) throws IOException
    {
        if (baseType != null && Asset.class.isAssignableFrom(baseType.getRawClass()))
        {
            logger.warn("Encountered unrecognized asset type '{}', falling back to UnrecognizedAsset", subTypeId);
            return ctx.constructType(UnrecognizedAsset.class);
        }
        return super.handleUnknownTypeId(ctx, baseType, subTypeId, idResolver, failureMsg);
    }
}
