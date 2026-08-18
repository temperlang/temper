package lang.temper.frontend.generate

import lang.temper.common.Log
import lang.temper.frontend.Module
import lang.temper.log.LogSink
import lang.temper.log.MessageTemplate
import lang.temper.log.unknownPos
import lang.temper.name.Symbol
import lang.temper.type.Abstractness
import lang.temper.type.DotMember
import lang.temper.type.MethodKind
import lang.temper.type.MethodShape
import lang.temper.type.TypeShape
import lang.temper.type.Visibility
import lang.temper.type.WellKnownTypes
import lang.temper.type2.DefinedNonNullType
import lang.temper.type2.MkType2
import lang.temper.type2.Nullity
import lang.temper.type2.Signature2
import lang.temper.type2.SuperTypeTree2
import lang.temper.type2.mapType
import lang.temper.type2.withNullity
import lang.temper.value.connectedSymbol
import lang.temper.value.parameterNameSymbols

internal class TypeDeclChecker(val module: Module, val logSink: LogSink) {
    private val abstractMethodCache = mutableMapOf<TypeShape, List<MethodDescriptor>>()

    fun checkDeclaredTypeShapes() {
        for (typeShape in module.declaredTypeShapes) {
            checkTypeShape(typeShape)
        }
    }

    fun checkTypeShape(typeShape: TypeShape) {
        val superTypeShapes = buildSet {
            fun walk(ts: TypeShape) {
                if (ts !in this) {
                    add(ts)
                    for (x in ts.superTypes) {
                        walk(x.definition as TypeShape)
                    }
                }
            }
            walk(typeShape)
        }

        when (typeShape.abstractness) {
            Abstractness.Abstract -> {}
            Abstractness.Concrete -> checkAllMethodsOverridden(typeShape, superTypeShapes)
        }
        checkOverridesCompatible(typeShape, superTypeShapes)
    }

    private fun checkAllMethodsOverridden(typeShape: TypeShape, superTypeShapes: Set<TypeShape>) {
        val isProcessingCore = module.isEffectivelyCore
        val isStd = module.isEffectivelyStd
        val allAbstractMethodDescriptors = mutableListOf<MethodDescriptor>()
        for (strictSuperTypeShape in superTypeShapes) {
            val abstractMethods = abstractMethodCache.getOrPut(strictSuperTypeShape) {
                strictSuperTypeShape.methods.mapNotNull { m ->
                    if (isProcessingCore && connectedSymbol in m.metadata) {
                        null // Some Core methods have no body because they must connect.
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
            val description = methodDescription(kind, word)
            val sigInContext = sigInContext(typeShape, example)
                ?: Signature2(WellKnownTypes.voidType2, false, listOf())
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

    private fun checkOverridesCompatible(typeShape: TypeShape, superTypeShapes: Set<TypeShape>) {
        if (WellKnownTypes.isWellKnown(typeShape)) {
            // TODO: cleanup implicits so it runs clean.  GeneratorResult.next and SafeGeneratorResult.next
            // are problematic because one Bubbles and one does not.
            return
        }

        for (method in typeShape.methods) {
            val sig = method.descriptor?.let { adjustOptionalToNullable(it) } ?: continue
            val methodKind = method.methodKind
            if (methodKind == MethodKind.Constructor) { continue }
            for (superTypeShape in superTypeShapes) {
                if (superTypeShape == typeShape) { continue }
                for (m in superTypeShape.membersMatching(DotMember(method.symbol))) {
                    if (m is MethodShape && m.visibility != Visibility.Private && m.methodKind == methodKind) {
                        if (method.visibility < m.visibility) {
                            logSink.log(
                                Log.Error,
                                MessageTemplate.IncompatibleVisibility,
                                method.stay?.pos ?: typeShape.pos,
                                listOf(
                                    typeShape.name,
                                    methodDescription(method.methodKind, method.symbol),
                                    method.visibility.keyword,
                                    m.enclosingType.name,
                                ),
                            )
                        }
                        val superSigInContext = sigInContext(typeShape, m)?.let { adjustOptionalToNullable(it) }
                        if (superSigInContext != null && superSigInContext != sig) {
                            logSink.log(
                                Log.Error,
                                MessageTemplate.IncompatibleSignature,
                                method.stay?.pos ?: typeShape.pos,
                                listOf(
                                    typeShape.name,
                                    methodDescription(method.methodKind, method.symbol),
                                    sigDescription(sig, method),
                                    sigDescription(superSigInContext, m),
                                    m.enclosingType.name,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }

    private fun sigInContext(subTypeShape: TypeShape, superTypeMethod: MethodShape): Signature2? {
        var sig = superTypeMethod.descriptor ?: return null
        val subType = MkType2(subTypeShape).actuals(
            subTypeShape.formals.map { MkType2(it).get() },
        ).get() as DefinedNonNullType
        val superTypeShape = superTypeMethod.enclosingType
        val superTypeInContext = SuperTypeTree2.of(subType)[superTypeShape].firstOrNull()
        sig = sig.withoutThisFormal()
        if (superTypeInContext != null) {
            val bindings = (superTypeShape.formals zip superTypeInContext.bindings).associate { it }
            sig = sig.mapType(bindings)
        }
        return sig
    }

    private fun adjustOptionalToNullable(sig: Signature2): Signature2 {
        var adjusted = sig
        adjusted = sig.withoutThisFormal()
        if (adjusted.optionalInputTypes.isNotEmpty()) {
            adjusted = adjusted.copy(
                requiredInputTypes = buildList {
                    addAll(adjusted.requiredInputTypes)
                    for (t in adjusted.optionalInputTypes) {
                        add(t.withNullity(Nullity.OrNull))
                    }
                },
                optionalInputTypes = listOf(),
            )
        }
        return adjusted
    }

    private fun methodDescription(kind: MethodKind, word: Symbol) = when (kind) {
        MethodKind.Normal -> word.text
        MethodKind.Getter -> "get ${word.text}"
        MethodKind.Setter -> "set ${word.text}"
        MethodKind.Constructor -> "constructor"
    }

    private fun sigDescription(sig: Signature2, m: MethodShape): String = buildString {
        append(m.symbol.text)
        val parameterInfo = m.parameterInfo?.names
        append("(")
        for ((i, formal) in sig.allValueFormals.withIndex()) {
            if (i != 0) { append(", ") }
            val name = parameterInfo?.getOrNull(i + 1)?.text ?: "_" // Skip over `this`
            append(name)
            append(": ")
            append(formal.type)
        }
        append("): ")
        append(sig.returnType2)
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

fun Signature2.withoutThisFormal(): Signature2 =
    if (hasThisFormal) {
        copy(requiredInputTypes = requiredInputTypes.drop(1), hasThisFormal = false)
    } else {
        this
    }
