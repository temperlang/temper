using System;

namespace TestFails;

internal sealed class Oops : Problem
{
    public Oops()
        : this(null)
    {
    }

    internal Oops(Exception? cause)
        : base(cause)
    {
    }

    public override string Message
    {
        get
        {
            return "oops";
        }
    }
}
