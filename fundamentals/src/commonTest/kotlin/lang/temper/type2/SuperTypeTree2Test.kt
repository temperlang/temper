package lang.temper.type2

import lang.temper.type.WellKnownTypes
import lang.temper.type.withTypeTestHarness
import kotlin.test.Test
import kotlin.test.assertEquals

class SuperTypeTree2Test {
    @Test
    fun bindingsPerSuperType() = withTypeTestHarness(
        """
            |interface I<T>;
            |interface J<U>;
            |interface K<X> extends I<List<X>>, J<Boolean>;
            |class C extends K<String>;
        """.trimMargin(),
    ) {
        val c = type2("C")

        val superTypeTree = SuperTypeTree2.of(c)

        val kString = type2("K<String>")
        val jBoolean = type2("J<Boolean>")
        val iListString = type2("I<List<String>>")
        val anyValue = type2("AnyValue")

        assertEquals(
            mapOf(
                c.definition to listOf(c),
                kString.definition to listOf(kString),
                jBoolean.definition to listOf(jBoolean),
                iListString.definition to listOf(iListString),
                anyValue.definition to listOf(anyValue),
            ),
            superTypeTree.byDefinition,
        )

        assertEquals(
            mapOf(
                c to listOf(anyValue, kString),
                kString to listOf(anyValue, iListString, jBoolean),
                iListString to listOf(anyValue),
                jBoolean to listOf(anyValue),
                anyValue to listOf(),
            ),
            superTypeTree.typeToDirectSupers,
        )

        assertEquals(c, superTypeTree.type)
    }

    @Test
    fun bindThroughComplexTypes() = withTypeTestHarness(
        """
            |interface I<T>;
            |class OrNullI<N> extends I<N?>;
        """.trimMargin(),
    ) {
        val orNullStr = type2("OrNullI<String>")

        val superTypeTree = SuperTypeTree2.of(orNullStr)

        val iStringOrNull = type2("I<String?>")
        val anyValue = type2("AnyValue")

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

    @Test
    fun mapSuperTypeTree() = withTypeTestHarness {
        val map = type2("Map<Int, String>")
        val stt = SuperTypeTree2.of(map)
        assertEquals(
            listOf(type2("Mapped<Int, String>")),
            stt[WellKnownTypes.mappedTypeDefinition],
        )
    }
}
