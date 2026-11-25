using System;
using TemperLang.Core;

namespace HostFailuresHostFailure;

public abstract class HostFailuresHostFailureException : Exception, IFailure
{
    private protected HostFailuresHostFailureException(Exception? cause)
        : base(null, cause)
    {
    }
}
