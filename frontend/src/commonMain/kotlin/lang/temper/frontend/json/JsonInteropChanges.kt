package lang.temper.frontend.json

import lang.temper.common.AtomicCounter
import lang.temper.common.Named
import lang.temper.common.structure.Hints
import lang.temper.common.structure.StructureSink
import lang.temper.common.structure.Structured
import lang.temper.format.OutputToken
import lang.temper.format.OutputTokenType
import lang.temper.format.TokenSerializable
import lang.temper.format.TokenSink
import lang.temper.format.toStringViaTokenSink
import lang.temper.lexer.Genre
import lang.temper.log.ConfigurationKey
import lang.temper.log.FilePath
import lang.temper.log.SharedLocationContext
import lang.temper.log.unknownPos
import lang.temper.name.ModuleName
import lang.temper.name.NamingContext
import lang.temper.name.ResolvedName
import lang.temper.name.Symbol
import lang.temper.type.Abstractness
import lang.temper.type.NominalType
import lang.temper.type.StaticType
import lang.temper.type.TypeFormal
import lang.temper.type.Visibility
import lang.temper.value.DependencyCategory
import lang.temper.value.Document
import lang.temper.value.DocumentContext
import lang.temper.value.FunTree
import lang.temper.value.Planting
import lang.temper.value.PseudoCodeDetail
import lang.temper.value.UnpositionedTreeTemplate
import lang.temper.value.toPseudoCode

internal class JsonInteropChanges(
    val typeNameToAddedMethods: Map<ResolvedName, List<AddedMethod>>,
    val adapterClasses: List<AddedType>,
) : Structured {
    fun isEmpty() = typeNameToAddedMethods.isEmpty() && adapterClasses.isEmpty()

    override fun destructure(structureSink: StructureSink) = structureSink.obj {
        key("methods", isDefault = typeNameToAddedMethods.isEmpty()) {
            obj {
                typeNameToAddedMethods.forEach { (typeName, addedMethods) ->
                    key(
                        toStringViaTokenSinkSuffixDropping {
                            it.emit(typeName.toToken(inOperatorPosition = false))
                        },
                    ) {
                        obj {
                            addedMethods.forEach {
                                key(it.nameAsStructuredKey()) {
                                    it.destructure(this, nameRequired = false)
                                }
                            }
                            key("__DO_NOT_CARE__", Hints.su) {
                                value("__DO_NOT_CARE__")
                            }
                        }
                    }
                }
            }
        }
        key("adapters", isDefault = adapterClasses.isEmpty()) {
            obj {
                adapterClasses.forEach { c ->
                    key(c.nameAsStructuredKey()) {
                        c.destructure(this, nameRequired = false)
                    }
                }
            }
        }
    }

    internal interface NamedStructured : Structured {
        override fun destructure(structureSink: StructureSink) =
            destructure(structureSink, nameRequired = true)
        fun destructure(structureSink: StructureSink, nameRequired: Boolean)
        fun nameAsStructuredKey(): String
    }

    data class AddedType(
        override val name: ResolvedName,
        val abstractness: Abstractness,
        val typeFormals: List<TypeFormal>,
        val superTypes: List<NominalType>,
        val properties: List<AddedProperty>,
        val methods: List<AddedMethod>,
    ) : NamedStructured, Named<ResolvedName> {
        override fun nameAsStructuredKey(): String = toStringViaTokenSinkSuffixDropping {
            name.renderTo(it)
        }

        override fun destructure(structureSink: StructureSink, nameRequired: Boolean) = structureSink.obj {
            key("name", isDefault = !nameRequired) { valueSuffixDropping(name) }
            key("typeFormals", isDefault = typeFormals.isEmpty()) {
                arr {
                    typeFormals.forEach { valueSuffixDropping(it) }
                }
            }
            key("extends", isDefault = superTypes.isEmpty()) {
                arr {
                    superTypes.forEach { valueSuffixDropping(it) }
                }
            }
            key("properties", isDefault = properties.isEmpty()) {
                obj {
                    properties.forEach {
                        key(it.nameAsStructuredKey()) {
                            it.destructure(this, nameRequired = false)
                        }
                    }
                }
            }
            key("methods") {
                obj {
                    methods.forEach {
                        key(it.nameAsStructuredKey()) {
                            it.destructure(this, nameRequired = false)
                        }
                    }
                }
            }
            if (!nameRequired) {
                key("__DO_NOT_CARE__", Hints.su) {
                    value("__DO_NOT_CARE__")
                }
            }
        }
    }

    data class AddedMethod(
        val isStatic: Boolean,
        val visibility: Visibility,
        override val name: Symbol,
        val body: Planting.() -> UnpositionedTreeTemplate<FunTree>,
    ) : NamedStructured, Named<Symbol> {
        override fun nameAsStructuredKey(): String = name.text
        override fun destructure(structureSink: StructureSink, nameRequired: Boolean) = structureSink.obj {
            key("name", isDefault = !nameRequired) { valueSuffixDropping(name) }
            key("visibility", isDefault = visibility == Visibility.Public) { visibility.destructure(this) }
            key("static", isDefault = !isStatic) {
                value(isStatic)
            }
            key("body") {
                val doc = Document(StubDocumentContext)
                val bodyTree = doc.treeFarm.grow(unknownPos) {
                    body()
                }
                value(
                    toStringViaTokenSinkSuffixDropping(singleLine = false) {
                        bodyTree.toPseudoCode(it, PseudoCodeDetail.default.copy(resugarDotHelpers = true))
                    }.trimEnd(),
                )
            }
            key("__DO_NOT_CARE__", Hints.su) {
                value("__DO_NOT_CARE__")
            }
        }
    }

    data class AddedProperty(
        val isStatic: Boolean,
        val visibility: Visibility,
        override val name: Symbol,
        val type: StaticType,
    ) : NamedStructured, Named<Symbol> {
        override fun nameAsStructuredKey(): String = name.text

        override fun destructure(structureSink: StructureSink, nameRequired: Boolean) = structureSink.obj {
            key("name", isDefault = !nameRequired) { valueSuffixDropping(name) }
            key("visibility", isDefault = visibility == Visibility.Public) { visibility.destructure(this) }
            key("static", isDefault = !isStatic) {
                value(isStatic)
            }
            key("type") {
                value(type)
            }
        }
    }
}

internal fun StructureSink.valueSuffixDropping(x: Any?) {
    when (x) {
        is TokenSerializable ->
            value(toStringViaTokenSinkSuffixDropping { x.renderTo(it) })
        is Iterable<*> ->
            arr {
                for (e in x) {
                    valueSuffixDropping(e)
                }
            }
        else -> value(x)
    }
}

internal fun toStringViaTokenSinkSuffixDropping(singleLine: Boolean = true, f: (TokenSink) -> Unit): String =
    toStringViaTokenSink(singleLine = singleLine) {
        f(SuffixDropper(it))
    }

internal class SuffixDropper(val tokenSink: TokenSink) : TokenSink by tokenSink {
    override fun emit(token: OutputToken) {
        tokenSink.emit(
            if (token.type == OutputTokenType.Name) {
                dropSuffix(token)
            } else {
                token
            },
        )
    }

    private fun dropSuffix(token: OutputToken): OutputToken {
        val newText = suffix.replace(token.text, "")
        return if (newText.length != token.text.length) {
            OutputToken(newText, token.type, token.association)
        } else {
            token
        }
    }

    private val suffix = Regex("""__\d+$""")
}

/**
 * Lets us turn AST content that has not yet been attached to a tree into
 * pseudocode for testing and debugging.
 */
private object StubDocumentContext : DocumentContext {
    override val sharedLocationContext: SharedLocationContext
        get() = error("Not a real document context")
    override val definitionMutationCounter: AtomicCounter
        get() = error("Not a real document context")
    override val namingContext: NamingContext = StubNamingContext()
    override val genre = Genre.Library
    override val dependencyCategory = DependencyCategory.Production
    override val configurationKey: ConfigurationKey
        get() = namingContext as ConfigurationKey
}

private class StubNamingContext : NamingContext(), ConfigurationKey {
    override val loc = ModuleName(FilePath.emptyPath, 0, isPreface = false)
}
