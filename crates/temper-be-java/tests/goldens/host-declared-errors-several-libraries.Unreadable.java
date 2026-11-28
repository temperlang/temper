package host_declared_errors_several_libraries;

@SuppressWarnings("serial")
final class Unreadable extends HostDeclaredErrorsException {
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
