using System;
using System.Threading.Tasks;

namespace TemperLang.Std.Keyboard
{
    public static class KeyboardSupport
    {
        public static async Task<string?> StdNextKeypress()
        {
            return await Task.Run(() =>
            {
                if (Console.IsInputRedirected) return null;
                var key = Console.ReadKey(true);
                switch (key.Key)
                {
                    case ConsoleKey.UpArrow: return "ArrowUp";
                    case ConsoleKey.DownArrow: return "ArrowDown";
                    case ConsoleKey.LeftArrow: return "ArrowLeft";
                    case ConsoleKey.RightArrow: return "ArrowRight";
                    case ConsoleKey.Escape: return "Escape";
                    case ConsoleKey.Enter: return "Enter";
                    case ConsoleKey.Backspace: return "Backspace";
                    case ConsoleKey.Tab: return "Tab";
                    default: return key.KeyChar.ToString();
                }
            });
        }
    }
}
