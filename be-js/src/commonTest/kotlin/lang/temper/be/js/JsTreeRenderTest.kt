package lang.temper.be.js

import lang.temper.ast.ChildMemberRelationships
import lang.temper.ast.OutTree
import lang.temper.be.OutCodeFormatter
import lang.temper.be.pairChunks
import lang.temper.common.assertStructure
import lang.temper.common.ignore
import lang.temper.common.jsonEscaper
import lang.temper.common.structure.Hints
import lang.temper.common.structure.StructureSink
import lang.temper.common.structure.Structured
import lang.temper.format.CodeFormattingTemplate
import lang.temper.format.FormattingHints
import lang.temper.fs.MemoryFileSystem
import lang.temper.fs.OutputRoot
import lang.temper.log.CodeLocation
import lang.temper.log.FilePathSegment
import lang.temper.log.FilePositions
import lang.temper.log.Position
import lang.temper.log.dirPath
import lang.temper.name.ModuleName
import lang.temper.name.ParsedName
import lang.temper.name.TemperName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.fail

class JsTreeRenderTest {
    @Suppress("SpellCheckingInspection") // SourceMap strings of letters are not English.
    @Test
    fun renderHelloWorld() =
        assertRendered(
            baseName = "hello-world",
            source = "print(\n    \"Hello, World!\"\n);",
            //        012345 67890 12345678901234 5 6789
            //        0          1          2
            expectedJson =
            """
            {
              js: "console.log(\u0022Hello, World!\u0022);\n",
              sourceMap: {
                version: 3,
                file: "out/hello-world.js",
                sources: ["hello-world.temper"],
                sourcesContent: [
                  "print(\n    \"Hello, World!\"\n);",
                ],
                names: [
                    "print"
                ],
                mappings: {
                  lines: [
                    [
                      // "console." corresponds to the space before "print"
                      {
                        outputStartColumn: 0,
                        source: "hello-world.temper",
                        sourceStartLine: 0,
                        sourceStartColumn: 0,
                        name: null
                      },
                      { // "log" corresponds to the word "print"
                        outputStartColumn: 8,
                        source: "hello-world.temper",
                        sourceStartLine: 0,
                        sourceStartColumn: 0,
                        name: "print"
                      },
                      { // "(" corresponds to "("
                        outputStartColumn: 11,
                        source: "hello-world.temper",
                        sourceStartLine: 0,
                        sourceStartColumn: 5,
                        name: null
                      },
                      { // 12-27:"Hello, world!" corresponds to "Hello, world!"
                        outputStartColumn: 12,
                        source: "hello-world.temper",
                        sourceStartLine: 1,
                        sourceStartColumn: 4,
                        name: null
                      },
                      { // ")" corresponds to ")"
                        outputStartColumn: 27,
                        source: "hello-world.temper",
                        sourceStartLine: 1,
                        sourceStartColumn: 19,
                        name: null
                      },
                      { // ";" corresponds to ""
                        outputStartColumn: 28,
                        source: "hello-world.temper",
                        sourceStartLine: 2,
                        sourceStartColumn: 1,
                        name: null
                      },
                    ]
                  ],
                  encoded: "AAAA,QAAAA,GAAK,CACD,eAAe,CAClB"
                }
              },
              chunks: [
                [ "console.", "" ],
                [ "log", "print" ],
                [ "(", "(\n    " ],
                [ "\u0022Hello, World!\u0022", "\u0022Hello, World!\u0022" ],
                [ ")", "\n)" ],
                [ ";\n", "" ],
              ]
            }
            """,
        ) { loc ->
            Js.Program(
                Position(loc, 0, 29),
                listOf(
                    Js.ExpressionStatement(
                        Position(loc, 0, 28),
                        Js.CallExpression(
                            Position(loc, 0, 28),
                            Js.MemberExpression(
                                Position(loc, 0, 5),
                                obj = makeJsIdentifier(
                                    Position(loc, 0, 0),
                                    "console",
                                    null,
                                ),
                                property = makeJsIdentifier(
                                    Position(loc, 0, 5),
                                    "log",
                                    ParsedName("print"), // The pre-translation identifier.
                                ),
                            ),
                            listOf(
                                Js.StringLiteral(
                                    Position(loc, 11, 26),
                                    "Hello, World!",
                                ),
                            ),
                        ),
                    ),
                ),
            )
        }

    @Test
    fun expressionStatementAmbiguity() {
        val expectedJs = """
            (function f() {
            });
            (async function af() {
            });
            (class {
            });
            (let[x]);
            ok;
            (class);
            (async ++);

        """.trimIndent()
        assertRendered(
            baseName = "file",
            source = """
            ... TODO
            """.trimIndent(),
            expectedJson = """
            {
              js: ${ jsonEscaper.escape(expectedJs) }
            }
            """,
        ) { loc ->
            val pos = Position(loc, 0, 0)
            Js.Program(
                pos,
                listOf(
                    // function expression ambiguity
                    Js.ExpressionStatement(
                        pos,
                        Js.FunctionExpression(
                            pos,
                            id = makeJsIdentifier(pos, "f", null),
                            params = Js.Formals(pos, listOf()),
                            body = Js.BlockStatement(pos, listOf()),
                            generator = false,
                            async = false,
                        ),
                    ),
                    // async function expression ambiguity
                    Js.ExpressionStatement(
                        pos,
                        Js.FunctionExpression(
                            pos,
                            id = makeJsIdentifier(pos, "af", ParsedName("af")),
                            params = Js.Formals(pos, listOf()),
                            body = Js.BlockStatement(pos, listOf()),
                            generator = false,
                            async = true,
                        ),
                    ),
                    // class expression ambiguity
                    Js.ExpressionStatement(
                        pos,
                        Js.ClassExpression(
                            pos,
                            id = null,
                            superClass = null,
                            body = Js.ClassBody(pos, listOf()),
                            decorators = Js.Decorators(pos, listOf()),
                        ),
                    ),
                    // array access of "let" ambiguity
                    Js.ExpressionStatement(
                        pos,
                        Js.MemberExpression(
                            pos,
                            obj = makeJsIdentifier(pos, "let", null),
                            property = makeJsIdentifier(pos, "x", null),
                            computed = true,
                            optional = false,
                        ),
                    ),
                    // "function" identifier ambiguity
                    Js.ExpressionStatement(
                        pos,
                        try {
                            makeJsIdentifier(pos, "function", null)
                        } catch (e: IllegalArgumentException) {
                            // Sigh, detekt, it's not called "swallowing an exception" when you try
                            // something different as a result.
                            ignore(e)
                            makeJsIdentifier(pos, "ok", null)
                        },
                    ),
                    // "class" identifier ambiguity
                    Js.ExpressionStatement(
                        pos,
                        makeJsIdentifier(pos, "class", null),
                    ),
                    // "async" identifier ambiguity
                    Js.ExpressionStatement(
                        pos,
                        Js.UpdateExpression(
                            pos,
                            operator = Js.Operator(pos, "++"),
                            argument = makeJsIdentifier(pos, "async", null),
                            prefix = false,
                        ),
                    ),
                ),
            )
        }
    }

    /**
     * Avoid ambiguity between
     *     (function) * (a, b)
     * and a generator function expression like
     *     function *(a, b)
     */
    @Test
    fun functionTimesAmbiguity() {
        val exc = assertFailsWith(
            exceptionClass = IllegalArgumentException::class,
        ) {
            assertRendered(
                baseName = "foo",
                source = "obj.function * do { a; b }",
                expectedJson = "{}",
            ) { loc ->
                val pos = Position(loc, 0, 0)
                Js.Program(
                    pos,
                    listOf(
                        Js.ExpressionStatement(
                            pos,
                            Js.BinaryExpression(
                                pos,
                                operator = Js.Operator(pos, "*"),
                                left = Js.MemberExpression(
                                    pos,
                                    obj = makeJsIdentifier(pos, "obj", null),
                                    property = makeJsIdentifier(pos, "function", null),
                                    computed = false,
                                    optional = false,
                                ),
                                right = Js.SequenceExpression(
                                    pos,
                                    listOf(
                                        makeJsIdentifier(pos, "a", null),
                                        makeJsIdentifier(pos, "b", null),
                                    ),
                                ),
                            ),
                        ),
                    ),
                )
            }
        }
        assertEquals(
            "//foo.temper+0: `function` is a JS reserved word",
            exc.message,
        )
    }

    @Test
    fun dotAmbiguity() = assertRendered(
        baseName = "dots",
        source = "\"${3}\"",
        expectedJson = """
        { js: "3 .toString();\n" }
        """,
    ) { loc ->
        val pos = Position(loc, 0, 0)
        Js.Program(
            pos,
            listOf(
                Js.ExpressionStatement(
                    pos,
                    Js.CallExpression(
                        pos,
                        callee = Js.MemberExpression(
                            pos,
                            obj = Js.NumericLiteral(pos, 3),
                            property = makeJsIdentifier(pos, "toString", null),
                        ),
                        arguments = listOf(),
                        optional = false,
                    ),
                ),
            ),
        )
    }

    @Test
    fun formattingHintsProtectRestrictedProductions() = assertLineBreaking(
        baseName = "no-line-break-after-keyword-break",
        jsTokensWithBreaksBetween = "if\n(\nx\n)\n{\nbreak\nlabel;\n}",
        expectedJson = """
            |{
            |  // It's important that there's no LineTerminator between `break` and `label`
            |  js: ```
            |    if
            |      (
            |      x
            |    )
            |    {
            |      break label;
            |    }
            |
            |    ```
            |}
        """.trimMargin(),
    )

    @Test
    fun typeAnnotationRestrictedProductions() = assertLineBreaking(
        baseName = "type-annotation-restricted-productions",
        // In
        //     type T = U!
        //     f()
        //     let arr : T[]
        //     -1
        //     x as (T)
        //
        // we cannot have line breaks
        //
        // 1. between `U` and `!` because that would cause `!` to negate `f()`
        // 2. before `[]` that is part of array type because then `[] - 1` would
        //    be a useless array as part of an infix subtraction operation
        // 3. before `as` because then `as(T)` would be a function call, not a type cast.
        jsTokensWithBreaksBetween = """
            |type T = U !${
            /* `!` is a postfix operator on types and cannot move to next line */
            ""
        }
            |f ( )
            |let arr : T [ ]${
            /* `[` on next line would have semicolon inserted before it */
            ""
        }
            |- 1
            |x as ( T )${
            /* `as` on next line would be reference to variable */
            ""
        }
        """.trimMargin().replace(' ', '\n'),
        expectedJson = """
            |{
            |  js: ```
            |    type
            |      T
            |      =
            |      U !
            |      f
            |      (
            |    )
            |    let
            |      arr
            |      :
            |      T[
            |    ]
            |    -
            |      1
            |      x as
            |      (
            |      T
            |    )
            |
            |    ```
            |}
        """.trimMargin(),
    )

    private fun assertLineBreaking(
        baseName: String,
        jsTokensWithBreaksBetween: String,
        expectedJson: String,
    ) = assertRendered(
        baseName = baseName,
        source = jsTokensWithBreaksBetween.replace('\n', ' '),
        expectedJson = expectedJson,
    ) { loc ->
        // Create a dodgy Js.Tree sub-type for testing purposes.
        object : OutTree<Nothing> {
            override fun child(index: Int) = error("$index")
            override val childCount: Int = 0
            override val operatorDefinition: JsOperatorDefinition? = null
            private val templateThunk = lazy {
                CodeFormattingTemplate.fromFormatString(formatString)
            }
            override val codeFormattingTemplate: CodeFormattingTemplate
                get() = templateThunk.value
            val formatString: String get() = jsTokensWithBreaksBetween
            override fun formattingHints(): FormattingHints = JsFormattingHints
            override val childMemberRelationships = ChildMemberRelationships()
            override val pos: Position = Position(loc, 0, 0)
            override val parent: OutTree<Nothing>? = null
            override fun deepCopy(): OutTree<Nothing> = this
        }
    }

    @Test
    fun bracketedOperands() {
        assertRendered(
            "brackets.js",
            source = "meh",
            expectedJson = """
                {
                  js: // The left of the bracket is parenthesized, but the right is not.
                    ```
                    (a ? b: c)[d + e]

                    ```
                }
            """,
        ) {
            val pos = Position(it, 0, 0)
            Js.MemberExpression(
                pos,
                Js.ConditionalExpression(
                    pos,
                    makeJsIdentifier(pos, "a", null),
                    makeJsIdentifier(pos, "b", null),
                    makeJsIdentifier(pos, "c", null),
                ),
                Js.BinaryExpression(
                    pos,
                    makeJsIdentifier(pos, "d", null),
                    Js.Operator(pos, "+"),
                    makeJsIdentifier(pos, "e", null),
                ),
                computed = true,
                optional = false,
            )
        }
    }

    @Test
    fun noHtmlComments() {
        assertRendered(
            "not.html.js",
            source = "meh",
            expectedJson = """
                {
                  js:
                  ```
                  a < ! -- b;
                  c -- > d;

                  ```
                }
            """,
        ) {
            val pos = Position(it, 0, 0)
            Js.Program(
                pos,
                listOf(
                    Js.ExpressionStatement(
                        pos,
                        Js.InfixExpression(
                            pos,
                            makeJsIdentifier(pos, "a", null),
                            Js.Operator(pos, "<"),
                            Js.UnaryExpression(
                                pos,
                                Js.Operator(pos, "!"),
                                Js.UpdateExpression(
                                    pos,
                                    operator = Js.Operator(pos, "--"),
                                    argument = makeJsIdentifier(pos, "b", null),
                                    prefix = true,
                                ),
                            ),
                        ),
                    ),
                    Js.ExpressionStatement(
                        pos,
                        Js.InfixExpression(
                            pos,
                            Js.UpdateExpression(
                                pos,
                                operator = Js.Operator(pos, "--"),
                                argument = makeJsIdentifier(pos, "c", null),
                                prefix = false,
                            ),
                            Js.Operator(pos, ">"),
                            makeJsIdentifier(pos, "d", null),
                        ),
                    ),
                ),
            )
        }
    }

    @Test
    fun arrowFunctionExpressions() = assertRendered(
        "f.js",
        source = "meh",
        expectedJson = """
        {
          js:
          ```
          [(x, y) => x + y, () => {
              f();
              return x + y;
            }, () => {
              return;
            }, () => ({})]

          ```
        }
        """,
    ) {
        val pos = Position(it, 0, 0)
        Js.ArrayExpression(
            pos,
            listOf(
                // An arrow with a simple expression as the result
                Js.ArrowFunctionExpression(
                    pos,
                    Js.Formals(
                        pos,
                        listOf(
                            makeParam(pos, "x"),
                            makeParam(pos, "y"),
                        ),
                    ),
                    Js.BlockStatement(
                        pos,
                        listOf(
                            Js.ReturnStatement(
                                pos,
                                Js.BinaryExpression(
                                    pos,
                                    makeJsIdentifier(pos, "x", null),
                                    Js.Operator(pos, "+"),
                                    makeJsIdentifier(pos, "y", null),
                                ),
                            ),
                        ),
                    ),
                ),
                // An arrow with two statements.
                Js.ArrowFunctionExpression(
                    pos,
                    Js.Formals(pos, listOf()),
                    Js.BlockStatement(
                        pos,
                        listOf(
                            Js.ExpressionStatement(
                                pos,
                                Js.CallExpression(
                                    pos,
                                    makeJsIdentifier(pos, "f", null),
                                    emptyList(),
                                ),
                            ),
                            Js.ReturnStatement(
                                pos,
                                Js.BinaryExpression(
                                    pos,
                                    makeJsIdentifier(pos, "x", null),
                                    Js.Operator(pos, "+"),
                                    makeJsIdentifier(pos, "y", null),
                                ),
                            ),
                        ),
                    ),
                ),
                // An arrow with a return with no expression
                Js.ArrowFunctionExpression(
                    pos,
                    Js.Formals(pos, listOf()),
                    Js.BlockStatement(
                        pos,
                        listOf(
                            Js.ReturnStatement(
                                pos,
                                null,
                            ),
                        ),
                    ),
                ),
                // An arrow with an object as a result
                Js.ArrowFunctionExpression(
                    pos,
                    Js.Formals(pos, listOf()),
                    Js.BlockStatement(
                        pos,
                        listOf(
                            Js.ReturnStatement(
                                pos,
                                Js.ObjectExpression(pos, emptyList()),
                            ),
                        ),
                    ),
                ),
            ),
        )
    }

    @Test
    fun nestingCurlies() = assertRendered(
        "f.js",
        source = "meh",
        expectedJson = """
        {
          js:
          ```
          {
            {
            }
            ({});
          }

          ```
        }
        """,
    ) {
        val pos = Position(it, 0, 0)
        Js.BlockStatement(
            pos,
            listOf(
                Js.BlockStatement(pos, emptyList()),
                Js.ExpressionStatement(
                    pos,
                    Js.ObjectExpression(pos, emptyList()),
                ),
            ),
        )
    }

    @Test
    fun arrowRestArgs() = assertRendered(
        baseName = "f",
        source = "(...a) => a",
        expectedJson = """
        {
          js:
          ```
          (...a) => a

          ```
        }
        """,
    ) {
        val pos = Position(it, 0, 0)
        Js.ArrowFunctionExpression(
            pos,
            Js.Formals(
                pos,
                listOf(
                    Js.Param(pos, Js.RestElement(pos, makeJsIdentifier(pos, "a", null))),
                ),
            ),
            makeJsIdentifier(pos, "a", null),
        )
    }

    @Test
    fun forLetOf() = assertRendered(
        baseName = "f",
        source = "for (let x of it) {}",
        expectedJson = """
        {
          js: ```
          for (let x of it) {
          }

          ```
        }
        """.trimIndent(),
    ) { loc ->
        val pos = Position(loc, 0, 0)
        Js.ForOfStatement(
            pos,
            Js.VariableDeclaration(
                pos,
                listOf(
                    Js.VariableDeclarator(
                        pos,
                        makeJsIdentifier(pos, "x", null),
                        null,
                    ),
                ),
                Js.DeclarationKind.Let,
            ),
            makeJsIdentifier(pos, "it", null),
            Js.BlockStatement(pos, emptyList()),
            awaits = false,
        )
    }

    @Test
    fun commentLinesAreOnTheirOwnLine() = assertRendered(
        baseName = "x",
        source = "",
        expectedJson = """
            |{
            |  js: ```
            |     // foo
            |     //
            |     // no trailing space on prior line
            |     f();
            |     // bar
            |
            |     ```
            |}
        """.trimMargin(),
    ) { loc ->
        val pos = Position(loc, 0, 0)
        Js.Program(
            pos,
            listOf(
                Js.CommentLine(pos, "foo"),
                Js.CommentLine(pos, ""),
                Js.CommentLine(pos, "no trailing space on prior line"),
                Js.ExpressionStatement(
                    pos,
                    Js.CallExpression(
                        pos,
                        Js.Identifier(pos, JsIdentifierName("f"), null),
                        arguments = emptyList(),
                        optional = false,
                    ),
                ),
                Js.CommentLine(pos, "bar"),
            ),
        )
    }

    @Test
    fun templateExpression() = assertRendered(
        "template-strings",
        source = """ "+foo\n-bar%{ x }$%{}{ "baz" }\%{ afterEsc }%{ adjacent }" """.replace('%', '$'),
        expectedJson = """
            |{
            |  js: ```
            |    `+foo
            |    -bar%{ x }\$%{ `baz` }\\%{ afterEsc }%{ adjacent }`
            |
            |    ```
            |}
        """.trimMargin().replace('%', '$'),
    ) { loc ->
        val pos = Position(loc, 0, 0)
        Js.TemplateExpression(
            pos,
            quasis = listOf(
                Js.TemplateElement(pos, "+foo\n-bar"),
                Js.TemplateElement(pos, "\\$"),
                Js.TemplateElement(pos, "\\\\"),
                Js.TemplateElement(pos, ""),
                Js.TemplateElement(pos, ""),
            ),
            expressions = listOf(
                Js.Identifier(pos, JsIdentifierName("x"), null),
                Js.TemplateExpression(
                    pos,
                    quasis = listOf(
                        Js.TemplateElement(pos, "baz"),
                    ),
                    expressions = emptyList(),
                ),
                Js.Identifier(pos, JsIdentifierName("afterEsc"), null),
                Js.Identifier(pos, JsIdentifierName("adjacent"), null),
            ),
        )
    }

    private fun assertRendered(
        baseName: String,
        source: String,
        expectedJson: String,
        makeAst: (CodeLocation) -> OutTree<*>,
    ) {
        val root = OutputRoot(MemoryFileSystem())

        val sourceFileName = "$baseName.temper"
        val libraryRoot = dirPath()
        val sourcePath = libraryRoot
            .resolve(FilePathSegment(sourceFileName), isDir = false)
        val loc = ModuleName(
            sourceFile = sourcePath,
            libraryRootSegmentCount = libraryRoot.segments.size,
            isPreface = false,
        )
        val ast = makeAst(loc)
        val outFile = root
            .makeDir(FilePathSegment("out"))
            .makeRegularFile(FilePathSegment("$baseName.js"))
        val outFilePath = outFile.path
        val formatter = OutCodeFormatter(
            outFilePath,
            lookupCodeLocation = {
                if (it == loc) {
                    sourcePath to FilePositions.fromSource(loc, source)
                } else {
                    null
                }
            },
        )
        var finished = true
        outFile.supplyTextContent(
            { out -> formatter.format(ast, out) },
            { ok ->
                if (!ok) {
                    fail("rendering failed")
                }
                finished = true
            },
        )
        assertTrue(finished)

        val sourceMap = formatter.buildSourceMap {
            if (it == loc) {
                source
            } else {
                null
            }
        }
        val outputJs = root.textContentOf(outFilePath)!!
        val pairedChunks = pairChunks(source, outputJs, sourceMap)

        val got = object : Structured {
            override fun destructure(structureSink: StructureSink) = structureSink.obj {
                key("js") { value(outputJs) }
                key("sourceMap", Hints.u) { value(sourceMap) }
                key("chunks", Hints.u) { value(pairedChunks) }
            }
        }

        assertStructure(expectedJson, got)
    }
}

private fun makeJsIdentifier(pos: Position, nameText: String, sourceName: TemperName?) =
    Js.Identifier(pos, JsIdentifierName(nameText), sourceName)

private fun makeParam(pos: Position, nameText: String) =
    Js.Param(pos, makeJsIdentifier(pos, nameText, null))
