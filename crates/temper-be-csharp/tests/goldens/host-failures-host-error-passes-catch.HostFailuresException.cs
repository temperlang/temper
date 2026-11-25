using System;
using TemperLang.Core;

namespace HostFailuresHostErrorPassesCatch;

public abstract class HostFailuresException : Exception, IFailure
{
    private protected HostFailuresException(Exception? cause)
        : base(null, cause)
    {
    }
}
