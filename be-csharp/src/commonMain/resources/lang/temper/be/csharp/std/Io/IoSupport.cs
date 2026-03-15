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
                    return Console.ReadLine();
                }
                catch (Exception)
                {
                    return null;
                }
            });
        }
    }
}
