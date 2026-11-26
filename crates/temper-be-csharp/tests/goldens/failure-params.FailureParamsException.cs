using System;
using TemperLang.Core;

namespace FailureParams;

internal abstract class FailureParamsException : Exception, IFailure
{
    private protected FailureParamsException(Exception? cause)
        : base(null, cause)
    {
    }
}
