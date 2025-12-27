package lang.temper.interp.importExport

import lang.temper.ast.TreeVisit
import lang.temper.ast.VisitCue
import lang.temper.ast.flatten
import lang.temper.astbuild.StoredCommentTokens
import lang.temper.astbuild.buildTree
import lang.temper.common.ListBackedLogSink
import lang.temper.common.Log
import lang.temper.common.TestDocumentContext
import lang.temper.common.assertStructure
import lang.temper.common.buildListMultimap
import lang.temper.common.putMultiList
import lang.temper.common.structure.StructureSink
import lang.temper.common.structure.Structured
import lang.temper.common.testCodeLocation
import lang.temper.cst.CstComment
import lang.temper.lexer.Lexer
import lang.temper.log.Position
import lang.temper.log.filePath
import lang.temper.name.ExportedName
import lang.temper.name.ModuleName
import lang.temper.name.NamingContext
import lang.temper.name.ParsedName
import lang.temper.name.Symbol
import lang.temper.parser.parse
import lang.temper.value.BlockTree
import lang.temper.value.DeclTree
import lang.temper.value.TBoolean
import lang.temper.value.TInt
import lang.temper.value.TString
import lang.temper.value.TypeInferences
import lang.temper.value.Value
import lang.temper.value.connectedSymbol
import lang.temper.value.defaultParsedName
import lang.temper.value.staySymbol
import lang.temper.value.toPseudoCode
import kotlin.test.Ignore
import kotlin.test.Test

class CreateLocalBindingsForImportTest {
    private fun assertBindings(
        input: String,
        want: String,
        wantErrors: List<String> = emptyList(),
        makeExportList: ExportListBuilder.() -> Unit,
    ) {
        val logSink = ListBackedLogSink()
        val importRecords = mutableListOf<Importer.ImportRecord>()

        val importer = object : Importer {
            override val loc = ModuleName(
                sourceFile = filePath("lib", "importer"),
                libraryRootSegmentCount = 1,
                isPreface = false,
            )

            override fun recordImportMetadata(importRecord: Importer.ImportRecord) {
                importRecords.add(importRecord)
            }
        }

        val exporter = object : NamingContext(), Exporter {
            override val loc = ModuleName(
                sourceFile = filePath("lib", "exporter"),
                libraryRootSegmentCount = 1,
                isPreface = false,
            )
            override val exports = run {
                val pos = Position(loc, 0, 0)
                val builder = ExportListBuilder(this, pos, namingContext = this)
                builder.makeExportList()
                builder.toExportList()
            }
        }

        val importerContext = TestDocumentContext(
            loc = ModuleName(
                sourceFile = filePath("lib", "importer"),
                libraryRootSegmentCount = 1,
                isPreface = false,
            ),
        )

        val root = run {
            val lexer = Lexer(testCodeLocation, logSink, input)
            val comments = mutableListOf<CstComment>()
            val cst = parse(lexer, logSink, comments)
            val cstParts = flatten(cst)
            // We need a root block so that we can splice single declarations adjacent to
            // multi-declarations.
            BlockTree.maybeWrap(
                buildTree(
                    cstParts = cstParts.toList(),
                    storedCommentTokens = StoredCommentTokens(comments),
                    logSink = logSink,
                    documentContext = importerContext,
                ),
            )
        }
        val decl: DeclTree = run {
            var decl: DeclTree? = null
            TreeVisit.startingAt(root)
                .forEach {
                    if (it is DeclTree) {
                        decl = it
                        VisitCue.AllDone
                    } else {
                        VisitCue.Continue
                    }
                }
                .visitPreOrder()
            decl as DeclTree
        }

        // We're going to have stay leaves on actual imports, so add one.
        decl.replace(decl.size until decl.size) {
            V(decl.pos.rightEdge, staySymbol)
            Stay()
        }

        createLocalBindingsForImport(
            decl,
            importer,
            exporter,
            logSink,
            specifier = "file:../exporter",
        )

        assertStructure(
            object : Structured {
                override fun destructure(structureSink: StructureSink) = structureSink.obj {
                    key("pseudoCode") { value(want) }
                    key("errors") { value(wantErrors) }
                }
            },
            object : Structured {
                override fun destructure(structureSink: StructureSink) = structureSink.obj {
                    key("pseudoCode") { value(root.toPseudoCode(singleLine = false)) }
                    key("errors") {
                        arr {
                            logSink.allEntries.forEach {
                                if (it.level >= Log.Error) {
                                    value(it.messageText)
                                }
                            }
                        }
                    }
                }
            },
            inputContext = emptyMap(),
        )
    }

    @Test
    fun singleBindingNameMatches() = assertBindings(
        input = """
        |let { x } = import("./exporter");
        """.trimMargin(),
        want = """
        |@stay @imported(\(`lib//exporter`.x)) let x = `lib//exporter`.x;
        |
        """.trimMargin(),
    ) {
        export("x", Value(42, TInt))
    }

    /**
     * In our latest planning, we've made non-destructured import illegal for now.
     * We'll work out semantics later.
     * Some options:
     *
     * - Use the assigned name as a namespace but not as an object, sort of like current `builtins`.
     * - Reify each module as an objects of an implicit type.
     * - Allow some default export as shown here, or maybe defaults just apply to backend generation where
     *   applicable, such as JS.
     */
    @Ignore
    @Test
    fun singleBindingNameBindsToDefault() = assertBindings(
        input = """
        |let exporter = import("./exporter");
        """.trimMargin(),
        want = """
        |@stay @imported(\(`lib//exporter`.default)) let exporter = `lib//exporter`.default;
        |
        """.trimMargin(),
    ) {
        export("y", Value(42, TInt))
        export(defaultParsedName.nameText, TBoolean.valueTrue)
    }

    @Test
    fun singleBindingNameFails() = assertBindings(
        input = """
        |let x = import("./exporter");
        """.trimMargin(),
        want = """
        |error (\(@stay let x = import(cat("./exporter"))));
        |
        """.trimMargin(),
        wantErrors = listOf(
            "No symbol for import from lib//exporter!",
        ),
    ) {
        export("y", Value(42, TInt))
    }

    @Test
    fun importOfListOfNamesOutOfOrderWithAnnotation() = assertBindings(
        input = """
        |@annotation(complexExpression()) let { z, y, x } = import("./exporter");
        """.trimMargin(),
        want = """
        |let t#0 = complexExpression();
        |nym`@`(annotation(t#0), @stay @imported(\(`lib//exporter`.z)) let z = `lib//exporter`.z);
        |nym`@`(annotation(t#0), @imported(\(`lib//exporter`.y)) let y = `lib//exporter`.y);
        |nym`@`(annotation(t#0), @imported(\(`lib//exporter`.x)) let x = `lib//exporter`.x);
        |
        """.trimMargin(),
    ) {
        export("x", Value("X", TString))
        export("y", Value("Y", TString))
        export("z", Value("Z", TString))
    }

    @Test
    fun wildcardImport() = assertBindings(
        input = """
            |let { y, w as a, ... } = import("./exporter");
        """.trimMargin(),
        want = run {
            val importTexts = listOf(
                """@imported(\(`lib//exporter`.y)) let y = `lib//exporter`.y""",
                """@imported(\(`lib//exporter`.w)) a = `lib//exporter`.w""",
                """@imported(\(`lib//exporter`.x)) x = `lib//exporter`.x""",
                """@imported(\(`lib//exporter`.z)) z = `lib//exporter`.z""",
            ).joinToString(", ")
            "@stay $importTexts;\n"
        },
    ) {
        export("w", Value("W", TString))
        export("x", Value("X", TString))
        export("y", Value("Y", TString))
        export("z", Value("Z", TString))
    }

    @Test
    fun pureWildcardImport() = assertBindings(
        input = """
        |let { ... } = import("./exporter");
        """.trimMargin(),
        want = """
        |@stay @imported(\(`lib//exporter`.x)) let x = `lib//exporter`.x,${""
        } @imported(\(`lib//exporter`.y)) y = `lib//exporter`.y,${""
        } @imported(\(`lib//exporter`.z)) z = `lib//exporter`.z;
        |
        """.trimMargin(),
    ) {
        export("x", Value("X", TString))
        export("y", Value("Y", TString))
        export("z", Value("Z", TString))
    }

    @Test
    fun metadataCopied() = assertBindings(
        input = """
            |let { a, b, c } = import("./exporter");
        """.trimMargin(),
        // We were getting @connected("::a") on all three local declarations.
        want = """
            |@stay @imported(\(`lib//exporter`.a)) @connected("::a") let a = `lib//exporter`.a,
            |      @imported(\(`lib//exporter`.b)) @connected("::b")     b = `lib//exporter`.b,
            |      @imported(\(`lib//exporter`.c)) @connected("::c")     c = `lib//exporter`.c;
            |
        """.trimMargin()
            // trim internal whitespace so that we can line things up nicely above.
            .replace(Regex("[ \n]* "), " "),
    ) {
        export(
            "a",
            Value("A", TString),
            declarationMetadata = listOf(connectedSymbol to Value("::a", TString)),
        )
        export(
            "b",
            Value("B", TString),
            declarationMetadata = listOf(connectedSymbol to Value("::b", TString)),
        )
        export(
            "c",
            Value("C", TString),
            declarationMetadata = listOf(connectedSymbol to Value("::c", TString)),
        )
    }
}

private class ExportListBuilder(
    val exporter: Exporter,
    val defaultPosition: Position,
    val namingContext: NamingContext,
) {
    private val exports = mutableListOf<Export>()

    fun export(
        baseName: String,
        value: Value<*>,
        typeInferences: TypeInferences? = null,
        declarationMetadata: List<Pair<Symbol, Value<*>>> = emptyList(),
        pos: Position = defaultPosition,
    ) = export(
        name = ExportedName(namingContext, ParsedName(baseName)),
        value = value,
        typeInferences = typeInferences,
        declarationMetadata = declarationMetadata,
        pos = pos,
    )

    fun export(
        name: ExportedName,
        value: Value<*>,
        typeInferences: TypeInferences? = null,
        declarationMetadata: List<Pair<Symbol, Value<*>>> = emptyList(),
        pos: Position = defaultPosition,
    ) {
        add(
            Export(
                exporter = exporter,
                name = name,
                value = value,
                typeInferences = typeInferences,
                declarationMetadata = buildListMultimap {
                    for ((mdKey, mdValue) in declarationMetadata) {
                        this.putMultiList(mdKey, mdValue)
                    }
                },
                position = pos,
            ),
        )
    }

    fun add(export: Export) {
        exports.add(export)
    }

    fun toExportList() = exports.toList()
}
