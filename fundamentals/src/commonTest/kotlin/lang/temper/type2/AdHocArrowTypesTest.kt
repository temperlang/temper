package lang.temper.type2

import lang.temper.type.TypeTestHarness
import kotlin.test.Test
import kotlin.test.assertEquals

class AdHocArrowTypesTest {
    @Test
    fun parameterizedFunInterfaceType() = TypeTestHarness(
        """
            |@fun interface Predicate<T>(x: T): Boolean;
        """.trimMargin(),
    ).run {
        val predString = type2("Predicate<String>")
            as DefinedNonNullType
        val sig = withType(
            predString,
            fn = { _, sig, _ -> sig },
            fallback = { null },
        )
        assertEquals("(String) -> Boolean", "$sig")
        val sig2 = sigForFunInterfaceType(predString)
        assertEquals("(String) -> Boolean", "$sig2")
        // No <T> on the signature.
    }

    @Test
    fun sigRoundTrips() = TypeTestHarness("").run {
        val sig = sig("fn<T>(x: T?): Boolean")
        // This is not a fn interface type.
        // `isNull` is not bound to only telling if a particular variety of
        // nullable value is null.

        assertEquals("<T__0>(T__0?) -> Boolean", "$sig")

        val defined = AdHocArrowTypes.definedTypeForSig(sig)
        assertEquals("Fn__0<T__0?, Boolean>", renumber("$defined"))

        val sig2 = withType(
            defined,
            fn = { _, sig, _ -> sig },
            fallback = { null },
        )
        val sig3 = sigForFunInterfaceType(defined)
        val sig4 = AdHocArrowTypes.reverseToSig(defined)
        assertEquals(
            "[<T__0>(T__0?) -> Boolean, <T__0>(T__0?) -> Boolean, <T__0>(T__0?) -> Boolean]",
            renumber("${listOf(sig2, sig3, sig4)}"),
        )
    }
}

private val renumberRegex = Regex("""__\d+\b""")
private fun renumber(s: String) = s.replace(renumberRegex, "__0")
