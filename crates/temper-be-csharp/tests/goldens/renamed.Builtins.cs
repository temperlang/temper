using System.Globalization;

namespace Renamed;

public class Builtins
{
    private readonly int hash;

    public Builtins(int hash)
    {
        this.hash = hash;
    }

    public int Hash
    {
        get
        {
            return this.hash;
        }
    }

    public int Get(int i)
    {
        return this.hash + i;
    }

    public int Set(int i)
    {
        return i * 2;
    }

    public int Map(int i)
    {
        return i + 1;
    }

    public string Format()
    {
        return "#" + this.hash.ToString(CultureInfo.InvariantCulture);
    }

    public int Int
    {
        get
        {
            return this.hash * 10;
        }
    }

    public int Later()
    {
        return this.Int + 1;
    }

    public int Init()
    {
        return this.hash + 3;
    }

    public string Print()
    {
        return "print " + this.Init().ToString(CultureInfo.InvariantCulture);
    }
}
