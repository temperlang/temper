package lang.temper.be.tmpl

import lang.temper.common.putMultiList
import lang.temper.log.CodeLocation
import lang.temper.log.spanningPosition
import lang.temper.name.ExportedName
import lang.temper.name.ResolvedName

/**
 * For documentation snippets to type properly, we need types for most names used.
 *
 * We use module prefaces so that documentation authors can specify pre-requisites,
 * things which are necessary for the documentation to stand alone, but they are not
 * things that a developer adapting the example code should have to provide.
 *
 * Where possible, we want to produce stand-alone translated documentation, so that
 * a button that lets the reader open the example in *repl.it* can provide everything
 * necessary.
 *
 * This step to *TmpLTranslator* recombines each preface from documentation with
 * the sole non-preface module that uses it, which is the case because documentation
 * prefaces should not be declaring module parameters.
 */
internal fun spliceDocPrefixesBackIntoModules(
    modules: MutableList<NascentModule>,
    docModuleLocations: Set<CodeLocation>,
) {
    val locationToIndices = mutableMapOf<CodeLocation, MutableList<Int>>()
    for ((i, module) in modules.withIndex()) {
        locationToIndices.putMultiList(module.codeLocation.codeLocation, i)
    }

    // Relate indices of prefaces to indices of corresponding modules.
    val byPreface = mutableMapOf<Int, MutableList<Int>>()
    for ((i, module) in modules.withIndex()) {
        val loc = module.codeLocation.codeLocation
        if (!loc.isPreface) {
            val preface = loc.copy(isPreface = true)
            val prefaceIndices = locationToIndices[preface] ?: emptyList()
            prefaceIndices.forEach { prefaceIndex ->
                byPreface.putMultiList(prefaceIndex, i)
            }
        }
    }

    // Whenever we have one doc module, that has exactly one preface that is only a preface to it,
    // fold the preface into the module, and remove the preface.
    val toRemove = mutableSetOf<Int>()
    for ((prefaceIndex, moduleIndices) in byPreface) {
        if (moduleIndices.size != 1) { continue }
        val moduleIndex = moduleIndices.first()
        val module = modules[moduleIndex]
        if (module.codeLocation.codeLocation !in docModuleLocations) { continue }
        val preface = modules[prefaceIndex]
        if (locationToIndices[preface.codeLocation.codeLocation]?.size != 1) { continue }

        val combined = spliceTogether(preface, module)
        modules[moduleIndex] = combined
        toRemove.add(prefaceIndex)
    }

    val toRemoveDescending = toRemove.sortedDescending()
    for (indexToRemove in toRemoveDescending) {
        modules.removeAt(indexToRemove)
    }
}

private fun spliceTogether(
    preface: NascentModule,
    module: NascentModule,
): NascentModule {
    val combinedTopLevels = mutableListOf<TmpL.TopLevel>()
    combinedTopLevels.add(TmpL.BoilerplateCodeFoldStart(preface.pos.leftEdge))

    val prefaceOrigin = preface.codeLocation.origin
    val prefaceExportedTypes = buildSet {
        // Type names are dependent on TypeShape names, which we can't translate
        // until we unhook TmpL from frontend objects.  We need to not remap these.
        // TODO: after uncoupling names from frontend names, this is no longer necessary.
        for (topLevel in preface.topLevels) {
            if (topLevel is TmpL.TypeDeclaration) {
                val name = topLevel.name.name
                if (name is ExportedName && name.comesFrom(prefaceOrigin)) {
                    add(name)
                }
            }
        }
    }
    val namesTranslatedInPreface = mutableMapOf<ResolvedName, ResolvedName?>()
    val prefaceRenamer = Renamer {
        namesTranslatedInPreface.getOrPut(it) {
            // We need preface exports to be combined module exports.
            if (it is ExportedName && it.comesFrom(prefaceOrigin) && it !in prefaceExportedTypes) {
                ExportedName(module.codeLocation.origin, it.baseName)
            } else {
                null
            }
        }
    }
    preface.topLevels.mapTo(combinedTopLevels) {
        prefaceRenamer.rewriteTopLevel(it)
    }
    combinedTopLevels.add(TmpL.BoilerplateCodeFoldEnd(preface.pos.rightEdge))

    val prefaceImports = mutableMapOf<ResolvedName, ExportedName>()
    val moduleTopLevelsToPreserve = mutableListOf<TmpL.TopLevel>()

    // For each local alias of a preface export, we'll need to rewrite it to
    // the preface name; undoing the aliasing that wouldn't be there if they
    // had been spliced together from the start.
    for (topLevel in module.topLevels) {
        if (topLevel is TmpL.ModuleLevelDeclaration && topLevel.assignOnce) {
            val name = topLevel.name
            val init = topLevel.init
            if (init is TmpL.Reference) {
                val rightName = init.id.name
                if (rightName is ExportedName && rightName.comesFrom(prefaceOrigin)) {
                    val nameInModuleContext = prefaceRenamer.newNameFor(rightName)
                    if (nameInModuleContext is ExportedName) {
                        prefaceImports[name.name] = nameInModuleContext
                    } else {
                        prefaceImports[name.name] = rightName
                    }
                    continue // do not preserve below
                }
            }
        }
        moduleTopLevelsToPreserve.add(topLevel)
    }

    val moduleRenamer = Renamer {
        // We need to rename it if it's a reference to a local import
        // or if it's a rewrite of an exported name used in place, like a type name.
        prefaceImports[it] ?: namesTranslatedInPreface[it]
    }
    moduleTopLevelsToPreserve.forEach { topLevel ->
        combinedTopLevels.add(moduleRenamer.rewriteTopLevel(topLevel))
    }

    return NascentModule(
        pos = listOf(preface, module).spanningPosition(module.pos),
        moduleMetadata = module.moduleMetadata,
        codeLocation = module.codeLocation,
        topLevels = combinedTopLevels,
        result = module.result,
        translator = module.translator,
    )
}

private class Renamer(
    val newNameFor: (ResolvedName) -> ResolvedName?,
) : TmpLTreeRewriter {
    override fun rewriteId(x: TmpL.Id): TmpL.Id {
        val name = x.name
        val newName = newNameFor(name)
        return if (newName != null) {
            TmpL.Id(x.pos, newName, outNameFor(newName))
        } else {
            super.rewriteId(x)
        }
    }
}
