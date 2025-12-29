package lang.temper.be.py

@Suppress("DataClassPrivateConstructor")
@ConsistentCopyVisibility
data class PyIdentifierName private constructor(val text: String) : Comparable<PyIdentifierName> {

    override fun toString(): String = text

    companion object {
        operator fun invoke(text: String): PyIdentifierName = PyIdentifierName(check(text))
        fun check(text: String): String {
            require(PyIdentifierGrammar.isIdentifierName(text)) { "Not an identifier: '$text'" }
            return text
        }
    }

    override fun compareTo(other: PyIdentifierName): Int = this.text.compareTo(other.text)
}
