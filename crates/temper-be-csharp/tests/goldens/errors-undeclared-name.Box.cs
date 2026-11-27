using TemperLang.Core;

namespace ErrorsUndeclaredName;

internal class Box
{
    private readonly int value;

    public Box(int value)
    {
        this.value = value;
    }

    public int Value
    {
        get
        {
            return this.value;
        }
    }

    public int Get()
    {
        return this.value;
    }

    public string Bad()
    {
        throw Core.Panic("panic");
    }
}
