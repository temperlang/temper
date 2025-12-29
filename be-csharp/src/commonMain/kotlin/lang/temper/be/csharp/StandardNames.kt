package lang.temper.be.csharp

import lang.temper.be.TargetLanguageTypeName
import lang.temper.format.OutToks
import lang.temper.format.OutputToken
import lang.temper.format.OutputTokenType
import lang.temper.format.TokenSink
import lang.temper.log.Position
import lang.temper.name.DashedIdentifier
import lang.temper.name.OutName

object StandardNames {
    val keyBool = "bool".toKeyTypeName()
    val keyDouble = "double".toKeyTypeName()
    val keyDoubleNan = keyDouble.member("NaN")
    val keyDoubleNegativeInfinity = keyDouble.member("NegativeInfinity")
    val keyDoublePositiveInfinity = keyDouble.member("PositiveInfinity")
    val keyInt = "int".toKeyTypeName()
    val keyLong = "long".toKeyTypeName()
    val keyObject = "object".toKeyTypeName()
    val keyString = "string".toKeyTypeName()
    val keyStringIsNullOrEmpty = keyString.member("IsNullOrEmpty")
    val keyVoid = "void".toKeyTypeName()
    private val microsoft = "Microsoft".toSpaceName()
    private val microsoftVisualStudio = microsoft.space("VisualStudio")
    private val microsoftVisualStudioTestTools = microsoftVisualStudio.space("TestTools")
    private val microsoftVisualStudioTestToolsUnitTesting = microsoftVisualStudioTestTools.space("UnitTesting")
    val microsoftVisualStudioTestToolsUnitTestingAssertFailedException =
        microsoftVisualStudioTestToolsUnitTesting.type("AssertFailedException")
    val microsoftVisualStudioTestToolsUnitTestingTestClass = microsoftVisualStudioTestToolsUnitTesting.type("TestClass")
    val microsoftVisualStudioTestToolsUnitTestingTestMethod =
        microsoftVisualStudioTestToolsUnitTesting.type("TestMethod")
    private val system = "System".toSpaceName()
    val systemAction = system.type("Action")
    private val systemArray = system.type("Array")
    val systemArrayAsReadOnly = systemArray.member("AsReadOnly")
    private val systemCollections = system.space("Collections")
    val systemCollectionsBitArray = systemCollections.type("BitArray")
    private val systemCollectionsGeneric = systemCollections.space("Generic")
    val systemCollectionsGenericDictionary = systemCollectionsGeneric.type("Dictionary")
    val systemCollectionsGenericEqualityComparer = systemCollectionsGeneric.type("EqualityComparer")
    val systemCollectionsGenericIDictionary = systemCollectionsGeneric.type("IDictionary")
    val systemCollectionsGenericIEnumerable = systemCollectionsGeneric.type("IEnumerable")
    val systemCollectionsGenericIList = systemCollectionsGeneric.type("IList")
    val systemCollectionsGenericIReadOnlyList = systemCollectionsGeneric.type("IReadOnlyList")
    val systemCollectionsGenericIReadOnlyDictionary = systemCollectionsGeneric.type("IReadOnlyDictionary")
    val systemCollectionsGenericKeyValuePair = systemCollectionsGeneric.type("KeyValuePair")
    val systemCollectionsGenericList = systemCollectionsGeneric.type("List")
    val systemCollectionsGenericQueue = systemCollectionsGeneric.type("Queue")
    private val systemConvert = system.type("Convert")
    val systemConvertToString = systemConvert.member("ToString")
    val systemDateTime = system.type("DateTime") // TODO System.DateOnly on NET6_0 or our own struct with conversions.
    val systemException = system.type("Exception")
    val systemFunc = system.type("Func")
    private val systemLinq = system.space("Linq")
    private val systemLinqEnumerable = systemLinq.type("Enumerable")
    val systemLinqEnumerableAggregate = systemLinqEnumerable.member("Aggregate", extension = true)
    val systemLinqEnumerableSelect = systemLinqEnumerable.member("Select", extension = true)
    val systemLinqEnumerableToList = systemLinqEnumerable.member("ToList", extension = true)
    val systemLinqEnumerableWhere = systemLinqEnumerable.member("Where", extension = true)
    val systemMath = system.type("Math")
    val systemMathE = systemMath.member("E")
    val systemMathMax = systemMath.member("Max")
    val systemMathMin = systemMath.member("Min")
    val systemMathPi = systemMath.member("PI")
    val systemMathPow = systemMath.member("Pow")
    val systemText = system.space("Text")
    val systemTextStringBuilder = systemText.type("StringBuilder")
    val systemThreadingTasks = system.space("Threading").space("Tasks")
    val systemThreadingTasksTask = systemThreadingTasks.type("Task")
    val systemThreadingTasksTaskCompletionSource = systemThreadingTasks.type("TaskCompletionSource")
    val systemType = system.type("Type")
    val systemTuple = system.type("Tuple")
    private val temper = listOf("TemperLang").toSpaceName()
    private val temperCore = temper.space("Core")
    val temperCoreCore = temperCore.type("Core")
    val temperCoreAsync = temperCore.type("Async")
    val temperCoreStringUtil = temperCore.type("StringUtil")
    val temperCoreCoreAdaptGenerator = temperCoreCore.member("AdaptGenerator")
    val temperCoreCoreBitGet = temperCoreCore.member("BitGet")
    val temperCoreCoreBitSet = temperCoreCore.member("BitSet")
    val temperCoreCoreBubble = temperCoreCore.member("Bubble")
    val temperCoreCoreCastToNonNull = temperCoreCore.member("CastToNonNull", extension = true)
    val temperCoreCoreCompare = temperCoreCore.member("Compare", extension = true)
    val temperCoreCoreDiv = temperCoreCore.member("Div", extension = true)
    val temperCoreCoreDivSafe = temperCoreCore.member("DivSafe", extension = true)
    val temperCoreCoreGarbage = temperCoreCore.member("Garbage", extension = true)
    val temperCoreCoreIgnore = temperCoreCore.member("Ignore", extension = true)
    val temperCoreCoreMod = temperCoreCore.member("Mod", extension = true)
    val temperCoreCoreModSafe = temperCoreCore.member("ModSafe", extension = true)
    val temperCoreCorePureVirtual = temperCoreCore.member("PureVirtual")
    val temperCoreCoreRemoveGet = temperCoreCore.member("RemoveGet", extension = true)
    val temperCoreCoreSplit = temperCoreCore.member("Split")
    val temperCoreCoreStringFromCodePoint = temperCoreCore.member("StringFromCodePoint")
    val temperCoreCoreStringFromCodePoints = temperCoreCore.member("StringFromCodePoints")
    val temperCoreCoreToInt = temperCoreCore.member("ToInt")
    val temperCoreCoreToInt64 = temperCoreCore.member("ToInt64")
    private val temperCoreFloat64 = temperCore.type("Float64")
    val temperCoreFloat64Compare = temperCoreFloat64.member("Compare", extension = true)
    val temperCoreFloat64ExpM1 = temperCoreFloat64.member("ExpM1", extension = true)
    val temperCoreFloat64Format = temperCoreFloat64.member("Format", extension = true)
    val temperCoreFloat64LogP1 = temperCoreFloat64.member("LogP1", extension = true)
    val temperCoreFloat64Near = temperCoreFloat64.member("Near", extension = true)
    val temperCoreFloat64Sign = temperCoreFloat64.member("Sign", extension = true)
    val temperCoreFloat64ToFloat64 = temperCoreFloat64.member("ToFloat64", extension = true)
    val temperCoreFloat64ToInt = temperCoreFloat64.member("ToInt", extension = true)
    val temperCoreFloat64ToInt64 = temperCoreFloat64.member("ToInt64", extension = true)
    val temperCoreIGenerator = temperCore.type("IGenerator")
    val temperCoreCoreGeneratorNext = temperCoreCore.member("GeneratorNext")
    val temperCoreGeneratorResult = temperCore.type("GeneratorResult")
    val temperCoreDoneResult = temperCore.type("DoneResult")
    val temperCoreValueResult = temperCore.type("ValueResult")
    val temperCoreCoreEmpty = temperCoreCore.member("Empty")
    val temperCoreILoggingConsole = temperCore.type("ILoggingConsole")
    private val temperCoreListed = temperCore.type("Listed")
    val temperCoreListedAdd = temperCoreListed.member("Add", extension = true)
    val temperCoreListedAddAll = temperCoreListed.member("AddAll", extension = true)
    val temperCoreListedAsReadOnly = temperCoreListed.member("AsReadOnly", extension = true)
    val temperCoreListedCreateReadOnlyList = temperCoreListed.member("CreateReadOnlyList", extension = true)
    val temperCoreListedForEach = temperCoreListed.member("ForEach", extension = true)
    val temperCoreListedGetOr = temperCoreListed.member("GetOr", extension = true)
    val temperCoreListedJoin = temperCoreListed.member("Join", extension = true)
    val temperCoreListedRemoveLast = temperCoreListed.member("RemoveLast", extension = true)
    val temperCoreListedReverse = temperCoreListed.member("Reverse", extension = true)
    val temperCoreListedSlice = temperCoreListed.member("Slice", extension = true)
    val temperCoreListedSort = temperCoreListed.member("Sort", extension = true)
    val temperCoreListedSorted = temperCoreListed.member("Sorted", extension = true)
    val temperCoreListedSplice = temperCoreListed.member("Splice", extension = true)
    val temperCoreListedToReadOnlyList = temperCoreListed.member("ToReadOnlyList", extension = true)
    private val temperCoreMapped = temperCore.type("Mapped")
    val temperCoreMappedAsReadOnly = temperCoreMapped.member("AsReadOnly", extension = true)
    val temperCoreMappedLength = temperCoreMapped.member("Count", extension = true)
    val temperCoreMappedGetOr = temperCoreMapped.member("GetOrDefault", extension = true)
    val temperCoreMappedHas = temperCoreMapped.member("ContainsKey", extension = true)
    val temperCoreMappedKeys = temperCoreMapped.member("Keys", extension = true)
    val temperCoreMappedValues = temperCoreMapped.member("Values", extension = true)
    val temperCoreMappedToMap = temperCoreMapped.member("ToMap", extension = true)
    val temperCoreMappedToMapBuilder = temperCoreMapped.member("ToMapBuilder", extension = true)
    val temperCoreMappedToList = temperCoreMapped.member("ToList", extension = true)
    val temperCoreMappedToListBuilder = temperCoreMapped.member("ToListWith", extension = true)
    val temperCoreMappedToListWith = temperCoreMapped.member("ToListWith", extension = true)
    val temperCoreMappedToListBuilderWith = temperCoreMapped.member("ToListBuilderWith", extension = true)
    val temperCoreMappedForEach = temperCoreMapped.member("ForEach", extension = true)
    val temperCoreMapConstructor = temperCoreMapped.member("ConstructMap", extension = true)
    val temperCoreOrderedDictionary = temperCore.type("OrderedDictionary")
    val temperCoreAsyncBreakPromise = temperCoreAsync.member("BreakPromise")
    val temperCoreAsyncCompletePromise = temperCoreAsync.member("CompletePromise")
    val temperCoreAsyncAwakeUpon = temperCoreAsync.member("AwakeUpon")
    val temperCoreOptional = temperCore.type("Optional")
    val temperCoreOptionalOrNull = temperCoreOptional.member("OrNull")
    val temperCoreOptionalToNullable = temperCoreOptional.member("ToNullable")
    val temperCoreStringUtilAppendBetween = temperCoreStringUtil.member("AppendBetween")
    val temperCoreStringUtilAppendCodePoint = temperCoreStringUtil.member("AppendCodePoint")
    val temperCoreStringUtilCountBetween = temperCoreStringUtil.member("CountBetween")
    val temperCoreStringUtilForEach = temperCoreStringUtil.member("ForEach")
    val temperCoreStringUtilGet = temperCoreStringUtil.member("Get")
    val temperCoreStringUtilHasAtLeast = temperCoreStringUtil.member("HasAtLeast")
    val temperCoreStringUtilHasIndex = temperCoreStringUtil.member("HasIndex")
    val temperCoreStringUtilNext = temperCoreStringUtil.member("Next")
    val temperCoreStringUtilPrev = temperCoreStringUtil.member("Prev")
    val temperCoreStringUtilRequireStringIndex = temperCoreStringUtil.member("RequireStringIndex")
    val temperCoreStringUtilRequireNoStringIndex = temperCoreStringUtil.member("RequireNoStringIndex")
    val temperCoreStringUtilSlice = temperCoreStringUtil.member("Slice")
    val temperCoreStringUtilStep = temperCoreStringUtil.member("Step")
    val temperCoreStringUtilCompareStringsByCodePoint = temperCoreStringUtil.member("CompareStringsByCodePoint")
    private val temperStd = temper.space("Std")
    private val temperStdRegex = temperStd.space("Regex")
    val temperStdRegexRegexSupport = temperStdRegex.type("RegexSupport")
    private val temperStdTemporal = temperStd.space("Temporal")
    private val temperStdTemporalTemporalSupport = temperStdTemporal.type("TemporalSupport")
    val temperStdTemporalTemporalSupportToday = temperStdTemporalTemporalSupport.member("Today")
    val temperStdTemporalTemporalSupportYearsBetween = temperStdTemporalTemporalSupport.member("YearsBetween")
    val temperStdTemporalTemporalSupportIsoWeekdayNum = temperStdTemporalTemporalSupport.member("IsoWeekdayNum")
    val temperStdTemporalTemporalSupportFromIsoString = temperStdTemporalTemporalSupport.member("FromIsoString")
    private val temperStdNet = temperStd.space("Net")
    val temperCoreNetINetResponse = temperStdNet.type("INetResponse")
    val temperCoreNetSupport = temperStdNet.type("NetSupport")
    val temperCoreNetCoreStdNetSend = temperCoreNetSupport.member("StdNetSend")

    val libraryForNamespace: Map<SpaceName, DashedIdentifier> = mapOf(
        temperStd to DashedIdentifier.temperStandardLibraryIdentifier,
    )
}

sealed interface AbstractTypeName : TargetLanguageTypeName {
    fun member(name: String, extension: Boolean = false) =
        MemberName(type = this, name = name.toOutName(), extension = extension)

    fun toTypeName(pos: Position): CSharp.UnboundTypeName
    fun toType(pos: Position): CSharp.UnboundType = CSharp.UnboundType(toTypeName(pos))
}

/** Type name based on a keyword. */
data class KeyTypeName(val name: OutName) : AbstractTypeName {
    // Using Identifier here is a bit abusive but works for now.
    override fun toTypeName(pos: Position) = name.toIdentifier(pos)

    override fun renderTo(tokenSink: TokenSink) {
        tokenSink.word(name.outputNameText)
    }
}

/** A static member inside a type that's inside a namespace. Might be either property or method. */
data class MemberName(
    val type: AbstractTypeName,
    val name: OutName,

    /** Is safe to use as an extension method. */
    val extension: Boolean = false,
) {
    fun toStaticMember(pos: Position): CSharp.PrimaryExpression {
        return CSharp.MemberAccess(
            pos,
            expr = type.toType(pos) as CSharp.PrimaryExpression, // Use our cheater equivalence.
            id = name.toIdentifier(pos),
            extension = extension,
        )
    }
}

/** A namespace but styled to fit with the naming convention of [MemberName] and [TypeName]. */
data class SpaceName(val names: List<OutName>) {
    fun space(name: String) = SpaceName(names = names + listOf(name.toOutName()))
    fun type(name: String) = TypeName(space = this, name = name.toOutName())
}

/**
 * A type inside a namespace indicated by a [SpaceName].
 *
 * No accommodation at present for nested types. However, given our conversion
 * of Temper classes to top level and the C# rule "CA1034: Nested types should
 * not be visible", so unlikely to be useful for library surface construction,
 * that might be ok.
 */
data class TypeName(val space: SpaceName, val name: OutName) : AbstractTypeName {
    val qualifiedName: QualifiedName get() = buildList {
        for (name in space.names) {
            add(name.outputNameText)
        }
        add(name.outputNameText)
    }

    override fun toTypeName(pos: Position): CSharp.QualTypeName {
        return CSharp.QualTypeName(
            pos,
            id = buildList {
                val leftPos = pos.leftEdge
                for (name in space.names) {
                    add(name.toIdentifier(leftPos))
                }
                add(name.toIdentifier(pos))
            },
        )
    }

    override fun renderTo(tokenSink: TokenSink) {
        qualifiedName.forEachIndexed { i, nameText ->
            if (i != 0) {
                tokenSink.emit(OutToks.dot)
            }
            tokenSink.emit(OutputToken(nameText, OutputTokenType.Name))
        }
    }
}

internal fun List<String>.toSpaceName(): SpaceName {
    return SpaceName(names = this.map { it.toOutName() })
}

private fun String.toKeyTypeName() = KeyTypeName(toOutName())

private fun String.toSpaceName() = listOf(this).toSpaceName()

val csharpKeywords = setOf(
    "abstract", "as", "base", "bool", "break", "byte", "case", "catch", "char",
    "checked", "class", "const", "continue", "decimal", "default", "delegate",
    "do", "double", "else", "enum", " event", "explicit", "extern", "false",
    "finally", "fixed", "float", "for", "foreach", "goto", "if", "implicit", "in",
    "int", "interface", "internal", "is", "lock", "long", " namespace", "new",
    "null", "object", "operator", "out", "override", "params", "private",
    "protected", "public", "readonly", "ref", "return", "sbyte", "sealed", "short",
    "sizeof", "stackalloc", " static", "string", "struct", "switch", "this",
    "throw", "true", "try", "typeof", "uint", "ulong", "unchecked", "unsafe",
    "ushort", "using", "virtual", "void", "volatile", "while",
)

val csharpContextualKeywords = setOf(
    "add", "and", "alias", "ascending", "args", "async", "await", "by",
    "descending", "dynamic", "equals", "file", "from", "get", "global", "group",
    "init", "into", "join", "let", "managed", "nameof", "nint", "not", "notnull",
    "nuint", "on", "or", "orderby", "partial", "partial", "record", "remove",
    "required", "scoped", "select", "set", "unmanaged", "unmanaged", "value",
    "var", "when", "where", "where", "with", "yield",
)

val csharpAllKeywords = (csharpKeywords + csharpContextualKeywords).sorted().toSet()
