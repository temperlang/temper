package lang.temper.frontend

import lang.temper.common.ContentHash
import lang.temper.cst.ConcreteSyntaxTree
import lang.temper.cst.CstComment
import lang.temper.fs.FileSystemSnapshot
import lang.temper.lexer.LanguageConfig
import lang.temper.lexer.TokenSource
import lang.temper.log.CodeLocation
import lang.temper.log.FilePath
import lang.temper.log.FilePositions
import lang.temper.log.Position
import lang.temper.log.UnknownCodeLocation
import lang.temper.value.BlockTree
import lang.temper.value.LinearFlow

/**
 * Modules can be formed from multiple source files. This represents all the
 * information that a single source file might present before merging happens
 * after parse stage.
 */
data class ModuleSource(
    val comments: List<CstComment>? = null,

    val cst: ConcreteSyntaxTree? = null,

    val fetchedContent: CharSequence? = null,

    /**
     * May disagree with the hash of the UTF-8 encoding of [fetchedContent] when a
     * source file is segmented into a preface and body.
     */
    val contentHash: ContentHash? = null,

    /** May be used to re-fetch content for error messages snippets. */
    val snapshot: FileSystemSnapshot? = null,

    val filePath: FilePath? = null,

    val filePositions: FilePositions? = null,

    val languageConfig: LanguageConfig? = null,

    val tokenSource: TokenSource? = null,

    val tree: BlockTree? = null,
)

/**
 * Return a tree made by combining all the trees from each source. Each source
 * must have a tree already. If only one source, return its tree unchanged.
 */
fun mergeTrees(sources: List<ModuleSource>, loc: CodeLocation? = null): BlockTree {
    val first = sources.first().tree!!
    // TODO New document?
    val destDocument = first.document
    val tree = when (sources.size) {
        1 -> first
        else -> {
            // Merge top-levels from all trees.
            // Track outer bounds, even across files, because it provides some constraint vs source files.
            var left = Int.MAX_VALUE
            var right = Int.MIN_VALUE
            val allTops = buildList {
                for (source in sources) {
                    val tree = source.tree!!
                    left = minOf(left, tree.pos.left)
                    right = maxOf(right, tree.pos.right)
                    for (top in tree.children) {
                        // Copy into the common document.
                        // TODO If we're ok retaining one of the current documents, avoid copies for tops of that tree?
                        add(top.copy(destDocument = destDocument))
                    }
                }
            }
            // TODO Separate merged document from any source?
            BlockTree(
                document = first.document,
                pos = Position(loc ?: UnknownCodeLocation, left = left, right = right),
                children = allTops,
                flow = LinearFlow,
            )
        }
    }
    return tree
}
