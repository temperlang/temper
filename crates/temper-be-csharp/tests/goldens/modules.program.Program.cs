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
            RuntimeHelpers.RunClassConstructor(typeof(TemperLang.Std.Regex.RegexGlobal).TypeHandle);
            RuntimeHelpers.RunClassConstructor(typeof(Modules.Helpers.Deep.DeepGlobal).TypeHandle);
            RuntimeHelpers.RunClassConstructor(typeof(Modules.Helpers.HelpersGlobal).TypeHandle);
            RuntimeHelpers.RunClassConstructor(typeof(Modules.Core.CoreGlobal).TypeHandle);
            RuntimeHelpers.RunClassConstructor(typeof(Modules.State.StateGlobal).TypeHandle);
            RuntimeHelpers.RunClassConstructor(typeof(Modules.Parts.Regex.RegexGlobal).TypeHandle);
            RuntimeHelpers.RunClassConstructor(typeof(Modules.Machine.MachineGlobal).TypeHandle);
            RuntimeHelpers.RunClassConstructor(typeof(Modules.ModulesGlobal).TypeHandle);
        }
        finally
        {
            global::TemperLang.Core.Async.EndTopLevel();
        }
        global::TemperLang.Core.Async.WaitUntilSafeToExit();
    }
}
