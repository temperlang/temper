package lang.temper.kcodegen.outgrammar

import lang.temper.format.OutputToken
import lang.temper.format.OutputTokenType
import lang.temper.log.Position
import lang.temper.log.Positioned

internal sealed interface PropertyType : Positioned {
    fun toKotlinCode(
        count: PropertyCount,
        mutability: Mutability,
        forCopy: Boolean,
    ): KotlinCode = when (count) {
        PropertyCount.One -> toKotlinCode()
        PropertyCount.ZeroOrOne -> KotlinCode("${toKotlinCode().sourceText}?", pos)
        PropertyCount.Many -> {
            val listType = when {
                forCopy -> "Iterable"
                mutability == Mutability.MutableType -> "MutableList"
                else -> "List"
            }
            KotlinCode("$listType<${toKotlinCode().sourceText}>", pos)
        }
    }

    fun toKotlinCode(): KotlinCode

    fun resolve(): PropertyType = this
}

internal sealed interface SuperType

internal sealed interface NamedPropertyType : PropertyType, Positioned {
    val name: Id
    override fun toKotlinCode(): KotlinCode = KotlinCode(name.text, pos)
}

internal class NodeTypeReference(
    override val pos: Position,
    val nodeType: NodeType,
) : Positioned, SuperType {
    override fun toString(): String = "${nodeType.name}"
}

internal class NodeType(
    override val name: Id,
    override val pos: Position,
) : NamedPropertyType, Commentable {
    var derivation: NodeTypeDerivation? = null
    var typeKind: TypeKind? = null
    var syntaxDeclaration: SyntaxDeclaration? = null
    val superTypes = mutableListOf<SuperType>()
    val enumeratedSubTypes = mutableListOf<NodeTypeReference>()
    val requirements = mutableListOf<Requirement>()
    var renderTo: KotlinCode? = null
    var extraBodyContent: KotlinCode? = null
    val localProperties = mutableMapOf<Id, Property>()
    var operatorDefinition: KotlinCode? = null
    val propertyOverrides = mutableMapOf<Id, KotlinCode>()
    val defaultExpressions = mutableMapOf<Id, KotlinCode>()
    override var docComment: DocComment? = null

    /**
     * Whether the node type is ever explicitly declared, not just it's existence implied.
     *
     * [GrammarProcessor] sets this for any statement that lexically starts with Type.
     * The `from` derivations also set this bit for types derived from an explicitly declared
     * type.
     */
    var isExplicitlyDeclared = false

    override fun toString(): String {
        return "NodeType($name)"
    }

    val nameWithKind
        get() = when (typeKind) {
            null -> "?${name.text}"
            TypeKind.Ast -> name.text
            TypeKind.Data -> "data ${name.text}"
        }
}

/** How one node type is equivalent to another of a different [TypeKind] */
internal sealed class NodeTypeDerivation {
    abstract val sourceReference: NodeTypeReference
}

/** The content of the node type is derived from the referenced node type. */
internal data class DerivesFrom(override val sourceReference: NodeTypeReference) : NodeTypeDerivation()

/** The content of the node type is equivalent to but is defined separately from the referenced node type. */
internal data class EquivalentTo(override val sourceReference: NodeTypeReference) : NodeTypeDerivation()

internal data class EnumType(
    override val name: Id,
    override val pos: Position,
    var members: List<Pair<Id, OutputToken?>>? = null,
) : NamedPropertyType, Commentable {
    override var docComment: DocComment? = null

    override fun toString(): String {
        return "EnumType($name)"
    }

    companion object {
        val emptyToken = OutputToken("", OutputTokenType.Comment)
    }
}

internal data class UnknownType(
    override val name: Id,
    override val pos: Position,
    var resolution: PropertyType? = null,
) : NamedPropertyType {
    override fun resolve() = resolution ?: this
}

internal class KotlinCode(
    val sourceText: String,
    override val pos: Position,
) : PropertyType, SuperType {
    override fun toKotlinCode(): KotlinCode = this

    fun indent(linePrefix: String): String {
        var lastWasBlank = true
        return sourceText.split(newlinePattern).joinToString("\n") {
            if (it.isNotBlank()) {
                // For linter, put a blank line before doc comments.
                val extraBlank = when (it.startsWith("/**") && !lastWasBlank) {
                    true -> "\n"
                    false -> ""
                }
                lastWasBlank = false
                "$extraBlank$linePrefix$it"
            } else {
                lastWasBlank = true
                ""
            }
        }
    }

    // pos does not affect equality
    override fun equals(other: Any?): Boolean = other is KotlinCode && sourceText == other.sourceText
    override fun hashCode(): Int = sourceText.hashCode()

    override fun toString(): String = sourceText
}

internal fun Pair<String, Position>.toKotlinCode() = KotlinCode(first, second)

internal enum class TypeKind {
    Ast,
    Data,
    ;

    /**
     * When deriving a node of one type kind from another type kind,
     * this is the suffix we strip off the end of the source type's name if present.
     *
     * FooTree -> the prefix Foo
     *
     * Then we can add the [required suffix][requiredDerivationSuffix] to get, for example FooData.
     */
    fun optionalDerivationSuffix() = when (this) {
        Ast -> "Tree"
        Data -> "Data"
    }

    fun requiredDerivationSuffix() = when (this) {
        Ast -> ""
        Data -> "Data"
    }
}

internal val TypeKind?.orDefault get() = this ?: TypeKind.Ast
