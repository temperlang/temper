package lang.temper.frontend.core

import lang.temper.common.ignore
import lang.temper.common.soleElementOrNull
import lang.temper.interp.EmptyEnvironment
import lang.temper.lexer.Genre
import lang.temper.name.BuiltinName
import lang.temper.type.MethodShape
import lang.temper.type.WellKnownTypes
import lang.temper.type.promoteSimpleValue
import lang.temper.value.TClass
import lang.temper.value.TInt
import lang.temper.value.Value
import lang.temper.value.isCore
import lang.temper.value.typeDefinitionAtLeafOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ImplicitsModuleTest {
    @Test
    fun implicitsModuleReturns() {
        CoreModule.module // Throws if not available
    }

    @Test
    fun isCore() {
        assertTrue(CoreModule.module.namingContext.isCore)
    }

    @Test
    fun implicitsExportsAnyValue() {
        val export = CoreModule.module.exports?.filter { it.name.baseName.nameText == "AnyValue" }
            ?.soleElementOrNull
        assertEquals(
            listOf(
                WellKnownTypes.anyValueTypeDefinition,
                WellKnownTypes.anyValueTypeDefinition,
            ),
            listOf(
                export?.valueFromStaging?.typeDefinitionAtLeafOrNull,
                export?.valueFromRun?.typeDefinitionAtLeafOrNull,
            ),
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
        val unavailableInBuiltin = CoreModule.module.exports?.filter {
            val equivalentBuiltinName = BuiltinName(it.name.baseName.nameText)
            builtinEnv.declarationMetadata(equivalentBuiltinName) == null
        }
        assertEquals(emptyList(), unavailableInBuiltin)
    }

    // Once ImplicitsModule is loaded, we can check that some things are true about
    // well-known types.
    @Test
    fun overrideRecognizedBetweenSafeGeneratorAndGenerator() {
        ignore(CoreModule.module)

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

    @Test
    fun testPromoteSimpleValue() {
        CoreModule.module
        // After define the backing classes, we can promote simple values to class instances.
        // This allows implementing methods in core.temper.
        val simpleValue = Value(1234, TInt)
        val promotedValue = promoteSimpleValue(simpleValue)
        assertEquals(TClass(WellKnownTypes.intTypeDefinition), promotedValue?.typeTag)
        assertEquals(
            "{content: 1234}: Int32__0",
            promotedValue?.toString()?.replace(Regex("__[0-9]+$"), "__0"),
        )
    }
}
