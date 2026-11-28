using System;
using TemperLang.Core;

namespace HostDeclaredErrorsSeveralLibraries;

public abstract class HostDeclaredErrorsException : Exception, IFailure
{
    private protected HostDeclaredErrorsException(Exception? cause)
        : base(null, cause)
    {
    }
}
