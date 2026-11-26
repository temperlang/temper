namespace Classes;

internal class Span
{
    private readonly int start;
    private readonly int end;
    private readonly int length;

    public Span(int start, int end)
    {
        this.start = start;
        this.end = end;
        this.length = this.end - this.start;
    }

    public int Start
    {
        get
        {
            return this.start;
        }
    }

    public int End
    {
        get
        {
            return this.end;
        }
    }

    public int Length
    {
        get
        {
            return this.length;
        }
    }
}
