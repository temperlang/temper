package lang.temper.be.elixir

import lang.temper.be.tmpl.TmpL
import lang.temper.log.Position
import lang.temper.name.OutName
import lang.temper.type.TypeFormal
import lang.temper.type.TypeShape
import lang.temper.type.WellKnownTypes

/**
 * A Temper type as the Elixir type its values have on the BEAM, for `@spec`
 * and `@type`. Each answer follows the representation the translator and
 * temper-core give values of that type, so Dialyzer can check one against
 * the other:
 *
 * | Temper | Elixir |
 * |--------|--------|
 * | `Int`, `Int64`, a string index | `integer()` |
 * | `Float64` | `TemperCore.Float.t()`: a float, `:infinity`, `:neg_infinity` or `:nan` |
 * | `String` | `String.t()` |
 * | `Void`, `Null` | `nil` |
 * | `List<T>` | `TemperCore.Vec.t(t)` |
 * | `Map<K, V>`, `Pair<K, V>` | `TemperCore.Map.t(k, v)`, `TemperCore.Pair.t(k, v)` |
 * | a builder, a generator, a promise, a deque | `TemperCore.Ref.t()`: a heap object |
 * | a translated class or interface | its module's `t()` |
 * | a type parameter, `AnyValue`, an intersection | `term()` |
 * | `Never`, `Bubble` | `no_return()` |
 *
 * A builtin type with no entry here fails the build and names itself.
 */
internal class ElixirTypespecs(
    /** The module of a translated class or interface, this library's or another's; null for a builtin. */
    private val classModule: (TypeShape) -> List<String>?,
) {
    fun of(type: TmpL.AType): Elixir.TypeExpr =
        of(type.privOtOrNull ?: TODO("type with no tree form to spec: $type"))

    fun of(type: TmpL.Type): Elixir.TypeExpr {
        val pos = type.pos
        return when (type) {
            is TmpL.GarbageType, is TmpL.TopType, is TmpL.TypeIntersection -> builtin(pos, "term")
            is TmpL.NeverType, is TmpL.BubbleType -> builtin(pos, "no_return")
            is TmpL.FunctionType -> Elixir.FunType(
                pos,
                params = type.valueFormals.formals.map { formal ->
                    of(formal.type).let { if (formal.isOptional) orNil(pos, it) else it }
                },
                result = of(type.returnType),
            )
            is TmpL.TypeUnion -> union(pos, type.types.map { of(it) })
            is TmpL.NominalType -> nominal(type)
        }
    }

    /** A parameter a caller may leave out arrives as `nil`. */
    fun orNil(pos: Position, type: Elixir.TypeExpr): Elixir.TypeExpr = union(pos, listOf(type, Elixir.NilLit(pos)))

    /** `name(args)`: one of Elixir's own types, or a module's own `t()`. */
    fun builtin(pos: Position, name: String, args: List<Elixir.TypeExpr> = listOf()): Elixir.TypeExpr =
        Elixir.LocalType(pos, Elixir.Id(pos, OutName(name, null)), args)

    /** `Module.name(args)` */
    fun remote(pos: Position, module: List<String>, name: String, args: List<Elixir.TypeExpr> = listOf()) =
        Elixir.RemoteType(pos, elixirModule(pos, module), Elixir.Id(pos, OutName(name, null)), args)

    /** `%TemperCore.Ref{class: Temper.Lib.C, id: reference()}`: a heap object, or with `Actor` an actor, of [module]. */
    fun reference(pos: Position, kind: String, module: List<String>): Elixir.TypeExpr = Elixir.StructType(
        pos,
        name = elixirModule(pos, "TemperCore", kind),
        fields = listOf(
            Elixir.TypeField(pos, Elixir.Id(pos, OutName("class", null)), elixirModule(pos, module)),
            Elixir.TypeField(pos, Elixir.Id(pos, OutName("id", null)), builtin(pos, "reference")),
        ),
    )

    /**
     * A union, flattened, with each member once. `no_return()` adds nothing
     * to a union that has another member: a function that may bubble returns
     * its pass type when it returns at all.
     */
    fun union(pos: Position, types: List<Elixir.TypeExpr>): Elixir.TypeExpr {
        val flat = types.flatMap { if (it is Elixir.UnionType) it.types else listOf(it) }
        val returning = flat.filterNot { key(it) == NO_RETURN }
        val members = returning.ifEmpty { flat.take(1) }.distinctBy(::key)
        return members.singleOrNull()?.deepCopy() ?: Elixir.UnionType(pos, members.map { it.deepCopy() })
    }

    private fun nominal(type: TmpL.NominalType): Elixir.TypeExpr {
        val pos = type.pos
        val definition = when (val name = type.typeName) {
            is TmpL.TemperTypeName -> name.typeDefinition
            is TmpL.ConnectedToTypeName -> TODO("connected type with no Elixir type: ${name.name}")
        }
        if (definition is TypeFormal) return builtin(pos, "term")
        val shape = definition as TypeShape
        val args = type.params.map { of(it) }
        return wellKnown(pos, shape, args)
            ?: classModule(shape)?.let { remote(pos, it, "t") }
            ?: TODO("no Elixir type for ${shape.name}")
    }

    @Suppress("CyclomaticComplexMethod") // one arm per builtin type
    private fun wellKnown(pos: Position, shape: TypeShape, args: List<Elixir.TypeExpr>): Elixir.TypeExpr? {
        fun arg(i: Int) = args.getOrNull(i)?.deepCopy() ?: builtin(pos, "term")
        fun core(module: String, vararg typeArgs: Elixir.TypeExpr) =
            remote(pos, listOf("TemperCore", module), "t", typeArgs.toList())
        val ref = { core("Ref") }
        return when (shape) {
            WellKnownTypes.intTypeDefinition,
            WellKnownTypes.int64TypeDefinition,
            WellKnownTypes.stringIndexTypeDefinition,
            WellKnownTypes.stringIndexOptionTypeDefinition,
            WellKnownTypes.noStringIndexTypeDefinition,
            -> builtin(pos, "integer")
            WellKnownTypes.float64TypeDefinition -> core("Float")
            WellKnownTypes.booleanTypeDefinition -> builtin(pos, "boolean")
            WellKnownTypes.stringTypeDefinition -> remote(pos, listOf("String"), "t")
            WellKnownTypes.voidTypeDefinition,
            WellKnownTypes.nullTypeDefinition,
            // `console` is nil: console.log drops its receiver
            WellKnownTypes.consoleTypeDefinition,
            WellKnownTypes.globalConsoleTypeDefinition,
            -> Elixir.NilLit(pos)
            WellKnownTypes.emptyTypeDefinition -> Elixir.Atom(pos, "empty")
            WellKnownTypes.symbolTypeDefinition, WellKnownTypes.typeTypeDefinition -> builtin(pos, "atom")
            WellKnownTypes.anyValueTypeDefinition,
            WellKnownTypes.equatableTypeDefinition,
            WellKnownTypes.mapKeyTypeDefinition,
            WellKnownTypes.closureRecordTypeDefinition,
            WellKnownTypes.problemTypeDefinition,
            WellKnownTypes.stageRangeTypeDefinition,
            WellKnownTypes.invalidTypeDefinition,
            -> builtin(pos, "term")
            WellKnownTypes.neverTypeDefinition, WellKnownTypes.bubbleTypeDefinition -> builtin(pos, NO_RETURN_NAME)
            WellKnownTypes.functionTypeDefinition -> builtin(pos, "fun")
            WellKnownTypes.resultTypeDefinition -> arg(0)
            WellKnownTypes.listTypeDefinition -> core("Vec", arg(0))
            WellKnownTypes.listedTypeDefinition -> union(pos, listOf(core("Vec", arg(0)), ref()))
            WellKnownTypes.mapTypeDefinition -> core("Map", arg(0), arg(1))
            WellKnownTypes.mappedTypeDefinition -> union(pos, listOf(core("Map", arg(0), arg(1)), ref()))
            WellKnownTypes.pairTypeDefinition -> core("Pair", arg(0), arg(1))
            WellKnownTypes.listBuilderTypeDefinition,
            WellKnownTypes.mapBuilderTypeDefinition,
            WellKnownTypes.stringBuilderTypeDefinition,
            WellKnownTypes.dequeTypeDefinition,
            WellKnownTypes.denseBitVectorTypeDefinition,
            WellKnownTypes.generatorTypeDefinition,
            WellKnownTypes.safeGeneratorTypeDefinition,
            WellKnownTypes.promiseTypeDefinition,
            WellKnownTypes.promiseBuilderTypeDefinition,
            -> ref()
            WellKnownTypes.valueResultTypeDefinition -> valueResult(pos, arg(0))
            WellKnownTypes.doneResultTypeDefinition -> Elixir.Atom(pos, "done")
            WellKnownTypes.generatorResultTypeDefinition ->
                union(pos, listOf(valueResult(pos, arg(0)), Elixir.Atom(pos, "done")))
            else -> null
        }
    }

    /** `{:value, v}`, what a generator step yields. */
    private fun valueResult(pos: Position, value: Elixir.TypeExpr) =
        Elixir.TupleType(pos, listOf(Elixir.Atom(pos, "value"), value))

    /** A type's text, enough to tell two apart: the members of a union are compared by it. */
    private fun key(type: Elixir.TypeExpr): String = when (type) {
        is Elixir.Atom -> ":${type.text}"
        is Elixir.NilLit -> "nil"
        is Elixir.Id -> type.outName.outputNameText
        is Elixir.LocalType -> type.id.outName.outputNameText + type.args.joinToString(",", "(", ")") { key(it) }
        is Elixir.RemoteType ->
            type.module.segments.joinToString(".") { it.outName.outputNameText } + "." +
                type.id.outName.outputNameText + type.args.joinToString(",", "(", ")") { key(it) }
        is Elixir.UnionType -> type.types.joinToString("|") { key(it) }
        is Elixir.StructType -> "%" + type.name.segments.joinToString(".") { it.outName.outputNameText } +
            type.fields.joinToString(",", "{", "}") { it.key.outName.outputNameText + ":" + key(it.type) }
        is Elixir.FunType -> type.params.joinToString(",", "(", "->") { key(it) } + key(type.result) + ")"
        is Elixir.ListType -> "[" + key(type.elem) + "]"
        is Elixir.TupleType -> type.items.joinToString(",", "{", "}") { key(it) }
        is Elixir.ModuleName -> type.segments.joinToString(".") { it.outName.outputNameText }
    }

    private companion object {
        const val NO_RETURN_NAME = "no_return"
        const val NO_RETURN = "$NO_RETURN_NAME()"
    }
}
