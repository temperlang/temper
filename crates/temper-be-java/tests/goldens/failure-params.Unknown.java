package failure_params;

@SuppressWarnings("serial")
final class Unknown extends FailureParamsException {
    public final String word;

    public Unknown(String word) {
        this.word = word;
    }

    public String getMessage() {
        return "unknown (word: " + this.word + ")";
    }

    public String getWord() {
        return this.word;
    }
}
