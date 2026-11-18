namespace Classes;

internal class Boxed<T>
{
    private readonly T value;
    private readonly int tag;

    public Boxed(T value, int tag)
    {
        this.value = value;
        this.tag = tag;
    }

    public T Value
    {
        get
        {
            return this.value;
        }
    }

    public int Tag
    {
        get
        {
            return this.tag;
        }
    }

    public bool Eq(Boxed<T> other)
    {
        return this.tag == other.tag;
    }

    public bool SameAs(Boxed<T> other)
    {
        if (this.Eq(other))
        {
            return other.Eq(this);
        }
        else
        {
            return false;
        }
    }
}
