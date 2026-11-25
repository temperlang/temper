using System.Globalization;
using System.Text;
using TemperLang.Core;

namespace Shifty;

public static class ShiftyGlobal
{
    internal static readonly ILoggingConsole console;

    internal static string InsertUnderscores(string hexString)
    {
        StringBuilder sb = new StringBuilder();
        int idx = 0;
        int end = hexString.Length;
        if (idx.CompareTo(end) < 0)
        {
            int possibleSign = StringUtil.Get(hexString, idx);
            if (possibleSign == 43 || possibleSign == 45)
            {
                idx = StringUtil.Next(hexString, idx);
                StringUtil.AppendBetween(sb, hexString, 0, idx);
            }
        }
        int nFirstGroup = Core.Mod(StringUtil.CountBetween(hexString, idx, end), 4);
        bool needsUnderscore = false;
        if (nFirstGroup != 0)
        {
            int groupStart = idx;
            {
                int i = 0;
                while (i < nFirstGroup)
                {
                    idx = StringUtil.Next(hexString, idx);
                    i = i + 1;
                }
            }
            StringUtil.AppendBetween(sb, hexString, groupStart, idx);
            needsUnderscore = true;
        }
        while (idx.CompareTo(end) < 0)
        {
            int groupStart = idx;
            idx = StringUtil.Step(hexString, idx, 4);
            if (needsUnderscore)
            {
                sb.Append('_');
            }
            StringUtil.AppendBetween(sb, hexString, groupStart, idx);
            needsUnderscore = true;
        }
        return sb.ToString();
    }

    internal static string Fmt32(int n)
    {
        return InsertUnderscores(Core.ConvertToString(n, 16));
    }

    internal static string Fmt64(long n)
    {
        return InsertUnderscores(Core.ConvertToString(n, 16));
    }

    internal static void And32(int a, int b)
    {
        int c = a & b;
        console.Log("0x:" + Fmt32(a) + " & " + Fmt32(b) + " == " + Fmt32(c));
    }

    internal static void And64(long a, long b)
    {
        long c = a & b;
        console.Log("0x:" + Fmt64(a) + " & " + Fmt64(b) + " == " + Fmt64(c));
    }

    internal static void Or32(int a, int b)
    {
        int c = a | b;
        console.Log("0x:" + Fmt32(a) + " | " + Fmt32(b) + " == " + Fmt32(c));
    }

    internal static void Or64(long a, long b)
    {
        long c = a | b;
        console.Log("0x:" + Fmt64(a) + " | " + Fmt64(b) + " == " + Fmt64(c));
    }

    internal static void BComp32(int n)
    {
        console.Log("0x:~" + Fmt32(n) + " == " + Fmt32(~n));
    }

    internal static void BComp64(long n)
    {
        console.Log("0x:~" + Fmt64(n) + " == " + Fmt64(~n));
    }

    internal static void Shl32(int a, int b)
    {
        int c = a << b;
        console.Log("0x:" + Fmt32(a) + " << " + b.ToString(CultureInfo.InvariantCulture) + " == " + Fmt32(c));
    }

    internal static void Shl64(long a, int b)
    {
        long c = a << b;
        console.Log("0x:" + Fmt64(a) + " << " + b.ToString(CultureInfo.InvariantCulture) + " == " + Fmt64(c));
    }

    internal static void Shr32(int a, int b)
    {
        int c = a >> b;
        console.Log("0x:" + Fmt32(a) + " >> " + b.ToString(CultureInfo.InvariantCulture) + " == " + Fmt32(c));
    }

    internal static void Shr64(long a, int b)
    {
        long c = a >> b;
        console.Log("0x:" + Fmt64(a) + " >> " + b.ToString(CultureInfo.InvariantCulture) + " == " + Fmt64(c));
    }

    internal static void UShr32(int a, int b)
    {
        int c = Core.UShr(a, b);
        console.Log("0x:" + Fmt32(a) + " >>> " + b.ToString(CultureInfo.InvariantCulture) + " == " + Fmt32(c));
    }

    internal static void UShr64(long a, int b)
    {
        long c = Core.UShr(a, b);
        console.Log("0x:" + Fmt64(a) + " >>> " + b.ToString(CultureInfo.InvariantCulture) + " == " + Fmt64(c));
    }

    static ShiftyGlobal()
    {
        console = Support.Logging.LoggingConsoleFactory.CreateConsole("Shifty");
        console.Log("and32");
        And32(0, 0);
        And32(3, 1);
        And32(2, 3);
        And32(-2, 7);
        And32(-809434545, 901070595);
        console.Log("and64");
        And64(0L, 0L);
        And64(3L, 1L);
        And64(2L, 3L);
        And64(-2L, 7L);
        And64(297421889964898413L, -3505189831424144594L);
        console.Log("or32");
        Or32(0, 0);
        Or32(0, 1);
        Or32(2, 1);
        Or32(2, 2);
        Or32(3, 1);
        Or32(2, 3);
        Or32(-2, 7);
        Or32(-809434545, 901070595);
        console.Log("or64");
        Or64(0L, 0L);
        Or64(0L, 1L);
        Or64(2L, 1L);
        Or64(2L, 2L);
        Or64(3L, 1L);
        Or64(2L, 3L);
        Or64(-2L, 7L);
        Or64(297421889964898413L, -3505189831424144594L);
        console.Log("bComp32");
        BComp32(0);
        BComp32(1);
        BComp32(-1);
        BComp32(2);
        BComp32(-2);
        BComp32(-809434545);
        BComp32(901070595);
        console.Log("bComp64");
        BComp64(0L);
        BComp64(1L);
        BComp64(-1L);
        BComp64(2L);
        BComp64(-2L);
        BComp64(297421889964898413L);
        BComp64(-3505189831424144594L);
        console.Log("shl32");
        Shl32(5, 0);
        Shl32(5, 1);
        Shl32(5, 2);
        Shl32(5, 3);
        Shl32(5, 4);
        Shl32(5, 5);
        Shl32(5, 20);
        Shl32(5, 28);
        Shl32(5, 29);
        Shl32(5, 30);
        Shl32(5, 31);
        Shl32(5, 32);
        Shl32(5, 33);
        Shl32(5, -1);
        Shl32(5, -30);
        Shl32(-1, 0);
        Shl32(-1, 1);
        Shl32(-1, 2);
        console.Log("shl64");
        Shl64(5L, 0);
        Shl64(5L, 1);
        Shl64(5L, 2);
        Shl64(5L, 3);
        Shl64(5L, 4);
        Shl64(5L, 5);
        Shl64(5L, 33);
        Shl64(5L, 62);
        Shl64(5L, 63);
        Shl64(5L, 64);
        Shl64(5L, -1);
        Shl64(5L, -60);
        Shl64(-1L, 0);
        Shl64(-1L, 1);
        Shl64(-1L, 2);
        console.Log("shr32");
        Shr32(128, 0);
        Shr32(128, 1);
        Shr32(128, 2);
        Shr32(128, 3);
        Shr32(128, 4);
        Shr32(128, 5);
        Shr32(128, 6);
        Shr32(128, 7);
        Shr32(128, 8);
        Shr32(128, 9);
        Shr32(128, 31);
        Shr32(128, 32);
        Shr32(128, 33);
        Shr32(128, -1);
        Shr32(128, -30);
        Shr32(-128, 0);
        Shr32(-128, 1);
        Shr32(-128, 2);
        Shr32(-128, 3);
        Shr32(-128, 4);
        console.Log("shr64");
        Shr64(128L, 0);
        Shr64(128L, 1);
        Shr64(128L, 2);
        Shr64(128L, 3);
        Shr64(128L, 4);
        Shr64(128L, 5);
        Shr64(128L, 6);
        Shr64(128L, 7);
        Shr64(128L, 8);
        Shr64(128L, 9);
        Shr64(128L, 32);
        Shr64(128L, 63);
        Shr64(128L, 64);
        Shr64(128L, 65);
        Shr64(128L, -1);
        Shr64(128L, -62);
        Shr64(-128L, 0);
        Shr64(-128L, 1);
        Shr64(-128L, 2);
        Shr64(-128L, 3);
        Shr64(-128L, 4);
        console.Log("uShr32");
        UShr32(128, 0);
        UShr32(128, 1);
        UShr32(128, 2);
        UShr32(128, 3);
        UShr32(128, 4);
        UShr32(128, 5);
        UShr32(128, 6);
        UShr32(128, 7);
        UShr32(128, 8);
        UShr32(128, 9);
        UShr32(128, 31);
        UShr32(128, 32);
        UShr32(128, 33);
        UShr32(128, -1);
        UShr32(128, -30);
        UShr32(-128, 0);
        UShr32(-128, 1);
        UShr32(-128, 2);
        UShr32(-128, 3);
        UShr32(-128, 4);
        console.Log("uShr64");
        UShr64(128L, 0);
        UShr64(128L, 1);
        UShr64(128L, 2);
        UShr64(128L, 3);
        UShr64(128L, 4);
        UShr64(128L, 5);
        UShr64(128L, 6);
        UShr64(128L, 7);
        UShr64(128L, 8);
        UShr64(128L, 9);
        UShr64(128L, 32);
        UShr64(128L, 63);
        UShr64(128L, 64);
        UShr64(128L, 65);
        UShr64(128L, -1);
        UShr64(128L, -62);
        UShr64(-128L, 0);
        UShr64(-128L, 1);
        UShr64(-128L, 2);
        UShr64(-128L, 3);
        UShr64(-128L, 4);
    }
}
