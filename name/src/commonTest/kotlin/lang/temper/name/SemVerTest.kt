package lang.temper.name

import lang.temper.common.assertStringsEqual
import lang.temper.common.withRandomForTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class SemVerTest {
    private val someSemVersInOrder = listOf(
        // Examples
        // 1.0.0-alpha < 1.0.0-alpha.1 < 1.0.0-alpha.beta < 1.0.0-beta < 1.0.0-beta.2 <
        // 1.0.0-beta.11 < 1.0.0-rc.1 < 1.0.0.
        "1.0.0-alpha" to SemVer(
            1,
            0,
            0,
            listOf(
                SemVerPreReleaseIdentifier("alpha").result!!,
            ),
        ),
        "1.0.0-alpha.1" to SemVer(
            1,
            0,
            0,
            listOf(
                SemVerPreReleaseIdentifier("alpha").result!!,
                SemVerPreReleaseIdentifier("1").result!!,
            ),
        ),
        "1.0.0-alpha.beta" to SemVer(
            1,
            0,
            0,
            listOf(
                SemVerPreReleaseIdentifier("alpha").result!!,
                SemVerPreReleaseIdentifier("beta").result!!,
            ),
        ),
        "1.0.0-beta" to SemVer(
            1,
            0,
            0,
            listOf(
                SemVerPreReleaseIdentifier("beta").result!!,
            ),
        ),
        "1.0.0-beta.2" to SemVer(
            1,
            0,
            0,
            listOf(
                SemVerPreReleaseIdentifier("beta").result!!,
                SemVerPreReleaseIdentifier("2").result!!,
            ),
        ),
        "1.0.0-beta.11" to SemVer(
            1,
            0,
            0,
            listOf(
                SemVerPreReleaseIdentifier("beta").result!!,
                SemVerPreReleaseIdentifier("11").result!!,
            ),
        ),
        "1.0.0-rc.1" to SemVer(
            1,
            0,
            0,
            listOf(
                SemVerPreReleaseIdentifier("rc").result!!,
                SemVerPreReleaseIdentifier("1").result!!,
            ),
        ),
        "1.0.0" to SemVer(1, 0, 0),
    )

    private val textsAndSemVers = listOf(
        "1.0.0" to SemVer(1, 0, 0),
        "2.0.0" to SemVer(2, 0, 0),
        "2.1.0" to SemVer(2, 1, 0),
        "2.1.1" to SemVer(2, 1, 1),
        "12.13.14" to SemVer(12, 13, 14),

        // https://semver.org/#spec-item-9
        // Examples: 1.0.0-alpha, 1.0.0-alpha.1, 1.0.0-0.3.7, 1.0.0-x.7.z.92, 1.0.0-x-y-z.–.
        "1.0.0-alpha" to SemVer(
            1,
            0,
            0,
            listOf(SemVerPreReleaseIdentifier("alpha").result!!),
        ),
        "1.0.0-alpha.1" to SemVer(
            1,
            0,
            0,
            listOf(
                SemVerPreReleaseIdentifier("alpha").result!!,
                SemVerPreReleaseIdentifier("1").result!!,
            ),
        ),
        "1.0.0-0.3.7" to SemVer(
            1,
            0,
            0,
            listOf(
                SemVerPreReleaseIdentifier("0").result!!,
                SemVerPreReleaseIdentifier("3").result!!,
                SemVerPreReleaseIdentifier("7").result!!,
            ),
        ),
        "1.0.0-x.7.z.92" to SemVer(
            1,
            0,
            0,
            listOf(
                SemVerPreReleaseIdentifier("x").result!!,
                SemVerPreReleaseIdentifier("7").result!!,
                SemVerPreReleaseIdentifier("z").result!!,
                SemVerPreReleaseIdentifier("92").result!! as NumericSemVerIdentifier,
            ),
        ),
        "1.0.0-x-y-z.-" to SemVer(
            1,
            0,
            0,
            listOf(
                SemVerPreReleaseIdentifier("x-y-z").result!!,
                SemVerPreReleaseIdentifier("-").result!!,
            ),
        ),

        // Examples: 1.0.0-alpha+001, 1.0.0+20130313144700, 1.0.0-beta+exp.sha.5114f85,
        //           1.0.0+21AF26D3—-117B344092BD.
        "1.0.0-alpha+001" to SemVer(
            1,
            0,
            0,
            listOf(SemVerPreReleaseIdentifier("alpha").result!!),
            listOf(SemVerBuildIdentifier("001").result!! as DigitsSemVerIdentifier),
        ),
        "1.0.0+20130313144700" to SemVer(
            1,
            0,
            0,
            buildIdentifiers = listOf(SemVerBuildIdentifier("20130313144700").result!!),
        ),
        "1.0.0-beta+exp.sha.5114f85" to SemVer(
            1,
            0,
            0,
            listOf(SemVerPreReleaseIdentifier("beta").result!!),
            listOf(
                SemVerBuildIdentifier("exp").result!! as AlphaNumericSemVerIdentifier,
                SemVerBuildIdentifier("sha").result!! as AlphaNumericSemVerIdentifier,
                SemVerBuildIdentifier("5114f85").result!! as AlphaNumericSemVerIdentifier,
            ),
        ),
        "1.0.0+21AF26D3--117B344092BD" to SemVer(
            1,
            0,
            0,
            emptyList(),
            listOf(
                SemVerBuildIdentifier("21AF26D3--117B344092BD").result!!,
            ),
        ),
    ) + someSemVersInOrder

    @Test
    fun parse() {
        for ((text, want) in textsAndSemVers) {
            assertEquals(want, SemVer(text).result!!)
        }
    }

    @Test
    fun equalsSemVer() {
        for ((textA, semVerA) in textsAndSemVers) {
            for ((textB, semVarB) in textsAndSemVers) {
                if (textA == textB) {
                    assertEquals(semVerA, semVarB)
                } else {
                    assertNotEquals(semVerA, semVarB)
                }
            }
        }
    }

    @Test
    fun testToString() {
        for ((want, semVer) in textsAndSemVers) {
            assertStringsEqual(want, "$semVer")
        }
    }

    @Test
    fun precedence() = withRandomForTest { prng ->
        val inOrder = someSemVersInOrder.map { it.second }
        val shuffled = inOrder.shuffled(prng)
        val got = shuffled.sorted()
        assertEquals(inOrder, got)
    }

    @Test
    fun orderingWithPartials() = withRandomForTest { prng ->
        val inOrder = listOf(
            SemVer(1, 0, 0),
            SemVerPrefix(1, 1),
            SemVerPrefix(1, 2),
            SemVer(1, 2, 3),
            SemVerPrefix(1, 2, 3),
            SemVerPrefix(1, 2, 4),
            SemVerPrefix(2),
            SemVer(2, 0, 0),
            SemVerPrefix(3),
            SemVerPrefix(11),
        )
        val shuffled = inOrder.shuffled(prng)
        val got = shuffled.sorted()
        assertEquals(inOrder, got)
    }
}
