using System;
using TemperLang.Core;

namespace HostErrorCauses;

public abstract class OopsException : Exception, IFailure
{
    private protected OopsException(Exception? cause)
        : base(null, cause)
    {
    }
}
