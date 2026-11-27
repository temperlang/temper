package spreads;

import temper.core.Nullable;

final class Dial {
    public int level;
    public final @Nullable String note;
    public final int twice;

    public Dial(@Nullable Integer level, @Nullable String note, @Nullable Integer twice) {
        int level2 = level != null ? level : 5;
        @Nullable String note2 = note != null ? note : "n";
        int twice2 = twice != null ? twice : level2 * 2;
        this.level = level2;
        this.note = note2;
        this.twice = twice2;
    }

    public Dial(@Nullable Integer level, @Nullable String note) {
        this(level, note, null);
    }

    public Dial(@Nullable Integer level) {
        this(level, null, null);
    }

    public Dial() {
        this(null, null, null);
    }

    public int getLevel() {
        return this.level;
    }

    public void setLevel(int newLevel) {
        this.level = newLevel;
    }

    public @Nullable String getNote() {
        return this.note;
    }

    public int getTwice() {
        return this.twice;
    }

    public static final class Builder {
        @Nullable Integer level;

        public Builder level(@Nullable Integer level) {
            this.level = level;
            return this;
        }

        @Nullable String note;

        public Builder note(@Nullable String note) {
            this.note = note;
            return this;
        }

        @Nullable Integer twice;

        public Builder twice(@Nullable Integer twice) {
            this.twice = twice;
            return this;
        }

        public Dial build() {
            return new Dial(level, note, twice);
        }
    }
}
