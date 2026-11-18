package property_members;

import java.util.List;
import temper.core.Core;

interface Holder<T> {
    T getHeld();

    default List<T> heldAnd(T other) {
        return Core.listOf(this.getHeld(), other);
    }
}
