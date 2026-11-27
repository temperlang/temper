package spreads;

import temper.core.Nullable;

final class Mark {
    public final @Nullable String note;
    public final int size;

    public Mark(@Nullable String note, @Nullable Integer size) {
        @Nullable String note2 = note != null ? note : "n";
        int size2 = size != null ? size : 2;
        this.note = note2;
        this.size = size2;
    }

    public Mark(@Nullable String note) {
        this(note, null);
    }

    public Mark() {
        this(null, null);
    }

    public @Nullable String getNote() {
        return this.note;
    }

    public int getSize() {
        return this.size;
    }

    public static final class Builder {
        @Nullable String note;

        public Builder note(@Nullable String note) {
            this.note = note;
            return this;
        }

        @Nullable Integer size;

        public Builder size(@Nullable Integer size) {
            this.size = size;
            return this;
        }

        public Mark build() {
            return new Mark(note, size);
        }
    }
}
