package modules.machine;

import temper.core.Core;

public final class MachineMain {
    private MachineMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Core.beginTopLevel();
        try {
            Class.forName("temper.std.regex.RegexGlobal");
            Class.forName("modules.state.StateGlobal");
            Class.forName("modules.parts.regex.RegexGlobal");
            Class.forName("modules.machine.MachineGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.waitUntilTasksComplete();
    }
}
