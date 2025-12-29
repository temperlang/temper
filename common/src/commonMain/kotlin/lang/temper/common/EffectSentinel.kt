package lang.temper.common

/**
 * Singleton sentinel value that enables the
 * (*unitExpression* [calledFor][Unit.calledFor] [effect]) idiom.
 */
class EffectSentinel private constructor() {
    companion object {
        internal val singleton = EffectSentinel()
    }
}

/**
 * Name for a side-effect.
 * Enables the (*unitExpression* [calledFor][Unit.calledFor] [effect]) idiom.
 */
val effect = EffectSentinel.singleton

/** Enables the (*unitExpression* [calledFor][Unit.calledFor] [effect]) idiom. */
infix fun Unit.calledFor(@Suppress("UNUSED_PARAMETER") e: EffectSentinel) = this
