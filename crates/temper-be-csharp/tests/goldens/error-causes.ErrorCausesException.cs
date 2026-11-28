using System;
using TemperLang.Core;

namespace ErrorCauses;

public abstract class ErrorCausesException : Exception, IFailure
{
    private protected ErrorCausesException(Exception? cause)
        : base(null, cause)
    {
    }
}
