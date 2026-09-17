package tech.illuin.wombat.persistence.backend.api;

import java.util.function.Supplier;

public class LazySupplier<T>
{
    private volatile T instance;
    private final Supplier<T> delegate;

    public LazySupplier(Supplier<T> delegate)
    {
        this.delegate = delegate;
    }

    public T supply()
    {
        T result = this.instance;
        if (result == null)
        {
            synchronized (this) {
                result = this.instance;
                if (result == null)
                {
                    result = this.delegate.get();
                    this.instance = result;
                }
            }
        }
        return result;
    }

    public boolean isSupplied()
    {
        return this.instance != null;
    }
}
