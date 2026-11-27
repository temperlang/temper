using System;
using TemperLang.Core;

namespace HostFailuresSeveralLibraries;

internal abstract class Problem : Exception, IFailure
{
    private protected Problem(Exception? cause)
        : base(null, cause)
    {
    }
}
