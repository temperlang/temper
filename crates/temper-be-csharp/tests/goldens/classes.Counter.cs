namespace Classes;

internal class Counter
{
    private int count;
    private int hidden;

    public Counter()
    {
        this.count = 0;
        this.hidden = 0;
    }

    public int Count
    {
        get
        {
            return this.count;
        }
        set
        {
            this.count = value;
        }
    }

    public int Doubled
    {
        get
        {
            return this.count * 2;
        }
        set
        {
            this.count = value - this.count;
        }
    }

    public int Added
    {
        set
        {
            this.count = this.count + value;
        }
    }

    public Counter Bump()
    {
        this.count = this.count + 1;
        this.hidden = this.hidden + 1;
        return this;
    }

    public bool Same(Counter other)
    {
        return this.hidden == other.hidden;
    }

    public static Counter Zero()
    {
        return new Counter();
    }
}
