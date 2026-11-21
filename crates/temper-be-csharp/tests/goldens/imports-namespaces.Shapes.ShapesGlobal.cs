namespace Namespaces.Shapes;

public static class ShapesGlobal
{
    public static readonly int Unit;

    public static int Twice(int x)
    {
        return x * 2;
    }

    static ShapesGlobal()
    {
        Unit = 1;
    }
}
