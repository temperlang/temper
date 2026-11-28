package errors_interop;

@SuppressWarnings("serial")
public final class NotFound extends ErrorsInteropException {
    public final String key;

    public NotFound(String key) {
        this.key = key;
    }

    public String getMessage() {
        return "not found (key: " + this.key + ")";
    }

    public String getKey() {
        return this.key;
    }
}
