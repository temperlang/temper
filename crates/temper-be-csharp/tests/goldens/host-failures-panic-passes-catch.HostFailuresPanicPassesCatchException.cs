using System;
using TemperLang.Core;

namespace HostFailuresPanicPassesCatch;

public abstract class HostFailuresPanicPassesCatchException : Exception, IFailure
{
    private protected HostFailuresPanicPassesCatchException(Exception? cause)
        : base(null, cause)
    {
    }
}
