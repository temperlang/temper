using System.Collections.Generic;
using TemperLang.Core;

namespace AngleCall;

internal class Classy
{
    private readonly int index;

    public Classy(int index)
    {
        this.index = index;
    }

    public int Index
    {
        get
        {
            return this.index;
        }
    }

    public static T First<T>(IReadOnlyList<T> things)
    {
        T thing = Listed.Get(things, 0);
        return thing;
    }

    public T Indexed<T>(IReadOnlyList<T> things)
    {
        T thing = Listed.Get(things, this.index);
        return thing;
    }
}
