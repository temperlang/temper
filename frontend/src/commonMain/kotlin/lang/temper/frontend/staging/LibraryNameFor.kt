package lang.temper.frontend.staging

import lang.temper.frontend.Module
import lang.temper.library.LibraryConfiguration
import lang.temper.log.FilePath
import lang.temper.log.FileRelatedCodeLocation
import lang.temper.name.DashedIdentifier
import lang.temper.name.ModuleName
import lang.temper.value.TString

/** Returns null when no valid name is found. */
internal fun libraryNameFromMarkdown(
    textContent: String,
): DashedIdentifier? {
    val firstLine = run {
        var lineEnd = textContent.length
        for (i in textContent.indices) {
            val c = textContent[i]
            if (c == '\n' || c == '\r') {
                lineEnd = i
                break
            }
        }
        textContent.substring(0, lineEnd)
    }

    return DashedIdentifier.from(firstLine)
}

/** Returns a default name when no valid name is found. */
fun libraryNameWithDefault(textContent: String, module: Module?, rootPath: FilePath? = null): DashedIdentifier =
    libraryNameForModule(module) {
        impliedLibraryName(textContent, module?.loc as? ModuleName ?: rootPath)
    }

fun libraryNameForModule(module: Module?, fallback: () -> DashedIdentifier?): DashedIdentifier {
    // Try the most explicit option first.
    val nameExport = module?.exports?.find { it.name.baseName == LibraryConfiguration.libraryNameParsedName }
    val explicitName = nameExport?.let { TString.unpackOrNull(it.value) }?.let { DashedIdentifier.from(it) }
    if (explicitName != null) { return explicitName }
    // But be willing to guess from other hints so we can proceed.
    // TODO Give an error/warning but proceed for any of the backup strategies?
    return fallback() ?: fallbackLibraryName()
}

/**
 * Tries to find a library name when none is explicitly exported
 */
fun impliedLibraryName(textContent: String, loc: FileRelatedCodeLocation?): DashedIdentifier? =
    libraryNameFromMarkdown(textContent)
        ?: run {
            val path = when (loc) {
                null -> null
                is ModuleName -> loc.libraryRoot()
                else -> loc.sourceFile
            }
            if (path != null && path.segments.isNotEmpty()) {
                DashedIdentifier.from(path.segments.joinToString("-") { it.baseName })
            } else {
                null
            }
        }

fun fallbackLibraryName() = DashedIdentifier.from(UNNAMED_LIBRARY_NAME)!!

const val UNNAMED_LIBRARY_NAME = "unnamed"
