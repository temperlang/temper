using System;

namespace HostDeclaredErrorsSeveralLibraries;

internal sealed class Unreadable : HostDeclaredErrorsException
{
    private readonly string key;

    public Unreadable(string key)
        : this(key, null)
    {
    }

    internal Unreadable(string key, Exception? cause)
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
            return "unreadable (key: " + this.key + ")";
        }
    }
}
