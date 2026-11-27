using System;
using System.Globalization;

namespace Nullables;

internal sealed class Negative : Problem
{
    private readonly int n;

    public Negative(int n)
        : this(n, null)
    {
    }

    internal Negative(int n, Exception? cause)
        : base(cause)
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

    public override string Message
    {
        get
        {
            return "negative (n: " + this.n.ToString(CultureInfo.InvariantCulture) + ")";
        }
    }
}
