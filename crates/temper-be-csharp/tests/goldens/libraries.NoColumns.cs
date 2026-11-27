using System;

namespace Libraries;

internal sealed class NoColumns : Problem
{
    public NoColumns()
        : this(null)
    {
    }

    internal NoColumns(Exception? cause)
        : base(cause)
    {
    }

    public override string Message
    {
        get
        {
            return "no columns";
        }
    }
}
