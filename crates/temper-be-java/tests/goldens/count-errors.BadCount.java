package count_errors;

@SuppressWarnings("serial")
public final class BadCount extends CountException {
    public final String text;

    public BadCount(String text) {
        this.text = text;
    }

    public String getMessage() {
        return "bad count (text: " + this.text + ")";
    }

    public String getText() {
        return this.text;
    }
}
