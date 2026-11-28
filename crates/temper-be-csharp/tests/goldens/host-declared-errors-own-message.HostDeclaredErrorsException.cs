using System;
using TemperLang.Core;

namespace HostDeclaredErrorsOwnMessage;

public abstract class HostDeclaredErrorsException : Exception, IFailure
{
    private protected HostDeclaredErrorsException(Exception? cause)
        : base(null, cause)
    {
    }
}
