package lang.temper.docgen.transformations

import lang.temper.docgen.Document

interface Transformer {
    /**
     * Applies a transformation to [document]
     */
    fun transform(document: Document<*, *, *>)
}
