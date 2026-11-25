package classes;

import temper.core.Nullable;

final class Theme {
    public final String fg;
    public final String bg;
    public final int size;
    public final boolean bold;

    public Theme(String fg, String bg, @Nullable Integer size, @Nullable Boolean bold) {
        int size2 = size != null ? size : 12;
        boolean bold2 = bold != null ? bold : false;
        this.fg = fg;
        this.bg = bg;
        this.size = size2;
        this.bold = bold2;
    }

    public Theme(String fg, String bg, @Nullable Integer size) {
        this(fg, bg, size, null);
    }

    public Theme(String fg, String bg) {
        this(fg, bg, null, null);
    }

    public String getWeight() {
        if (this.bold) {
            return "bold";
        } else {
            return "plain";
        }
    }

    public String toString() {
        return this.fg + "/" + this.bg + " " + this.size + " " + this.getWeight();
    }

    public String getFg() {
        return this.fg;
    }

    public String getBg() {
        return this.bg;
    }

    public int getSize() {
        return this.size;
    }

    public boolean isBold() {
        return this.bold;
    }

    public static final class Builder {
        String fg;

        public Builder fg(String fg) {
            this.fg = fg;
            return this;
        }

        String bg;

        public Builder bg(String bg) {
            this.bg = bg;
            return this;
        }

        @Nullable Integer size;

        public Builder size(@Nullable Integer size) {
            this.size = size;
            return this;
        }

        @Nullable Boolean bold;

        public Builder bold(@Nullable Boolean bold) {
            this.bold = bold;
            return this;
        }

        public Theme build() {
            if (fg == null || bg == null) {
                StringBuilder _message = new StringBuilder("Missing required fields:");
                if (fg == null) {
                    _message.append(" fg");
                }
                if (bg == null) {
                    _message.append(" bg");
                }
                throw new IllegalStateException(_message.toString());
            }
            return new Theme(fg, bg, size, bold);
        }
    }
}
