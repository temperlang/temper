package lang.temper.frontend.implicits

import lang.temper.builtin.BuiltinFuns
import lang.temper.env.InterpMode
import lang.temper.frontend.Module
import lang.temper.fs.FileClassification
import lang.temper.fs.FileSystem
import lang.temper.fs.StitchedFileSystem
import lang.temper.interp.connectedDecoratorBindings
import lang.temper.interp.importExport.STANDARD_LIBRARY_NAME
import lang.temper.lexer.TEMPER_FILE_EXTENSION
import lang.temper.library.LibraryConfiguration
import lang.temper.log.FilePath
import lang.temper.log.MessageTemplate
import lang.temper.log.dirPath
import lang.temper.log.resolveDir
import lang.temper.log.resolveFile
import lang.temper.name.SourceName
import lang.temper.name.TemperName
import lang.temper.regex.RegexCompileFormattedFn
import lang.temper.regex.RegexCompiledFindFn
import lang.temper.regex.RegexCompiledFoundFn
import lang.temper.regex.RegexCompiledReplaceFn
import lang.temper.regex.RegexCompiledSplitFn
import lang.temper.regex.RegexFormatFn
import lang.temper.stage.Stage
import lang.temper.type2.Signature2
import lang.temper.value.ActualValues
import lang.temper.value.CallableValue
import lang.temper.value.DynamicMessage
import lang.temper.value.Fail
import lang.temper.value.InstancePropertyMap
import lang.temper.value.InstancePropertyRecord
import lang.temper.value.InterpreterCallback
import lang.temper.value.NamedBuiltinFun
import lang.temper.value.PartialResult
import lang.temper.value.Resolutions
import lang.temper.value.Stayless
import lang.temper.value.TClass
import lang.temper.value.TFloat64
import lang.temper.value.TInt
import lang.temper.value.TInt64
import lang.temper.value.TNull
import lang.temper.value.TString
import lang.temper.value.TypeTag
import lang.temper.value.Value
import lang.temper.value.unify
import lang.temper.value.unpackOrFail
import lang.temper.value.void

/** Returns the std file system, if available, wrapped under a subdir prefix. */
fun accessStdWrapped(): FileSystem? {
    return accessStd()?.let {
        StitchedFileSystem(
            mapOf(dirPath(STANDARD_LIBRARY_NAME) to it),
        )
    }
}

/**
 * Implicits and std modules get extra privileges, like the privilege to connect to JVM machinery for implicits.
 * Call once before each advance to get the environment updated for privileged processing.
 */
internal fun considerPrivilegedEnvironmentBindings(module: Module) {
    when (module.nextStage) {
        Stage.Lex -> if (module.isEffectivelyImplicits || module.isEffectivelyStd) {
            module.addEnvironmentBindings(connectedDecoratorBindings)
        }
        else -> {}
    }
}

/**
 * Finds any existing directory or file first. If missing, finds "whatever.temper" or
 * "whatever.temper.md" when given nonexistent "whatever", if either exists, checked
 * in that order. Also for directories, if no "config.temper.md" is present but a
 * subdirectory called "src" is present, provide that dir instead.
 *
 * Although motivated by standard library usage, the feature can be applied generally.
 */
fun findMatchingTemperFile(filePath: FilePath, fileSystem: FileSystem): FilePath {
    return when (fileSystem.classify(filePath)) {
        FileClassification.Directory -> {
            // Ensure dir indicator for result and before resolving deeper.
            val dir = FilePath(filePath.segments, isDir = true)
            when (fileSystem.classify(dir.resolveFile(LibraryConfiguration.fileName.fullName))) {
                FileClassification.DoesNotExist -> {
                    val src = dir.resolveDir("src")
                    when (fileSystem.classify(src)) {
                        FileClassification.Directory -> src
                        else -> dir
                    }
                }
                else -> dir
            }
        }
        FileClassification.File -> filePath
        else -> when {
            filePath.lastOrNull()!!.fullName.indexOf('.') >= 0 -> filePath
            else -> {
                for (extension in temperExtensions) {
                    val extended = filePath.withExtension(extension)!!
                    if (fileSystem.isFile(extended)) {
                        return extended
                    }
                }
                filePath
            }
        }
    }
}

/** The signature should be provided by the Temper source declaration. */
abstract class SigFn(sig: Signature2) : CallableValue, NamedBuiltinFun, Stayless {
    override val sigs = listOf(sig)
}

/** Makes it easy to provide an actual value with a name and an implementation. */
abstract class SigFnBuilder(val name: String, val impure: Boolean = false) {
    fun fn(sig: Signature2) = Value(
        object : SigFn(sig) {
            override val name: String get() = this@SigFnBuilder.name
            override val isPure: Boolean get() = !impure
            override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
                checkArityOr(args, cb) { return@invoke it }
                val unified = unify(DynamicMessage(args, interpMode), sigs[0], Resolutions(cb))
                val reorderedArgs = unified?.namedArguments(cb)?.let { reorderedValues ->
                    // Requires expectation of the void convention and loss of all arg names.
                    ActualValues.from(reorderedValues.map { arg -> arg.initialValue ?: void })
                } ?: args
                return this@SigFnBuilder.invoke(reorderedArgs, cb, interpMode)
            }
        },
    )

    abstract fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult
}

inline fun <V : Any> (TypeTag<V>).unpackWithNullDefault(
    args: ActualValues,
    index: Int,
    defaultValue: V,
    cb: InterpreterCallback,
    interpMode: InterpMode,
    onWrongResult: (PartialResult) -> Nothing,
): V = when (index < args.size && args[index] == TNull.value) {
    true -> defaultValue
    false -> unpackOrFail(args, index, cb, interpMode, onWrongResult)
}

fun builtinLibraryConnecteds() = listOf(
    ConsoleFns.GlobalLog,
    DequeFns.Add,
    DequeFns.Constructor,
    DequeFns.IsEmpty,
    DequeFns.RemoveFirst,
    FloatFns.Abs,
    FloatFns.Acos,
    FloatFns.Asin,
    FloatFns.Atan,
    FloatFns.Atan2,
    FloatFns.Ceil,
    FloatFns.Cos,
    FloatFns.Cosh,
    FloatFns.Exp,
    FloatFns.Expm1,
    FloatFns.Floor,
    FloatFns.Log,
    FloatFns.Log10,
    FloatFns.Log1p,
    FloatFns.Max,
    FloatFns.Min,
    FloatFns.Round,
    FloatFns.Sign,
    FloatFns.Sin,
    FloatFns.Sinh,
    FloatFns.Sqrt,
    FloatFns.Tan,
    FloatFns.Tanh,
    FloatFns.ToInt,
    FloatFns.ToIntUnsafe,
    FloatFns.ToInt64,
    FloatFns.ToInt64Unsafe,
    FloatFns.ToString,
    IntFns.ToFloat64,
    IntFns.ToInt64,
    IntFns.ToString,
    Int64Fns.ToFloat64,
    Int64Fns.ToFloat64Unsafe,
    Int64Fns.ToInt32,
    Int64Fns.ToInt32Unsafe,
    Int64Fns.ToString,
    ListFns.Filter,
    ListFns.Get,
    ListFns.GetOr,
    ListFns.Join,
    ListFns.Length,
    ListFns.Map,
    ListFns.MapDropping,
    ListFns.Slice,
    ListFns.Sorted,
    ListBuilderFns.Add,
    ListBuilderFns.AddAll,
    ListBuilderFns.Clear,
    ListBuilderFns.Constructor,
    ListBuilderFns.RemoveLast,
    ListBuilderFns.Reverse,
    ListBuilderFns.Set,
    ListBuilderFns.Sort,
    ListBuilderFns.Splice,
    ListBuilderFns.ToList,
    MapFns.Constructor,
    MapBuilderFns.Clear,
    MapBuilderFns.Constructor,
    MapBuilderFns.Remove,
    MapBuilderFns.Set,
    MappedFns.Get,
    MappedFns.GetOr,
    MappedFns.Has,
    MappedFns.Keys,
    MappedFns.Values,
    MappedFns.ToMap,
    MappedFns.ToMapBuilder,
    MappedFns.ToListWith,
    MappedFns.ToListBuilderWith,
    MappedFns.ForEach,
    PromiseBuilderFns.BreakPromise,
    PromiseBuilderFns.Complete,
    PromiseBuilderFns.Constructor,
    StringFns.CountBetween,
    StringFns.FromCodePoint,
    StringFns.FromCodePoints,
    StringFns.Get,
    StringFns.GetBegin,
    StringFns.GetEnd,
    StringFns.HasAtLeast,
    StringFns.HasIndex,
    StringFns.Next,
    StringFns.Prev,
    StringFns.Split,
    StringFns.Slice,
    StringFns.ToFloat64,
    StringFns.ToInt,
    StringFns.ToInt64,
    StringFns.GetNone,
    StringFns.StringIndexOptionCompareTo,
).associate<SigFnBuilder, String, (Signature2) -> Value<*>> { it.name to { sig -> it.fn(sig) } }

fun standardLibraryConnecteds() = builtinLibraryConnecteds() + mapOf(
    // Builtin aliases
    "Listed::filter" to { sig -> ListFns.Filter.fn(sig) },
    "Listed::get" to { sig -> ListFns.Get.fn(sig) },
    "Listed::getOr" to { sig -> ListFns.GetOr.fn(sig) },
    "Listed::join" to { sig -> ListFns.Join.fn(sig) },
    "Listed::length" to { sig -> ListFns.Length.fn(sig) },
    "Listed::map" to { sig -> ListFns.Map.fn(sig) },
    "Listed::mapDropping" to { sig -> ListFns.MapDropping.fn(sig) },
    "Listed::slice" to { sig -> ListFns.Slice.fn(sig) },
    "ListBuilder::filter" to { sig -> ListFns.Filter.fn(sig) },
    "ListBuilder::get" to { sig -> ListFns.Get.fn(sig) },
    "ListBuilder::getOr" to { sig -> ListFns.GetOr.fn(sig) },
    "ListBuilder::join" to { sig -> ListFns.Join.fn(sig) },
    "ListBuilder::length" to { sig -> ListFns.Length.fn(sig) },
    "ListBuilder::map" to { sig -> ListFns.Map.fn(sig) },
    "ListBuilder::mapDropping" to { sig -> ListFns.MapDropping.fn(sig) },
    "ListBuilder::slice" to { sig -> ListFns.Slice.fn(sig) },
    // Other std
    "Regex::compileFormatted" to { sig -> Value(RegexCompileFormattedFn(sig)) },
    "Regex::compiledFind" to { sig -> Value(RegexCompiledFindFn(sig)) },
    "Regex::compiledFound" to { sig -> Value(RegexCompiledFoundFn(sig)) },
    "Regex::compiledReplace" to { sig -> Value(RegexCompiledReplaceFn(sig)) },
    "Regex::compiledSplit" to { sig -> Value(RegexCompiledSplitFn(sig)) },
    "Regex::format" to { sig -> Value(RegexFormatFn(sig)) },
    "Date::today" to { sig -> Value(DateTodayFn(sig)) },
)

const val TEMPER_MD_FILE_EXTENSION = "$TEMPER_FILE_EXTENSION.md"
private val temperExtensions = listOf(TEMPER_FILE_EXTENSION, TEMPER_MD_FILE_EXTENSION)

internal interface ConsoleFns {
    object GlobalLog : SigFnBuilder("GlobalConsole::globalLog") {
        override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
            return (BuiltinFuns.print as CallableValue).invoke(ActualValues.from(args[1]), cb, interpMode)
        }
    }
}

internal interface IntFns {
    object ToFloat64 : SigFnBuilder("Int32::toFloat64") {
        override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
            return Value(TInt.unpackContent(args[0]).toDouble(), TFloat64)
        }
    }

    object ToInt64 : SigFnBuilder("Int32::toInt64") {
        override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
            return Value(TInt.unpackContent(args[0]).toLong(), TInt64)
        }
    }

    object ToString : SigFnBuilder("Int32::toString") {
        override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
            @Suppress("MagicNumber")
            val radix = TInt.unpackWithNullDefault(args, 1, 10, cb, interpMode) { return@invoke it }
            radix in MIN_INT_RADIX..MAX_INT_RADIX || return Fail
            return Value(TInt.unpackContent(args[0]).toString(radix = radix), TString)
        }
    }
}

internal interface Int64Fns {
    object ToFloat64 : SigFnBuilder("Int64::toFloat64") {
        override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
            val long = TInt64.unpackContent(args[0])
            val double = long.toDouble()
            return when {
                long in -MANTISSA64_LIMIT..MANTISSA64_LIMIT -> Value(double, TFloat64)
                else -> Fail
            }
        }
    }

    object ToFloat64Unsafe : SigFnBuilder("Int64::toFloat64Unsafe") {
        override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
            return Value(TInt64.unpackContent(args[0]).toDouble(), TFloat64)
        }
    }

    object ToInt32 : SigFnBuilder("Int64::toInt32") {
        override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
            val long = TInt64.unpackContent(args[0])
            val int = long.toInt()
            return when {
                int.toLong() == long -> Value(int, TInt)
                else -> Fail
            }
        }
    }

    object ToInt32Unsafe : SigFnBuilder("Int64::toInt32Unsafe") {
        override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
            return Value(TInt64.unpackContent(args[0]).toInt(), TInt)
        }
    }

    object ToString : SigFnBuilder("Int64::toString") {
        override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
            @Suppress("MagicNumber")
            val radix = TInt.unpackWithNullDefault(args, 1, 10, cb, interpMode) { return@invoke it }
            radix in MIN_INT_RADIX..MAX_INT_RADIX || return Fail
            return Value(TInt64.unpackContent(args[0]).toString(radix = radix), TString)
        }
    }
}

private inline fun CallableValue.checkArityOr(args: ActualValues, cb: InterpreterCallback, onFail: (Fail) -> Nothing) {
    val arityRange: IntRange = sigs!![0].arityRange
    val good = args.size in arityRange
    if (!good) {
        onFail(cb.fail(MessageTemplate.ArityMismatch, values = listOf(arityRange)))
    }
}

internal fun <V : Any> TypeTag<V>.unpackContent(value: Value<*>): V {
    if (value.stateVector !is InstancePropertyRecord) {
        // Presume it's already unwrapped content, so go for the gold.
        return unpack(value)
    }
    val properties = value.properties()
    for ((name, propValue) in properties) {
        when (base(name)) {
            "content" -> return unpack(propValue)
            else -> unexpectedName(name)
        }
    }
    error("No content")
}

/**
 * The minimum valid value for `Int.toString(radix)`.  With fewer than 2 numerals, string
 * representations cannot be non-empty (using "0" for 0) allowing systems to distinguish between
 * the absence of an integer input (empty string) and an input.
 */
const val MIN_INT_RADIX: Int = 2

/**
 * The maximum valid value for `Int.toString(radix)`.  With more than 36, string
 * encoders run out of case-insensitive ASCII alphanumeric numerals.
 */
const val MAX_INT_RADIX: Int = 36

// 52b of significand imply 15 decimal digits of precision since 10**15 < 2**52 < 10**16
// But everyone else gets 16 digits, so keep 16 here, too.
const val F64_DECIMAL_DIGITS = 16

internal fun Value<*>.properties(): InstancePropertyMap = unpackInstance().properties

internal fun Value<*>.unpackInstance() = (typeTag as TClass).unpack(this)

internal fun base(name: TemperName) = when (name) {
    is SourceName -> name.baseName.nameText
    else -> unexpectedName(name)
}

internal fun unexpectedName(name: TemperName): Nothing = error("Unexpected name: $name")
