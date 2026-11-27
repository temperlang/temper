using TemperLang.Core;

namespace Broken;

public static class BrokenGlobal
{
    static BrokenGlobal()
    {
        throw Core.Panic("panic");
    }
}
