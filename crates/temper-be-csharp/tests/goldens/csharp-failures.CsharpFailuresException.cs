using System;
using TemperLang.Core;

namespace CsharpFailures;

public abstract class CsharpFailuresException : Exception, IFailure
{
    private protected CsharpFailuresException(Exception? cause)
        : base(null, cause)
    {
    }
}
