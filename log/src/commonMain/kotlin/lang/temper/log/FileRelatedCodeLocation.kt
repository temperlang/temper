package lang.temper.log

/**
 * A [CodeLocation] that is associated with a file.
 */
interface FileRelatedCodeLocation : CodeLocation {
    /** The source file for the code in the location. */
    val sourceFile: FilePath
}
