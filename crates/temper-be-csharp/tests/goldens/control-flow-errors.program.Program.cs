using System.Runtime.CompilerServices;
using TemperLang.Core;

static class Program
{
    static void Main()
    {
        Core.InitSimpleLogging();
        // Holds off async blocks until top-level code is done.
        global::TemperLang.Core.Async.BeginTopLevel();
        try
        {
            RuntimeHelpers.RunClassConstructor(typeof(Errors.ErrorsGlobal).TypeHandle);
        }
        finally
        {
            global::TemperLang.Core.Async.EndTopLevel();
        }
        global::TemperLang.Core.Async.WaitUntilSafeToExit();
    }
}
