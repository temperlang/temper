package lang.temper.kcodegen.outgrammar

import lang.temper.common.partiallyOrder
import lang.temper.common.putMulti
import lang.temper.common.toStringViaBuilder
import lang.temper.format.CodeFormattingTemplate
import lang.temper.format.OutputToken
import lang.temper.format.OutputTokenType
import lang.temper.log.MessageTemplate

internal fun digestFormatString(
    nodeType: NodeType,
): FormatStringDigest? {
    val syntaxDeclaration = nodeType.syntaxDeclaration ?: return null

    val syntaxPaths = syntaxAnalysis(syntaxDeclaration)

    // We don't want all the declared properties to show up when we iterate children.
    // {{*:,}} needs to match only properties that correspond to node types and which
    // are used in the syntax.  It should not include computed properties provided for
    // convenience.
    val usedInSyntaxUnordered = syntaxPaths.flatMap { it.propertiesUsed }.toSet()

    // Keep track of which properties appear before each other in a syntax path so that we
    // can produce an intuitive order to receive constructor parameters.
    // In a syntax declaration like
    //     (a || ()) & b & (c || ())
    // there are four paths with property order:
    // - b
    // - b c
    // - a b
    // - a b c
    // We want to end up with the order a,b,c, so we build a map like
    // (a -> [b]), (b -> [c])
    // which, in this case, uniquely determines the property order.
    val formatsBefore = mutableMapOf<Id, MutableSet<Id>>()
    syntaxPaths.forEach { path ->
        val propertyUses = path.propertiesUsed
        // Make sure all uses appear in the formatsBefore table, because the adjacent pair loop
        // never fires for lists of size <= 1.
        for (propertyUse in propertyUses) {
            formatsBefore.getOrPut(propertyUse) { mutableSetOf() }
        }
        // Add relationships between adjacent pairs.
        for (i in 1 until propertyUses.size) {
            formatsBefore.putMulti(propertyUses[i], propertyUses[i - 1]) {
                mutableSetOf()
            }
        }
    }
    val usedInSyntax = partiallyOrder(formatsBefore)

    // Now we have an order that has things in the format string in their
    // natural order which is intuitive and makes it easy for people familiar
    // with the syntax to guess the order of parameters in the constructor.

    // Figure out how many times each used property is used.
    // This affects how the type for the property.  Something optional has a nullable
    // type, and something repeated is backed by a list.
    val repeated: Set<Id> = run {
        val repeated = mutableSetOf<Id>()
        syntaxPaths.forEach { path ->
            var propertyIndex = 0
            for (part in path.formatTemplateParts) {
                if (part is FormatPlaceholder) {
                    if (part.joiner != null) {
                        repeated.add(path.propertiesUsed[propertyIndex])
                    }
                    propertyIndex += 1
                }
            }
        }
        repeated.toSet()
    }
    val inferredCounts = usedInSyntaxUnordered.associateWith { propertyName ->
        nodeType.localProperties[propertyName]?.propertyCount
            ?: when {
                propertyName in repeated -> PropertyCount.Many
                syntaxPaths.any { propertyName !in it.propertiesUsed } ->
                    PropertyCount.ZeroOrOne
                else -> PropertyCount.One
            }
    }

    return FormatStringDigest(
        syntaxDeclaration,
        syntaxPaths,
        usedInSyntax,
        inferredCounts,
    )
}

internal fun maybeDefineFormatStringProperty(
    gp: GrammarProcessor,
    nodeType: CompleteNodeType,
): List<MemberDefinition> {
    val members = mutableListOf<MemberDefinition>()
    val digest = nodeType.formatStringDigest
    if (digest is Declared) {
        defineFormatStringProperty(gp, nodeType, digest.content, members)
    } else if (
        nodeType.isConcrete && digest !is Inherited && nodeType.renderTo != null
    ) {
        // Since it has a renderTo method, the tree formatter should never request
        // the formatString, but we still need to fit the interface.
        members.add(
            PropertyDefinition(
                name = Id("codeFormattingTemplate"),
                overrides = true,
                type = PropertyTypeAndCount(
                    KotlinCode("CodeFormattingTemplate", nodeType.renderTo.content.pos),
                    PropertyCount.ZeroOrOne,
                ),
                getter = KotlinCode("null", nodeType.pos.leftEdge),
                setter = null,
                containingTypeIsConcrete = nodeType.isConcrete,
                containingTypeKind = nodeType.typeKind,
                defaultExpression = null,
                docComment = null,
            ),
        )
    }
    return members.toList()
}

private fun defineFormatStringProperty(
    gp: GrammarProcessor,
    nodeType: CompleteNodeType,
    digest: FormatStringDigest,
    members: MutableList<MemberDefinition>,
) {
    val syntaxDeclaration: SyntaxDeclaration = digest.syntaxDeclaration
    val pos = syntaxDeclaration.pos
    // Consider every path through the syntax tree.
    // Keep track of which properties are on each path and how many times they're
    // encountered, so we can decide whether the property is optional, singly used, or
    // repeated.
    // Also produce enough branch information so that we can generate code for the format string.
    val syntaxPaths = digest.syntaxPaths
    val usedInSyntax = digest.usedInSyntax
    val isConcrete = nodeType.isConcrete
    val typeKind = nodeType.typeKind

    val localNameToIndex = usedInSyntax.mapIndexed { index, id -> id to index }.toMap()
    // Produce a branching `if` that picks a format string.
    val indented = syntaxPaths.any { it.conditions.isNotEmpty() }
    val codeFormattingTemplateCode = toStringViaBuilder { out ->
        syntaxPaths.forEachIndexed { i, path ->
            if (i != 0) {
                out.append(" else")
            }
            val conditions = path.conditions
            if (conditions.isNotEmpty()) {
                if (i != 0) {
                    out.append(' ')
                }
                out.append("if (")
                conditions.forEachIndexed { conditionIndex, condition ->
                    if (conditionIndex != 0) {
                        out.append(" && ")
                    }
                    appendCondition(nodeType, condition, out)
                }
                out.append(")")
            } else {
                if (i + 1 != syntaxPaths.size) {
                    gp.fail(
                        syntaxDeclaration.pos,
                        MessageTemplate.Unparsable,
                        listOf("condition on || for ${nodeType.name.text}"),
                    )
                }
            }
            if (indented) {
                out.append(" {\n")
            }
            // Generate a template key.
            // This produces a CodeFormatTemplate so that we can share code with CodeFormatTemplate
            // about space splitting and normalizing angle brackets.
            // We do need to pull out special token expressions like `SpecialTokens.indent`.
            // We mark the location of those so that after we've built our part list we can
            // normalize it and then re-incorporate them.
            val codeFormattingTemplateKey = run {
                // Will be reincorporated later
                val deferredFormatExprs = mutableListOf<Pair<Int, FormatExpr>>()
                var propertyUseIndex = 0
                val parts = mutableListOf<CodeFormattingTemplate>()
                for (part in path.formatTemplateParts) {
                    when (part) {
                        is FormatPlaceholder -> {
                            val propertyUse = path.propertiesUsed[propertyUseIndex]
                            propertyUseIndex += 1
                            val propertyIndex = localNameToIndex.getValue(propertyUse)
                            val substitution = when (val joiner = part.joiner) {
                                null -> CodeFormattingTemplate.OneSubstitution(propertyIndex)
                                else -> CodeFormattingTemplate.GroupSubstitution(
                                    propertyIndex,
                                    // We get texts like "\n\n" and ", " so use fromFormatString
                                    // which recognizes that space elements are separate from
                                    // literal tokens.
                                    CodeFormattingTemplate.fromFormatString(joiner),
                                )
                            }
                            parts.add(substitution)
                        }

                        is FormatText -> {
                            parts.add(CodeFormattingTemplate.guessFromTokenText(part.text))
                        }

                        is FormatExpr -> {
                            parts.add(CodeFormattingTemplate.empty)
                            deferredFormatExprs.add(parts.lastIndex to part)
                        }
                    }
                }
                val literalTexts = buildSet {
                    parts.mapNotNullTo(this) {
                        (it as? CodeFormattingTemplate.LiteralToken)?.token?.text
                    }
                }
                val substitutions = buildMap {
                    var counter = 0
                    for ((indexInParts, formatExpr) in deferredFormatExprs) {
                        var placeholderTokenText: String
                        while (true) {
                            placeholderTokenText = "expr#$counter"
                            counter += 1
                            if (placeholderTokenText !in literalTexts) {
                                break
                            }
                        }
                        this[placeholderTokenText] = formatExpr.code
                        parts[indexInParts] = CodeFormattingTemplate.LiteralToken(
                            OutputToken(placeholderTokenText, OutputTokenType.Comment),
                        )
                    }
                }
                CodeFormattingTemplate.heuristicAdjustParts(parts)
                CodeFormattingTemplateKey(parts.toList(), substitutions)
            }

            if (i != 0 || conditions.isNotEmpty()) {
                out.append("    ")
            }
            out.append(gp.getCodeFormattingTemplateId(codeFormattingTemplateKey))
            if (indented) {
                out.append("\n}")
            }
        }
    }
    members.add(
        PropertyDefinition(
            name = Id("codeFormattingTemplate"),
            overrides = true,
            type = PropertyTypeAndCount(
                KotlinCode("CodeFormattingTemplate", pos.leftEdge),
                PropertyCount.One,
            ),
            getter = KotlinCode(codeFormattingTemplateCode, pos),
            setter = null,
            containingTypeIsConcrete = isConcrete,
            containingTypeKind = typeKind,
            defaultExpression = null,
            docComment = null,
        ),
    )
    members.add(
        PropertyDefinition(
            name = Id("formatElementCount"),
            overrides = true,
            type = null,
            getter = KotlinCode("${localNameToIndex.size}", pos.leftEdge),
            setter = null,
            containingTypeIsConcrete = isConcrete,
            containingTypeKind = typeKind,
            defaultExpression = null,
            docComment = null,
        ),
    )
    if (localNameToIndex.isNotEmpty()) {
        // Map placeholder indices to properties by overriding fun formatTreeElement(index).
        val body = KotlinCode(
            toStringViaBuilder { body ->
                body.append("return when (index) {\n")
                localNameToIndex.forEach { (name, index) ->
                    body.append("    $index -> ")
                    val prop = nodeType.properties[name]
                    val count = prop?.typeAndCount?.count ?: PropertyCount.One
                    val isExternal = when (prop?.typeAndCount?.type) {
                        is NamedPropertyType? -> false
                        is KotlinCode -> true
                    }
                    val propertyExpr = "this.${name.text}"
                    body.append(
                        if (isExternal) {
                            "IndexableFormattableTreeElement.wrap($propertyExpr)"
                        } else {
                            when (count) {
                                PropertyCount.ZeroOrOne -> "$propertyExpr ?: FormattableTreeGroup.empty"
                                PropertyCount.One -> propertyExpr
                                PropertyCount.Many -> "FormattableTreeGroup($propertyExpr)"
                            }
                        },
                    )
                    body.append("\n")
                }
                body.append("    else -> throw IndexOutOfBoundsException(\"\$index\")\n")
                body.append("}")
            },
            pos,
        )
        members.add(
            MethodDefinition(
                name = Id("formatElement"),
                overrides = true,
                params = listOf(
                    Id("index") to
                        PropertyTypeAndCount(KotlinCode("Int", pos.leftEdge), PropertyCount.One),
                ),
                returnType = PropertyTypeAndCount(
                    KotlinCode("IndexableFormattableTreeElement", pos.leftEdge),
                    PropertyCount.One,
                ),
                body = body,
            ),
        )
    }
}

private fun appendCondition(
    nodeType: CompleteNodeType,
    condition: Condition,
    out: StringBuilder,
) {
    when (val wanted = condition.wanted) {
        is SimpleBoolean -> {
            if (!wanted.value) {
                out.append('!')
            }
            out.append(condition.propertyName)
        }
        is EnumReference ->
            out.append(condition.propertyName)
                .append(" == ")
                .append(wanted.enumName)
                .append('.')
                .append(wanted.memberName)
        Truthy -> when (
            nodeType.properties[condition.propertyName]?.typeAndCount?.count
        ) {
            PropertyCount.ZeroOrOne ->
                out.append(condition.propertyName).append(" != null")
            PropertyCount.Many ->
                out.append(condition.propertyName)
                    .append(".isNotEmpty()")
            // Probably an error.
            else -> out.append(condition.propertyName)
        }
    }
}

internal data class FormatStringDigest(
    val syntaxDeclaration: SyntaxDeclaration,
    val syntaxPaths: List<SyntaxPath>,
    val usedInSyntax: List<Id>,
    val inferredCounts: Map<Id, PropertyCount>,
)

internal data class SyntaxPath(
    val formatTemplateParts: List<FormatPart>,
    val propertiesUsed: List<Id> = emptyList(),
    val conditions: List<Condition> = emptyList(),
)

private fun syntaxAnalysis(syntax: SyntaxDeclaration): List<SyntaxPath> = when (syntax) {
    is LiteralText ->
        listOf(SyntaxPath(formatTemplateParts = listOf(FormatText(syntax.text))))
    is SpecialTokenExpr ->
        listOf(SyntaxPath(formatTemplateParts = listOf(FormatExpr(syntax.code))))
    is PropertyUse ->
        listOf(
            SyntaxPath(
                formatTemplateParts = listOf(FormatPlaceholder(null)),
                propertiesUsed = listOf(syntax.propertyName),
            ),
        )
    is Repetition -> listOf(
        SyntaxPath(
            formatTemplateParts = listOf(FormatPlaceholder(syntax.joiner)),
            propertiesUsed = listOf(syntax.repeated.propertyName),
        ),
    )
    is Concatenation -> {
        val lefts = syntaxAnalysis(syntax.left)
        val rights = syntaxAnalysis(syntax.right)
        lefts.flatMap { left ->
            rights.map { right ->
                SyntaxPath(
                    formatTemplateParts = left.formatTemplateParts + right.formatTemplateParts,
                    propertiesUsed = left.propertiesUsed + right.propertiesUsed,
                    conditions = left.conditions + right.conditions,
                )
            }
        }
    }
    is Alternation -> syntaxAnalysis(syntax.left) + syntaxAnalysis(syntax.right)
    is Conditional -> syntaxAnalysis(syntax.consequent).map {
        it.copy(conditions = listOf(syntax.condition) + it.conditions)
    }
    is Epsilon -> listOf(SyntaxPath(emptyList()))
}

internal data class CodeFormattingTemplateKey(
    val codeFormatTemplateElements: List<CodeFormattingTemplate>,
    val substitutions: Map<String, KotlinCode>,
) {
    fun toFormatStringApproximate() =
        codeFormatTemplateElements.joinToString(" ") {
            if (
                it is CodeFormattingTemplate.LiteralToken &&
                it.token.type == OutputTokenType.Comment
            ) {
                val sub = substitutions[it.token.text]
                if (sub != null) {
                    return@joinToString "`${sub.sourceText}`"
                }
            }
            it.toFormatStringApproximate()
        }
}
