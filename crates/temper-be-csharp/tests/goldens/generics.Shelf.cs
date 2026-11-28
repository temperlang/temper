using System.Collections.Generic;

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
            return this.items[0].Name;
        }
    }

    public int Tally<TName>(int extra)
        where TName : Named
    {
        return this.items.Count + extra;
    }
}
