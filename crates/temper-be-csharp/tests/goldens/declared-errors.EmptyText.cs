using System;

namespace DeclaredErrors;

internal sealed class EmptyText : SignException
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
            return "empty text";
        }
    }
}
