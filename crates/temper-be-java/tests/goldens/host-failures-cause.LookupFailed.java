package host_failures_cause;

@SuppressWarnings("serial")
final class LookupFailed extends HostFailuresException {
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
