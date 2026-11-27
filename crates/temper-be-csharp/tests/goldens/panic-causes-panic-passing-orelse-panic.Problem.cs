using System;
using TemperLang.Core;

namespace PanicCausesPanicPassingOrelsePanic;

internal abstract class Problem : Exception, IFailure
{
    private protected Problem(Exception? cause)
        : base(null, cause)
    {
    }
}
