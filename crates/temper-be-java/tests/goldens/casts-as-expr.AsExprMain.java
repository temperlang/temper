package as_expr;

import temper.core.Core;

public final class AsExprMain {
    private AsExprMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Core.keepStackTraces();
        Core.beginTopLevel();
        try {
            Class.forName("as_expr.AsExprGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.finishProgram();
    }
}
