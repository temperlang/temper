package lang.temper.common

/**
 * May have custom behavior before being included in a string formatted via [sprintf].
 */
interface Formattable {
    /**
     * Called before this value is interpolated into an [sprintf]
     * format string, or
     *
     * The arguments come from printf format markers like
     *
     *     %[parameter][flags][width][.precision][length]type
     */
    fun preformat(): CharSequence
}
