using System;

namespace Panics;

internal sealed class NotSquare : Problem
{
    public NotSquare()
        : this(null)
    {
    }

    internal NotSquare(Exception? cause)
        : base(cause)
    {
    }

    public override string Message
    {
        get
        {
            return "not square";
        }
    }
}
