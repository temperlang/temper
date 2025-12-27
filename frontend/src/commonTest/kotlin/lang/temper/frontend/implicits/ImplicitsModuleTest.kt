package lang.temper.frontend.implicits

import lang.temper.common.ignore
import lang.temper.interp.EmptyEnvironment
import lang.temper.lexer.Genre
import lang.temper.name.BuiltinName
import lang.temper.type.ANY_VALUE_TYPE_NAME_TEXT
import lang.temper.type.MethodShape
import lang.temper.type.WellKnownTypes
import lang.temper.value.isImplicits
import lang.temper.value.typeDefinitionAtLeafOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ImplicitsModuleTest {
    @Test
    fun implicitsModuleReturns() {
        ImplicitsModule.module // Throws if not available
    }

    @Test
    fun isImplicits() {
        assertTrue(ImplicitsModule.module.namingContext.isImplicits)
    }

    @Test
    fun implicitsExportsAnyValue() {
        assertEquals(
            true,
            ImplicitsModule.module.exports?.any {
                it.name.baseName.nameText == ANY_VALUE_TYPE_NAME_TEXT &&
                    it.value?.typeDefinitionAtLeafOrNull == WellKnownTypes.anyValueTypeDefinition
            },
        )
    }

    @Test
    fun allImplicitsExportsReflectedInBuiltinEnvironment() {
        // For each name-text x, exported from ImplicitsModule, if a binding for
        // BuiltinName(x) not available via BuiltinEnvironment,
        // then users can't use `builtins.x` to refer to that implicit export.
        //
        // Whether some global is implemented in Implicits or implemented in frontend code is an
        // implementation detail that we ought not foist on users.
        val builtinEnv = builtinEnvironment(EmptyEnvironment, Genre.Library)
        val unavailableInBuiltin = ImplicitsModule.module.exports?.filter {
            val equivalentBuiltinName = BuiltinName(it.name.baseName.nameText)
            builtinEnv.declarationMetadata(equivalentBuiltinName) == null
        }
        assertEquals(emptyList(), unavailableInBuiltin)
    }

    // Once ImplicitsModule is loaded, we can check that some things are true about
    // well known types.
    @Test
    fun overrideRecognizedBetweenSafeGeneratorAndGenerator() {
        ignore(ImplicitsModule.module)

        fun isNextMethod(m: MethodShape) = m.symbol.text == "next"

        val safeGeneratorTypeDefinition = WellKnownTypes.safeGeneratorTypeDefinition
        val dotNext = safeGeneratorTypeDefinition.methods.first(::isNextMethod)
        val overriddenMembers = dotNext.overriddenMembers
        assertEquals(1, overriddenMembers?.size, "SafeGenerator.next overrides $overriddenMembers")
        val overriddenMember = overriddenMembers?.first()
        assertEquals(
            WellKnownTypes.generatorTypeDefinition.methods.first(::isNextMethod),
            overriddenMember?.superTypeMember,
        )
        val contextualizedType = overriddenMember?.superTypeMemberTypeInSubTypeContext
        // We translated the type from the sub-type so that we use the YIELD_TYPE_NAME from SafeGenerator,
        // but the Bubble type still shows up because that's declared on the super type.
        val yieldTypeName = "${WellKnownTypes.safeGeneratorTypeDefinition.typeParameters.first().name}"
        assertEquals(
            """(this : Generator<$yieldTypeName>) -> Result<GeneratorResult<$yieldTypeName>, Bubble>""",
            "$contextualizedType",
        )
    }
}
