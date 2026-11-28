package host_declared_errors_cause;

@SuppressWarnings("serial")
final class LookupFailed extends HostDeclaredErrorsException {
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
