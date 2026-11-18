using System.Runtime.CompilerServices;
using TemperLang.Core;

static class Program
{
    static void Main()
    {
        Core.InitSimpleLogging();
        // Holds off async blocks until top-level code is done.
        Async.BeginTopLevel();
        try
        {
            RuntimeHelpers.RunClassConstructor(typeof(Program).TypeHandle);
        }
        finally
        {
            Async.EndTopLevel();
        }
        Async.WaitUntilSafeToExit();
    }
}
