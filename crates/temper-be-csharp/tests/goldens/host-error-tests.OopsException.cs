using System;
using TemperLang.Core;

namespace HostErrorTests;

public abstract class OopsException : Exception, IFailure
{
    private protected OopsException(Exception? cause)
        : base(null, cause)
    {
    }
}
