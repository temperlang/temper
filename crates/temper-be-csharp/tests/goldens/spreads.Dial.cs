namespace Spreads;

internal class Dial
{
    private int level;
    private readonly string? note;
    private readonly int twice;

    public Dial(int? level = null, string? note = null, int? twice = null)
    {
        int level2 = level ?? 5;
        string? note2 = note ?? "n";
        int twice2 = twice ?? level2 * 2;
        this.level = level2;
        this.note = note2;
        this.twice = twice2;
    }

    public int Level
    {
        get
        {
            return this.level;
        }
        set
        {
            this.level = value;
        }
    }

    public string? Note
    {
        get
        {
            return this.note;
        }
    }

    public int Twice
    {
        get
        {
            return this.twice;
        }
    }
}
