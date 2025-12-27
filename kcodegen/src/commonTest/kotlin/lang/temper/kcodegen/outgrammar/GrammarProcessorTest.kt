package lang.temper.kcodegen.outgrammar

import lang.temper.common.Log
import lang.temper.common.assertStringsEqual
import lang.temper.common.console
import lang.temper.common.indexOf
import lang.temper.common.stripDoubleHashCommentLinesToPutCommentsInlineBelow
import lang.temper.kcodegen.OutputGrammarCodeGenerator
import lang.temper.log.LogEntry
import lang.temper.log.LogSink
import lang.temper.log.MessageTemplateI
import lang.temper.log.Position
import lang.temper.log.excerpt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.fail

class GrammarProcessorTest {
    @Test
    fun enumTypeWithSpecializedTokens() = assertGeneratedKotlin(
        """
            |enum Foo = A("+") | B | Cee("-1") | D("") | E("\"2.0\"");
        """.trimMargin(),
        wantedChunks = listOf(
            """
                |enum class Foo : FormattableEnum {
                |    A,
                |    B,
                |    Cee,
                |    D,
                |    E,
                |    ;
                |
                |    override fun renderTo(tokenSink: TokenSink) {
                |        when (this) {
                |            A -> tokenSink.punctuation("+")
                |            B -> tokenSink.word("b")
                |            Cee -> tokenSink.number("-1")
                |            D -> {}
                |            E -> tokenSink.quoted("\u00222.0\u0022")
                |        }
                |    }
                |}
            """.trimMargin(),
        ),
    )

    @Test
    fun formattingArbitraryType() = assertGeneratedKotlin(
        """
            |Foo ::= "foo" & "(" & x%`SomeType` & ")";
        """.trimMargin(),
        wantedChunks = listOf(
            """
                |override fun formatElement(
                |    index: Int,
                |): IndexableFormattableTreeElement {
                |    return when (index) {
                |        0 -> IndexableFormattableTreeElement.wrap(this.x)
                |        else -> throw IndexOutOfBoundsException("${'$'}index")
                |    }
                |}
            """.trimMargin(),
        ),
    )

    @Test
    fun oneGeneratedAstNodeType() = assertGeneratedKotlin(
        sourceText = """
            |FooBar ::= "foo" & ((bar%FooBar => bar) || ());
        """.trimMargin(),
        wantedChunks = listOf(
            """
                |class FooBar(
                |    pos: Position,
                |    bar: FooBar?,
                |) : BaseTree(pos) {
                |    override val operatorDefinition: MyOperatorDefinition?
                |        get() = null
                |    override val codeFormattingTemplate: CodeFormattingTemplate
                |        get() =
                |            if (bar != null) {
                |                sharedCodeFormattingTemplate0
                |            } else {
                |                sharedCodeFormattingTemplate1
                |            }
                |    override val formatElementCount
                |        get() = 1
                |    override fun formatElement(
                |        index: Int,
                |    ): IndexableFormattableTreeElement {
                |        return when (index) {
                |            0 -> this.bar ?: FormattableTreeGroup.empty
                |            else -> throw IndexOutOfBoundsException("${'$'}index")
                |        }
                |    }
                |    private var _bar: FooBar?
                |    var bar: FooBar?
                |        get() = _bar
                |        set(newValue) { _bar = updateTreeConnection(_bar, newValue) }
                |    override fun deepCopy(): FooBar {
                |        return FooBar(pos, bar = this.bar?.deepCopy())
                |    }
                |    override val childMemberRelationships
                |        get() = cmr
                |    override fun equals(
                |        other: Any?,
                |    ): Boolean {
                |        return other is FooBar && this.bar == other.bar
                |    }
                |    override fun hashCode(): Int {
                |        return (bar?.hashCode() ?: 0)
                |    }
                |    init {
                |        this._bar = updateTreeConnection(null, bar)
                |    }
                |    companion object {
                |        private val cmr = ChildMemberRelationships(
                |            { n -> (n as FooBar).bar },
                |        )
                |    }
                |}
            """.trimMargin(),
        ),
    )

    @Test
    fun oneGeneratedDataClass() = assertGeneratedKotlin(
        sourceText = """
            |data FooBar ::= "foo" & ((bar%FooBar => bar) || ());
            |FooBar.oneTwoThree%`Int` = `123`;
        """.trimMargin(),
        wantedChunks = listOf(
            // The auto-generated super-types for
            """
                |sealed interface Data : OutData<Data> {
                |    override fun formattingHints(): FormattingHints = MyFormattingHints.getInstance()
                |    override val operatorDefinition: MyOperatorDefinition?
                |}
            """.trimMargin(),
            """
                |sealed class BaseData : BaseOutData<Data>(), Data
            """.trimMargin(),
            // The generated FooBar type.
            """
                |data class FooBar(
                |    override val sourceLibrary: DashedIdentifier,
                |## Members are just data constructor properties
                |    val bar: FooBar?,
                |) : BaseData() {
                |    override val operatorDefinition: MyOperatorDefinition?
                |        get() = null
                |    override val codeFormattingTemplate: CodeFormattingTemplate
                |        get() =
                |            if (bar != null) {
                |                sharedCodeFormattingTemplate0
                |            } else {
                |                sharedCodeFormattingTemplate1
                |            }
                |    override val formatElementCount
                |        get() = 1
                |    override fun formatElement(
                |        index: Int,
                |    ): IndexableFormattableTreeElement {
                |        return when (index) {
                |            0 -> this.bar ?: FormattableTreeGroup.empty
                |            else -> throw IndexOutOfBoundsException("${'$'}index")
                |        }
                |    }
                |    val oneTwoThree: Int
                |        get() = 123
                |    override val childMemberRelationships
                |        get() = cmr
                |## Just use equals() and hashCode from Kotlin data classes.
                |    companion object {
                |        private val cmr = ChildMemberRelationships(
                |            { n -> (n as FooBar).bar },
                |        )
                |    }
                |}
            """.trimMargin().stripDoubleHashCommentLinesToPutCommentsInlineBelow(),
        ),
    )

    @Test
    fun oneGeneratedDataInterface() = assertGeneratedKotlin(
        sourceText = """
            |data FooOrBar = Foo | Bar;
            |FooOrBar(x%FooOrBar?);
            |FooOrBar.oneTwoThree%`Int` = `123`;
            |data Foo ::= "foo" & ((x%FooOrBar => x) || ());
            |data Bar ::= "bar" & ((x%FooOrBar => x) || ());
        """.trimMargin(),
        wantedChunks = listOf(
            // The generated FooOrBar type.
            """
                |sealed interface FooOrBar : Data {
                |    val x: FooOrBar?
                |    val oneTwoThree: Int
                |        get() = 123
                |}
            """.trimMargin(),
            """
                |data class Foo(
                |    override val sourceLibrary: DashedIdentifier,
                |    override val x: FooOrBar?,
                |) : BaseData(), FooOrBar {
            """.trimMargin(),
            """
                |data class Bar(
                |    override val sourceLibrary: DashedIdentifier,
                |    override val x: FooOrBar?,
                |) : BaseData(), FooOrBar {
            """.trimMargin(),
        ),
    )

    @Test
    fun nodeTypeUsedAsKotlinType() = assertGeneratedKotlin(
        sourceText = """
            |Foo ::= "foo";
            |Bar ::= "bar";
            |FooBar ::= foo%Foo & bar%`Bar`;
        """.trimMargin(),
        wantedErrors = listOf(
            // This can lead to missing child/parent links which can lead to subtrees being
            // missed during recursive analysis, and problems with deepCopy() methods not
            // shallowly copying subtrees.
            "Type for FooBar.bar declared with backticks but is a node type name: Bar!!",
        ),
    )

    @Test
    fun dataTypeCannotExtendAstType() = assertGeneratedKotlin(
        sourceText = """
            |FooOrBar = Foo | Bar;
            |FooOrBar(x%FooOrBar?);
            |data Foo ::= "foo" & ((x%FooOrBar => x) || ());
            |Bar ::= "bar" & ((x%FooOrBar => x) || ());
        """.trimMargin(),
        wantedErrors = listOf(
            // Because of the super-type relationship
            "Data nodes cannot extend AST nodes or vice versa: data Foo extends FooOrBar!!",
            // Because data Foo stores a tree FooOrBar
            "Data nodes may not have AST node properties: Foo.x has kind Ast!!",
        ),
    )

    @Test
    fun impliedButNotDefinedType() = assertGeneratedKotlin(
        """
            |Foo = Bar | Baz;
            |// Bar not declared.
            |Baz();
        """.trimMargin(),
        wantedErrors = listOf(
            "Node type Bar is implied but never declared!!",
        ),
    )

    @Test
    fun missingPropertyType() = assertGeneratedKotlin(
        """
            |TypeHaver ::= t%Type;
            |Typo ::= "type";
            |data TypeHaverData from TypeHaver;
        """.trimMargin(),
        wantedErrors = listOf(
            "Missing type for property TypeHaverData.t!!",
            "No type for TypeHaver.t!!",
            "Node type Type is implied but never declared!!",
            "Missing info needed to generate code for property data TypeHaverData.t!!",
        ),
    )

    @Test
    fun derivationExplicitAndInferred() = assertGeneratedKotlin(
        sourceText = """
            |FooBar ::= foo%Foo & "&" & bar%Bar & "&" & baz%Baz;
            |Foo ::= "Foo";
            |Foo.x%`Int` = `1`;
            |Bar ::= "Bar";
            |Bar.x%`Int` = `2`;
            |Baz ::= "Baz";
            |Baz.x%`Int` = `3`;
            |
            |/**
            | * We'll auto-derive FooData and BarData because this needs them but
            | * FooBarDataBundle intentionally does not follow the *Data naming convention.
            | */
            |data FooBarDataBundle from FooBar;
            |// data BarData is inferred as derived from Bar
            |data FooData; // FooData is declared as data but not derived from Foo
            |FooData.renderTo = `tokenSink.word("FOO")`;
            |FooData.x%`Int` = `4`;
            |data BazData from Baz; // BazData is derived and overrides x
            |BazData.x%`Int` = `5`;
        """.trimMargin(),
        wantedChunks = listOf(
            """
                |data class FooBarDataBundle(
                |    override val sourceLibrary: DashedIdentifier,
                |    val foo: FooData,
                |    val bar: BarData,
                |    val baz: BazData,
                |) : BaseData() {
                |    override val operatorDefinition: MyOperatorDefinition?
                |        get() = null
                |    override val codeFormattingTemplate: CodeFormattingTemplate
                |        get() = sharedCodeFormattingTemplate0
                |    override val formatElementCount
                |        get() = 3
                |    override fun formatElement(
                |        index: Int,
                |    ): IndexableFormattableTreeElement {
                |        return when (index) {
                |            0 -> this.foo
                |            1 -> this.bar
                |            2 -> this.baz
                |            else -> throw IndexOutOfBoundsException("${'$'}index")
                |        }
                |    }
                |    override val childMemberRelationships
                |        get() = cmr
                |    companion object {
                |        private val cmr = ChildMemberRelationships(
                |            { n -> (n as FooBarDataBundle).foo },
                |            { n -> (n as FooBarDataBundle).bar },
                |            { n -> (n as FooBarDataBundle).baz },
                |        )
                |    }
                |}
            """.trimMargin(),
            // FooData is declared, not derived.
            """
                |data class FooData(
                |    override val sourceLibrary: DashedIdentifier,
                |) : BaseData() {
                |    override val operatorDefinition: MyOperatorDefinition?
                |        get() = null
                |    override fun renderTo(
                |        tokenSink: TokenSink,
                |    ) {
                |        tokenSink.word("FOO")
                |    }
                |    override val codeFormattingTemplate: CodeFormattingTemplate?
                |        get() = null
                |    val x: Int
                |        get() = 4
                |    override val childMemberRelationships
                |        get() = cmr
                |    companion object {
                |        private val cmr = ChildMemberRelationships()
                |    }
                |}
            """.trimMargin(),
            // And we auto-derive BarData and fill in BazData
            """
                |data class BarData(
                |    override val sourceLibrary: DashedIdentifier,
                |) : BaseData() {
                |    override val operatorDefinition: MyOperatorDefinition?
                |        get() = null
                |    override val codeFormattingTemplate: CodeFormattingTemplate
                |        get() = sharedCodeFormattingTemplate2
                |    override val formatElementCount
                |        get() = 0
                |    val x: Int
                |        get() = 2
                |    override val childMemberRelationships
                |        get() = cmr
                |    companion object {
                |        private val cmr = ChildMemberRelationships()
                |    }
                |}
            """.trimMargin(),
            """
                |data class BazData(
                |    override val sourceLibrary: DashedIdentifier,
                |) : BaseData() {
                |    override val operatorDefinition: MyOperatorDefinition?
                |        get() = null
                |    override val codeFormattingTemplate: CodeFormattingTemplate
                |        get() = sharedCodeFormattingTemplate3
                |    override val formatElementCount
                |        get() = 0
                |    val x: Int
                |        get() = 5
                |    override val childMemberRelationships
                |        get() = cmr
                |    companion object {
                |        private val cmr = ChildMemberRelationships()
                |    }
                |}
            """.trimMargin(),
        ),
    )

    @Test
    fun orTypeSubTypesAreDerived() = assertGeneratedKotlin(
        sourceText = """
            |Sup = Sub1 | Sub2;
            |data SupData from Sup;
            |Sub1;
            |Sub2;
        """.trimMargin(),
        wantedChunks = listOf(
            """
                |data class Sub1Data(
                |    override val sourceLibrary: DashedIdentifier,
                |) : BaseData(), SupData {
            """.trimMargin(),
            """
                |data class Sub2Data(
                |    override val sourceLibrary: DashedIdentifier,
                |) : BaseData(), SupData {
            """.trimMargin(),
        ),
    )

    @Test
    fun dataTypeCannotHaveTreeProperties() = assertGeneratedKotlin(
        sourceText = """
            |A ::= "a"; // not data
            |data B(a%A);
            |data.renderTo = `tokenSink.word("a")`;
        """.trimMargin(),
        wantedErrors = listOf(
            "Data nodes may not have AST node properties: B.a has kind Ast!!",
        ),
    )

    @Test
    fun treeNodeCanContainNonChildDataNode() = assertGeneratedKotlin(
        """
            |data MyData ::= "world";
            |
            |MyTree ::= "hello" & "," & data%MyData;
        """.trimMargin(),
        wantedChunks = listOf(
            """
                |data class MyData(
                |    override val sourceLibrary: DashedIdentifier,
                |) : BaseData() {
                |    override val operatorDefinition: MyOperatorDefinition?
                |        get() = null
                |    override val codeFormattingTemplate: CodeFormattingTemplate
                |        get() = sharedCodeFormattingTemplate0
                |    override val formatElementCount
                |        get() = 0
                |    override val childMemberRelationships
                |        get() = cmr
                |    companion object {
                |        private val cmr = ChildMemberRelationships()
                |    }
                |}
            """.trimMargin(),
            """
                |class MyTree(
                |    pos: Position,
                |    data: MyData,
                |) : BaseTree(pos) {
                |    override val operatorDefinition: MyOperatorDefinition?
                |        get() = null
                |    override val codeFormattingTemplate: CodeFormattingTemplate
                |        get() = sharedCodeFormattingTemplate1
                |    override val formatElementCount
                |        get() = 1
                |    override fun formatElement(
                |        index: Int,
                |    ): IndexableFormattableTreeElement {
                |        return when (index) {
                |## data shows up as a formattable element but not farther down as a child
                |            0 -> this.data
                |            else -> throw IndexOutOfBoundsException("${'$'}index")
                |        }
                |    }
                |    private var _data: MyData
                |    var data: MyData
                |        get() = _data
                |## No child/parent linking instructions here or in the init block below.
                |        set(newValue) { _data = newValue }
                |    override fun deepCopy(): MyTree {
                |## No use of deepCopy().  Data node types have a .copy() operator because
                |## they are data classes but there is no need to copy because they are not
                |## tree-mutable the way AST nodes are.
                |        return MyTree(pos, data = this.data)
                |    }
                |    override val childMemberRelationships
                |        get() = cmr
                |    override fun equals(
                |        other: Any?,
                |    ): Boolean {
                |        return other is MyTree && this.data == other.data
                |    }
                |    override fun hashCode(): Int {
                |        return data.hashCode()
                |    }
                |    init {
                |        this._data = data
                |    }
                |    companion object {
                |## No mention of data here.
                |        private val cmr = ChildMemberRelationships()
                |    }
                |}
            """.trimMargin().stripDoubleHashCommentLinesToPutCommentsInlineBelow(),
        ),
    )

    @Test
    fun fromSuperTypesCherryPicked() = assertGeneratedKotlin(
        """
            |Sup = Mid;
            |Mid = Sub1 | Sub2;
            |Sub1 ::= "sub1" & x%`X`;
            |Sub2 ::= "sub2";
            |
            |data Sub1Data from Sub1;
            |data SupData =~ Sup;
        """.trimMargin(),
        wantedChunks = listOf(
            """
                |data class Sub1Data(
                |    override val sourceLibrary: DashedIdentifier,
                |    val x: X,
                |) : BaseData(), SupData {
            """.trimMargin(),
        ),
        unmatchedLines = listOf(
            // We said SupData =~ Sup, so it should show up as a super-type of
            // Sub1Data above despite `Mid` being unassociated with any data node type, but
            // we should not auto-derive Sub2Data from Sub2 because there is no
            // `from`-derived supertype that inherits a `|` clause that leads to Sub2.
            "data class Sub2Data(",
        ),
    )

    @Test
    fun kotlinSuperType() = assertGeneratedKotlin(
        """
            |NameNode implements `TemperTypeName`;
            |NameNode(nameText%`String`);
            |NameNode.renderTo = `tokenSink.emit(OutputToken(nameText, OutputTokenType.Name));`
        """.trimMargin(),
        wantedChunks = listOf(
            """
                |class NameNode(
                |    pos: Position,
                |    var nameText: String,
                |) : BaseTree(pos), TemperTypeName {
            """.trimMargin(),
        ),
    )

    @Test
    fun childMemberRelationshipsForNonSyntaxClasses() = assertGeneratedKotlin(
        """
            |Bar ::= "bar";
            |Baz ::= "baz";
            |
            |Foo(a%Bar, b%Baz?);
            |Foo.renderTo = `
            |   a.renderTo(tokenSink)
            |   b?.renderTo(tokenSink)
            |   `;
        """.trimMargin(),
        wantedChunks = listOf(
            """
                |class Foo(
                |    pos: Position,
                |    a: Bar,
                |    b: Baz?,
                |) : BaseTree(pos) {
                |    override val operatorDefinition: MyOperatorDefinition?
                |        get() = null
                |    override fun renderTo(
                |        tokenSink: TokenSink,
                |    ) {
                |        a.renderTo(tokenSink)
                |        b?.renderTo(tokenSink)
                |    }
                |    override val codeFormattingTemplate: CodeFormattingTemplate?
                |        get() = null
                |    private var _a: Bar
                |    var a: Bar
                |        get() = _a
                |        set(newValue) { _a = updateTreeConnection(_a, newValue) }
                |    private var _b: Baz?
                |    var b: Baz?
                |        get() = _b
                |        set(newValue) { _b = updateTreeConnection(_b, newValue) }
                |    override fun deepCopy(): Foo {
                |        return Foo(pos, a = this.a.deepCopy(), b = this.b?.deepCopy())
                |    }
                |    override val childMemberRelationships
                |        get() = cmr
                |    override fun equals(
                |        other: Any?,
                |    ): Boolean {
                |        return other is Foo && this.a == other.a && this.b == other.b
                |    }
                |    override fun hashCode(): Int {
                |        var hc = a.hashCode()
                |        hc = 31 * hc + (b?.hashCode() ?: 0)
                |        return hc
                |    }
                |    init {
                |        this._a = updateTreeConnection(null, a)
                |        this._b = updateTreeConnection(null, b)
                |    }
                |    companion object {
                |        private val cmr = ChildMemberRelationships(
                |            { n -> (n as Foo).a },
                |            { n -> (n as Foo).b },
                |        )
                |    }
                |}
            """.trimMargin(),
        ),
    )

    @Test
    fun namesCanExtendKotlinTypeAndDerive() = assertGeneratedKotlin(
        """
            |## Importing this means we can resolve fields in it by reflection if needed.
            |let imports = ${"\"\"\""}
            |  "lang.temper.be.TargetLanguageName
            |;
            |
            |data MyNameData = MyQualifiedNameData | MyLocalNameData;
            |MyNameData extends `TargetLanguageName`;
            |MyNameData(
            |  finalName%`MyNamePart`,
            |  showSerialNum%`Boolean`,
            |  serialNum%`Int`,
            |);
            |MyNameData.suffix%`MyNamePart` = `
            |    if (showSerialNum) {
            |        finalName.withSerialNum(serialNum)
            |    } else {
            |        finalName
            |    }
            |    `;
            |
            |MyQualifiedNameData ::= prefixParts%`MyNamePart`*"." & ((prefixParts => ".") || ()) & suffix;
            |MyLocalNameData     ::=                                                               suffix;
            |
            |## When we derive the tree type from the data type we ned to also extend TargetLanguageName.
            |MyName from MyNameData;
        """.trimMargin().stripDoubleHashCommentLinesToPutCommentsInlineBelow(),
        wantedChunks = listOf(
            """
                |sealed interface MyNameData : Data, TargetLanguageName {
                |    val finalName: MyNamePart
                |    val showSerialNum: Boolean
                |    val serialNum: Int
                |    val suffix: MyNamePart
                |        get() =
                |            if (showSerialNum) {
                |                finalName.withSerialNum(serialNum)
                |            } else {
                |                finalName
                |            }
                |}
            """.trimMargin(),
            """
                |data class MyQualifiedNameData(
                |    override val sourceLibrary: DashedIdentifier,
                |    val prefixParts: List<MyNamePart>,
                |    override val finalName: MyNamePart,
                |    override val showSerialNum: Boolean,
                |    override val serialNum: Int,
                |) : BaseData(), MyNameData {
            """.trimMargin(),
            """
                |data class MyLocalNameData(
                |    override val sourceLibrary: DashedIdentifier,
                |    override val finalName: MyNamePart,
                |    override val showSerialNum: Boolean,
                |    override val serialNum: Int,
                |) : BaseData(), MyNameData {
            """.trimMargin(),
            """
                |sealed interface MyName : Tree, TargetLanguageName {
            """.trimMargin(),
            """
                |class MyQualifiedName(
                |    pos: Position,
                |    val prefixParts: MutableList<MyNamePart>,
                |    override var finalName: MyNamePart,
                |    override var showSerialNum: Boolean,
                |    override var serialNum: Int,
                |) : BaseTree(pos), MyName {
            """.trimMargin(),
            """
                |class MyLocalName(
                |    pos: Position,
                |    override var finalName: MyNamePart,
                |    override var showSerialNum: Boolean,
                |    override var serialNum: Int,
                |) : BaseTree(pos), MyName {
            """.trimMargin(),
        ),
    )

    fun assertGeneratedKotlin(
        sourceText: String,
        wantedChunks: List<String> = emptyList(),
        wantedErrors: List<String> = emptyList(),
        unmatchedLines: List<String> = emptyList(),
        subProject: String = "TestOutGrammar",
    ) {
        val logEntries = mutableListOf<LogEntry>()
        val logSink = object : LogSink {
            // Be verbose about unexpected errors as they crop up
            override var hasFatal: Boolean = false
                private set

            override fun log(
                level: Log.Level,
                template: MessageTemplateI,
                pos: Position,
                values: List<Any>,
                fyi: Boolean,
            ) {
                val logEntry = LogEntry(level, template, pos, values, fyi)
                logEntries.add(logEntry)
                if (level >= Log.Error) {
                    if (level >= Log.Fatal) { hasFatal = true }
                    val messageText = logEntry.messageText
                    if (messageText !in wantedErrors) {
                        excerpt(logEntry.pos, sourceText, console.textOutput)
                        console.trace(messageText)
                    }
                }
            }
        }
        val gp = OutputGrammarCodeGenerator(subProject = subProject, logSink = logSink)
        val got = gp.generateGrammarSource(listOf("path", "to", "my.out-grammar"), sourceText = sourceText)

        val errorLogEntries = logEntries.filter { it.level >= Log.Error }
        val gotErrors = errorLogEntries.map { it.messageText }
        assertEquals(wantedErrors.toSet(), gotErrors.toSet())
        assertEquals(wantedErrors.isNotEmpty(), got.contentHasErrors)

        val content = got.content
        assertNotNull(content)

        val contentLines = content.lines().filter { it.isNotBlank() }
        for (wantedChunk in wantedChunks) {
            val wantedLines = wantedChunk.lines().filter { it.isNotBlank() }
            val firstWantedLine = wantedLines.first()
            val lastWantedLine = wantedLines.last()
            // Look for the first wanted line but do not be particular about the leading whitespace
            val firstWantedLineIndex = contentLines.indexOfFirst {
                it.endsWith(firstWantedLine) && it.substring(0, it.length - firstWantedLine.length).isBlank()
            }
            if (firstWantedLineIndex < 0) {
                console.group("Generated source") {
                    console.log(content)
                }
                fail("Did not find `$firstWantedLine`")
            }
            // Extract the ignored space prefix so that we can use it to find the matching end line
            val strippedSpacePrefix = run {
                val actualLine = contentLines[firstWantedLineIndex]
                actualLine.substring(0, actualLine.length - firstWantedLine.length)
            }
            // Find the end line which is either:
            // - an exact match for $strippedSpacePrefix$lastWantedLine after firstWantedLineIndex in contentLines
            // - or the last line where the indentation reduces below that of the first line.
            // - or the last content line
            var lastWantedLineIndex = contentLines.indexOf(
                "$strippedSpacePrefix$lastWantedLine",
                firstWantedLineIndex,
            )
            if (lastWantedLineIndex < 0) {
                for (i in firstWantedLineIndex + 1..contentLines.lastIndex) {
                    val contentLine = contentLines[i]
                    if (!contentLine.startsWith(strippedSpacePrefix)) {
                        lastWantedLineIndex = i - 1
                        break
                    }
                }
            }

            // chunks with leading whitespace normalized
            val gotChunk = (firstWantedLineIndex..lastWantedLineIndex).joinToString("\n") {
                contentLines[it]
            }.trimIndent()
            val wantChunk = wantedLines.joinToString("\n") {
                "$strippedSpacePrefix$it"
            }.trimIndent()

            assertStringsEqual(wantChunk, gotChunk)
        }

        val shouldNotMatchContentLines = buildMap {
            for (unmatchedLine in unmatchedLines) {
                val index = contentLines.indexOfFirst { it.trim() == unmatchedLine.trim() }
                if (index >= 0) {
                    this[index] = unmatchedLine
                }
            }
        }
        if (shouldNotMatchContentLines.isNotEmpty()) {
            console.group("contentLines") {
                console.log(
                    contentLines.indices.joinToString("\n") { lineIndex ->
                        val prefix = if (lineIndex in shouldNotMatchContentLines) {
                            "!!! "
                        } else {
                            "    "
                        }
                        "$prefix${contentLines[lineIndex]}"
                    },
                )
            }
            for ((matchIndex, unmatchedLine) in shouldNotMatchContentLines) {
                console.group("Should not match") {
                    console.log("- `$unmatchedLine` matched at $matchIndex")
                }
            }
            fail("${shouldNotMatchContentLines.size} lines should not have matched")
        }
    }
}
