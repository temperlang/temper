using System;
using TemperLang.Core;

namespace FailureGenerators;

internal abstract class FailureGeneratorsException : Exception, IFailure
{
    private protected FailureGeneratorsException(Exception? cause)
        : base(null, cause)
    {
    }
}
