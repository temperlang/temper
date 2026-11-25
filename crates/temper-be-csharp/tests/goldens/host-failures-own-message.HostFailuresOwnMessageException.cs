using System;
using TemperLang.Core;

namespace HostFailuresOwnMessage;

public abstract class HostFailuresOwnMessageException : Exception, IFailure
{
    private protected HostFailuresOwnMessageException(Exception? cause)
        : base(null, cause)
    {
    }
}
