package lang.temper.name

import lang.temper.common.RSuccess
import lang.temper.log.FilePath
import lang.temper.log.dirPath
import kotlin.test.Test
import kotlin.test.assertEquals

class QNameTest {
    private val defaultLibName = DashedIdentifier("my-library")
    private val defaultPath = dirPath("foo", "bar")

    fun build(
        libName: DashedIdentifier = defaultLibName,
        path: FilePath = defaultPath,
        f: QName.Builder.() -> Unit,
    ) = QName.Builder(libName, path).run {
        this.f()
        this.toQName()
    }

    @Test
    fun stringFormReversible() {
        val stringNamePairs = listOf(
            // Examples from class comments
            "my-library/foo/bar.x" to build {
                decl(ParsedName("x"))
            },
            "my-library/foo/bar.f()" to build {
                fn(ParsedName("f"))
            },
            "my-library/foo/bar.f().(x)" to build {
                fn(ParsedName("f"))
                input(ParsedName("x"))
            },
            "my-library/foo/bar.f().y=" to build {
                fn(ParsedName("f"))
                local(ParsedName("y"))
            },
            "my-library/foo/bar.type C" to build {
                type(ParsedName("C"))
            },
            "my-library/foo/bar.type C.<T>" to build {
                type(ParsedName("C"))
                formal(ParsedName("T"))
            },
            "my-library/foo/bar.type C.x" to build {
                type(ParsedName("C"))
                decl(ParsedName("x"))
            },
            "my-library/foo/bar.type C.f()" to build {
                type(ParsedName("C"))
                fn(ParsedName("f"))
            },
            "my-library/foo/bar.type C.f().<T>" to build {
                type(ParsedName("C"))
                fn(ParsedName("f"))
                formal(ParsedName("T"))
            },
            "my-library/foo/bar.type C.f().(x)" to build {
                type(ParsedName("C"))
                fn(ParsedName("f"))
                input(ParsedName("x"))
            },
            "my-library/foo/bar.type C.get y()" to build {
                type(ParsedName("C"))
                getter(ParsedName("y"))
            },
            "my-library/foo/bar.type C.set y()" to build {
                type(ParsedName("C"))
                setter(ParsedName("y"))
            },
            "my-library/foo/bar.type C.set y().(newY)" to build {
                type(ParsedName("C"))
                setter(ParsedName("y"))
                input(ParsedName("newY"))
            },
            "my-library/foo/bar.type C.fromY()" to build {
                type(ParsedName("C"))
                fn(ParsedName("fromY"))
            },
            "my-library/foo/bar.type C.fromY().(y)" to build {
                type(ParsedName("C"))
                fn(ParsedName("fromY"))
                input(ParsedName("y"))
            },
            "my-library/foo/bar.type C.fromY().c=" to build {
                type(ParsedName("C"))
                fn(ParsedName("fromY"))
                local(ParsedName("c"))
            },
            "my-library/foo/bar.x=#0" to build {
                local(ParsedName("x"))
                disambiguate(0)
            },
            "my-library/foo/bar.x=#1" to build {
                local(ParsedName("x"))
                disambiguate(1)
            },
            "my-library/foo/bar.f()" to build {
                fn(ParsedName("f"))
            },
            "my-library/foo/bar.f().type Helper" to build {
                fn(ParsedName("f"))
                type(ParsedName("Helper"))
            },
            "my-library/foo/bar.f().helper()" to build {
                fn(ParsedName("f"))
                fn(ParsedName("helper"))
            },

            // Examples with escape sequences
            """my-library/emacs\.d.\.a\/\\b().c=""" to build(path = dirPath("emacs.d")) {
                fn(ParsedName(".a/\\b"))
                local(ParsedName("c"))
            },
        )

        for ((string, qName) in stringNamePairs) {
            assertEquals(string, "$qName")
            assertEquals(RSuccess(qName), QName.fromString(string))
            assertEquals(qName, QName.Builder(qName).toQName())
        }
    }
}
