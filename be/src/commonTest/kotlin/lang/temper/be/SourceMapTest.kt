package lang.temper.be

import lang.temper.common.assertStructure
import lang.temper.common.isJson
import lang.temper.log.FilePath
import lang.temper.log.FilePathSegment
import kotlin.test.Test
import kotlin.test.assertTrue

class SourceMapTest {
    @Test
    fun mimeTypeIsJsonMimeType() = assertTrue(SourceMap.mimeType.isJson)

    @Test
    fun lucidaChartExample() {
        // https://www.lucidchart.com/techblog/2019/08/22/decode-encoding-base64-vlqs-source-maps/
        val greeterDotJs = FilePath(
            listOf("demo", "src", "greeter.js").map { FilePathSegment(it) },
            false,
        )
        val indexDotJs = FilePath(
            listOf("demo", "src", "index.js").map { FilePathSegment(it) },
            false,
        )
        val sourceMap = SourceMap(
            file = FilePath(listOf(FilePathSegment("output.min.js")), false),
            sourceRoot = null,
            sources = listOf(greeterDotJs, indexDotJs),
            names = listOf(
                "window",
                "alert",
                "greeting",
                "greet",
                "constructor",
            ),
            mappings = SourceMap.Mappings(
                listOf(
                    SourceMap.MappingGroup(
                        listOf(
                            // [0]
                            SourceMap.MappingSegment(0, null, 0, 0, null),
                        ),
                    ),
                    SourceMap.MappingGroup(
                        listOf(
                            // [13, 0, 12, 8, 0]
                            SourceMap.MappingSegment(13, greeterDotJs, 12, 8, "window"),
                            // [6, 0, 0, 0, 1]
                            SourceMap.MappingSegment(19, greeterDotJs, 12, 8, "alert"),
                            // [6, 0, 0, 0]
                            SourceMap.MappingSegment(25, greeterDotJs, 12, 8, null),
                            // [1, 0, 0, 13, 1]
                            SourceMap.MappingSegment(26, greeterDotJs, 12, 21, "greeting"),
                            // [1, 1, -8, -21, 1]
                            SourceMap.MappingSegment(27, indexDotJs, 4, 0, "greet"),
                            // [4, -1, 2, 4, 1]
                            SourceMap.MappingSegment(31, greeterDotJs, 6, 4, "constructor"),
                            // [8, 0, 0, 11]
                            SourceMap.MappingSegment(39, greeterDotJs, 6, 15, null),
                            // [2, 0, 0, 11]
                            SourceMap.MappingSegment(41, greeterDotJs, 6, 26, null),
                            // [1, 0, 2, -18]
                            SourceMap.MappingSegment(42, greeterDotJs, 8, 8, null),
                            // [4, 0, 0, 0, -2]
                            SourceMap.MappingSegment(46, greeterDotJs, 8, 8, "greeting"),
                            // [2, 0, 0, 0]
                            SourceMap.MappingSegment(48, greeterDotJs, 8, 8, null),
                            // [1, 1, -4, 12, 0]
                            SourceMap.MappingSegment(49, indexDotJs, 4, 20, "greeting"),
                            // [14, -1, 2, 6]
                            SourceMap.MappingSegment(63, greeterDotJs, 6, 26, null),
                            // [1, 0, 6, -5, 0]
                            SourceMap.MappingSegment(64, greeterDotJs, 12, 21, "greeting"),
                            // [3, 0, 0, -13]
                            SourceMap.MappingSegment(67, greeterDotJs, 12, 8, null),
                        ),
                    ),
                ),
            ),
            sourcesContent = null,
        )
        @Suppress("SpellCheckingInspection") // Source mappings are not English
        assertStructure(
            """
            {
              "version": 3,
              "file": "output.min.js",
              "sources": [
                "demo/src/greeter.js",
                "demo/src/index.js"
              ],
              "names": [
                "window",
                "alert",
                "greeting",
                "greet",
                "constructor"
              ],
              "mappings": "A;aAYQA,MAAAC,MAAA,CAAaC,CCRrBC,IDEIC,QAAW,EAAW,CAElB,IAAAF,EAAA,CCJYA,cDEM,CAMLA,GAAb",
            }
            """,
            sourceMap,
        )
    }
}
