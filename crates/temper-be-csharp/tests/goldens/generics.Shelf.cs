using System.Collections.Generic;
using TemperLang.Core;

namespace Generics;

public class Shelf<T>
    where T : Named
{
    private readonly IReadOnlyList<T> items;

    public Shelf(IReadOnlyList<T> items)
    {
        this.items = items;
    }

    public IReadOnlyList<T> Items
    {
        get
        {
            return this.items;
        }
    }

    public string? FirstName()
    {
        if (this.items.Count == 0)
        {
            return null;
        }
        else
        {
            return Listed.Get(this.items, 0).Name;
        }
    }

    public int Tally<TName>(int extra)
    {
        return this.items.Count + extra;
    }
}
