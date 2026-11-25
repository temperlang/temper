using System;
using TemperLang.Core;

namespace HostFailuresSeveralLibraries;

public abstract class HostFailuresException : Exception, IFailure
{
    private protected HostFailuresException(Exception? cause)
        : base(null, cause)
    {
    }
}
