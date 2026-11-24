using System.Collections.Generic;
using TemperLang.Core;

namespace Libraries;

public class Grid
{
    private readonly int ncols;
    private readonly IReadOnlyList<double> values;

    public Grid(int ncols, IReadOnlyList<double> values)
    {
        this.ncols = ncols;
        this.values = values;
    }

    public int Ncols
    {
        get
        {
            return this.ncols;
        }
    }

    public IReadOnlyList<double> Values
    {
        get
        {
            return this.values;
        }
    }

    public double Get(int i, int j)
    {
        return Listed.Get(this.values, i * this.ncols + j);
    }

    public static Grid? Parse(string s)
    {
        try
        {
            return LibrariesGlobal.ParseGrid(s);
        }
        catch
        {
            return null;
        }
    }
}
