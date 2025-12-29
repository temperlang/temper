package lang.temper.cli.repl

import lang.temper.common.Console
import lang.temper.env.InterpMode
import lang.temper.frontend.StagingFlags
import lang.temper.log.LogSink
import lang.temper.log.MessageTemplate
import lang.temper.log.logConfigurationsByName
import lang.temper.name.BackendId
import lang.temper.name.BuiltinName
import lang.temper.name.TemperName
import lang.temper.supportedBackends.lookupFactory
import lang.temper.supportedBackends.supportedBackends
import lang.temper.type2.Signature2
import lang.temper.value.ActualValues
import lang.temper.value.CallableValue
import lang.temper.value.Helpful
import lang.temper.value.HelpfullyNamed
import lang.temper.value.InterpreterCallback
import lang.temper.value.NamedBuiltinFun
import lang.temper.value.OccasionallyHelpful
import lang.temper.value.PartialResult
import lang.temper.value.Promises
import lang.temper.value.TNull
import lang.temper.value.TString
import lang.temper.value.Value
import lang.temper.value.void
import lang.temper.value.warnAboutUnresolved
import lang.temper.type.WellKnownTypes as WKT

internal class ReplHelpFn(
    private val repl: Repl,
) : NamedBuiltinFun, CallableValue, HelpfullyNamed {
    override val sigs: List<Signature2>
        get() = listOf(
            Signature2(
                returnType2 = WKT.voidType2,
                hasThisFormal = false,
                requiredInputTypes = listOf(),
                optionalInputTypes = listOf(WKT.anyValueOrNullType2),
            ),
        )
    override val name get() = NAME

    private val topicsLazy = lazy {
        Topics(
            repl.extraBindings.filter {
                it.key !in StagingFlags.allFlags()
            },
            supportedBackends,
            repl.promises,
        )
    }
    private val topics get() = topicsLazy.value
    val topicKeys: Iterable<String> get() = topics.stringKeyToHelpful.value.keys

    override fun invoke(
        args: ActualValues,
        cb: InterpreterCallback,
        interpMode: InterpMode,
    ): PartialResult {
        val console = repl.console

        val (topicName: String, target: OccasionallyHelpful?) = when {
            args.size == 0 -> topics.lookup(name)!!
            args[0].typeTag == TNull -> topics.lookup(name)!!
            args[0].typeTag == TString -> {
                val topicName = TString.unpack(args[0])
                topics.lookup(topicName) ?: (topicName to null)
            }
            else -> {
                val arg = args[0]
                "$arg" to Helpful.wrap(arg)
            }
        }

        if (target is ConsoleHelpTopic) {
            console.group(topicName) {
                target.displayHelp(console, repl.logSink)
            }
            return void
        }

        val helpful = target?.prettyPleaseHelp()
        if (helpful != null) {
            console.group(topicName) {
                console.textOutput.emitLine(helpful.longHelp())
            }
            return void
        }

        return cb.fail(MessageTemplate.UserMessage, cb.pos, listOf("No topic $topicName found; try help()"))
    }

    override fun helpfulTopicName(): String = NAME

    override fun briefHelp(): String = "this help topic"
    override fun longHelp(): String = buildString {
        appendLine(
            "The help command takes a single argument that is a" +
                " topic string or a function or type with a doc string. The topics are:",
        )
        for ((topic, helpObj) in topics.stringKeyToHelpful.value) {
            this.appendLine("    $topic: ${helpObj.briefHelp()}")
        }
    }

    companion object {
        const val NAME = "help"
    }
}

private class Topics(
    private val bindings: Map<TemperName, Value<*>>,
    private val backendIds: Iterable<BackendId>,
    private val promises: Promises,
) {

    val stringKeyToHelpful: Lazy<Map<String, Helpful>> = lazy {
        buildMap {
            for ((n, v) in bindings.entries) {
                put(
                    "$n",
                    when (val sv = v.stateVector) {
                        is Helpful -> sv
                        else -> Helpful.generic(v)
                    },
                )
            }
            for (topic in extraHelpTopics) {
                put(topic.helpfulTopicName(), topic)
            }
            put(UnresolvedPromisesHelp.NAME, UnresolvedPromisesHelp(promises))
            for (backendId in backendIds) {
                val backend = lookupFactory(backendId) ?: continue
                val helpTopics = backend.extraHelpTopics
                for ((key, help) in helpTopics) {
                    val helpful = help.prettyPleaseHelp() ?: continue
                    put("$backendId/$key", helpful)
                }
            }
        }
    }

    fun lookup(name: String): Pair<String, Helpful>? {
        bindings[BuiltinName(name)]?.let { v ->
            return@lookup name to Helpful.wrap(v)
        }

        // Try a case-insensitive search
        for ((n, v) in stringKeyToHelpful.value) {
            if (name.equals(n, ignoreCase = true)) {
                return n to v
            }
        }

        return null
    }
}

internal object LoggingStepNamesHelp : HelpfullyNamed {
    override fun helpfulTopicName() = NAME

    override fun briefHelp(): String = "List of logging configuration names"

    override fun longHelp(): String = buildString {
        appendLine("These are names of steps in the compilation pipeline where extra logging may be dumped.")
        appendLine("These may also be used with the REPL ${ReplDescribeFn.NAME} function to describe the")
        appendLine("intermediate representation at that step.")
        appendLine()
        logConfigurationsByName.keys.sorted().forEach {
            if (it != "*") { // * is a wildcard, not an actual step.
                appendLine("- $it")
            }
        }
    }

    const val NAME = "loggingConfigNames"
}

/** Allows for colorful output */
interface ConsoleHelpTopic {
    fun displayHelp(console: Console, logSink: LogSink)
}

internal class UnresolvedPromisesHelp(
    private val promises: Promises,
) : HelpfullyNamed, ConsoleHelpTopic {
    companion object {
        const val NAME = "unresolved"
    }

    override fun helpfulTopicName(): String = NAME

    override fun longHelp(): String = buildString {
        val n = promises.numUnresolved
        append(n)
        append(" unresolved promise")
        if (n != 1) { append('s') }
    }

    override fun briefHelp(): String = "Info about currently unresolved promises"

    override fun displayHelp(console: Console, logSink: LogSink) {
        if (promises.numUnresolved == 0) {
            console.log("There are no unresolved promises.")
        } else {
            promises.warnAboutUnresolved(logSink)
        }
    }
}

private val extraHelpTopics = listOf<HelpfullyNamed>(
    LoggingStepNamesHelp,
)
