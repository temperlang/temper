package temper.core;

/** Stub classes to represent artifacts that should be removed during compilation. */
public final class Stub {
    private Stub() {}

    /** The pure virutal function indicates a method that should be abstract without a body. */
    public static <T> T pureVirtual(Object ... args) {
        throw new UnsupportedOperationException();
    }
    /** Indicates an error in translation. */
    public static <T> T cantTranslate(Object ... args) {
        throw new UnsupportedOperationException();
    }
    /** Represents an error during type resolution. */
    public static final class InvalidType {}
}
