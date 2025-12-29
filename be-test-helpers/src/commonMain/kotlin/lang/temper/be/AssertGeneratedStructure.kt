package lang.temper.be

import lang.temper.common.ListBackedLogSink
import lang.temper.common.Log
import lang.temper.common.assertStructure
import lang.temper.common.console
import lang.temper.common.structure.Structured
import lang.temper.fs.fileTreeStructure
import lang.temper.lexer.Genre
import lang.temper.log.FilePath
import lang.temper.log.FilePositions
import lang.temper.name.BackendId
import lang.temper.name.ProbableNameMatcher
import lang.temper.name.PseudoCodeNameRenumberer
import lang.temper.name.defaultProbableNameMatcher
import lang.temper.supportedBackends.lookupFactory as defaultLookupFactory

fun <BACKEND : Backend<BACKEND>> assertGeneratedStructure(
    inputs: List<Pair<FilePath, String>>,
    factory: Backend.Factory<BACKEND>,
    backendConfig: Backend.Config,
    genre: Genre,
    moduleResultNeeded: Boolean,
    postProcess: (Structured) -> Structured = { it },
    lookupFactory: (BackendId) -> Backend.Factory<*>? = factoryFinder(factory),
    assertion: (Structured) -> Unit,
) {
    val logSink = ListBackedLogSink()
    val result = generateCode(
        inputs = inputs,
        backendConfig = backendConfig,
        factory = factory,
        genre = genre,
        moduleResultNeeded = moduleResultNeeded,
        logSink = logSink,
        lookupFactory = lookupFactory,
    )
    logSink.toConsole(
        console,
        Log.Warn,
        inputs.associate {
            it.first to (it.second to FilePositions.fromSource(it.first, it.second))
        },
    )

    val outputWithErrors = logSink.wrapErrorsAround(result.fileTreeStructure())
    val outputProcessed = postProcess(outputWithErrors)

    // check that test completed by looking at the written content.
    assertion(outputProcessed)
}

fun <BACKEND : Backend<BACKEND>> assertGeneratedCode(
    inputs: List<Pair<FilePath, String>>,
    want: String,
    factory: Backend.Factory<BACKEND>,
    backendConfig: Backend.Config,
    probableNameMatcher: ProbableNameMatcher = defaultProbableNameMatcher,
    moduleResultNeeded: Boolean = false,
    postProcess: (Structured) -> Structured = { it },
    genre: Genre = Genre.Library,
    lookupFactory: (BackendId) -> Backend.Factory<*>? = factoryFinder(factory),
) {
    assertGeneratedStructure(
        inputs = inputs,
        factory = factory,
        backendConfig = backendConfig,
        postProcess = postProcess,
        genre = genre,
        moduleResultNeeded = moduleResultNeeded,
        lookupFactory = lookupFactory,
    ) { outputRoot ->
        // check that test completed by looking at the written content.
        assertStructure(
            want,
            outputRoot,
            postProcessor = { s ->
                PseudoCodeNameRenumberer.newStructurePostProcessor(probableNameMatcher)(s)
            },
        )
    }
}

/**
 * Looks up [backend factories][Backend.Factory] by [BackendId] but if the request
 * is for the given factory returns that.
 * This lets us avoid class-loading any plugin machinery if we already have the factories we need,
 * so most backend's tests run without a dependency on :bundled-backends.
 */
fun factoryFinder(factory: Backend.Factory<*>): (BackendId) -> Backend.Factory<*>? {
    return { id ->
        if (factory.backendId == id) {
            factory
        } else {
            defaultLookupFactory(id)
        }
    }
}
