package lang.temper.type

import lang.temper.common.OpenOrClosed
import lang.temper.common.structure.Hints
import lang.temper.common.structure.PropertySink
import lang.temper.common.structure.StructureSink
import lang.temper.common.structure.Structured
import lang.temper.format.OutToks
import lang.temper.format.OutputToken
import lang.temper.format.OutputTokenType
import lang.temper.format.TokenSerializable
import lang.temper.name.Symbol
import lang.temper.name.TemperName
import lang.temper.type2.Descriptor
import lang.temper.type2.Signature2
import lang.temper.type2.Type2
import lang.temper.value.MetadataValueMultimap
import lang.temper.value.StayLeaf
import lang.temper.value.StayReferrer
import lang.temper.value.StaySink
import lang.temper.value.fnSymbol

/** A named fragment of a [TypeShape]. */
sealed class MemberShape(
    /**
     * The id of the enclosing type shape.
     */
    val enclosingType: TypeShape,
    /**
     * An internal name that can be used to reference the member in the context of the class body.
     */
    val name: TemperName,
    /**
     * A symbol corresponding to the text after the dot used to refer to this member.
     */
    val symbol: Symbol,
    /**
     * A reference to the declaration that specifies this member.
     */
    val stay: StayLeaf?,
) : Structured, StayReferrer {
    protected open fun destructureCommonProperties(sink: PropertySink) {
        sink.key("enclosingTypeName", Hints.u) { value(enclosingType.name) }
        sink.key("name") { value(name) }
        sink.key("symbol", isDefault = symbol == name.toSymbol()) { value(symbol) }
    }

    abstract fun withEnclosingType(newEnclosingType: TypeShape): MemberShape

    val metadata: MetadataValueMultimap
        get() = when (stay) {
            null -> MetadataValueMultimap.empty
            else -> MetadataValueMultimapImpl(stay)
        }
}

/** The shape of a type parameter.  E.g. `T` in `class C<T>`. */
class TypeParameterShape(
    enclosingType: TypeShape,
    val definition: TypeFormal,
    symbol: Symbol,
    stay: StayLeaf?,
) : MemberShape(enclosingType, definition.name, symbol, stay) {
    override fun withEnclosingType(
        newEnclosingType: TypeShape,
    ): TypeParameterShape = TypeParameterShape(
        newEnclosingType,
        definition,
        symbol,
        stay,
    )

    override fun addStays(s: StaySink) {
        s.whenUnvisited(this) {
            s.add(stay)
        }
    }

    override fun destructure(structureSink: StructureSink) = structureSink.obj {
        destructureCommonProperties(this)
    }

    override fun toString(): String = "(TypeParameterShape ${definition.name})"
}

/**
 * A member that is part of the enclosing type's interface.
 */
sealed class VisibleMemberShape(
    enclosingType: TypeShape,
    name: TemperName,
    symbol: Symbol,
    stay: StayLeaf?,
    val visibility: Visibility,
) : MemberShape(enclosingType, name, symbol, stay) {
    // Filled in by *Typer* towards the end of the type stage.
    abstract val descriptor: Descriptor?

    /** Extra info about parameters useful for translating */
    var parameterInfo: ExtraNonNormativeParameterInfo? = null

    /**
     * Shapes in super-types of the enclosing type that are masked/overridden by this shape.
     *
     * So if `class C` extends `interface I` and both define method `let f(): Void`, then
     * `class C`'s method shape for `f` will have a list containing `interface I`'s
     * method shape for `f` at the time typing completes for the module containing the
     * definition of `class C`.
     */
    var overriddenMembers: Set<MemberOverride2>? = null

    override fun destructureCommonProperties(sink: PropertySink) {
        super.destructureCommonProperties(sink)
        val isVisibilityDefault = visibility == Visibility.Public
        sink.key("visibility", isDefault = isVisibilityDefault) { visibility.destructure(this) }
    }

    abstract override fun withEnclosingType(
        newEnclosingType: TypeShape,
    ): VisibleMemberShape

    /** A compact form that can be rendered in an error message */
    abstract val loggable: TokenSerializable
}

/**
 * The shape of a property.  E.g. `x` in `class C { x: T }`.
 */
class PropertyShape(
    enclosingType: TypeShape,
    name: TemperName,
    symbol: Symbol,
    stay: StayLeaf?,
    visibility: Visibility,
    /**
     * A [Abstractness.Concrete] (aka **backed**) property is backed by space in
     * instances' state vectors.
     * Code internal to the class body that reads/writes these properties does so directly using
     * the [getp][lang.temper.builtin.BuiltinFuns.getpFn] and
     * [setp][lang.temper.builtin.BuiltinFuns.setpFn] builtins rather than via a getter/setter.
     *
     * An [Abstractness.Abstract] property is not backed by space in an instance's state vector.
     * All properties defined on `interface`s are abstract; they imply a getter and setter that
     * must be implemented by any class that `extends` that interface.
     *
     * Properties in a `class` body that have explicit getters and setters are also abstract though
     * implied getters and setters are added in a late stage of type processing for `public`&backed
     * properties.
     */
    val abstractness: Abstractness,
    /** Name of any implementation of the getter in the scope of the class body. */
    val getter: TemperName?,
    /** Name of any implementation of the setter in the scope of the class body. */
    val setter: TemperName?,
    /**
     * Whether a setter should exist for this property even if abstract in the current type.
     * Default is a best effort guess, but more info is needed for accuracy.
     * TODO Also define hasGetter? Skipped because not needed for now and awkward across stages.
     */
    val hasSetter: Boolean = setter != null,
) : VisibleMemberShape(enclosingType, name, symbol, stay, visibility) {
    override var descriptor: Type2? = null

    override fun destructure(structureSink: StructureSink) = structureSink.obj {
        destructureCommonProperties(this)
        key(
            "abstract",
            hints = if (getter == null && setter == null) Hints.empty else Hints.u,
        ) { value(abstractness == Abstractness.Abstract) }
        key("getter", isDefault = getter == null) { value(getter) }
        key("setter", isDefault = setter == null) { value(setter) }
    }

    override fun addStays(s: StaySink) {
        s.add(stay)
    }

    override fun withEnclosingType(newEnclosingType: TypeShape): PropertyShape =
        copy(enclosingType = newEnclosingType)

    fun copy(
        enclosingType: TypeShape = this.enclosingType,
        getter: TemperName? = this.getter,
        setter: TemperName? = this.setter,
    ): PropertyShape {
        val newPropertyShape = PropertyShape(
            enclosingType = enclosingType,
            name = name,
            symbol = symbol,
            stay = stay,
            visibility = visibility,
            abstractness = abstractness,
            getter = getter,
            setter = setter,
        )
        newPropertyShape.descriptor = descriptor
        newPropertyShape.overriddenMembers = overriddenMembers
        return newPropertyShape
    }

    override fun toString(): String = "PropertyShape(${enclosingType.name}.$name)"

    override val loggable: TokenSerializable
        get() = TokenSerializable { tokenSink ->
            tokenSink.emit(OutToks.dot)
            tokenSink.emit(OutputToken(symbol.text, OutputTokenType.Name))
        }
}

/**
 * Static properties are like top-level variables but are associated with a type and are accessed
 * via `TypeName.propertyName` syntax.
 */
class StaticPropertyShape(
    enclosingType: TypeShape,
    name: TemperName,
    symbol: Symbol,
    stay: StayLeaf?,
    visibility: Visibility,
) : VisibleMemberShape(enclosingType, name, symbol, stay, visibility) {
    /** A [Type2] for a non-method like static property, but a [Signature2] for a method like static */
    override var descriptor: Descriptor? = null

    override fun destructure(structureSink: StructureSink) = structureSink.obj {
        destructureCommonProperties(this)
    }

    override fun addStays(s: StaySink) {
        s.add(stay)
    }

    override fun withEnclosingType(newEnclosingType: TypeShape) = StaticPropertyShape(
        enclosingType = newEnclosingType,
        name = name,
        symbol = symbol,
        stay = stay,
        visibility = visibility,
    )

    override fun toString(): String = "StaticPropertyShape(${enclosingType.name}.$name)"

    override val loggable: TokenSerializable
        get() = TokenSerializable { tokenSink ->
            tokenSink.emit(OutToks.staticWord)
            tokenSink.emit(OutToks.dot)
            tokenSink.emit(OutputToken(symbol.text, OutputTokenType.Name))
            if (fnSymbol in metadata) {
                tokenSink.emit(OutToks.leftParen)
                tokenSink.emit(OutToks.ellipses)
                tokenSink.emit(OutToks.rightParen)
            }
        }
}

enum class MethodKind {
    Normal,
    Getter,
    Setter,
    Constructor,
}

/**
 * The shape of a method, which may be called directly, or read to return a bound function.
 */
class MethodShape(
    enclosingType: TypeShape,
    name: TemperName,
    symbol: Symbol,
    stay: StayLeaf?,
    visibility: Visibility,
    val methodKind: MethodKind,
    /**
     * An open method that may be overridden in a sub-type, so even internal access must be virtual.
     * Since class types are not extendable, all methods defined in an `interface` are open, but
     * none defined in a `class` are.
     *
     * A closed method may not be overridden in a sub-type, so internal uses may be direct by name
     * instead of going through an umbrella function.
     */
    val openness: OpenOrClosed,
) : VisibleMemberShape(enclosingType, name, symbol, stay, visibility) {
    override var descriptor: Signature2? = null

    /** True for methods that do not have a body */
    var isPureVirtual = false

    override fun destructure(structureSink: StructureSink) = structureSink.obj {
        destructureCommonProperties(this)
        key("kind", isDefault = methodKind == MethodKind.Normal) {
            value(methodKind)
        }
        key("open", isDefault = openness == OpenOrClosed.Open) {
            value(openness == OpenOrClosed.Open)
        }
    }

    override fun withEnclosingType(newEnclosingType: TypeShape): MethodShape =
        MethodShape(newEnclosingType, name, symbol, stay, visibility, methodKind, openness)

    override fun addStays(s: StaySink) {
        s.add(stay)
    }

    override fun toString(): String = "MethodShape($methodKind ${enclosingType.name}.$name)"

    override val loggable: TokenSerializable
        get() = TokenSerializable { tokenSink ->
            when (methodKind) {
                MethodKind.Normal -> null
                MethodKind.Getter -> OutToks.getWord
                MethodKind.Setter -> OutToks.setWord
                MethodKind.Constructor -> null
            }?.let { tokenSink.emit(it) }
            tokenSink.emit(OutToks.dot)
            tokenSink.emit(OutputToken(symbol.text, OutputTokenType.Name))
            tokenSink.emit(OutToks.leftParen)
            tokenSink.emit(OutToks.ellipses)
            tokenSink.emit(OutToks.rightParen)
        }
}

sealed class MemberAccessor(
    /** Prefix for generated identifiers */
    val prefix: String,
    /**
     * The index in the [DotHelper] argument list of the enclosing type (for internal uses) or `-1`.
     */
    val enclosingTypeIndexOrNegativeOne: Int,
) {
    /**
     * The index of the first actual argument.
     */
    val firstArgumentIndex = enclosingTypeIndexOrNegativeOne + 1

    fun prefix(symbol: Symbol) = Symbol("${prefix}_${symbol.text}")
    abstract fun prefix(member: VisibleMemberShape): Symbol?

    override fun toString(): String = prefix
}

sealed class InternalMemberAccessor(
    prefix: String,
) : MemberAccessor(prefix, enclosingTypeIndexOrNegativeOne = 0) {
    override fun prefix(member: VisibleMemberShape) = prefix(member.symbol)
}

sealed class ExternalMemberAccessor(
    prefix: String,
) : MemberAccessor(prefix, enclosingTypeIndexOrNegativeOne = -1) {
    override fun prefix(member: VisibleMemberShape) = when (member.visibility) {
        Visibility.Private, Visibility.Protected -> null
        Visibility.Public -> prefix(member.symbol)
    }
}

sealed interface GetMemberAccessor
sealed interface SetMemberAccessor
sealed interface BindMemberAccessor

object InternalGet : InternalMemberAccessor("iget"), GetMemberAccessor
object InternalSet : InternalMemberAccessor("iset"), SetMemberAccessor
object InternalBind : InternalMemberAccessor("ibind"), BindMemberAccessor
object ExternalGet : ExternalMemberAccessor("get"), GetMemberAccessor
object ExternalSet : ExternalMemberAccessor("set"), SetMemberAccessor
object ExternalBind : ExternalMemberAccessor("bind"), BindMemberAccessor
