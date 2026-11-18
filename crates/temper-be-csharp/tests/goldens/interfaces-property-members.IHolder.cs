using System.Collections.Generic;
using TemperLang.Core;

namespace PropertyMembers;

internal interface IHolder<T>
{
    T Held { get; }

    IReadOnlyList<T> HeldAnd(T other)
    {
        return HeldAndDefault(this, other);
    }

    protected static IReadOnlyList<T> HeldAndDefault(IHolder<T> @this, T other)
    {
        return Listed.CreateReadOnlyList<T>(@this.Held, other);
    }
}
