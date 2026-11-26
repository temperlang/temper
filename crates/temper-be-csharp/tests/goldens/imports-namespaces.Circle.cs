using Namespaces.Shapes;

namespace Namespaces;

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

    public int Area()
    {
        return 3 * this.r * this.r;
    }
}
