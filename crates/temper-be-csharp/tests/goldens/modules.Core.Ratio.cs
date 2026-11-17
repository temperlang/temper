namespace Modules.Core;

public class Ratio
{
    private readonly int n;
    private readonly int d;

    public Ratio(int n, int d)
    {
        this.n = n;
        this.d = d;
    }

    public int N
    {
        get
        {
            return this.n;
        }
    }

    public int D
    {
        get
        {
            return this.d;
        }
    }

    public bool Eq(Ratio other)
    {
        return this.n * other.d == other.n * this.d;
    }
}
