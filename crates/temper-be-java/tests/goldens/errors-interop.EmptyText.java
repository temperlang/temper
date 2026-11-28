package errors_interop;

@SuppressWarnings("serial")
public final class EmptyText extends SignException {
    public EmptyText() {}

    public String getMessage() {
        return "no text";
    }
}
