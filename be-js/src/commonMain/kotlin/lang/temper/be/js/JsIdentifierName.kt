package lang.temper.be.js

data class JsIdentifierName(val text: String) {
    init {
        require(JsIdentifierGrammar.isIdentifierName(text)) {
            "Identifier would have been: $text"
        }
    }

    companion object {
        /** Escape reserved words for safety or names already ending in "_" for uniqueness. */
        fun escaped(text: String): JsIdentifierName = when {
            // TODO Allow user override of name choice.
            text in jsReservedWords || text.endsWith("_") -> "${text}_"
            JsIdentifierGrammar.isIdentifierName(text) -> when {
                text.endsWith("_") -> "${text}_"
                else -> text
            }
            else -> JsIdentifierGrammar.massageJsIdentifier(text).ifEmpty { "_" }
        }.let { JsIdentifierName(it) } // TODO Avoid duplicate isIdentifierName check?
    }
}

val globalThisName = JsIdentifierName("globalThis")
