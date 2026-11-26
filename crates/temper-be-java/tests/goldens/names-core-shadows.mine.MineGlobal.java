package core_shadows.mine;

public final class MineGlobal {
    private MineGlobal() {}

    static final int Boolean_;

    public static int ignore(int x) {
        return x + 1;
    }

    public static int summary() {
        String_ s = new String_(3);
        List l = new List(4);
        return s.text + s.getDoubled() + l.n + ignore(10) + Boolean_;
    }

    static {
        Boolean_ = 7;
    }
}
