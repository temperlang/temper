using System;

namespace HostFailuresSeveralLibraries;

internal sealed class Zero : Problem
{
    public Zero()
        : this(null)
    {
    }

    internal Zero(Exception? cause)
        : base(cause)
    {
    }

    public override string Message
    {
        get
        {
            return "zero";
        }
    }
}
