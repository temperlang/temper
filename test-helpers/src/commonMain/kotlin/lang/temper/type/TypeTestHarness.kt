package lang.temper.type

import lang.temper.common.AtomicCounter
import lang.temper.common.Log
import lang.temper.common.OpenOrClosed
import lang.temper.common.TestDocumentContext
import lang.temper.common.ignore
import lang.temper.common.structure.FormattingStructureSink
import lang.temper.common.testModuleName
import lang.temper.cst.ConcreteSyntaxTree
import lang.temper.cst.CstLeaf
import lang.temper.format.OutToks
import lang.temper.lexer.Genre
import lang.temper.lexer.Lexer
import lang.temper.lexer.Operator
import lang.temper.log.LogSink
import lang.temper.log.MessageTemplateI
import lang.temper.log.Position
import lang.temper.name.BuiltinName
import lang.temper.name.ModuleLocation
import lang.temper.name.NamingContext
import lang.temper.name.ParsedName
import lang.temper.name.ResolvedName
import lang.temper.name.ResolvedNameMaker
import lang.temper.name.ResolvedParsedName
import lang.temper.name.Symbol
import lang.temper.parser.parse
import lang.temper.type2.AdHocArrowTypes
import lang.temper.type2.MkType2
import lang.temper.type2.Nullity
import lang.temper.type2.Signature2
import lang.temper.type2.Type2
import lang.temper.type2.hackMapOldStyleToNew
import lang.temper.type2.withNullity
import lang.temper.value.Document
import lang.temper.value.StayLeaf
import lang.temper.value.Tree
import lang.temper.value.functionalInterfaceSymbol
import lang.temper.value.staySymbol
import lang.temper.value.void
import kotlin.test.fail

fun interface ExtraDefinitions {
    fun findDefinition(nameText: String): TypeDefinition?

    private data object NoExtraDefinitions : ExtraDefinitions {
        override fun findDefinition(nameText: String): TypeDefinition? = null
    }

    private data class ListExtraDefinitions(
        val definitions: List<TypeDefinition>,
    ) : ExtraDefinitions {
        override fun findDefinition(nameText: String): TypeDefinition? =
            definitions.firstOrNull {
                (it.name as? ResolvedParsedName)?.baseName?.nameText == nameText
            }
    }

    private data class ComposedExtraDefinitions(
        val a: ExtraDefinitions,
        val b: ExtraDefinitions,
    ) : ExtraDefinitions {
        override fun findDefinition(nameText: String): TypeDefinition? =
            a.findDefinition(nameText) ?: b.findDefinition(nameText)
    }

    companion object {
        val zero: ExtraDefinitions = NoExtraDefinitions
        fun from(definitions: List<TypeDefinition>): ExtraDefinitions =
            if (definitions.isNotEmpty()) {
                ListExtraDefinitions(definitions)
            } else {
                NoExtraDefinitions
            }

        fun compose(a: ExtraDefinitions, b: ExtraDefinitions): ExtraDefinitions = when {
            a is NoExtraDefinitions -> b
            b is NoExtraDefinitions -> a
            a is ListExtraDefinitions && b is ListExtraDefinitions ->
                ListExtraDefinitions(a.definitions + b.definitions)

            else -> ComposedExtraDefinitions(a, b)
        }
    }
}

/**
 * Just enough parsing to let us create type definitions.
 *
 * This is meant to be used thus:
 *
 *      TypeTestHarness("class C<T> ...").run {
 *          val a = type("C<String>")              // type is this.type()
 *          // OTHER TEST CODE
 *      }
 */
@Suppress("MagicNumber") // Uses tree child indices
class TypeTestHarness(
    /**
     * A string with type definitions like
     *
     *     class C<T extends Foo> extends Base {}
     *
     *     interface I {}
     */
    sourceCodeForPseudoDeclarations: String,
    genre: Genre = Genre.Library,
    extraDefinitions: List<TypeDefinition> = emptyList(),
) {
    private val namingContext = MinimalNamingContext(testModuleName)
    private val nameMaker = ResolvedNameMaker(namingContext, genre)
    private val mutationCounter = AtomicCounter()

    private val tetchyLogSink: LogSink = TetchyLogSink()

    private val definitionsByNameText = mutableMapOf<String, TypeDefinition>()
    private val definitionsByName = mutableMapOf<ResolvedName, TypeDefinition>()

    init {
        val preexisting = buildList {
            addAll(WellKnownTypes.allWellKnown)
            addAll(extraDefinitions)
        }
        for (definition in preexisting) {
            registerTopLevel(definition)
        }
        // Register common top-level aliases
        registerTopLevel(WellKnownTypes.intTypeDefinition, BuiltinName("Int"))

        processTypeDefinition(toCst(sourceCodeForPseudoDeclarations))
    }

    private fun registerTopLevel(
        definition: TypeDefinition,
        name: ResolvedName = definition.name,
    ) {
        val word = name.toSymbol()!!
        definitionsByNameText[word.text] = definition
        definitionsByName[name] = definition
    }

    fun getDefinition(name: String): TypeDefinition? = definitionsByNameText[name]

    /**
     * Recognizes type expressions including
     *
     * ## Nominal types
     *
     *      Foo<A, B>
     *
     * ## *Or* types
     *
     *      A | B
     *
     * ## *And* types
     *
     *      A & B
     *
     * ## *Function* types
     *
     *      fn <T>(_: A): T
     *
     * ## As well as [well-known types][WellKnownTypes] including
     *
     *      Top
     *      AnyValue                 Bubble
     *      Function  Int  String
     *      Never
     *
     *      Invalid
     *
     * @param extraDefinitions other names that can be resolved.
     *   For example, if parsing a type in the context of a type definition's
     *   body, passing in the list of formal parameters would allow using
     *   their names.
     */
    fun type(
        codeFragment: String,
        extraDefinitions: List<TypeDefinition> = emptyList(),
    ): StaticType = type(
        codeFragment,
        extraDefinitions = ExtraDefinitions.from(extraDefinitions),
    )

    fun type(
        codeFragment: String,
        extraDefinitions: ExtraDefinitions,
    ): StaticType = type(
        cst = toCst(codeFragment),
        extraDefinitions = extraDefinitions,
        factory = StaticTypeFactory,
    )

    fun type2(
        codeFragment: String,
        extraDefinitions: List<TypeDefinition> = emptyList(),
    ) = type2(
        codeFragment,
        extraDefinitions = ExtraDefinitions.from(extraDefinitions),
    )

    fun type2(
        codeFragment: String,
        extraDefinitions: ExtraDefinitions,
    ) = type(
        toCst(codeFragment),
        extraDefinitions = extraDefinitions,
        factory = Type2Factory,
    )

    private interface Factory<TYP> {
        fun top(): TYP
        fun invalid(): TYP
        fun bubble(): TYP
        fun never(): TYP
        fun make(defn: TypeDefinition, bindings: List<TYP>): TYP
        fun or(members: List<TYP>): TYP
        fun and(members: List<TYP>): TYP
        fun throws(pass: TYP, fail: List<TYP>): TYP
        fun nullable(typ: TYP): TYP
        fun fn(typeFormals: List<TypeFormal>, inps: List<ValueFormal<TYP>>, ret: TYP): TYP
    }

    private object StaticTypeFactory : Factory<StaticType> {
        override fun top() = TopType
        override fun invalid() = InvalidType
        override fun bubble() = BubbleType
        override fun never() = OrType.emptyOrType
        override fun make(defn: TypeDefinition, bindings: List<StaticType>) =
            MkType.nominal(defn, bindings)
        override fun or(members: List<StaticType>) = MkType.or(members)
        override fun and(members: List<StaticType>) = MkType.and(members)
        override fun throws(pass: StaticType, fail: List<StaticType>) =
            MkType.or(listOf(pass, BubbleType))
        override fun nullable(typ: StaticType) = MkType.nullable(typ)
        override fun fn(
            typeFormals: List<TypeFormal>,
            inps: List<ValueFormal<StaticType>>,
            ret: StaticType,
        ) = MkType.fnDetails(
            typeFormals,
            inps.map { FunctionType.ValueFormal(it.symbol, it.typ, it.isOptional) },
            ret,
        )
    }

    private object Type2Factory : Factory<Type2> {
        override fun top() = WellKnownTypes.anyValueOrNullType2
        override fun invalid() = WellKnownTypes.invalidType2
        override fun bubble() = WellKnownTypes.bubbleType2
        override fun never() = MkType2(WellKnownTypes.neverTypeDefinition).get()
        override fun make(
            defn: TypeDefinition,
            bindings: List<Type2>,
        ) = when (defn) {
            is TypeFormal -> {
                check(bindings.isEmpty())
                MkType2(defn).get()
            }
            is TypeShape -> MkType2(defn).actuals(bindings).get()
        }
        override fun or(members: List<Type2>) = error("OrType($members)")
        override fun and(members: List<Type2>) = error("AndType($members)")
        override fun throws(pass: Type2, fail: List<Type2>) =
            MkType2.result(listOf(pass) + fail).get()
        override fun nullable(typ: Type2): Type2 = typ.withNullity(Nullity.OrNull)
        override fun fn(
            typeFormals: List<TypeFormal>,
            inps: List<ValueFormal<Type2>>,
            ret: Type2,
        ): Type2 {
            val req = mutableListOf<Type2>()
            val opt = mutableListOf<Type2>()
            for (inp in inps) {
                if (inp.isOptional) {
                    opt.add(inp.typ)
                } else {
                    check(opt.isEmpty())
                    req.add(inp.typ)
                }
            }
            return AdHocArrowTypes.definedTypeForSig(
                Signature2(
                    returnType2 = ret,
                    hasThisFormal = inps.firstOrNull()?.symbol?.text == "this",
                    requiredInputTypes = req.toList(),
                    optionalInputTypes = opt.toList(),
                    typeFormals = typeFormals,
                ),
            )
        }
    }

    private fun <TYP> type(
        cst: ConcreteSyntaxTree,
        extraDefinitions: ExtraDefinitions,
        bindings: List<TYP> = emptyList(),
        factory: Factory<TYP>,
    ): TYP {
        if (cst.operator == Operator.Leaf && cst.childCount == 1) {
            val nameText = cst.child(0).tokenText!!
            if (bindings.isEmpty()) {
                when (nameText) {
                    OutToks.topWord.text -> return factory.top()
                    OutToks.invalidWord.text -> return factory.invalid()
                    OutToks.bubbleWord.text -> return factory.bubble()
                    OutToks.neverWord.text -> return factory.never()
                    else -> Unit
                }
            }

            val definition = extraDefinitions.findDefinition(nameText)
                ?: definitionsByNameText[nameText]
            check(definition != null) { "$nameText !in ${definitionsByNameText.keys}" }
            return factory.make(definition, bindings)
        }
        if (
            cst.operator == Operator.Angle && cst.childCount == 4 &&
            cst.child(1).tokenText == "<" && cst.child(cst.childCount - 1).tokenText == ">"
        ) {
            val bindingsList = mutableListOf<TYP>()
            forEachAngleBracketed(cst) { bracketed ->
                bindingsList.add(
                    type(bracketed, extraDefinitions, factory = factory),
                )
            }
            return type(cst.child(0), extraDefinitions, bindingsList.toList(), factory)
        }

        if (cst.operator == Operator.Bar && bindings.isEmpty()) {
            return factory.or(
                (0 until cst.childCount).mapNotNull { i ->
                    val c = cst.child(i)
                    if (c.tokenText == "|") {
                        null
                    } else {
                        type(c, extraDefinitions, factory = factory)
                    }
                },
            )
        }
        if (cst.operator == Operator.Throws && bindings.isEmpty()) {
            val types = (0 until cst.childCount).mapNotNull { i ->
                val c = cst.child(i)
                if (c.tokenText == "|" || c.tokenText == "throws") {
                    null
                } else {
                    type(c, extraDefinitions, factory = factory)
                }
            }
            return factory.throws(types[0], types.subList(1, types.size))
        }
        if (cst.operator == Operator.Amp && bindings.isEmpty()) {
            return factory.and(
                (0 until cst.childCount).mapNotNull { i ->
                    val c = cst.child(i)
                    if (c.tokenText == "&") {
                        null
                    } else {
                        type(c, extraDefinitions, factory = factory)
                    }
                },
            )
        }

        if ( // T?
            cst.operator == Operator.PostQuest && cst.childCount == 2 &&
            cst.child(1).tokenText == "?"
        ) {
            val operand = cst.child(0)
            return factory.nullable(type(operand, extraDefinitions, factory = factory))
        }

        if ( // fn (...): ...
            cst.operator == Operator.HighColon && cst.childCount == 3 &&
            cst.child(1).tokenText == ":"
        ) {
            val left = cst.child(0)
            val returnTypeTree = cst.child(2)
            if (
                left.operator == Operator.Paren &&
                left.childCount in 3..4 && // `fn ()` or `fn ( ... )`
                left.child(1).tokenText == "(" &&
                left.child(left.childCount - 1).tokenText == ")"
            ) {
                var left0 = left.child(0)
                val typeFormals = mutableListOf<TypeFormal>()
                if (left0.operator == Operator.Angle) {
                    forEachAngleBracketed(left0) {
                        typeFormals.add(processTypeParameter(it, null, ExtraDefinitions.from(typeFormals)))
                    }
                    left0 = left0.child(0)
                }

                if (left0.childCount == 1 && left0.child(0).tokenText == "fn") {
                    val valueFormals = mutableListOf<ValueFormal<TYP>>()
                    val valueFormalsTree = if (left.childCount == 4) {
                        left.child(2)
                    } else {
                        null
                    }
                    val allExtraDefinitions = ExtraDefinitions.compose(
                        extraDefinitions,
                        ExtraDefinitions.from(typeFormals),
                    )
                    if (valueFormalsTree != null) {
                        forEachCommaSeparated(valueFormalsTree) {
                            valueFormals.add(valueFormal(it, allExtraDefinitions, factory))
                        }
                    }
                    val returnType = type(returnTypeTree, allExtraDefinitions, factory = factory)
                    return factory.fn(
                        typeFormals.toList(),
                        valueFormals.toList(),
                        returnType,
                    )
                }
            }
        }

        if (cst.operator == Operator.ParenGroup && cst.childCount == 3 && bindings.isEmpty()) {
            return type(cst.child(1), extraDefinitions, factory = factory)
        }

        TODO("${FormattingStructureSink.toJsonString(cst)} $extraDefinitions")
    }

    private data class ValueFormal<TYP>(
        val symbol: Symbol?,
        val typ: TYP,
        val isOptional: Boolean,
    )

    private fun <TYP> valueFormal(
        cst: ConcreteSyntaxTree,
        extraDefinitions: ExtraDefinitions,
        factory: Factory<TYP>,
    ): ValueFormal<TYP> {
        var tCst = cst
        var isOptional = false
        var symbol: Symbol? = null
        var nameCst: ConcreteSyntaxTree? = null
        if (tCst.operator == Operator.HighColon && tCst.operands.size == 3) {
            val (left, _, right) = tCst.operands
            val isProbableNestedFnType = when (left.operator) {
                Operator.PostQuest, Operator.Leaf -> false
                else -> true
            }
            if (!isProbableNestedFnType) {
                nameCst = left
                tCst = right
            }
        }
        if (nameCst?.operator == Operator.PostQuest && nameCst.operands.size == 2) {
            isOptional = true
            nameCst = nameCst.operands[0]
        }
        if (nameCst != null) {
            check(nameCst.operator == Operator.Leaf && nameCst.operands.size == 1) { "$nameCst" }
            val nameText = nameCst.operands[0].tokenText!!
            if (nameText != "_") {
                symbol = Symbol(nameText)
            }
        }
        return ValueFormal(symbol, type(tCst, extraDefinitions, factory = factory), isOptional = isOptional)
    }

    private fun processTypeDefinition(cst: ConcreteSyntaxTree): TypeShapeImpl? {
        if (cst.operator == Operator.At && cst.operands.size > 2) {
            // Like @(name ...rest annotated)
            val name = cst.operands[1]
            val rest = cst.operands.subList(2, cst.operands.lastIndex)
            val annotated = cst.operands.last()
            // May call back in here with annotated
            return processAnnotatedTypeDefinition(name, rest, annotated)
        }
        if (cst.operator == Operator.Semi || cst.operator == Operator.Root) {
            for (childIndex in 0 until cst.childCount) {
                val child = cst.child(childIndex)
                if (child.tokenText == ";") {
                    continue
                }
                processTypeDefinition(child)
            }
            return null
        }
        if ( // Skip into operator skipping empty class body `{}`
            cst.operator == Operator.Curly && cst.childCount == 3 &&
            cst.child(1).tokenText == "{"
        ) {
            processTypeDefinition(cst.child(0))
            return null
        }
        if (cst.operator == Operator.Leaf && cst.childCount == 2) {
            // Two words like "interface Name" or "class Name"
            val c0 = cst.child(0)
            val c1 = cst.child(1)
            if (c0 is CstLeaf && c1 is CstLeaf) {
                val abstractness = when (c0.tokenText) {
                    "class" -> Abstractness.Concrete
                    "interface" -> Abstractness.Abstract
                    else -> fail("Expected `class` or `interface` not ${c0.tokenText}")
                }
                val nameText = c1.tokenText
                check(nameText !in definitionsByNameText) { "Duplicate $nameText" }
                val typeShape = TypeShapeImpl(
                    cst.pos,
                    Symbol(nameText),
                    nameMaker,
                    abstractness,
                    mutationCounter,
                )
                typeShape.superTypes.add(type(ANY_VALUE_TYPE_NAME_TEXT) as NominalType)
                registerTopLevel(typeShape)
                return typeShape
            }
        }
        if (
            cst.operator == Operator.Angle && cst.childCount == 4 &&
            cst.child(1).tokenText == "<" &&
            cst.child(cst.childCount - 1).tokenText == ">"
        ) {
            val typeShape = processTypeDefinition(cst.child(0))!!
            // Add formals
            forEachAngleBracketed(cst) { bracketed ->
                processTypeParameter(bracketed, typeShape, ExtraDefinitions.zero)
            }
            return typeShape
        }
        if (
            (cst.operator == Operator.ExtendsComma || cst.operator == Operator.ExtendsNoComma) &&
            cst.childCount >= 2 && cst.child(1).tokenText == "extends"
        ) {
            val typeShape = processTypeDefinition(cst.child(0))!!
            forEachExtended(cst) { extended ->
                typeShape.superTypes.add(
                    type(
                        extended,
                        ExtraDefinitions.from(typeShape.formals),
                        factory = StaticTypeFactory,
                    ) as NominalType,
                )
            }
            return typeShape
        }
        TODO("${cst.childCount} $cst")
    }

    private fun processTypeParameter(
        cst: ConcreteSyntaxTree,
        enclosingType: TypeShapeImpl?,
        extraDefinitions: ExtraDefinitions,
        upperBounds: List<NominalType> = emptyList(),
    ): TypeFormal {
        if (cst.operator == Operator.Leaf) {
            var variance: Variance = Variance.Invariant
            val typeParameterNameText: String? = when (cst.childCount) {
                1 -> cst.child(0).tokenText!!
                2 -> { // `in name` or `out name`
                    val modifierToken = cst.child(0).tokenText!!
                    Variance.entries.firstOrNull { it.keyword == modifierToken }
                        ?.let {
                            variance = it
                            cst.child(1).tokenText!!
                        }
                }
                else -> null
            }
            if (typeParameterNameText != null) {
                val symbol = Symbol(typeParameterNameText)
                val name = nameMaker.unusedSourceName(ParsedName(typeParameterNameText))
                val definition = TypeFormal(
                    cst.pos,
                    name,
                    symbol,
                    variance,
                    mutationCounter,
                    upperBounds,
                )
                if (enclosingType != null) {
                    val typeParameterShape = TypeParameterShape(
                        enclosingType = enclosingType,
                        definition = definition,
                        symbol = symbol,
                        stay = null,
                    )
                    enclosingType.typeParameters.add(typeParameterShape)
                }
                definitionsByName[name] = definition
                // Do not add via name text since it's scoped to the enclosing type.
                return definition
            }
        }
        if (
            (cst.operator == Operator.ExtendsNoComma || cst.operator == Operator.ExtendsComma) &&
            cst.childCount >= 2 && cst.child(1).tokenText == "extends"
        ) {
            val upperBoundsList = mutableListOf<NominalType>()
            forEachExtended(cst) { extended ->
                val allExtraDefinitions = ExtraDefinitions.compose(
                    extraDefinitions,
                    ExtraDefinitions.from((enclosingType?.formals ?: emptyList())),
                )
                val upperBound = type(extended, allExtraDefinitions, factory = StaticTypeFactory)
                if (upperBound is AndType) {
                    for (oneUpperBound in upperBound.members) {
                        upperBoundsList.add(oneUpperBound as NominalType)
                    }
                } else {
                    upperBoundsList.add(upperBound as NominalType)
                }
            }
            return processTypeParameter(
                cst.child(0),
                enclosingType,
                extraDefinitions,
                upperBounds = upperBoundsList.toList(),
            )
        }
        TODO("$cst")
    }

    private fun forEachExtended(cst: ConcreteSyntaxTree, body: (ConcreteSyntaxTree) -> Unit) {
        for (i in 2 until cst.childCount) {
            val c = cst.child(i)
            if (c.tokenText == ",") {
                continue
            }
            if (c.operator == Operator.Amp) {
                for (ampIndex in 0 until c.childCount) {
                    val ampChild = c.child(ampIndex)
                    if (ampChild.tokenText == "&") {
                        continue
                    }
                    body(ampChild)
                }
            } else {
                body(c)
            }
        }
    }

    private fun forEachAngleBracketed(cst: ConcreteSyntaxTree, body: (ConcreteSyntaxTree) -> Unit) {
        // By child index:
        // 0 - before `<`
        // 1 - The `<` bracket
        // 2 - The sole element of the brackets or a comma operator
        // 3 - The `>` bracket
        forEachCommaSeparated(cst.child(2), body)
    }

    private fun processAnnotatedTypeDefinition(
        name: ConcreteSyntaxTree,
        rest: List<ConcreteSyntaxTree>,
        annotated: ConcreteSyntaxTree,
    ): TypeShapeImpl {
        if (rest.isEmpty() && name.operands.size == 1 && name.operands.first().tokenText == "fun") {
            return processAbbreviatedInterfaceMethodSyntax(annotated).also { typeShape ->
                val doc = Document(TestDocumentContext())
                val fakeRoot = doc.treeFarm.grow(annotated.pos) {
                    Block {
                        Decl(typeShape.name) {
                            V(staySymbol)
                            Stay()
                            V(functionalInterfaceSymbol)
                            V(void)
                        }
                    }
                }
                fun findStayLeaf(fakeRoot: Tree): StayLeaf? {
                    if (fakeRoot is StayLeaf) { return fakeRoot }
                    for (c in fakeRoot.children) {
                        val stayLeafOrNull = findStayLeaf(c)
                        if (stayLeafOrNull != null) { return stayLeafOrNull }
                    }
                    return null
                }
                typeShape.stayLeaf = findStayLeaf(fakeRoot)!!
            }
        }
        TODO("$name $rest $annotated")
    }

    private fun processAbbreviatedInterfaceMethodSyntax(cst: ConcreteSyntaxTree): TypeShapeImpl {
        var unprocessed = cst

        // Wait until we have found any <...> to parse the output and input types.
        var outputTypeNode: ConcreteSyntaxTree? = null
        if (unprocessed.operator == Operator.HighColon && unprocessed.operands.size == 3) {
            val (left, _, right) = unprocessed.operands
            outputTypeNode = right
            unprocessed = left
        }

        var argList: ConcreteSyntaxTree? = null
        if (unprocessed.operator == Operator.Paren) {
            if (unprocessed.operands.size == 3) { // zero args
                unprocessed = unprocessed.operands.first()
            } else if (unprocessed.operands.size == 4) {
                val (left, _, parenContents, _) = unprocessed.operands
                argList = parenContents
                unprocessed = left
            }
        }

        var typeFormalContent: ConcreteSyntaxTree? = null
        if (unprocessed.operator == Operator.Angle && unprocessed.operands.size == 4) {
            val (left, _, angleContents, _) = unprocessed.operands
            unprocessed = left
            typeFormalContent = angleContents
        }

        var word: Symbol? = null
        if (unprocessed.operator == Operator.Leaf && unprocessed.operands.size == 2) {
            val (a, b) = unprocessed.operands
            if (a.tokenText == "interface") {
                word = b.tokenText?.let { Symbol(it) }
            }
        }
        if (word == null) {
            TODO("$cst")
        }
        val typeShape = TypeShapeImpl(cst.pos, word, nameMaker, Abstractness.Abstract, mutationCounter)
        registerTopLevel(typeShape)

        val typeFormals = mutableListOf<TypeFormal>()
        if (typeFormalContent != null) {
            forEachCommaSeparated(typeFormalContent) {
                val tp = processTypeParameter(it, typeShape, ExtraDefinitions.from(typeFormals))
                typeFormals.add(tp)
            }
        }

        val extras = ExtraDefinitions.from(typeFormals)
        // Now we're ready to parse input and output types.
        val outputType = outputTypeNode?.let {
            type(it, extras, factory = StaticTypeFactory)
        }
        val requiredFormals = mutableListOf<Type2>()
        if (argList != null) {
            forEachCommaSeparated(argList) { arg ->
                if (arg.operator == Operator.HighColon && arg.operands.size == 3) {
                    val (nameLeaf, _, type) = arg.operands
                    if (nameLeaf.operator == Operator.Leaf && nameLeaf.operands.size == 1) {
                        val symbol = nameLeaf.operands.first().tokenText?.let { Symbol(it) }
                        ignore(symbol)
                        requiredFormals.add(type(type, extras, factory = Type2Factory))
                    } else {
                        TODO("$nameLeaf")
                    }
                } else {
                    TODO("$arg")
                }
            }
        }

        val applyMethod = MethodShape(
            typeShape,
            nameMaker.unusedSourceName(ParsedName("apply")),
            Symbol("apply"),
            null,
            Visibility.Public,
            MethodKind.Normal,
            OpenOrClosed.Open,
        )
        typeShape.methods.add(applyMethod)
        val sig = Signature2(
            returnType2 = hackMapOldStyleToNew(outputType ?: InvalidType),
            hasThisFormal = false,
            requiredInputTypes = requiredFormals.toList(),
            optionalInputTypes = listOf(),
        )
        applyMethod.descriptor = sig

        return typeShape
    }

    private fun forEachCommaSeparated(cst: ConcreteSyntaxTree, body: (ConcreteSyntaxTree) -> Unit) {
        if (cst.operator == Operator.Comma) {
            for (i in 0 until cst.childCount) {
                val c = cst.child(i)
                if (c.tokenText != ",") {
                    body(c)
                }
            }
        } else {
            body(cst)
        }
    }

    private fun toCst(codeFragment: String): ConcreteSyntaxTree {
        val loc = namingContext.loc
        val lexer = Lexer(loc, tetchyLogSink, codeFragment)
        return parse(lexer, tetchyLogSink)
    }
}

private class MinimalNamingContext(
    override val loc: ModuleLocation,
) : NamingContext()

private class TetchyLogSink : LogSink {
    override fun log(
        level: Log.Level,
        template: MessageTemplateI,
        pos: Position,
        values: List<Any>,
        fyi: Boolean,
    ) {
        check(level < Log.Error) { template.format(values) }
    }

    override val hasFatal: Boolean get() = false
}

fun withTypeTestHarness(typeDefinitionSource: String = "", body: TypeTestHarness.() -> Unit) {
    val typeTestHarness = TypeTestHarness(
        typeDefinitionSource,
    )
    typeTestHarness.body()
}
