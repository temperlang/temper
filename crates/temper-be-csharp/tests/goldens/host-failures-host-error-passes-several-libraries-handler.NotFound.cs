using System;

namespace HostFailuresHostErrorPassesSeveralLibrariesHandler;

public sealed class NotFound : HostFailuresException
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
