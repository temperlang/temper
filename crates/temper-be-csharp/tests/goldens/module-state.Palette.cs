namespace ModuleState;

public class Palette
{
    private readonly string name;

    public Palette(string name)
    {
        this.name = name;
    }

    public string Name
    {
        get
        {
            return this.name;
        }
    }

    public static Palette Warm
    {
        get
        {
            return ModuleStateGlobal.paletteWarm;
        }
    }

    public static Palette Named(string n)
    {
        return new Palette(ModuleStateGlobal.prefixes[0] + n);
    }
}
