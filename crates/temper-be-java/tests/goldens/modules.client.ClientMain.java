package modules.client;

import temper.core.Core;

public final class ClientMain {
    private ClientMain() {}

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
            Class.forName("modules.client.ClientGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.waitUntilTasksComplete();
    }
}
