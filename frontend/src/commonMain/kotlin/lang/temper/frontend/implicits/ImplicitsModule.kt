package lang.temper.frontend.implicits

import lang.temper.builtin.PureCallableValue
import lang.temper.common.AppendingTextOutput
import lang.temper.common.Console
import lang.temper.common.Log
import lang.temper.common.console
import lang.temper.env.Environment
import lang.temper.env.InterpMode
import lang.temper.frontend.Module
import lang.temper.frontend.ModuleSource
import lang.temper.fs.loadResource
import lang.temper.interp.builtinOnlyEnvironment
import lang.temper.interp.immutableEnvironment
import lang.temper.lexer.Genre
import lang.temper.lexer.StandaloneLanguageConfig
import lang.temper.log.FilePositions
import lang.temper.log.LogSink
import lang.temper.log.MessageTemplate
import lang.temper.log.MessageTemplateI
import lang.temper.log.Position
import lang.temper.log.excerpt
import lang.temper.log.filePath
import lang.temper.name.BuiltinName
import lang.temper.name.ImplicitsCodeLocation
import lang.temper.name.ModularName
import lang.temper.name.TemperName
import lang.temper.stage.Stage
import lang.temper.type.Abstractness
import lang.temper.type.NominalType
import lang.temper.type.TypeShape
import lang.temper.type.WellKnownTypes
import lang.temper.type.initializeBindingsFromImplicits
import lang.temper.type2.Signature2
import lang.temper.value.ActualValues
import lang.temper.value.InstancePropertyRecord
import lang.temper.value.InternalFeatureKeys
import lang.temper.value.InterpreterCallback
import lang.temper.value.NamedBuiltinFun
import lang.temper.value.NotYet
import lang.temper.value.PartialResult
import lang.temper.value.ReifiedType
import lang.temper.value.TClass
import lang.temper.value.TType
import lang.temper.value.Value
import lang.temper.value.toPseudoCode
import lang.temper.value.unpackPositionedOr

private const val DEBUG = false

/**
 * Provides common access to a module with core language definitions.
 */
object ImplicitsModule {

    private var singletonPositions: FilePositions? = null

    val implicitsFilePositions: FilePositions @Synchronized get() {
        val fp = singletonPositions
        if (fp != null) {
            return fp
        }
        init()
        return singletonPositions!!
    }

    private var codeSingleton: CharSequence? = null

    val code: CharSequence @Synchronized get() {
        val c = codeSingleton
        if (c != null) {
            return c
        }
        init()
        return codeSingleton!!
    }

    private var started = false

    fun init() = kotlin.runCatching {
        // Bootstrap allows nested calls on a single thread because allImplicitlyImportedNames, so prevent that.
        check(!started) { "Attempt to reinit implicits" }
        started = true

        // Really starting init now.
        val bufferedOutput = StringBuilder()
        val moduleConsole = if (DEBUG) {
            console
        } else {
            Console(
                AppendingTextOutput(bufferedOutput, isTtyLike = console.textOutput.isTtyLike),
            )
        }

        val content = loadResource(this, "implicits/Implicits.temper")

        codeSingleton = content

        singletonPositions = FilePositions.fromSource(ImplicitsCodeLocation, content)

        val neverStop = { true } // Continue condition
        val logSink = FailFastLogSink(content)
        val loc = ImplicitsCodeLocation
        val module = Module(
            projectLogSink = logSink,
            loc = loc,
            console = moduleConsole,
            continueCondition = neverStop,
            namingContext = WellKnownTypes.anyValueTypeDefinition.name.origin,
            mayRun = true, // TODO Remove this option once `console` is a stable value.
        )

        module.deliverContent(
            ModuleSource(
                filePath = filePath("implicits", "Implicits.temper"),
                fetchedContent = content,
                languageConfig = StandaloneLanguageConfig,
            ),
        )
        val endStage = Stage.Run // TODO Go back to Stage.GenerateCode once `console` is a stable value.
        stageLoop@
        while (module.canAdvance()) {
            val nextStage = module.nextStage!!
            if (nextStage > endStage) {
                break@stageLoop
            }
            logSink.log(
                level = Log.Info,
                template = MessageTemplate.StartingStage,
                pos = Position(loc, 0, 0),
                values = listOf(nextStage),
            )
            module.advance()
            if (DEBUG) {
                console.group("Implicits module at ${module.stageCompleted}") {
                    module.treeForDebug?.toPseudoCode(console.textOutput)
                }
            }
        }
        if (
            module.stageCompleted != endStage ||
            !module.ok || module.exports == null
        ) {
            if (bufferedOutput.isNotEmpty()) {
                console.textOutput.emitLineChunk(bufferedOutput)
            }
            val ok = module.ok
            val hasExports = !module.exports.isNullOrEmpty()
            throw ImplicitsUnavailableException(
                "Implicits module stalled at ${module.stageCompleted}, ok=$ok, hasExports=$hasExports",
            )
        }
        if (DEBUG) {
            console.group("Implicits module exports") {
                module.exports?.forEach {
                    console.log("- $it")
                }
            }
        }
        val bindingNamingContext = module.namingContext
        initializeBindingsFromImplicits(
            bindingNamingContext.topLevelBindingNames.mapNotNull { name ->
                bindingNamingContext.getTopLevelBinding(name)?.let { name to it }
            }.toMap(),
        )
        singletonModule = module
    }.onFailure { exception ->
        exception.printStackTrace()
        throw exception
    }

    private var singletonModule: Module? = null

    val module: Module
        @Synchronized
        get() {
            val m = singletonModule
            if (m != null) {
                return m
            }
            init()
            return singletonModule!!
        }

    /**
     * The class type and closed over environment for a class that provides methods for a builtin
     * type.
     */
    internal fun promoteSimpleValueToClassType(
        value: Value<*>,
    ): Value<InstancePropertyRecord>? {
        val typeTag = value.typeTag
        val typeName = typeTag.name.builtinKey
        val exportedValue = module.exports?.firstOrNull {
            it.name.baseName.nameText == typeName
        }?.value
        if (exportedValue != null) {
            val exportedType = TType.unpackOrNull(exportedValue)
            if (exportedType is ReifiedType) {
                val t = (exportedType.type as? NominalType)?.definition as? TypeShape
                if (t != null) {
                    // The wrapper types must have a single backed property.
                    val backedProperty =
                        t.properties.firstOrNull { it.abstractness == Abstractness.Concrete }
                    if (backedProperty != null) {
                        val wrappingObject = InstancePropertyRecord(
                            mutableMapOf(
                                backedProperty.name as ModularName to value,
                            ),
                        )
                        return Value(wrappingObject, TClass(t))
                    }
                }
            }
        }
        return null
    }
}

private class FailFastLogSink(private val code: CharSequence) : LogSink {
    private var stage: Stage? = null

    private val stagePrefixString get() = when (val s = stage) { null -> "" else -> "$s: " }

    override fun log(
        level: Log.Level,
        template: MessageTemplateI,
        pos: Position,
        values: List<Any>,
        fyi: Boolean,
    ) {
        if (template == MessageTemplate.StartingStage) {
            (values.getOrNull(0) as? Stage)?.let { this.stage = it }
        }
        if (level >= Log.Warn) {
            val posInfo = ImplicitsModule.implicitsFilePositions
            val posStr = posInfo.filePositionAtOffset(pos.left)
            val messageStr = "$stagePrefixString$posStr: ${template.format(values)}"

            console.log(messageStr, level)
            if (pos.loc == ImplicitsCodeLocation) {
                excerpt(pos, code, console.textOutput)
            }
            check(level < Log.Error) { "Error boot-strapping implicits.  $messageStr" }
        }
    }

    override val hasFatal: Boolean
        get() = false
}

internal class ImplicitsUnavailableException(message: String) : RuntimeException(message)

private val allImplicitlyImportedNamesLazy = lazy {
    // If this throws because `module` is bootstrapping, lazy will try again later.
    (ImplicitsModule.module.exports ?: emptyList()).associate { export ->
        BuiltinName(export.name.baseName.nameText) as TemperName to export.value!!
    }
}

/**
 * Not-super-efficient accessor for the list of names implicitly exported
 */
val allImplicitlyImportedNames: Map<TemperName, Value<*>>
    get() = try {
        allImplicitlyImportedNamesLazy.value
    } catch (_: IllegalStateException) {
        // This can happen while bootstrapping.
        emptyMap()
    }

/** An environment that includes all effective builtins, including implicits. */
fun builtinEnvironment(
    parent: Environment,
    genre: Genre,
    skipImplicits: Boolean = false,
): Environment {
    val implicitsBindings = if (skipImplicits) emptyMap() else allImplicitlyImportedNames
    val implicitsEnvironment = immutableEnvironment(parent, implicitsBindings, isLongLived = true)
    return builtinOnlyEnvironment(implicitsEnvironment, genre = genre)
}

internal object PromoteSimpleFn : NamedBuiltinFun, PureCallableValue {
    override val name: String = InternalFeatureKeys.PromoteSimpleValueToClassInstance.name
    override val sigs: List<Signature2>? = null

    override fun invoke(args: ActualValues, cb: InterpreterCallback, interpMode: InterpMode): PartialResult {
        val (arg) = args.unpackPositionedOr(1, cb) { return@invoke it }
        return ImplicitsModule.promoteSimpleValueToClassType(arg) ?: NotYet
    }
}
