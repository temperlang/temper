package host_failures_host_failure;

@SuppressWarnings("serial")
public final class NotFound extends HostFailuresHostFailureException {
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
