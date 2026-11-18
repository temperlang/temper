namespace Reach;

internal class TestSquare : ITestShape
{
    private readonly int side;

    public TestSquare(int side)
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

    public int Area()
    {
        return this.side * this.side;
    }
}
