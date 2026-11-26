package temper.core;

/**
 * What making a string from a code point throws for one that isn't a
 * Unicode scalar value, with that number. (Later perhaps an
 * {@link IllegalArgumentException} too, as Java's own
 * {@code Character.toChars} throws.)
 */
@SuppressWarnings("serial")
public final class InvalidCodePoint extends CoreException {
    /** The number that isn't a Unicode scalar value. */
    public final int codePoint;

    /**
     * @param codePoint the number that isn't a Unicode scalar value
     */
    public InvalidCodePoint(int codePoint) {
        this.codePoint = codePoint;
    }

    @Override
    public String getMessage() {
        return "invalid code point (codePoint: " + this.codePoint + ")";
    }

    /**
     * @return the number that isn't a Unicode scalar value
     */
    public int getCodePoint() {
        return this.codePoint;
    }
}
