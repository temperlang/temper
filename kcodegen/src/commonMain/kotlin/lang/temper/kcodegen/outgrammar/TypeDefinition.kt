package lang.temper.kcodegen.outgrammar

import lang.temper.format.OutputToken

internal sealed class TypeDefinition
internal data class EnumDefinition(
    val name: Id,
    val members: List<Pair<Id, OutputToken?>>,
    val docComment: DocComment?,
) : TypeDefinition()
internal data class NodeTypeDefinition(
    val name: Id,
    val isConcrete: Boolean,
    val typeKind: TypeKind,
    val superTypes: List<KotlinCode>,
    val members: List<MemberDefinition>,
    val extraCode: KotlinCode?,
    val docComment: DocComment?,
) : TypeDefinition()
internal sealed class MemberDefinition {
    abstract val name: Id
    abstract val overrides: Boolean
    abstract fun appendTo(
        out: StringBuilder,
        linePrefix: String,
    )
}
internal data class PropertyDefinition(
    override val name: Id,
    override val overrides: Boolean,
    /**
     * The count determines whether its type is type, type? or `List<type>`.
     * The type may be null for inherited properties or where the type can be inferred from getter.
     */
    val type: PropertyTypeAndCount?,
    val getter: KotlinCode?,
    val setter: KotlinCode?,
    val containingTypeIsConcrete: Boolean,
    val containingTypeKind: TypeKind,
    val defaultExpression: KotlinCode?,
    val visibility: KotlinVisibility = KotlinVisibility.Default,
    val docComment: DocComment?,
) : MemberDefinition() {
    override fun appendTo(
        out: StringBuilder,
        linePrefix: String,
    ) = appendTo(out, linePrefix, asConstructorParameter = false)

    fun appendTo(
        out: StringBuilder,
        linePrefix: String,
        asConstructorParameter: Boolean,
    ) {
        if (docComment != null) {
            // We don't normally put newlines here, but linter wants them before doc comments.
            // And most cases where doc comments might appear, we put newlines anyway, so just handle this here.
            out.append('\n')
        }
        indentDocCommentTo(linePrefix, docComment, out)

        val useDeclarationKeywords = when {
            // We only generate one definition for Data types.
            // For non-computed properties in a class type, they're in the constructor list.
            // Otherwise, they're in the body.
            containingTypeKind == TypeKind.Data -> true
            // We need to emit `val`/`var` except when we're emitting a constructor parameter and
            // there's a setter that needs the value.
            asConstructorParameter && setter != null -> false
            else -> true
        }

        val getter = this.getter
        val type = this.type
        // We allow mutating if there is no getter.
        // But if the count is many, we're using a list type, so we instead
        // make it mutable by making the list a MutableList.
        val mutability = when {
            containingTypeKind == TypeKind.Data -> Mutability.Immutable
            getter != null -> Mutability.Immutable
            type?.count == PropertyCount.Many -> if (containingTypeIsConcrete) {
                Mutability.MutableType
            } else {
                Mutability.Immutable
            }
            containingTypeIsConcrete -> Mutability.VarDecl
            else -> Mutability.Immutable
        }

        out.append(linePrefix)
        if (useDeclarationKeywords) {
            if (overrides) { out.append("override ") }
            visibility.keyword?.let { keyword ->
                out.append(keyword).append(' ')
            }
            out.append(if (mutability == Mutability.VarDecl || setter != null) "var" else "val")
                .append(' ')
        }
        out.append(name.text)
        if (type != null) {
            val forCopy = asConstructorParameter && setter != null
            out.append(": ")
            out.append(type.toKotlinCode(mutability, forCopy).sourceText)
        }
        if (asConstructorParameter) {
            appendDefaultExpression(out, linePrefix)
        } else {
            if (getter != null) {
                out.append('\n')
                out.append(linePrefix)
                out.append("    get() =")
                val isMultilineGetter = '\n' in getter.sourceText
                if (isMultilineGetter) {
                    out.append('\n')
                    out.append(getter.indent("$linePrefix        "))
                } else {
                    out.append(' ')
                    out.append(getter.sourceText)
                }
            }
            if (setter != null) {
                out.append('\n')
                out.append(linePrefix)
                out.append("    set($SETTER_ARG_NAME) {")
                val isMultilineSetter = '\n' in setter.sourceText
                if (isMultilineSetter) {
                    out.append('\n')
                    out.append(setter.indent("$linePrefix        "))
                    out.append("\n$linePrefix    }")
                } else {
                    out.append(' ')
                    out.append(setter.sourceText)
                    out.append(" }")
                }
            }
            if (
                getter == null && setter == null &&
                name.text.startsWith(BACKED_PROPERTY_NAME_PREFIX)
            ) {
                // Initialize with a mutableList as appropriate.
                when (type?.count) {
                    null, PropertyCount.ZeroOrOne, PropertyCount.One -> Unit
                    PropertyCount.Many -> {
                        out.append(" = mutableListOf()")
                    }
                }
            }
        }
    }

    private fun appendDefaultExpression(out: StringBuilder, linePrefix: String) {
        if (defaultExpression != null) {
            out.append(" =")
            val isMultiline = '\n' in defaultExpression.sourceText
            if (isMultiline) {
                out.append('\n')
                out.append(defaultExpression.indent("$linePrefix    "))
            } else {
                out.append(' ')
                out.append(defaultExpression.sourceText)
            }
        }
    }
}
internal data class MethodDefinition(
    override val name: Id,
    override val overrides: Boolean,
    val params: List<Pair<Id, PropertyTypeAndCount>>,
    val returnType: PropertyTypeAndCount?,
    val body: KotlinCode?,
) : MemberDefinition() {
    override fun appendTo(
        out: StringBuilder,
        linePrefix: String,
    ) {
        out.append(linePrefix)
        if (overrides) { out.append("override ") }
        out.append("fun")
            .append(' ')
            .append(name.text)
            .append('(')
        params.forEach { param ->
            out.append('\n')
            out.append(linePrefix).append("    ")
            out.append(param.first.text)
            out.append(": ")
            val paramType = param.second
            out.append(paramType.toKotlinCode(Mutability.Immutable, false).sourceText)
            out.append(',')
        }
        if (params.isNotEmpty()) {
            out.append("\n$linePrefix")
        }
        out.append(')')
        if (returnType != null) {
            out.append(": ")
            out.append(
                returnType.toKotlinCode(Mutability.Immutable, false).sourceText,
            )
        }
        if (body != null) {
            out.append(" {\n")
            out.append(body.indent("$linePrefix    "))
            out.append('\n')
            out.append(linePrefix)
            out.append("}")
        }
    }
}

internal const val SETTER_ARG_NAME = "newValue"

internal enum class KotlinVisibility(val keyword: String?) {
    Default(null),
    Private("private"),
}
