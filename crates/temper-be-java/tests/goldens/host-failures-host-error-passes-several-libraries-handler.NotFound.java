package host_failures_host_error_passes_several_libraries_handler;

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
