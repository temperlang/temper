using System;
using TemperLang.Core;

namespace ErrorParams;

internal abstract class ErrorParamsException : Exception, IFailure
{
    private protected ErrorParamsException(Exception? cause)
        : base(null, cause)
    {
    }
}
