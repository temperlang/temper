using System;
using TemperLang.Core;

namespace HostFailuresHostErrorPassesCatch;

public abstract class HostFailuresHostErrorPassesCatchException : Exception, IFailure
{
    private protected HostFailuresHostErrorPassesCatchException(Exception? cause)
        : base(null, cause)
    {
    }
}
