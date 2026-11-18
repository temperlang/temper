namespace Libraries;

public class Casing
{
    private readonly string name;

    public bool Eq(Casing other)
    {
        return this.name == other.name;
    }

    public Casing(string name)
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

    public override string ToString()
    {
        return this.name;
    }

    public static Casing First
    {
        get
        {
            return LibrariesGlobal.casingFirst;
        }
    }

    internal static Casing Named(string name)
    {
        return new Casing(name);
    }

    public static Casing Last
    {
        get
        {
            return LibrariesGlobal.casingLast;
        }
    }

    private static string Mark
    {
        get
        {
            return LibrariesGlobal.casingMark;
        }
    }

    public string Marked
    {
        get
        {
            return Casing.Mark + this.name;
        }
    }
}
