package lang.temper.frontend.define

import lang.temper.builtin.BuiltinFuns
import lang.temper.common.Log
import lang.temper.frontend.Module
import lang.temper.frontend.isEffectivelyStd
import lang.temper.log.MessageTemplate
import lang.temper.name.BuiltinName
import lang.temper.name.ExportedName
import lang.temper.name.ModuleName
import lang.temper.name.ParsedName
import lang.temper.name.SourceName
import lang.temper.name.TemperName
import lang.temper.value.BlockTree
import lang.temper.value.DeclTree
import lang.temper.value.RightNameLeaf
import lang.temper.value.TString
import lang.temper.value.TVoid
import lang.temper.value.Value
import lang.temper.value.importedSymbol
import lang.temper.value.newBuiltinName
import lang.temper.value.testSymbol
import lang.temper.value.vConnectedSymbol
import lang.temper.value.vImportedSymbol
import lang.temper.value.vInitSymbol
import lang.temper.value.vSsaSymbol
import lang.temper.value.vStaySymbol
import lang.temper.value.valueContained

internal fun addTemperTestInstructionsTo(
    module: Module,
    root: BlockTree,
) {
    val sharedLocationContext = module.sharedLocationContext

    // Infer a local name for "runTest" by looking for import of `Test`.
    var testClassExportedName: ExportedName? = null
    // In unit tests, we might instead directly access `runTestCases`.
    var localRunTestCasesName: TemperName? = null
    @Suppress("LoopWithTooManyJumpStatements") // All fairly uniform and enable flatter code.
    for (t in root.children) {
        val parts = (t as? DeclTree)?.parts ?: continue
        if ((parts.name.content as? SourceName)?.baseName == runTestCasesParsedName) {
            // This is used only in limited unit test situations.
            localRunTestCasesName = parts.name.content
            continue
        }
        val imported =
            (parts.metadataSymbolMap[importedSymbol]?.target?.children?.first() as? RightNameLeaf) ?: continue
        val exportedName = (imported.content as? ExportedName) ?: continue
        val loc = (exportedName.origin.loc as? ModuleName) ?: continue
        loc.isEffectivelyStd(sharedLocationContext) || continue
        if (exportedName.baseName == testClassParsedName) {
            // This is the expected case for actual code.
            testClassExportedName = exportedName
            break
        }
    }
    if (testClassExportedName == null && localRunTestCasesName == null) {
        module.logSink.log(
            Log.Error,
            MessageTemplate.CannotConnectToTestRunner,
            root.pos.leftEdge,
            emptyList(),
        )
        return
    }

    // Import runTestCases for metadata purposes if we found an import of Test.
    if (testClassExportedName != null) {
        // And insert at the top since this is after top-level sorting. This one has no dependencies.
        root.replace(IntRange(0, -1)) {
            val runTestCasesExportedName = ExportedName(testClassExportedName.origin, runTestCasesParsedName)
            // This decl is based on what we see generated for similar imports.
            // TODO Can we combine any with `createLocalBindingsForImport`?
            Decl {
                Ln { nameMaker ->
                    localRunTestCasesName = nameMaker.unusedSourceName(runTestCasesParsedName)
                    localRunTestCasesName!!
                }
                V(vInitSymbol)
                Rn(runTestCasesExportedName)
                V(vStaySymbol)
                Stay()
                V(vImportedSymbol)
                Esc { Rn(runTestCasesExportedName) }
                V(vConnectedSymbol)
                V(runTestCasesConnectedValue)
                V(vSsaSymbol)
                V(TVoid.value)
            }
        }
    }

    val testDecls = root.children.filter { (it as? DeclTree)?.parts?.metadataSymbolMap?.get(testSymbol) != null }
    if (testDecls.isNotEmpty()) {
        // Run tests, build report, and assign to an export with a predefined name.
        root.replace(root.size until root.size) {
            Decl {
                Ln(ExportedName(module.namingContext, testReportExportName))
                V(vInitSymbol)
                Call {
                    Rn(localRunTestCasesName!!)
                    Call {
                        V(BuiltinFuns.vListifyFn)
                        for (decl in testDecls) {
                            // These things ought to be here when using the test macro.
                            val funName = (decl as DeclTree).parts?.name?.content ?: continue
                            val testName =
                                decl.parts!!.metadataSymbolMap[testSymbol]?.target?.valueContained ?: continue
                            Call {
                                Rn(newBuiltinName)
                                Rn(pairBuiltinName)
                                V(testName)
                                Rn(funName)
                            }
                        }
                    }
                }
                V(vStaySymbol)
                Stay()
                V(vSsaSymbol)
                V(TVoid.value)
            }
        }
    }
}

private val pairBuiltinName = BuiltinName("Pair")
private val runTestCasesParsedName = ParsedName("runTestCases")
private val runTestCasesConnectedValue = Value("::${runTestCasesParsedName.nameText}", TString)
private val testClassParsedName = ParsedName("Test")

// Public on purpose.
// TODO Officially reserve something like "all names beginning with temper__" or some such?
val testReportExportName = ParsedName("temper__testReport")
