package generics;

import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Map.Entry;
import java.util.function.Function;

public final class Box<T> implements Holder<T> {
    public final T value;

    public Box(T value) {
        this.value = value;
    }

    public T getHeld() {
        return this.value;
    }

    public Entry<T, Integer> pairWith(int other) {
        return new SimpleImmutableEntry<>(this.value, other);
    }

    public <TOut> TOut applied(Function<T, TOut> f) {
        return f.apply(this.value);
    }

    public T getValue() {
        return this.value;
    }
}
