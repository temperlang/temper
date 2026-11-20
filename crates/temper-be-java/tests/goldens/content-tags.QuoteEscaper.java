package content_tags;

public final class QuoteEscaper implements Escaper {
    private static final QuoteEscaper instance = new QuoteEscaper();

    public QuoteEscaper() {}

    public String applyString(String x) {
        return "(" + x + ")";
    }

    public static QuoteEscaper getInstance() {
        return QuoteEscaper.instance;
    }
}
