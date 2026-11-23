package property_members;

import temper.core.Bubble;
import temper.core.Core;

public final class PropertyMembersMain {
    private PropertyMembersMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Bubble.keepStackTraces();
        Core.beginTopLevel();
        try {
            Class.forName("property_members.PropertyMembersGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.finishProgram();
    }
}
