package lang.temper.be

import lang.temper.be.tmpl.TmpL
import lang.temper.common.Either
import lang.temper.common.Log
import lang.temper.library.LibraryConfiguration
import lang.temper.log.FilePath
import lang.temper.log.LogSink
import lang.temper.log.MessageTemplate
import lang.temper.name.ModularName
import lang.temper.name.ModuleName
import lang.temper.name.ParsedName
import lang.temper.name.ResolvedName
import lang.temper.name.ResolvedNameMaker
import lang.temper.name.ResolvedParsedName
import lang.temper.name.Temporary
import lang.temper.value.TType
import lang.temper.value.isImplicits

/**
 * Rewrites [TmpL.Import] directives with signature information, and,
 * if any local names are needed, rewrites uses of those names to the
 * local names.
 */
fun Backend<*>.finishTmpLImports(
    tentative: TmpL.ModuleSet,
    externalNamesProvided: ExternalNamesProvided,
    logSink: LogSink,
) {
    val supportNetwork = this.supportNetwork
    val genre = tentative.genre
    val nameMaker = ResolvedNameMaker(tentative.mergedNamingContext, genre)

    for (module in tentative.modules) {
        val nameReplacements = mutableMapOf<ModularName, ResolvedName>()
        val localAliases = buildMap {
            for (topLevel in module.topLevels) {
                if (topLevel is TmpL.ModuleLevelDeclaration) {
                    val aliased = when (val init = topLevel.init) {
                        null -> null
                        is TmpL.Reference -> init.id.name
                        is TmpL.ValueReference -> {
                            // TODO: if there's a canonical name for this value, treat it as
                            // an alias of that.
                            val reifiedType = TType.unpackOrNull(init.value)
                            reifiedType?.type2?.definition?.name
                        }
                        else -> null
                    }
                    if (
                        aliased is ModularName &&
                        aliased.origin.loc != module.codeLocation.codeLocation &&
                        aliased !in this
                    ) {
                        put(aliased, topLevel)
                    }
                }
            }
        }

        val redundantAliases = mutableSetOf<TmpL.TopLevel>()
        for (importNode in module.imports) {
            if (importNode.sig != null) { continue } // Already finished

            val externalName = importNode.externalName.name as ModularName
            val externalNameLoc = externalName.origin.loc
            val providers: ProviderList =
                externalNamesProvided.localProviders[externalName]
                    ?: externalNamesProvided.providers[externalName]
                    ?: emptyList()
            val isLocal = externalName in externalNamesProvided.localProviders

            var providerIndex = 0
            if (providers.size >= 2 && externalNameLoc is ModuleName) {
                providerIndex = providers.indexOfFirst { (definingModule) ->
                    definingModule.libraryConfiguration.libraryRoot == externalNameLoc.libraryRoot()
                }
            }
            if (providerIndex !in providers.indices) {
                if (externalName.origin.isImplicits) {
                    // OK.  Implicits is not compiled.  Backends need to map these name to support codes
                    // or have some other strategy for them.
                } else {
                    logSink.log(
                        level = Log.Error,
                        template = MessageTemplate.NotExported,
                        pos = importNode.pos,
                        values = listOf(externalNameLoc, externalName),
                    )
                }
                continue
            }

            val (provider, definition) = providers[providerIndex]

            val pos = importNode.pos.rightEdge
            val to = provider.codeLocation.codeLocation
            val libraryName = provider.libraryConfiguration.libraryName
            val translatedPath: FilePath = provider.codeLocation.outputPath

            val newModulePath = if (isLocal) {
                TmpL.SameLibraryPath(
                    pos = pos,
                    to = to,
                    libraryName = libraryName,
                    translatedPath = translatedPath,
                )
            } else {
                TmpL.CrossLibraryPath(
                    pos = pos,
                    to = to,
                    libraryName = libraryName,
                    translatedPath = translatedPath,
                )
            }

            // If there's an existing local alias, then we can re-use that.
            val localAlias = localAliases[externalName]
            val localName = localAlias?.name?.name
            // Otherwise, figure out if the SupportNetwork wants a local name
            var needsLocal = false
            val newSig = when (definition) {
                is TmpL.PooledValueDeclaration -> null
                is TmpL.SupportCodeDeclaration -> null
                is TmpL.TypeDeclaration -> {
                    needsLocal = supportNetwork.needsLocalNameForExternallyDefinedType
                    TmpL.ImportedType(
                        pos = definition.pos,
                        metadata = definition.metadata,
                        typeShape = definition.typeShape,
                    )
                }
                is TmpL.TypeConnection -> TmpL.ImportedConnection(
                    pos = definition.pos,
                    metadata = definition.metadata,
                    connectedKey = definition.connectedKey.deepCopy(),
                )
                is TmpL.ModuleFunctionDeclaration -> {
                    needsLocal = supportNetwork.needsLocalNameForExternallyDefinedFunction
                    TmpL.ImportedFunction(
                        pos = definition.pos,
                        metadata = definition.metadata,
                        type = definition.sig,
                    )
                }
                is TmpL.Test -> TODO()
                is TmpL.ModuleLevelDeclaration -> {
                    needsLocal = supportNetwork.needsLocalNameForExternallyDefinedValue
                    TmpL.ImportedValue(
                        pos = definition.pos,
                        metadata = definition.metadata,
                        type = definition.type.deepCopy(),
                    )
                }
                is TmpL.TypeFormal -> null
            }
            importNode.path = newModulePath
            if (needsLocal || localName != null) {
                val baseName = (externalName as? ResolvedParsedName)?.baseName
                    ?: ParsedName((externalName as? Temporary)?.nameHint ?: "local")
                val localNameForImportNode = localName
                    ?: run {
                        val newLocalName = nameMaker.unusedSourceName(baseName)
                        newLocalName
                    }
                importNode.localName = TmpL.Id(importNode.externalName.pos, localNameForImportNode)
                if (externalName !in nameReplacements) {
                    nameReplacements[externalName] = localNameForImportNode
                }
                if (localAlias != null) {
                    redundantAliases.add(localAlias)
                }
            }
            if (newSig != null) {
                importNode.sig = newSig
            }
        }
        if (redundantAliases.isNotEmpty()) {
            module.topLevels = module.topLevels.filter {
                it !in redundantAliases
            }
        }
        if (nameReplacements.isNotEmpty()) {
            fun replaceNames(t: TmpL.Tree) {
                if (t is TmpL.Id) {
                    val replacement = nameReplacements[t.name]
                    if (replacement != null) {
                        t.nameContent = Either.Left(replacement)
                    }
                }
                t.children.forEach { replaceNames(it) }
            }

            module.topLevels.forEach { replaceNames(it) }
        }
    }
}

/**
 * This looks at all the [TmpL.Import]s that have [cross library][TmpL.CrossLibraryPath] dependencies
 * and adds those to [dependenciesBuilder].
 *
 * [Backend]s call out to this so that [Backend.getDependencies] eventually has
 * all the dependencies needed.
 */
fun registerCrossLibraryImports(
    moduleSet: TmpL.ModuleSet,
    dependenciesBuilder: Dependencies.Builder<*>,
) {
    val from = moduleSet.libraryConfiguration.libraryName
    for (module in moduleSet.modules) {
        for (dep in module.deps) {
            dependenciesBuilder.addDependency(from = from, to = dep.libraryName)
        }
        for (import in module.imports) {
            when (val path = import.path) {
                is TmpL.CrossLibraryPath -> {
                    val to = dependenciesBuilder.libraryConfigurations
                        .byLibraryRoot[path.to.libraryRoot()]
                        ?.libraryName
                    if (to != null) {
                        dependenciesBuilder.addDependency(from = from, to = to)
                    }
                }
                is TmpL.SameLibraryPath -> {}
                null -> {}
            }
        }
    }
}

private val TmpL.Module.libraryConfiguration: LibraryConfiguration get() =
    (this.parent as TmpL.ModuleSet).libraryConfiguration
