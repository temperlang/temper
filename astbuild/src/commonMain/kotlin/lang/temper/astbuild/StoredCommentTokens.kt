package lang.temper.astbuild

import lang.temper.common.binarySearch
import lang.temper.common.putMultiList
import lang.temper.cst.CstComment
import lang.temper.log.CodeLocation
import lang.temper.log.Position

interface StoredCommentTokens {
    fun commentsBetween(before: Position, after: Position): List<CstComment>

    companion object {
        val empty: StoredCommentTokens = NullStoredCommentTokens

        operator fun invoke(comments: Iterable<CstComment>): StoredCommentTokens =
            if (comments.iterator().hasNext()) {
                StoredCommentsTokensImpl(comments)
            } else {
                empty
            }
    }
}

private class StoredCommentsTokensImpl(
    comments: Iterable<CstComment>,
) : StoredCommentTokens {
    private val commentsByLocation: Map<CodeLocation, List<CstComment>>
    init {
        val m = mutableMapOf<CodeLocation, MutableList<CstComment>>()
        comments.forEach {
            m.putMultiList(it.pos.loc, it)
        }
        // Sort lists so that we can bin-search into them.
        m.entries.forEach {
            it.value.sortBy { comment -> comment.pos.left }
        }
        commentsByLocation = m.mapValues { it.value.toList() }
    }

    override fun commentsBetween(before: Position, after: Position): List<CstComment> {
        val loc = before.loc
        if (after.loc != loc) { return emptyList() }
        val commentList = commentsByLocation[loc] ?: return emptyList()
        val left = before.pos.right
        val right = after.pos.left
        if (left > right) { return emptyList() }
        var searchResult = binarySearch(commentList, left) { comment, posIndex ->
            comment.pos.left.compareTo(posIndex)
        }
        if (searchResult < 0) { searchResult = searchResult.inv() }
        val between = mutableListOf<CstComment>()
        var i = searchResult
        while (i in commentList.indices) {
            val comment = commentList[i]
            val pos = comment.pos
            if (pos.left in left until right && pos.right in left..right) {
                between.add(comment)
                i += 1
            } else {
                break
            }
        }
        return between.toList()
    }
}

private object NullStoredCommentTokens : StoredCommentTokens {
    override fun commentsBetween(before: Position, after: Position): List<CstComment> =
        emptyList()
}
