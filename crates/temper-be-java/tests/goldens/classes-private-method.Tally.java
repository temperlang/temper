package private_method;

import java.util.List;
import temper.core.Core;
import temper.core.Nullable;

final class Tally {
    public final int depth;
    private final int size;

    public Tally(int size, @Nullable Tally parent, List<Tally> children) {
        this.depth = parent == null ? 0 : parent.depth + 1;
        int sum = size;
        {
            int i = 0;
            while (i < children.size()) {
                sum = sum + Core.listGet(children, i).size;
                i = i + 1;
            }
        }
        this.size = sum + this.depth;
    }

    public int getTotal() {
        return this.size;
    }

    public int getDepth() {
        return this.depth;
    }

    public static final class Builder {
        int size;
        boolean size__set;

        public Builder size(int size) {
            size__set = true;
            this.size = size;
            return this;
        }

        @Nullable Tally parent;
        boolean parent__set;

        public Builder parent(@Nullable Tally parent) {
            parent__set = true;
            this.parent = parent;
            return this;
        }

        List<Tally> children;

        public Builder children(List<Tally> children) {
            this.children = children;
            return this;
        }

        public Tally build() {
            if (!size__set || !parent__set || children == null) {
                StringBuilder _message = new StringBuilder("Missing required fields:");
                if (!size__set) {
                    _message.append(" size");
                }
                if (!parent__set) {
                    _message.append(" parent");
                }
                if (children == null) {
                    _message.append(" children");
                }
                throw new IllegalStateException(_message.toString());
            }
            return new Tally(size, parent, children);
        }
    }
}
