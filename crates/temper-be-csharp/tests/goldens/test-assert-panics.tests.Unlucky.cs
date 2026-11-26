using System;

namespace TestAssertPanics;

internal sealed class Unlucky : AtException
{
    public Unlucky()
        : this(null)
    {
    }

    internal Unlucky(Exception? cause)
        : base(cause)
    {
    }

    public override string Message
    {
        get
        {
            return "unlucky";
        }
    }
}
