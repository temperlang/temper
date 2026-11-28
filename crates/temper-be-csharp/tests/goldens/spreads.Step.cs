namespace Spreads;

internal class Step
{
    private readonly int n;
    private readonly string tag;

    public Step(int n, string tag)
    {
        this.n = n;
        this.tag = tag;
    }

    public int N
    {
        get
        {
            return this.n;
        }
    }

    public string Tag
    {
        get
        {
            return this.tag;
        }
    }

    public Step Next()
    {
        {
            Step spread = this;
            return new Step(this.n + 1, spread.tag);
        }
    }
}
