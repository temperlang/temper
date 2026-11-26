package classes;

import temper.core.Nullable;

final class Theme {
    public final String fg;
    public final String bg;
    public final int size;
    public final boolean bold;
    public final @Nullable String note;

    public Theme(String fg, String bg, int size, boolean bold, @Nullable String note) {
        this.fg = fg;
        this.bg = bg;
        this.size = size;
        this.bold = bold;
        this.note = note;
    }

    public Theme(String fg, String bg, int size, boolean bold) {
        this(fg, bg, size, bold, null);
    }

    public String weight() {
        if (this.bold) {
            return "bold";
        } else {
            return "plain";
        }
    }

    public String toString() {
        String t = this.fg + "/" + this.bg + " " + this.size + " " + this.weight();
        @Nullable String subject = this.note;
        return t + (subject == null ? "" : subject);
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

    public @Nullable String getNote() {
        return this.note;
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

        int size;
        boolean size__set;

        public Builder size(int size) {
            size__set = true;
            this.size = size;
            return this;
        }

        boolean bold;
        boolean bold__set;

        public Builder bold(boolean bold) {
            bold__set = true;
            this.bold = bold;
            return this;
        }

        @Nullable String note;

        public Builder note(@Nullable String note) {
            this.note = note;
            return this;
        }

        public Theme build() {
            if (fg == null || bg == null || !size__set || !bold__set) {
                StringBuilder _message = new StringBuilder("Missing required fields:");
                if (fg == null) {
                    _message.append(" fg");
                }
                if (bg == null) {
                    _message.append(" bg");
                }
                if (!size__set) {
                    _message.append(" size");
                }
                if (!bold__set) {
                    _message.append(" bold");
                }
                throw new IllegalStateException(_message.toString());
            }
            return new Theme(fg, bg, size, bold, note);
        }
    }
}
