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
            RuntimeHelpers.RunClassConstructor(typeof(Modules.Core.CoreGlobal).TypeHandle);
            RuntimeHelpers.RunClassConstructor(typeof(Renamed.RenamedGlobal).TypeHandle);
            RuntimeHelpers.RunClassConstructor(typeof(Modules.ModulesGlobal).TypeHandle);
            RuntimeHelpers.RunClassConstructor(typeof(Modules.Helpers.HelpersGlobal).TypeHandle);
            RuntimeHelpers.RunClassConstructor(typeof(ImportsLibrary.ImportsLibraryGlobal).TypeHandle);
        }
        finally
        {
            global::TemperLang.Core.Async.EndTopLevel();
        }
        global::TemperLang.Core.Async.WaitUntilSafeToExit();
    }
}
