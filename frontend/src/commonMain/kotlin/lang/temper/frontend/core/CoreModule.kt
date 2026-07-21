package lang.temper.frontend.core

import lang.temper.common.AppendingTextOutput
import lang.temper.common.Console
import lang.temper.common.Log
import lang.temper.common.console
import lang.temper.env.Environment
import lang.temper.frontend.Module
import lang.temper.frontend.ModuleSource
import lang.temper.fs.loadResource
import lang.temper.interp.ContinueCondition
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
import lang.temper.name.CoreCodeLocation
import lang.temper.name.TemperName
import lang.temper.stage.Stage
import lang.temper.type.WellKnownTypes
import lang.temper.type.initializeBindingsFromImplicits
import lang.temper.value.Value
import lang.temper.value.toPseudoCode

private const val DEBUG = false

/**
 * Provides common access to a module with core language definitions.
 */
object CoreModule {

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

        val content = loadResource(this, "core/core.temper")

        codeSingleton = content

        singletonPositions = FilePositions.fromSource(CoreCodeLocation, content)

        val logSink = FailFastLogSink(content)
        val loc = CoreCodeLocation
        val module = Module(
            projectLogSink = logSink,
            loc = loc,
            console = moduleConsole,
            continueCondition = NeverStop,
            namingContext = WellKnownTypes.anyValueTypeDefinition.name.origin,
        )

        module.deliverContent(
            ModuleSource(
                filePath = filePath("implicits", "core.temper"),
                fetchedContent = content,
                languageConfig = StandaloneLanguageConfig,
            ),
        )
        val endStage = Stage.Run
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
                console.group("Core module at ${module.stageCompleted}") {
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
                "Core module stalled at ${module.stageCompleted}, ok=$ok, hasExports=$hasExports",
            )
        }
        if (DEBUG) {
            console.group("Core module exports") {
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
            val posInfo = CoreModule.implicitsFilePositions
            val posStr = posInfo.filePositionAtOffset(pos.left)
            val messageStr = "$stagePrefixString$posStr: ${template.format(values)}"

            console.log(messageStr, level)
            if (pos.loc == CoreCodeLocation) {
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
    (CoreModule.module.exports ?: emptyList()).associate { export ->
        BuiltinName(export.name.baseName.nameText) as TemperName to export.valueFromRun!!
    }
}

/**
 * Not-superefficient accessor for the list of names implicitly exported
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

private object NeverStop : ContinueCondition {
    override fun shouldContinue(): Boolean = true // Go off, you
}
