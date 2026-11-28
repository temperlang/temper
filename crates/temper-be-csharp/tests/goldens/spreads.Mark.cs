namespace Spreads;

internal class Mark
{
    private readonly string? note;
    private readonly int size;

    public Mark(string? note = null, int? size = null)
    {
        string? note2 = note ?? "n";
        int size2 = size ?? 2;
        this.note = note2;
        this.size = size2;
    }

    public string? Note
    {
        get
        {
            return this.note;
        }
    }

    public int Size
    {
        get
        {
            return this.size;
        }
    }
}
