package lang.temper.be.java

import lang.temper.be.Dependencies
import lang.temper.be.cli.Advice
import lang.temper.be.cli.Aux
import lang.temper.be.cli.CliEnv
import lang.temper.be.cli.CliFailure
import lang.temper.be.cli.CliTool
import lang.temper.be.cli.Command
import lang.temper.be.cli.CopyMode
import lang.temper.be.cli.Effort
import lang.temper.be.cli.EffortSuccess
import lang.temper.be.cli.ToolchainResult
import lang.temper.be.cli.composing
import lang.temper.be.cli.explain
import lang.temper.be.cli.mapEffort
import lang.temper.be.cli.maybeLogBeforeRunning
import lang.temper.common.RFailure
import lang.temper.common.RResult
import lang.temper.common.RSuccess
import lang.temper.common.invoke
import lang.temper.common.partiallyOrder
import lang.temper.fs.OutDir
import lang.temper.log.FilePath
import lang.temper.log.dirPath
import lang.temper.log.filePath
import lang.temper.log.resolveDir
import lang.temper.name.DashedIdentifier
import lang.temper.result.junit.combineSurefireResults
import xmlparser.XmlParser

internal sealed interface RunJava {
    val mavenCommand: List<String>
}
internal object RunAsTest : RunJava {
    override val mavenCommand = listOf("test")
}
internal object ExecJShellAsLastCommand : RunJava {
    override val mavenCommand: List<String> = listOf(
        // https://github.com/johnpoth/jshell-maven-plugin/tags
        "com.github.johnpoth:jshell-maven-plugin:1.4:run",
    )
}

internal class RunByExec(val mainJavaName: QualifiedName, private val bundled: Boolean = false) : RunJava {
    override val mavenCommand get() = buildList {
        add("compile")
        if (bundled) {
            // For bundled, no parent pom, so simple running from mvn works ok.
            add("exec:java@${mainJavaName.fullyQualified}")
        }
    }
}

internal fun runJavaBestEffort(
    cliEnv: CliEnv,
    factory: JavaBackend.JavaFactory,
    runJava: RunJava,
    files: OutDir,
    runLibrary: DashedIdentifier?,
    taskName: String?,
    dependencies: Dependencies<*>,
    bundled: Boolean = false,
    testLibraries: Collection<DashedIdentifier>? = null,
): List<ToolchainResult> = benchmark("runJavaBestEffort") {
    val javaLang = factory.lang
    @Suppress("UNCHECKED_CAST") // Allow use with JavaBackendKey
    dependencies as Dependencies<JavaBackend>

    val specifics = factory.specifics
    return cliEnv.composing(specifics) {
        val groupingPomPath = filePath(factory.backendId.uniqueId, "pom.xml")
        if (!bundled) {
            this.write(
                destination = groupingPomPath,
                source = "${groupingPom(javaLang, dependencies)}".encodeToByteArray(),
            )
        }

        val prep = prepareJavaPackages(
            dependencies = dependencies,
            factory = factory,
            filesForBundled = when {
                bundled -> files
                else -> null
            },
            runLibrary = runLibrary,
            testLibraries = testLibraries,
        )
        val prepInfo = when (prep) {
            is PackagePrepFailure -> return prep.results
            is PackagePrepInfo -> prep
        }
        val javaHomeEnv = prepInfo.javaHomeEnv
        val libraryNames = prepInfo.libraryNames
        val localRepo = prepInfo.localRepo
        val maven = prepInfo.maven
        // Use explicit encoding because Windows.
        val encodingDef = "-Dfile.encoding=${specifics.preferredEncoding.realCharset.name()}"

        fun runMaven(
            workingDir: FilePath,
            mavenArgs: List<String>,
            /** True if the current OS process should be discarded in favour of the maven process. */
            runAsLast: Boolean = false,
        ): RResult<EffortSuccess, CliFailure> = benchmark("runMaven($mavenArgs)") {
            val cmd = Command(
                args = buildList {
                    localRepo?.also { add("-Dmaven.repo.local=$localRepo") }
                    if (VERBOSE) {
                        add("-X")
                        add("-e")
                    }
                    addAll(mavenArgs)
                },
                cwd = workingDir,
                reproduce = mapOf(
                    Advice.Caller to "running test $taskName",
                    Advice.Step to "building and running source files",
                ),
                aux = mapOf(
                    Aux.BuildLogs to workingDir.resolve(mavenLogs.segments, false),
                ),
                env = javaHomeEnv + mapOf("MAVEN_OPTS" to encodingDef),
            )
            cmd.maybeLogBeforeRunning(maven, cliEnv.shellPreferences)
            return if (runAsLast) {
                runAsLast(maven, cmd)
            } else {
                maven.run(cmd)
            }
        }

        fun doRunJava(
            workingDir: FilePath,
            extraArgs: List<String> = listOf(),
        ): RResult<EffortSuccess, CliFailure> {
            val extraMavenArgs = listOf(
                "-Dorg.slf4j.simpleLogger.logFile=${relativePath(mavenLogs)}",
                "--no-transfer-progress", // this spams stdout
            )
            return runMaven(
                workingDir = workingDir,
                mavenArgs = extraMavenArgs + extraArgs + runJava.mavenCommand,
            )
        }

        when (bundled) {
            true -> {
                val result = doRunJava(dirPath()).let { withTestResult(dirPath(), it) ?: it }
                listOf(ToolchainResult(result = result))
            }
            false -> when (runJava) {
                is RunByExec -> {
                    // Currently always for a specific library.
                    val workingDir = dirPath(specifics.backendId.uniqueId)
                    val compileResult = doRunJava(workingDir)
                    if (compileResult.failure != null) {
                        return listOf(ToolchainResult(libraryName = runLibrary, compileResult))
                    }
                    // TODO Does this ensure a proper java version? If not, do so?
                    // TODO Minimally, we expect the compile above to be on the right version.
                    // TODO Functional tests also run entirely on maven.
                    val java = this[factory.specifics.javaTool]
                    val classpath = libraryNames.joinToString(cliEnv.pathSeparator) { "$it/target/classes" }
                    val cmd = Command(
                        args = listOf(encodingDef, "--class-path", classpath, runJava.mainJavaName.fullyQualified),
                        cwd = workingDir,
                    )
                    val runResult = java.run(cmd)
                    listOf(ToolchainResult(libraryName = runLibrary, runResult))
                }
                is RunAsTest -> {
                    val workingDir = dirPath(specifics.backendId.uniqueId)
                    val libs = libraryNames.joinToString(",") { it.text }
                    if (VERBOSE) {
                        println("--- pre test details ---")
                        println("working dir:")
                        cliEnv.readDir(workingDir, recursively = true).forEach { kid ->
                            println(kid)
                        }
                        when {
                            cliEnv.fileExists(groupingPomPath) -> {
                                println("grouping pom:")
                                println(cliEnv.readFile(groupingPomPath))
                            }
                            else -> println("no grouping pom")
                        }
                        println("--- run test ---")
                    }
                    val result = doRunJava(workingDir, listOf("-pl", libs, "-am"))
                    if (VERBOSE) {
                        println("--- post test explanation ---")
                        for (pair in result.explain(true)) {
                            println(pair)
                        }
                    }
                    libraryNames.mapNotNull libraries@{ libraryName ->
                        val libraryDir = workingDir.resolveDir(libraryName.text)
                        val testResult = withTestResult(libraryDir, result) ?: return@libraries null
                        ToolchainResult(libraryName = libraryName, result = testResult)
                    }
                }
                is ExecJShellAsLastCommand -> buildList {
                    val execDir = groupingPomPath.dirName()
                    // First compile everything.
                    val compileCommand = runMaven(
                        execDir,
                        listOf("compile"),
                    )
                    add(ToolchainResult(result = compileCommand))
                    if (compileCommand is RSuccess) {
                        // Then, start up the interactive shell.
                        val message = buildString {
                            append("Starting JShell\nUse the imports below to access translations.")

                            dependencies.libraryConfigurations.byLibraryName.keys
                                .forEach { temperLibraryName ->
                                    val javaArtifact = dependencies.metadata[
                                        temperLibraryName, JavaMetadataKey.LibraryArtifact(javaLang),
                                    ]?.toMavenString()
                                    val packages = dependencies.metadata[
                                        temperLibraryName,
                                        JavaMetadataKey.Packages(javaLang),
                                    ]?.main ?: emptySet()
                                    append("\n\n")
                                    append("$javaArtifact translated from Temper $temperLibraryName\n")
                                    append(HORIZONTAL_RULE).append("\n")
                                    packages.forEach { packageName ->
                                        append("  import ").append(packageName).append(".*;\n")
                                    }
                                    append(HORIZONTAL_RULE).append("\n")
                                }
                            append("\n")
                        }
                        cliEnv.shellPreferences.console.log(message)
                        val jshell = cliEnv[Java17Specifics.jshellTool]
                        val classpath = libraryNames.joinToString(cliEnv.pathSeparator) { "$it/target/classes" }
                        val cmd = Command(
                            args = listOf("-R$encodingDef", "--class-path", classpath),
                            cwd = execDir,
                            reproduce = mapOf(
                                Advice.Caller to "running test $taskName",
                                Advice.Step to "running jshell",
                            ),
                        )
                        cliEnv.runAsLast(jshell, cmd).also { add(ToolchainResult(result = it)) }
                    } else {
                        // Compile failure.
                        if (VERBOSE) {
                            // We don't seem to be printing this elsewhere, so print here when verbose.
                            compileCommand.failure?.effort?.also { println(it) }
                        }
                    }
                }
            }
        }.also { results ->
            if (results.any { it.result is RFailure }) {
                maybeFreeze()
            }
        }
    }
}

private fun CliEnv.withTestResult(
    libraryDir: FilePath,
    result: RResult<EffortSuccess, CliFailure>,
): RResult<EffortSuccess, CliFailure>? = run {
    val testResultsDir = libraryDir.resolve(sureFire.segments, true)
    val reports = readGlob(testResultsDir, "", ".xml")
    reports.isEmpty() && return null
    val combined = combineSurefireResults(reports.values)
    result.mapEffort { it.withAux(Aux.JunitXml, combined) }
}

private fun CliEnv.runAsLast(tool: CliTool, cmd: Command): RFailure<CliFailure> {
    tool.runAsLast(cmd)
    return RFailure(
        CliFailure(
            message = "execve failed to transfer control",
            effort = Effort(command = tool.specify(cmd), cliEnv = this),
        ),
    )
}

fun handlePackages(
    backend: String,
    env: Map<String, String>,
    libraryNames: List<DashedIdentifier>,
    localRepo: String?,
    maven: CliTool,
    subcommand: String,
    cwd: FilePath? = null,
): CliFailure? = benchmark("handlePackages") {
    for (libraryName in libraryNames) {
        val installCmd = Command(
            args = buildList {
                localRepo?.also { add("-Dmaven.repo.local=$localRepo") }
                // Skip signing for test repo installs.
                add("-Dgpg.skip=true")
                // Also install without testing first.
                add("-DskipTests")
                // Also skip clean for sake of speed. Worst case, users can delete temper.out.
                // "clean",
                add(subcommand)
            },
            cwd = cwd ?: dirPath(backend, libraryName.text),
            env = env,
        )
        maven.run(installCmd).invoke { (_, failure) ->
            if (failure != null) {
                return@handlePackages failure
            }
        }
    }
    return null
}

sealed interface PackagePrep

data class PackagePrepFailure(val results: List<ToolchainResult>) : PackagePrep

data class PackagePrepInfo(
    val javaHomeEnv: Map<String, String>,
    val libraryNames: List<DashedIdentifier>,
    val localRepo: String?,
    val maven: CliTool,
) : PackagePrep

private fun CliEnv.makeJavaHomeEnv(specifics: JavaSpecifics): Map<String, String> =
    specifics.findJdkHome(this).getOrElse(
        // Find jdk home explicitly in case we're running on a plain jre.
        mapResult = { mapOf("JAVA_HOME" to it) },
        // But for java8's sake, fall back to pure trust of toolchains if we don't find a jdk.
        mapFailure = { mapOf() },
    )

fun CliEnv.prepareJavaPackages(
    dependencies: Dependencies<JavaBackend>,
    factory: JavaBackend.JavaFactory,
    filesForBundled: OutDir? = null,
    installWhenSeparate: Boolean = false,
    packageWhenBundled: Boolean = false,
    runLibrary: DashedIdentifier? = null,
    testLibraries: Collection<DashedIdentifier>? = null,
): PackagePrep = run {
    val specifics = factory.specifics
    val javaHomeEnv = makeJavaHomeEnv(specifics)
    val maven = this[factory.specifics.mavenTool]
    val localRepo = when {
        // If installing, use a separate local repo, so we don't clutter the user's main local repo.
        installWhenSeparate -> userCachePath(specifics.mavenCachePath.resolveDir("repository"))
        else -> null
    }
    val libraryNames: List<DashedIdentifier>
    fun runMaven(subcommand: String, libraryNames: List<DashedIdentifier>, cwd: FilePath? = null): PackagePrep? = run {
        handlePackages(
            backend = factory.backendId.uniqueId,
            env = javaHomeEnv,
            libraryNames = libraryNames,
            localRepo = localRepo,
            maven = maven,
            subcommand = subcommand,
            cwd = cwd,
        )?.let { failure ->
            maybeFreeze()
            // TODO Organize all of RunJava better. Meanwhile, work through possible library names here.
            val effectiveLibraryNames = runLibrary?.let { listOf(it) } ?: testLibraries ?: listOf(null)
            PackagePrepFailure(
                effectiveLibraryNames.map { libraryName ->
                    ToolchainResult(libraryName = libraryName, result = RFailure(failure))
                },
            )
        }
    }
    when (filesForBundled) {
        null -> {
            // Order them so we can install them with dependencies satisfied as we go.
            // TODO Install more precise dependencies if only testing one library.
            libraryNames = (
                // And we currently reference temper-core in shallowDependencies from std but not necesarily others,
                // so rely on ordered set to make sure it's in front of the list only one time.
                setOf(DashedIdentifier.temperCoreLibraryIdentifier) + partiallyOrder(
                    dependencies.libraryConfigurations.byLibraryName.keys,
                    dependencies.shallowDependencies,
                ) { it }.toSet()
                ).toList()
            for (libraryName in libraryNames) {
                if (
                    libraryName == DashedIdentifier.temperCoreLibraryIdentifier &&
                    factory.backendId.uniqueId == JavaBackend.Java17.backendId.uniqueId
                ) {
                    // Hack remove toolchains for testing java17, so people don't need both toolchains configured.
                    // It's there specifically for people on java8 requirements, who we do require to configure toolchains.
                    // Ideally this happens earlier during the copy at build time, but is easier and ok for now.
                    // Note that this modifies in place for builds, so don't publish locally run java17 temper-core.
                    // We could also replace source & target with release, but we test under java8 also, anyway.
                    val pomPath = filePath(factory.backendId.uniqueId, libraryName.text, "pom.xml")
                    maven.cliEnv.removeToolchainsConfig(pomPath)
                }
            }
            if (installWhenSeparate) {
                runMaven("install", libraryNames)?.let { return@prepareJavaPackages it }
            }
        }
        else -> {
            makeDir(buildTop)
            libraryNames = listOf()
            copyOutputDir(filesForBundled, FilePath.emptyPath, mode = CopyMode.PlaceTop)
            copyResources(factory.immediateLibraryResources, Java.SourceDirectory.MainJava.filePath)
            if (packageWhenBundled) {
                // We actually expect only one library here, but grab all the keys anyway.
                val bundledLibraryNames = dependencies.libraryConfigurations.byLibraryName.keys.toList()
                runMaven("package", bundledLibraryNames, FilePath.emptyPath)?.let { return@prepareJavaPackages it }
            }
        }
    }
    PackagePrepInfo(
        javaHomeEnv = javaHomeEnv,
        libraryNames = libraryNames,
        localRepo = localRepo,
        maven = maven,
    )
}

private fun CliEnv.removeToolchainsConfig(pomPath: FilePath) {
    val xml = readFile(pomPath)
    val xmlParser = XmlParser()
    val doc = xmlParser.fromXml(xml)
    val plugins = doc.findChildForName("build", null)!!.findChildForName("plugins", null)!!
    // Remove toolchains, which we expect to find in our controlled pom file.
    val toolchainsIndex = plugins.children.indexOfFirst plugins@{ kid ->
        kid.name == "plugin" || return@plugins true
        kid.findChildForName("artifactId", null)?.text == "maven-toolchains-plugin"
    }
    plugins.children.removeAt(toolchainsIndex)
    // Write updated xml.
    val cleaned = xmlParser.domToXml(doc)
    write(cleaned, pomPath)
}

private val buildTop = dirPath("target")
private val sureFire = buildTop.resolveDir("surefire-reports")
private val mavenLogs = filePath("maven.log")

private const val HORIZONTAL_RULE = "----------------------------------------"

/** Like Console.benchmarkIf but simpler local expectations with just println. */
private inline fun<T> benchmark(message: String, action: () -> T): T = run {
    val start = System.currentTimeMillis()
    try {
        action()
    } finally {
        if (VERBOSE) {
            val duration = (System.currentTimeMillis() - start) * SECONDS_PER_MS
            println("$message: $duration seconds")
        }
    }
}

private const val VERBOSE = false
private const val SECONDS_PER_MS = 1e-3
