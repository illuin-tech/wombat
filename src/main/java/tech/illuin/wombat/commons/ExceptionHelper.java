package tech.illuin.wombat.commons;

import java.util.function.Function;

public final class ExceptionHelper
{
    private ExceptionHelper() {}

    /**
     * Adapts a ThrowingFunction into a standard java.util.function.Function,
     * sneaky-throwing any checked exceptions.
     */
    @SuppressWarnings("IllegalCatch")
    public static <T, R> Function<T, R> uncheck(ThrowingFunction<T, R, ?> function)
    {
        return t -> {
            try {
                return function.apply(t);
            }
            catch (Throwable e) {
                throw sneakyThrow(e);
            }
        };
    }

    /**
     * Tricks the compiler into throwing a checked exception without declaring it.
     */
    @SuppressWarnings("unchecked")
    private static <E extends Throwable> RuntimeException sneakyThrow(Throwable e) throws E
    {
        throw (E) e;
    }

    @FunctionalInterface
    public interface ThrowingFunction<T, R, E extends Throwable>
    {
        R apply(T t) throws E;
    }
}
