package lang.temper.type

import kotlin.test.Test
import kotlin.test.assertEquals

class SuperTypeTreeTest {
    @Test
    fun bindingsPerSuperType() = TypeTestHarness(
        """
            |interface I<T>;
            |interface J<U>;
            |interface K<X> extends I<List<X>>, J<Null>;
            |class C extends K<String>;
        """.trimMargin(),
    ).run {
        fun nomType(typeStr: String) = type(typeStr) as NominalType

        val c = nomType("C")

        val superTypeTree = SuperTypeTree.of(c)

        val kString = nomType("K<String>")
        val jNull = nomType("J<Null>")
        val iListString = nomType("I<List<String>>")
        val anyValue = nomType("AnyValue")

        assertEquals(
            mapOf(
                c.definition to listOf(c),
                kString.definition to listOf(kString),
                jNull.definition to listOf(jNull),
                iListString.definition to listOf(iListString),
                anyValue.definition to listOf(anyValue),
            ),
            superTypeTree.byDefinition,
        )

        assertEquals(
            mapOf(
                c to listOf(anyValue, kString),
                kString to listOf(anyValue, iListString, jNull),
                iListString to listOf(anyValue),
                jNull to listOf(anyValue),
                anyValue to listOf(),
            ),
            superTypeTree.typeToDirectSupers,
        )

        assertEquals(c, superTypeTree.type)
    }

    @Test
    fun bindThroughComplexTypes() = TypeTestHarness(
        """
            |interface I<T>;
            |class OrNullI<N> extends I<N?>;
        """.trimMargin(),
    ).run {
        fun nomType(typeStr: String) = type(typeStr) as NominalType

        val orNullStr = nomType("OrNullI<String>")

        val superTypeTree = SuperTypeTree.of(orNullStr)

        val iStringOrNull = nomType("I<String?>")
        val anyValue = nomType("AnyValue")

        assertEquals(
            mapOf(
                orNullStr.definition to listOf(orNullStr),
                anyValue.definition to listOf(anyValue),
                iStringOrNull.definition to listOf(iStringOrNull),
            ),
            superTypeTree.byDefinition,
        )

        assertEquals(orNullStr, superTypeTree.type)
    }
}
