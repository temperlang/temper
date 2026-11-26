package generics;

import java.util.List;
import temper.core.Core;
import temper.core.Nullable;

public final class Shelf<T extends Named> {
    public final List<T> items;

    public Shelf(List<T> items) {
        this.items = items;
    }

    public @Nullable String firstName() {
        if (this.items.isEmpty()) {
            return null;
        } else {
            return Core.listGet(this.items, 0).name;
        }
    }

    public <TName extends Named> int tally(int extra) {
        return this.items.size() + extra;
    }

    public List<T> getItems() {
        return this.items;
    }
}
