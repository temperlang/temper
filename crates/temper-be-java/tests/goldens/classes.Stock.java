package classes;

import java.util.List;
import temper.core.Nullable;

final class Stock {
    public final int count;
    private List<Integer> itemsVar;
    public final int size;

    public List<Integer> getItems() {
        return this.itemsVar;
    }

    public void setItems(List<Integer> value) {
        this.itemsVar = value;
    }

    public Stock(List<Integer> items, @Nullable Integer count) {
        int count2 = count != null ? count : items.size();
        this.count = count2;
        this.itemsVar = items;
        this.size = items.size();
    }

    public Stock(List<Integer> items) {
        this(items, null);
    }

    public int sizeNow() {
        return this.getItems().size();
    }

    public int getCount() {
        return this.count;
    }

    public int getSize() {
        return this.size;
    }

    public static final class Builder {
        List<Integer> items;

        public Builder items(List<Integer> items) {
            this.items = items;
            return this;
        }

        @Nullable Integer count;

        public Builder count(@Nullable Integer count) {
            this.count = count;
            return this;
        }

        public Stock build() {
            if (items == null) {
                StringBuilder _message = new StringBuilder("Missing required fields:");
                if (items == null) {
                    _message.append(" items");
                }
                throw new IllegalStateException(_message.toString());
            }
            return new Stock(items, count);
        }
    }
}
