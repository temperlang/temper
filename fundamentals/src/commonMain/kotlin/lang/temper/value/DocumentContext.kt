package lang.temper.value

import lang.temper.common.AtomicCounter
import lang.temper.lexer.Genre
import lang.temper.log.ConfigurationKey
import lang.temper.log.FilePath
import lang.temper.log.FilePositions
import lang.temper.log.FileRelatedCodeLocation
import lang.temper.log.Position
import lang.temper.log.SharedLocationContext
import lang.temper.log.toReadablePosition
import lang.temper.name.NamingContext

/**
 * Some bits and bobs related to the owner of a document meant to allow safe cross-document
 * interactions.
 */
interface DocumentContext : ConfigurationKey.Holder {
    /** A context for looking up information about compilation units */
    val sharedLocationContext: SharedLocationContext

    /** A mutation counter for definitions local to the document. */
    val definitionMutationCounter: AtomicCounter

    /** A context for names local to the document. */
    val namingContext: NamingContext

    /** The genre for code represented by the document. */
    val genre: Genre

    /** Whether for production or test code. */
    val dependencyCategory: DependencyCategory

    /** Useful for debugging */
    val filePositions: Map<FilePath, FilePositions> get() = emptyMap()

    /** Useful for debugging. */
    fun formatPosition(pos: Position): String {
        val sourceFile = (pos.loc as? FileRelatedCodeLocation)?.sourceFile
        if (sourceFile != null) {
            val positions = filePositions[sourceFile]
            positions?.spanning(pos)?.toReadablePosition(sourceFile.diagnostic)?.let {
                return it
            }
        }
        return "$pos"
    }
}

/**
 * An externally configurable context with respect to [dependencyCategory].
 * Specific rules might apply that prevent changes under some conditions.
 */
interface DependencyCategoryConfigurable {
    var dependencyCategory: DependencyCategory
}
