package lang.temper.frontend.syntax

import lang.temper.common.Log
import lang.temper.common.RSuccess
import lang.temper.common.putMultiList
import lang.temper.frontend.Module
import lang.temper.log.FilePath
import lang.temper.log.MessageTemplate
import lang.temper.name.DashedIdentifier
import lang.temper.name.ImplicitsCodeLocation
import lang.temper.name.LibraryNameLocationKey
import lang.temper.name.ModuleName
import lang.temper.name.ParsedName
import lang.temper.name.QName
import lang.temper.name.ResolvedParsedName
import lang.temper.name.TemperName
import lang.temper.type.NominalType
import lang.temper.type.TypeShape
import lang.temper.value.BlockTree
import lang.temper.value.DeclTree
import lang.temper.value.FunTree
import lang.temper.value.LinearFlow
import lang.temper.value.MetadataMultimapHelpers.get
import lang.temper.value.ReifiedType
import lang.temper.value.TString
import lang.temper.value.TSymbol
import lang.temper.value.TType
import lang.temper.value.Tree
import lang.temper.value.Value
import lang.temper.value.constructorSymbol
import lang.temper.value.fnSymbol
import lang.temper.value.getterSymbol
import lang.temper.value.initSymbol
import lang.temper.value.methodSymbol
import lang.temper.value.propertySymbol
import lang.temper.value.qNameSymbol
import lang.temper.value.setterSymbol
import lang.temper.value.staticPropertySymbol
import lang.temper.value.typeDeclSymbol
import lang.temper.value.typeDefinedSymbol
import lang.temper.value.typeFormalSymbol

/**
 * Attaches [qNameSymbol] to [QName] metadata to [DeclTree]s.
 */
internal fun attachQNameMetadata(module: Module, root: BlockTree) {
    val (libraryName, relPath) = when (val loc = module.loc) {
        is ImplicitsCodeLocation -> implicitsLibraryName to FilePath.emptyPath
        is ModuleName -> {
            val libraryName = module.sharedLocationContext[loc, LibraryNameLocationKey] ?: return
            var relPath = loc.relativePath()
            if (relPath.isFile) { relPath = relPath.dirName() }
            libraryName to relPath
        }
    }

    // Walk tree to allocated QNames and collect them in a map.
    // Later, we disambiguate and store them back.
    val qNameToDecls = mutableMapOf<QName, MutableList<DeclTree>>()
    val qNameBuilder = QName.Builder(libraryName, relPath)
    fun visit(t: Tree, inLocalContext: Boolean) {
        val partCount = qNameBuilder.partCount
        if (t is DeclTree) {
            val parts = t.parts
            val name = parsedNameFor(parts?.name?.content)
            if (name != null) {
                check(parts != null)
                val metadata = parts.metadataSymbolMultimap
                if (qNameSymbol !in metadata) {
                    val parent = t.incoming?.source
                    val parentFnParts = (parent as? FunTree)?.parts
                    var initial = metadata[initSymbol]?.lastOrNull()?.target
                    if (initial is BlockTree && initial.flow is LinearFlow) {
                        initial = initial.children.lastOrNull()
                    }
                    val kind = when {
                        getterSymbol in metadata -> QName.PartKind.Getter
                        setterSymbol in metadata -> QName.PartKind.Setter
                        constructorSymbol in metadata -> QName.PartKind.Constructor
                        methodSymbol in metadata -> QName.PartKind.FunctionOrMethod
                        typeFormalSymbol in metadata -> QName.PartKind.TypeFormal
                        typeDeclSymbol in metadata -> QName.PartKind.Type
                        propertySymbol in metadata -> QName.PartKind.Decl
                        staticPropertySymbol in metadata -> if (fnSymbol in metadata) {
                            QName.PartKind.FunctionOrMethod
                        } else {
                            QName.PartKind.Decl
                        }
                        parentFnParts?.formals?.contains(t) == true -> QName.PartKind.Input
                        initial is FunTree -> QName.PartKind.FunctionOrMethod
                        inLocalContext -> QName.PartKind.Local
                        else -> QName.PartKind.Decl
                    }
                    var parsedName: ParsedName = name
                    if (kind == QName.PartKind.Getter || kind == QName.PartKind.Setter) {
                        metadata[methodSymbol, TSymbol]?.let {
                            parsedName = ParsedName(it.text)
                        }
                    }
                    qNameBuilder.part(parsedName, kind)
                    val qName = qNameBuilder.toQName()
                    qNameToDecls.putMultiList(qName, t)
                } else {
                    // Store existing QNames in case some micro-passes pre-allocate them.
                    val qNameText = metadata[qNameSymbol, TString]
                    if (qNameText != null) {
                        val qName = QName.fromString(qNameText).result
                        if (qName != null) {
                            qNameToDecls.putMultiList(qName.copy(disambiguationIndex = null), t)
                        } else {
                            module.logSink.log(
                                Log.Error, MessageTemplate.BadQName, t.pos,
                                listOf(qNameText),
                            )
                        }
                    }
                }
            }
        } else if (t is FunTree) {
            val parts = t.parts
            if (parts != null) {
                val metadata = parts.metadataSymbolMultimap
                val typeDefined = metadata[typeDefinedSymbol, TType]
                val definition = ((typeDefined as? ReifiedType)?.type as? NominalType)?.definition
                if (definition is TypeShape) {
                    val name = parsedNameFor(definition.name)
                    if (name != null) {
                        // Push type on as we visit the body containing the member declarations.
                        qNameBuilder.part(name, QName.PartKind.Type)
                    }
                }
            }
        }
        for (child in t.children) {
            val childInLocalContext = inLocalContext ||
                when (t) {
                    is BlockTree -> t !== root // Nested block
                    is FunTree -> child === t.parts?.body
                    else -> false
                }
            visit(child, inLocalContext = childInLocalContext)
        }
        qNameBuilder.resetPartCount(partCount)
    }
    visit(root, false)

    // Store them back
    for ((qName, decls) in qNameToDecls) {
        val unambiguousQNameDeclPairs = if (decls.size == 1) {
            listOf(qName to decls.first())
        } else { // disambiguate
            decls.mapIndexed { i, decl ->
                qName.copy(disambiguationIndex = i) to decl
            }
        }
        for ((unambiguousQName, decl) in unambiguousQNameDeclPairs) {
            val parts = decl.parts
            val edge = parts?.metadataSymbolMap?.get(qNameSymbol)
            check(QName.fromString(unambiguousQName.toString()) is RSuccess)
            val nameTextValue = Value("$unambiguousQName", TString)
            if (edge != null) {
                edge.replace { V(edge.target.pos, nameTextValue) }
            } else {
                decl.insert(decl.size) {
                    val pos = (parts?.name?.pos ?: decl.pos).leftEdge
                    V(pos, qNameSymbol)
                    V(pos, nameTextValue)
                }
            }
        }
    }
}

val implicitsLibraryName = DashedIdentifier("implicits")

private fun parsedNameFor(name: TemperName?): ParsedName? =
    (name as? ParsedName) ?: (name as? ResolvedParsedName)?.baseName
