package lang.temper.frontend.generate

import lang.temper.common.Log
import lang.temper.frontend.Module
import lang.temper.log.LogSink
import lang.temper.log.MessageTemplate
import lang.temper.log.unknownPos
import lang.temper.name.Symbol
import lang.temper.type.Abstractness
import lang.temper.type.MethodKind
import lang.temper.type.MethodShape
import lang.temper.type.TypeShape
import lang.temper.type.Visibility
import lang.temper.type.WellKnownTypes
import lang.temper.type2.DefinedNonNullType
import lang.temper.type2.MkType2
import lang.temper.type2.Signature2
import lang.temper.type2.SuperTypeTree2
import lang.temper.type2.mapType
import lang.temper.value.connectedSymbol
import lang.temper.value.parameterNameSymbols

internal class TypeDeclChecker(val module: Module, val logSink: LogSink) {
    private val abstractMethodCache = mutableMapOf<TypeShape, List<MethodDescriptor>>()

    fun checkDeclaredTypeShapes() {
        for (typeShape in module.declaredTypeShapes) {
            when (typeShape.abstractness) {
                Abstractness.Abstract -> {}
                Abstractness.Concrete -> checkAllMethodsOverridden(typeShape)
            }
        }
    }

    private fun checkAllMethodsOverridden(typeShape: TypeShape) {
        val superTypeShapes = mutableSetOf<TypeShape>()
        fun walk(ts: TypeShape) {
            if (ts !in superTypeShapes) {
                superTypeShapes.add(ts)
                for (x in ts.superTypes) {
                    walk(x.definition as TypeShape)
                }
            }
        }
        walk(typeShape)

        val isProcessingImplicits = module.isEffectivelyImplicits
        val isStd = module.isEffectivelyStd
        val allAbstractMethodDescriptors = mutableListOf<MethodDescriptor>()
        for (strictSuperTypeShape in superTypeShapes) {
            val abstractMethods = abstractMethodCache.getOrPut(strictSuperTypeShape) {
                strictSuperTypeShape.methods.mapNotNull { m ->
                    if (isProcessingImplicits && connectedSymbol in m.metadata) {
                        null // Some Implicits methods have no body because they must connect.
                    } else if (isStd && m.visibility == Visibility.Private && connectedSymbol in m.metadata) {
                        null // std has some required connections too which are sneakily hidden away
                    } else if (m.methodKind != MethodKind.Constructor && m.isPureVirtual) {
                        MethodDescriptor(m.symbol, m.methodKind, m)
                    } else {
                        null
                    }
                }
            }
            allAbstractMethodDescriptors.addAll(abstractMethods)
        }

        val needed = allAbstractMethodDescriptors.filter { abstractMember ->
            val word = abstractMember.word
            val kind = abstractMember.methodKind
            superTypeShapes.none { superTypeShape ->
                superTypeShape.methods.any {
                    it.methodKind == kind && it.symbol == word && !it.isPureVirtual
                }
            }
        }
        for (descriptor in needed) {
            val word = descriptor.word
            val example = descriptor.example
            val kind = descriptor.methodKind
            val description = when (kind) {
                MethodKind.Normal -> word.text
                MethodKind.Getter -> "get ${word.text}"
                MethodKind.Setter -> "set ${word.text}"
                MethodKind.Constructor -> "constructor"
            }
            val sigInContext = run {
                val type = MkType2(typeShape).actuals(
                    typeShape.formals.map { MkType2(it).get() },
                ).get() as DefinedNonNullType
                val superTypeInContext = SuperTypeTree2.of(type)[example.enclosingType].firstOrNull()
                var sig = example.descriptor
                    ?: Signature2(WellKnownTypes.voidType2, false, listOf())
                if (sig.hasThisFormal) { sig = sig.copy(requiredInputTypes = sig.requiredInputTypes.drop(1)) }
                if (superTypeInContext != null) {
                    val bindings = (example.enclosingType.formals zip superTypeInContext.bindings).associate { it }
                    sig = sig.mapType(bindings)
                }
                sig
            }

            val skeletonCode = buildString {
                append("public $description(")
                val parameterNameSymbols = example.parameterNameSymbols?.let {
                    it.requiredSymbols + it.optionalSymbols
                }
                for ((i, vf) in sigInContext.allValueFormals.withIndex()) {
                    val name = parameterNameSymbols?.getOrNull(i)?.text ?: "_"
                    if (i != 0) { append(", ") }
                    append("$name: ${vf.type}")
                }
                append("): ${sigInContext.returnType2}")
            }
            logSink.log(
                Log.Error,
                MessageTemplate.MissingMethodDefinition,
                typeShape.stayLeaf?.pos ?: unknownPos,
                listOf(
                    typeShape.name,
                    description,
                    example.enclosingType.name,
                    skeletonCode,
                ),
            )
        }
    }
}

private class MethodDescriptor(
    val word: Symbol,
    val methodKind: MethodKind,
    val example: MethodShape,
) {
    override fun equals(other: Any?): Boolean =
        other is MethodDescriptor && word == other.word && methodKind == other.methodKind
    // example is non-normative

    override fun hashCode(): Int = word.hashCode() + 31 * methodKind.hashCode()

    override fun toString(): String = "MethodDescriptor($word, $methodKind)"
}
