using System;
using TemperLang.Core;

namespace HostDeclaredErrorsCause;

public abstract class HostDeclaredErrorsException : Exception, IFailure
{
    private protected HostDeclaredErrorsException(Exception? cause)
        : base(null, cause)
    {
    }
}
