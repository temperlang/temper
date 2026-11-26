package java_statics;

public final class Policy {
    public final String names;
    static Policy standard;
    static boolean strict;
    private static final Policy textOnly = new Policy("");
    private static final int limit = 3;
    private static final String label = "policy";
    private static final boolean on = true;
    private static final Policy hidden = new Policy("x");

    public Policy(String names) {
        this.names = names;
    }

    public static String peek() {
        return Policy.hidden.names;
    }

    public String getNames() {
        return this.names;
    }

    public static Policy getStandard() {
        return Policy.standard;
    }

    public static boolean isStrict() {
        return Policy.strict;
    }

    public static Policy getTextOnly() {
        return Policy.textOnly;
    }

    public static int getLimit() {
        return Policy.limit;
    }

    public static String getLabel() {
        return Policy.label;
    }

    public static boolean isOn() {
        return Policy.on;
    }

    static {
        JavaStaticsGlobal.initStatics();
    }
}
