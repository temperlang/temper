using System;

namespace FailuresInterop;

public abstract class SignException : FailuresInteropException
{
    private protected SignException(Exception? cause)
        : base(cause)
    {
    }
}
