using System;

namespace DeclaredErrors;

internal sealed class NotFound : DeclaredErrorsException
{
    private readonly string key;

    public NotFound(string key)
        : this(key, null)
    {
    }

    internal NotFound(string key, Exception? cause)
        : base(cause)
    {
        this.key = key;
    }

    public string Key
    {
        get
        {
            return this.key;
        }
    }

    public override string Message
    {
        get
        {
            return "not found (key: " + this.key + ")";
        }
    }
}
