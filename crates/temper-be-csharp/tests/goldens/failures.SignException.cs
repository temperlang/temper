using System;

namespace Failures;

internal abstract class SignException : FailuresException
{
    private protected SignException(Exception? cause)
        : base(cause)
    {
    }
}
