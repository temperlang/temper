using System;
using TemperLang.Core;

namespace Failures;

internal abstract class FailuresException : Exception, IFailure
{
    private protected FailuresException(Exception? cause)
        : base(null, cause)
    {
    }
}
