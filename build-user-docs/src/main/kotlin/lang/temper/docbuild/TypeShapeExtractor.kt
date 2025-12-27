package lang.temper.docbuild

import lang.temper.common.MimeType
import lang.temper.common.RFailure
import lang.temper.common.RResult
import lang.temper.common.RSuccess
import lang.temper.common.ignore
import lang.temper.common.putMultiList
import lang.temper.common.splitAfter
import lang.temper.common.toStringViaBuilder
import lang.temper.format.OutToks
import lang.temper.frontend.implicits.ImplicitsModule
import lang.temper.lexer.StandaloneLanguageConfig
import lang.temper.lexer.TemperToken
import lang.temper.lexer.TokenType
import lang.temper.log.FilePath
import lang.temper.log.Position
import lang.temper.log.Positioned
import lang.temper.log.filePath
import lang.temper.name.ExportedName
import lang.temper.name.ModularName
import lang.temper.name.ParsedName
import lang.temper.name.ResolvedName
import lang.temper.name.ResolvedParsedName
import lang.temper.name.SourceName
import lang.temper.name.Symbol
import lang.temper.type.Abstractness
import lang.temper.type.MemberShape
import lang.temper.type.MethodKind
import lang.temper.type.MethodShape
import lang.temper.type.PropertyShape
import lang.temper.type.StaticPropertyShape
import lang.temper.type.TypeFormal
import lang.temper.type.TypeParameterShape
import lang.temper.type.TypeShape
import lang.temper.type.Visibility
import lang.temper.type.VisibleMemberShape
import lang.temper.type.WellKnownTypes
import lang.temper.type2.DefinedNonNullType
import lang.temper.type2.Descriptor
import lang.temper.type2.MkType2
import lang.temper.type2.Nullity
import lang.temper.type2.Signature2
import lang.temper.type2.Type2
import lang.temper.type2.ValueFormalKind
import lang.temper.type2.hackMapOldStyleToNew
import lang.temper.type2.hackSynthesizedFunInterfaceSymbol
import lang.temper.type2.withType
import lang.temper.value.BlockTree
import lang.temper.value.DeclTree
import lang.temper.value.TEdge
import lang.temper.value.enumTypeSymbol
import lang.temper.value.fromTypeSymbol
import lang.temper.value.reifiedTypeContained
import lang.temper.value.returnParsedName
import lang.temper.value.staticSymbol
import lang.temper.value.typeDeclSymbol
import lang.temper.value.varSymbol
import kotlin.reflect.KClass
import kotlin.reflect.cast
import kotlin.reflect.safeCast

// These classes help us look through Implicits.temper and group comments with
// builtin type members.
private sealed class Documentable : Positioned

internal fun isDocumentableCommentToken(token: TemperToken) =
    token.tokenType == TokenType.Comment && token.tokenText.startsWith("/**")

private data class InterstitialComment(
    override val pos: Position,
    val content: TextDocContent,
    val commentIndexInFile: Int,
) : Documentable()

private data class Declaration(val decl: DeclTree) : Documentable() {
    override val pos get() = decl.pos
}

private open class Info<T>(
    val decl: DeclTree,
    var comment: InterstitialComment?,
    val element: T,
    val snippetId: SnippetId,
) {
    val pos: Position get() = decl.pos
    operator fun component1() = pos
    operator fun component2() = comment
    operator fun component3() = element

    val commentText: String get() = comment?.content?.text ?: ""
}

private const val TYPE_ID_PREFIX = "type"
private const val COMMENTARY_ID_SUFFIX = "commentary"
private const val SIG_ID_SUFFIX = "sig"
private const val TYPE_FORMAL_ID_PART = "typeFormal"
private const val METHOD_ID_PART = "method"
private const val GETTER_ID_PART = "setter"
private const val SETTER_ID_PART = "getter"
private const val PROPERTY_ID_PART = "property"
private const val STATIC_ID_PART = "static"

private fun snippetIdForType(typeName: ParsedName) = SnippetId(
    listOf(TYPE_ID_PREFIX, snippetNameFor(typeName)),
    MD_EXTENSION,
)

private fun commentaryIdFor(memberOrTypeSnippetId: SnippetId) =
    SnippetId(memberOrTypeSnippetId.parts + COMMENTARY_ID_SUFFIX, MD_EXTENSION)

private fun snippetIdForMember(type: TypeShape, member: MemberShape): SnippetId {
    val typeName = type.name.asParsedName
    val memberKind = when (member) {
        is TypeParameterShape -> TYPE_FORMAL_ID_PART
        is MethodShape -> when (member.methodKind) {
            MethodKind.Normal -> METHOD_ID_PART
            MethodKind.Getter -> GETTER_ID_PART
            MethodKind.Setter -> SETTER_ID_PART
            MethodKind.Constructor -> METHOD_ID_PART
        }
        is PropertyShape -> PROPERTY_ID_PART
        is StaticPropertyShape -> STATIC_ID_PART
    }
    val memberName = (member.name as SourceName).baseName
    return SnippetId(
        listOf(TYPE_ID_PREFIX, snippetNameFor(typeName), memberKind, snippetNameFor(memberName)),
        MD_EXTENSION,
    )
}

private class TypeInfo(
    decl: DeclTree,
    comment: InterstitialComment?,
    typeShape: TypeShape,
) : Info<TypeShape>(decl, comment, typeShape, snippetIdForType(typeShape.name.asParsedName)) {
    val memberInfo = mutableMapOf<Symbol, MutableList<Info<MemberShape>>>()
}

private data class CommentDeclarationRelationships(
    val groupedInfo: Map<TypeShape, TypeInfo>,
    val containingTypeFor: (ResolvedName) -> TypeShape?,
    val idToComment: Map<SnippetId, InterstitialComment>,
    val ungroupedSnippets: List<Snippet>,
)

private fun extractCommentDeclarationRelationships(
    content: TemperContent,
    extractor: SnippetExtractor,
): CommentDeclarationRelationships {
    // A list of declarations paired with (TypeShape | MemberShape | TextDocContent) where
    // DocContent contain comment texts
    val documentables = mutableListOf<Documentable>()

    // Grab documentation strings.
    content.lexer().let { lexer ->
        var commentIndex = 0
        for (token in lexer) {
            if (isDocumentableCommentToken(token)) {
                documentables.add(
                    InterstitialComment(
                        token.pos,
                        commentContentToSnippetContent(token.tokenText),
                        commentIndexInFile = commentIndex++,
                    ),
                )
            }
        }
    }

    // Add types and members into the mix
    val module = ImplicitsModule.module
    val root = module.treeForDebug
    require(root is BlockTree)

    for (child in root.children) {
        if (child is DeclTree) {
            val name = child.parts?.name?.content ?: continue
            if (name !is ResolvedParsedName) { // Skip typePlaceholder#123
                continue
            }
            if (name.baseName == returnParsedName) {
                // Skip over module result variable
                continue
            }
            documentables.add(Declaration(child))
        }
    }

    documentables.sortWith { a, b -> a.pos.left - b.pos.left }

    // Group members by their enclosing type
    val groupedInfo = mutableMapOf<TypeShape, TypeInfo>()
    val idToComment = mutableMapOf<SnippetId, InterstitialComment>()
    val ungroupedSnippets = mutableListOf<Snippet>()
    val derivation = ExtractedBy(extractor)
    run {
        var lastComment: InterstitialComment? = null
        for (d in documentables) {
            val precedingComment: InterstitialComment? = lastComment
            lastComment = null

            when (d) {
                is InterstitialComment -> lastComment = d
                is Declaration -> {
                    val decl = d.decl
                    val parts = decl.parts
                    if (parts != null) {
                        val typeDecl = parts.metadataSymbolMap[typeDeclSymbol]
                        if (typeDecl != null) {
                            val type = shapeFor(typeDecl)
                            if (type != null) {
                                val info = groupedInfo.getOrPut(type) {
                                    TypeInfo(decl, precedingComment, type)
                                }
                                if (precedingComment != null) {
                                    info.comment = precedingComment
                                }
                            }
                        }
                        val fromType = parts.metadataSymbolMap[fromTypeSymbol]
                        if (fromType == null) {
                            val name = parts.name.content
                            if (typeDecl == null && precedingComment != null && name is ExportedName) {
                                // We could either store the info needed to build these snippets or just build them.
                                // Building seems easier, since these don't actually need fancy relationships.
                                val id = SnippetId(listOf("builtin", snippetNameFor(name.baseName)), MD_EXTENSION)
                                idToComment[id] = precedingComment
                                val title = name.baseName.nameText
                                val text = precedingComment.content.text
                                val snippet = Snippet(
                                    id = id,
                                    shortTitle = "`$title`",
                                    source = content.source,
                                    sourceStartOffset = precedingComment.pos.left,
                                    mimeType = MimeType.markdown,
                                    content = TextDocContent("# `$title`\n$text"),
                                    isIntermediate = false,
                                    derivation = derivation,
                                )
                                ungroupedSnippets.add(snippet)
                            }
                        } else {
                            val type = shapeFor(fromType)
                            if (type != null) {
                                val memberName = parts.name
                                val member =
                                    type.members.firstOrNull { it.name == memberName.content }
                                if (
                                    member != null &&
                                    (
                                        member !is VisibleMemberShape ||
                                            when (member.visibility) {
                                                Visibility.Public, Visibility.Protected -> true
                                                Visibility.Private -> false
                                            }
                                        )
                                ) {
                                    groupedInfo[type]?.let { info ->
                                        val memberId = snippetIdForMember(type, member)
                                        info.memberInfo.putMultiList(
                                            member.symbol,
                                            Info(decl, precedingComment, member, memberId),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Bootstrap types that haven't been fleshed out yet in Implicits.temper, like `Void`
    WellKnownTypes.allNames.forEach { typeName ->
        val type = WellKnownTypes.withName(typeName)
        if (type != null) {
            groupedInfo.getOrPut(type) {
                TypeInfo(
                    root.document.treeFarm.grow(root.pos.leftEdge) {
                        Decl(type.name)
                    },
                    null,
                    type,
                )
            }
        }
    }

    val containingTypeFor: (ResolvedName) -> TypeShape? = run {
        val containers = mutableMapOf<ResolvedName, TypeShape>()
        for (typeInfo in groupedInfo.values) {
            for (member in typeInfo.element.members) {
                containers[member.name as? ResolvedName ?: continue] = typeInfo.element
            }
        }
        ({ containers[it] })
    }

    for (typeInfo in groupedInfo.values) {
        val comment = typeInfo.comment
        if (comment != null) {
            idToComment[typeInfo.snippetId] = comment
        }
        for (memberInfos in typeInfo.memberInfo.values) {
            for (memberInfo in memberInfos) {
                val memberComment = memberInfo.comment
                if (memberComment != null) {
                    idToComment[memberInfo.snippetId] = memberComment
                }
            }
        }
    }

    return CommentDeclarationRelationships(
        groupedInfo = groupedInfo.toMap(),
        containingTypeFor = containingTypeFor,
        idToComment = idToComment.toMap(),
        ungroupedSnippets = ungroupedSnippets.toList(),
    )
}

private val implicitsLanguageConfig = StandaloneLanguageConfig

/** From `Implicits.temper`, extracts API documentation for our builtin types. */
internal object TypeShapeExtractor : SnippetExtractor() {
    private val commentDeclarationRelationships = extractCommentDeclarationRelationships(
        content = TemperContent(
            filePath(
                "frontend",
                "src",
                "commonMain",
                "resources",
                "implicits",
                "Implicits.temper",
            ),
            "${ImplicitsModule.code}",
            implicitsLanguageConfig,
        ),
        extractor = this,
    )

    /** These are top-level builtins defined in implicits. Currently, that's just the global console. */
    val ungroupedSnippets get() = commentDeclarationRelationships.ungroupedSnippets

    override fun extractSnippets(
        from: FilePath,
        content: DocSourceContent,
        mimeType: MimeType,
        onto: MutableCollection<Snippet>,
    ) {
        if (content !is TemperContent) {
            return
        }
        if (
            !"$from".startsWith(
                "frontend/src/commonMain/resources/implicits/Implicits.",
            )
        ) {
            return
        }

        val derivation = ExtractedBy(this)

        check(content.fileContent == "${ImplicitsModule.code}")

        val (groupedInfo, containingTypeFor) = commentDeclarationRelationships

        // Generate snippets with paths like
        // type/<TypeName>: Type name and any comment
        //       class TypeName: /** is a very nice class */
        // type/<TypeName>/sig: Info about its super classes and type parameters.
        //       <T> extends AnyValue
        // type/<TypeName>/members
        // type/<TypeName>/typeFormals
        // type/<TypeName>/typeFormal/<memberName>: Member name and any comment
        // type/<TypeName>/typeFormal/<memberName>/sig: upper bounds
        // type/<TypeName>/properties
        // type/<TypeName>/property/<memberName>: Member name and any comment
        // type/<TypeName>/property/<memberName>/commentary: Just the comment
        // type/<TypeName>/property/<memberName>/sig: type, backed v computed, read v write
        // type/<TypeName>/methods
        // type/<TypeName>/method/<memberName>: Member name and any comment
        // type/<TypeName>/method/<memberName>/sig: signature
        // type/<TypeName>/statics
        // type/<TypeName>/static/<memberName>: Member name and any comment
        // type/<TypeName>/static/<memberName>/sig: signature
        for (typeInfo in groupedInfo.values) {
            val type = typeInfo.element
            val typeParsedName = type.name.asParsedName
            val typeNameMd = snippetNameFor(typeParsedName)
            val shortTitleForType = "*$typeNameMd*"
            val longTitleForType = when {
                enumTypeSymbol in type.metadata -> "*enum $typeNameMd*"
                type.abstractness == Abstractness.Abstract -> "*interface $typeNameMd*"
                else -> "*class $typeNameMd*"
            }
            val typeSnippetId = snippetIdForType(typeParsedName)
            onto.add(
                Snippet(
                    id = typeSnippetId,
                    shortTitle = shortTitleForType,
                    source = from,
                    sourceStartOffset = typeInfo.pos.left,
                    mimeType = MimeType.markdown,
                    content = TextDocContent(
                        """
                            |# $longTitleForType
                            |
                            |$INSERTION_MARKER_CHAR $TYPE_ID_PREFIX/$typeNameMd/$COMMENTARY_ID_SUFFIX
                            |
                            |$INSERTION_MARKER_CHAR $TYPE_ID_PREFIX/$typeNameMd/$SIG_ID_SUFFIX
                            |
                            |<blockquote markdown="1" class="indent-only">
                            |
                            |$INSERTION_MARKER_CHAR $TYPE_ID_PREFIX/$typeNameMd/members
                            |
                            |</blockquote>
                        """.trimMargin(),
                    ),
                    isIntermediate = false,
                    derivation = derivation,
                ),
            )
            onto.add(
                Snippet(
                    id = commentaryIdFor(typeSnippetId),
                    shortTitle = null,
                    source = from,
                    sourceStartOffset = (typeInfo.comment?.pos ?: typeInfo.pos).left,
                    mimeType = MimeType.markdown,
                    content = TextDocContent(typeInfo.commentText),
                    isIntermediate = false,
                    derivation = derivation,
                ),
            )
            // The type/XYZ/sig snippet explains super-type and parameter relationships
            run {
                val typeParams = toStringViaBuilder { sb ->
                    val params = type.typeParameters
                    if (params.isNotEmpty()) {
                        sb.append(" `<`")
                        for ((i, param) in params.withIndex()) {
                            if (i != 0) { sb.append(", ") }
                            typeToMarkdownOnto(
                                MkType2(param.definition).get(),
                                containingTypeFor,
                                sb,
                            )
                        }
                        sb.append("`>`")
                    }
                }
                val extendsClause = toStringViaBuilder { sb ->
                    val superTypes = type.superTypes
                    for ((i, superType) in superTypes.withIndex()) {
                        if (i == 0) {
                            sb.append(" `extends` ")
                        } else {
                            sb.append(", ")
                        }
                        typeToMarkdownOnto(hackMapOldStyleToNew(superType), containingTypeFor, sb)
                    }
                }
                onto.add(
                    Snippet(
                        SnippetId(listOf(TYPE_ID_PREFIX, typeNameMd, SIG_ID_SUFFIX), extension = MD_EXTENSION),
                        shortTitle = shortTitleForType,
                        source = from,
                        sourceStartOffset = typeInfo.pos.left,
                        mimeType = MimeType.markdown,
                        content = TextDocContent(
                            """
                            |*$typeNameMd*$typeParams$extendsClause
                            """.trimMargin(),
                        ),
                        isIntermediate = false,
                        derivation = derivation,
                    ),
                )
            }
            // The members list just groups the more specific lists.  It, like those other groups
            // just serves to enable `[snippet/type/MyType/members]` style links.
            onto.add(
                Snippet(
                    SnippetId(listOf(TYPE_ID_PREFIX, typeNameMd, "members"), extension = MD_EXTENSION),
                    shortTitle = "$shortTitleForType members",
                    source = from,
                    sourceStartOffset = typeInfo.pos.left,
                    mimeType = MimeType.markdown,
                    content = TextDocContent(
                        """
                            |$INSERTION_MARKER_CHAR $TYPE_ID_PREFIX/$typeNameMd/typeFormals
                            |
                            |$INSERTION_MARKER_CHAR $TYPE_ID_PREFIX/$typeNameMd/properties
                            |
                            |$INSERTION_MARKER_CHAR $TYPE_ID_PREFIX/$typeNameMd/methods
                            |
                            |$INSERTION_MARKER_CHAR $TYPE_ID_PREFIX/$typeNameMd/statics
                        """.trimMargin(),
                    ),
                    isIntermediate = false,
                    derivation = derivation,
                ),
            )
            fun <T : MemberShape> makeMemberSnippets(
                snippetIdSegmentPlural: String,
                snippetIdSegmentSingular: String,
                title: String,
                kClass: KClass<T>,
                filter: (T) -> Boolean = { true },
                emitSnippets: (SnippetId, SnippetId, Info<*>, T) -> Unit,
            ) {
                val members = typeInfo.memberInfo.values.flatMap { ls ->
                    ls.filter {
                        val m = kClass.safeCast(it.element)
                        m != null && filter(m)
                    }
                }
                val groupingSnippetContent = toStringViaBuilder { groupContent ->
                    if (members.isNotEmpty()) {
                        groupContent.append("# $snippetIdSegmentPlural")
                    }
                    for (member in members) {
                        val element = kClass.cast(member.element)
                        val memberParsedName = ParsedName(element.symbol.text)
                        val memberNameMd = snippetNameFor(memberParsedName)
                        val snippetParts =
                            listOf(TYPE_ID_PREFIX, typeNameMd, snippetIdSegmentSingular, memberNameMd)
                        groupContent.append(
                            "\n\n$INSERTION_MARKER_CHAR ${snippetParts.joinToString("/")}",
                        )
                        emitSnippets(
                            SnippetId(snippetParts, MD_EXTENSION),
                            SnippetId(snippetParts + listOf(SIG_ID_SUFFIX), MD_EXTENSION),
                            member,
                            element,
                        )
                    }
                }
                onto.add(
                    Snippet(
                        id = SnippetId(
                            listOf(TYPE_ID_PREFIX, typeNameMd, snippetIdSegmentPlural),
                            MD_EXTENSION,
                        ),
                        shortTitle = title,
                        source = from,
                        sourceStartOffset = typeInfo.pos.left,
                        mimeType = MimeType.markdown,
                        content = TextDocContent(groupingSnippetContent),
                        isIntermediate = false,
                        derivation = derivation,
                    ),
                )
            }

            fun addSnippetForMemberWithCommentAndSig(
                snippetId: SnippetId,
                sigSnippetId: SnippetId,
                titleMd: String,
                info: Info<*>,
                trailingContent: (StringBuilder) -> Unit,
            ) {
                val commentarySnippetId = commentaryIdFor(snippetId)
                onto.add(
                    Snippet(
                        id = snippetId,
                        shortTitle = titleMd,
                        source = from,
                        sourceStartOffset = info.pos.left,
                        mimeType = MimeType.markdown,
                        content = TextDocContent(
                            toStringViaBuilder { sb ->
                                sb.append("# $titleMd")
                                sb.append("\n\n")
                                sb.append(INSERTION_MARKER_CHAR)
                                    .append(' ')
                                    .append(sigSnippetId.parts.joinToString("/"))
                                sb.append("\n\n")
                                sb.append(INSERTION_MARKER_CHAR)
                                    .append(' ')
                                    .append(commentarySnippetId.parts.joinToString("/"))
                                sb.append("\n\n")
                                trailingContent(sb)
                            },
                        ),
                        isIntermediate = false,
                        derivation = derivation,
                    ),
                )
                onto.add(
                    Snippet(
                        id = commentarySnippetId,
                        shortTitle = titleMd,
                        source = from,
                        sourceStartOffset = (info.comment?.pos ?: info.pos).left,
                        mimeType = MimeType.markdown,
                        content = TextDocContent(info.commentText),
                        isIntermediate = false,
                        derivation = derivation,
                    ),
                )
            }

            makeMemberSnippets(
                "typeFormals",
                TYPE_FORMAL_ID_PART,
                "$shortTitleForType Type Parameters",
                TypeParameterShape::class,
            ) { snippetId, sigSnippetId, info, member ->
                val titleMd = MarkdownEscape.codeSpan(
                    "${typeParsedName.nameText}.<${member.symbol.text}>",
                )
                onto.add(
                    Snippet(
                        id = snippetId,
                        shortTitle = titleMd,
                        source = from,
                        sourceStartOffset = info.pos.left,
                        mimeType = MimeType.markdown,
                        content = TextDocContent(
                            """
                            |# $titleMd
                            |
                            |$INSERTION_MARKER_CHAR ${sigSnippetId.parts.joinToString("/")}
                            |
                            |${info.commentText}
                            |
                            """.trimMargin(),
                        ),
                        isIntermediate = false,
                        derivation = derivation,
                    ),
                )
                val definition = member.definition
                onto.add(
                    Snippet(
                        id = sigSnippetId,
                        shortTitle = titleMd,
                        source = from,
                        sourceStartOffset = info.pos.left,
                        mimeType = MimeType.markdown,
                        content = TextDocContent(
                            toStringViaBuilder { sb ->
                                definition.variance.keyword?.let {
                                    sb.append(MarkdownEscape.codeSpan(it))
                                        .append(' ')
                                }
                                for ((i, bound) in definition.upperBounds.withIndex()) {
                                    if (i == 0) {
                                        sb.append("`extends` ")
                                    } else {
                                        sb.append(" \\& ")
                                    }
                                    typeToMarkdownOnto(
                                        hackMapOldStyleToNew(bound),
                                        containingTypeFor,
                                        sb,
                                    )
                                }
                            },
                        ),
                        isIntermediate = false,
                        derivation = derivation,
                    ),
                )
            }

            makeMemberSnippets(
                "properties",
                PROPERTY_ID_PART,
                "$shortTitleForType Properties",
                PropertyShape::class,
            ) { snippetId, sigSnippetId, info, member ->
                val titleMd = MarkdownEscape.codeSpan(
                    "${typeParsedName.nameText}.${member.symbol.text}",
                )
                val getterInfo = typeInfo.memberInfo[member.symbol]?.firstOrNull {
                    val element = it.element
                    element is MethodShape && element.methodKind == MethodKind.Getter
                }
                val setterInfo = typeInfo.memberInfo[member.symbol]?.firstOrNull {
                    val element = it.element
                    element is MethodShape && element.methodKind == MethodKind.Setter
                }
                addSnippetForMemberWithCommentAndSig(snippetId, sigSnippetId, titleMd, info) { sb ->
                    // Generate snippets for setter and getter comments as needed and include them
                    // under the property definition.
                    for ((kw, accessorInfo) in listOf("get" to getterInfo, "set" to setterInfo)) {
                        val accessorComment = accessorInfo?.comment
                        val accessorCommentText = accessorComment?.content?.text
                        if (!accessorCommentText.isNullOrBlank()) {
                            val accessorId = snippetIdForMember(type, accessorInfo.element)
                            val accessorCommentaryId = commentaryIdFor(accessorId)
                            sb.append("\n\n$INSERTION_MARKER_CHAR ${accessorCommentaryId.shortCanonString(false)}")
                            onto.add(
                                Snippet(
                                    id = accessorCommentaryId,
                                    shortTitle = null,
                                    source = from,
                                    sourceStartOffset = accessorComment.pos.left,
                                    mimeType = MimeType.markdown,
                                    content = TextDocContent(
                                        toStringViaBuilder {
                                            sb.append("# `$kw`\n")
                                            sb.append(accessorCommentText)
                                        },
                                    ),
                                    isIntermediate = false,
                                    derivation = derivation,
                                ),
                            )
                        }
                    }
                }
                onto.add(
                    Snippet(
                        id = sigSnippetId,
                        shortTitle = titleMd,
                        source = from,
                        sourceStartOffset = info.pos.left,
                        mimeType = MimeType.markdown,
                        content = TextDocContent(
                            toStringViaBuilder { sb ->
                                val descriptor = member.descriptor
                                if (descriptor != null) {
                                    val parts = info.decl.parts
                                    if (
                                        parts?.metadataSymbolMap?.contains(varSymbol) == true ||
                                        setterInfo != null
                                    ) {
                                        sb.append("${MarkdownEscape.codeSpan(varSymbol.text)} ")
                                    }
                                    sb.append("`:` ")
                                    typeToMarkdownOnto(
                                        descriptor,
                                        containingTypeFor,
                                        sb,
                                    )
                                }
                            },
                        ),
                        isIntermediate = false,
                        derivation = derivation,
                    ),
                )
            }

            makeMemberSnippets(
                "methods",
                METHOD_ID_PART,
                "$shortTitleForType Methods",
                MethodShape::class,
                filter = {
                    when (it.methodKind) {
                        MethodKind.Normal,
                        MethodKind.Constructor,
                        -> true
                        // Handled in properties.
                        MethodKind.Getter,
                        MethodKind.Setter,
                        -> false
                    }
                },
            ) { snippetId, sigSnippetId, info, member ->
                val titleMd = MarkdownEscape.codeSpan(
                    "${typeParsedName.nameText}.${member.symbol.text}",
                )
                addSnippetForMemberWithCommentAndSig(snippetId, sigSnippetId, titleMd, info) {}
                onto.add(
                    Snippet(
                        id = sigSnippetId,
                        shortTitle = titleMd,
                        source = from,
                        sourceStartOffset = info.pos.left,
                        mimeType = MimeType.markdown,
                        content = TextDocContent(
                            toStringViaBuilder { sb ->
                                val descriptor = member.descriptor
                                if (descriptor != null) {
                                    sb.append("`:` ")
                                    typeToMarkdownOnto(
                                        descriptor,
                                        containingTypeFor,
                                        sb,
                                    )
                                }
                            },
                        ),
                        isIntermediate = false,
                        derivation = derivation,
                    ),
                )
            }

            makeMemberSnippets(
                "statics",
                STATIC_ID_PART,
                "$shortTitleForType Statics",
                StaticPropertyShape::class,
            ) { snippetId, sigSnippetId, info, member ->
                val titleMd = MarkdownEscape.codeSpan(
                    "${typeParsedName.nameText}.${member.symbol.text}",
                )
                addSnippetForMemberWithCommentAndSig(snippetId, sigSnippetId, titleMd, info) {}
                onto.add(
                    Snippet(
                        id = sigSnippetId,
                        shortTitle = titleMd,
                        source = from,
                        sourceStartOffset = info.pos.left,
                        mimeType = MimeType.markdown,
                        content = TextDocContent(
                            toStringViaBuilder { sb ->
                                val staticType = member.descriptor
                                if (staticType != null) {
                                    sb.append("${MarkdownEscape.codeSpan(staticSymbol.text)} ")
                                    sb.append("`:` ")
                                    typeToMarkdownOnto(
                                        staticType,
                                        containingTypeFor,
                                        sb,
                                    )
                                }
                            },
                        ),
                        isIntermediate = false,
                        derivation = derivation,
                    ),
                )
            }
        }

        // Also add the ungrouped top-levels while we're at it.
        onto.addAll(ungroupedSnippets)
    }

    /**
     * Create or edit a `/**...*/` comment to reflect the new snippet content.
     *
     * @return true when update worked
     */
    private fun updateCommentInImplicits(
        snippet: Snippet,
        newContent: MarkdownContent,
        into: StringBuilder,
        problemTracker: ProblemTracker,
    ): Boolean {
        ignore(problemTracker)

        val id = snippet.id
        val isTopLevel = id.parts.first() == "builtin"
        val commentedMemberId = if (isTopLevel) {
            id
        } else {
            if (id.parts.last() != COMMENTARY_ID_SUFFIX) {
                return false
            }
            id.copy(parts = id.parts.subList(0, id.parts.size - 1))
        }
        val comment = commentDeclarationRelationships.idToComment[commentedMemberId]
            ?: run {
                problemTracker.error(
                    "Cannot find `/**...*/` comment for ${
                        commentedMemberId.shortCanonString(false)
                    }.  Maybe add a comment before its declaration.",
                )
                return@updateCommentInImplicits false
            }

        val commentInInto =
            TemperContent(snippet.source, "$into", implicitsLanguageConfig).run {
                var commentCountDown = comment.commentIndexInFile
                for (token in lexer()) {
                    if (isDocumentableCommentToken(token)) {
                        if (commentCountDown == 0) {
                            return@run token
                        }
                        commentCountDown -= 1
                    }
                }
                null
            } ?: return false

        var startOfLine = commentInInto.pos.left
        while (startOfLine > 0 && into[startOfLine - 1].isTabOrSpace) {
            startOfLine -= 1
        }
        val linePrefix = into.substring(startOfLine, commentInInto.pos.left)

        val newContentAdjusted = run {
            val content = if (isTopLevel) {
                newContent.fileContent.replace(builtinHeading, "")
            } else {
                newContent.fileContent
            }
            // The TypeShapeExtractor treats all /**...*/ comments as snippets.
            // Having a snippet header complicates that.
            val match = snippetMarker.matchAt(content, 0)
            if (match != null) {
                content.substring(match.range.last + 1).trimStart()
            } else {
                content
            }
        }

        val replacementComment = toStringViaBuilder { sb ->
            sb.append("/**\n")
            newContentAdjusted.splitAfter(crLfOrLfPattern).forEach { line ->
                if (line != "") {
                    sb.append(linePrefix).append(" *")
                    if (line.isNotBlank()) { sb.append(' ') }
                    sb.append(line)
                }
            }
            val last = sb.last()
            if (last != '\n' && last != '\r') { sb.append('\n') }
            sb.append(linePrefix).append(" */")
        }

        into.replace(commentInInto.pos.left, commentInInto.pos.right, replacementComment)
        return true
    }

    override fun backPortInsertion(
        inserted: Snippet,
        priorInsertion: TextDocContent?,
        readInlined: () -> TextDocContent,
    ): RResult<TextDocContent, IllegalStateException> =
        if (priorInsertion != null) {
            RSuccess(priorInsertion)
        } else {
            RFailure(IllegalStateException(BACKPORT_ERROR_MESSAGE))
        }

    override fun backPortSnippetChange(
        snippet: Snippet,
        newContent: MarkdownContent,
        into: StringBuilder,
        problemTracker: ProblemTracker,
    ): Boolean {
        val problemCountBefore = problemTracker.problemCount
        val updateWorked = updateCommentInImplicits(snippet, newContent, into, problemTracker)
        if (!updateWorked && problemCountBefore == problemTracker.problemCount) {
            problemTracker.error(BACKPORT_ERROR_MESSAGE)
        }
        return updateWorked
    }

    override val supportsBackPorting: Boolean = false
}

private fun shapeFor(e: TEdge): TypeShape? {
    val t = e.target
    val reifiedType = t.reifiedTypeContained
    if (reifiedType != null) {
        val type = reifiedType.type2
        if (type is DefinedNonNullType) {
            return type.definition
        }
    }
    return null
}

internal const val MD_EXTENSION = ".md"

private fun snippetNameFor(temperName: ParsedName): String =
    MarkdownEscape.escape(temperName.toToken(inOperatorPosition = false).text)

private fun typeToMarkdownOnto(
    t: Descriptor,
    containingTypeFor: (ResolvedName) -> TypeShape?,
    onto: StringBuilder,
) {
    when (t) {
        WellKnownTypes.invalidType2 -> onto.append("*${OutToks.invalidWord.text}*")
        is Type2 -> {
            val d = t.definition
            if (hackSynthesizedFunInterfaceSymbol in d.metadata) {
                val sig = withType(t, fn = { _, sig, _ -> sig }, fallback = { null })
                if (sig != null) {
                    return typeToMarkdownOnto(sig, containingTypeFor, onto)
                }
            }

            val nameText: String = t.definition.name.toSymbol()!!.text
            val parsedName = ParsedName(nameText)
            val linkTarget = when (d) {
                is TypeShape -> "[snippet/$TYPE_ID_PREFIX/${snippetNameFor(parsedName)}]"
                is TypeFormal -> {
                    // It could be defined on a function or a type
                    val containingType = containingTypeFor(d.name)
                    if (containingType != null) {
                        "[snippet/$TYPE_ID_PREFIX/${
                            snippetNameFor(containingType.name.asParsedName)
                        }/$TYPE_FORMAL_ID_PART/${
                            snippetNameFor(parsedName)
                        }]"
                    } else {
                        null
                    }
                }
            }
            if (linkTarget != null) {
                onto.append("[")
            }
            onto.append('*').append(MarkdownEscape.escape(nameText)).append('*')
            if (linkTarget != null) {
                onto.append("]").append(linkTarget)
            }
            if (t.bindings.isNotEmpty()) {
                onto.append("&lt;")
                for ((i, binding) in t.bindings.withIndex()) {
                    if (i != 0) { onto.append(", ") }
                    typeToMarkdownOnto(binding, containingTypeFor, onto)
                }
                onto.append("&gt;")
            }
            if (t.nullity == Nullity.OrNull) {
                onto.append("&#63;")
            }
        }
        is Signature2 -> {
            onto.append("`fn")
            if (t.typeFormals.isNotEmpty()) {
                onto.append("<`")
                for ((i, formal) in t.typeFormals.withIndex()) {
                    if (i != 0) { onto.append(", ") }
                    typeToMarkdownOnto(
                        MkType2(formal).get(),
                        containingTypeFor,
                        onto,
                    )
                }
                onto.append("`>")
            }
            onto.append("(`")
            var sawFormalInParens = false
            for (valueFormal in t.allValueFormals) {
                if (sawFormalInParens) { onto.append(", ") }
                val (before, after) = when (valueFormal.kind) {
                    ValueFormalKind.Required -> null to null
                    ValueFormalKind.Optional -> null to " \\= \\.\\.\\."
                    ValueFormalKind.Rest -> "\\.\\.\\." to null
                }
                before?.let { onto.append(it) }
                typeToMarkdownOnto(valueFormal.type, containingTypeFor, onto)
                after?.let { onto.append(it) }
                sawFormalInParens = true
            }
            onto.append("`):` ")
            val (passType, failTypes) = withType(
                t.returnType2,
                result = { p, fs, _ -> p to fs },
                fallback = { it to null },
            )
            typeToMarkdownOnto(passType, containingTypeFor, onto)
            if (failTypes != null) {
                for ((i, failType) in failTypes.withIndex()) {
                    onto.append(
                        if (i == 0) { " `throws` " } else { " \\& " },
                    )
                    typeToMarkdownOnto(failType, containingTypeFor, onto)
                }
            }
        }
    }
}

private const val BACKPORT_ERROR_MESSAGE =
    "Cannot back-port changes to the builtin type definitions.  ${
        ""
    }Maybe edit Implicits.temper or move the changes into a nested $COMMENTARY_ID_SUFFIX snippet."

private val ModularName.asParsedName get() = (this as ResolvedParsedName).baseName
