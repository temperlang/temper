package asserts;

import java.util.List;

public final class AssertsGlobal {
    private AssertsGlobal() {}

    static final List<Vec2> unitVecs;

    static double normSquared(Vec2 vec) {
        return vec.x * vec.x + vec.y * vec.y;
    }

    static {
        unitVecs = List.<Vec2>of(new Vec2(1.0, 0.0), new Vec2(0.0, 1.0));
    }
}
