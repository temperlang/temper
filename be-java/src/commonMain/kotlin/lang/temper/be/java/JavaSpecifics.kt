package lang.temper.be.java

import lang.temper.be.Dependencies
import lang.temper.be.cli.Aux
import lang.temper.be.cli.CliEnv
import lang.temper.be.cli.CliFailure
import lang.temper.be.cli.Command
import lang.temper.be.cli.EffortBase
import lang.temper.be.cli.EffortSuccess
import lang.temper.be.cli.EnvImpl
import lang.temper.be.cli.ExecInteractiveRepl
import lang.temper.be.cli.MissingConfig
import lang.temper.be.cli.RunBackendSpecificCompilationStepRequest
import lang.temper.be.cli.RunLibraryRequest
import lang.temper.be.cli.RunTestsRequest
import lang.temper.be.cli.RunnerSpecifics
import lang.temper.be.cli.SemVerParseError
import lang.temper.be.cli.ToolSpecifics
import lang.temper.be.cli.ToolchainRequest
import lang.temper.be.cli.ToolchainResult
import lang.temper.be.cli.VersionedTool
import lang.temper.be.cli.check
import lang.temper.be.cli.checkMin
import lang.temper.common.RFailure
import lang.temper.common.RResult
import lang.temper.common.RSuccess
import lang.temper.common.flatMap
import lang.temper.common.ignore
import lang.temper.fs.NativePath
import lang.temper.fs.OutDir
import lang.temper.log.FilePath
import lang.temper.log.dirPath
import lang.temper.name.BackendId
import lang.temper.name.DashedIdentifier
import lang.temper.name.SemVer
import java.lang.Integer.parseInt
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.exists

abstract class JavaSpecifics(val majorVersion: Int) : RunnerSpecifics {
    abstract val defaultFactory: JavaBackend.JavaFactory
    val javaLang: JavaLang get() = defaultFactory.lang

    val mavenCachePath = dirPath("backends", "java", "maven-cache")

    val majorVersionText: String = stringifyJavaMajorVersion(majorVersion).first()

    open val pomSelectJdk: SelectJdk = SelectJdk.IgnoreJdk

    abstract val javaTool: ToolSpecifics
    abstract val javacTool: ToolSpecifics

    val mavenTool = object : VersionedTool {
        override val cliNames: List<String> = listOf("mvn")
        override val versionCheckArgs: List<String> = listOf("--version")

        override fun checkVersion(run: EffortSuccess): RResult<Unit, CliFailure> {
            val content = run.stdout + (run.auxOut[Aux.Stderr] ?: return RFailure(SemVerParseError(run)))
            val matchResult = versionExtract.matchEntire(content)
            val extracted = matchResult?.let { it.groupValues[1] } ?: content
            return SemVer(extracted).check(run, mavenVersions)
        }

        // mvn --version:
        // Apache Maven 3.6.3 (cecedd343002696d0abb50b32b541b8a6ba2883f)
        // etc
        private val versionExtract =
            Regex("""Apache Maven "?([\d.]+)"?.*""", RegexOption.DOT_MATCHES_ALL)

        // Version rationale: prior to 3.2.5 are EOL
        @Suppress("MagicNumber")
        private val mavenVersions = SemVer(3, 2, 5) until SemVer(4, 0, 0)
    }

    fun findJdkHome(cliEnv: CliEnv): RResult<String, CliFailure> {
        val strategies = listOf(
            ::findJdkHomeAboveJavac,
            ::findJdkHomeFromJavaProperties,
            ::findJdkHomeFromEnvironment,
        )
        for (strategy in strategies) {
            val result = strategy(cliEnv)
            if (result is RSuccess) {
                return result
            }
        }
        // TODO Gather up every failure message to combine them?
        return RFailure(CliFailure("no jdk found", effort = EffortBase.Nil))
    }

    private fun findJdkHomeAboveJavac(cliEnv: CliEnv) = cliEnv.which(javacTool).flatMap javac@{ javac ->
        for (path in Path(javac.command).readSymbolicLinks()) {
            path.parent?.parent?.let { candidate ->
                if (candidate.isJdk()) {
                    return@javac RSuccess("$candidate")
                }
            }
        }
        RFailure(CliFailure("no javac jdk", effort = EffortBase.Nil))
    }

    private fun findJdkHomeFromEnvironment(cliEnv: CliEnv): RResult<String, CliFailure> {
        ignore(cliEnv)
        return System.getenv("JAVA_HOME")?.let javaHome@{ javaHome ->
            if (Path(javaHome).isJdk()) {
                return@javaHome RSuccess(javaHome)
            }
            null
        } ?: RFailure(CliFailure("no JAVA_HOME jdk", effort = EffortBase.Nil))
    }

    private fun findJdkHomeFromJavaProperties(cliEnv: CliEnv) = cliEnv.which(javaTool).flatMap { java ->
        java.run(Command(listOf("-XshowSettings:properties", "-version")))
    }.flatMap javaHome@{
        Regex("""^\s*java.home\s*=\s*(.*)""", RegexOption.MULTILINE).find(it.stdout)?.let { match ->
            val javaHome = match.groups[1]!!.value.trim()
            if (Path(javaHome).isJdk()) {
                return@javaHome RSuccess(javaHome)
            }
            null
        } ?: RFailure(CliFailure("no java.home jdk", effort = EffortBase.Nil))
    }

    override fun runSingleSource(
        cliEnv: CliEnv,
        code: String,
        env: Map<String, String>,
        aux: Map<Aux, FilePath>,
    ): RResult<EffortSuccess, CliFailure> {
        TODO("Not yet implemented")
    }

    override fun runBestEffort(
        cliEnv: CliEnv,
        request: ToolchainRequest,
        code: OutDir,
        dependencies: Dependencies<*>,
    ): List<ToolchainResult> {
        @Suppress("UNCHECKED_CAST") // Assertion that allows backend key lookup below.
        dependencies as Dependencies<JavaBackend>

        val runJava: RunJava
        var runLibrary: DashedIdentifier? = null
        var testLibraries: Collection<DashedIdentifier>? = null
        when (request) {
            is RunLibraryRequest -> {
                // Expects a pseudo-path for [main] of "java-proj/root/qualified.ClassName"
                // where "java-proj/root" is the dir with the pom.xml file.
                runLibrary = request.libraryName
                fun failure(message: String) = listOf(
                    ToolchainResult(libraryName = runLibrary, result = RFailure(CliFailure(message))),
                )
                val qualifiedName = dependencies.metadata[runLibrary, JavaMetadataKey.MainClass(javaLang)]
                    ?: return failure("No main class for $runLibrary")
                runJava = RunByExec(qualifiedName)
            }
            is RunTestsRequest -> {
                // TODO Test specific library, group, or function.
                runJava = RunAsTest
                request.libraries?.let { testLibraries = it }
            }
            is RunBackendSpecificCompilationStepRequest -> error(request)
            is ExecInteractiveRepl -> {
                runJava = ExecJShellAsLastCommand
            }
        }
        return runJavaBestEffort(
            cliEnv = cliEnv,
            factory = defaultFactory,
            runJava = runJava,
            files = code,
            runLibrary = runLibrary,
            taskName = request.taskName,
            testLibraries = testLibraries,
            dependencies = dependencies,
        )
    }
}

private val javaCliNames = listOf("java")
private val javacCliNames = listOf("javac")
private val jshellCliNames = listOf("jshell")

@Suppress("MagicNumber")
object Java17Specifics : JavaSpecifics(majorVersion = 17) {
    override val defaultFactory get() = JavaBackend.Java17

    /**
     * <!-- snippet: backend/java/id -->
     * BackendID: `java`
     */
    override val backendId: BackendId = BackendId("java")

    @Suppress("MagicNumber")
    private val minVersion = SemVer(17, 0, 0)

    private abstract class JavaTool(private val versionPattern: Regex) : VersionedTool {
        // `--version` works on java 11+ but not java 8 (unsure about intermediates there).
        // Best to support friendly version checking even in the face of java 8.
        override val versionCheckArgs get() = listOf("-version")
        override fun checkVersion(run: EffortSuccess): RResult<Unit, CliFailure> {
            // Some go to stdout and some to stderr. Might even vary by version and/or platform. Just grab both.
            val out = (run.stdout + run.auxOut[Aux.Stderr]).trim()
            val version = versionPattern.matchAt(out, 0)!!.groupValues[1]
            return SemVer(version).flatMap({ RSuccess(it) }, { _ ->
                RResult.of(IllegalArgumentException::class) {
                    SemVer(parseInt(version.trim()), 0, 0)
                }
            }).checkMin(run, minVersion)
        }
    }

    // Examples (with other lines below these):
    // openjdk version "23" 2024-09-17
    // openjdk version "1.8.0_372"
    // openjdk version "17.0.7" 2023-04-18
    // java version "17.0.5" 2022-10-18 LTS
    private val java17Tool = object : JavaTool(Regex("""[\w ]+"([\d.]+)""")) {
        override val cliNames get() = javaCliNames
    }

    // Examples (matching the same distributions as the java examples):
    // javac 1.8.0_372
    // javac 17.0.7
    // javac 17.0.5
    // javac 23
    private val javac17Tool = object : JavaTool(Regex("""\w+ ([\d.]+)""")) {
        override val cliNames get() = javacCliNames
    }

    /** Unavailable on java8. */
    val jshellTool: ToolSpecifics = object : JavaTool(Regex("""\w+ ([\d.]+)""")) {
        override val cliNames get() = jshellCliNames
    }

    override val javaTool: ToolSpecifics get() = java17Tool
    override val javacTool: ToolSpecifics get() = javac17Tool
    override val tools: List<ToolSpecifics> = listOf(java17Tool, javac17Tool, jshellTool, mavenTool)
}

/**
 * Oracle Java 8 is complicated, but [Temurin JDK8](https://adoptium.net/temurin/releases/?version=8) works.
 */
@Suppress("MagicNumber")
object Java8Specifics : JavaSpecifics(majorVersion = 8) {
    override val defaultFactory get() = JavaBackend.Java8

    override val pomSelectJdk = SelectJdk.ToolchainJdk(majorVersionText)

    /**
     * <!-- snippet: backend/java8/id -->
     * BackendID: `java8`
     */
    override val backendId: BackendId = BackendId("java8")

    // Don't check versioning on java8 tools because we use custom toolchains.xml validation instead.

    private val java8Tool = object : ToolSpecifics {
        override val cliNames get() = javaCliNames
    }

    private val javac8Tool = object : ToolSpecifics {
        override val cliNames get() = javacCliNames
    }

    override val javaTool: ToolSpecifics get() = java8Tool
    override val javacTool: ToolSpecifics get() = javac8Tool
    override val tools: List<ToolSpecifics> = listOf(java8Tool, javac8Tool, mavenTool)

    override fun validate(cliEnv: CliEnv): RResult<Unit, CliFailure> {
        if (cliEnv.implementation != EnvImpl.Local) {
            error("Only expected for a local environment")
        }
        for ((_, path) in toolchainsForVersion(majorVersion)) {
            if (path.exists()) {
                return RSuccess(Unit)
            }
        }
        return RFailure(MissingConfig(userMavenToolchains))
    }
}

/** Native path to ~/.m2/toolchains.xml */
expect val userMavenToolchains: NativePath

/** Given the numbered major version, lists pairs of version-numbers and JDK home values that match */
expect fun toolchainsForVersion(major: Int): List<Pair<String, NativePath>>

fun Path.isJdk() = listOf("jmods", "jre").any { resolve(it).exists() }

fun Path.readSymbolicLinks(): Sequence<Path> = sequence {
    var path = this@readSymbolicLinks
    yield(path)
    while (Files.isSymbolicLink(path)) {
        path = Files.readSymbolicLink(path)
        yield(path)
    }
}
