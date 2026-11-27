using System;
using System.Globalization;

namespace AsyncTurns;

public sealed class Odd : HalveException
{
    private readonly int n;

    public Odd(int n)
        : this(n, null)
    {
    }

    internal Odd(int n, Exception? cause)
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
            return "odd (n: " + this.n.ToString(CultureInfo.InvariantCulture) + ")";
        }
    }
}
