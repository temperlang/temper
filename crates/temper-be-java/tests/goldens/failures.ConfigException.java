package failures;

@SuppressWarnings("serial")
final class ConfigException extends FailuresException {
    public final String setting;

    public ConfigException(String setting) {
        this.setting = setting;
    }

    public String getMessage() {
        return "config error (setting: " + this.setting + ")";
    }

    public String getSetting() {
        return this.setting;
    }
}
