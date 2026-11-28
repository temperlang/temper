using System.Collections.Generic;
using TemperLang.Core;

namespace NullableTypeArgs;

internal class Shelf<T>
    where T : class, IShown
{
    private T? top;

    public Shelf(T? top)
    {
        this.top = top;
    }

    public T? Top
    {
        get
        {
            return this.top;
        }
        set
        {
            this.top = value;
        }
    }

    public IReadOnlyList<T?> All()
    {
        return Listed.CreateReadOnlyList<T?>(this.top, null);
    }
}
