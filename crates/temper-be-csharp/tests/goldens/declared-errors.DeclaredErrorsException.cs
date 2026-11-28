using System;
using TemperLang.Core;

namespace DeclaredErrors;

internal abstract class DeclaredErrorsException : Exception, IFailure
{
    private protected DeclaredErrorsException(Exception? cause)
        : base(null, cause)
    {
    }
}
