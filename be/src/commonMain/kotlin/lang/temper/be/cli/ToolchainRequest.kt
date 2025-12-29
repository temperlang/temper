package lang.temper.be.cli

import lang.temper.be.Dependencies
import lang.temper.be.util.ConfigFromCli
import lang.temper.library.LibraryConfigurations
import lang.temper.log.FilePath
import lang.temper.log.FilePathSegment
import lang.temper.name.BackendId
import lang.temper.name.DashedIdentifier

/**
 * Packages up info needed to shell out to a target language toolchain that
 * operates on sources or binaries translated from Temper.
 *
 * @see RunnerSpecifics.runBestEffort
 */
sealed interface ToolchainRequest {
    /**
     * Identifier that describes the task.
     * When running a functional test, this is the test name.
     */
    val taskName: String

    /** Some requests can be performed on multiple backends. */
    fun specializeForBackend(
        backendId: BackendId,
        dependencies: Dependencies<*>,
    ): ToolchainRequest?
}

/**
 * A request that loads the translated version of a library for load side effects
 * as via `temper run --backend <some-backend> --library <some-library>`.
 */
data class RunLibraryRequest(
    val libraryName: DashedIdentifier,
    override val taskName: String = "run",
) : ToolchainRequest {
    override fun specializeForBackend(
        backendId: BackendId,
        dependencies: Dependencies<*>,
    ): RunLibraryRequest = this
}

/** A request that runs tests for one or more libraries à la `temper test <some-library>`. */
data class RunTestsRequest(
    /** The libraries to test.  `null` is a placeholder for: all libraries under the workspace root. */
    val libraries: Set<DashedIdentifier>?,
    val testFileGroups: List<TestFileGroup> = emptyList(),
    /* could expand with a test filter predicate here */
    override val taskName: String = "test",
) : ToolchainRequest {
    override fun specializeForBackend(
        backendId: BackendId,
        dependencies: Dependencies<*>,
    ): RunTestsRequest {
        val libraries = this.libraries ?: dependencies.libraryConfigurations.byLibraryName.keys
        return copy(
            libraries = libraries,
            testFileGroups = libraries.map { libraryName ->
                TestFileGroup(
                    libraryName = libraryName,
                    outputFileRoot = FilePath(
                        segments = listOf(
                            FilePathSegment(backendId.uniqueId),
                            FilePathSegment(libraryName.text),
                        ),
                        isDir = true,
                    ),
                    outputTestFiles = dependencies.filesPerLibrary[libraryName]
                        ?.map { it.filePath } // TODO: filter by an is-test bit?
                        ?.toSet()
                        ?: emptySet(),
                )
            },
        )
    }

    data class TestFileGroup(
        val libraryName: DashedIdentifier,
        val outputFileRoot: FilePath,
        val outputTestFiles: Set<FilePath>,
    )
}

/**
 * A request that starts up an interactive shell for a target language,
 * preloaded with some Temper modules' exports à la `temper test -b <backend-id> <some-module>`.
 */
data class ExecInteractiveRepl(
    val config: ConfigFromCli,
    override val taskName: String = "repl",
) : ToolchainRequest {
    override fun specializeForBackend(
        backendId: BackendId,
        dependencies: Dependencies<*>,
    ) = this
}

/**
 * A request by a backend to invoke some tool-chain element during module staging.
 * This may be used, for example, to reformat generated sources, generate binaries,
 * or edit debug metadata to point to Temper sources instead of generated sources.
 *
 * It is each backend's responsibility to only issue requests that the [Specifics]
 * they provide are able to service.
 *
 * @see lang.temper.be.Backend.preWrite
 * @see lang.temper.be.Backend.collate
 */
interface RunBackendSpecificCompilationStepRequest : ToolchainRequest {
    val libraryConfigurations: LibraryConfigurations
}
