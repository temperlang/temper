package lang.temper.be.java

import lang.temper.common.RFailure
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class JavaVersionTest {
    private fun parsed(version: String): String =
        "${Java17Specifics.parseJavaVersion(version).result}"

    @Test
    fun featureOnly() {
        assertEquals("23.0.0", parsed("23"))
    }

    @Test
    fun threeParts() {
        assertEquals("17.0.7", parsed("17.0.7"))
    }

    @Test
    fun fourParts() {
        // Homebrew's openjdk@21 reports "21.0.12.1".
        assertEquals("21.0.12", parsed("21.0.12.1"))
    }

    @Test
    fun java8() {
        assertEquals("1.8.0", parsed("1.8.0"))
    }

    @Test
    fun notAVersion() {
        assertIs<RFailure<*>>(Java17Specifics.parseJavaVersion("21..1"))
        assertIs<RFailure<*>>(Java17Specifics.parseJavaVersion(""))
    }
}
