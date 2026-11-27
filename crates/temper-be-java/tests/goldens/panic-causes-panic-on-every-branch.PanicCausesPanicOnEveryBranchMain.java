package panic_causes_panic_on_every_branch;

import temper.core.Core;

public final class PanicCausesPanicOnEveryBranchMain {
    private PanicCausesPanicOnEveryBranchMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Core.keepStackTraces();
        Core.beginTopLevel();
        try {
            Class.forName("panic_causes_panic_on_every_branch.PanicCausesPanicOnEveryBranchGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.finishProgram();
    }
}
