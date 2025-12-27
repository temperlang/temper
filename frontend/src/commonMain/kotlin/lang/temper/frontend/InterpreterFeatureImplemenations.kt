package lang.temper.frontend

import lang.temper.frontend.disambiguate.FormalizeArrowArgsMacro
import lang.temper.frontend.implicits.PromoteSimpleFn
import lang.temper.value.InternalFeatureKey
import lang.temper.value.InternalFeatureKeys
import lang.temper.value.Value

val interpreterFeatureImplementations: Map<InternalFeatureKey, Value<*>> = listOf(
    InternalFeatureKeys.Arrow to Value(FormalizeArrowArgsMacro),
    InternalFeatureKeys.Class to Value(ClassDefinitionMacro),
    InternalFeatureKeys.Fn to Value(MultiStageFnMacro),
    InternalFeatureKeys.Interface to Value(InterfaceDefinitionMacro),
    InternalFeatureKeys.Let to Value(MultiStageLetMacro),
    InternalFeatureKeys.AdaptGeneratorFn to Value(AdaptGeneratorFn.bubblyInstance),
    InternalFeatureKeys.SafeAdaptGeneratorFn to Value(AdaptGeneratorFn.safeInstance),
    InternalFeatureKeys.GeneratorStepperFn to Value(GeneratorStepperFn),
    InternalFeatureKeys.MakeValueResult to Value(MakeValueResult),
    InternalFeatureKeys.PromoteSimpleValueToClassInstance to Value(PromoteSimpleFn),
).associate {
    it.first.featureKey to it.second
}
