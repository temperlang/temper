using System;
using TemperLang.Core;

namespace HostDeclaredErrorsSeveralLibraries;

internal abstract class Problem : Exception, IFailure
{
    private protected Problem(Exception? cause)
        : base(null, cause)
    {
    }
}
