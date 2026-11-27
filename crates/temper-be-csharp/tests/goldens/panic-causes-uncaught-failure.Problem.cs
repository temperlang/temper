using System;
using TemperLang.Core;

namespace PanicCausesUncaughtFailure;

internal abstract class Problem : Exception, IFailure
{
    private protected Problem(Exception? cause)
        : base(null, cause)
    {
    }
}
