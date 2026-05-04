package lang.temper.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MimeTypeTest {
    @Test
    fun parsingSuccess() {
        val textPlainResult = MimeType.parse("text/plain")
        assertIs<RSuccess<MimeType, String>>(textPlainResult)
        assertEquals(textPlainResult.result, MimeType.textPlain)
    }

    @Test
    fun dashAllowed() {
        val textPlainResult = MimeType.parse("text/x-plum")
        assertIs<RSuccess<MimeType, String>>(textPlainResult)
        assertEquals(textPlainResult.result.major, "text")
        assertEquals(textPlainResult.result.minor, "x-plum")
    }

    @Test
    fun roundTripping() {
        val mimeTypes = listOf(
            MimeType.textPlain,
            MimeType.json,
            MimeType.markdown,
            MimeType.kotlinSource,
            MimeType.textPlain,
            MimeType.svg,
            MimeType.luaSource,
            MimeType.javascript,
            MimeType.javascriptApp,
            MimeType.cppSource,
            MimeType.makefileSource,
        )
        for (mimeType in mimeTypes) {
            val result = MimeType.parse("$mimeType")
            assertIs<RSuccess<MimeType, String>>(result)
            assertEquals(mimeType, result.result)
        }
    }

    @Test
    fun parsingFailureEmpty() {
        val result = MimeType.parse("")
        assertIs<RFailure<String>>(result)
        assertTrue(result.failure.isNotEmpty(), result.failure)
    }

    @Test
    fun parsingEmptyLeft() {
        val result = MimeType.parse("/plain")
        assertIs<RFailure<String>>(result)
        assertTrue(result.failure.isNotEmpty(), result.failure)
    }

    @Test
    fun parsingEmptyRight() {
        val result = MimeType.parse("text/")
        assertIs<RFailure<String>>(result)
        assertTrue(result.failure.isNotEmpty(), result.failure)
    }

    @Test
    fun tooFewSeparators() {
        val result = MimeType.parse("text\\plain")
        assertIs<RFailure<String>>(result)
        assertTrue(result.failure.isNotEmpty(), result.failure)
    }

    @Test
    fun parsingFailureTooManySeparators() {
        val result = MimeType.parse("text/plain/plain")
        assertIs<RFailure<String>>(result)
        assertTrue(result.failure.isNotEmpty(), result.failure)
    }

    @Test
    fun tooLongMajor() {
        val result = MimeType.parse(
            "textttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttt/pain",
        )
        assertIs<RFailure<String>>(result)
        assertTrue(result.failure.isNotEmpty(), result.failure)
    }

    @Test
    fun tooLongMinor() {
        val result = MimeType.parse(
            "text/veryloooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooong",
        )
        assertIs<RFailure<String>>(result)
        assertTrue(result.failure.isNotEmpty(), result.failure)
    }

    @Test
    fun invalidMajor() {
        val result = MimeType.parse("t%65xt/plain")
        assertIs<RFailure<String>>(result)
        assertTrue(result.failure.isNotEmpty(), result.failure)
    }

    @Test
    fun invalidMinor() {
        val result = MimeType.parse(
            "text/-123",
        )
        assertIs<RFailure<String>>(result)
        assertTrue(result.failure.isNotEmpty(), result.failure)
    }

    @Test
    fun extrasNotAllowed() {
        val result = MimeType.parse(
            "text/x-foo; charset=utf-8",
        )
        assertIs<RFailure<String>>(result)
        assertTrue(result.failure.isNotEmpty(), result.failure)
    }
}
