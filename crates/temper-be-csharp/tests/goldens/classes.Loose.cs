namespace Classes;

internal class Loose
{
    private readonly int n;

    public Loose(int n)
    {
        this.n = n;
    }

    public int N
    {
        get
        {
            return this.n;
        }
    }

    public bool Eq(Loose other)
    {
        if (this.n == other.n)
        {
            return true;
        }
        else
        {
            return this.n == -other.n;
        }
    }

    public bool Mirrors(Loose other)
    {
        if (other.Eq(this))
        {
            return this.Eq(other);
        }
        else
        {
            return false;
        }
    }
}
