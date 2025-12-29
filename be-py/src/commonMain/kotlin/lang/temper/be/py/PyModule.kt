package lang.temper.be.py

import lang.temper.be.Backend
import lang.temper.be.py.PyIdentifierGrammar.scrubNonIdentifierParts
import lang.temper.common.dequeIterator
import lang.temper.log.FilePath
import lang.temper.log.FilePathSegment
import lang.temper.log.plus
import lang.temper.name.OutName
import lang.temper.value.DependencyCategory

/**
 * A layout data structure that lets us put AST nodes into python modules and figures out the __init__.py for us.
 */
class PyModule(var moduleId: PyDottedIdentifier?) : Iterable<PyModule> {
    var program: Py.Program? = null
        private set
    private val modules: MutableMap<OutName, PyModule> = mutableMapOf()
    val exports: MutableSet<OutName> = mutableSetOf()
    val imports: MutableSet<OutName> = mutableSetOf()
    internal val support: MutableSet<Support> = mutableSetOf()

    operator fun get(name: OutName): PyModule {
        PyIdentifierName(name.outputNameText)
        return modules.getOrPut(name) { PyModule(moduleId.dot(name)) }
    }

    /** Intended for a top level module, this sets a program based on its path. */
    fun setProgram(program: Py.Program): PyModule {
        val path = program.outputPath
        var node = this
        for (seg in path.segments) {
            node = node[safeModuleName(seg.baseName)]
        }
        node.program = program
        return node
    }

    fun analyzeImports(excludeImportsFromExports: Boolean) {
        val exports: MutableSet<OutName> = mutableSetOf()
        val exportDrops: MutableSet<OutName> = mutableSetOf()
        val imports: MutableSet<OutName> = mutableSetOf()
        program?.let { prog ->
            prog.gatherExports(null, excludeImports = excludeImportsFromExports) { name, delete ->
                name.asSimpleName()!!.let {
                    if (delete) {
                        exportDrops.add(it)
                    } else {
                        exports.add(it)
                    }
                }
            }
            prog.gatherImports(exports) { name ->
                imports.add(name)
            }
        }
        this.imports.clear()
        this.imports.addAll(imports)
        this.exports.clear()
        this.exports.addAll(exports)
        this.exports.removeAll(exportDrops)
    }

    /**
     * Writes out an individual module, creating intervening directories as needed.
     *
     * Example:
     * ```
     * Given: this =
     * PyM(id = "project",
     *     program = null,
     *     modules = {
     *         foo: PyM(id = "project.foo", program = 'def foo(): ...', modules = {})
     *         bar: PyM(id = "project.bar", program = 'def bar(): ...', modules = {
     *            qux: PyM(id = "project.bar.qux", program = 'def qux(): ...', modules={})
     *         })
     *     })
     *
     * this.write(dir) creates:
     *   OutDir(dir/project), EMPTY_PROGRAM
     *   OutRegularFile(dir/project/__init__.py), EMPTY_PROGRAM
     *
     * this["foo"].write(dir) creates:
     *   OutRegularFile(dir/project/foo.py), Py.Program('def foo(): ...')
     *
     * this["bar"].write(dir) creates:
     *   OutDir(dir/project/bar)
     *   OutRegularFile(dir/project/bar/__init__.py), Py.Program('def bar(): ...')
     * ```
     */
    fun write(): PyFile {
        val modulePath = moduleId?.absModulePath() ?: FilePath.emptyPath
        val dir = modulePath.dirName()
        val lastPart = modulePath.segments.last()
        val file = if (modules.isNotEmpty() || modulePath.segments.size <= 1) {
            // Output a __init__ if it's just for the top-level library name or there are source files under it.
            dir.resolve(lastPart, isDir = true)
                .resolve(dunderInitPyFileName, isDir = false)
        } else {
            // Can be represented as a simple name.py module.
            dir + lastPart.withExtension(PyBackend.fileExtension)
        }
        return PyFile(program, moduleId, file)
    }

    override fun iterator(): Iterator<PyModule> = dequeIterator { deque ->
        deque.removeFirst().also { m -> deque.addAll(m.modules.values) }
    }

    /** Save support codes requested by the module for the linking phase. */
    fun saveSupport(trans: PyTranslator, dependencyCategory: DependencyCategory) {
        trans.withDependencyMode(dependencyCategory) {
            trans.support().forEach { sc -> support.add(Support(sc, trans.pyNames.supportCodeName(sc), false)) }
        }
    }

    /** Save shared support codes requested by the module for the linking phase. */
    fun saveSharedSupport(trans: PyTranslator, dependencyCategory: DependencyCategory) {
        trans.withDependencyMode(dependencyCategory) {
            trans.sharedSupport().forEach { sc -> support.add(Support(sc, trans.pyNames.supportCodeName(sc), true)) }
        }
    }
}

fun toModuleFileName(name: String): String {
    return avoidReserved(scrubNonIdentifierParts(name))
}

fun safeModuleName(name: String) =
    OutName(PyIdentifierName.check(toModuleFileName(FilePathSegment(name).baseName)), null)

fun safeModuleFileName(segment: FilePathSegment) =
    FilePathSegment(toModuleFileName(segment.baseName)).withExtension(PyBackend.fileExtension)

fun safeForImportFilePath(unsafeFilePath: FilePath): FilePath {
    return FilePath(
        unsafeFilePath.segments.map {
            FilePathSegment(toModuleFileName(it.baseName))
        },
        isDir = unsafeFilePath.isDir,
    )
}

/**
 * Identify each program and its module identifier.
 */
data class PyFile(val program: Py.Program?, val id: PyDottedIdentifier?, val file: FilePath) {
    fun toTreeFile() =
        Backend.TranslatedFileSpecification(file, PyBackend.mimeType, program.orEmpty(file))
}

internal data class Support(val supportCode: PySupportCode, val name: OutName, val shared: Boolean)

/** The name of a python package `__init__` file. */
const val DUNDER_INIT = "__init__"
internal val dunderInitPyFileName = FilePathSegment("$DUNDER_INIT.py")
