namespace Spreads;

internal class Square : IShape
{
    private readonly int side;

    public Square(int side)
    {
        this.side = side;
    }

    public int Side
    {
        get
        {
            return this.side;
        }
    }
}
