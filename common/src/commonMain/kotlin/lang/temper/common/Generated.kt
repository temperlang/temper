package lang.temper.common

// TODO: This should be a typealias for
// javax.annotation.processing.Generated on the JVM backend

/** Used by subpackage lang.temper.kcodegen */
@Target(AnnotationTarget.FILE)
@Retention(AnnotationRetention.SOURCE)
annotation class Generated(
    vararg val value: String,
    val date: String = "",
    val comments: String = "",
)
