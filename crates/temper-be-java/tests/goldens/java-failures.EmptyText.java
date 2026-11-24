package java_failures;

@SuppressWarnings("serial")
public final class EmptyText extends SignException {
    public EmptyText() {}

    public String getMessage() {
        return "no text";
    }
}
