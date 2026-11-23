namespace SealedWhens;

internal class Dot : IShape
{
    private readonly int size;

    public Dot(int size)
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
