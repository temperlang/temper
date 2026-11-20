package content_tags;

public final class TextEscaper implements Escaper {
    private static final TextEscaper instance = new TextEscaper();

    public TextEscaper() {}

    public String applyString(String x) {
        return "[" + x + "]";
    }

    public static TextEscaper getInstance() {
        return TextEscaper.instance;
    }
}
