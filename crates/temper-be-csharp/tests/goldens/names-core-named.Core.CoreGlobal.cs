namespace CoreNamed.Core;

public static class CoreGlobal
{
    public static string Greet(string who)
    {
        return "hello from " + who;
    }

    public static int Share(int n, int d)
    {
        try
        {
            return global::TemperLang.Core.Core.Div(n, d);
        }
        catch
        {
            return 0;
        }
    }
}
