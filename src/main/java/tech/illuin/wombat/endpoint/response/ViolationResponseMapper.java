package tech.illuin.wombat.endpoint.response;

import io.quarkus.hibernate.validator.runtime.jaxrs.ResteasyReactiveViolationException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ElementKind;
import jakarta.validation.Path;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import static jakarta.ws.rs.core.Response.*;

/**
 * Serves the request validation errors in the {@link Response} envelope, so that clients can show which field is invalid.
 * <p>
 * No mapper may apply to {@link jakarta.ws.rs.NotFoundException}, e.g. one for every {@link jakarta.ws.rs.WebApplicationException}:
 * the requests matching no endpoint would then be answered here rather than fall through to the UI's routing.
 */
public class ViolationResponseMapper
{
    @ServerExceptionMapper
    public jakarta.ws.rs.core.Response mapViolationException(ResteasyReactiveViolationException exception)
    {
        List<Map<String, String>> violations = new ArrayList<>();
        for (ConstraintViolation<?> violation : exception.getConstraintViolations())
        {
            /* A violation of the return value is a bug of the endpoint, left to the default 500 handling */
            if (kinds(violation.getPropertyPath()).contains(ElementKind.RETURN_VALUE))
                throw exception;
            violations.add(Map.of("field", field(violation.getPropertyPath()), "message", violation.getMessage()));
        }

        String message = "Invalid request: " + String.join(", ", violations.stream()
            .map(violation -> violation.get("field") + " " + violation.get("message"))
            .sorted()
            .toList()
        );
        return status(jakarta.ws.rs.core.Response.Status.BAD_REQUEST)
            .type(MediaType.APPLICATION_JSON)
            .entity(Response.status(message).set("violations", violations))
            .build();
    }

    private static List<ElementKind> kinds(Path path)
    {
        return StreamSupport.stream(path.spliterator(), false).map(Path.Node::getKind).toList();
    }

    /**
     * @return the path within the request body, e.g. {@code name} rather than {@code create.request.name}, or the
     *         parameter's name when the violation is about a parameter itself
     */
    private static String field(Path path)
    {
        List<Path.Node> nodes = StreamSupport.stream(path.spliterator(), false).toList();
        List<String> properties = nodes.stream()
            .filter(node -> node.getKind() == ElementKind.PROPERTY || node.getKind() == ElementKind.CONTAINER_ELEMENT)
            .map(Path.Node::getName)
            .toList();
        if (!properties.isEmpty())
            return String.join(".", properties);
        return nodes.stream()
            .filter(node -> node.getKind() == ElementKind.PARAMETER)
            .map(Path.Node::getName)
            .findFirst()
            .orElse(path.toString());
    }
}
