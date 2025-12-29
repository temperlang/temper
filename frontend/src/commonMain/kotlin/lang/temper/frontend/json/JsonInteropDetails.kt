package lang.temper.frontend.json

import lang.temper.common.structure.StructureSink
import lang.temper.common.structure.Structured
import lang.temper.log.Position
import lang.temper.log.Positioned
import lang.temper.name.ResolvedName
import lang.temper.name.Symbol
import lang.temper.type.Abstractness
import lang.temper.type.StaticType
import lang.temper.type.TypeFormal
import lang.temper.type.TypeShape
import lang.temper.value.Value

internal class JsonInteropDetails(
    val localTypes: Map<ResolvedName, TypeDecl>,
    val stdJson: StdJson,
) {
    /** Names of exports from std/json */
    data class StdJson(
        val typeJsonAdapter: TypeShape,
        val typeJsonProducer: TypeShape,
        val typeInterchangeContext: TypeShape,
        val typeJsonSyntaxTree: TypeShape,
        val typeJsonObject: TypeShape,
        val typeJsonBoolean: TypeShape,
        val typeJsonFloat64: TypeShape,
        val typeJsonInt: TypeShape,
        val typeJsonNull: TypeShape,
        val typeJsonNumeric: TypeShape,
        val typeJsonString: TypeShape,
        val typeOrNullJsonAdapter: TypeShape,
    )

    sealed interface HowToConstruct {
        data object CannotIsAbstract : HowToConstruct
        data object CannotNoConstructor : HowToConstruct

        /**
         * We know how to construct an instance by passing backed property
         * values as constructor arguments.
         */
        data class ViaConstructor(
            val propertyNames: List<ResolvedName>,
        ) : HowToConstruct
    }

    data class PropertyDecl(
        val pos: Position,
        val name: ResolvedName,
        val symbol: Symbol,
        val abstractness: Abstractness,
        val type: StaticType?,
        /** Non-null for extra properties */
        val knownValue: Value<*>?,
        /** True if the property corresponds to a JSON object property in the encoded form */
        val shouldEncode: Boolean = knownValue != null || abstractness == Abstractness.Concrete,
        val jsonPropertyKey: String = symbol.text,
    )

    enum class MethodPresence(val available: Boolean) {
        Absent(false),
        Inherited(true),
        Present(true),
    }

    data class TypeDecl(
        override val pos: Position,
        val definition: TypeShape,
        val properties: List<PropertyDecl>,
        val howToConstruct: HowToConstruct,
        val hasJsonDecoration: Boolean,
        val toJsonMethod: MethodPresence,
        val fromJsonMethod: MethodPresence,
        /** null if not sealed */
        val sealedSubTypes: List<SealedSubType>?,
    ) : Positioned, Structured {
        val name: ResolvedName get() = definition.name
        val typeFormals: List<TypeFormal> = definition.formals

        val isSealed get() = sealedSubTypes != null
        val abstractness get() = definition.abstractness

        override fun destructure(structureSink: StructureSink) = structureSink.obj {
            key("name") { value(name) }
            TODO("$isSealed $abstractness")
        }
    }

    data class SealedSubType(
        val subTypeName: ResolvedName,
        /**
         * When `(class|interface) SubType<A> extends SuperType<A, Foo<A>>`, the type parameter list,
         * `<A, Foo<A>>`.
         *
         * This allows us to go from subsidiary adapters for *SuperType*'s type formals to subsidiary
         * adapters for *SubType*.
         */
        val typeParameters: List<StaticType>,
    )
}
