package lang.temper.common

/**
 * Some kinds of fuzz testing turn out to be slow on Node.
 */
expect val mightRepeatedTestsBeSlooow: Boolean

enum class KotlinBackend {
    JVM,
    JS,
    NATIVE,
    ;

    companion object {
        val all = values().toSet()
    }
}

expect val kotlinBackend: KotlinBackend

val KotlinBackend.affectedByIssue11 get() = this == KotlinBackend.NATIVE
val KotlinBackend.affectedByIssue33 get() = this == KotlinBackend.JS
val KotlinBackend.affectedByIssue1499 get() = this == KotlinBackend.JS
