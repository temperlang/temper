using System;
using TemperLang.Core;

namespace HostFailuresOwnMessage;

public abstract class HostFailuresException : Exception, IFailure
{
    private protected HostFailuresException(Exception? cause)
        : base(null, cause)
    {
    }
}
