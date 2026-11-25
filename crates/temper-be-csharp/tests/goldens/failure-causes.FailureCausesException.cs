using System;
using TemperLang.Core;

namespace FailureCauses;

public abstract class FailureCausesException : Exception, IFailure
{
    private protected FailureCausesException(Exception? cause)
        : base(null, cause)
    {
    }
}
