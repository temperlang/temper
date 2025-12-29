package lang.temper.be.rust

import lang.temper.ast.deepCopy
import lang.temper.be.rust.Rust.AttrOuter
import lang.temper.be.tmpl.TmpL
import lang.temper.be.tmpl.isPureVirtual
import lang.temper.lexer.temperAwareBaseName
import lang.temper.log.CodeLocation
import lang.temper.log.FilePath
import lang.temper.log.FilePathSegment
import lang.temper.log.Position
import lang.temper.log.dirPath
import lang.temper.log.last
import lang.temper.log.resolveFile
import lang.temper.log.spanningPosition
import lang.temper.name.ImplicitsCodeLocation
import lang.temper.name.ModuleName
import lang.temper.name.OutName
import lang.temper.name.ResolvedName
import lang.temper.name.ResolvedParsedName
import lang.temper.name.identifiers.IdentStyle
import lang.temper.type.Abstractness
import lang.temper.type.TypeDefinition
import lang.temper.type.TypeShape
import lang.temper.type.WellKnownTypes
import lang.temper.type2.DefinedNonNullType
import lang.temper.type2.Descriptor
import lang.temper.type2.MkType2
import lang.temper.type2.NonNullType
import lang.temper.type2.Nullity
import lang.temper.type2.Signature2
import lang.temper.type2.Type2
import lang.temper.type2.hackMapOldStyleToNew
import lang.temper.type2.withNullity
import lang.temper.type2.withType

internal fun makeError(pos: Position) = Rust.Call(pos, callee = ERROR_NEW_NAME.toId(pos), args = listOf())

internal fun makePath(pos: Position, vararg segments: String) = Rust.PathSegments(pos, segments.map { it.toId(pos) })

fun makeSrcFilePath(relDir: FilePath): FilePath = makeSrcFilePath(relDir.segments)

fun makeSrcFilePath(relDir: List<FilePathSegment>): FilePath {
    val modPath = dirPath(relDir.map { it.fullName.dashToSnake() }).resolveFile("mod.rs")
    return dirPath("src").resolve(modPath)
}

internal fun MutableList<Rust.Item>.declareSubmods(pos: Position, modKids: Collection<FilePath>) {
    for (kid in modKids) {
        val modId = kid.last().temperAwareBaseName().dashToSnake().toId(pos)
        add(Rust.Module(pos, id = modId, pub = Rust.VisibilityPub(pos)).toItem())
    }
}

internal fun Iterable<String>.toPath(pos: Position): Rust.Path {
    return Rust.PathSegments(pos, map { it.toId(pos) })
}

internal inline fun <reified T> List<Rust.Statement>.firstOrElse(noinline default: (List<Rust.Statement>) -> T): T {
    return firstOrElse({ it as? T }, default)
}

internal fun <T> List<Rust.Statement>.firstOrElse(
    first: (Rust.Statement) -> T?,
    default: (List<Rust.Statement>) -> T,
): T {
    return when (size) {
        1 -> first(first())
        else -> null
    } ?: default(this)
}

internal fun List<Rust.Statement>.toBlock(pos: Position) = firstOrElse {
    Rust.Block(spanningPosition(pos), attrs = listOf(), statements = this)
}

/** Find the string text for all mentioned types that are simple ids. */
internal fun List<Rust.FunctionParamOption>.findAllSimpleTypeNames(): Set<String> {
    fun MutableSet<String>.addAllSimpleTypeNames(type: Rust.Type) {
        // In both of the following cases, they're always just Type, but we might have lifetimes or more later.
        fun handleBounds(bounds: List<Rust.TypeParamBound>) {
            for (bound in bounds) {
                (bound as? Rust.Type)?.let { addAllSimpleTypeNames(it) }
            }
        }
        fun handleGenericArgs(args: List<Rust.GenericArg>) {
            for (arg in args) {
                (arg as? Rust.Type)?.let { addAllSimpleTypeNames(it) }
            }
        }
        when (type) {
            is Rust.FunctionType -> {
                for (param in type.params) {
                    addAllSimpleTypeNames(param)
                }
                addAllSimpleTypeNames(type.returnType)
            }
            is Rust.GenericType -> {
                addAllSimpleTypeNames(type.path)
                handleGenericArgs(type.args)
            }
            is Rust.Id -> add(type.outName.outputNameText)
            is Rust.ImplTraitType -> handleBounds(type.bounds)
            is Rust.PathSegments -> when (val last = type.segments.last()) {
                is Rust.GenericArgs -> handleGenericArgs(last.args)
                is Rust.Id -> if (type.segments.size == 1) {
                    add(last.outName.outputNameText)
                }
            }
            is Rust.RefType -> addAllSimpleTypeNames(type.type)
            is Rust.TraitObjectType -> handleBounds(type.bounds)
            is Rust.TupleType -> for (part in type.types) {
                addAllSimpleTypeNames(part)
            }
        }
    }
    return buildSet {
        paramOptions@ for (paramOption in this@findAllSimpleTypeNames) {
            val type = (paramOption as? Rust.FunctionParam)?.type ?: continue@paramOptions
            addAllSimpleTypeNames(type)
        }
    }
}

private fun OutName.escape(): OutName {
    // Replace explicit "#" because we get those sometimes.
    // TODO Any other cleanup we need to do?
    return when (val cleaned = outputNameText.replace("#", "___")) {
        in keys -> OutName(cleaned.escapeAsKey(), sourceName)
        outputNameText -> this
        else -> OutName(cleaned, sourceName)
    }
}

internal fun OutName.toId(pos: Position) = Rust.Id(pos, this.escape())

internal fun OutName.toKeyId(pos: Position) = Rust.Id(pos, this)

internal fun Rust.Block.simplify(): Rust.Block = when {
    statements.size == 1 -> when (val first = statements[0]) {
        is Rust.Block -> first.simplify().deepCopy()
        else -> null
    }
    else -> null
} ?: this

internal fun Rust.Expr.box(wanted: TypeDefinition? = null, translator: RustTranslator): Rust.Expr {
    // TODO What other cases do we want to customize?
    val callee = when (wanted) {
        WellKnownTypes.generatorResultTypeDefinition -> return this // specially-handled type
        WellKnownTypes.listedTypeDefinition -> TO_LISTED_TO_LISTED_NAME.toId(pos)
        null -> "Box::new".toId(pos) // TODO Can this happen and if so, what to do?
        else ->
            (translator.translateTypeDefinition(wanted, pos.leftEdge) as Rust.Path)
                .extendWith("new")
    }
    return Rust.Call(pos, callee = callee, args = listOf(this))
}

internal fun Rust.Expr.box(wanted: Description, translator: RustTranslator): Rust.Expr =
    box(wanted = wanted.definition(), translator = translator)

internal fun Rust.Expr.call(args: List<Rust.Expr> = listOf()) = Rust.Call(pos, callee = this, args = args)

internal fun Rust.Expr.deref(): Rust.Expr {
    return Rust.Operation(pos, left = null, operator = Rust.Operator(pos, RustOperator.Deref), right = this)
}

internal fun Rust.Expr.effectiveId() = effectively { it as? Rust.Id }
internal fun Rust.Expr.effectivePath() = effectively { it as? Rust.Path }

internal fun <T> Rust.Expr.effectively(cast: (Rust.Expr) -> T?): T? {
    return when (this) {
        is Rust.Call -> when (val callee = callee) {
            is Rust.Operation -> when {
                args.isEmpty() &&
                    callee.operator.operator == RustOperator.Member &&
                    (callee.right as? Rust.Id)?.outName?.outputNameText == "clone"
                -> callee.left?.effectively(cast)

                else -> null
            }

            else -> null
        }

        else -> cast(this)
    }
}

internal fun Rust.Expr.infix(operator: RustOperator, right: Rust.Expr?): Rust.Expr {
    return Rust.Operation(pos, left = this, operator = Rust.Operator(pos, operator), right = right)
}

internal fun Rust.Expr.maybeClone(type: Descriptor, avoidClone: Boolean = false): Rust.Expr {
    (avoidClone || type.isCopy()) && return this
    // Only precisely on references to trait `self` do we need to box our clones.
    val isTrait = (type as? NonNullType)?.definition?.abstractness == Abstractness.Abstract
    val cloneBoxed = isTrait && (this as? Rust.Id)?.outName?.outputNameText == "self"
    return when {
        cloneBoxed -> methodCall(CLONE_BOXED_NAME)
        else -> wrapClone()
    }
}

internal fun Rust.Expr.maybeWrap(
    given: Description,
    wanted: Description?,
    translator: RustTranslator,
): Rust.Expr {
    wanted ?: return this
    val givenNone = this is Rust.Id && this.outName.outputNameText == "None"
    val wantedDefinition = wanted.definition()
    val wantStringIndexOption = wantedDefinition == WellKnownTypes.stringIndexOptionTypeDefinition
    val givenDefinition = given.definition()
    val mapParam = when {
        // Need to wrap in map for maintaining Option, but type null means `()` rather than Option.
        given.nullable && wanted.nullable && !givenNone -> translator.unusedTemporaryName(pos, "it")
        else -> null
    }
    return (mapParam ?: this).let { result ->
        when {
            wanted is FnDescription && !givenNone && !translator.isClosure(this) -> result.wrapArc()
            wantedDefinition == givenDefinition -> result
            wanted.isInterface() && !wantStringIndexOption -> when {
                wantedDefinition == WellKnownTypes.anyValueTypeDefinition -> result.methodCall("as_any_value")
                wantedDefinition == WellKnownTypes.generatorTypeDefinition ->
                    // TODO Remove this hack once we've defined Generator in be-rust temper-core.
                    result
                given.isClass() -> result.box(wanted = wanted, translator = translator)
                given.isInterface() -> {
                    // Going from interface to super interface needs cast.
                    val callee = translator.buildCastCallee(pos, found = given, wanted = wanted)
                    Rust.Call(pos, callee = callee, args = listOf(result)).methodCall("unwrap")
                }
                else -> result // neither ordinary class nor interface, some special type, like Null
            }
            else -> result
        }
    }.let { result ->
        when {
            !given.nullable && wanted.nullable -> result.wrapSome()
            wantStringIndexOption -> when (givenDefinition) {
                WellKnownTypes.noStringIndexTypeDefinition -> "None".toId(pos)
                WellKnownTypes.stringIndexTypeDefinition -> result.wrapSome()
                else -> result
            }

            else -> result
        }
    }.let { result ->
        when {
            mapParam == null -> result // no need to wrap in the first place
            mapParam === result -> this // no changes made, so no need to wrap
            else -> { // wrap in map
                val param = Rust.FunctionParam(pos, mapParam.deepCopy(), null)
                val closure = Rust.Closure(pos, params = listOf(param), value = result)
                methodCall("map", listOf(closure))
            }
        }
    }.let { result ->
        // Bubble/Result wrapping only applies to return values but easier just to check for all cases.
        when {
            !given.bubbly && wanted.bubbly -> result.wrapOk()
            else -> result
        }
    }
}

internal fun Rust.Expr.maybeWrap(given: Type2, wanted: Description?, translator: RustTranslator) =
    maybeWrap(given = given.described(), wanted = wanted, translator = translator)

internal fun Rust.Expr.maybeWrap(given: Type2, wanted: Type2?, translator: RustTranslator) =
    maybeWrap(given = given.described(), wanted = wanted?.described(), translator = translator)

// Might be nice to default member to not method and make Method a separate thing, but notMethod is easier for now.
// TODO Full review of usage to change the default.
internal fun Rust.Expr.member(key: String, notMethod: Boolean = false) = member(key.toId(pos), notMethod = notMethod)

internal fun Rust.Expr.member(key: Rust.Expr, notMethod: Boolean = false) = when {
    notMethod -> RustOperator.MemberNotMethod
    else -> RustOperator.Member
}.let { infix(it, right = key) }

internal fun Rust.Expr.methodCall(key: String, args: List<Rust.Expr> = listOf()) =
    Rust.Call(pos, callee = member(key), args = args)

internal fun Rust.Expr.methodCall(key: Rust.Expr, args: List<Rust.Expr> = listOf()) =
    Rust.Call(pos, callee = member(key), args = args)

internal fun Rust.Expr.propagate() = Rust.Operation(
    pos,
    left = this,
    operator = Rust.Operator(pos, RustOperator.Propagation),
    right = null,
)

internal fun Rust.Expr.readLocked() = Rust.Call(pos, "temper_core::read_locked".toId(pos), args = listOf(ref()))

internal fun Rust.Expr.ref(): Rust.Expr {
    return Rust.Operation(pos, left = null, operator = Rust.Operator(pos, RustOperator.Ref), right = this)
}

internal fun Rust.Expr.stripArc(): Rust.Expr {
    // Nicer not to wrap in the first place, but sometimes that's awkward.
    return when (this) {
        is Rust.Call -> when ((callee as? Rust.Id)?.outName?.outputNameText) {
            ARC_NEW_NAME -> this.args.first().deepCopy()
            else -> this
        }

        else -> this
    }
}

internal fun Rust.Expr.wrapArc(): Rust.Expr {
    return Rust.Call(pos, callee = ARC_NEW_NAME.toId(pos), args = listOf(this))
}

internal fun Rust.Expr.wrapArcString(): Rust.Expr {
    // TODO Use a helper function from temper_core once we have that?
    return methodCall("to_string").wrapArc()
}

// TODO Instead call `...::Clone::clone(x)` to avoid namespace collisions.
internal fun Rust.Expr.wrapClone() = methodCall("clone")

internal fun Rust.Expr.wrapLock() = Rust.Call(pos, callee = "$RW_LOCK_NAME::new".toId(pos), args = listOf(this))

internal fun Rust.Expr.wrapOk() = Rust.Call(pos, callee = "Ok".toId(pos), args = listOf(this))

/** Convert Option to Result. */
internal fun Rust.Expr.wrapOkOrElse(pos: Position = this.pos) =
    methodCall("ok_or_else", listOf(Rust.Closure(pos, params = listOf(), value = makeError(pos))))

internal fun Rust.Expr.wrapSome() = Rust.Call(pos, callee = "Some".toId(pos), args = listOf(this))

/** Only handles cases where the pattern is a [Rust.Id]. */
internal fun Rust.FunctionParamOption.toId(): Rust.Id {
    return when (this) {
        is Rust.FunctionParam -> this.pattern.deepCopy() as Rust.Id
        is Rust.Id, is Rust.RefType -> error(this)
    }
}

internal fun Rust.GenericParam.toArg(): Rust.Id {
    return when (this) {
        is Rust.Id -> this
        is Rust.TypeParam -> id
        is Rust.PathSegments -> TODO() // needed?
    }.deepCopy()
}

internal fun Rust.Id.makeTypeRef(generics: List<Rust.GenericParam>): Rust.Type = when {
    generics.isEmpty() -> this
    else -> Rust.GenericType(pos, path = deepCopy(), args = generics.map { it.toArg() })
}

internal fun Rust.ImplTraitType.implConvertName(): String? {
    bounds.size == 1 || return null
    val id = when (val bound = bounds.first()) {
        is Rust.Id -> bound
        is Rust.GenericType -> bound.path as? Rust.Id
        else -> null
    } ?: return null
    return when (id.outName.outputNameText) {
        TO_ARC_STRING_NAME -> "to_arc_string"
        TO_LIST_NAME -> "to_list"
        TO_LIST_BUILDER_NAME -> "to_list_builder"
        TO_LISTED_NAME -> "to_listed"
        else -> null
    }
}

internal fun Rust.ItemBase.toItem(attrs: Iterable<AttrOuter> = listOf(), pub: Rust.VisibilityPub? = null): Rust.Item {
    return Rust.Item(pos, attrs = attrs, pub = pub, item = this)
}

internal fun Rust.Path.extendWith(nexts: Iterable<Rust.PathSegment>): Rust.Path {
    return buildList {
        when (this@extendWith) {
            is Rust.Id -> add(this@extendWith)
            is Rust.PathSegments -> addAll(segments.deepCopy())
        }
        addAll(nexts)
    }.let { Rust.PathSegments(pos, segments = it) }
}

internal fun Rust.Path.extendWith(nexts: List<String>): Rust.Path {
    return extendWith(nexts.map { it.toId(pos) })
}

internal fun Rust.Path.extendWith(next: String) = extendWith(listOf(next.toId(pos)))

internal fun Rust.Path.suffixed(suffix: String): Rust.Path {
    fun suffixed(id: Rust.Id) = id.deepCopy().also { copy ->
        copy.outName = OutName("${copy.outName.outputNameText}$suffix", copy.outName.sourceName)
    }
    return when (this) {
        is Rust.Id -> suffixed(this)
        is Rust.PathSegments -> deepCopy().also { copy ->
            copy.segments = buildList {
                addAll(copy.segments.subList(0, copy.segments.size - 1))
                // Just let this fail for now if it's not an id last.
                val last = copy.segments.last() as Rust.Id
                add(suffixed(last))
            }
        }
    }
}

internal fun Rust.Pattern.id() = when (this) {
    is Rust.Id -> this
    is Rust.IdPattern -> this.id
    else -> error("no id: $this")
}

internal fun Rust.Type.onceLock(): Rust.Type {
    return Rust.GenericType(pos, path = ONCE_LOCK_NAME.toId(pos), args = listOf(this))
}

internal fun Rust.Type.option(): Rust.Type {
    return Rust.GenericType(pos, path = OPTION_NAME.toId(pos), args = listOf(this))
}

internal fun Rust.Type.wrapArcType(): Rust.Type {
    return Rust.GenericType(pos, path = ARC_NAME.toId(pos), args = listOf(this))
}

internal fun Rust.Type.wrapResult(): Rust.Type {
    return Rust.GenericType(pos, path = RESULT_NAME.toId(pos), args = listOf(this))
}

internal fun Rust.Type.wrapRwLockType(): Rust.Type {
    return Rust.GenericType(pos, path = RW_LOCK_NAME.toId(pos), args = listOf(this))
}

internal fun Descriptor.isCopy(): Boolean {
    return when (val type = described().type) {
        // Plain functions and methods are Copy, but our Arc function values aren't. We distinguish that elsewhere.
        // is FunctionType -> true
        is DefinedNonNullType -> when (type.definition.sourceLocation) {
            ImplicitsCodeLocation -> when (type.definition) {
                // TODO Which others are copy?
                WellKnownTypes.booleanTypeDefinition,
                WellKnownTypes.float64TypeDefinition,
                WellKnownTypes.intTypeDefinition,
                WellKnownTypes.int64TypeDefinition,
                WellKnownTypes.noStringIndexTypeDefinition,
                WellKnownTypes.stringIndexOptionTypeDefinition,
                WellKnownTypes.stringIndexTypeDefinition,
                WellKnownTypes.voidTypeDefinition,
                -> true

                else -> false
            }

            else -> false
        }
        // TODO Other cases, including nullable.
        else -> false
    }
}

fun String.camelToShout() = IdentStyle.Camel.convertTo(IdentStyle.LoudSnake, this)
fun String.camelToSnake() = IdentStyle.Camel.convertTo(IdentStyle.Snake, this)
fun String.dashToSnake() = IdentStyle.Dash.convertTo(IdentStyle.Snake, this)

private fun String.escapeAsKey() = "r#${this}"

internal fun String.escapeIfNeeded(): String {
    return when (this) {
        in keys -> escapeAsKey()
        else -> this
    }
}

internal fun String.toId(pos: Position, sourceName: ResolvedName? = null) =
    OutName(this, sourceName).toId(pos)

internal fun String.toKeyId(pos: Position, sourceName: ResolvedName? = null) =
    OutName(this, sourceName).toKeyId(pos)

internal fun String.toPath(pos: Position, current: ModuleName, names: RustNames, origin: CodeLocation?): Rust.Path {
    val id = toId(pos)
    return when (origin) {
        is ModuleName -> buildList {
            val originRoot = origin.libraryRoot()
            when (current.libraryRoot()) {
                originRoot -> add("crate".toKeyId(pos))
                else -> names.packageNamingsByRoot[originRoot]?.let { add(it.crateName.toId(pos)) }
            }
            for (segment in origin.relativePath().segments) {
                add(segment.temperAwareBaseName().dashToSnake().toId(pos))
            }
            add(id)
        }.let { Rust.PathSegments(pos, segments = it) }

        else -> id
    }
}

internal fun TmpL.Actual.stripToString(): TmpL.Actual {
    return when (this) {
        is TmpL.CallExpression -> when (val fn = this.fn) {
            is TmpL.SupportCodeWrapper -> when (fn.supportCode) {
                // TODO Also generate for user-defined String-returning toString?
                intToString, int64ToString -> when (parameters.size) {
                    1 -> parameters[0]
                    else -> this
                }

                SimpleToString -> parameters[0]
                else -> this
            }

            else -> this
        }

        else -> this
    }
}

internal fun TmpL.Actual.supportCode() =
    ((this as? TmpL.CallExpression)?.fn as? TmpL.InlineSupportCodeWrapper)?.supportCode

internal fun TmpL.BlockStatement?.isPureVirtual() = isPureVirtual(PureVirtualBuiltin)

internal fun TmpL.ModuleLevelDeclaration.isConsole(): Boolean {
    (type.ot as? TmpL.NominalType)?.typeName?.sourceDefinition?.let { typeDefinition ->
        if (typeDefinition.sourceLocation === ImplicitsCodeLocation) {
            when ((typeDefinition.name as? ResolvedParsedName)?.baseName?.nameText) {
                "Console", "GlobalConsole" -> return true
                else -> {}
            }
        }
    }
    return false
}

internal fun Descriptor.described(): Description = when (this) {
    is Type2 -> TypeDescription.make(this)
    is Signature2 -> FnDescription(type = this, bubbly = false, nullable = false)
}

sealed interface Description {
    val type: Descriptor?
    val bubbly: Boolean
    val nullable: Boolean

    fun definition(): TypeDefinition?
}

data class FnDescription(
    override val type: Signature2,
    override val bubbly: Boolean,
    override val nullable: Boolean,
) : Description {
    override fun definition(): TypeDefinition? = null
}

data class TypeDescription(
    override val type: NonNullType?,
    override val bubbly: Boolean,
    override val nullable: Boolean,
) : Description {
    override fun definition() = type?.definition

    fun maybeWrapStatic(wanted: Description): Type2? = when {
        type != null && (wanted.nullable || nullable || wanted.bubbly || bubbly) -> {
            var t: Type2 = type
            if (nullable || wanted.nullable) {
                t = t.withNullity(Nullity.OrNull)
            }
            if (bubbly || wanted.bubbly) {
                t = MkType2(WellKnownTypes.resultTypeDefinition)
                    .actuals(listOf(t, WellKnownTypes.bubbleType2))
                    .get()
            }
            t
        }

        else -> type
    }

    companion object {
        fun make(type: Type2): Description {
            var bubbly = false
            val passType = withType(
                t = type,
                result = { passType, _, _ ->
                    bubbly = true
                    passType
                },
                fallback = { it },
            )
            var nullable = false
            var core: NonNullType? = when (passType.nullity) {
                Nullity.NonNull -> passType
                Nullity.OrNull -> {
                    nullable = true
                    passType.withNullity(Nullity.NonNull)
                }
            } as NonNullType
            when (core?.definition) {
                WellKnownTypes.nullTypeDefinition -> {
                    nullable = true
                    core = null
                }

                WellKnownTypes.bubbleTypeDefinition -> {
                    bubbly = true
                    core = null
                }

                else -> {}
            }
            return if (core != null) {
                withType(
                    core,
                    fn = { _, sig, _ ->
                        FnDescription(type = sig, bubbly = bubbly, nullable = nullable)
                    },
                    fallback = { TypeDescription(type = core, bubbly = bubbly, nullable = nullable) },
                )
            } else {
                TypeDescription(null, bubbly = bubbly, nullable = nullable)
            }
        }
    }
}

internal fun Description?.isClass() = this is TypeDescription && this.definition()?.isClass() == true
internal fun Description?.isInterface() = this is TypeDescription && this.definition()?.isInterface() == true
internal fun Description.isUnit() = !nullable && !bubbly && this.type == WellKnownTypes.voidType2
internal fun TmpL.Type.isUnit() = this is TmpL.NominalType &&
    this.typeName.sourceDefinition == WellKnownTypes.voidTypeDefinition
internal fun Rust.Type.isUnit() = this is Rust.Id && this.outName.outputNameText == "()"

private val TypeDefinition.abstractness get() = (this as? TypeShape)?.abstractness

// TODO: can we replace this with uses of SuperTypeTree2
internal fun TypeDefinition.allInterfaces(): Sequence<Pair<TypeDefinition, Type2>> = sequence {
    // SuperTypeTree requires a NominalType to start with, and I didn't find that in TmpL.TypeDeclaration.
    types@ for (type in superTypes) {
        type.definition == WellKnownTypes.anyValueTypeDefinition && continue@types
        val superType = hackMapOldStyleToNew(type)
        if (superType is DefinedNonNullType) { // Not a type parameter upper bound
            yield(this@allInterfaces to superType)
        }
        yieldAll(type.definition.allInterfaces())
    }
}

private fun TypeDefinition?.isClass() = this?.abstractness == Abstractness.Concrete
internal fun TypeDefinition?.isInterface() = this?.abstractness == Abstractness.Abstract

internal enum class NameStyle {
    Camel,
    Shout,
    Snake,

    /** Typically implies numeric suffixes as well as no effort in styling. */
    Ugly,
}

/**
 * This includes both strict keywords and reserved words as of Rust 2018 edition.
 * https://doc.rust-lang.org/reference/keywords.html
 */
private val keys = setOf(
    "abstract",
    "as",
    "async",
    "await",
    "become",
    "box",
    "break",
    "const",
    "continue",
    "crate",
    "do",
    "dyn",
    "else",
    "enum",
    "extern",
    "false",
    "final",
    "fn",
    "for",
    "if",
    "impl",
    "in",
    "let",
    "loop",
    "macro",
    "match",
    "mod",
    "move",
    "mut",
    "override",
    "priv",
    "pub",
    "ref",
    "return",
    "self",
    "Self",
    "static",
    "struct",
    "super",
    "trait",
    "true",
    "try",
    "type",
    "typeof",
    "unsafe",
    "unsized",
    "use",
    "virtual",
    "where",
    "while",
    "yield",
)

internal val NonNullType?.isFunctionType get() = this != null &&
    withType(this, fn = { _, _, _ -> true }, fallback = { false })
