package tech.illuin.wombat.commons.response;

import org.junit.jupiter.api.Test;
import tech.illuin.wombat.commons.response.Response;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ResponseTest
{

    @Test
    void status_buildsResponseWithMessageAndNullPayload()
    {
        Response<Void> response = Response.status("hello");

        assertEquals("hello", response.status().message());
        assertNull(response.payload());
    }

    @Test
    void success_withPayloadOnly_defaultsMessageToSuccess()
    {
        Response<String> response = Response.success("data");

        assertEquals("Success", response.status().message());
        assertEquals("data", response.payload());
    }

    @Test
    void success_withMessageAndPayload_keepsBoth()
    {
        Response<Integer> response = Response.success("ok", 42);

        assertEquals("ok", response.status().message());
        assertEquals(42, response.payload());
    }

    @Test
    void set_addsMetadataAndReturnsSameResponse()
    {
        Response<String> response = Response.success("p");

        Response<String> chained = response.set("k", "v");

        assertEquals(response, chained);
        assertEquals("v", chained.status().metadata().get("k"));
    }
}
