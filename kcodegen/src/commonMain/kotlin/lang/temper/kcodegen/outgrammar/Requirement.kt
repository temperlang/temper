package lang.temper.kcodegen.outgrammar

internal sealed class Requirement
internal data class CodeRequirement(val kotlinCode: KotlinCode) : Requirement()
internal data class PropertyRequirement(val propertyName: Id) : Requirement()
