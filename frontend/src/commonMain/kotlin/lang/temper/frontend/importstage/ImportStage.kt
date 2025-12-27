package lang.temper.frontend.importstage

import lang.temper.ast.TreeVisit
import lang.temper.ast.VisitCue
import lang.temper.common.Log
import lang.temper.common.ignore
import lang.temper.common.putMultiSet
import lang.temper.env.Environment
import lang.temper.frontend.AstSnapshotKey
import lang.temper.frontend.Module
import lang.temper.frontend.StageOutputs
import lang.temper.frontend.StagingFlags
import lang.temper.frontend.flipDeclaredNames
import lang.temper.frontend.implicits.ImplicitsModule
import lang.temper.frontend.implicits.ImplicitsUnavailableException
import lang.temper.frontend.interpretiveDanceStage
import lang.temper.interp.UserFunction
import lang.temper.interp.importExport.Export
import lang.temper.interp.importExport.Exporter
import lang.temper.interp.importExport.ImportMacro
import lang.temper.interp.importExport.Importer
import lang.temper.interp.importExport.STANDARD_LIBRARY_FILEPATH
import lang.temper.interp.importExport.STANDARD_LIBRARY_SPECIFIER_PREFIX
import lang.temper.interp.importExport.emplaceMetadataFromExportingDeclaration
import lang.temper.log.CodeLocation
import lang.temper.log.Debug
import lang.temper.log.FailLog
import lang.temper.log.LogSink
import lang.temper.log.MessageTemplate
import lang.temper.log.Position
import lang.temper.log.resolveDir
import lang.temper.log.snapshot
import lang.temper.name.BuiltinName
import lang.temper.name.ExportedName
import lang.temper.name.ImplicitsCodeLocation
import lang.temper.name.ModuleName
import lang.temper.name.ParsedName
import lang.temper.stage.Stage
import lang.temper.type.Abstractness
import lang.temper.type.NominalType
import lang.temper.value.BlockTree
import lang.temper.value.CallTree
import lang.temper.value.InterpreterCallback
import lang.temper.value.InterpreterCallback.NullInterpreterCallback.logSink
import lang.temper.value.ReifiedType
import lang.temper.value.RightNameLeaf
import lang.temper.value.TBoolean
import lang.temper.value.TClass
import lang.temper.value.TFunction
import lang.temper.value.TString
import lang.temper.value.TSymbol
import lang.temper.value.TType
import lang.temper.value.Value
import lang.temper.value.ValueLeaf
import lang.temper.value.curliesBuiltinName
import lang.temper.value.extensionSymbol
import lang.temper.value.implicitSymbol
import lang.temper.value.initSymbol
import lang.temper.value.isImplicits
import lang.temper.value.nameContained
import lang.temper.value.optionalImportSymbol
import lang.temper.value.regexLiteralBuiltinName
import lang.temper.value.staticExtensionSymbol
import lang.temper.value.symbolContained
import lang.temper.value.typeShapeAtLeafOrNull
import lang.temper.value.unpackPairValue
import lang.temper.value.vInitSymbol
import lang.temper.value.void

internal class ImportStage(
    private val module: Module,
    private val root: BlockTree,
    private val failLog: FailLog,
    private val logSink: LogSink,
    private val additionalImplicitImports: List<Export>,
) {
    fun process(callback: (StageOutputs) -> Unit) {
        val configKey = root.configurationKey
        val outputs = Debug.Frontend.ImportStage(configKey).group("Import stage") {
            interpretiveDanceStage(
                stage = Stage.Import,
                module = module,
                root = root,
                failLog = failLog,
                logSink = logSink,
                beforeInterpretation = { root, env ->
                    Debug.Frontend.ImportStage.Before.snapshot(configKey, AstSnapshotKey, root)
                    maybeImportImplicits(module, additionalImplicitImports, root, env)
                },
                afterInterpretation = { (root), _ ->
                    flipDeclaredNames(root)

                    Debug.Frontend.ImportStage.After.snapshot(configKey, AstSnapshotKey, root)
                },
            )
        }

        callback(outputs)
    }
}

private fun maybeImportImplicits(
    module: Module,
    additionalImplicitImports: List<Export>,
    root: BlockTree,
    env: Environment,
) {
    val skipImportImplicits = module.isImplicits || TBoolean.unpackOrNull(
        env[StagingFlags.skipImportImplicits, InterpreterCallback.NullInterpreterCallback]
            as? Value<*>,
    ) == true

    val exportsFromImplicits = if (
        skipImportImplicits
    ) {
        // Do not try to do any implicit imports when bootstrapping the implicits module.
        emptyList()
    } else {
        try {
            ImplicitsModule.module.exports
        } catch (ex: ImplicitsUnavailableException) {
            // It's nice to be able to debug problems with implicits via unit tests that do not
            // need implicits to work.
            // Swallow any trouble getting the implicits module so that those tests can continue,
            // and use a separate unit test to check that the implicits module stages properly.
            ignore(ex)
            null
        } ?: run {
            logSink.log(
                level = Log.Fatal,
                template = MessageTemplate.ImplicitsUnavailable,
                pos = Position(ImplicitsCodeLocation, 0, 0),
                values = emptyList(),
            )
            emptyList()
        }
    }

    val exportsFromOuter = module.outer?.exports ?: emptyList()

    fun isBuiltin(name: ParsedName) =
        env.constness(BuiltinName(name.nameText)) != null

    val exportMap = buildMap {
        for (
        exports in listOf(
            exportsFromImplicits,
            exportsFromOuter,
        )
        ) {
            for (export in exports) {
                this[export.name.baseName] = export
            }
        }
    }

    val namesMentioned = mutableSetOf<ParsedName>()
    val dotNamesMentioned = mutableSetOf<String>()
    val namesImported = mutableMapOf<ParsedName, Export>()
    val depsNeeded = mutableSetOf<String>()
    TreeVisit.startingAt(root)
        .forEachContinuing node@{ node ->
            // Find names we might want.
            val name = when (node) {
                is RightNameLeaf -> node.content as? ParsedName
                // Symbols might be used in key-value punning.
                is ValueLeaf -> node.symbolContained?.let { ParsedName(it.text) }
                is CallTree -> {
                    // Look for dot names

                    // Look for `@json` annotations, and add a dependency on `std/json`.
                    // This allows for JSON codegen to generate and then re-stage generated code
                    // in the context of std/json extension functions.
                    // TODO: allow a general mechanism for macros to connect to other modules.
                    val callee = node.childOrNull(0)
                    if (!skipImportImplicits && callee is RightNameLeaf) {
                        when (callee.content.builtinKey) {
                            "@" -> {
                                val decorator = node.childOrNull(1)
                                if (decorator is RightNameLeaf && "json" == decorator.content.builtinKey) {
                                    depsNeeded.add("${STANDARD_LIBRARY_SPECIFIER_PREFIX}json")
                                }
                            }
                            "." -> {
                                TSymbol.unpackOrNull((node.childOrNull(2) as? ValueLeaf)?.content)?.let {
                                    dotNamesMentioned.add(it.text)
                                }
                            }
                        }
                    }

                    null
                }
                else -> null
            } ?: return@node
            // Got a possibility. False positives are ok, just extra work.
            namesMentioned.add(name)
            val export = exportMap[name]
            if (export != null && !isBuiltin(name)) {
                namesImported[name] = export
            }
        }
        .visitPreOrder()

    if (dotNamesMentioned.isNotEmpty()) {
        for (e in additionalImplicitImports) {
            val extensionMatchesMentionedDotName =
                (e.declarationMetadata[extensionSymbol] ?: emptyList()).any {
                    // Is there an @extension matching a dot name that is mentioned?
                    TString.unpackOrNull(it) in dotNamesMentioned
                } || (e.declarationMetadata[staticExtensionSymbol] ?: emptyList()).any {
                    // Or a static extension?
                    it != null &&
                        TString.unpackOrNull(unpackPairValue(it)?.second) in dotNamesMentioned
                }
            if (extensionMatchesMentionedDotName) {
                namesMentioned.add(e.name.baseName)
            }
        }
    }

    if (namesImported.isEmpty() && additionalImplicitImports.isEmpty() && depsNeeded.isEmpty()) {
        return
    }

    val adjustedAdditionalImplicitImports = adjustAdditionalImplicitImports(additionalImplicitImports, root)

    val leftPos = root.pos.leftEdge
    val importedFrom = mutableMapOf<Exporter, MutableSet<ExportedName>>()
    root.insert(at = 0) {
        val sortedImportNames = namesImported.entries.sortedBy { it.key.nameText }
        val allToImport = buildList {
            sortedImportNames.forEach { (baseName, export) ->
                add(baseName to export)
            }
            adjustedAdditionalImplicitImports.forEach { export ->
                // If we import names that weren't mentioned, then the REPL session
                // ends up linking every Module to every Module with a top-level
                // definition which means that the cost of the ReplTranslateFn
                // grows with the size of the REPL session.
                // By only importing those names that could be resolved to, we
                // limit that growth significantly.
                if (export.name.baseName in namesMentioned || !export.optionallyImported) {
                    add(export.name.baseName to export)
                }
            }
        }
        for ((importedName, export) in allToImport) {
            importedFrom.putMultiSet(export.exporter, export.name)
            Decl(leftPos, importedName) {
                V(vInitSymbol)
                Rn(export.name)
                V(implicitSymbol)
                V(void)
                emplaceMetadataFromExportingDeclaration(leftPos, export)
            }
        }
        for (depNeeded in depsNeeded) {
            Decl(leftPos) {
                Call {
                    Rn(curliesBuiltinName)
                }
                V(initSymbol)
                Call(ImportMacro) {
                    V(Value(depNeeded, TString))
                }
            }
        }
    }

    importedFrom.forEach { (exporter, names) ->
        module.recordImportMetadata(
            Importer.OkImportRecord(imported = names.toSet(), isBlockingImport = true, exporter = exporter),
        )
    }
}

val Export.optionallyImported: Boolean get() {
    if (optionalImportSymbol !in this.declarationMetadata) {
        return false
    }
    // Names of instantiable types can be resolved to by property bags, so we
    // do not prune those.
    val typeShape = this.value?.typeShapeAtLeafOrNull
    return !(typeShape != null && typeShape.abstractness == Abstractness.Concrete)
}

/**
 * Return imports adjusted for whatever needs. The check for applicability is fast.
 * This is expected to matter only in the repl and currently considers only regex literal needs.
 */
private fun adjustAdditionalImplicitImports(imports: List<Export>, root: BlockTree): List<Export> {
    // Optional imports only apply to repl right now, where usually modules are small.
    imports.any { it.optionallyImported } || return imports
    // We really want to know needed source locs *before* interpretation, which means we haven't
    // resolved names to macros yet, so it's not really easy even to ask those macros for imports
    // that they want to require.
    // This is a workaround for that catch 22, but still this is at least written in a way that
    // we could expand to other names and their requirements in the future.
    // TODO Some strategy that would work for user macros if they ever need to control this.
    val stdRegexModuleName = ModuleName(
        sourceFile = STANDARD_LIBRARY_FILEPATH.resolveDir("regex"),
        libraryRootSegmentCount = STANDARD_LIBRARY_FILEPATH.segments.size,
        isPreface = false,
    )
    val requiredModuleNames = mutableSetOf<ModuleName>()
    TreeVisit.startingAt(root).forEach { tree ->
        // This is approximate because we haven't resolved ParsedNames, but some false positives are ok.
        // Meanwhile, still limit what kinds of names we consider here.
        val nameText = when (val name = tree.nameContained) {
            is BuiltinName, is ParsedName -> name.builtinKey
            else -> null
        }
        when {
            nameText == regexLiteralBuiltinName.builtinKey -> {
                requiredModuleNames.add(stdRegexModuleName)
                // For now, we aren't looking for anything else, so might as well stop.
                VisitCue.AllDone
            }
            else -> VisitCue.Continue
        }
    }.visitPreOrder()
    requiredModuleNames.isNotEmpty() || return imports
    return imports.map imports@{ import ->
        val value = import.value ?: return@imports import
        val loc = value.loc() ?: return@imports import
        when {
            loc in requiredModuleNames -> import.copy(
                declarationMetadata = import.declarationMetadata.filter { it.key != optionalImportSymbol },
            )
            else -> import
        }
    }
}

/**
 * Find the source location for the value when available.
 * Likely doesn't handle all potential cases yet.
 */
private fun Value<*>.loc(): CodeLocation? {
    return when (val tag = typeTag) {
        // This actually gets the loc of the type, which is good enough for current needs.
        is TClass -> tag.typeShape.pos
        // And we don't actually even need TFunction right now, but I had an example to work from so supported that.
        is TFunction -> (stateVector as? UserFunction)?.pos
        is TType -> ((stateVector as? ReifiedType)?.type as? NominalType)?.definition?.pos
        else -> null
    }?.loc
}
