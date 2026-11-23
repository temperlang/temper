package content_tags;

import temper.core.Bubble;
import temper.core.Core;

public final class ContentTagsMain {
    private ContentTagsMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Bubble.keepStackTraces();
        Core.beginTopLevel();
        try {
            Class.forName("content_tags.ContentTagsGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.finishProgram();
    }
}
