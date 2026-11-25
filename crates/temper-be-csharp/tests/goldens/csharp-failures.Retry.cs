using System;
using System.Globalization;

namespace CsharpFailures;

public sealed class Retry : CsharpFailuresException
{
    private readonly string reason;
    private readonly int tries;

    public Retry(string cause, int? tries = null)
        : this(cause, tries, null)
    {
    }

    internal Retry(string cause, int? tries, Exception? cause2)
        : base(cause2)
    {
        int tries2 = tries ?? 1;
        this.reason = "retry " + cause;
        this.tries = tries2;
    }

    public string Reason
    {
        get
        {
            return this.reason;
        }
    }

    public int Tries
    {
        get
        {
            return this.tries;
        }
    }

    public override string Message
    {
        get
        {
            return "retry (reason: " + this.reason + ", tries: " + this.tries.ToString(CultureInfo.InvariantCulture) + ")";
        }
    }
}
