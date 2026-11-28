using System;

namespace DeclaredErrors;

internal sealed class ConfigException : DeclaredErrorsException
{
    private readonly string setting;

    public ConfigException(string setting)
        : this(setting, null)
    {
    }

    internal ConfigException(string setting, Exception? cause)
        : base(cause)
    {
        this.setting = setting;
    }

    public string Setting
    {
        get
        {
            return this.setting;
        }
    }

    public override string Message
    {
        get
        {
            return "config error (setting: " + this.setting + ")";
        }
    }
}
