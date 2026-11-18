namespace Modules;

internal class Engine
{
    private readonly string name;

    public Engine(string name)
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
}
