using TemperLang.Core;

namespace Classes;

internal class AnyFailing<T> : IFailing<T>
{
    public AnyFailing()
    {
    }

    public T Fail()
    {
        throw Core.Panic("panic");
    }
}
