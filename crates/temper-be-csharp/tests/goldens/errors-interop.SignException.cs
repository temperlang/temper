using System;

namespace ErrorsInterop;

public abstract class SignException : ErrorsInteropException
{
    private protected SignException(Exception? cause)
        : base(cause)
    {
    }
}
