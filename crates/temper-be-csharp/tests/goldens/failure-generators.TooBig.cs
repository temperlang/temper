using System;
using System.Globalization;

namespace FailureGenerators;

internal sealed class TooBig : FailureGeneratorsException
{
    private readonly int n;

    public TooBig(int n)
        : this(n, null)
    {
    }

    internal TooBig(int n, Exception? cause)
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
            return "too big (n: " + this.n.ToString(CultureInfo.InvariantCulture) + ")";
        }
    }
}
