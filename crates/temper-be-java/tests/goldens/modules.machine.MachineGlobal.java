package modules.machine;

import modules.parts.regex.RegexGlobal;
import modules.state.Engine;
import modules.state.Parts;
import modules.state.State;
import modules.state.StateGlobal;

public final class MachineGlobal {
    private MachineGlobal() {}

    public static String describe() {
        State state = new State(StateGlobal.firstId);
        Engine engine = new Engine(RegexGlobal.wheels());
        int least = temper.std.regex.RegexGlobal.oneOrMore(temper.std.regex.RegexGlobal.Dot).min;
        Parts parts = new Parts(engine.n + least);
        return "state " + state.id + " engine " + engine.n + " parts " + parts.count;
    }
}
