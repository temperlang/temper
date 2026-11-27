using System;
using System.Globalization;

namespace Panics;

internal sealed class Positive : Problem
{
    private readonly int n;

    public Positive(int n)
        : this(n, null)
    {
    }

    internal Positive(int n, Exception? cause)
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
            return "positive (n: " + this.n.ToString(CultureInfo.InvariantCulture) + ")";
        }
    }
}
