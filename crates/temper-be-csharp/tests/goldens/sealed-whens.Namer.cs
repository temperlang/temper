using TemperLang.Core;

namespace SealedWhens;

internal class Namer
{
    public Namer()
    {
    }

    public string Name(IShape s)
    {
        {
            if (s is Dot)
            {
                return "a dot";
            }
            else if (s is Line)
            {
                return "a line";
            }
            else
            {
                throw Core.Panic("panic");
            }
        }
    }
}
