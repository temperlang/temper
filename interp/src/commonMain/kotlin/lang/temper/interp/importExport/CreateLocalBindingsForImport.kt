package lang.temper.interp.importExport

import lang.temper.common.Log
import lang.temper.common.ignore
import lang.temper.interp.asCurliesCall
import lang.temper.interp.convertToErrorNode
import lang.temper.interp.errorNodeFor
import lang.temper.interp.walkDestructuring
import lang.temper.log.LogSink
import lang.temper.log.MessageTemplate
import lang.temper.log.Position
import lang.temper.name.ExportedName
import lang.temper.name.ParsedName
import lang.temper.name.ResolvedParsedName
import lang.temper.name.TemperName
import lang.temper.value.BlockTree
import lang.temper.value.CallTree
import lang.temper.value.DeclTree
import lang.temper.value.LeftNameLeaf
import lang.temper.value.LinearFlow
import lang.temper.value.NameLeaf
import lang.temper.value.Planting
import lang.temper.value.RightNameLeaf
import lang.temper.value.StayLeaf
import lang.temper.value.TSymbol
import lang.temper.value.Tree
import lang.temper.value.Value
import lang.temper.value.ValueLeaf
import lang.temper.value.connectedSymbol
import lang.temper.value.extensionSymbol
import lang.temper.value.fnSymbol
import lang.temper.value.freeTree
import lang.temper.value.importedSymbol
import lang.temper.value.initSymbol
import lang.temper.value.staticExtensionSymbol
import lang.temper.value.staySymbol
import lang.temper.value.surpriseMeSymbol
import lang.temper.value.symbolContained
import lang.temper.value.toLispy
import lang.temper.value.vImportedSymbol
import lang.temper.value.void

fun createLocalBindingsForImport(
    declTree: DeclTree,
    importer: Importer,
    exporter: Exporter,
    logSink: LogSink,
    specifier: String,
) {
    // Once destructuring assignments are implemented, we may be able to treat import as a simple
    // fetch of an ad-hoc object as long as that allows early stages access to the imports.
    // Either way, the below unblocks us.
    val declEdge = declTree.incoming!!
    val leftTree = declTree.childOrNull(0)
    val parsedNameToExport = exporter.exports?.associate { it.name.baseName to it }
        ?: emptyMap()
    val parts = declTree.partsIgnoringName
        ?: run {
            logSink.log(
                level = Log.Error,
                template = MessageTemplate.MalformedDeclaration,
                pos = declTree.pos,
                values = listOf(),
            )
            convertToErrorNode(declEdge)
            importer.recordImportMetadata(
                Importer.UnresolvableImportRecord(specifier, isBlockingImport = true),
            )
            return@createLocalBindingsForImport
        }

    // Look for parsed names.
    // Several patterns to recognize
    // I. Import of a single name that matches exported name.
    //     // When other.temper exports oneName
    //     let oneName = import("./other");
    // II. Import of a single name when exporter exports a name `default`.
    //     // When other.temper exports default but not oneName.
    //     let oneName = import("./other");
    // III. Import of multiple names that match exported names.
    //     // When other.temper exports a, b, and c.
    //     let [a, b, c] = import("./other");
    // IV. Import of object record that allows binding to new names.
    //     // When other.temper exports exportedName, sameName
    //     let { exportedName: localName, sameName } = import("./other");
    // V. Import of ellipses creates a binding per exported name.
    //     let {...} = import("./other");

    // TODO: IV is not supported by parser yet.  Look into that and/or into let [x as y] syntax.

    val localNameToExportedNamePairs = mutableListOf<Pair<LeftNameLeaf, Export>>()
    fun addExport(leftNameLeaf: LeftNameLeaf, export: Export) {
        localNameToExportedNamePairs.add(leftNameLeaf to export)
    }
    fun badExport(pos: Position, wantedName: TemperName?) {
        if (wantedName != null) {
            logSink.log(
                level = Log.Error,
                template = MessageTemplate.NotExported,
                pos = pos,
                values = listOf(exporter.loc.diagnostic, wantedName),
            )
        } else {
            logSink.log(
                level = Log.Error,
                template = MessageTemplate.NoSymbolForImport,
                pos = pos,
                values = listOf(exporter.loc.diagnostic),
            )
        }
    }

    when {
        leftTree is NameLeaf -> {
            // We haven't yet defined official semantics for importing a module as a whole.
            badExport(leftTree.pos, null)
            convertToErrorNode(declEdge)
            return
        }
        leftTree?.asCurliesCall() != null -> {
            // III., V.
            var wildcard: Tree? = null
            leftTree.walkDestructuring(logSink) targets@{ targetNameLeaf, source, metaNodes ->
                // TODO(tjp, destructuring): Handle meta nodes on import destructures?
                ignore(metaNodes)
                val sourceParsedName = when (source) {
                    is LeftNameLeaf -> when (val name = source.content) {
                        is ParsedName -> name
                        is ResolvedParsedName -> name.baseName
                        else -> null
                    }
                    is ValueLeaf -> TSymbol.unpackOrNull(source.content)?.let {
                        ParsedName(it.text)
                    }
                    else -> null
                }
                if (sourceParsedName != null && targetNameLeaf != null) {
                    val export = parsedNameToExport[sourceParsedName]
                    if (export == null) {
                        badExport(source.pos, sourceParsedName)
                        return@targets
                    }
                    addExport(freeTree(targetNameLeaf), export)
                } else if (isWildcardLeftHandSide(source)) {
                    wildcard = source
                    // Handle wildcard later, after we know all those imported by name
                } else {
                    badExport(source.pos, null)
                }
            }
            if (wildcard != null) {
                val explicitlyImported = localNameToExportedNamePairs.map { it.second }.toSet()
                for (export in parsedNameToExport.values) {
                    if (export !in explicitlyImported) {
                        addExport(
                            LeftNameLeaf(wildcard!!.document, wildcard!!.pos, export.name.baseName),
                            export,
                        )
                    }
                }
            }
        }
        else -> {
            TODO("${leftTree?.toLispy()}")
        }
    }

    // After we've created all the local declarations, add metadata.
    // We delay these until after we've created the local declarations so that
    // we don't accidentally get extra metadata when copying the first declaration.
    val delayedDeclarationMutations = mutableListOf<() -> Unit>()

    if (localNameToExportedNamePairs.size == 1) {
        val (localName, export) = localNameToExportedNamePairs.first()
        leftTree.incoming!!.replace { Replant(localName) }
        val initEdge = parts.metadataSymbolMap[initSymbol]
            ?: run {
                val endIndex = declTree.size
                val initPos = declTree.pos.rightEdge
                declTree.replace(endIndex..endIndex) {
                    V(initPos, initSymbol)
                    V(initPos, void)
                }
                declTree.edge(endIndex + 1)
            }
        initEdge.replace {
            Rn(initEdge.target.pos, export.name)
        }
        delayedDeclarationMutations.add {
            emplaceImportedMeta(declTree, export.name)
            emplaceMetadataFromExportingDeclaration(declTree, export)
        }
    } else {
        // If we have more than one import, we need to copy it.
        // But there are a few situations where we don't want to copy naively.
        // We need to pull arguments out of decorators to preserve order/frequency of evaluation
        // so that
        //
        //     @Decorator(complexArgument) let ... = import("...");
        //
        // becomes
        //
        //     let t#0 = complexArgument;
        //     @Decorator(t#0) let firstExportedName = exporter.firstExportedName;
        //     @Decorator(t#0) let secondExportedName = exporter.secondExportedName;
        //
        // Also we should pull complex metadata out to preserve order/frequency of evaluation.  In
        //
        //     let ...: ComplexTypeExpression = import("...");
        //
        // ideally we would pull complex metadata out of the declaration.
        // For types, we should be pulling apart the type, so we currently issue an error
        // if there is a type when we've got complicated imports.
        // At some points, we might implement a type operator like `getElementType(index)` and
        // use it.
        //
        // The third wrinkle is that we shouldn't move the stay leaf that the BuildConductor uses
        // to find the declaration after exports become available.
        var edgeToCopy = declEdge
        while (true) {
            val parentOfEdgeToCopy = edgeToCopy.source
            if (parentOfEdgeToCopy !is CallTree) { break }
            val callee = parentOfEdgeToCopy.child(0) as? RightNameLeaf ?: break
            if (callee.content.builtinKey != "@") { break }
            if (edgeToCopy.edgeIndex != 2) { break } // Must be decorated
            edgeToCopy = (parentOfEdgeToCopy.incoming ?: break)
        }

        val replacements = mutableListOf<Tree>()
        // Pull out complex annotation parameters.
        run {
            var tree = edgeToCopy.target
            while (tree is CallTree) {
                val decoration = tree.child(1)
                if (decoration is CallTree) {
                    for (i in 1 until decoration.size) {
                        val argEdge = decoration.edge(i)
                        val argTree = argEdge.target
                        if (mayBeComplexArg(argTree)) {
                            val temporaryName = argTree.document.nameMaker.unusedTemporaryName("t")
                            argEdge.replace { Rn(argTree.pos, temporaryName) }
                            replacements.add(
                                argTree.treeFarm.grow(argTree.pos) {
                                    Decl {
                                        Ln(argTree.pos, temporaryName)
                                        V(initSymbol)
                                        Replant(argTree)
                                    }
                                },
                            )
                        }
                    }
                }
                tree = tree.child(2) // Ok based on decorated check above
            }
        }

        // Now, generate an adjusted copy of edgeToCopy for each imported symbol.
        localNameToExportedNamePairs.forEachIndexed { importedIndex, (localNameLeaf, export) ->
            val oneReplacement = if (importedIndex == 0) {
                edgeToCopy.target
            } else {
                edgeToCopy.target.copy()
            }

            var replacementDecl = oneReplacement
            while (replacementDecl is CallTree) {
                replacementDecl = replacementDecl.child(2) // Assumes decorated index check above
            }
            check(replacementDecl is DeclTree) // Assumes reversal of loop checks above

            replacementDecl.replace(0..0) { Replant(localNameLeaf) }
            val replacementParts = replacementDecl.parts
            val initEdge = replacementParts?.metadataSymbolMap?.get(initSymbol)
            if (initEdge == null) {
                logSink.log(
                    level = Log.Error,
                    template = MessageTemplate.MalformedDeclaration,
                    pos = replacementDecl.pos,
                    values = listOf(),
                )
                replacements.add(errorNodeFor(oneReplacement))
                return@forEachIndexed
            }
            initEdge.replace {
                Rn(initEdge.target.pos, export.name)
            }

            if (importedIndex == 0) {
                emplaceImportedMeta(replacementDecl, export.name)
            } else {
                // Remove any stay leaf
                val stayEdgeIndex = replacementParts.metadataSymbolMap[staySymbol]?.edgeIndex
                if (stayEdgeIndex != null) {
                    replacementDecl.replace((stayEdgeIndex - 1)..stayEdgeIndex) {
                        // Replace with nothing
                    }
                }
                // Modify any imported symbol.
                val importedEdgeIndex =
                    replacementParts.metadataSymbolMap[importedSymbol]?.edgeIndex
                if (importedEdgeIndex != null) {
                    replacementDecl.replace(importedEdgeIndex..importedEdgeIndex) {
                        buildImportedValue(initEdge.target.pos, export.name)
                    }
                }
            }
            delayedDeclarationMutations.add {
                emplaceMetadataFromExportingDeclaration(replacementDecl, export)
            }

            replacements.add(oneReplacement)
        }

        val parent = edgeToCopy.source!!
        if (parent is BlockTree && parent.flow is LinearFlow) {
            val edgeIndex = edgeToCopy.edgeIndex
            parent.replace(edgeIndex..edgeIndex) {
                replacements.forEach { Replant(it) }
            }
        } else {
            edgeToCopy.replace {
                Block(edgeToCopy.target.pos) {
                    replacements.forEach { Replant(it) }
                }
            }
        }
    }

    for (delayedAction in delayedDeclarationMutations) {
        delayedAction()
    }

    importer.recordImportMetadata(
        Importer.OkImportRecord(
            imported = buildSet {
                for ((_, export) in localNameToExportedNamePairs) {
                    add(export.name)
                }
            },
            isBlockingImport = true,
            exporter = exporter,
        ),
    )
}

private fun emplaceImportedMeta(declTree: DeclTree, name: ExportedName) {
    declTree.replace(declTree.size until declTree.size) {
        V(declTree.pos.rightEdge, vImportedSymbol)
        buildImportedValue(declTree.pos.rightEdge, name)
    }
}

/** Returns a name wrapped in an escape to prevent interpreter evaluation. */
private fun Planting.buildImportedValue(pos: Position, name: ExportedName) =
    Esc(pos.rightEdge) { Rn(pos.rightEdge, name) }

private fun mayBeComplexArg(tree: Tree) = tree !is ValueLeaf && tree !is StayLeaf

private fun isWildcardLeftHandSide(tree: Tree) = tree.symbolContained == surpriseMeSymbol

private fun emplaceMetadataFromExportingDeclaration(declTree: DeclTree, export: Export) {
    val pos = declTree.pos.rightEdge
    declTree.replace(declTree.size until declTree.size) {
        this.emplaceMetadataFromExportingDeclaration(pos, export)
    }
}

fun Planting.emplaceMetadataFromExportingDeclaration(pos: Position, export: Export) {
    val metadataToCopy = export.declarationMetadata.filter {
        it.key in copyableMetadataKeys
    }
    metadataToCopy.forEach { (symbol, values) ->
        values.lastOrNull()?.let { value ->
            V(pos, Value(symbol))
            V(pos, value)
        }
    }
}

private val copyableMetadataKeys = listOf(
    connectedSymbol,
    extensionSymbol,
    fnSymbol,
    staticExtensionSymbol,
)
