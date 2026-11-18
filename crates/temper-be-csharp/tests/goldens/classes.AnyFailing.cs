using System;

namespace Classes;

internal class AnyFailing<T> : IFailing<T>
{
    public AnyFailing()
    {
    }

    public T Fail()
    {
        Environment.FailFast("panic");
        throw new InvalidOperationException();
    }
}
