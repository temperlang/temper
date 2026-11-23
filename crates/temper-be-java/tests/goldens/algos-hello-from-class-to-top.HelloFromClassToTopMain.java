package hello_from_class_to_top;

import temper.core.Bubble;
import temper.core.Core;

public final class HelloFromClassToTopMain {
    private HelloFromClassToTopMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Bubble.keepStackTraces();
        Core.beginTopLevel();
        try {
            Class.forName("hello_from_class_to_top.HelloFromClassToTopGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.finishProgram();
    }
}
