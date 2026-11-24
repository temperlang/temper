package failures;

@SuppressWarnings("serial")
final class BadSign extends SignException {
    public final String text;

    public BadSign(String text) {
        this.text = text;
    }

    public String getMessage() {
        return "bad sign (text: " + this.text + ")";
    }

    public String getText() {
        return this.text;
    }
}
