using System.Collections.Generic;

namespace Classes;

internal class Stock
{
    private readonly int count;
    private IReadOnlyList<int> itemsVar;
    private readonly int size;

    public IReadOnlyList<int> Items
    {
        get
        {
            return this.itemsVar;
        }
        set
        {
            this.itemsVar = value;
        }
    }

    public Stock(IReadOnlyList<int> items, int? count = null)
    {
        int count2 = count ?? items.Count;
        this.count = count2;
        this.itemsVar = items;
        this.size = items.Count;
    }

    public int Count
    {
        get
        {
            return this.count;
        }
    }

    public int Size
    {
        get
        {
            return this.size;
        }
    }

    public int SizeNow()
    {
        return this.Items.Count;
    }
}
