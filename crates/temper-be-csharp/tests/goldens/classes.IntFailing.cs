using TemperLang.Core;

namespace Classes;

internal class IntFailing : IFailing<int>
{
    public IntFailing()
    {
    }

    public int Fail()
    {
        throw Core.Panic("panic");
    }
}
