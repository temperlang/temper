using Modules.State;
using System.Globalization;

namespace Modules.Machine;

public static class MachineGlobal
{
    public static string Describe()
    {
        global::Modules.State.State state = new global::Modules.State.State(StateGlobal.FirstId);
        global::Modules.State.Engine engine = new global::Modules.State.Engine(global::Modules.Parts.Regex.RegexGlobal.Wheels());
        int least = global::TemperLang.Std.Regex.RegexGlobal.OneOrMore(global::TemperLang.Std.Regex.RegexGlobal.Dot).Min;
        global::Modules.State.Parts parts = new global::Modules.State.Parts(engine.N + least);
        return string.Create(CultureInfo.InvariantCulture, $"state {state.Id} engine {engine.N} parts {parts.Count}");
    }
}
