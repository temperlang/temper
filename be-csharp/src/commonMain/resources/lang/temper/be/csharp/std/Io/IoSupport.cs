using System;
using System.Threading.Tasks;

namespace TemperLang.Std.Io
{
    public static class IoSupport
    {
        public static async Task<Tuple<object?>> StdSleep(int ms)
        {
            await Task.Delay(ms);
            return Tuple.Create<object?>(null);
        }

        public static async Task<string?> StdReadLine()
        {
            return await Task.Run(() =>
            {
                try
                {
                    if (Console.IsInputRedirected)
                    {
                        return Console.ReadLine();
                    }
                    var key = Console.ReadKey(true);
                    if (key.Key == ConsoleKey.C && key.Modifiers.HasFlag(ConsoleModifiers.Control))
                    {
                        Environment.Exit(1);
                    }
                    return key.KeyChar.ToString();
                }
                catch (Exception)
                {
                    return null;
                }
            });
        }
    }
}
