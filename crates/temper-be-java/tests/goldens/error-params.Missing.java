package error_params;

@SuppressWarnings("serial")
final class Missing extends ErrorParamsException {
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
