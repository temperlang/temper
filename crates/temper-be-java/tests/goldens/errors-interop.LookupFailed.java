package errors_interop;

@SuppressWarnings("serial")
public final class LookupFailed extends ErrorsInteropException {
    public final String name;

    public LookupFailed(String name) {
        this.name = name;
    }

    public String getMessage() {
        return "lookup failed (name: " + this.name + ")";
    }

    public String getName() {
        return this.name;
    }
}
