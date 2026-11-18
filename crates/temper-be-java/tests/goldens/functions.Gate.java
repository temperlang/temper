package functions;

import java.util.function.IntUnaryOperator;
import temper.core.Nullable;

final class Gate {
    public @Nullable IntUnaryOperator check;

    public Gate() {
        this.check = null;
    }

    public boolean allows(int n) {
        @Nullable IntUnaryOperator c = this.check;
        if (c == null) {
            return true;
        } else {
            return c.applyAsInt(n) > 0;
        }
    }

    public int passed(int n) {
        @Nullable IntUnaryOperator c = this.check;
        if (c != null) {
            return c.applyAsInt(n);
        } else {
            return n;
        }
    }

    public @Nullable IntUnaryOperator getCheck() {
        return this.check;
    }

    public void setCheck(@Nullable IntUnaryOperator newCheck) {
        this.check = newCheck;
    }
}
