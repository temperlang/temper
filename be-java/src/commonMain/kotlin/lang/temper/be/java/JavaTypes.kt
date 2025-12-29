package lang.temper.be.java

import lang.temper.be.TargetLanguageTypeName
import lang.temper.be.java.BoundedWildcard.Dir
import lang.temper.be.java.JavaSimpleType.JstBool
import lang.temper.be.java.JavaSimpleType.JstDouble
import lang.temper.be.java.JavaSimpleType.JstInt
import lang.temper.be.java.JavaSimpleType.JstObject
import lang.temper.be.java.JavaSimpleType.JstVoid
import lang.temper.be.tmpl.TmpL
import lang.temper.format.OutToks
import lang.temper.format.TokenSink
import lang.temper.log.Position
import lang.temper.log.unknownPos
import lang.temper.name.OutName
import lang.temper.name.Symbol
import lang.temper.type.Abstractness
import lang.temper.type.SimplifyStaticType
import lang.temper.type.TAnnotation
import lang.temper.type.TypeDefinition
import lang.temper.type.TypeFormal
import lang.temper.type.TypeShape
import lang.temper.type.isBooleanLike
import lang.temper.type.simplify
import lang.temper.type2.DefinedType
import lang.temper.type2.MkType2
import lang.temper.type2.NonNullType
import lang.temper.type2.Nullity
import lang.temper.type2.Signature2
import lang.temper.type2.Type2
import lang.temper.type2.ValueFormal2
import lang.temper.type2.ValueFormalKind
import lang.temper.type2.hackMapNewStyleToOld
import lang.temper.type2.hackMapOldStyleToNew
import lang.temper.type2.withNullity
import lang.temper.type2.withType
import lang.temper.value.TString
import lang.temper.value.connectedSymbol
import lang.temper.be.java.Java as J
import lang.temper.type.WellKnownTypes as WKT

/**
 * Single-access method interfaces are needed in Java to represent function types.
 */
@ConsistentCopyVisibility
data class Sam private constructor(
    val klassName: QualifiedName,
    internal val sig: SimpleSignature,
    val method: String,
    val numTypeArgs: Int,
    val synthetic: Boolean,
) {
    companion object {
        fun synthetic(
            name: String,
            funcType: Signature2,
            pkg: QualifiedName,
        ): Sam {
            val sig = signature(funcType)
            return Sam(
                klassName = pkg.qualifyKnownSafe(name),
                sig = sig,
                method = sig.returnType.samMethodName,
                numTypeArgs = 0,
                synthetic = true,
            )
        }

        fun standard(
            funcType: Signature2,
        ): Sam? {
            return sams[signature(funcType)]
        }
    }

    /** Constructor for standard Java names. */
    constructor(
        name: String,
        sig: String,
        method: String = JavaSimpleType.methodName(sig),
        typeArgs: Int = sig.count { it == 'o' },
        pkg: QualifiedName = javaUtilFunction,
    ) : this(
        klassName = pkg.qualifyKnownSafe(name),
        sig = JavaSimpleType.unpack(sig),
        method = method,
        numTypeArgs = typeArgs,
        synthetic = false,
    )
}

/** Applies some simple rules to deduce a name from a function type. */
fun suggestSamName(type: Signature2): String = buildString {
    for (param in type.valueFormalsExceptThis) {
        append(suggestSimpleTypeName(param.type))
    }
    type.restInputsType ?. let { suggestSimpleTypeName(it) }?. let {
        append(it)
    }
    if (this.isEmpty()) {
        append(SIMPLE_NAME_NO_PARAMS)
    }
    val rt = type.returnType2
    append(
        (rt as? DefinedType)?.let {
            when (rt.definition) {
                WKT.voidTypeDefinition -> SIMPLE_NAME_VOID_RETURN
                WKT.booleanTypeDefinition -> SIMPLE_NAME_BOOLEAN_RETURN
                else -> null
            }
        } ?: suggestSimpleTypeName(rt),
    )
}

private fun suggestSimpleTypeName(type: Type2): String = withType(
    type,
    fn = { _, _, _ -> SIMPLE_NAME_FUNCTION },
    fallback = { _ ->
        if (type.definition == WKT.anyValueType) {
            SIMPLE_NAME_ANY
        } else {
            type.definition.name.simpleText()
        }
    },
)

const val SIMPLE_NAME_FUNCTION = "Function"
const val SIMPLE_NAME_ANY = "Any"
const val SIMPLE_RETURN_MARKER = "To"
const val SIMPLE_NAME_VOID_RETURN = "Procedure"
const val SIMPLE_NAME_BOOLEAN_RETURN = "Predicate"
const val SIMPLE_NAME_NO_PARAMS = "Nullary"

/**
 * "Simple" types bifurcate the Java type system into "some kind of primitive" and "everything else" and adopt
 * conventions observed in the Java standard library, especially specializations in `java.util.function`,
 * but also `Iterator`, `Spliterator` and `Stream`.
 *
 * Caveats: `float` is usually _not_ specialized, but we could do it.
 */
internal enum class JavaSimpleType(
    /**
     * The standard name for a method on a `java.util.function` Single Abstract Method interface that returns
     * this simple type.
     * Exception: the `Supplier` variants use `get` as the preferred verb.
     */
    val samMethodName: String,

    /** A short name used to distinguish type variants in Core. */
    val shortCamelName: String,
) {
    JstVoid(samMethodName = "accept", shortCamelName = "Void"),
    JstObject(samMethodName = "apply", shortCamelName = "Obj"),
    JstBool(samMethodName = "test", shortCamelName = "Bool"),
    JstDouble(samMethodName = "applyAsDouble", shortCamelName = "Double"),
    JstInt(samMethodName = "applyAsInt", shortCamelName = "Int"),
    JstLong(samMethodName = "applyAsLong", shortCamelName = "Long"),
    ;

    /**
     * Returns the strongest (computationally) simple type that encompasses this and another type.
     */
    fun strongest(other: JavaSimpleType) = when {
        this == JstBool && other == JstBool -> JstBool
        this == JstInt && other == JstInt -> JstInt
        (this == JstLong || this == JstInt) && (other == JstLong || other == JstInt) -> JstLong
        this == JstDouble && other == JstDouble -> JstDouble
        else -> JstObject
    }

    companion object {
        private val byAbbrev = mapOf(
            'v' to JstVoid,
            'o' to JstObject,
            'b' to JstBool,
            'd' to JstDouble,
            'i' to JstInt,
            'l' to JstLong,
        )
        private fun getByAbbrev(c: Char) = byAbbrev.getValue(c.lowercaseChar())

        /** String signatures are used to declare the standard interfaces in `java.util.function`. */
        fun unpack(sig: String) =
            SimpleSignature(
                returnType = getByAbbrev(sig.last()),
                formals = sig.dropLast(1).map { getByAbbrev(it) },
                varArg = null,
            )
        fun methodName(sig: String) = getByAbbrev(sig.last()).samMethodName
    }
}

/** A function signature with minimal information to describe lambdas. */
internal data class SimpleSignature(
    val returnType: JavaSimpleType,
    val formals: List<JavaSimpleType>,
    val varArg: JavaSimpleType?,
    val requiredCount: Int = formals.size,
)

sealed interface JavaType : TargetLanguageTypeName {
    fun toTypeAst(pos: Position): J.Type
    fun toResultTypeAst(pos: Position): J.ResultType = toTypeAst(pos)
    fun toRawType(): JavaType
    fun toClassLiteral(pos: Position) = J.ClassLiteral(pos, type = toRawType().toTypeAst(pos))

    /** For use as a type argument, or if it's otherwise known it should be a reference type. */
    fun asReferenceType(): ReferenceType
    fun makeNullable(): JavaType
    fun withPos(pos: Position): JavaType

    companion object {
        private val objectType = ReferenceType(javaLangObject)
        private val topType = if (STRICT_TYPES) Invalid else objectType
        private val emptyType = ReferenceType( // Optional<? super Object>
            javaUtilOptional, isNullable = false,
            args = listOf(
                BoundedWildcard(Dir.Super, objectType),
            ),
        )

        fun fromSig(sig: Signature2, names: JavaNames): JavaType =
            samJavaType(names.samType(sig), sig, names = names)

        fun fromFrontend(type: Type2, names: JavaNames): JavaType {
            val (principal, annotations) = SimplifyStaticType(type)
            var result = withType(
                principal,
                fn = { _, sig, _ ->
                    samJavaType(names.samType(sig), sig, names = names)
                },
                fallback = { _ ->
                    when (val definition = principal.definition) {
                        WKT.booleanTypeDefinition -> Primitive.JavaBoolean
                        WKT.emptyTypeDefinition -> emptyType
                        WKT.float64TypeDefinition -> Primitive.JavaDouble
                        WKT.noStringIndexTypeDefinition,
                        WKT.stringIndexTypeDefinition,
                        WKT.stringIndexOptionTypeDefinition,
                        WKT.intTypeDefinition,
                        -> Primitive.JavaInt
                        WKT.int64TypeDefinition -> Primitive.JavaLong

                        WKT.anyValueTypeDefinition -> topType
                        WKT.problemTypeDefinition -> Invalid
                        WKT.voidTypeDefinition -> Void
                        else -> {
                            // Ask JavaSupportNetwork if it's @connected to a known Java type.
                            val connectedKey = TString.unpackOrNull(
                                definition.metadata[connectedSymbol]?.firstOrNull(),
                            )
                            val args = principal.bindings.map { JavaTypeArg.fromTypeActual(it, names) }
                            val connectedType: JavaType? = connectedKey?.let {
                                names.javaLang.supportNetwork.translatedConnectedTypeToJavaType(connectedKey, args)
                            }

                            connectedType ?: ReferenceType(
                                names.classTypeName(type),
                                args = args,
                            )
                        }
                    }
                },
            )
            if (TAnnotation.Nullable in annotations) { result = result.makeNullable() }
            return result
        }

        fun fromTmpL(type: TmpL.AType, names: JavaNames) = fromTmpL(type.ot, names)

        internal fun toFrontend(t: TmpL.Type): Type2 = when (t) {
            is TmpL.FunctionType -> hackMapOldStyleToNew(
                lang.temper.type.MkType.fnDetails(
                    typeFormals = t.typeParameters.ot.typeParameters.map {
                        it.definition
                    },
                    valueFormals = t.valueFormals.formals.map {
                        lang.temper.type.FunctionType.ValueFormal(
                            it.name?.symbol,
                            hackMapNewStyleToOld(toFrontend(it.type.ot)),
                            isOptional = it.isOptional,
                        )
                    },
                    restValuesFormal = t.valueFormals.rest?.let {
                        hackMapNewStyleToOld(toFrontend(it.ot))
                    },
                    returnType = hackMapNewStyleToOld(toFrontend(t.returnType.ot)),
                ),
            )
            is TmpL.TypeIntersection -> WKT.invalidType2
            is TmpL.TypeUnion -> {
                var hasNull = false
                var hasBubble = false
                var principal: Type2? = null
                for (type in t.types) {
                    if (type is TmpL.BubbleType) {
                        hasBubble = true
                    } else if (type is TmpL.NominalType && type.typeName.sourceDefinition == WKT.nullTypeDefinition) {
                        hasNull = true
                    } else if (principal == null) {
                        principal = toFrontend(type)
                    }
                }
                if (principal == null) {
                    principal = WKT.invalidType2
                }
                if (hasNull) {
                    principal = principal.withNullity(Nullity.OrNull)
                }
                if (hasBubble) {
                    principal = MkType2(WKT.resultTypeDefinition)
                        .actuals(listOf(principal, WKT.bubbleType2))
                        .get()
                }
                principal
            }
            is TmpL.GarbageType -> WKT.invalidType2
            is TmpL.NominalType -> when (val d = t.typeName.sourceDefinition) {
                is TypeFormal -> MkType2(d).get()
                is TypeShape -> MkType2(d).actuals(
                    t.params.map {
                        toFrontend(it.ot)
                    },
                ).get()
            }
            is TmpL.BubbleType -> WKT.bubbleType2
            is TmpL.NeverType -> MkType2(WKT.neverTypeDefinition).get()
            is TmpL.TopType -> WKT.anyValueType2.withNullity(Nullity.OrNull)
        }

        fun fromTmpL(type: TmpL.Type, names: JavaNames): JavaType {
            return when (type) {
                is TmpL.FunctionType -> { // TODO: can go away once we start using functional interfaces
                    val requiredValueFormals = mutableListOf<Type2>()
                    val optionalValueFormals = mutableListOf<Type2>()

                    for (vf in type.valueFormals.formals) {
                        if (vf.isOptional) {
                            optionalValueFormals.add(toFrontend(vf.type.ot))
                        } else {
                            requiredValueFormals.add(toFrontend(vf.type.ot))
                        }
                    }
                    val restValuesFormal: Type2? = type.valueFormals.rest?.let {
                        toFrontend(it.ot)
                    }

                    val sig = Signature2(
                        returnType2 = toFrontend(type.returnType.ot),
                        hasThisFormal = type.valueFormals.formals.firstOrNull()?.let {
                            !it.isOptional && it.name?.symbol?.text == "this"
                        } ?: false,
                        requiredInputTypes = requiredValueFormals,
                        optionalInputTypes = optionalValueFormals,
                        restInputsType = restValuesFormal,
                        typeFormals = type.typeParameters.ot.typeParameters.map {
                            it.definition
                        },
                    )
                    samJavaType(names.samType(sig), sig, names)
                }
                is TmpL.TypeIntersection -> fromFrontend(WKT.invalidType2, names)
                is TmpL.TypeUnion -> {
                    var hasNull = false
                    var principal: JavaType? = null
                    for (t in type.types) {
                        if (t is TmpL.BubbleType) { continue }
                        if (t is TmpL.NominalType && t.typeName.sourceDefinition == WKT.nullTypeDefinition) {
                            hasNull = true
                        } else if (principal == null) {
                            principal = fromTmpL(t, names)
                        }
                    }
                    if (hasNull && principal != null) {
                        principal = principal.makeNullable()
                    }
                    return principal ?: fromFrontend(WKT.invalidType2, names)
                }
                is TmpL.GarbageType -> fromFrontend(WKT.invalidType2, names)
                is TmpL.NominalType -> fromFrontend(
                    when (val typeDef = type.typeName.sourceDefinition) {
                        is TypeFormal -> MkType2(typeDef).position(type.pos).get()
                        is TypeShape -> MkType2(typeDef)
                            .actuals(type.params.map { toFrontend(it.ot) })
                            .position(type.pos)
                            .get()
                    },
                    names,
                )
                is TmpL.BubbleType -> fromFrontend(WKT.voidType2, names)
                is TmpL.NeverType -> fromFrontend(WKT.voidType2, names)
                is TmpL.TopType -> fromFrontend(WKT.anyValueType2.withNullity(Nullity.OrNull), names)
            }
        }

        /** Determine the Java type for a function type given its single-access method. */
        private fun samJavaType(sam: Sam, funcType: Signature2, names: JavaNames): ReferenceType {
            return ReferenceType(
                name = sam.klassName,
                args = buildList {
                    if (sam.numTypeArgs > 0) {
                        funcType.valueFormalsExceptThis.forEachIndexed { idx, formal ->
                            if (sam.sig.formals[idx] == JstObject) {
                                this.add(JavaTypeArg.fromFormal(formal, names))
                            }
                        }
                        if (sam.sig.varArg == JstObject) {
                            this.add(JavaTypeArg.fromStatic(funcType.restInputsType!!, names))
                        }
                        if (sam.sig.returnType == JstObject) {
                            this.add(JavaTypeArg.fromStatic(funcType.returnType2, names))
                        }
                    }
                },
            )
        }
    }
}

/**
 * Type arguments are an existing, named type or declared type parameter that is applied to a generic type, e.g.
 *     List<T> myList;
 *     List<Integer> myList;
 */
sealed interface JavaTypeArg {
    fun toTypeArgAst(pos: Position): J.TypeArgument

    companion object {
        fun fromFormal(formal: ValueFormal2, names: JavaNames): JavaTypeArg =
            fromStatic(formal.type, names)
        fun fromStatic(type: Type2, names: JavaNames): JavaTypeArg =
            JavaType.fromFrontend(type, names).asReferenceType()
        fun fromTypeActual(a: Type2, names: JavaNames): JavaTypeArg = fromStatic(a, names)
    }
}

/** Type parameters declare a new type, e.g. `class Foo<Param, Param>`. */
sealed interface JavaTypeParam {
    fun toTypeParamAst(pos: Position): J.TypeParameter

    companion object {
        fun fromFormal(
            formal: TypeFormal,
            names: JavaNames,
        ): JavaTypeParam = when (formal) {
            is TypeFormal -> JavaTypeFormal(
                names.typeFormal(formal.name),
                formal.upperBounds
                    .sortedWith { a, b ->
                        // Java requires any class type to appear first.
                        // Sort types that erase to concrete types first.
                        b.definition.isJavaClassLike.compareTo(a.definition.isJavaClassLike)
                    }
                    .map {
                        JavaType.fromFrontend(hackMapOldStyleToNew(it), names).asReferenceType()
                    }
                    .filter {
                        !it.hasQualifiedName(javaLangObject)
                    },
            )
        }

        fun fromTmpL(formal: TmpL.TypeFormal, names: JavaNames): JavaTypeParam {
            return fromFormal(formal.definition, names)
        }
    }
}

/** The JVM primitive types, excluding void. */
@Suppress("unused")
enum class Primitive(val primitiveName: String, wrapperClassName: QualifiedName) : JavaType {
    JavaBoolean("boolean", javaLangBoolean),
    JavaChar("char", javaLangCharacter),
    JavaShort("short", javaLangShort),
    JavaByte("byte", javaLangByte),
    JavaInt("int", javaLangInteger),
    JavaLong("long", javaLangLong),
    JavaFloat("float", javaLangFloat),
    JavaDouble("double", javaLangDouble),
    ;

    private val refType = ReferenceType(wrapperClassName)
    private val nullableType = ReferenceType(wrapperClassName, isNullable = true)

    /** Convenience method to cast to a type. */
    fun cast(expr: J.Expression, pos: Position = expr.pos) =
        J.CastExpr(pos, type = J.PrimitiveType(pos, this), expr = expr)

    override fun toTypeAst(pos: Position) = J.PrimitiveType(pos, this)
    override fun asReferenceType(): ReferenceType = refType
    override fun toRawType(): ReferenceType = refType
    override fun makeNullable(): JavaType = nullableType
    override fun withPos(pos: Position): Primitive = this
    override fun toString(): String = primitiveName

    override fun renderTo(tokenSink: TokenSink) {
        tokenSink.word(primitiveName)
    }
}

object Void : JavaType {
    private val refType = ReferenceType(javaLangVoid)
    private val nullableType = ReferenceType(javaLangVoid, isNullable = true)
    override fun toTypeAst(pos: Position) = javaLangVoid.toClassType(pos)
    override fun toResultTypeAst(pos: Position) = J.VoidType(pos)
    override fun asReferenceType(): ReferenceType = refType
    override fun toRawType(): ReferenceType = refType
    override fun makeNullable(): JavaType = nullableType
    override fun withPos(pos: Position): Void = this
    override fun toString(): String = "void"

    override fun renderTo(tokenSink: TokenSink) {
        tokenSink.word("void")
    }
}

object Invalid : JavaType {
    private val refType = ReferenceType(temperInvalidStrict)
    private val nullableType = ReferenceType(temperInvalidStrict, isNullable = true)

    override fun toTypeAst(pos: Position): J.Type = refType.toTypeAst(pos)
    override fun asReferenceType(): ReferenceType = refType
    override fun toRawType(): ReferenceType = refType
    override fun makeNullable(): JavaType = nullableType
    override fun withPos(pos: Position): Invalid = this
    override fun toString(): String = temperInvalidStrict.fullyQualified

    override fun renderTo(tokenSink: TokenSink) {
        temperInvalidStrict.parts.forEachIndexed { i, part ->
            if (i != 0) { tokenSink.emit(OutToks.dot) }
            part.renderTo(tokenSink)
        }
    }
}

data class JavaTypeFormal(
    val name: OutName,
    val upperBounds: List<ReferenceType> = emptyList(),
    private val knownPos: Position? = null,
) : JavaTypeArg, JavaTypeParam {
    override fun toTypeArgAst(pos: Position): J.TypeArgument =
        J.ReferenceTypeArgument(
            knownPos ?: pos,
            annType = J.AnnotatedQualIdentifier(
                knownPos ?: pos,
                pkg = listOf(),
                anns = listOf(),
                type = J.Identifier(knownPos ?: pos, name),
            ),
        )

    override fun toTypeParamAst(pos: Position): J.TypeParameter =
        J.TypeParameter(
            pos = pos,
            anns = listOf(),
            type = name.toIdentifier(pos),
            upperBounds = upperBounds.map {
                it.toTypeAst(pos)
            },
        )

    override fun toString(): String = name.toString()
}

object Wildcard : JavaTypeArg {
    override fun toTypeArgAst(pos: Position): J.TypeArgument =
        J.WildcardTypeArgument(pos)
}

data class BoundedWildcard(
    val dir: Dir,
    val bound: JavaType,
) : JavaTypeArg {
    enum class Dir {
        Extends,
        Super,
    }

    override fun toTypeArgAst(pos: Position): J.TypeArgument {
        val classBound = bound.toTypeAst(pos) as J.ClassType
        val anns = classBound.anns
        val type = classBound.type
        val args = classBound.args
        return when (dir) {
            Dir.Extends -> J.ExtendsTypeArgument(pos, anns, type, args)
            Dir.Super -> J.SuperTypeArgument(pos, anns, type, args)
        }
    }
}

data class ReferenceType(
    val name: QualifiedName,
    val isNullable: Boolean = false,
    val args: List<JavaTypeArg> = listOf(),
    private val knownPos: Position? = null,
) : JavaType, JavaTypeArg {
    private fun typeArgs(pos: Position) =
        if (args.isNotEmpty()) J.TypeArguments(knownPos ?: pos, args.map { it.toTypeArgAst(pos) }) else null
    private fun anns(pos: Position) = if (isNullable) {
        listOf(annNullable.toAnnotation(knownPos ?: pos))
    } else {
        listOf()
    }

    override fun toTypeAst(pos: Position) = name.toClassType(knownPos ?: pos, typeArgs(pos), anns(pos))
    override fun toTypeArgAst(pos: Position): J.TypeArgument =
        name.toTypeArg(knownPos ?: pos, anns = anns(pos), args = typeArgs(pos))
    override fun asReferenceType(): ReferenceType = this
    override fun toRawType(): JavaType = if (args.isNotEmpty()) copy(args = listOf()) else this

    override fun makeNullable(): JavaType = if (isNullable) this else copy(isNullable = true)
    override fun withPos(pos: Position): ReferenceType = if (knownPos == null) copy(knownPos = pos) else this

    override fun renderTo(tokenSink: TokenSink) {
        toTypeAst(unknownPos).formatTo(tokenSink)
    }
}

internal fun simpleType(type: Type2): JavaSimpleType {
    val simple = SimplifyStaticType(type)
    val principal = simple.principal
    return if (simple.hasNullAnnotation) {
        JstObject
    } else {
        when (principal.definition) {
            WKT.voidTypeDefinition -> JstVoid
            WKT.booleanTypeDefinition -> JstBool
            WKT.intTypeDefinition -> JstInt
            WKT.float64TypeDefinition -> JstDouble
            WKT.neverTypeDefinition -> JstVoid
            WKT.bubbleTypeDefinition -> JstVoid
            else -> JstObject
        }
    }
}

internal fun signature(func: Signature2): SimpleSignature {
    val valueFormals = func.valueFormalsExceptThis

    return SimpleSignature(
        returnType = simpleType(func.returnType2.simplify()),
        formals = valueFormals.map {
            if (it.type.isBooleanLike) {
                JstObject
            } else {
                simpleType(it.type)
            }
        },
        varArg = func.restInputsType ?. let(::simpleType),
        requiredCount = valueFormals.countUntilMatch {
            it.kind == ValueFormalKind.Optional
        },
    )
}

private fun <T> Iterable<T>.countUntilMatch(predicate: (T) -> Boolean): Int {
    var num = 0
    for (e in this) {
        if (predicate(e)) {
            return num
        }
        num++
    }
    return num
}

/** Skip a lead 'this' argument when parsing a FunctionType. */
val Signature2.valueFormalsExceptThis: List<ValueFormal2> get() = allValueFormals.let {
    if (hasThisFormal) {
        it.drop(1)
    } else {
        it
    }
}

/** Test whether a specific type has a null annotation.  */
val Type2.isNullable: Boolean
    get() = SimplifyStaticType(this).hasNullAnnotation

/** Test whether a specific type has a null annotation.  */
val Type2?.isFunctional: Boolean
    get() = this != null && withType(
        this,
        fallback = { _ -> false },
        fn = { _, _, _ -> true },
    )

/**
 * Identify if the initial formal is nullable, e.g. `List<Foo?>`. Many newer Java containers don't accept null
 * elements, so we'll use the older variants if we aren't sure that the element type is not nullable.
 */
val Type2.hasNullableTypeActual: Boolean
    get() {
        val bindings = (this as? NonNullType)?.bindings ?: listOf()
        return bindings.size != 1 || bindings[0].isNullable
    }

private val sams = listOf(
    Sam("BiConsumer", "oov", "accept"),
    Sam("BiFunction", "ooo"),
    Sam("BiPredicate", "oob"),
    Sam("BooleanSupplier", "b", "getAsBoolean"),
    Sam("Consumer", "ov", "accept"),
    Sam("DoubleBinaryOperator", "ddd"),
    Sam("DoubleConsumer", "dv", "accept"),
    Sam("DoubleFunction", "do"),
    Sam("DoublePredicate", "db"),
    Sam("DoubleSupplier", "d", "getAsDouble"),
    Sam("DoubleToIntFunction", "di"),
    Sam("DoubleToLongFunction", "dl"),
    Sam("DoubleUnaryOperator", "dd"),
    Sam("Function", "oo"),
    Sam("IntBinaryOperator", "iii"),
    Sam("IntConsumer", "iv", "accept"),
    Sam("IntFunction", "io"),
    Sam("IntPredicate", "ib"),
    Sam("IntSupplier", "i", "getAsInt"),
    Sam("IntToDoubleFunction", "id"),
    Sam("IntToLongFunction", "il"),
    Sam("IntUnaryOperator", "ii"),
    Sam("LongBinaryOperator", "lll"),
    Sam("LongConsumer", "lv", "accept"),
    Sam("LongFunction", "lo"),
    Sam("LongPredicate", "lb"),
    Sam("LongSupplier", "l", "getAsLong"),
    Sam("LongToDoubleFunction", "ld"),
    Sam("LongToIntFunction", "li"),
    Sam("LongUnaryOperator", "ll"),
    Sam("ObjectDoubleConsumer", "odv", "accept"),
    Sam("ObjectIntConsumer", "oiv", "accept"),
    Sam("ObjectLongConsumer", "olv", "accept"),
    Sam("Predicate", "ob"),
    Sam("Supplier", "o", "get"),
    Sam("ToDoubleBiFunction", "ood"),
    Sam("ToDoubleFunction", "od"),
    Sam("ToIntBiFunction", "ooi"),
    Sam("ToIntFunction", "oi"),
    Sam("ToLongBiFunction", "ool"),
    Sam("ToLongFunction", "ol"),
    Sam("Runnable", "v", "run", pkg = javaLang),
).associateBy { it.sig }

val typeDefsToJava: Map<Symbol, QualifiedName> = mapOf(
    WKT.float64TypeDefinition.word!! to javaLangDouble,
    WKT.intTypeDefinition.word!! to javaLangInteger,
    WKT.booleanTypeDefinition.word!! to javaLangBoolean,
    WKT.stringTypeDefinition.word!! to javaLangString,
    WKT.anyValueTypeDefinition.word!! to javaLangObject,
    WKT.voidTypeDefinition.word!! to javaLangVoid,
    WKT.problemTypeDefinition.word!! to temperInvalidStrict,
    WKT.nullTypeDefinition.word!! to temperInvalidStrict,
    WKT.typeTypeDefinition.word!! to javaLangClass,
    WKT.generatorTypeDefinition.word!! to temperGenerator,
    WKT.safeGeneratorTypeDefinition.word!! to temperGenerator,
    WKT.generatorResultTypeDefinition.word!! to temperGeneratorResult,
    WKT.doneResultTypeDefinition.word!! to temperGeneratorDoneResult,
    WKT.valueResultTypeDefinition.word!! to temperGeneratorValueResult,
    Symbol("Console") to temperConsoleClass,
    Symbol("DenseBitVector") to javaUtilBitSet,
    Symbol("Deque") to javaUtilDeque,
    Symbol("GlobalConsole") to temperGlobalConsole,
    Symbol("List") to javaUtilList,
    Symbol("ListBuilder") to javaUtilList,
    Symbol("Listed") to javaUtilList,
    Symbol("Map") to javaUtilMap,
    Symbol("Mapped") to javaUtilMap,
    Symbol("MapBuilder") to javaUtilMap,
    Symbol("Pair") to javaUtilMapEntry,
    // equals and hashCode are defined on object so when these types are used as upper
    // bounds we can just connect that to Object.
    Symbol("Equatable") to javaLangObject,
    Symbol("MapKey") to javaLangObject,
)

internal fun JavaType.hasQualifiedName(qn: QualifiedName): Boolean =
    when (this) {
        Invalid -> false
        is Primitive -> false
        is ReferenceType -> this.name == qn
        Void -> false
    }

private val TypeDefinition.isJavaClassLike: Boolean get() =
    when (this) {
        is TypeShape -> this.abstractness == Abstractness.Concrete
        is TypeFormal -> this.superTypes.any { it.definition.isJavaClassLike }
    }
