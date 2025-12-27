package lang.temper.common

import kotlin.random.Random

/** Gets a random seed without resort to configuration state. */
expect fun getPrngSeed(): Long

/** Best effort to get the value of some configuration. */
expect fun getConfigFromEnvironment(varName: String): String?

const val RANDOM_SEED_ENVIRONMENT_VARIABLE = "TEST_RANDOM_SEED"

val randomSeedForTest: Long by lazy {
    when (val fromEnv: String? = getConfigFromEnvironment(RANDOM_SEED_ENVIRONMENT_VARIABLE)) {
        null -> getPrngSeed()
        else -> fromEnv.toLong()
    }
}

/**
 * Runs [f] with a source of pseudo-randomness.
 * If [f]\(\) fails exceptionally, then it dumps the seed to the console.
 */
fun <T> withRandomForTest(f: (prng: Random) -> T) = withRandomForTest(null, f)

/**
 * Runs [f] with a source of pseudo-randomness.
 * If [f]\(\) fails exceptionally, then it dumps the seed to the console.
 */
fun <T> withRandomForTest(seedOverride: Long?, f: (prng: Random) -> T) {
    val seed: Long = seedOverride ?: randomSeedForTest
    val prng = Random(seed)

    var pass = false
    try {
        f(prng)
        pass = true
    } finally {
        if (!pass) {
            printErr(
                """
            Test failed using random seed $seed
            To repeat, rerun tests with

                $RANDOM_SEED_ENVIRONMENT_VARIABLE=$seed gradlew ...
                """.trimIndent(),
            )
        }
    }
}
