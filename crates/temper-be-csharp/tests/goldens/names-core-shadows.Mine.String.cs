namespace CoreShadows.Mine;

public class String
{
    private readonly int text;

    public String(int text)
    {
        this.text = text;
    }

    public int Text
    {
        get
        {
            return this.text;
        }
    }

    public int Doubled
    {
        get
        {
            return this.text * 2;
        }
    }
}
