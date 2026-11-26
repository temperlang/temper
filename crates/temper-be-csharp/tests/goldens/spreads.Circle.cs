namespace Spreads;

internal class Circle : IShape
{
    private readonly int r;

    public Circle(int r)
    {
        this.r = r;
    }

    public int R
    {
        get
        {
            return this.r;
        }
    }
}
