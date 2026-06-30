package lang.temper.frontend.syntax

import lang.temper.ast.TreeVisit
import lang.temper.common.Log
import lang.temper.common.RSuccess
import lang.temper.common.putMultiList
import lang.temper.common.soleElementOrNull
import lang.temper.frontend.Module
import lang.temper.log.FilePath
import lang.temper.log.MessageTemplate
import lang.temper.name.DashedIdentifier
import lang.temper.name.ImplicitsCodeLocation
import lang.temper.name.LibraryNameLocationKey
import lang.temper.name.ModuleName
import lang.temper.name.ParsedName
import lang.temper.name.QName
import lang.temper.name.ResolvedName
import lang.temper.name.ResolvedParsedName
import lang.temper.name.Symbol
import lang.temper.name.TemperName
import lang.temper.type.NominalType
import lang.temper.type.TypeShape
import lang.temper.value.BlockTree
import lang.temper.value.DeclParts
import lang.temper.value.DeclTree
import lang.temper.value.FunTree
import lang.temper.value.LinearFlow
import lang.temper.value.MetadataMultimapHelpers.get
import lang.temper.value.NameLeaf
import lang.temper.value.TString
import lang.temper.value.TSymbol
import lang.temper.value.TType
import lang.temper.value.Tree
import lang.temper.value.Value
import lang.temper.value.constructorSymbol
import lang.temper.value.fnSymbol
import lang.temper.value.fromTypeSymbol
import lang.temper.value.getterSymbol
import lang.temper.value.initSymbol
import lang.temper.value.methodSymbol
import lang.temper.value.propertySymbol
import lang.temper.value.qNameSymbol
import lang.temper.value.setterSymbol
import lang.temper.value.staticPropertySymbol
import lang.temper.value.typeDeclSymbol
import lang.temper.value.typeDefContained
import lang.temper.value.typeDefinedSymbol
import lang.temper.value.typeFormalSymbol
import lang.temper.value.vQNameSymbol
import lang.temper.value.valueContained
import lang.temper.value.varSymbol

private fun buildTentative(
    qNameBuilder: QName.Builder,
    name: TemperName?,
    parts: DeclParts,
    isFnFormal: Boolean,
    isFunction: Boolean,
    inLocalContext: Boolean,
): QName? {
    val metadata = parts.metadataSymbolMultimap
    var nameKey: Symbol? = null
    val kind = when {
        getterSymbol in metadata -> {
            nameKey = methodSymbol
            QName.PartKind.Getter
        }
        setterSymbol in metadata -> {
            nameKey = methodSymbol
            QName.PartKind.Setter
        }
        constructorSymbol in metadata -> {
            nameKey = methodSymbol
            QName.PartKind.Constructor
        }
        methodSymbol in metadata -> {
            nameKey = methodSymbol
            QName.PartKind.FunctionOrMethod
        }
        typeFormalSymbol in metadata -> {
            nameKey = typeFormalSymbol
            QName.PartKind.TypeFormal
        }
        typeDeclSymbol in metadata -> {
            QName.PartKind.Type
        }
        propertySymbol in metadata -> {
            nameKey = propertySymbol
            QName.PartKind.Decl
        }
        staticPropertySymbol in metadata -> {
            nameKey = staticPropertySymbol
            if (fnSymbol in metadata) {
                QName.PartKind.FunctionOrMethod
            } else {
                QName.PartKind.Decl
            }
        }

        isFnFormal -> QName.PartKind.Input
        isFunction -> QName.PartKind.FunctionOrMethod
        inLocalContext -> QName.PartKind.Local
        else -> QName.PartKind.Decl
    }
    val parsedName = nameKey?.let { nameKey ->
        metadata[nameKey, TSymbol]?.let { metadataSymbolValue ->
            ParsedName(metadataSymbolValue.text)
        }
    } ?: name?.let { parsedNameFor(it) }
    return if (parsedName != null) {
        qNameBuilder.part(parsedName, kind)
        qNameBuilder.toQName()
    } else {
        null
    }
}

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
                    val isFnFormal = parentFnParts?.formals?.contains(t) == true
                    val isFunction = initial is FunTree && varSymbol !in metadata
                    val qName = buildTentative(
                        qNameBuilder,
                        name,
                        parts,
                        isFnFormal = isFnFormal,
                        isFunction = isFunction,
                        inLocalContext = inLocalContext,
                    )
                    if (qName != null) {
                        qNameToDecls.putMultiList(qName, t)
                    }
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
                val definition = (typeDefined?.type as? NominalType)?.definition
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

internal fun fillInMissingQnameMetadata(root: BlockTree) {
    val nameToQName = mutableMapOf<ResolvedName, QName>()
    // Like above, we tentatively allocate and then disambiguate, so collect the
    // same info as above.
    val qNameToDecls = mutableMapOf<QName, MutableList<DeclTree>>()
    val needsRepair = mutableListOf<DeclTree>()
    // The declarations with tentative assignments into qNameToDecls above.
    val missing = mutableMapOf<DeclTree, QName>()
    TreeVisit.startingAt(root)
        .forEachContinuing { t ->
            if (t is DeclTree) {
                val parts = t.parts
                if (parts != null) {
                    val name = parts.name.content as ResolvedName
                    val qNameEdge = parts.metadataSymbolMap[qNameSymbol]
                    if (qNameEdge != null) {
                        val qNameStr = TString.unpackOrNull(qNameEdge.target.valueContained)
                        val qNameResult = qNameStr?.let { QName.fromString(it) }
                        if (qNameResult is RSuccess) {
                            val qName = qNameResult.result
                            nameToQName[name] = qName
                            qNameToDecls.putMultiList(qName, t)
                        }
                    } else {
                        needsRepair.add(t)
                    }
                }
            }
        }
        .visitPreOrder()

    for (t in needsRepair) {
        val parts = t.parts ?: continue
        val name = parts.name.content
        var parentQName: QName? = null
        val parent = t.incoming?.source
        val grandParent = parent?.incoming?.source
        val fromType = parts.metadataSymbolMap[fromTypeSymbol]
        if (fromType != null) {
            val typeDefName = fromType.target.typeDefContained()?.name
            if (typeDefName != null) {
                parentQName = nameToQName[typeDefName]
            }
        }
        if (parentQName == null) {
            var owningDecl: DeclTree? = null
            if (parent is FunTree) {
                if (isAssignment(grandParent) && parent.incoming?.edgeIndex == 2) {
                    val leftName = grandParent?.childOrNull(0) as? NameLeaf
                    if (leftName != null) {
                        val pQName = nameToQName[leftName.content]
                        owningDecl = qNameToDecls[pQName]?.soleElementOrNull
                    }
                } else if (
                    grandParent is DeclTree &&
                    grandParent.parts?.metadataSymbolMap[initSymbol] == parent.incoming
                ) {
                    owningDecl = grandParent
                }
            }
            val owningDeclParts = owningDecl?.parts
            if (owningDeclParts != null && varSymbol !in owningDeclParts.metadataSymbolMap) {
                val ownerName = owningDeclParts.name.content
                parentQName = nameToQName[ownerName] ?: missing[owningDecl]
            }
        }

        if (parentQName != null) {
            val builder = QName.Builder(parentQName)
            val tentative = buildTentative(
                builder, name, parts,
                isFnFormal = parent is FunTree && parent.parts?.formals?.contains(t) == true,
                isFunction = fnSymbol in parts.metadataSymbolMap,
                inLocalContext = false,
            )
            if (tentative != null) {
                missing[t] = tentative
                qNameToDecls.putMultiList(tentative, t)
            }
        }
    }

    for ((decl, tentativeQName) in missing) {
        val ambiguityGroup = qNameToDecls.getValue(tentativeQName)
        var disambiguationIndex: Int? = null
        val indicesToAvoid = buildSet {
            for (ambiguousWith in ambiguityGroup) {
                if (ambiguousWith != decl) {
                    val qName = ambiguousWith.parts?.metadataSymbolMultimap[qNameSymbol, TString]
                    val result = qName?.let { QName.fromString(it) }
                    if (result is RSuccess) {
                        add(result.result.disambiguationIndex)
                    }
                }
            }
        }
        while (disambiguationIndex in indicesToAvoid) {
            disambiguationIndex = (disambiguationIndex ?: 0) + 1
        }
        val assignedQName = tentativeQName.copy(disambiguationIndex = disambiguationIndex)
        decl.insert(decl.size) {
            V(decl.pos.rightEdge, vQNameSymbol)
            V(decl.pos.rightEdge, Value("$assignedQName", TString))
        }
    }
}

val implicitsLibraryName = DashedIdentifier("implicits")

private fun parsedNameFor(name: TemperName?): ParsedName? =
    (name as? ParsedName) ?: (name as? ResolvedParsedName)?.baseName
