package property_members;

import java.util.List;

interface Labeled<L, T> extends Holder<T> {
    String label(L l);

    default List<T> heldAnd(T other) {
        return Holder.super.heldAnd(other);
    }
}
