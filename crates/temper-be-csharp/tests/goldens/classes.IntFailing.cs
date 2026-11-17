using System;

namespace Classes;

internal class IntFailing : IFailing<int>
{
    public IntFailing()
    {
    }

    public int Fail()
    {
        Environment.FailFast("panic");
        throw new InvalidOperationException();
    }
}
