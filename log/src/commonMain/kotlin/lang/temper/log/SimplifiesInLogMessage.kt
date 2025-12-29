package lang.temper.log

/** A value that has a simple form in log messages. */
interface SimplifiesInLogMessage {
    /** A simpler value to render for use in diagnostic messages. */
    val simplerLoggable: Any
}
