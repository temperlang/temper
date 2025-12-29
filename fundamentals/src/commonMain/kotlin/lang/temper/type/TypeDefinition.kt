package lang.temper.type

import lang.temper.common.AtomicCounter
import lang.temper.common.LawfulEvil
import lang.temper.common.LawfulEvilOverlord
import lang.temper.common.MappingListView
import lang.temper.common.StableReaderCount
import lang.temper.common.isNotEmpty
import lang.temper.common.lawfulEvilListOf
import lang.temper.common.putMultiList
import lang.temper.common.structure.Hints
import lang.temper.common.structure.StructureSink
import lang.temper.common.structure.Structured
import lang.temper.common.toStringViaBuilder
import lang.temper.format.OutToks
import lang.temper.format.OutputToken
import lang.temper.format.OutputTokenType
import lang.temper.format.TokenAssociation
import lang.temper.format.TokenSerializable
import lang.temper.format.TokenSink
import lang.temper.format.toStringViaTokenSink
import lang.temper.log.Position
import lang.temper.log.Positioned
import lang.temper.name.BuiltinName
import lang.temper.name.ImplicitsCodeLocation
import lang.temper.name.ModularName
import lang.temper.name.ModuleLocation
import lang.temper.name.NameMaker
import lang.temper.name.ParsedName
import lang.temper.name.ResolvedName
import lang.temper.name.Symbol
import lang.temper.name.TemperName
import lang.temper.value.DeclTree
import lang.temper.value.Helpful
import lang.temper.value.MetadataValueMultimap
import lang.temper.value.OccasionallyHelpful
import lang.temper.value.StayLeaf
import lang.temper.value.StayReferrer
import lang.temper.value.StaySink
import lang.temper.value.constructorPropertySymbol
import lang.temper.value.docStringSymbol
import lang.temper.value.fnSymbol
import lang.temper.value.fromTypeSymbol
import lang.temper.value.parameterNameSymbolsListSymbol
import lang.temper.value.qNameSymbol
import lang.temper.value.reachSymbol
import lang.temper.value.resolutionSymbol
import lang.temper.value.staticSymbol
import lang.temper.value.staySymbol
import lang.temper.value.typeMemberMetadataSymbols
import lang.temper.value.typeSymbol

/**
 * Information about a type and its super-type relationships used to answer [TypeContext.isSubType]
 * and similar questions.
 */
sealed interface TypeDefinition : StayReferrer, Structured, TokenSerializable, Positioned, OccasionallyHelpful {
    val name: ResolvedName
    val word: Symbol?
    val formals: List<TypeFormal>
    val superTypes: List<NominalType>
    val sourceLocation: ModuleLocation
        get() = when (val name = this.name) {
            is ModularName -> name.origin.loc
            is BuiltinName -> ImplicitsCodeLocation
        }

    /** Likely cheaper to access than [formals]. */
    val hasFormals: Boolean

    // TODO: store metadata from type formal definitions here to
    val metadata: MetadataValueMultimap get() = MetadataValueMultimap.empty

    fun renderName(tokenSink: TokenSink) {
        tokenSink.emit(name.toToken(inOperatorPosition = false))
    }

    /** Checks if this is a non-strict subtype of [maybeSup], ignoring [formals]. */
    fun isSubOrSame(maybeSup: TypeDefinition): Boolean {
        if (this === maybeSup) {
            return true
        }
        for (supType in superTypes) {
            if (supType.definition.isSubOrSame(maybeSup)) {
                return true
            }
        }
        return false
    }

    override fun prettyPleaseHelp(): Helpful? =
        helpfulFromMetadata(metadata)?.prettyPleaseHelp()
}

/** The name of the super-type for all non-failure types.  See the README. */
const val ANY_VALUE_TYPE_NAME_TEXT = "AnyValue"

/** The name of the super-type for all [function types][FunctionType]. */
const val FUNCTION_TYPE_NAME_TEXT = "Function"

/**
 * Represents a function argument that takes multiple
 * TODO(rest formal) should this get integrated into the hierarchy here better?
 * @param type the type as seen from the invocation so `Foo` not `List<Foo>`
 */
data class RestFormal(val name: TemperName, val type: StaticType, val position: Position, val tree: DeclTree)

/**
 * A formal type parameter describes how the containing type can be parameterized.
 *
 * In `class C<A, B>`, the class *C* has two formal type parameters: *A* and *B*.
 *
 * In the type expression, `C<String, Int>` has two [actual][TypeActual] bindings:
 * the formal *A* is bound to the actual `String` and *B* is bound to *Int*.
 */
sealed interface TypeFormal : TypeDefinition {
    override val word: Symbol?
    override val formals get() = emptyList<Nothing>()
    override val hasFormals: Boolean get() = false

    val variance: Variance

    /** Required super-types for actual types that bind to this formal. */
    val upperBounds: List<NominalType>

    override val superTypes get() = upperBounds

    override fun addStays(s: StaySink) {
        upperBounds.forEach { it.addStays(s) }
    }

    override fun renderTo(tokenSink: TokenSink) {
        variance.keyword?.let {
            tokenSink.emit(OutputToken(it, OutputTokenType.Word, TokenAssociation.Prefix))
        }
        tokenSink.emit(name.toToken(inOperatorPosition = false))
        if (upperBounds.isNotEmpty()) {
            tokenSink.emit(OutToks.extendsWord)
            upperBounds.forEachIndexed { i, t ->
                if (i != 0) {
                    tokenSink.emit(OutToks.amp)
                }
                t.renderTo(tokenSink)
            }
        }
    }

    companion object {
        operator fun invoke(
            pos: Position,
            name: ResolvedName,
            symbol: Symbol?,
            variance: Variance,
            mutationCount: AtomicCounter,
            upperBounds: List<NominalType> = emptyList(),
        ): TypeFormal = MutableTypeFormal(
            pos,
            name,
            symbol,
            variance,
            mutationCount,
            upperBounds,
        )
    }
}

/**
 * A mutable [TypeFormal].
 * This is mutable to allow type formals to be allocated before bounds can be resolved so that
 * user macros could be involved in that process while requiring explicit casting to be able to
 * mutate.
 */
class MutableTypeFormal(
    override val pos: Position,
    override val name: ResolvedName,
    override val word: Symbol?,
    override var variance: Variance,
    mutationCount: AtomicCounter,
    upperBounds: List<NominalType>,
) : TypeFormal, LawfulEvilOverlord() {
    override val upperBounds = lawfulEvilListOf<NominalType>(mutationCount)
    init {
        if (upperBounds.isNotEmpty()) {
            this.upperBounds.addAll(upperBounds)
        }
    }

    override fun toString() = toStringViaBuilder {
        it.append("(TypeFormal ")
        if (!variance.keyword.isNullOrEmpty()) {
            it.append(variance.keyword).append(' ')
        }
        it.append(name)
        if (upperBounds.isNotEmpty()) {
            it.append(" extends ")
            upperBounds.joinTo(it, " & ")
        }
        it.append(')')
    }

    override fun equals(other: Any?): Boolean =
        other is MutableTypeFormal && this.name == other.name &&
            this.variance == other.variance && this.upperBounds == other.upperBounds

    override fun hashCode(): Int =
        name.hashCode() + 31 * (variance.hashCode() + 31 * upperBounds.hashCode())

    override val minions: List<LawfulEvil> get() = listOf(upperBounds)

    override fun destructure(structureSink: StructureSink) = structureSink.obj {
        key("name") { value(name) }
        key("word", Hints.u) { value(word) }
        key("variance", isDefault = variance == Variance.Default) { value(variance) }
        val extendsOnlyAnyValue = upperBounds.size == 1 &&
            upperBounds[0].let { upperBound ->
                upperBound.definition.name == WellKnownTypes.anyValueTypeDefinition.name &&
                    upperBound.bindings.isEmpty()
            }
        key("upperBounds", isDefault = extendsOnlyAnyValue) {
            value(upperBounds)
        }
    }
}

/**
 * A type shape includes information about a Temper `class` or `interface` along with members.
 *
 * Each type shape is a [TypeDefinition]: it includes enough super-type information to answer
 * [isSubType][lang.temper.type.TypeContext.isSubType] questions.
 *
 * But it also includes info about members, so can answer questions like:
 *
 * - is `x`, appearing in the body of a type, an implicit reference to `this.x`?
 * - can `this.f()` be turned into a call to the method `f` defined in this shape's type body,
 *   or might it be over-ridden in a subtype?
 * - how many method variants are there with the same name which need to be externalized as an
 *   umbrella function?
 */
sealed interface TypeShape : TypeDefinition {
    /**
     * A key that identifies this type shape in the context of a set of type shapes.
     */
    override val name: ModularName

    /** The symbol text associated with the type's name, `\C` for `class C`. */
    override val word: Symbol?

    /**
     * Abstract &rarr; the type cannot be directly implemented; all instances of this type must
     * also be instances of a non-abstract subtype.
     */
    val abstractness: Abstractness

    /**
     * A reference to the declaration that specifies this type.
     */
    val stayLeaf: StayLeaf?

    /** The resolved type expressions in the `extends` clause. */
    override val superTypes: List<NominalType>

    /** For a sealed interface, its direct non-type-formal subtypes' shapes. Null otherwise. */
    val sealedSubTypes: List<TypeShape>?

    /**
     * Formal type parameters inside the `<...>` in the type header.
     * For example, `class C<T>` has a formal type parameter `T`.
     */
    val typeParameters: List<TypeParameterShape>

    override val hasFormals: Boolean get() = typeParameters.isNotEmpty()

    /** Shapes of properties for the type. */
    val properties: List<PropertyShape>

    /** Shapes of methods for the type. */
    val methods: List<MethodShape>

    /** Shapes of static properties for the type. */
    val staticProperties: List<StaticPropertyShape>

    /**
     * 0 if there are no super types or 1 + the inheritance depth of the deepest super-type.
     * This is useful in ordering to properly handle
     * [diamond](https://en.wikipedia.org/wiki/Multiple_inheritance#The_diamond_problem)
     * inheritance.
     */
    val inheritanceDepth: Int

    /**
     * Derived from decorators applied to type definitions.
     */
    override val metadata: MetadataValueMultimap get() = when (val sl = stayLeaf) {
        null -> MetadataValueMultimap.empty
        else -> MetadataValueMultimapImpl(sl)
    }

    /**
     * All members (parameters, properties, and methods) defined on the type, not including any
     * inherited from super-types.
     */
    val members: List<MemberShape> get() = typeParameters + properties + methods + staticProperties

    override fun addStays(s: StaySink) {
        this.stayLeaf?.let { stayLeaf ->
            s.whenUnvisited(this) {
                s.add(stayLeaf)
            }
        }
        for (m in members) {
            m.addStays(s)
        }
    }

    /**
     * All members with the given symbol, including those from the super type, in inheritance order.
     */
    fun membersMatching(symbol: Symbol): Iterable<MemberShape>

    /**
     * The transitive closure of the type IDs of super types including [name].
     * Iteration order places closer super type's IDs before deeper super types.
     */
    val rawSuperTypeNames: Set<ModularName>

    /**
     * If this is mutable, an equivalent but not mutable shape.
     * The original is not required to continue being mutable after this call.
     */
    fun toTypeShape(): TypeShape = this

    override val formals: List<TypeFormal>

    /** Allows caching of derived, mutable state */
    val mutationCount: AtomicCounter

    override fun destructure(structureSink: StructureSink) = structureSink.obj {
        key("name") { value(name) }
        key("word", Hints.u) { value(word) }
        key("abstract", isDefault = abstractness == Abstractness.Concrete) {
            value(abstractness == Abstractness.Abstract)
        }
        val typeParameters = typeParameters
        key("typeParameters", isDefault = typeParameters.isEmpty()) { value(typeParameters) }
        val superTypes = superTypes
        key("supers", isDefault = superTypes.isEmpty()) { value(superTypes) }
        val sealedSubTypes = sealedSubTypes?.map { it.name } ?: emptyList()
        key("sealedSubTypes", isDefault = sealedSubTypes.isEmpty()) { value(sealedSubTypes) }
        val properties = properties
        key("properties", isDefault = properties.isEmpty()) { value(properties) }
        val methods = methods
        key("methods", isDefault = methods.isEmpty()) { value(methods) }
        val staticProperties = staticProperties
        key("staticProperties", isDefault = staticProperties.isEmpty()) { value(staticProperties) }
        val metadata = metadata
        key("metadata", isDefault = metadata.all { (k) -> k in ignorableMetadataInTest }) {
            obj {
                metadata.forEach { (k, edges) ->
                    val hints = if (k in ignorableMetadataInTest) {
                        Hints.u
                    } else {
                        emptySet()
                    }
                    key(k.text, hints) {
                        arr {
                            edges.forEach { v ->
                                value(v)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun renderTo(tokenSink: TokenSink) {
        tokenSink.emit(
            OutputToken(
                when (abstractness) {
                    Abstractness.Abstract -> "class"
                    Abstractness.Concrete -> "interface"
                },
                OutputTokenType.Word,
            ),
        )
        renderName(tokenSink)
    }
}

/** A type shape that is mutable. */
sealed interface MutableTypeShape : TypeShape {
    override var word: Symbol?
    override var stayLeaf: StayLeaf?

    override val superTypes: MutableList<NominalType>
    override var sealedSubTypes: List<TypeShape>?
    override val typeParameters: MutableList<TypeParameterShape>
    override val properties: MutableList<PropertyShape>
    override val methods: MutableList<MethodShape>
    override val staticProperties: MutableList<StaticPropertyShape>

    override fun toTypeShape(): TypeShape
}

class TypeShapeImpl(
    override var pos: Position,
    word: Symbol?,
    override val name: ModularName,
    override val abstractness: Abstractness,
    override val mutationCount: AtomicCounter,
) : LawfulEvilOverlord(), MutableTypeShape {
    private var _word = word
    override var stayLeaf: StayLeaf? = null
    private val stableReaderCount = StableReaderCount()
    override var sealedSubTypes: List<TypeShape>?
        get() = _sealedSubTypes
        set(newValue) {
            stableReaderCount.doWhileUnstable {
                this._sealedSubTypes = newValue
            }
        }
    private var _sealedSubTypes: List<TypeShape>? = null

    constructor(
        pos: Position,
        word: Symbol?,
        nameMaker: NameMaker,
        abstractness: Abstractness,
        mutationCount: AtomicCounter,
    ) : this(
        pos,
        word,
        allocTypeName(word, nameMaker),
        abstractness,
        mutationCount,
    )

    override var word: Symbol?
        get() = _word
        set(newValue) { _word = newValue }
    override val superTypes = lawfulEvilListOf<NominalType>(mutationCount)
    override val typeParameters = lawfulEvilListOf<TypeParameterShape>(mutationCount)
    override val properties = lawfulEvilListOf<PropertyShape>(mutationCount)
    override val methods = lawfulEvilListOf<MethodShape>(mutationCount)
    override val staticProperties = lawfulEvilListOf<StaticPropertyShape>(mutationCount)
    override val formals: List<TypeFormal> = MappingListView(typeParameters) { it.definition }

    override fun membersMatching(symbol: Symbol): Iterable<MemberShape> =
        getMembersBySymbolMap()[symbol] ?: emptyList()

    private var membersBySymbolMapCached: Pair<Map<Symbol, List<MemberShape>>, Long>? = null
    private fun getMembersBySymbolMap(): Map<Symbol, List<MemberShape>> {
        val cached = membersBySymbolMapCached
        val mutCountValue = mutationCount.get()
        if (cached != null && cached.second == mutCountValue) {
            return cached.first
        }
        membersBySymbolMapCached = null

        val map = mutableMapOf<Symbol, MutableList<MemberShape>>()
        // Use a queue to walk super-types in depth order.
        val seen = mutableSetOf<TypeShapeImpl>()
        val toWalk = ArrayDeque<TypeShapeImpl>()
        toWalk.add(this)
        while (toWalk.isNotEmpty()) {
            val typeShape = toWalk.removeFirst()
            if (typeShape in seen) {
                continue
            }
            seen.add(typeShape)
            for (p in typeShape.properties) {
                map.putMultiList(p.symbol, p)
            }
            for (m in typeShape.methods) {
                map.putMultiList(m.symbol, m)
            }
            for (p in typeParameters) {
                map.putMultiList(p.symbol, p)
            }
            // static members are not members of instances so are not in the symbol map, and are not
            // inherited
            for (superType in typeShape.superTypes) {
                val superTypeShape = superType.definition as? TypeShapeImpl
                    ?: continue
                toWalk.add(superTypeShape)
            }
        }

        val imap = map.mapValues { it.value.toList() }
        membersBySymbolMapCached = imap to mutCountValue
        return imap
    }

    private var rawSuperTypeNamesCached: Pair<Set<ModularName>, Long>? = null
    override val rawSuperTypeNames: Set<ModularName>
        get() {
            val cached = rawSuperTypeNamesCached
            val mutCountValue = mutationCount.get()
            if (cached != null && cached.second == mutCountValue) {
                return cached.first
            }
            rawSuperTypeNamesCached = null

            val mutSet = mutableSetOf<ModularName>()
            val queue = ArrayDeque<TypeShape>()
            queue.add(this)
            while (queue.isNotEmpty()) {
                val element = queue.removeFirst()
                val name = element.name
                if (name !in mutSet) {
                    mutSet.add(name)
                    for (superType in element.superTypes) {
                        val definition = superType.definition
                        if (definition is TypeShape) {
                            queue.add(definition)
                        }
                    }
                }
            }
            val nameSet = mutSet.toSet()
            rawSuperTypeNamesCached = nameSet to mutCountValue
            return nameSet
        }

    private var inheritanceDepthCached: Pair<Int, Long>? = null
    override val inheritanceDepth: Int
        get() {
            val cached = inheritanceDepthCached
            val mutCountValue = mutationCount.get()
            if (cached != null && cached.second == mutCountValue) {
                return cached.first
            }
            // Use a map and a helper to avoid inf. recursion on inheritance cycles.
            // Inheritance cycles are handled eventually, so most code tries to be robust in the
            // face of them and may break cycles arbitrarily.
            return findInheritanceDepth(mutableMapOf(), mutCountValue)
        }

    private fun findInheritanceDepth(
        inhDepMap: MutableMap<TypeShape, Int>,
        mutCountValue: Long,
    ): Int {
        val known = inhDepMap[this]
        if (known != null) { return known }

        val cached = inheritanceDepthCached
        if (cached != null && cached.second == mutCountValue) {
            return cached.first
        }

        var lowerBound = 0
        inhDepMap[this] = lowerBound // Store value to break inheritance cycles.
        for (superType in this.superTypes) {
            val superDepth = when (val superDefinition = superType.definition) {
                is TypeShapeImpl -> superDefinition.findInheritanceDepth(inhDepMap, mutCountValue)
                is TypeShape -> superDefinition.inheritanceDepth
                else -> 0
            }
            if (superDepth >= lowerBound) {
                lowerBound = superDepth + 1
                inhDepMap[this] = lowerBound
            }
        }
        inheritanceDepthCached = lowerBound to mutCountValue
        return lowerBound
    }

    override fun toTypeShape(): TypeShape {
        if (this.stableReaderCount.readerCount == 0) {
            this.stableReaderCount.incrStableReaderCount()
        }
        return this
    }

    override val minions: List<LawfulEvil>
        get() = listOf(stableReaderCount, superTypes, typeParameters, properties, methods)

    override fun renderName(tokenSink: TokenSink) {
        val word = _word
        if (word != null && WellKnownTypes.isWellKnown(this)) {
            tokenSink.emit(OutputToken(word.text, OutputTokenType.Name))
        } else {
            tokenSink.emit(name.toToken(inOperatorPosition = false))
        }
    }

    override fun toString(): String = toStringViaTokenSink {
        renderName(it)
    }
}

private fun allocTypeName(typeSymbol: Symbol?, nameMaker: NameMaker): ModularName {
    val nameHint = typeSymbol?.text ?: "Anon"
    return nameMaker.unusedSourceName(ParsedName(nameHint))
}

val ignorableMetadataInTest = setOf(
    // The `"types": { ... }` metadata in stage snapshots should
    // not require copying doc string symbols for implicitly imported types.
    docStringSymbol,
    fromTypeSymbol, // Redundant because it appears with the type
    resolutionSymbol, // Redundant because it appears with the name in later stages
    staticSymbol, // Redundant because it appears under staticProperties
    staySymbol, // Not expressible as a value
    qNameSymbol, // Implied by position in input file
    reachSymbol, // Auto-generated
    typeSymbol, // Almost always present on properties, statics
    fnSymbol, // Often present on statics
    constructorPropertySymbol, // Often present on type members
    parameterNameSymbolsListSymbol, // Autogenerated. Often present on methods
) + typeMemberMetadataSymbols // Redundant because of position in the type record
