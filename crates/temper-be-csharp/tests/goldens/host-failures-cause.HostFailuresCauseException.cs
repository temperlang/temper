using System;
using TemperLang.Core;

namespace HostFailuresCause;

public abstract class HostFailuresCauseException : Exception, IFailure
{
    private protected HostFailuresCauseException(Exception? cause)
        : base(null, cause)
    {
    }
}
