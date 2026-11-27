using System;
using System.Collections.Generic;

namespace Generics;

public class Box<T> : IHolder<T>
{
    private readonly T value;

    public Box(T value)
    {
        this.value = value;
    }

    public T Value
    {
        get
        {
            return this.value;
        }
    }

    public T Held
    {
        get
        {
            return this.value;
        }
    }

    public KeyValuePair<T, int> PairWith(int other)
    {
        return new KeyValuePair<T, int>(this.value, other);
    }

    public TOut Applied<TOut>(Func<T, TOut> f)
    {
        return f(this.value);
    }
}
