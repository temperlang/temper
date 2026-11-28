using System;
using TemperLang.Core;

namespace HostDeclaredErrorsHostError;

public abstract class HostDeclaredErrorsException : Exception, IFailure
{
    private protected HostDeclaredErrorsException(Exception? cause)
        : base(null, cause)
    {
    }
}
