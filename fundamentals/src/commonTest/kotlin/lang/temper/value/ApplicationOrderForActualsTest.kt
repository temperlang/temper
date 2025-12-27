package lang.temper.value

import lang.temper.common.Either
import lang.temper.common.allIndexed
import lang.temper.name.Symbol
import lang.temper.type.MkType
import lang.temper.type.StaticType
import lang.temper.type.WellKnownTypes
import lang.temper.type2.IValueFormal
import lang.temper.type2.Type2
import lang.temper.type2.ValueFormalKind
import lang.temper.type2.hackMapNewStyleToOld
import lang.temper.type2.hackMapOldStyleToNew
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.fail

class ApplicationOrderForActualsTest {
    @Test
    fun empty() = assertApplicationOrder("", "", "")

    @Test
    fun one() = assertApplicationOrder("_", "a", "0")

    @Test
    fun oneFn() = assertApplicationOrder("_", "a:Fn", "0")

    @Test
    fun oneFnFn() = assertApplicationOrder("fn", "a:Fn", "0")

    @Test
    fun two() = assertApplicationOrder("_, _", "a, b", "0, 1")

    @Test
    fun tooMany() = assertApplicationOrder(
        "_, _, _",
        "a, b",
        "!ArgumentListSizeMismatch(nPositionalActuals=3, nFormals=2)",
    )

    @Test
    fun tooFew() = assertApplicationOrder(
        "_, _",
        "a, b, c",
        "!ArgumentListSizeMismatch(nPositionalActuals=2, nFormals=3)",
    )

    @Test
    fun optionalGiven() = assertApplicationOrder(
        "_, _, _",
        "a, b, c",
        "0, 1, 2",
    )

    @Test
    fun optionalNotGiven() = assertApplicationOrder(
        "_, _",
        "a, b, c=",
        "0, 1, null",
    )

    @Test
    fun noRest() = assertApplicationOrder(
        "_, _",
        "a, b=, ...",
        "0, 1",
    )

    @Test
    fun oneRest() = assertApplicationOrder(
        "_, _, _",
        "a, b=, ...",
        "0, 1, 2",
    )

    @Test
    fun someRest() = assertApplicationOrder(
        "_, _, _, _",
        "a, b=, ...",
        "0, 1, 2, 3",
    )

    @Test
    fun fnRequired0() = assertApplicationOrder(
        "_, _, fn",
        "a:Fn, b, c",
        "2, 0, 1",
    )

    @Test
    fun fnRequired1() = assertApplicationOrder(
        "_, _, fn",
        "a, b:Fn, c",
        "0, 2, 1",
    )

    @Test
    fun fnRequired2() = assertApplicationOrder(
        "_, _, fn",
        "a, b, c:Fn",
        "0, 1, 2",
    )

    @Test
    fun fnOpt0() = assertApplicationOrder(
        "_, fn",
        "a=:Fn, b=, c=",
        "1, 0, null",
    )

    @Test
    fun fnOpt1() = assertApplicationOrder(
        "_, fn",
        "a, b=:Fn, c=",
        "0, 1, null",
    )

    @Test
    fun fnOpt2() = assertApplicationOrder(
        "_, fn",
        "a, b=, c=:Fn",
        "0, null, 1",
    )

    @Test
    fun fnMixed3() = assertApplicationOrder(
        "_, _, fn",
        "a, b:Fn, c, x=, y=:Fn, z=",
        "0, 2, 1, null, null, null",
    )

    @Test
    fun fnMixed4() = assertApplicationOrder(
        "_, _, _, fn",
        "a, b:Fn, c, x=, y=:Fn, z=",
        "0, 1, 2, null, 3, null",
    )

    @Test
    fun fnMixed5() = assertApplicationOrder(
        "_, _, _, _, fn",
        "a, b:Fn, c, x=, y=:Fn, z=",
        "0, 1, 2, 3, 4, null",
    )

    @Test
    fun fnMixed6() = assertApplicationOrder(
        "_, _, _, _, _, fn",
        "a, b:Fn, c, x=, y=:Fn, z=",
        "0, 1, 2, 3, 5, 4",
    )

    @Test
    fun fnBeforeResty() = assertApplicationOrder(
        "_, _, _, fn",
        "a, b:Fn, ...",
        "0, 3, 1, 2",
    )

    @Test
    fun fnNotResty() = assertApplicationOrder(
        "_, fn",
        "a, b:Fn, ...",
        "0, 1",
    )

    @Test
    fun fnResty() = assertApplicationOrder(
        "_, fn",
        "a, ...",
        "0, 1",
    )

    @Test
    fun namedInOrder() = assertApplicationOrder(
        "a=, b=",
        "a, b",
        "0, 1",
    )

    @Test
    fun namedOutOfOrder() = assertApplicationOrder(
        "b=, a=",
        "a, b",
        "1, 0",
    )

    @Test
    fun namedInOrderOneOptional() = assertApplicationOrder(
        "a=, b=",
        "a, b=",
        "0, 1",
    )

    @Test
    fun namedOutOfOrderOneOptional() = assertApplicationOrder(
        "b=, a=",
        "a, b=",
        "1, 0",
    )

    @Test
    fun someNamedSomeNot() = assertApplicationOrder(
        "_, _, a=, _",
        "a, b=, ...",
        "2, 0, 1, 3",
    )

    private fun assertApplicationOrder(
        /**
         * Split on comma.  `foo=` means the actual has a name.  `fn` means the actual is a lambda.
         * `_` indicates an actual that has neither symbol nor is a lambda.
         */
        commaSeparatedActuals: String,
        /**
         * Split on comma.  `foo` is a formal name.  `foo=` means the formal is optional.
         * `:Fn` at the end means the formal is function like.
         *
         * ", ..." at the end means there's a rest parameter.
         */
        commaSeparatedFormals: String,
        /**
         * "!text" is an expected error.
         *
         * "0, 1, 2" is integer indices split on comma.  `null` is allowed for unfilled optional params.
         */
        want: String,
    ) {
        val valueActuals = commaSeparatedActuals.split0(",")
            .map { it.trim() }
            .map { part ->
                when {
                    part == "fn" -> Either.Right(TFunction)
                    part.endsWith("=") ->
                        Either.Left(Symbol(part.dropLast(1)))
                    part == "_" -> null
                    else -> fail(part)
                }
            }

        var hasRest = false
        val valueFormals = buildList {
            val parts = commaSeparatedFormals.split0(",")
                .map { it.trim() }
            for ((i, part) in parts.withIndex()) {
                if (i == parts.lastIndex && part == "...") {
                    hasRest = true
                    continue
                }

                var s = part

                var type: Type2? = null
                if (s.endsWith(":Fn")) {
                    type = hackMapOldStyleToNew(
                        MkType.fn(listOf(), listOf(), null, WellKnownTypes.voidType),
                    )
                    s = s.dropLast(3).trim()
                }

                var kind = ValueFormalKind.Required
                if (s.endsWith("=")) {
                    s = s.dropLast(1).trim()
                    kind = ValueFormalKind.Optional
                }

                var symbol: Symbol? = null
                if (s.isNotEmpty()) {
                    symbol = Symbol(s)
                }

                add(ValueFormalImpl(symbol, type, kind = kind))
            }
        }

        for (useFastPath in listOf(true, false)) {
            val result = if (useFastPath) {
                applicationOrderForActuals(
                    valueActuals = valueActuals,
                    valueFormals = valueFormals,
                    hasRest = hasRest,
                )
            } else {
                applicationOrderForActualsSlow(
                    valueActuals = valueActuals,
                    valueFormals = valueFormals,
                    hasRest = hasRest,
                )
            }

            if (want.startsWith("!")) {
                assertIs<Either.Right<ResolutionProblem>>(result)
                assertEquals(want.drop(1), result.item.toString())
            } else {
                val message = "useFastPath=$useFastPath"
                assertIs<Either.Left<List<Int?>>>(result, message = "$message\n\t$result")
                val resultList = result.item
                assertEquals(want, resultList.joinToString(", "), message = message)
                assertEquals(
                    resultList is IdentityActualOrder,
                    resultList.allIndexed { i, j -> i == j },
                    message = message,
                )

                // Check that the reordering is idempotent
                val reorderedActuals = buildList {
                    for (r in resultList) {
                        if (r != null) {
                            add(valueActuals[r])
                        } else {
                            add(null) // A placeholder for the default expression
                        }
                    }
                }
                val idempotentResult = if (useFastPath) {
                    applicationOrderForActuals(reorderedActuals, valueFormals, hasRest = hasRest)
                } else {
                    applicationOrderForActualsSlow(reorderedActuals, valueFormals, hasRest = hasRest)
                }
                assertIs<Either.Left<List<Int?>>>(idempotentResult, "idempotent check $message")
                val idempotentOrder = idempotentResult.item
                assertIs<IdentityActualOrder>(idempotentOrder, "idempotent check $message")
                assertEquals(
                    reorderedActuals.size, idempotentOrder.size,
                    message = "idempotent check $message",
                )
            }
        }
    }
}

private fun String.split0(sep: String) =
    if (isEmpty()) { listOf() } else { split(sep) }

private data class ValueFormalImpl(
    override val symbol: Symbol?,
    override val type: Type2?,
    override val kind: ValueFormalKind,
) : IValueFormal {
    override val staticType: StaticType?
        get() = type?.let { hackMapNewStyleToOld(it) }

    override val reifiedType: BaseReifiedType?
        get() = type?.let { ReifiedType(it) }

    override fun toString(): String = "ValueFormalImpl(${symbol ?: ""})"
}
