namespace Namespaces.Shapes;

public class Square : IShape
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

    public int Area()
    {
        return this.side * this.side;
    }

    public static Square Sized(int side)
    {
        return new Square(side);
    }
}
