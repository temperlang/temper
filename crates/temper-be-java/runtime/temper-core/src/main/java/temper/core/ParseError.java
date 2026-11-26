package temper.core;

/**
 * What a conversion throws for a value it can't convert: its input, as
 * text, and why, as {@code not an Int32}. (Later perhaps a
 * {@link NumberFormatException} too, as Java's own parsing throws.)
 */
@SuppressWarnings("serial")
public final class ParseError extends CoreException {
    /** The text, or the number as text, that couldn't be converted. */
    public final String input;
    /** Why, as {@code not an Int32}. */
    public final String reason;

    /**
     * @param input the text, or the number as text
     * @param reason why it couldn't be converted
     */
    public ParseError(String input, String reason) {
        this.input = input;
        this.reason = reason;
    }

    @Override
    public String getMessage() {
        return "parse error (input: " + this.input + ", reason: " + this.reason + ")";
    }

    /**
     * @return the text, or the number as text
     */
    public String getInput() {
        return this.input;
    }

    /**
     * @return why it couldn't be converted
     */
    public String getReason() {
        return this.reason;
    }
}
