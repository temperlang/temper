package modules;

import temper.core.Core;

public final class ModulesMain {
    private ModulesMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Core.beginTopLevel();
        try {
            Class.forName("temper.std.regex.RegexGlobal");
            Class.forName("modules.helpers.deep.DeepGlobal");
            Class.forName("modules.helpers.HelpersGlobal");
            Class.forName("modules.core.CoreGlobal");
            Class.forName("modules.state.StateGlobal");
            Class.forName("modules.parts.regex.RegexGlobal");
            Class.forName("modules.machine.MachineGlobal");
            Class.forName("modules.ModulesGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.waitUntilTasksComplete();
    }
}
