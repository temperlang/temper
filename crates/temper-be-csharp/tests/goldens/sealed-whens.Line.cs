namespace SealedWhens;

internal class Line : IShape
{
    private readonly int size;

    public Line(int size)
    {
        this.size = size;
    }

    public int Size
    {
        get
        {
            return this.size;
        }
    }
}
