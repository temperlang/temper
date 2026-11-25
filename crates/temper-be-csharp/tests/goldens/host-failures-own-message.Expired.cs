using System;
using System.Globalization;

namespace HostFailuresOwnMessage;

internal sealed class Expired : HostFailuresException
{
    private readonly int days;

    public Expired(int days)
        : this(days, null)
    {
    }

    internal Expired(int days, Exception? cause)
        : base(cause)
    {
        this.days = days;
    }

    public int Days
    {
        get
        {
            return this.days;
        }
    }

    public override string Message
    {
        get
        {
            return "expired " + this.days.ToString(CultureInfo.InvariantCulture) + " days ago";
        }
    }
}
