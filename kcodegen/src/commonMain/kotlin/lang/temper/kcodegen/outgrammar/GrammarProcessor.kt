package lang.temper.kcodegen.outgrammar

import lang.temper.common.Either
import lang.temper.common.Log
import lang.temper.common.RFailure
import lang.temper.common.RSuccess
import lang.temper.common.asciiTitleCase
import lang.temper.common.toStringViaBuilder
import lang.temper.cst.ConcreteSyntaxTree
import lang.temper.cst.CstInner
import lang.temper.cst.CstLeaf
import lang.temper.format.CodeFormattingTemplate
import lang.temper.lexer.LexicalDefinitions
import lang.temper.lexer.Operator
import lang.temper.lexer.TokenType
import lang.temper.lexer.unpackQuotedString
import lang.temper.log.FilePositions
import lang.temper.log.LogSink
import lang.temper.log.MessageTemplate
import lang.temper.log.MessageTemplateI
import lang.temper.log.Position
import lang.temper.log.Positioned
import kotlin.math.min

internal val commonSuperTreeClassName = Id("BaseTree")
internal val commonSuperTreeInterfaceName = Id("Tree")
internal val commonSuperDataClassName = Id("BaseData")
internal val commonSuperDataInterfaceName = Id("Data")

/**
 * Responsible for converting a concrete syntax tree to domain specific objects related to the
 * output grammar, gathering node type definitions, enum type definitions, and metadata.
 */
internal class GrammarProcessor(
    val logSink: LogSink,
    private val nameDefault: String,
    val packageNameParts: List<String>,
    @Suppress("Unused") // For debugging
    val filePositions: FilePositions,
    private val positionToPrecedingComment: Map<Position, String>,
) {
    val startOfFile = Position(filePositions.codeLocation!!, 0, 0)

    /** @return (kotlinCode, namespaceName) */
    fun generateGrammar(root: CstInner): Pair<String, String>? {
        processTopLevels(root)
        val definitions = TypeDefinitionBuilder(this).build(types)
        return if (!logSink.hasFatal) {
            Pair(
                KotlinSourceGenerator(this).generateKotlinSource(definitions),
                getNamespaceName(),
            )
        } else {
            null
        }
    }

    private val metadata = mutableMapOf<String, Pair<String, Position>>()
    private val types = mutableMapOf<Id, NamedPropertyType>()
    private val codeFormattingTemplates = mutableMapOf<CodeFormattingTemplateKey, Id>()

    private fun getNodeType(name: Id, pos: Position): NodeType? {
        var type = types[name]
        if (type is UnknownType?) {
            val newNodeType = NodeType(name, pos)
            type?.resolution = newNodeType
            types[name] = newNodeType
            type = newNodeType
        }
        if (type !is NodeType) {
            alreadyDeclared(pos, name.text, type)
            return null
        }
        return type
    }

    private fun getPropertyType(name: Id, pos: Position): PropertyType =
        types.getOrPut(name) { UnknownType(name, pos) }

    private fun getEnumType(name: Id, pos: Position): EnumType? {
        var type = types[name]
        if (type is UnknownType?) {
            val newEnumType = EnumType(name, pos)
            type?.resolution = newEnumType
            types[name] = newEnumType
            type = newEnumType
        }
        if (type !is EnumType) {
            alreadyDeclared(pos, name.text, type)
            return null
        }
        return type
    }

    /** Gets a previously declared type without possibly adding an entry to the type table. */
    internal fun getDeclaredPropertyType(name: Id): PropertyType? = types[name]?.resolve()

    internal fun getMetadata(key: String) = metadata[key]

    internal fun getCodeFormattingTemplateId(t: CodeFormattingTemplateKey): Id =
        codeFormattingTemplates.getOrPut(t) {
            Id("sharedCodeFormattingTemplate${codeFormattingTemplates.size}")
        }

    internal fun getAllCodeFormattingTemplates() = codeFormattingTemplates.toMap()

    private fun declareProperty(
        pos: Position,
        nodeType: NodeType,
        propertyName: Id,
        propertyType: PropertyType?,
    ): Property {
        if (BACKED_PROPERTY_NAME_PREFIX in propertyName.text) {
            // We reserve wunderbar names for backing properties so that we can play getter/setter
            // games to keep nodes linked to their parent.
            fail(
                pos,
                OutputGrammarCodeGeneratorMessageTemplate.IllegalPropertyName,
                listOf(propertyName.text),
            )
        }

        val prop = nodeType.localProperties.getOrPut(propertyName) {
            Property(pos, propertyName, propertyType)
        }
        val prevPropertyType = prop.propertyType
        if (propertyType != prevPropertyType) {
            when {
                propertyType == null -> Unit
                prevPropertyType == null -> prop.propertyType = propertyType
                propertyType is UnknownType &&
                    propertyType.name == (prevPropertyType as? NamedPropertyType)?.name -> Unit
                prevPropertyType is UnknownType &&
                    prevPropertyType.name == (propertyType as? NamedPropertyType)?.name ->
                    prop.propertyType = propertyType
                else -> alreadyDeclared(
                    pos,
                    "${nodeType.nameWithKind}.${propertyName.text}",
                    prevPropertyType,
                )
            }
        }

        return prop
    }

    private fun processTopLevels(t: ConcreteSyntaxTree) {
        when (t.operator) {
            Operator.Root -> t.operands.forEach {
                if (logSink.hasFatal) {
                    return@processTopLevels
                }
                processTopLevels(it)
            }
            Operator.Semi -> t.operands.forEach {
                if (logSink.hasFatal) {
                    return@processTopLevels
                }
                processStmt(it)
            }
            else -> fail(t, MessageTemplate.Unparsable, listOf("TopLevel"))
        }
    }

    /**
     * @return null, or a named type, or a property so that decorations like `@data`
     * can recursively process the decorated item and then modify the returned declaration.
     */
    private fun processStmt(t: ConcreteSyntaxTree): Either<NamedPropertyType, Property>? {
        if (t.tokenText == ";") {
            // Separator
            return null
        }
        // let <Id> = ...
        // enum <Id> = ...
        // <Id>.<MemberName> = ...
        // <Id>.<MemberName>.default = ...
        // <Id> = <SuperTypes>
        if (isInfix(t, Operator.Eq)) {
            // Recognize `let name = ...` and `enum name`
            val left = t.child(0)
            val right = t.child(2)
            if (left.childCount == 2) {
                when (left.child(0).tokenText) {
                    "let" -> {
                        val name = left.child(1).tokenText
                        val rightValue = stringValue(right, permissive = true)
                        if (name != null && rightValue != null) {
                            metadata[name] = rightValue to right.pos
                        } else {
                            stopAt(t, "declaration statement")
                        }
                        return null
                    }
                    "enum" -> {
                        val name = Id(left.child(1).tokenText, left, logSink)
                            ?: return null
                        val members = unpackEnumMembers(right)
                        val enumType = getEnumType(name, left.child(1).pos)
                            ?: return null
                        maybeAssociateDocCommentAt(left.child(0), enumType)
                        if (enumType.members != null) {
                            alreadyDeclared(left.pos, "${name.text} members", enumType.pos)
                        } else {
                            enumType.members = members.mapNotNull { (name, token, pos) ->
                                when (token) {
                                    null -> name to null
                                    "" -> name to EnumType.emptyToken
                                    else ->
                                        when (val guess = CodeFormattingTemplate.guessOutputToken(token)) {
                                            is RSuccess -> name to guess.result
                                            is RFailure -> {
                                                stopAt(pos, "enum element")
                                                null
                                            }
                                        }
                                }
                            }
                        }
                        return Either.Left(enumType)
                    }
                }
            }
            if (isInfix(left, Operator.Dot)) {
                // Type.member.default = ...
                val beforeLastDot = left.child(0)
                if (isInfix(beforeLastDot, Operator.Dot) && "default" == unpackName(left.child(2))?.text) {
                    val typeName = unpackName(beforeLastDot.child(0)) ?: return null
                    val memberName = unpackName(beforeLastDot.child(2)) ?: return null
                    val nodeType = processDefaultExpression(left.pos, typeName, memberName, right)
                    nodeType?.isExplicitlyDeclared = true
                    return null
                }
            }
            if (isInfix(left, Operator.Dot)) {
                // Handle Type.member = ...
                val typeName = unpackName(left.child(0)) ?: return null
                val memberName = unpackName(left.child(2)) ?: return null
                val docComment = docCommentAt(left.child(0))
                return processMemberOverride(t.pos, typeName, memberName, right, docComment)?.let {
                    Either.Right(it)
                }
            }
            if (isInfix(left, Operator.Pct) && isInfix(left.child(0), Operator.Dot)) {
                // Handle Type.member%PropertyType = ...
                val dot = left.child(0)
                val typeName = unpackName(dot.child(0)) ?: return null
                val memberName = unpackName(dot.child(2)) ?: return null
                val (propertyType, count) = unpackPropertyTypeAndCount(left.child(2))
                    ?: return null
                val nodeType = getNodeType(typeName, dot.child(0).pos) ?: return null
                nodeType.isExplicitlyDeclared = true
                val prop = declareProperty(t.pos, nodeType, memberName, propertyType)
                val docComment = docCommentAt(dot.child(0))
                processMemberOverride(t.pos, typeName, memberName, right, docComment)
                if (count != null) {
                    if (prop.propertyCount != null && prop.propertyCount != count) {
                        alreadyDeclared(
                            left.pos,
                            "${nodeType.nameWithKind}.${prop.propertyName.text}",
                            prop,
                        )
                    } else {
                        prop.propertyCount = count
                    }
                }
                return Either.Right(prop)
            }
            return processSuperTypeDeclaration(t.child(0), t.child(2), docCommentAt(t.child(0)))
                ?.let { Either.Left(it) }
        }
        // <Id> ::= <Syntax>
        if (isInfix(t, Operator.Eq, "::=")) {
            return processSyntaxDeclaration(t.child(0), t.child(2), docCommentAt(t.child(0)))
                ?.let { Either.Left(it) }
        }
        // <Id> implements <SuperType>
        if (isInfix(t, Operator.ExtendsComma) || isInfix(t, Operator.ImplementsComma)) {
            val (subTypeNode, _, superTypeNode) = t.operands
            val subTypeName = unpackName(subTypeNode) ?: return null
            val subType = getNodeType(subTypeName, subTypeNode.pos) ?: return null
            subType.isExplicitlyDeclared = true
            val superType: SuperType = stringValue(superTypeNode, delim = '`')
                ?.let {
                    KotlinCode(it, superTypeNode.pos)
                }
                ?: unpackName(superTypeNode)?.let {
                    getNodeType(it, superTypeNode.pos)?.let { nodeType ->
                        NodeTypeReference(superTypeNode.pos, nodeType)
                    }
                }
                ?: return null
            subType.superTypes.add(superType)
            maybeAssociateDocCommentAt(t, subType)
            return Either.Left(subType)
        }
        // <Id> requires <Requirement>
        @Suppress("MagicNumber") // childCount for 3 words in a row
        if (
            t.operator == Operator.Leaf &&
            t.childCount == 3 &&
            t.child(1).tokenText == "requires"
        ) {
            processRequirement(t.child(0), t.child(2))
            return null
        }
        // <Id> requires `...`
        // parses as <Id> requires (`...`)
        if (isInfix(t, Operator.Paren)) {
            val left = t.child(0)
            if (
                left.operator == Operator.Leaf && left.childCount == 2 &&
                left.child(1).tokenText == "requires"
            ) {
                processRequirement(left.child(0), t.child(2))
                return null
            }
        }

        // <Id> from <Id>
        if (
            t.operator == Operator.Leaf &&
            t.childCount == THREE_LEAF_COUNT &&
            t.child(1).tokenText == "from"
        ) {
            return processDerivation(t) {
                DerivesFrom(it)
            }?.let { Either.Left(it) }
        }
        // <Id> =~ <Id>
        // Specifies an equivalent to relationship.
        if (isInfix(t, Operator.EqTilde)) {
            return processDerivation(t) {
                EquivalentTo(it)
            }?.let { Either.Left(it) }
        }

        // <Id> { <Code> }
        // <Id>(<Properties>) { <Code> }
        @Suppress("MagicNumber") // childCount for an id node, brackets, and the code
        if (
            t.operator == Operator.Curly &&
            t.childCount == 4 &&
            t.child(1).tokenText == "{" &&
            t.child(3).tokenText == "}"
        ) {
            val nodeType =
                processExtraNodeCode(t.child(0), t.child(2), docCommentAt(t.child(0)))
            nodeType?.isExplicitlyDeclared = true
            return nodeType?.let { Either.Left(it) }
        }
        // <Id>(<Properties>)
        if (isInfix(t, Operator.Paren) || isEmptyParen(t)) {
            val nodeTypeName = unpackName(t.child(0)) ?: return null
            val nodeType = getNodeType(nodeTypeName, t.child(0).pos) ?: return null
            nodeType.isExplicitlyDeclared = true
            maybeAssociateDocCommentAt(t.child(0), nodeType)
            if (isInfix(t, Operator.Paren)) {
                processCommaSeparatedPropertyDefinitions(nodeType, t.child(2))
            }
            return Either.Left(nodeType)
        }
        // <Id>
        if (t.operator == Operator.Leaf && t.childCount == 1) {
            val nodeName = unpackName(t) ?: return null
            val nodeType = getNodeType(nodeName, t.pos) ?: return null
            maybeAssociateDocCommentAt(t, nodeType)
            nodeType.isExplicitlyDeclared = true
            return Either.Left(nodeType)
        }
        // @data DeclarationThatAffectsANodeType
        if (t.operator == Operator.At && t.childCount == BINARY_OP_ARG_COUNT &&
            unpackName(t.child(1))?.text == "data"
        ) {
            val decorated = processStmt(t.child(2))
            val nodeType = (decorated as? Either.Left)?.item as? NodeType
            if (nodeType != null) {
                nodeType.typeKind = TypeKind.Data
            } else {
                stopAt(t, "node type declaration")
            }
            return decorated
        }
        // @override DeclarationThatAffectsANodeType
        if (t.operator == Operator.At && t.childCount == BINARY_OP_ARG_COUNT &&
            unpackName(t.child(1))?.text == "override"
        ) {
            val decorated = processStmt(t.child(2))
            val property = (decorated as? Either.Right)?.item
            if (property != null) {
                property.isOverride = true
            } else {
                stopAt(t, "property declaration")
            }
            return decorated
        }
        stopAt(t, "declaration statement")
        return null
    }

    private fun processMemberOverride(
        pos: Position,
        typeName: Id,
        memberName: Id,
        right: ConcreteSyntaxTree,
        docComment: DocComment?,
    ): Property? {
        val nodeType = getNodeType(typeName, pos) ?: return null
        nodeType.isExplicitlyDeclared = true
        val code = unpackCode(right) ?: return null
        val debugName = "${typeName.text}.${memberName.text}"
        return when (memberName.text) {
            "operatorDefinition" -> {
                if (nodeType.operatorDefinition != null) {
                    alreadyDeclared(pos, debugName, nodeType.operatorDefinition!!)
                } else {
                    nodeType.operatorDefinition = code
                }
                null
            }
            "renderTo" -> {
                if (nodeType.renderTo != null) {
                    alreadyDeclared(pos, debugName, nodeType.renderTo!!)
                } else {
                    nodeType.renderTo = code
                }
                null
            }
            else -> {
                val old = nodeType.propertyOverrides[memberName]
                if (old != null) {
                    alreadyDeclared(pos, debugName, old)
                    null
                } else {
                    nodeType.propertyOverrides[memberName] = code
                    val prop = declareProperty(pos, nodeType, memberName, null)
                    maybeAssociateDocComment(docComment, prop)
                    return prop
                }
            }
        }
    }

    private fun processDefaultExpression(
        pos: Position,
        typeName: Id,
        memberName: Id,
        right: ConcreteSyntaxTree,
    ): NodeType? {
        val nodeType = getNodeType(typeName, pos) ?: return null
        val old = nodeType.defaultExpressions[memberName]
        if (old != null) {
            alreadyDeclared(pos, "${nodeType.nameWithKind}.${memberName.text}", old)
        } else {
            nodeType.defaultExpressions[memberName] = unpackCode(right) ?: return null
        }
        return nodeType
    }

    private fun processSyntaxDeclaration(
        name: ConcreteSyntaxTree,
        sDecl: ConcreteSyntaxTree,
        docComment: DocComment?,
    ): NodeType? {
        val nameText = unpackName(name)
            ?: run {
                stopAt(name, "name")
                return@processSyntaxDeclaration null
            }
        val nodeType = getNodeType(nameText, name.pos)
            ?: return null
        nodeType.isExplicitlyDeclared = true
        maybeAssociateDocComment(docComment, nodeType)
        val old = nodeType.syntaxDeclaration
        if (old != null) {
            alreadyDeclared(
                name.pos,
                "${nodeType.nameWithKind}.${nameText.text}",
                old,
            )
        } else {
            toSyntaxDeclaration(nodeType, sDecl)?.let { syntaxDeclaration ->
                nodeType.syntaxDeclaration = syntaxDeclaration
            }
        }
        return nodeType
    }

    private fun processSuperTypeDeclaration(
        name: ConcreteSyntaxTree,
        subTypes: ConcreteSyntaxTree,
        docComment: DocComment?,
    ): NodeType? {
        val nameText = unpackName(name)
            ?: run {
                stopAt(name, "super type name")
                return@processSuperTypeDeclaration null
            }
        val nodeType = getNodeType(nameText, name.pos) ?: return null
        nodeType.isExplicitlyDeclared = true
        maybeAssociateDocComment(docComment, nodeType)
        for ((rightName, namePos) in unpackNameUnion(subTypes)) {
            val subNodeType = getNodeType(rightName, namePos) ?: break
            subNodeType.superTypes.add(NodeTypeReference(namePos, nodeType))
            nodeType.enumeratedSubTypes.add(NodeTypeReference(namePos, subNodeType))
        }
        return nodeType
    }

    private fun processDerivation(
        t: ConcreteSyntaxTree,
        makeDerivation: (NodeTypeReference) -> NodeTypeDerivation,
    ): NodeType? {
        val (derivedTree, _, sourceTree) = t.operands
        val derived = unpackName(derivedTree) ?: return null
        val source = unpackName(sourceTree) ?: return null
        val derivedNodeType = getNodeType(derived, derivedTree.pos) ?: return null
        derivedNodeType.isExplicitlyDeclared = true
        val sourceNodeType = getNodeType(source, sourceTree.pos) ?: return null
        derivedNodeType.derivation?.sourceReference?.let { sourceReference ->
            fail(
                t,
                OutputGrammarCodeGeneratorMessageTemplate.InconsistentDerivation,
                listOf(
                    derived,
                    sourceReference.nodeType.name,
                    sourceReference.pos,
                ),
            )
            return null
        }
        if (derivedNodeType.typeKind == null) {
            derivedNodeType.typeKind = TypeKind.Ast // Handler for @data may override
        }
        derivedNodeType.derivation = makeDerivation(
            NodeTypeReference(sourceTree.pos, sourceNodeType),
        )
        maybeAssociateDocCommentAt(t.child(0), derivedNodeType)
        return derivedNodeType
    }

    private fun unpackName(t: ConcreteSyntaxTree): Id? {
        var leaf = t
        if (leaf.operator == Operator.Leaf) {
            if (leaf.childCount == 1) {
                leaf = leaf.child(0)
            }
            if (leaf.operator == Operator.Leaf) {
                val text = leaf.tokenText
                if (text != null) {
                    return Id(text, leaf, logSink)
                }
            }
        }
        fail(t, MessageTemplate.InvalidIdentifier)
        return null
    }

    private fun toSyntaxDeclaration(nodeType: NodeType, t: ConcreteSyntaxTree): SyntaxDeclaration? {
        when (t.operator) {
            Operator.Star, Operator.Plus -> if (t.childCount == BINARY_OP_ARG_COUNT) {
                val left = t.child(0)
                val right = t.child(2)
                when (t.child(1).tokenText) {
                    "+" -> return toRepetition(nodeType, t.pos, left, right, mayBeEmpty = false)
                    "*" -> return toRepetition(nodeType, t.pos, left, right, mayBeEmpty = true)
                }
            }
            Operator.QuotedGroup -> {
                // A literal value
                //     "foo"
                // or a reference to a special token like
                //     `SpecialTokens.indent`
                val backQuoted = stringValue(t, delim = '`')
                if (backQuoted != null) {
                    return SpecialTokenExpr(t.pos, KotlinCode(backQuoted, t.pos))
                }
                val stringValue = stringValue(t, delim = '"')
                    ?: run {
                        fail(t, MessageTemplate.Unparsable, listOf("string literal"))
                        return@toSyntaxDeclaration null
                    }
                return LiteralText(t.pos, stringValue)
            }
            Operator.Leaf -> {
                // A use of a property with a type,
                //     foo
                val propertyName = unpackName(t) ?: return null
                declareProperty(t.pos, nodeType, propertyName, null)
                return PropertyUse(t.pos, propertyName)
            }
            Operator.Pct -> if (isInfix(t, Operator.Pct)) {
                val propertyName = unpackName(t.child(0)) ?: return null
                val (propertyType, count) = unpackPropertyTypeAndCount(t.child(2))
                    ?: return null
                val prop = declareProperty(t.pos, nodeType, propertyName, propertyType)
                if (count != null) {
                    if (prop.propertyCount != null && prop.propertyCount != count) {
                        alreadyDeclared(
                            t.pos.rightEdge,
                            "${nodeType.nameWithKind}.${prop.propertyName.text} count",
                            prop.pos,
                        )
                    } else {
                        prop.propertyCount = count
                    }
                }
                return PropertyUse(t.pos, propertyName)
            }
            Operator.Amp -> if (isInfix(t, Operator.Amp)) {
                val left = toSyntaxDeclaration(nodeType, t.child(0)) ?: return null
                val right = toSyntaxDeclaration(nodeType, t.child(2)) ?: return null
                return Concatenation(t.pos, left, right)
            }
            Operator.BarBar -> if (isInfix(t, Operator.BarBar)) {
                var left = toSyntaxDeclaration(nodeType, t.child(0)) ?: return null
                val right = toSyntaxDeclaration(nodeType, t.child(2)) ?: return null
                if (left !is Conditional) {
                    // Infer a condition.
                    val condition = left.toImplicitCondition()
                    if (condition != null) {
                        left = Conditional(left.pos, condition, left)
                    }
                }
                return Alternation(t.pos, left, right)
            }
            Operator.Arrow -> if (isInfix(t, Operator.Arrow)) {
                val left = toCondition(nodeType, t.child(0)) ?: return null
                val right = toSyntaxDeclaration(nodeType, t.child(2)) ?: return null
                return Conditional(t.pos, left, right)
            }
            Operator.ParenGroup -> if (
                t.childCount >= 2 &&
                t.child(0).tokenText == "(" && t.child(t.childCount - 1).tokenText == ")"
            ) {
                @Suppress("MagicNumber") // child counts
                when (t.childCount) {
                    2 -> return Epsilon(t.pos) // `()` means empty concatenation
                    3 -> return toSyntaxDeclaration(nodeType, t.child(1))
                }
            }
            else -> Unit
        }
        fail(t, MessageTemplate.Unparsable, values = listOf("Syntax"))
        return null
    }

    private fun toRepetition(
        nodeType: NodeType,
        pos: Position,
        repeated: ConcreteSyntaxTree,
        joiner: ConcreteSyntaxTree,
        mayBeEmpty: Boolean,
    ): SyntaxDeclaration? {
        val repeatedSyntax = toSyntaxDeclaration(nodeType, repeated) ?: return null
        if (repeatedSyntax !is PropertyUse) {
            // We don't support arbitrary repetition
            fail(repeated, MessageTemplate.Unparsable, values = listOf("property use"))
            return null
        }
        val joinerText = stringValue(joiner)
            ?: run {
                fail(joiner, MessageTemplate.Unparsable, values = listOf("quoted string"))
                return@toRepetition null
            }
        // When a repetition occurs in a non-syntax property declaration, recognize its count.
        //     NodeType(propertyNames%Foo*"");
        // expresses the idea that NodeType has a List<Foo>.
        nodeType.localProperties[repeatedSyntax.propertyName]?.propertyCount = PropertyCount.Many
        return Repetition(pos, repeatedSyntax, joinerText, mayBeEmpty)
    }

    private fun toCondition(nodeType: NodeType, t: ConcreteSyntaxTree): Condition? {
        when {
            isInfix(t, Operator.EqEq) -> {
                // foo=value => ...    means do ... when foo matches value
                val left = t.child(0)
                val propertyName = when (left.operator) {
                    Operator.Pct ->
                        (toSyntaxDeclaration(nodeType, left) as PropertyUse?)?.propertyName
                    else -> {
                        val name = unpackName(left) ?: return null
                        declareProperty(left.pos, nodeType, name, null)
                        name
                    }
                } ?: return null
                val valueTree = t.child(2)
                val simpleValue = toSimpleValue(valueTree) ?: return null
                return Condition(t.pos, propertyName, simpleValue)
            }
            isInfix(t, Operator.Pct) -> {
                // thing%Thing => ...   means do ... when thing is non-null/empty,
                // but also declares thing's type
                val use = toSyntaxDeclaration(nodeType, t) as? PropertyUse
                    ?: return null
                return Condition(t.pos, use.propertyName, Truthy)
            }
            else -> {
                // foo => ...   means do ... when foo is non-null/empty
                val name = unpackName(t) ?: return null
                declareProperty(t.pos, nodeType, name, null)
                return Condition(t.pos, name, Truthy)
            }
        }
    }

    private fun toSimpleValue(t: ConcreteSyntaxTree): SimpleValue? {
        if (isInfix(t, Operator.Dot)) {
            val enumName = unpackName(t.child(0)) ?: return null
            val memberName = unpackName(t.child(2)) ?: return null
            return EnumReference(enumName, memberName)
        }
        val name = unpackName(t) ?: return null
        return when (name.text) {
            "false" -> SimpleBoolean(false)
            "true" -> SimpleBoolean(true)
            // Else, it's a reference to an enum member whose enum type should be knowable
            // based on context.  We'll resolve later when we know property names.
            else -> EnumReference(null, name)
        }
    }

    private fun processRequirement(left: ConcreteSyntaxTree, right: ConcreteSyntaxTree) {
        val nodeName = Id(left.tokenText, left, logSink)
            ?: run {
                fail(left, MessageTemplate.MissingName)
                return@processRequirement
            }
        val nodeType = getNodeType(nodeName, left.pos) ?: return
        nodeType.isExplicitlyDeclared = true
        val requirement = when (val asCode = stringValue(right, delim = '`')) {
            null -> {
                val pr = PropertyRequirement(unpackName(right) ?: return)
                // T requires p
                // indicates that, though p may have count ZeroOrOne in a super-type of T, it has
                // count one in T.
                val property = nodeType.localProperties.getOrPut(pr.propertyName) {
                    Property(left.pos, pr.propertyName, null)
                }
                if (property.propertyCount == null) {
                    property.propertyCount = PropertyCount.One
                }
                pr
            }
            else -> CodeRequirement(KotlinCode(asCode, right.pos))
        }
        nodeType.requirements.add(requirement)
    }

    private fun unpackNameUnion(t: ConcreteSyntaxTree): Map<Id, Position> = buildMap {
        fun unpack(u: ConcreteSyntaxTree) {
            if (isInfix(u, Operator.Bar)) {
                unpack(u.child(0))
                unpack(u.child(2))
            } else {
                val name = unpackName(u)
                if (name != null) {
                    this[name] = u.pos
                }
            }
        }
        unpack(t)
    }

    private fun unpackEnumMembers(t: ConcreteSyntaxTree): List<Triple<Id, String?, Position>> = buildList {
        fun unpack(u: ConcreteSyntaxTree) {
            if (isInfix(u, Operator.Bar)) {
                unpack(u.child(0))
                unpack(u.child(2))
            } else if (isInfix(u, Operator.Paren)) {
                val (nameTree, _, tokenTree, _) = u.operands
                val name = unpackName(nameTree)
                val tokenText = stringValue(tokenTree)
                if (name != null && tokenText != null) {
                    add(Triple(name, tokenText, u.pos))
                }
            } else {
                val name = unpackName(u)
                if (name != null) {
                    add(Triple(name, null, u.pos))
                }
            }
        }
        unpack(t)
    }

    private fun processExtraNodeCode(
        left: ConcreteSyntaxTree,
        right: ConcreteSyntaxTree,
        docComment: DocComment?,
    ): NodeType? {
        val name: Id = if (isInfix(left, Operator.Paren)) {
            // Handle the property definitions in
            // <NodeType>(<PropertyDefinitions>) { ... }
            val name = unpackName(left.child(0)) ?: return null
            val nodeType = getNodeType(name, left.child(0).pos) ?: return null
            processCommaSeparatedPropertyDefinitions(nodeType, left.child(2))
            name
        } else {
            unpackName(left) ?: return null
        }
        val code = unpackCode(right) ?: return null
        val node = getNodeType(name, left.pos) ?: return null
        maybeAssociateDocComment(docComment, node)
        if (node.extraBodyContent != null) {
            alreadyDeclared(
                right.pos,
                "${node.nameWithKind} body",
                node.extraBodyContent!!,
            )
        } else {
            node.extraBodyContent = code
        }
        return node
    }

    private fun processCommaSeparatedPropertyDefinitions(
        nodeType: NodeType,
        t: ConcreteSyntaxTree,
    ) {
        if (t.tokenText == ",") { return }
        if (t.operator == Operator.Comma) {
            t.operands.forEach {
                processCommaSeparatedPropertyDefinitions(nodeType, it)
            }
            return
        }
        // Create a syntax declaration for its side effect of entering it into the property
        // table and then drop the resulting value.
        when (toSyntaxDeclaration(nodeType, t)) {
            is PropertyUse, is Repetition -> Unit
            else -> fail(t.pos, MessageTemplate.Unparsable, listOf("property declaration"))
        }
    }

    internal fun fail(p: Positioned, template: MessageTemplateI, values: List<Any> = emptyList()) {
        logSink.log(level = Log.Fatal, template = template, pos = p.pos, values = values)
    }

    private fun alreadyDeclared(pos: Position, declared: String, old: Positioned) {
        fail(pos, OutputGrammarCodeGeneratorMessageTemplate.AlreadyDeclared, listOf(declared, old.pos))
    }

    private fun stopAt(t: Positioned, production: String) {
        logSink.log(
            level = Log.Fatal,
            template = MessageTemplate.Unparsable,
            pos = t.pos,
            values = listOf(production),
        )
    }

    internal fun getNamespaceName() = (metadata["namespace"]?.first ?: nameDefault).asciiTitleCase()
    internal fun getOperatorDefinitionType() =
        metadata["operatorDefinitionType"]?.toKotlinCode()
            ?: KotlinCode("${getNamespaceName()}OperatorDefinition", startOfFile)
    internal fun getFormattingHintsType() =
        metadata["formattingHintsType"]?.toKotlinCode()
            ?: KotlinCode("${getNamespaceName()}FormattingHints", startOfFile)

    private fun unpackPropertyTypeAndCount(
        t: ConcreteSyntaxTree,
    ): Pair<PropertyType, PropertyCount?>? {
        return when (val asCode = stringValue(t, delim = '`')) {
            null -> {
                var propertyCount: PropertyCount? = null
                val name: Id = if (t.childCount == 1 && t.operator == Operator.Leaf) {
                    unpackCountFromNameText(t.child(0).tokenText ?: "")
                        .let { (nameText, count) ->
                            propertyCount = count
                            Id(nameText, t, logSink)
                        }
                } else {
                    unpackName(t)
                } ?: return null
                getPropertyType(name, t.pos) to propertyCount
            }
            else -> {
                val codeAndCount = unpackCountFromNameText(asCode)
                KotlinCode(codeAndCount.first, t.pos) to codeAndCount.second
            }
        }
    }

    private fun unpackCountFromNameText(
        decoratedNameText: String,
    ): Pair<String, PropertyCount?> {
        val lastChar = decoratedNameText.lastOrNull()
        // <Type>! means <Type> where there is reliably one.
        return if (lastChar == '?' || lastChar == '!') {
            val propertyCount = if (lastChar == '!') {
                PropertyCount.One
            } else {
                PropertyCount.ZeroOrOne
            }
            decoratedNameText.dropLast(1) to propertyCount
        } else {
            decoratedNameText to null
        }
    }

    private fun unpackCode(t: ConcreteSyntaxTree): KotlinCode? {
        val code = stringValue(t, delim = '`')
            ?: run {
                fail(t, MessageTemplate.Unparsable, listOf("`back-quoted code`"))
                return@unpackCode null
            }
        return KotlinCode(code.trim(), t.pos)
    }

    @Suppress("MagicNumber") // childCount for 2 parens and one child in the middle
    private fun stringValue(
        t: ConcreteSyntaxTree,
        delim: Char = '"',
        permissive: Boolean = false,
    ): String? = when {
        t is CstInner && t.operator == Operator.QuotedGroup -> {
            val (delimGot, content) = tokenContentForString(t)
            if (delimGot?.startsWith(delim) == true) {
                content
            } else {
                null
            }
        }
        t.operator == Operator.ParenGroup && t.childCount == 3 -> stringValue(t.child(1), delim)
        t is CstLeaf && permissive -> t.tokenText
        else -> null
    }

    private fun stringValue(tokenText: String): String {
        val unpacked = unpackQuotedString(tokenText, skipDelimiter = false)
        return if (unpacked.isOk) {
            unpacked.decoded
        } else {
            throw IllegalArgumentException(tokenText)
        }
    }

    private fun tokenContentForString(
        t: CstInner,
    ): Pair<String?, String?> {
        if (
            t.childCount >= 2 &&
            t.child(0).tokenType == TokenType.LeftDelimiter &&
            t.child(t.childCount - 1).tokenType == TokenType.RightDelimiter
        ) {
            val delim = t.child(0).tokenText
            var contentText = toStringViaBuilder { sb ->
                fun appendTokens(cst: ConcreteSyntaxTree) {
                    val tokenText = cst.tokenText
                    if (tokenText != null) {
                        sb.append(tokenText)
                    }
                    for (i in 0 until cst.childCount) {
                        appendTokens(cst.child(i))
                    }
                }
                for (i in 1 until t.childCount - 1) {
                    appendTokens(t.child(i))
                }
            }
            if (delim == "`") {
                contentText = stripLastLinePrefix(contentText)
            }
            return delim to stringValue(contentText)
        }
        return null to null
    }

    private fun maybeAssociateDocCommentAt(p: Positioned, commentable: Commentable) {
        maybeAssociateDocComment(docCommentAt(p), commentable)
    }

    private fun docCommentAt(p: Positioned) =
        positionToPrecedingComment[p.pos.leftEdge]?.let { DocComment(it) }
}

private fun isInfix(t: ConcreteSyntaxTree, op: Operator, wanted: String = op.text!!): Boolean {
    @Suppress("MagicNumber") // LEFT OP RIGHT   or   LEFT OPEN RIGHT CLOSE
    val childrenWanted = if (op.closer) {
        4
    } else {
        3
    }
    return t.operator == op && t.childCount == childrenWanted && t.child(1).tokenText == wanted
}

private fun isEmptyParen(t: ConcreteSyntaxTree) =
    t.operator == Operator.Paren &&
        t.operands.size == THREE_LEAF_COUNT && // LEFT OPEN CLOSE
        t.operands[1].tokenText == "(" &&
        t.operands[2].tokenText == ")"

internal val newlinePattern = Regex("\r\n?|\n")
internal val closeCurlyPattern = Regex("""^(\s*)[}]\s*$""")

internal const val BINARY_OP_ARG_COUNT = 3
internal const val THREE_LEAF_COUNT = 3

private fun stripLastLinePrefix(s: String): String {
    val lines = s.split("\n", "\r\n", "\r")
    var commonPrefixLen = Int.MAX_VALUE
    for (line in lines) {
        if (line.isTemperBlank()) {
            continue
        }
        commonPrefixLen = min(commonPrefixLen, line.length)
        var nSpaceCharsAtFront = 0
        while (nSpaceCharsAtFront < commonPrefixLen) {
            if (LexicalDefinitions.isSpace(line[nSpaceCharsAtFront])) {
                nSpaceCharsAtFront += 1
            } else {
                break
            }
        }
        commonPrefixLen = min(commonPrefixLen, nSpaceCharsAtFront)
    }
    val strippedLines = lines.map { line ->
        if (line.isTemperBlank()) {
            ""
        } else {
            line.substring(commonPrefixLen)
        }
    }
    return strippedLines.joinToString("\n")
}

private fun String.isTemperBlank(): Boolean = all { LexicalDefinitions.isSpace(it) }
