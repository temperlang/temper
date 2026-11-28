using System;

namespace ErrorsInterop;

public sealed class EmptyText : SignException
{
    public EmptyText()
        : this(null)
    {
    }

    internal EmptyText(Exception? cause)
        : base(cause)
    {
    }

    public override string Message
    {
        get
        {
            return "no text";
        }
    }
}
