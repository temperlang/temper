using System;

namespace Bubble;

internal sealed class Missing : Problem
{
    public Missing()
        : this(null)
    {
    }

    internal Missing(Exception? cause)
        : base(cause)
    {
    }

    public override string Message
    {
        get
        {
            return "missing";
        }
    }
}
