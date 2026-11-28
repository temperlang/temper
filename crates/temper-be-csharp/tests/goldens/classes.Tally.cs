namespace Classes;

internal class Tally
{
    private int count;
    private readonly string name;

    public Tally(int count, string name)
    {
        this.count = count;
        this.name = name;
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

    public string Name
    {
        get
        {
            return this.name;
        }
    }
}
