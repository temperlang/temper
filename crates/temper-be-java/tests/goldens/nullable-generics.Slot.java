package nullable_generics;

import temper.core.Nullable;

public final class Slot<T> {
    public final T value;

    public Slot(T value) {
        this.value = value;
    }

    public @Nullable T kept(boolean keep) {
        if (keep) {
            return this.value;
        } else {
            return null;
        }
    }

    public T getValue() {
        return this.value;
    }
}
