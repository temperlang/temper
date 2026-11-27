using System;
using TemperLang.Core;

namespace AsyncTurns;

public abstract class HalveException : Exception, IFailure
{
    private protected HalveException(Exception? cause)
        : base(null, cause)
    {
    }
}
