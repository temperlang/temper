package lang.temper.be

import lang.temper.be.tmpl.TmpL
import lang.temper.common.putMultiList
import lang.temper.common.putMultiSet
import lang.temper.common.transitiveClosure
import lang.temper.library.LibraryConfigurationsBundle
import lang.temper.log.FilePathAndMimeTypeOrNull
import lang.temper.name.BackendId
import lang.temper.name.DashedIdentifier
import lang.temper.name.ModuleName

private typealias LibraryName = DashedIdentifier

/**
 * A type safe key into a [BackendMetadataMap].
 *
 * [BACKEND] may define an `object` and then code that implements shell commands for
 * *temper {run,test,repl}* for that backend can retrieve structured information
 * stored in a metadata map it receives.
 */
@Suppress("UnnecessaryAbstractClass") // Can't be interface with internal fun.
abstract class MetadataKey<BACKEND : Backend<BACKEND>, VALUE_TYPE> {
    /** The backend id for the given backend.  Used to make sure metadata-maps silo things properly */
    abstract val backendId: BackendId

    internal fun castAssociatedValue(x: Any): VALUE_TYPE {
        // Safe as long as it's used on a pair associated in a [BackendMetadataMap]
        @Suppress("UNCHECKED_CAST")
        return x as VALUE_TYPE
    }
}

interface MetadataKeyFactory<VALUE_TYPE> {
    fun <BACKEND : Backend<BACKEND>>acquireKey(backend: Backend<BACKEND>): MetadataKey<BACKEND, VALUE_TYPE>
}

/**
 * An extensible collection of information provided by a backend that may be
 * used by backend specific shell command code.
 *
 * For example, the JS backend might store a Kotlin value representing
 * `package.json` so that the code that backs `temper test -b js` does not
 * need to parse the generated `package.json`.
 */
class BackendMetadataMap<BACKEND : Backend<BACKEND>>(
    private val values: Map<Pair<LibraryName, BackendId>, Map<MetadataKey<BACKEND, *>, Any>>,
) {
    operator fun <VALUE_TYPE> get(
        libraryName: LibraryName,
        key: MetadataKey<BACKEND, VALUE_TYPE>,
    ): VALUE_TYPE? =
        values[libraryName to key.backendId]?.get(key)?.let {
            key.castAssociatedValue(it)
        }
}

/**
 * Information about dependencies between generated files.
 */
class Dependencies<BACKEND : Backend<BACKEND>>(
    val libraryConfigurations: LibraryConfigurationsBundle,
    /**
     * Non-transitive dependencies.
     * Could provide a map computed from this with the transitive stuff.
     * @see transitiveDependencies
     */
    val shallowDependencies: Map<LibraryName, Set<LibraryName>>,
    /**
     * Backend specific metadata.
     * For example, If the JS backend wants to include information like the
     * package.json as a programmatic object, we can.
     */
    val metadata: BackendMetadataMap<BACKEND>,
    // The file list can be auto-derived from the OutputFileSpecifications, but comes in handy for copy-all operations.
    val filesPerLibrary: Map<LibraryName, Set<FilePathAndMimeTypeOrNull>>,
    /**
     * Allowed to be optional for backends that haven't implemented this yet.
     * Null distinguishes missing information from a known lack from tests.
     */
    val tests: Map<LibraryName, List<TestInfo>>? = null,
) {
    private var _transitiveDependencies: Map<LibraryName, Set<LibraryName>>? = null

    /** The transitive closure of [shallowDependencies] */
    val transitiveDependencies: Map<LibraryName, Set<LibraryName>>
        get() = _transitiveDependencies ?: run {
            val deep = transitiveClosure(shallowDependencies)
            _transitiveDependencies = deep
            deep
        }

    class Builder<BACKEND : Backend<BACKEND>>(
        val libraryConfigurations: LibraryConfigurationsBundle,
    ) {
        private var cached: Dependencies<BACKEND>? = null
        private val shallowDependencies = mutableMapOf<LibraryName, MutableSet<LibraryName>>()

        @Synchronized
        fun addDependency(from: LibraryName, to: LibraryName) {
            cached = null
            shallowDependencies.putMultiSet(from, to)
        }

        private val metadata =
            mutableMapOf<Pair<LibraryName, BackendId>, MutableMap<MetadataKey<BACKEND, *>, Any>>()

        @Synchronized
        fun <VALUE> addMetadata(
            libraryName: LibraryName,
            key: MetadataKey<BACKEND, VALUE>,
            value: VALUE,
        ) {
            cached = null
            metadata.getOrPut(libraryName to key.backendId) { mutableMapOf() }[key] = value as Any
        }

        @Synchronized
        fun <VALUE> getMetadata(libraryName: LibraryName, key: MetadataKey<BACKEND, VALUE>): VALUE? =
            metadata[libraryName to key.backendId]?.get(key)?.let { key.castAssociatedValue(it) }

        private val filesPerLibrary = mutableMapOf<LibraryName, MutableSet<FilePathAndMimeTypeOrNull>>()

        @Synchronized
        fun addFile(libraryName: LibraryName, filePathAndMimeTypeOrNull: FilePathAndMimeTypeOrNull) {
            cached = null
            filesPerLibrary.putMultiSet(libraryName, filePathAndMimeTypeOrNull)
        }

        private var tests: MutableMap<LibraryName, MutableList<TestInfo>>? = null

        @Synchronized
        fun addTest(libraryName: LibraryName, testInfo: TestInfo) {
            cached = null
            if (tests == null) {
                tests = mutableMapOf()
            }
            tests!!.putMultiList(libraryName, testInfo)
        }

        /** Convenience for common usage. */
        fun addTest(libraryName: LibraryName?, test: TmpL.Test, backendName: String? = null) {
            var anc: TmpL.Tree = test
            while (anc !is TmpL.Module) {
                anc = anc.parent as TmpL.Tree? ?: break
            }
            val moduleName = (anc as? TmpL.Module)?.codeLocation?.codeLocation
            if (libraryName != null && moduleName != null) {
                val testInfo = TestInfo(
                    backendName = backendName ?: test.rawName,
                    moduleName = moduleName,
                    temperName = test.rawName,
                )
                addTest(libraryName, testInfo)
            }
        }

        @Synchronized
        fun build(): Dependencies<BACKEND> =
            when (val cached = this.cached) {
                null -> {
                    val created = Dependencies(
                        libraryConfigurations = libraryConfigurations,
                        shallowDependencies = shallowDependencies.mapValues { it.value.toSet() },
                        metadata = BackendMetadataMap(
                            metadata.mapValues { it.value.toMap() },
                        ),
                        filesPerLibrary = filesPerLibrary.mapValues { it.value.toSet() },
                        tests = tests?.mapValues { it.value.toList() },
                    )
                    this.cached = created
                    created
                }
                else -> cached
            }
    }
}

/** Metadata about a test. */
data class TestInfo(
    /** The backend-specific name for the test that will appear on test reports. */
    val backendName: String,
    /** Might be useful for restraining which tests are run. */
    val moduleName: ModuleName,
    /** The string name for this test as specified in Temper source. */
    val temperName: String,
)
