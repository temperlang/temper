namespace CoreShadows.Mine;

public static class MineGlobal
{
    internal static readonly int Boolean;

    public static int Ignore(int x)
    {
        return x + 1;
    }

    public static int Summary()
    {
        String s = new String(3);
        List l = new List(4);
        return s.Text + s.Doubled + l.N + Ignore(10) + Boolean;
    }

    static MineGlobal()
    {
        Boolean = 7;
    }
}
