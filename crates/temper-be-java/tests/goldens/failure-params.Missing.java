package failure_params;

@SuppressWarnings("serial")
final class Missing extends FailureParamsException {
    public final String key;

    public Missing(String key) {
        this.key = key;
    }

    public String getMessage() {
        return "missing (key: " + this.key + ")";
    }

    public String getKey() {
        return this.key;
    }
}
