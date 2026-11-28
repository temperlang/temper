using System;
using TemperLang.Core;

namespace ErrorGenerators;

internal abstract class ErrorGeneratorsException : Exception, IFailure
{
    private protected ErrorGeneratorsException(Exception? cause)
        : base(null, cause)
    {
    }
}
