package lang.temper.type2

import lang.temper.format.OutToks
import lang.temper.format.TokenSerializable
import lang.temper.format.TokenSink
import lang.temper.log.Position
import lang.temper.log.Positioned
import lang.temper.name.ParsedName
import lang.temper.name.name
import lang.temper.type.WellKnownTypes
import lang.temper.value.ReifiedType
import lang.temper.value.TEdge
import lang.temper.value.Value
import lang.temper.value.ValueLeaf

sealed interface InputBound : Positioned, TokenSerializable {
    fun solvedType(typeSolver: TypeSolver): PositionedType

    data class Pretyped(
        val type: PositionedType,
    ) : InputBound, Positioned by type {
        override fun renderTo(tokenSink: TokenSink) {
            type.renderTo(tokenSink)
        }

        override fun solvedType(typeSolver: TypeSolver) = type
    }

    data class UntypedCallInput(
        override val pos: Position,
        val passVar: TypeVar,
    ) : InputBound {
        override fun renderTo(tokenSink: TokenSink) {
            passVar.renderTo(tokenSink)
        }

        override fun solvedType(typeSolver: TypeSolver) =
            MkType2.from(
                when (val solution = typeSolver[passVar]) {
                    is Type2 -> solution
                    is Unsolvable? -> WellKnownTypes.invalidType2
                },
            ).position(pos).get() as PositionedType
    }

    /**
     * Reifies a reified type value that bounds a type variable as `List` in `expr as List` or `expr is List`.
     * There, `List` is incomplete because it needs some type parameter.  For example, if *expr* resolved to
     * a `Listed<String>`, then the Typer should conclude that the type binding for the function calls include
     * the type actual *List<String>*, and to simplify translation, the IR should change to include the completed,
     * reified type in the IR.
     */
    data class IncompleteReification(
        override val pos: Position,
        val reifiedType: ReifiedType,
        val typeArgumentIndex: Int,
        /** Pre-allocated variable for the full type */
        val typeVar: TypeVar,
        /** Edge to replace with a complete reification. */
        val reificationEdge: TEdge?,
        /** Which value actual is described by the type. */
        val describedValueArgumentIndex: Int?,
    ) : InputBound {
        override fun renderTo(tokenSink: TokenSink) {
            reifiedType.renderTo(tokenSink)
            tokenSink.word("reifies")
            tokenSink.emit(OutToks.leftAngle)
            tokenSink.name(ParsedName("T$typeArgumentIndex"), inOperatorPosition = false)
            tokenSink.emit(OutToks.rightAngle)
        }

        override fun solvedType(typeSolver: TypeSolver) =
            MkType2(WellKnownTypes.typeTypeDefinition).position(pos).get()
                as PositionedType
    }

    data class ValueInput(
        val valueLeaf: ValueLeaf,
        /** The type variable that should resolve to [value]'s type in context */
        val typeVar: TypeVar,
    ) : InputBound, Positioned by valueLeaf {
        val value: Value<*> get() = valueLeaf.content
        var valueSolvedType: Type2? = null

        override fun renderTo(tokenSink: TokenSink) {
            value.renderTo(tokenSink)
        }

        override fun solvedType(typeSolver: TypeSolver) = MkType2.from(
            when (val solution = typeSolver[typeVar]) {
                is Type2 -> solution
                is Unsolvable? -> WellKnownTypes.invalidType2
            },
        ).position(pos).get() as PositionedType
    }

    data class Typeless(
        override val pos: Position,
    ) : InputBound {
        override fun renderTo(tokenSink: TokenSink) {
            tokenSink.word("typeless")
        }

        override fun solvedType(typeSolver: TypeSolver): PositionedType =
            MkType2.Companion(WellKnownTypes.invalidTypeDefinition).position(pos).get() as PositionedType
    }
}
