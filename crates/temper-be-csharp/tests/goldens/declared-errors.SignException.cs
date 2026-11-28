using System;
using TemperLang.Core;

namespace DeclaredErrors;

internal abstract class SignException : Exception, IFailure
{
    private protected SignException(Exception? cause)
        : base(null, cause)
    {
    }
}
