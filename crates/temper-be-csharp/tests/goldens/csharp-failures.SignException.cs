using System;

namespace CsharpFailures;

public abstract class SignException : CsharpFailuresException
{
    private protected SignException(Exception? cause)
        : base(cause)
    {
    }
}
