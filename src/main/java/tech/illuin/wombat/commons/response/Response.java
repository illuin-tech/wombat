package tech.illuin.wombat.commons.response;

public record Response<T>(
    Status status,
    T payload
)
{
    public static Response<Void> status(String message)
    {
        return new Response<>(new Status(message), null);
    }

    public static <T> Response<T> success(String message, T payload)
    {
        return new Response<>(new Status(message), payload);
    }

    public static <T> Response<T> success(T payload)
    {
        return new Response<>(new Status("Success"), payload);
    }

    public Response<T> set(String key, Object value)
    {
        this.status.set(key, value);
        return this;
    }
}
