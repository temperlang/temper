using TemperLang.Core;

namespace NullableGenerics;

public class Slot<T>
{
    private readonly T value;

    public Slot(T value)
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

    public Optional<T> Kept(bool keep)
    {
        if (keep)
        {
            return this.value;
        }
        else
        {
            return Optional<T>.None;
        }
    }
}
