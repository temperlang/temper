package host_failures_several_libraries;

@SuppressWarnings("serial")
final class Unreadable extends HostFailuresException {
    public final String key;

    public Unreadable(String key) {
        this.key = key;
    }

    public String getMessage() {
        return "unreadable (key: " + this.key + ")";
    }

    public String getKey() {
        return this.key;
    }
}
