package lang.temper.kcodegen.outgrammar

import lang.temper.log.Position
import lang.temper.log.Positioned

internal sealed class CompleteSuperType

/**
 * Similar information to a [NodeType] but after all the super-types are fully defined and known
 * so we can aggregate information inherited from super types.
 */
internal data class CompleteNodeType(
    val pos: Position,
    val name: Id,
    val original: NodeType,
    val typeKind: TypeKind,
    /** Non-transitive. */
    val superTypes: List<CompleteSuperType>,
    val isConcrete: Boolean,
    val properties: Map<Id, CompleteProperty>,
    val allRequirements: List<Requirement>,
    val extraBodyContent: KotlinCode?,
    val operatorDefinition: InheritedOrDeclared<KotlinCode>?,
    val renderTo: InheritedOrDeclared<KotlinCode>?,
    val formatStringDigest: InheritedOrDeclared<FormatStringDigest>?,
    val constructorArgumentOrder: List<Id>?,
    val docComment: DocComment?,
) : CompleteSuperType() {
    val nameWithKind
        get() = when (typeKind) {
            TypeKind.Ast -> name.text
            TypeKind.Data -> "data ${name.text}"
        }
}

internal data class CompleteKotlinSuperType(
    val kotlinType: KotlinCode,
) : CompleteSuperType()

internal data class CompleteProperty(
    override val pos: Position,
    val name: Id,
    val typeAndCount: PropertyTypeAndCount,
    val getter: KotlinCode?,
    val defaultExpression: KotlinCode?,
    val declarer: Id,
    val needsDeclaration: Boolean,
    val docComment: DocComment?,
) : Positioned

internal sealed class InheritedOrDeclared<T>(
    val content: T,
)
internal class Inherited<T>(content: T) : InheritedOrDeclared<T>(content)
internal class Declared<T>(content: T) : InheritedOrDeclared<T>(content)
