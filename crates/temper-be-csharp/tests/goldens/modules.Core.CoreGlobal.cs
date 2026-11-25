namespace Modules.Core;

public static class CoreGlobal
{
    public static int Share(int n, int d)
    {
        return global::TemperLang.Core.Core.Div(n, d);
    }

    public static Ratio? RatioOf(int n, int d)
    {
        if (d == 0)
        {
            return null;
        }
        else
        {
            return new Ratio(n, d);
        }
    }
}
