using System;
using TemperLang.Core;

namespace FailuresInterop;

public abstract class FailuresInteropException : Exception, IFailure
{
    private protected FailuresInteropException(Exception? cause)
        : base(null, cause)
    {
    }
}
