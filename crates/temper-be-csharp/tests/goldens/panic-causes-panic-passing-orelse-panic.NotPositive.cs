using System;
using System.Globalization;

namespace PanicCausesPanicPassingOrelsePanic;

internal sealed class NotPositive : Problem
{
    private readonly int n;

    public NotPositive(int n)
        : this(n, null)
    {
    }

    internal NotPositive(int n, Exception? cause)
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
            return "not positive (n: " + this.n.ToString(CultureInfo.InvariantCulture) + ")";
        }
    }
}
