namespace GenericNullChecks;

public class Named
{
    private readonly string name;

    public Named(string name)
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
