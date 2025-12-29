package lang.temper.be

import lang.temper.common.LeftOrRight
import lang.temper.common.assertStructure
import lang.temper.common.toStringViaBuilder
import lang.temper.log.FilePath
import lang.temper.log.FilePathSegment
import lang.temper.log.FilePositions
import lang.temper.log.Position
import lang.temper.log.filePath
import lang.temper.name.ModuleName
import kotlin.test.Test
import kotlin.test.assertEquals

class SourceMapBuilderTest {
    @Suppress("NonAsciiCharacters") // C'mon!
    // @JsName("borkwards")
    @Test
    fun `‮backwards‬Script`() {
        val sourceText = listOf(
            "foo = bar + baz",
            "  * boo;",
            "",
        ).joinToString("\n").reversed()

        val boilerplate = "// Reversed by a code generator.  You're welcome.\n"
        val outputText = boilerplate + sourceText.reversed()

        val sourcePath = filePath("src", "backwards.bs")
        val loc = ModuleName(
            sourceFile = filePath("backwards.bs"),
            libraryRootSegmentCount = 0,
            isPreface = false,
        )
        var askedForPositions = false
        val sourceMapBuilder = SourceMapBuilder(
            outPath = FilePath(
                listOf("out", "backwards.reversed.js").map { FilePathSegment(it) },
                false,
            ),
            lookupCodeLocation = {
                if (it == loc) {
                    require(!askedForPositions) // Don't ask for every Position.
                    askedForPositions = true
                    sourcePath to FilePositions.fromSource(loc, sourceText)
                } else {
                    null
                }
            },
        )

        val output = StringBuilder()
        output.append(boilerplate)
        sourceMapBuilder.wroteChars(output.length)
        sourceMapBuilder.lineEnded()

        // Walk over the sourceText in reverse, producing the output, but recognizing identifiers
        // and line breaks.
        var i = sourceText.length - 1
        while (i >= 0) {
            val outputLengthBefore = output.length

            var c = sourceText[i]
            i -= 1

            var lineEnded = false
            var sourceName: String? = null
            if (c == '\n') { // Break lines on LF
                output.append(c)
                lineEnded = true
            } else if (c in 'a'..'z') { // Recognize a run of lower-case letters as a name.
                val outputName = toStringViaBuilder { nameBuffer ->
                    nameBuffer.append(c)
                    while (i >= 0 && sourceText[i] in 'a'..'z') {
                        c = sourceText[i]
                        nameBuffer.append(c)
                        i -= 1
                    }
                }
                output.append(outputName)
                sourceName = outputName.reversed()
            } else { // A run of non-special characters afa the sourcemap is concerned.
                output.append(c)
                var runCount = 1
                while (i >= 0) {
                    c = sourceText[i]
                    if (c !in 'a'..'z' && c != '\n') {
                        output.append(c)
                        runCount += 1
                        i -= 1
                    } else {
                        break
                    }
                }
            }

            val nCharactersProcessed = output.length - outputLengthBefore
            val pos = Position(loc, i + 1, i + 1 + nCharactersProcessed)

            sourceMapBuilder.position(pos, LeftOrRight.Left)
            if (sourceName == null) {
                sourceMapBuilder.wroteChars(nCharactersProcessed)
            } else {
                sourceMapBuilder.wroteName(
                    outputNameLength = nCharactersProcessed,
                    sourceName = sourceName,
                )
            }
            sourceMapBuilder.position(pos, LeftOrRight.Right)
            if (lineEnded) {
                sourceMapBuilder.lineEnded()
            }
        }
        // Preliminary check that we built our output completely.
        assertEquals(outputText, "$output")

        val sourceMap = sourceMapBuilder.build { source ->
            if (source == loc) {
                sourceText
            } else {
                null
            }
        }

        assertEquals(
            listOf(
                listOf("// Reversed by a code generator.  You're welcome.\n", ""),
                listOf("foo", "oof"),
                listOf(" = ", " = "),
                listOf("bar", "rab"),
                listOf(" + ", " + "),
                listOf("baz", "zab"),
                listOf("\n", ""),
                listOf("  * ", " *  "),
                listOf("boo", "oob"),
                listOf(";", ";"),
                listOf("\n", ""),
            ),
            pairChunks(
                source = sourceText,
                output = outputText,
                sourceMap = sourceMap,
            ),
        )

        @Suppress("SpellCheckingInspection") // Source mappings are not English
        assertStructure(
            """
            {
              "version": 3,
              "file": "out/backwards.reversed.js",
              "sources": [ "src/backwards.bs" ],
              "sourcesContent": [ "\n;oob *  \nzab + rab = oof" ],
              "names": [ "oof", "rab", "zab", "oob" ],
              "mappings":
                  "A;AAEYA,GAAG,AAAN,GAAG,AAANC,GAAG,AAAN,GAAG,AAANC,GAAG,AADK;AAAJ,IAAI,AAAPC,GAAG,AAAJ,CAAC,AADD"
            }
            """,
            // I don't know whether these mappings are perfect but
            //    node --enable-source-maps
            //    Chrome
            //    Firefox
            // all report "`bar` is not defined in backwards.bs:3"-ish error
            // I used the following shell script to test via node.
            /*
            rm -rf /tmp/source-map-test
            mkdir /tmp/source-map-test
            cd /tmp/source-map-test

            echo -n '// Reversed by a code generator.  You'"'"'re welcome.
            foo = bar + baz
             * boo;
            //# sourceMappingURL=backwards.js.map' \
            > backwards.js

            echo -n '
            ;oob *
            zab + rab = oof' \
            > backwards.bs

            echo '
            {
              "version": 3,
              "file": "out/backwards.reversed.js",
              "sources": [ "src/backwards.bs" ],
              "sourcesContent": [ "\n;oob *  \nzab + rab = oof" ],
              "names": [ "oof", "rab", "zab", "oob" ],
              "mappings":
                  "A;AAEYA,GAAG,AAAN,GAAG,AAANC,GAAG,AAAN,GAAG,AAANC,GAAG,AADK;AAAJ,IAAI,AAAPC,GAAG,AAAJ,CAAC,AADD"
            }
            ' \
            > backwards.js.map

            echo '<html><meta charset=utf-8>
            <script src=backwards.js></script>
            ' \
            > index.html

            node --enable-source-maps backwards.js
             */
            sourceMap,
        )
    }
}
