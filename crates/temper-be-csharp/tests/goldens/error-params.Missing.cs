using System;

namespace ErrorParams;

internal sealed class Missing : ErrorParamsException
{
    private readonly string key;

    public Missing(string key)
        : this(key, null)
    {
    }

    internal Missing(string key, Exception? cause)
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
            return "missing (key: " + this.key + ")";
        }
    }
}
