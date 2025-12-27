package lang.temper.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class LogicalAlgebraTest {
    @Test
    fun trueAndFalseStringify() {
        assertStringsEqual("true", LogicalAlgebra.valueTrue<Unit, Any>(Unit).toString())
        assertStringsEqual("false", LogicalAlgebra.valueFalse<Unit, Any>(Unit).toString())
    }

    @Test
    fun toStringOfAnd() {
        val conj = LogicalAlgebra.and(
            Unit,
            Term(Unit, "foo"),
            Term(Unit, "bar"),
            Term(Unit, "baz"),
        )
        assertStringsEqual(
            "foo & bar & baz",
            conj.toString(),
            conj.toStructureString(),
        )
    }

    @Test
    fun toStringOfOr() {
        assertStringsEqual(
            "foo | bar | baz",
            LogicalAlgebra.or(
                Unit,
                Term(Unit, "foo"),
                Term(Unit, "bar"),
                Term(Unit, "baz"),
            ).toString(),
        )
    }

    @Test
    fun toStringOfNot() {
        assertStringsEqual(
            "-foo",
            LogicalAlgebra.not(
                Term(Unit, "foo"),
            ).toString(),
        )
    }

    @Test
    fun toStringOfNotNot() {
        assertStringsEqual(
            "foo",
            LogicalAlgebra.not(
                LogicalAlgebra.not(
                    Term(Unit, "foo"),
                ),
            ).toString(),
        )
    }

    @Test
    fun toStringOfMinus() {
        assertStringsEqual(
            "foo - bar - baz",
            LogicalAlgebra.minus(
                Unit,
                Term(Unit, "foo"),
                Term(Unit, "bar"),
                Term(Unit, "baz"),
            ).toString(),
        )
    }

    @Test
    fun andTruthTable() {
        val f = LogicalAlgebra.valueFalse<Unit, Nothing>(Unit)
        val t = LogicalAlgebra.valueTrue<Unit, Nothing>(Unit)
        assertNotEquals(f, t)

        assertEquals(
            listOf(f, f, f, t),
            listOf(
                LogicalAlgebra.and(Unit, f, f),
                LogicalAlgebra.and(Unit, f, t),
                LogicalAlgebra.and(Unit, t, f),
                LogicalAlgebra.and(Unit, t, t),
            ),
        )
    }

    @Test
    fun orTruthTable() {
        val f = LogicalAlgebra.valueFalse<Unit, Nothing>(Unit)
        val t = LogicalAlgebra.valueTrue<Unit, Nothing>(Unit)

        assertEquals(
            listOf(f, t, t, t),
            listOf(
                LogicalAlgebra.or(Unit, f, f),
                LogicalAlgebra.or(Unit, f, t),
                LogicalAlgebra.or(Unit, t, f),
                LogicalAlgebra.or(Unit, t, t),
            ),
        )
    }
}
