using System;

namespace HostErrorCauses;

public sealed class Oops : OopsException
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
