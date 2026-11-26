using System;
using TemperLang.Core;

namespace TestAssertPanics;

internal abstract class AtException : Exception, IFailure
{
    private protected AtException(Exception? cause)
        : base(null, cause)
    {
    }
}
