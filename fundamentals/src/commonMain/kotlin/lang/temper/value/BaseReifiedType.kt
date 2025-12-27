package lang.temper.value

import lang.temper.format.OutToks
import lang.temper.format.OutputToken
import lang.temper.format.OutputTokenType
import lang.temper.format.TokenSerializable
import lang.temper.format.TokenSink
import lang.temper.format.toStringViaTokenSink
import lang.temper.name.BuiltinName
import lang.temper.type.AndType
import lang.temper.type.BubbleType
import lang.temper.type.FunctionType
import lang.temper.type.InvalidType
import lang.temper.type.NominalType
import lang.temper.type.OrType
import lang.temper.type.StaticType
import lang.temper.type.TopType
import lang.temper.type.TypeDefinition
import lang.temper.type.TypeFormal
import lang.temper.type.TypeShape
import lang.temper.type.WellKnownTypes
import lang.temper.type2.Descriptor
import lang.temper.type2.Nullity
import lang.temper.type2.Signature2
import lang.temper.type2.Type2
import lang.temper.type2.hackMapNewStyleToOld
import lang.temper.type2.hackMapOldStyleToNew
import lang.temper.type2.withType

/**
 * Expectations about how a value or AST is used that can be stored in
 * values with type tag [TType].
 *
 * In the context of [ReifiedType] this represents a regular type.
 * In the context of a macro's parameter, a [StructureExpectation], may express
 * expectations about the structure of the AST when the macro is applied,
 * so helps pick a sensitive order for macro applications.
 */
sealed class BaseReifiedType : TokenSerializable, StayReferrer {
    abstract fun accepts(value: Value<*>?, args: MacroActuals?, argIndex: Int): Boolean
}

/**
 * Reifies a [StaticType].
 *
 * Reified types have no semantics by themselves, but they are used to record type
 * information in the tree so that it can be used when inferring and checking types.
 *
 * During the interpretative dance we make an effort to check for obvious type errors.
 * In
 *
 *     let i: Int
 *
 * `Int` specifies that assignments of values that are not of [TInt] should be
 * rejected.
 */
data class ReifiedType(
    val type2: Type2,
    val hasExplicitActuals: Boolean = false,
) : BaseReifiedType(), OccasionallyHelpful {
    private val typeThunk = lazy { hackMapNewStyleToOld(type2) }
    val type: StaticType get() = typeThunk.value
    private var valuePredicateCache: Pair<ValuePredicate, Long>? = null
    val valuePredicate: ValuePredicate get() {
        val counter = when (val defn = type2.definition) {
            is TypeShape -> defn.mutationCount
            is TypeFormal -> null
        }
        val mcount = counter?.get() ?: 0
        val cached = valuePredicateCache
        return if (cached != null && mcount == cached.second) {
            cached.first
        } else {
            val valuePredicate = valuePredicateFor(type2)
            valuePredicateCache = valuePredicate to mcount
            valuePredicate
        }
    }

    override fun accepts(value: Value<*>?, args: MacroActuals?, argIndex: Int): Boolean =
        value != null && valuePredicate(value)

    override fun prettyPleaseHelp(): Helpful? = type2.definition.prettyPleaseHelp()

    /** For [WellKnownTypes] the builtin type name. */
    val builtinTypeName: BuiltinName?
        get() {
            val definition = type2.definition
            return if (definition is TypeShape && WellKnownTypes.isWellKnown(definition)) {
                definition.word?.let { BuiltinName(it.text) }
            } else {
                null
            }
        }

    override fun equals(other: Any?): Boolean =
        other is ReifiedType && this.type2 == other.type2

    override fun hashCode(): Int = type2.hashCode()

    override fun renderTo(tokenSink: TokenSink) {
        type2.renderTo(tokenSink)
    }

    override fun addStays(s: StaySink) {
        s.whenUnvisited(this) {
            type2.addStays(s)
        }
    }

    override fun toString() = toStringViaTokenSink { renderTo(it) }
}

sealed class StructureExpectation : BaseReifiedType() {
    abstract fun applicableTo(t: Tree): Boolean

    override fun accepts(value: Value<*>?, args: MacroActuals?, argIndex: Int): Boolean {
        if (args == null || argIndex < 0 || argIndex >= args.size) {
            return false
        }
        return applicableTo(args.valueTree(argIndex))
    }
}

data class TreeTypeStructureExpectation(
    val treeTypes: Set<TreeType>,
) : StructureExpectation(), Stayless {
    override fun applicableTo(t: Tree) = t.treeType in treeTypes

    override fun renderTo(tokenSink: TokenSink) {
        // TODO: come up with an actual syntax for these.
        tokenSink.emit(OutputToken("isTree", OutputTokenType.Name))
        tokenSink.emit(OutToks.leftParen)
        treeTypes.forEachIndexed { i, tt ->
            if (i != 0) {
                tokenSink.emit(OutToks.comma)
            }
            tokenSink.emit(OutputToken(tt.name, OutputTokenType.Name))
        }
        tokenSink.emit(OutToks.rightParen)
    }

    override fun toString() = toStringViaTokenSink { renderTo(it) }
}

data class SizeStructureExpectation(
    val minSize: Int,
    val maxSize: Int,
) : StructureExpectation(), Stayless {
    override fun applicableTo(t: Tree) = t.size in minSize..maxSize

    override fun renderTo(tokenSink: TokenSink) {
        // TODO: come up with an actual syntax for these.
        tokenSink.emit(OutputToken("treeHasSize", OutputTokenType.Name))
        tokenSink.emit(OutToks.leftParen)
        tokenSink.emit(OutputToken("$minSize", OutputTokenType.NumericValue))
        tokenSink.emit(OutToks.comma)
        tokenSink.emit(OutputToken("$maxSize", OutputTokenType.NumericValue))
        tokenSink.emit(OutToks.rightParen)
    }

    override fun toString() = toStringViaTokenSink { renderTo(it) }
}

data class ChildStructureExpectation(
    val childIndex: Int,
    val expectation: StructureExpectation,
) : StructureExpectation(), Stayless {
    override fun applicableTo(t: Tree) =
        childIndex in t.indices && expectation.applicableTo(t.child(childIndex))

    override fun renderTo(tokenSink: TokenSink) {
        // TODO: Come up with an actual syntax for these
        expectation.renderTo(tokenSink)
        tokenSink.emit(OutputToken("=~", OutputTokenType.Punctuation))
        tokenSink.emit(thisParsedName.toToken(inOperatorPosition = false))
        tokenSink.emit(OutToks.leftSquare)
        tokenSink.emit(OutputToken("$childIndex", OutputTokenType.NumericValue))
        tokenSink.emit(OutToks.rightSquare)
    }

    override fun toString() = toStringViaTokenSink { renderTo(it) }
}

data class IntersectionStructureExpectation(
    val structureExpectations: List<StructureExpectation>,
) : StructureExpectation(), Stayless {
    override fun applicableTo(t: Tree): Boolean = structureExpectations.all { it.applicableTo(t) }

    override fun renderTo(tokenSink: TokenSink) {
        // TODO: Come up with an actual syntax for these and handle precedence
        if (structureExpectations.isEmpty()) {
            tokenSink.emit(OutToks.leftParen)
            tokenSink.emit(OutToks.rightParen)
        } else {
            structureExpectations.forEachIndexed { index, structureExpectation ->
                if (index != 0) { tokenSink.emit(OutToks.amp) }
                structureExpectation.renderTo(tokenSink)
            }
        }
    }

    override fun toString() = toStringViaTokenSink { renderTo(it) }
}

object TypeStructureExpectation : StructureExpectation(), Stayless {
    override fun applicableTo(t: Tree): Boolean =
        t.reifiedTypeContained != null

    override fun renderTo(tokenSink: TokenSink) {
        tokenSink.emit(isBuiltinName.toToken(inOperatorPosition = false))
        tokenSink.emit(typeBuiltinName.toToken(inOperatorPosition = false))
    }

    override fun toString() = toStringViaTokenSink { renderTo(it) }
}

object TypesStructureExpectation : StructureExpectation(), Stayless {
    override fun applicableTo(t: Tree): Boolean {
        val v = t.valueContained ?: return false
        return when {
            // A type
            v.typeTag == TType -> true
            // or a list of types
            TList.unpackOrNull(v)?.all { it.typeTag == TType } == true -> true
            else -> false
        }
    }

    override fun renderTo(tokenSink: TokenSink) {
        tokenSink.emit(isBuiltinName.toToken(inOperatorPosition = false))
        tokenSink.emit(typeBuiltinName.toToken(inOperatorPosition = false))
        tokenSink.emit(OutToks.bar)
        tokenSink.emit(isBuiltinName.toToken(inOperatorPosition = false))
        tokenSink.word("List")
        tokenSink.emit(OutToks.leftAngle)
        tokenSink.emit(typeBuiltinName.toToken(inOperatorPosition = false))
        tokenSink.emit(OutToks.rightAngle)
    }

    override fun toString() = toStringViaTokenSink { renderTo(it) }
}

fun interface ValuePredicate {
    operator fun invoke(value: Value<*>): Boolean
}
private data object AlwaysTrueValuePredicate : ValuePredicate {
    override fun invoke(value: Value<*>): Boolean = true
}
private data object AlwaysFalseValuePredicate : ValuePredicate {
    override fun invoke(value: Value<*>): Boolean = false
}

@ConsistentCopyVisibility
private data class OrValuePredicate private constructor(private val ps: List<ValuePredicate>) : ValuePredicate {
    override fun invoke(value: Value<*>): Boolean = ps.any { it(value) }

    companion object {
        operator fun invoke(ps: Iterable<ValuePredicate>): ValuePredicate {
            val psFiltered = ps.flatMap {
                when (it) {
                    is OrValuePredicate -> it.ps
                    is AlwaysFalseValuePredicate -> listOf()
                    else -> listOf(it)
                }
            }
            return when (psFiltered.size) {
                0 -> AlwaysFalseValuePredicate
                1 -> psFiltered[0]
                else -> OrValuePredicate(psFiltered)
            }
        }
    }
}

@ConsistentCopyVisibility
private data class AndValuePredicate private constructor(private val ps: List<ValuePredicate>) : ValuePredicate {
    override fun invoke(value: Value<*>): Boolean = ps.all { it(value) }

    companion object {
        operator fun invoke(ps: Iterable<ValuePredicate>): ValuePredicate {
            val psFiltered = ps.flatMap {
                when (it) {
                    is AndValuePredicate -> it.ps
                    is AlwaysTrueValuePredicate -> listOf()
                    else -> listOf(it)
                }
            }
            return when (psFiltered.size) {
                0 -> AlwaysTrueValuePredicate
                1 -> psFiltered[0]
                else -> AndValuePredicate(psFiltered)
            }
        }
    }
}

private fun valuePredicateFor(type: Descriptor): ValuePredicate = when (type) {
    is Signature2 -> valuePredicateFor(TFunction)
    WellKnownTypes.anyValueOrNullType2 -> AlwaysTrueValuePredicate
    WellKnownTypes.invalidType2 -> AlwaysFalseValuePredicate
    is Type2 -> {
        var nullity = type.nullity
        val nonNull = withType(
            type,
            fn = { _, _, _ -> valuePredicateFor(TFunction) },
            never = { _, _, _ -> AlwaysFalseValuePredicate },
            result = { pass, _, _ ->
                valuePredicateFor(pass)
            },
            invalid = { _ ->
                nullity = Nullity.NonNull
                AlwaysFalseValuePredicate
            },
            // Incomplete types might end up being evaluated at runtime for `as` and `is` calls.
            // malformed = { _, _ ->
            //    nullity = Nullity.NonNull
            //    AlwaysFalseValuePredicate
            // },
            fallback = {
                valuePredicateFor(it.definition)
            },
        )
        when (nullity) {
            Nullity.NonNull -> nonNull
            Nullity.OrNull -> OrValuePredicate(listOf(nonNull, valuePredicateFor(TNull)))
        }
    }
}

private fun valuePredicateFor(type: StaticType): ValuePredicate = when (type) {
    TopType -> AlwaysTrueValuePredicate
    BubbleType,
    InvalidType,
    -> // if it's invalid, it can't produce reliable values
        AlwaysFalseValuePredicate
    is NominalType -> valuePredicateFor(type.definition)
    is FunctionType -> valuePredicateFor(WellKnownTypes.functionTypeDefinition)
    is OrType -> if (type.members.isEmpty()) {
        AlwaysFalseValuePredicate
    } else {
        val ps = type.members.map(::valuePredicateFor)
        if (ps.any { it is AlwaysTrueValuePredicate }) {
            AlwaysTrueValuePredicate
        } else {
            OrValuePredicate(ps)
        }
    }
    is AndType -> {
        val ps = type.members.map(::valuePredicateFor)
        if (ps.all { it == AlwaysTrueValuePredicate }) {
            AlwaysTrueValuePredicate
        } else {
            AndValuePredicate(ps)
        }
    }
}

private fun valuePredicateFor(definition: TypeDefinition): ValuePredicate =
    if (definition == WellKnownTypes.anyValueTypeDefinition) {
        notNullValuePredicate
    } else {
        when (definition) {
            is TypeFormal -> AndValuePredicate(
                buildSet {
                    definition.upperBounds.mapTo(this) { valuePredicateFor(hackMapOldStyleToNew(it)) }
                },
            )
            is TypeShape -> {
                IsValuePredicate(definition)
            }
        }
    }

private fun valuePredicateFor(typeTag: TypeTag<*>) = ValuePredicate {
    it.typeTag == typeTag
}

private data class IsValuePredicate(private val definition: TypeShape) : ValuePredicate {
    val wellKnownTypeName =
        if (WellKnownTypes.isWellKnown(definition)) {
            definition.name
        } else {
            null
        }

    override fun invoke(value: Value<*>): Boolean {
        val typeTag = value.typeTag
        return if (typeTag is TClass) {
            val typeShape = typeTag.typeShape
            typeShape.name == definition.name ||
                definition.name in typeShape.rawSuperTypeNames
        } else {
            wellKnownTypeName != null && typeTag.isCompatibleType(wellKnownTypeName)
        }
    }
}

private val notNullValuePredicate = ValuePredicate { it.typeTag != TNull }
