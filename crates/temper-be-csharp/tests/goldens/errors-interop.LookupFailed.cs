using System;

namespace ErrorsInterop;

public sealed class LookupFailed : ErrorsInteropException
{
    private readonly string name;

    public LookupFailed(string name)
        : this(name, null)
    {
    }

    internal LookupFailed(string name, Exception? cause)
        : base(cause)
    {
        this.name = name;
    }

    public string Name
    {
        get
        {
            return this.name;
        }
    }

    public override string Message
    {
        get
        {
            return "lookup failed (name: " + this.name + ")";
        }
    }
}
