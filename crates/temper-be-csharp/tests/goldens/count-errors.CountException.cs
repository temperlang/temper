using System;
using TemperLang.Core;

namespace CountErrors;

public abstract class CountException : Exception, IFailure
{
    private protected CountException(Exception? cause)
        : base(null, cause)
    {
    }
}
