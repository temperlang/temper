package temper.core;

/**
 * What a map's {@code get} throws for a key it doesn't have, with that
 * key. (Later perhaps a {@link java.util.NoSuchElementException} too, as
 * Java's own lookups throw.)
 */
@SuppressWarnings("serial")
public final class MissingKey extends CoreException {
    /** The key that was missing. */
    public final Object key;

    /**
     * @param key the key that was missing
     */
    public MissingKey(Object key) {
        this.key = key;
    }

    @Override
    public String getMessage() {
        return "missing key (key: " + this.key + ")";
    }

    /**
     * @return the key that was missing
     */
    public Object getKey() {
        return this.key;
    }
}
