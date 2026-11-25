using System.Globalization;
using TemperLang.Core;

namespace Loops;

public static class LoopsGlobal
{
    static LoopsGlobal()
    {
        ILoggingConsole console = Support.Logging.LoggingConsoleFactory.CreateConsole("Loops");
        {
            int i = 0;
            while (i < 5)
            {
                if (i == 1 || i == 3)
                {
                    {
                        i = i + 1;
                        continue;
                    }
                }
                console.Log(i.ToString(CultureInfo.InvariantCulture));
                i = i + 1;
            }
        }
        {
            int i2 = 0;
            while (i2 < 5)
            {
                if (i2 == 3)
                {
                    break;
                }
                console.Log(i2.ToString(CultureInfo.InvariantCulture));
                i2 = i2 + 1;
            }
        }
        int n = 0;
        {
            {
                int i3 = 0;
                while (i3 < 4)
                {
                    string str = "row " + i3.ToString(CultureInfo.InvariantCulture) + " =";
                    int j = 0;
                    while (true)
                    {
                        int t = n;
                        n = t + 1;
                        str = str + " " + t.ToString(CultureInfo.InvariantCulture);
                        if (i3 <= j)
                        {
                            console.Log(str);
                            {
                                i3 = i3 + 1;
                                goto outerNext;
                            }
                        }
                        j = j + 1;
                    }
                    outerNext: ;
                }
            }
        }
        int prime = 1;
        {
            {
                int i4 = 0;
                while (i4 < 25)
                {
                    {
                        {
                            int j2 = 0;
                            while (j2 < 25)
                            {
                                {
                                    {
                                        int k = 0;
                                        while (k < 25)
                                        {
                                            prime = prime + 1;
                                            if (prime != 2 && Core.Mod(prime, 2) == 0)
                                            {
                                                {
                                                    i4 = i4 + 1;
                                                    goto outerNext2;
                                                }
                                            }
                                            if (prime != 3 && Core.Mod(prime, 3) == 0)
                                            {
                                                {
                                                    j2 = j2 + 1;
                                                    goto outerNext3;
                                                }
                                            }
                                            if (prime != 5 && Core.Mod(prime, 5) == 0)
                                            {
                                                {
                                                    k = k + 1;
                                                    continue;
                                                }
                                            }
                                            if (prime != 7 && Core.Mod(prime, 7) == 0)
                                            {
                                                {
                                                    k = k + 1;
                                                    continue;
                                                }
                                            }
                                            console.Log(prime.ToString(CultureInfo.InvariantCulture));
                                            k = k + 1;
                                        }
                                    }
                                }
                                j2 = j2 + 1;
                                outerNext3: ;
                            }
                        }
                    }
                    i4 = i4 + 1;
                    outerNext2: ;
                }
            }
        }
        int val = 0;
        {
            {
                int i5 = 0;
                while (i5 < 10)
                {
                    string str2 = "row " + i5.ToString(CultureInfo.InvariantCulture) + " =";
                    int j3 = 0;
                    while (true)
                    {
                        int t2 = val;
                        val = t2 + 1;
                        str2 = str2 + " " + t2.ToString(CultureInfo.InvariantCulture);
                        if (i5 == 4)
                        {
                            console.Log("break");
                            goto outerEnd;
                        }
                        if (i5 <= j3)
                        {
                            console.Log(str2);
                            {
                                i5 = i5 + 1;
                                goto outerNext4;
                            }
                        }
                        j3 = j3 + 1;
                    }
                    outerNext4: ;
                }
                outerEnd: ;
            }
        }
    }
}
