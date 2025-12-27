@file:JvmName("Cli") // The Gradle application plugin expects this name.

package lang.temper.cli

import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.ExperimentalCli
import kotlinx.cli.SingleNullableOption
import kotlinx.cli.Subcommand
import kotlinx.cli.default
import kotlinx.cli.multiple
import kotlinx.cli.optional
import kotlinx.cli.required
import kotlinx.coroutines.DelicateCoroutinesApi
import lang.temper.be.BackendHelpTopicKeys
import lang.temper.be.cli.EXIT_SUCCESS
import lang.temper.be.cli.ExecInteractiveRepl
import lang.temper.be.cli.RunLibraryRequest
import lang.temper.be.cli.RunTestsRequest
import lang.temper.be.cli.ShellPreferences
import lang.temper.be.util.ConfigFromCli
import lang.temper.be.util.parseConfigFromCli
import lang.temper.cli.repl.Repl
import lang.temper.cli.repl.ReplConfig
import lang.temper.cli.repl.ReplInterop
import lang.temper.cli.repl.ReplPrompt
import lang.temper.cli.repl.ReplSeparator
import lang.temper.cli.repl.configExpandHistory
import lang.temper.cli.repl.configTemperHistory
import lang.temper.common.Console
import lang.temper.common.Log
import lang.temper.common.RSuccess
import lang.temper.common.console
import lang.temper.common.currents.CompletableRFuture
import lang.temper.common.currents.ExecutorService
import lang.temper.common.currents.UnmanagedFuture
import lang.temper.common.isatty
import lang.temper.common.temperEscaper
import lang.temper.frontend.staging.ModuleConfig
import lang.temper.fs.TEMPER_OUT_NAME
import lang.temper.fs.getDirectories
import lang.temper.langserver.TokenMode
import lang.temper.langserver.doServe
import lang.temper.library.LibraryConfiguration
import lang.temper.name.BackendId
import lang.temper.name.DashedIdentifier
import lang.temper.name.interpBackendId
import lang.temper.stage.Stage
import lang.temper.supportedBackends.defaultSupportedBackendList
import lang.temper.supportedBackends.isSupported
import lang.temper.supportedBackends.lookupFactory
import lang.temper.supportedBackends.supportedBackends
import lang.temper.tooling.buildrun.Build
import lang.temper.tooling.buildrun.BuildDoneResult
import lang.temper.tooling.buildrun.BuildInitFailed
import lang.temper.tooling.buildrun.BuildNotNeededResult
import lang.temper.tooling.buildrun.RunTask
import lang.temper.tooling.buildrun.doBuild
import lang.temper.tooling.buildrun.doOneBuild
import lang.temper.tooling.buildrun.prepareBuild
import org.jline.reader.EndOfFileException
import org.jline.reader.LineReaderBuilder
import org.jline.reader.UserInterruptException
import org.jline.terminal.TerminalBuilder
import org.jline.widget.AutosuggestionWidgets
import sun.misc.Signal
import sun.misc.SignalHandler
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Path
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.ForkJoinPool

/**
 * What the command line user types to execute the tool.
 *
 * This must match cli/build.gradle's `application.applicationName=...`
 */
const val COMMAND_LINE_TOOL_NAME = "temper"

@ExperimentalCli
abstract class Main {
    abstract fun exitProcess(exitCode: Int): Nothing
    private fun exitProcess(ok: Boolean): Nothing {
        exitProcess(if (ok) EXIT_SUCCESS else GENERIC_ERROR_EXIT_CODE)
    }

    private val fs: FileSystem = FileSystems.getDefault()
    protected open val cwd: Path by lazyIo("Cannot determine working directory") {
        fs.getPath("").toAbsolutePath()
    }

    fun run(argv: Array<String>) {
        val configFileName = LibraryConfiguration.fileName.fullName

        fun backendsOptBasics(
            argParser: ArgParser,
            allowInterpreter: Boolean,
            default: List<BackendId>?,
            fullName: String = "backend",
            shortName: String = "b",
            usageContext: String = "",
        ) = run {
            val possibleValues = buildList {
                addAll(supportedBackends)
                if (allowInterpreter) { add(interpBackendId) }
            }
            val defaultMessage = "\n$HELP_INDENT " + when (default) {
                null -> "Consider using backend js if unsure."
                else -> "Defaults to $default if none specified."
            }
            argParser.option(
                type = ArgType.Choice(
                    possibleValues,
                    toVariant = {
                        BackendId(it).let { bid ->
                            require(bid in possibleValues) { "Not a supported backend" }
                            bid
                        }
                    },
                    variantToString = { it.uniqueId },
                ),
                fullName = fullName,
                shortName = shortName,
                // Slice off the end of the last indent, because the auto text adds its own space.
                description = """
                    |Which backend(s) to use${usageContext}.  May be specified multiple times.${defaultMessage}
                    |$HELP_INDENT
                """.trimMargin(),
            ).multiple()
        }

        fun backendsOptDefaulting(
            argParser: ArgParser,
        ): LazyOpt<List<BackendId>, List<BackendId>> {
            val default = defaultSupportedBackendList
            return backendsOptBasics(argParser, allowInterpreter = false, default = default).lazilyMapValue {
                it.ifEmpty { default }
            }
        }

        fun backendsOptRequired(
            argParser: ArgParser,
            allowInterpreter: Boolean,
        ) = backendsOptBasics(argParser, allowInterpreter = allowInterpreter, default = null).required()

        fun backendsOptWatchTest(
            argParser: ArgParser,
        ): LazyOpt<List<BackendId>, List<BackendId>> {
            val default = emptyList<BackendId>()
            return backendsOptBasics(
                argParser = argParser,
                allowInterpreter = true, // Can test in interpreter
                default = default,
                fullName = "test-backend",
                shortName = "t",
                usageContext = " for testing",
            ).lazilyMapValue {
                it.ifEmpty { default }
            }
        }

        // Like backendsOpt but only allows one backend.
        fun backendOpt(
            argParser: ArgParser,
        ): SingleNullableOption<BackendId> {
            return argParser.option(
                type = BackendArgType,
                fullName = "backend",
                shortName = "b",
                description = "Which backend to use",
            )
        }

        fun backendPlusReplConfigOpt(
            argParser: ArgParser,
            description: String,
        ): SingleNullableOption<BackendPlusReplConfig> {
            return argParser.option(
                type = BackendPlusReplConfigArgType,
                fullName = "backend",
                shortName = "b",
                description = description,
            )
        }

        fun workRootOpt(argParser: ArgParser) = argParser.option(
            type = PathArgType(fs, wantAbsolute = true),
            fullName = "workroot",
            shortName = "w",
            description = """
                    |
                    |$HELP_INDENT Root directory to scan for Temper libraries.
                    |
                    |$HELP_INDENT Each `$configFileName` file under the work root
                    |$HELP_INDENT establishes a Temper library.
                    |
                    |$HELP_INDENT The default directory is found by scanning from the
                    |$HELP_INDENT current directory upwards looking for:
                    |$HELP_INDENT 1. The farthest ancestor directory that contains a
                    |$HELP_INDENT    `$TEMPER_OUT_NAME` directory, or
                    |$HELP_INDENT 2. The closest ancestor directory that contains a
                    |$HELP_INDENT    `${LibraryConfiguration.fileName.fullName}` file.
                    |
                    |$HELP_INDENT Value must be a${
                "" // followed by flag type
            }
            """.trimMargin(),
        ).default(defaultWorkdir(cwd))

        fun initDirectoryOpt(argParser: ArgParser) = argParser.option(
            type = PathArgType(fs),
            fullName = "output-directory",
            shortName = "o",
            description = """
                    |New or empty directory in which to create a new project.
                    |
                    |$HELP_INDENT Defaults to the current directory:
                    |$HELP_INDENT ${temperEscaper.escape("$cwd")}.
                    |
                    |$HELP_INDENT Value must be a${
                "" // followed by flag type
            }
            """.trimMargin(),
        ).lazilyMapValue { it ?: cwd }

        fun limitOpt(argParser: ArgParser) = argParser.option(
            type = ArgType.Int,
            fullName = "limit",
            description = """
                |Number of builds to complete before exiting. Useful for testing.
            """.trimMargin(),
        )

        fun outputDirectoryDocsOpt(argParser: ArgParser) = argParser.option(
            type = PathArgType(fs),
            fullName = "output-directory",
            shortName = "o",
            description = """
                |Root directory to save build artifacts to.
                |
                |$HELP_INDENT Defaults to <workroot>/$TEMPER_OUT_NAME/-docs.
                |
                |$HELP_INDENT Value must be a${
                "" // followed by flag type
            }
            """.trimMargin(),
        )

        fun verboseOpt(argParser: ArgParser) = argParser.option(
            type = ArgType.Boolean,
            fullName = "verbose",
            shortName = "v",
            description = "Enable verbose logging",
        ).default(false)

        fun ignoreFileOpt(argParser: ArgParser) = argParser.option(
            type = PathArgType(fs, wantAbsolute = true),
            fullName = "ignorefile",
            shortName = "ign",
            description = """
                    |
                    |$HELP_INDENT Path to a file like `.gitignore` which specifies file
                    |$HELP_INDENT patterns to *not* consider as source files.
                    |
                    |$HELP_INDENT The default ignore is found by scanning from the
                    |$HELP_INDENT current directory upwards looking for the first
                    |$HELP_INDENT regular file whose name is in this list:
                    |$HELP_INDENT ${ignoreFileSearchList.joinToString(", ") { "`$it`"}}
                    |
                    |$HELP_INDENT For example, if the current directory does not have
                    |$HELP_INDENT any such file, but the parent contains `.gitignore`
                    |$HELP_INDENT then the default is `../.gitignore` and the rules
                    |$HELP_INDENT in it are evaluated relative to its parent directory.
                    |
                    |$HELP_INDENT Value must be a${
                "" // followed by flag type
            }
            """.trimMargin(),
        ).default(defaultIgnoreFile(cwd) ?: Path.of(DEV_NULL))

        fun makeArgParser(): ArgParser {
            val argParser = ArgParser(
                programName = COMMAND_LINE_TOOL_NAME,
                useDefaultHelpShortName = true, // Allow -h
                prefixStyle = ArgParser.OptionPrefixStyle.LINUX,
                skipExtraArguments = false,
                strictSubcommandOptionsOrder = true,
            )

            val subcommands = arrayOf(
                object : Subcommand(
                    name = HELP_SUBCOMMAND_NAME,
                    actionDescription = """
                        |Prints help text for a subcommand and exits.
                    """.trimMargin(),
                ) {
                    val subCommandArg = this.argument(
                        type = ArgType.String,
                        fullName = "subcommand",
                        description = "The subcommand to get help for; defaults to listing subcommands.",
                    ).optional()

                    override fun execute() {
                        // Make a new argument parser since they're not reusable.
                        val subCommand = subCommandArg.value
                        if (subCommand == null) {
                            println(TEMPER_COMMAND_HELP_SYNOPSIS)
                        }
                        makeArgParser().parse(listOfNotNull(subCommand, "--help").toTypedArray())
                        exitProcess(EXIT_SUCCESS)
                    }
                },
                object : Subcommand(
                    name = "build",
                    actionDescription = """
                        |Brings Temper libraries under the work root up to date.
                    """.trimMargin(),
                ) {
                    val wr = workRootOpt(this)
                    val backends = backendsOptDefaulting(this)
                    val verbose = verboseOpt(this)
                    val ignoreFile = ignoreFileOpt(this)
                    override fun execute() {
                        val ok = wrapBuild("Build", verbose = verbose.value) { cliConsole, executorService ->
                            val result = doBuild(
                                executorService = executorService,
                                backends = backends.value,
                                workRoot = wr.value,
                                ignoreFile = ignoreFile.value.orNullIfDevNull,
                                shellPreferences = shellPreferencesFor(verbose.value, cliConsole),
                            )
                            result.ok
                        }
                        exitProcess(ok)
                    }
                },
                object : Subcommand(
                    name = "init",
                    actionDescription = """
                        |Generates initial contents for a Temper project.
                    """.trimMargin(),
                ) {
                    // Note that this uses the "init" dir option, not the "out" dir.
                    val outputDirectory = initDirectoryOpt(this)
                    override fun execute() {
                        val ok = doInit(cliConsole = console, outputDirectory = outputDirectory.value)
                        if (!ok) {
                            console.error("Init failed")
                        }
                        exitProcess(ok)
                    }
                },
                object : Subcommand(
                    name = "license",
                    actionDescription = """
                        |Displays the copyright and license attributions for the Temper toolchain and documentation.
                    """.trimMargin(),
                ) {
                    override fun execute() {
                        // TODO(tjp, cli): Support pagination if tty.
                        println(javaClass.getResource("license-do-not-edit.txt")!!.readText())
                    }
                },
                object : Subcommand(
                    name = "licenses",
                    actionDescription = """
                        |Displays dependencies and licenses bundled with them.
                    """.trimMargin(),
                ) {
                    override fun execute() {
                        // TODO(tjp, cli): Support pagination if tty.
                        println(javaClass.getResource("licenses-do-not-edit.txt")!!.readText())
                    }
                },
                object : Subcommand(
                    name = REPL_SUBCOMMAND_NAME,
                    actionDescription = """
                        |Interactive shell that allows evaluating Temper code. Enter help() for internal docs.
                    """.trimMargin(),
                ) {
                    val stages = Stage.entries.map { s ->
                        s to option(
                            type = ArgType.Boolean,
                            fullName = s.name,
                            shortName = s.abbrev.toString(),
                            description = "Dumps this stage while processing",
                        )
                    }
                    val backendPlusConfig = backendPlusReplConfigOpt(
                        this,
                        description = """
                            |If not `${interpBackendId.uniqueId}` then instead of starting an
                            |$HELP_INDENT interactive Temper shell, builds the current work root and start
                            |$HELP_INDENT an interactive shell for that backend's target language.
                            |
                            |$HELP_INDENT For example, `-b js` starts `node` with the work root's libraries'
                            |$HELP_INDENT JS translations pre-loaded.
                            |
                            |$HELP_INDENT See the `help()` function's ".../${
                            BackendHelpTopicKeys.REPL
                        }" help topics for backend
                            |$HELP_INDENT specific notes.
                            |
                            |$HELP_INDENT Value must be a${
                            // followed by type description
                            ""
                        }
                        """.trimMargin(),
                    ).default(interpBackendId to ConfigFromCli.empty)
                    val wr = workRootOpt(this)
                    val verbose = verboseOpt(this)
                    val separator = option(
                        replSeparatorArgType,
                        "separator",
                        description = buildString {
                            append("token that signals a Temper shell should interpret\n")
                            append("$HELP_INDENT its pending input instead of waiting for more:\n")
                            for (separator in ReplSeparator.entries) {
                                append("$HELP_INDENT - ${separator.keyword}: ${separator.description}\n")
                            }
                            append(HELP_INDENT) // followed by type description
                        },
                    ).default(ReplSeparator.default)
                    val prompt = option(
                        replPromptArgType,
                        "prompt",
                        description = buildString {
                            append("prompt displayed to user when Temper shell is waiting\n")
                            append("$HELP_INDENT for input:\n")
                            for (separator in ReplPrompt.entries) {
                                append("$HELP_INDENT - ${separator.keyword}: ${separator.description}\n")
                            }
                            append(HELP_INDENT) // followed by type description
                        },
                    ).default(ReplPrompt.default)

                    override fun execute() {
                        // If we have a backend
                        if (interpBackendId == backendPlusConfig.value.first) {
                            executeTemperRepl()
                        } else {
                            executeBackendRepl()
                        }
                    }

                    fun executeTemperRepl() {
                        val dumpStages =
                            stages.mapNotNull { (s, o) -> if (o.value == true) s else null }
                                .toSet()
                        wrapBuild("repl", verbose = verbose.value) { console, executorService ->
                            if (verbose.value) {
                                console.setLogLevel(Log.Fine)
                            }
                            val repl = Repl(
                                console = console,
                                directories = getDirectories(fs),
                                executorService = executorService,
                                config = ReplConfig(
                                    dumpStages = dumpStages,
                                    separator = separator.value,
                                    prompt = prompt.value,
                                ),
                            )

                            val isTtyLike = repl.console.textOutput.isTtyLike
                            val lineReader = LineReaderBuilder.builder()
                                .appName("Temper")
                                // .dumb(true) allows, it doesn't force
                                .terminal(TerminalBuilder.builder().dumb(true).build())
                                // Use the Temper lexer to style tokens as user types input
                                .highlighter(ReplInterop.TemperJlineHighlighterImpl(repl))
                                // TAB auto-completes using available variables.
                                .completer(ReplInterop.TemperCompleterImpl(repl))
                                .parser(ReplInterop.LineParser(repl))
                                .configTemperHistory(repl.directories.userDataDir)
                                .configExpandHistory(false)
                                .build()
                            AutosuggestionWidgets(lineReader).enable()
                            while (true) {
                                val prompt = repl.promptAsString(isTtyLike = isTtyLike)
                                    .replace("%", "%%") // Prevent substitution
                                val line = try {
                                    // Quick hack editable auto indent.
                                    // TODO Indent by open lines not brackets.
                                    // TODO Also, auto dedent on close and/or awesome multiline, etc.
                                    val indent = "  ".repeat(repl.openBracketDepth)
                                    // Read line.
                                    lineReader.readLine(prompt, null, indent)
                                } catch (_: UserInterruptException) {
                                    repl.reset()
                                    continue
                                } catch (_: EndOfFileException) {
                                    null
                                } ?: break
                                repl.processLineRobustly(line)
                            }
                            cleanUpAndExit {
                                lineReader.history.save()
                                repl.close()
                            }
                        }
                    }

                    fun executeBackendRepl() {
                        val (backendId, backendReplConfig) = backendPlusConfig.value
                        val backendFactory = lookupFactory(backendId)
                        if (backendFactory == null) {
                            console.error("No backend with ID `$backendId`")
                            exitProcess(ok = false)
                        }
                        val workRoot = wr.value

                        val ok = wrapBuild("Repl", verbose = verbose.value) { cliConsole, executorService ->
                            val request = ExecInteractiveRepl(config = backendReplConfig)
                            val backends = setOf(backendId)

                            val build = prepareBuild(
                                executorService = executorService,
                                backends = backends.toList(),
                                workRoot = workRoot,
                                ignoreFile = null,
                                shellPreferences = shellPreferencesFor(
                                    verbose = verbose.value,
                                    console = cliConsole,
                                ),
                                moduleConfig = ModuleConfig.default,
                                runTask = RunTask(
                                    request = request,
                                    backends = backends,
                                ),
                            )
                            tryBuild(build)
                            // getting any kind of result here is an indicator that something went wrong
                        }
                        exitProcess(ok)
                    }
                },
                object : Subcommand(
                    name = "run",
                    actionDescription = """
                        |Loads the named Temper-built library after processing libraries in the work root.
                    """.trimMargin(),
                ) {
                    val library =
                        option(
                            fullName = "library",
                            type = DashedIdentifierArgType,
                            description = "Name of library to load.",
                        ).required()
                    val backend = backendOpt(this).required()
                    val verbose = verboseOpt(this)
                    val workRoot = workRootOpt(this)
                    override fun execute() {
                        val ok = wrapBuild("Run", verbose = verbose.value) { cliConsole, executorService ->
                            val libraryName = library.value
                            val request = RunLibraryRequest(libraryName)

                            val backends = setOf(backend.value)

                            val build = prepareBuild(
                                executorService = executorService,
                                backends = backends.toList(),
                                workRoot = workRoot.value,
                                ignoreFile = null,
                                shellPreferences = shellPreferencesFor(
                                    verbose = verbose.value,
                                    console = cliConsole,
                                ),
                                moduleConfig = ModuleConfig.default,
                                runTask = RunTask(
                                    request = request,
                                    backends = backends,
                                ),
                            )
                            tryBuild(build)
                        }
                        exitProcess(ok)
                    }
                },
                object : Subcommand(
                    name = "doc",
                    actionDescription = """
                        |Generates documentation for Temper libraries under the work root.
                    """.trimMargin(),
                ) {
                    val workRoot = workRootOpt(this)
                    val outputDirectory = outputDirectoryDocsOpt(this)
                    val backends = backendsOptDefaulting(this)

                    @DelicateCoroutinesApi
                    override fun execute() {
                        val ok = wrapBuild("docgen", false) { cliConsole, _ ->
                            doDocGen(
                                workRoot = workRoot.value,
                                outputDirectory = outputDirectory.value,
                                backends = backends.value,
                                cliConsole = cliConsole,
                            )
                        }
                        exitProcess(ok)
                    }
                },
                object : Subcommand(
                    name = "serve",
                    actionDescription = """
                        |Starts the Temper language server, expecting to be called from a language client.
                    """.trimMargin(),
                ) {
                    val port =
                        option(
                            fullName = "port",
                            type = ArgType.Int,
                            description = "Port on which to run the server.",
                        ).required()

                    // Manually map to keep the cli stable, even though the strings match at the moment.
                    val tokenModes = mapOf("add" to TokenMode.Add, "full" to TokenMode.Full, "none" to TokenMode.None)
                    val tokenModeStrings = tokenModes.entries.associate { it.value to it.key }
                    val tokens =
                        option(
                            fullName = "tokens",
                            type = ArgType.Choice(
                                tokenModes.values.toList(),
                                { tokenModes.getValue(it) },
                                { tokenModeStrings.getValue(it) },
                            ),
                            description = "Which semantic tokens to produce, defaulting to supplementary additions.",
                        ).default(TokenMode.Add)

                    @DelicateCoroutinesApi
                    override fun execute() {
                        startPeriodicGc()
                        doServe(port = port.value, tokenMode = tokens.value)
                    }
                },
                object : Subcommand(
                    name = "test",
                    actionDescription = """
                        |Runs the tests with the configured backends.
                    """.trimMargin(),
                ) {
                    val library =
                        option(
                            fullName = "library",
                            type = DashedIdentifierArgType,
                            description = "Name of the library to test.",
                        )
                    val backends = backendsOptRequired(this, allowInterpreter = true)
                    val verbose = verboseOpt(this)
                    val workRoot = workRootOpt(this)
                    override fun execute() {
                        val ok = wrapBuild("Test", verbose = verbose.value) { cliConsole, executorService ->
                            val backends = backends.value.toSet()
                            val request = RunTestsRequest(
                                library.value?.let { setOf(it) },
                            )
                            val result = doBuild(
                                executorService = executorService,
                                backends = backends.toList(),
                                workRoot = workRoot.value,
                                ignoreFile = null,
                                shellPreferences = shellPreferencesFor(verbose.value, cliConsole),
                                runTask = RunTask(request, backends),
                            )
                            result.ok
                        }
                        exitProcess(ok)
                    }
                },
                object : Subcommand(
                    name = "watch",
                    actionDescription = """
                        |Like `$COMMAND_LINE_TOOL_NAME build`, but keeps running, watching for changes to files
                        |$HELP_INDENT under the work root and rebuilds as necessary.
                        |$HELP_INDENT ${CTRL_KEY_SYMBOL}C exits.
                    """.trimMargin(),
                ) {
                    val wr = workRootOpt(this)
                    val ignoreFile = ignoreFileOpt(this)
                    val backends = backendsOptDefaulting(this)
                    val limit = limitOpt(this)
                    val testBackends = backendsOptWatchTest(this)
                    val verbose = verboseOpt(this)

                    override fun execute() {
                        startPeriodicGc()
                        val ok = wrapSemiInteractive { userSignalledDoneFuture ->
                            // The userSignalledDoneFuture is marked completed
                            // when the user is done.
                            // `doWhen` uses this to shut down gracefully.

                            wrapBuild("Watch", verbose = verbose.value) { cliConsole, executorService ->
                                doWatch(
                                    executorService = executorService,
                                    // Test also implies building.
                                    backends = buildSet {
                                        addAll(testBackends.value)
                                        remove(interpBackendId)
                                        addAll(backends.value)
                                    }.toList(),
                                    testBackends = testBackends.value.toList(),
                                    buildLimit = limit.value,
                                    shellPreferences = shellPreferencesFor(verbose.value, cliConsole),
                                    workRoot = wr.value,
                                    ignoreFile = ignoreFile.value.orNullIfDevNull,
                                    userSignalledDone = userSignalledDoneFuture,
                                )
                            }
                        }
                        exitProcess(ok)
                    }
                },
                object : Subcommand(
                    name = "version",
                    actionDescription = """
                        |Reports Temper version.
                    """.trimMargin(),
                ) {
                    override fun execute() {
                        val title = javaClass.`package`.implementationTitle ?: "Temper"
                        // TODO(tjp, cli): Discover source dir and try to run git there?
                        val version = javaClass.`package`.implementationVersion ?: "unversioned source code"
                        // TODO(tjp, legal): Add copyright notice(s)? Point to `temper license(s)`?
                        println("$title $version")
                        exitProcess(ok = true)
                    }
                },
            )
            // No need to reshuffle above commands on rename, but list them to the user alphabetically.
            subcommands.sortBy { it.name }
            @Suppress("SpreadOperator") // Just passing in the array here.
            argParser.subcommands(*subcommands)
            return argParser
        }

        // Assume an interactive shell if there is no subcommand specified.
        // If there were global flags, then we'd have to advance over those
        // to see if there's a subcommand and if not, insert the subcommand there.
        val fullArgs = if (argv.isEmpty() || argv.first().isSubcommandFlagArgument) {
            // No subcommand specified.  Default to starting the interactive shell.
            console.warn(NO_SUBCOMMAND_WARNING)
            Array(argv.size + 1) { i ->
                if (i == 0) {
                    DEFAULT_SUBCOMMAND_NAME
                } else {
                    argv[i - 1]
                }
            }
        } else {
            argv
        }

        makeArgParser().parse(fullArgs)

        exitProcess(EXIT_SUCCESS)
    }

    private fun wrapBuild(
        label: String,
        verbose: Boolean,
        process: (Console, ExecutorService) -> Boolean,
    ): Boolean {
        if (verbose) {
            console.setLogLevel(Log.Fine)
        }

        val executorService = makeExecutorServiceForBuild()
        return try {
            process(console, executorService).also { ok ->
                if (!ok) {
                    // Go to main console here, so it gets its own line no matter what.
                    console.error("$label failed")
                }
            }
        } finally {
            console.textOutput.flush()
            executorService.shutdown()
        }
    }

    /**
     * Handles shutdown gracefully for a semi-interactive process.
     * One which may run in a shell, or via a manager like `start-stop-daemon`.
     *
     * This is for processes that may inherit stdin from a shell, but which
     * do not expect to use it.
     */
    private fun <T> wrapSemiInteractive(
        go: (CompletableRFuture<Unit, Nothing>) -> T,
    ): T {
        // There are several cases handled below which all
        // end up completing the Future above.
        // - Ctrl-C sends a SIGINT signal to the foreground
        //   process.  We use sun.misc APIs to catch that.
        // - When a terminal is closed, it sends SIGHUP to
        //   any owned, child processes.
        //   Many Daemon management systems send SIGTERM
        //   when told to stop a daemon.
        //   We use the same sun.misc APIs to catch these.
        // - Some process managers use SIGQUIT.
        //   We can't handle SIGQUIT directly because that
        //   is a trusted path to Java debuggers, so we
        //   instead intercept shutdown via Runtime
        //   shutdown hooks.

        val userSignalledDone = UnmanagedFuture.newCompletableFuture<Unit, Nothing>(
            "Completes if aborted from command line",
        )

        Runtime.getRuntime().addShutdownHook(
            object : Thread(
                {
                    // Invoked on SIGQUIT
                    userSignalledDone.complete(RSuccess(Unit))
                },
                "Signal `temper watch` done on SIGQUIT",
            ) {},
        )

        val onEndSignal = SignalHandler { userSignalledDone.complete(RSuccess(Unit)) }
        Signal.handle(Signal("INT"), onEndSignal)
        Signal.handle(Signal("TERM"), onEndSignal)
        try {
            Signal.handle(Signal("HUP"), onEndSignal)
        } catch (exception: IllegalArgumentException) {
            // Presume we're on a system such as windows that has no such thing.
            if ("windows" !in System.getProperty("os.name").lowercase()) {
                // But report surprises so we hear about them.
                console.error(exception)
            }
        }

        return go(userSignalledDone)
    }
}

private fun shellPreferencesFor(verbose: Boolean, console: Console): ShellPreferences =
    ShellPreferences(
        verbosity = if (verbose) {
            ShellPreferences.Verbosity.Verbose
        } else {
            ShellPreferences.Verbosity.Quiet
        },
        onFailure = if (isatty(2)) {
            ShellPreferences.OnFailure.Freeze
        } else {
            ShellPreferences.OnFailure.Release
        },
        console = console,
    )

@ExperimentalCli
fun main(argv: Array<String>) {
    object : Main() {
        override fun exitProcess(exitCode: Int) = kotlin.system.exitProcess(exitCode)
    }.run(argv)
}

/**
 * https://tldp.org/LDP/abs/html/exitcodes.html
 * > 1  Catchall for general errors
 */
const val GENERIC_ERROR_EXIT_CODE = 1

private const val HELP_INDENT = "           "

const val CTRL_KEY_SYMBOL = '\u2303'

internal fun defaultWorkdir(cwd: Path): Path =
    // Find the nearest established out root or else the highest up library config.
    cwd.lowestAncestorWithAnyOf(listOf(TEMPER_OUT_NAME))
        ?: cwd.highestAncestorWithAnyOf(listOf(LibraryConfiguration.fileName.fullName))
        ?: cwd

@Suppress("SpellCheckingInspection")
internal val ignoreFileSearchList = listOf(
    ".temperignore",
    ".gitignore",
)

internal fun defaultIgnoreFile(cwd: Path): Path? =
    cwd.lowestAncestorAndMemberWithAnyOf(ignoreFileSearchList, allowFile = true)
        ?.let { (dir, fileName) ->
            dir.resolve(fileName)
        }

private const val GC_PERIOD_MS = 60_000L

private fun startPeriodicGc() {
    Timer("periodic-gc", true).scheduleAtFixedRate(
        object : TimerTask() {
            override fun run() {
                Runtime.getRuntime().gc()
            }
        },
        GC_PERIOD_MS,
        GC_PERIOD_MS,
    )
}

private object BackendArgType : ArgType<BackendId>(hasParameter = true) {
    override val description = "backend in (${
        supportedBackends.joinToString(", ")
    })"

    override fun convert(value: kotlin.String, name: kotlin.String): BackendId {
        val backendId = BackendId(value)
        require(isSupported(backendId) || backendId == interpBackendId) {
            "Not a supported backend"
        }
        return backendId
    }
}

typealias BackendPlusReplConfig = Pair<BackendId, ConfigFromCli>
private object BackendPlusReplConfigArgType : ArgType<BackendPlusReplConfig>(hasParameter = true) {
    override val description = "backend in ($interpBackendId, ${
        supportedBackends.joinToString(", ")
    })\n$HELP_INDENT optionally followed by a colon (':') and extra configuration."

    override fun convert(value: kotlin.String, name: kotlin.String): BackendPlusReplConfig {
        val (beforeColon, afterColon) = run {
            val colonIndex = value.indexOf(':')
            if (colonIndex < 0) {
                value to ""
            } else {
                value.substring(0, colonIndex) to value.substring(colonIndex + 1)
            }
        }

        val backendId = BackendId(beforeColon.trimEnd())
        require(isSupported(backendId) || backendId == interpBackendId) {
            "Not a supported backend"
        }
        return backendId to parseConfigFromCli(afterColon)
    }
}

private object DashedIdentifierArgType : ArgType<DashedIdentifier>(hasParameter = true) {
    override val description = "dashed library name like \"foo-bar\""

    override fun convert(value: kotlin.String, name: kotlin.String): DashedIdentifier =
        DashedIdentifier.from(value)
            ?: throw IllegalArgumentException(
                "Library name `$value` is not a valid dashed-identifier at index ${
                    DashedIdentifier.firstProblemCharIndex(value)
                }",
            )
}

private val replSeparatorArgType = ArgType.Choice(
    choices = ReplSeparator.entries,
    toVariant = { value ->
        ReplSeparator.entries.firstOrNull { it.keyword == value }
            ?: throw IllegalArgumentException(
                "$value is not one of (${ReplSeparator.entries.joinToString { it.keyword }})",
            )
    },
    variantToString = { it.keyword },
)

private val replPromptArgType = ArgType.Choice(
    choices = ReplPrompt.entries,
    toVariant = { value ->
        ReplPrompt.entries.firstOrNull { it.keyword == value }
            ?: throw IllegalArgumentException(
                "$value is not one of (${ReplPrompt.entries.joinToString { it.keyword }})",
            )
    },
    variantToString = { it.keyword },
)

private val String.isSubcommandFlagArgument: Boolean get() = startsWith('-')

private const val HELP_SUBCOMMAND_NAME = "help"
private const val REPL_SUBCOMMAND_NAME = "repl"
private const val DEFAULT_SUBCOMMAND_NAME = REPL_SUBCOMMAND_NAME
private const val TEMPER_COMMAND_HELP_SYNOPSIS = "Launches the Temper programming language toolchain.\n"
private val NO_SUBCOMMAND_WARNING = """
    |No subcommand given.
    |Running interactively as if `$COMMAND_LINE_TOOL_NAME $DEFAULT_SUBCOMMAND_NAME`. Ctrl-D exits.
    |`$COMMAND_LINE_TOOL_NAME $HELP_SUBCOMMAND_NAME` shows usage.
    |
""".trimMargin()

/** Placeholder for "no value" in a context that requires a path */
private const val DEV_NULL = "/dev/null"
private val Path.orNullIfDevNull: Path?
    get() =
        if (this == Path.of(DEV_NULL)) {
            null
        } else {
            this
        }

internal fun makeExecutorServiceForBuild(): ExecutorService =
    // We tried Executors.newScheduledThreadPool here, but it has a hard cap on thread count which
    // can lead to stalls.
    //
    // docs.oracle.com/en/java/javase/11/docs/api/java.base/java/util/concurrent/ForkJoinPool.html:
    //
    // > A ForkJoinPool differs from other kinds of ExecutorService mainly by virtue of employing
    // > work-stealing: all threads in the pool attempt to find and execute tasks submitted to
    // > the pool and/or created by other active tasks (eventually blocking waiting for work if
    // > none exist). This enables efficient processing when most tasks spawn other subtasks ...
    //
    // Most build concurrency is future chaining which fits into that sub-task spawning pattern.
    //
    // > A static `commonPool()` is available and appropriate for most applications.
    ForkJoinPool.commonPool()

private fun tryBuild(build: Build?): Boolean {
    val result = build?.let { doOneBuild(it) }
    val taskResult = when (result) {
        is BuildDoneResult -> result.taskResults
        null,
        is BuildInitFailed,
        is BuildNotNeededResult,
        -> null
    }
    taskResult?.outputThunk?.let { outputThunk ->
        build!!.harness.cliConsole.info(outputThunk())
    }
    return result?.ok == true && taskResult?.errorFree != false
}
