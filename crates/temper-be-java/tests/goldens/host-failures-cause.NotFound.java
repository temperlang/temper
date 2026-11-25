package host_failures_cause;

@SuppressWarnings("serial")
public final class NotFound extends HostFailuresException {
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
