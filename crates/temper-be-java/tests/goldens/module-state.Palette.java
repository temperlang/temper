package module_state;

import temper.core.Core;

public final class Palette {
    public final String name;
    static Palette warm;

    public Palette(String name) {
        this.name = name;
    }

    public static Palette named(String n) {
        return new Palette(Core.listGet(ModuleStateGlobal.prefixes, 0) + n);
    }

    public String getName() {
        return this.name;
    }

    public static Palette getWarm() {
        return Palette.warm;
    }

    static {
        ModuleStateGlobal.initStatics();
    }
}
