namespace Libraries;

public class Held<T>
    where T : class, IShown
{
    private readonly T item;
    private readonly Held<T>? next;

    public Held(T item, Held<T>? next)
    {
        this.item = item;
        this.next = next;
    }

    public T Item
    {
        get
        {
            return this.item;
        }
    }

    public Held<T>? Next
    {
        get
        {
            return this.next;
        }
    }

    public override string ToString()
    {
        return "Held(" + ((IShown)this.item).ToString() + ")";
    }
}
