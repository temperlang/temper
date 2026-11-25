using System;
using TemperLang.Core;

namespace HostFailuresHostFailure;

public abstract class HostFailuresException : Exception, IFailure
{
    private protected HostFailuresException(Exception? cause)
        : base(null, cause)
    {
    }
}
