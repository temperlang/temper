package libraries;

import java.util.List;
import temper.core.Bubble;
import temper.core.Core;
import temper.core.Nullable;

public final class Grid {
    public final int ncols;
    public final List<Double> values;

    public Grid(int ncols, List<Double> values) {
        this.ncols = ncols;
        this.values = values;
    }

    public double get(int i, int j) {
        return Core.listGet(this.values, i * this.ncols + j);
    }

    public static @Nullable Grid parse(String s) {
        try {
            return LibrariesGlobal.parseGrid(s);
        } catch (Bubble ignored) {
            return null;
        }
    }

    public int getNcols() {
        return this.ncols;
    }

    public List<Double> getValues() {
        return this.values;
    }

    public static final class Builder {
        int ncols;
        boolean ncols__set;

        public Builder ncols(int ncols) {
            ncols__set = true;
            this.ncols = ncols;
            return this;
        }

        List<Double> values;

        public Builder values(List<Double> values) {
            this.values = values;
            return this;
        }

        public Grid build() {
            if (!ncols__set || values == null) {
                StringBuilder _message = new StringBuilder("Missing required fields:");
                if (!ncols__set) {
                    _message.append(" ncols");
                }
                if (values == null) {
                    _message.append(" values");
                }
                throw new IllegalStateException(_message.toString());
            }
            return new Grid(ncols, values);
        }
    }
}
