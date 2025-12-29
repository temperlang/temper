package lang.temper.kcodegen.outgrammar

import lang.temper.common.asciiUnTitleCase
import lang.temper.common.jsonEscaper
import lang.temper.common.toStringViaBuilder
import lang.temper.format.CodeFormattingTemplate
import lang.temper.format.OutputToken
import lang.temper.format.OutputTokenType
import lang.temper.format.TokenAssociation

internal class KotlinSourceGenerator(
    private val gp: GrammarProcessor,
) {
    fun generateKotlinSource(
        definitions: List<TypeDefinition>,
    ) = toStringViaBuilder { out ->
        val namespaceName = gp.getNamespaceName()
        val operatorDefinitionType = gp.getOperatorDefinitionType()
        val formattingHintsType = gp.getFormattingHintsType()

        val packageName = gp.packageNameParts.joinToString(".")
        out.append("@file:lang.temper.common.Generated(\"OutputGrammarCodeGenerator\")\n")
        // Parts of these are probably unused or could be private because we're deriving them from
        // grammar, but having the grammar maintainer mark them as such is a wast of time.
        // The CascadeIf results from format string destructuring.
        // MagicNumber enters in because format element indices end up in code.
        // MaxLineLength is triggered because it'd be a PITA to build line breaking into the
        // generator.

        out.append(
            listOf(
                "ktlint",
                "unused",
                "CascadeIf",
                "MagicNumber",
                "MemberNameEqualsClassName",
                "MemberVisibilityCanBePrivate",
            ).joinToString(", ", prefix = "@file:Suppress(", postfix = ")\n\n") { "\"$it\"" },
        )
        out.append("package $packageName\n")
        val allImports = listOf(
            "lang.temper.be.BaseOutData",
            "lang.temper.be.BaseOutTree",
            "lang.temper.ast.ChildMemberRelationships",
            "lang.temper.ast.deepCopy",
            "lang.temper.ast.OutData",
            "lang.temper.ast.OutTree",
            "lang.temper.common.replaceSubList",
            "lang.temper.format.CodeFormattingTemplate",
            "lang.temper.format.FormattableEnum",
            "lang.temper.format.FormattableTreeGroup",
            "lang.temper.format.FormattingHints",
            "lang.temper.format.IndexableFormattableTreeElement",
            "lang.temper.format.OutputTokenType",
            "lang.temper.format.TokenAssociation",
            "lang.temper.format.TokenSerializable",
            "lang.temper.format.TokenSink",
            "lang.temper.log.Position",
        ) + (gp.getMetadata("imports")?.first ?: "").split(newlinePattern).mapNotNull {
            it.trim().ifEmpty { null }
        }.filter {
            !(
                it.length >= packageName.length + 2 &&
                    it.startsWith(packageName) &&
                    it[packageName.length] == '.' &&
                    it[packageName.length + 1] in 'A'..'Z'
                )
        }
        var importLocation = out.length

        // Create a wrapping object.  If the wrapping object were named `Ns`, client code could
        // refer to
        //     Ns.NodeType
        out.append("\n")
        out.append("object $namespaceName {\n")
        // Define four base types for all output nodes in this language, an interface and an abstract
        // class for each of the Tree and Data hierarchies.
        out.append(
            "    sealed interface ${commonSuperTreeInterfaceName.text} : OutTree<${
                commonSuperTreeInterfaceName.text
            }> {\n",
        )
        out.append(
            "        override fun formattingHints(): FormattingHints = ${
                formattingHintsType
            }.getInstance()\n",
        )
        out.append("        override val operatorDefinition: $operatorDefinitionType?\n")
        out.append("        override fun deepCopy(): ${commonSuperTreeInterfaceName.text}\n")
        out.append("    }\n")
        out.append("    sealed class ${commonSuperTreeClassName.text}(\n")
        out.append("        pos: Position,\n")
        out.append(
            "    ) : BaseOutTree<${commonSuperTreeInterfaceName.text}>(pos), ${
                commonSuperTreeInterfaceName.text
            }\n",
        )
        out.append(
            "    sealed interface ${commonSuperDataInterfaceName.text} : OutData<${
                commonSuperDataInterfaceName.text
            }> {\n",
        )
        out.append(
            "        override fun formattingHints(): FormattingHints = ${
                formattingHintsType
            }.getInstance()\n",
        )
        out.append("        override val operatorDefinition: $operatorDefinitionType?\n")
        out.append("    }\n")
        out.append(
            "    sealed class ${commonSuperDataClassName.text} : BaseOutData<${commonSuperDataInterfaceName.text}>(), ${
                commonSuperDataInterfaceName.text
            }\n",
        )

        // Most lines are indented twice.  Once for the containing `object` and once for the node
        // type they're part of.  This is eight spaces which is two Kotlin-style indent levels.
        val linePrefix = "        "

        for (d in definitions) {
            out.append("\n")
            when (d) {
                is EnumDefinition ->
                    generateCodeForDefinition(linePrefix, d, out)
                is NodeTypeDefinition ->
                    generateCodeForDefinition(linePrefix, d, out)
            }
        }

        val codeFormattingTemplates = gp.getAllCodeFormattingTemplates()
        for ((codeFormattingTemplateKey, id) in codeFormattingTemplates) {
            val codeFormattingTemplateParts = codeFormattingTemplateKey.codeFormatTemplateElements
            val formatString = codeFormattingTemplateKey.toFormatStringApproximate()
                .replace(kotlinCommentDelimiter) { // Make safe to embed in /**...*/
                    val first = it.value[0] // '/' or '*'
                    val second = it.value[1] // The other
                    "$first\\$second"
                }
            out.append("\n    /** `$formatString` */\n")
            out.ktIndent(1)
            out.append("private val ${id.text} =\n")
            out.kotlinCodeForTemplate(
                if (codeFormattingTemplateParts.size == 1) {
                    codeFormattingTemplateParts[0]
                } else {
                    CodeFormattingTemplate.Concatenation(codeFormattingTemplateParts)
                },
                indent = 2,
                substitutions = codeFormattingTemplateKey.substitutions,
            )
            out.append("\n")
        }

        out.append("}\n")

        // Insert imports whose identifier after the last dot is a simple substring
        // of the content appended to out after the import location was saved
        val afterImportLocation = out.substring(importLocation)
        for (imported in allImports.toSet().toList().sorted()) {
            val afterLastDot = imported.substring(imported.lastIndexOf('.') + 1)
            if (afterLastDot !in afterImportLocation) {
                continue
            }
            val importStmt = "import $imported\n"
            out.insert(importLocation, importStmt)
            importLocation += importStmt.length
        }
    }

    private fun generateCodeForDefinition(
        linePrefix: String,
        d: EnumDefinition,
        out: StringBuilder,
    ) {
        indentDocCommentTo("    ", d.docComment, out)
        out.append("    enum class ").append(d.name.text).append(" : FormattableEnum {\n")
        for ((name, _) in d.members) {
            out.append(linePrefix).append(name.text).append(",\n")
        }
        if (d.members.any { it.second != null }) {
            out.append(linePrefix).append(";\n")
            out.append(linePrefix).append("override fun renderTo(tokenSink: TokenSink) {\n")
            out.append(linePrefix).append("    when (this) {\n")
            for ((name, token) in d.members) {
                out.append(linePrefix).append("        ").append(name.text)
                    .append(" -> ")
                val t = token
                    ?: OutputToken(name.text.asciiUnTitleCase(), OutputTokenType.Word)
                if (t == EnumType.emptyToken) {
                    out.append("{}\n")
                    continue
                }
                val methodName = when (t.type as OutputTokenType.StandardOutputTokenType) {
                    OutputTokenType.Word -> "word"
                    OutputTokenType.Name -> "name"
                    OutputTokenType.QuotedValue -> "quoted"
                    OutputTokenType.NumericValue -> "number"
                    OutputTokenType.OtherValue -> "value"
                    OutputTokenType.Punctuation -> "punctuation"
                    OutputTokenType.Comment -> "comment"
                    OutputTokenType.Space -> "space"
                    OutputTokenType.NotEmitted -> "invisible"
                }
                out.append("tokenSink.").append(methodName).append('(')
                jsonEscaper.escapeTo(t.text, out)
                out.append(")\n")
            }
            out.append(linePrefix).append("    }\n")
            out.append(linePrefix).append("}\n")
        }
        out.append("    }\n")
    }

    private fun generateCodeForDefinition(
        linePrefix: String,
        d: NodeTypeDefinition,
        out: StringBuilder,
    ) {
        indentDocCommentTo("    ", d.docComment, out)
        out.append("    ")
        when (d.typeKind) {
            TypeKind.Ast -> {}
            TypeKind.Data -> if (d.isConcrete) out.append("data ")
        }
        out.append(if (d.isConcrete) "class" else "sealed interface")
        out.append(' ').append(d.name.text)
        // We need to put some properties in the constructor parameter list,
        // and some need to correspond to property declarations in the body.
        // Some appear as both a non-val declaration in the constructor and a property
        // in the body.
        val constructorProperties = mutableListOf<PropertyDefinition>()
        val bodyMembers = mutableListOf<MemberDefinition>()
        if (!d.isConcrete) {
            bodyMembers.addAll(d.members)
        } else {
            val mirroredConstructorProperties = d.members.mapNotNull {
                if (
                    it is PropertyDefinition &&
                    it.name.text.startsWith(BACKED_PROPERTY_NAME_PREFIX)
                ) {
                    it.name.text.substring(BACKED_PROPERTY_NAME_PREFIX.length)
                } else {
                    null
                }
            }.toSet()
            d.members.forEach { m ->
                if (m !is PropertyDefinition) {
                    bodyMembers.add(m)
                } else if (m.name.text in mirroredConstructorProperties) {
                    bodyMembers.add(m)
                    constructorProperties.add(m)
                } else if (
                    !m.name.text.startsWith(BACKED_PROPERTY_NAME_PREFIX) &&
                    m.getter == null && m.setter == null
                ) {
                    constructorProperties.add(m)
                } else {
                    bodyMembers.add(m)
                }
            }
        }
        if (d.isConcrete) {
            out.append("(\n")
            out.append(linePrefix)
            out.append(
                when (d.typeKind) {
                    TypeKind.Ast -> "pos: Position,"
                    TypeKind.Data -> "override val sourceLibrary: DashedIdentifier,"
                },
            )
            out.append("\n")
            constructorProperties.forEach { m ->
                m.appendTo(
                    out,
                    linePrefix = linePrefix,
                    // We can put the defaults on any forwarding constructor
                    asConstructorParameter = true,
                )
                out.append(",\n")
            }
            out.append("    )")
        }
        val superTypes = d.superTypes
        if (superTypes.isNotEmpty()) {
            out.append(" : ")
            for ((index, st) in superTypes.withIndex()) {
                if (index != 0) {
                    out.append(", ")
                }
                out.append(st.sourceText)
                if (index == 0 && d.isConcrete) {
                    // super class needs constructor invocation
                    if (d.typeKind == TypeKind.Ast) {
                        // Pass position
                        out.append("(pos)")
                    } else {
                        out.append("()")
                    }
                }
            }
        }

        val extraCode = d.extraCode
        if (bodyMembers.isNotEmpty() || extraCode != null) {
            out.append(" {\n")
            for (m in bodyMembers) {
                m.appendTo(out, linePrefix = linePrefix)
                out.append('\n')
            }
            if (extraCode != null) {
                out.append(extraCode.indent(linePrefix)).append('\n')
            }
            out.append("    }")
        }
        out.append('\n')
    }
}

internal fun indentDocCommentTo(linePrefix: String, docComment: DocComment?, out: StringBuilder) {
    docComment?.commentText?.split("\n")?.forEach {
        out.append(linePrefix)
        out.append(it)
        out.append('\n')
    }
}

private val kotlinCommentDelimiter = Regex("""[/][*]|[*][/]""")

private fun StringBuilder.ktIndent(levels: Int) {
    repeat(levels) { append("    ") }
}

/** Turn a [CodeFormattingTemplate] back into code that calls its constructor */
private fun StringBuilder.kotlinCodeForTemplate(
    t: CodeFormattingTemplate,
    indent: Int,
    substitutions: Map<String, KotlinCode>,
) {
    ktIndent(indent)
    when (t) {
        CodeFormattingTemplate.NewLine ->
            append("CodeFormattingTemplate.NewLine")
        CodeFormattingTemplate.Space ->
            append("CodeFormattingTemplate.Space")
        is CodeFormattingTemplate.LiteralToken -> {
            val token = t.token
            val sub = if (token.type == OutputTokenType.Comment) {
                substitutions[token.text]
            } else {
                null
            }
            append("CodeFormattingTemplate.LiteralToken(")
            if (sub != null) {
                append(sub.sourceText)
            } else {
                jsonEscaper.escapeTo(token.text, this)
                append(", OutputTokenType.")
                append(
                    when (token.type as OutputTokenType.StandardOutputTokenType) {
                        OutputTokenType.Comment -> "Comment"
                        OutputTokenType.Name -> "Name"
                        OutputTokenType.NotEmitted -> "NotEmitted"
                        OutputTokenType.NumericValue -> "NumericValue"
                        OutputTokenType.OtherValue -> "OtherValue"
                        OutputTokenType.Punctuation -> "Punctuation"
                        OutputTokenType.QuotedValue -> "QuotedValue"
                        OutputTokenType.Space -> "Space"
                        OutputTokenType.Word -> "Word"
                    },
                )
                if (token.association != TokenAssociation.Unknown) {
                    append(", TokenAssociation.")
                    append(token.association.name)
                }
            }
            append(")")
        }
        is CodeFormattingTemplate.Concatenation -> if (t.elements.isEmpty()) {
            append("CodeFormattingTemplate.empty")
        } else {
            append("CodeFormattingTemplate.Concatenation(\n")
            ktIndent(indent + 1)
            append("listOf(\n")
            for (item in t.elements) {
                kotlinCodeForTemplate(item, indent + 2, substitutions)
                append(",\n")
            }
            ktIndent(indent + 1)
            append("),\n")
            ktIndent(indent)
            append(")")
        }
        is CodeFormattingTemplate.GroupSubstitution -> {
            val sep = t.elementSeparator
            append("CodeFormattingTemplate.GroupSubstitution(\n")
            ktIndent(indent + 1)
            append(t.relativeIndex)
            append(",\n")
            kotlinCodeForTemplate(sep, indent + 1, emptyMap())
            append(",\n")
            ktIndent(indent)
            append(")")
        }
        is CodeFormattingTemplate.OneSubstitution -> {
            append("CodeFormattingTemplate.OneSubstitution(")
            append(t.relativeIndex)
            append(")")
        }
    }
}
