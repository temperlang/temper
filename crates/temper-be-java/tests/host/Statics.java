package statics;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java_statics.JavaStaticsGlobal;
import java_statics.Policy;
import temper.core.Core;

/**
 * Host code using the generated {@code java_statics} package's statics,
 * for {@code functional.rs}'s {@code runs}: it loads {@code Policy} before
 * the module's class, reads its statics through their getters, and
 * lists the static fields code outside the package reaches, which
 * should be none.
 */
public final class Statics {
    private Statics() {}

    /**
     * Runs the checks.
     *
     * @param args unused
     */
    public static void main(String[] args) {
        Core.initSimpleLogging();
        Core.beginTopLevel();
        Policy standard;
        try {
            // Policy first: its static block runs the module's init.
            standard = Policy.getStandard();
        } finally {
            Core.endTopLevel();
        }
        System.out.println("standard " + standard.getNames());
        System.out.println("default " + JavaStaticsGlobal.describe());
        System.out.println("textOnly [" + Policy.getTextOnly().getNames() + "]");
        System.out.println("strict " + Policy.isStrict());
        System.out.println("literals " + Policy.getLimit() + " " + Policy.getLabel() + " " + Policy.isOn());
        // Static fields code outside the package reaches: none.
        List<String> open = new ArrayList<>();
        for (Field f : Policy.class.getDeclaredFields()) {
            int m = f.getModifiers();
            if (Modifier.isStatic(m) && (Modifier.isPublic(m) || Modifier.isProtected(m))) {
                open.add(f.getName());
            }
        }
        System.out.println("public static fields " + open);
    }
}
