package content_tags;

import java.util.function.BiConsumer;

public final class Picker {
    public final boolean strict;

    public Picker(boolean strict) {
        this.strict = strict;
    }

    public Escaper escaperFor(boolean inQuote, BiConsumer<String, Boolean> callout) {
        if (this.strict && inQuote) {
            callout.accept("a value in quotes", false);
        }
        if (inQuote) {
            return new Both(TextEscaper.getInstance(), QuoteEscaper.getInstance());
        } else {
            return TextEscaper.getInstance();
        }
    }

    public boolean isStrict() {
        return this.strict;
    }
}
