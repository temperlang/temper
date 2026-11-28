using System;
using TemperLang.Core;

namespace ErrorsInterop;

public abstract class ErrorsInteropException : Exception, IFailure
{
    private protected ErrorsInteropException(Exception? cause)
        : base(null, cause)
    {
    }
}
