package libraries;

import java.util.List;
import temper.core.Core;

public interface Counting<T> extends Stepper<T> {
    int getCount();

    void setCount(int c);

    T made();

    default List<T> step() {
        this.setCount(this.getCount() + 1);
        return Core.<T>listOf(this.made());
    }
}
