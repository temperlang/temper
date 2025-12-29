package lang.temper.kcodegen.outgrammar

internal interface Commentable {
    var docComment: DocComment?
}

internal fun maybeAssociateDocComment(docComment: DocComment?, commentable: Commentable) {
    if (docComment != null && commentable.docComment == null) {
        commentable.docComment = docComment
    }
}
