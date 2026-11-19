package generics;

import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Map.Entry;

public final class Box<T> implements Holder<T> {
    public final T value;

    public Box(T value) {
        this.value = value;
    }

    public T getHeld() {
        return this.value;
    }

    public Entry<T, Integer> pairWith(int other) {
        return new SimpleImmutableEntry<T, Integer>(this.value, other);
    }

    public T getValue() {
        return this.value;
    }
}
