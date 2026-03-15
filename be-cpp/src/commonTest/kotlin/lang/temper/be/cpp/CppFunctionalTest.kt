package lang.temper.be.cpp

import lang.temper.be.FunctionalTestRunner
import lang.temper.be.assertRunOutput
import lang.temper.be.assertTestingTest
import lang.temper.be.cli.CliEnv
import lang.temper.be.cli.ShellPreferences
import lang.temper.be.cli.ToolchainRequest
import lang.temper.be.cli.cliEnvImplemented
import lang.temper.be.cli.print
import lang.temper.common.console
import lang.temper.frontend.Module
import lang.temper.fs.OutDir
import lang.temper.fs.OutputRoot
import lang.temper.log.FilePath
import lang.temper.name.ModuleName
import lang.temper.tests.Disposition.Run
import lang.temper.tests.Ft
import lang.temper.tests.FunctionalTestBase
import lang.temper.tests.FunctionalTests
import kotlin.test.Test

class CppFunctionalTest :
    FunctionalTestRunner<CppBackend>(CppBackend.Cpp11) {
    @Test
    override fun algosHelloWorld() {
        runFunctionalTest(FunctionalTests.AlgosHelloWorld, runOverride = Run)
    }

    // Force-run all tests to discover which ones pass
    @Test override fun algosFibonacci() =
        runFunctionalTest(Ft.AlgosFibonacci, runOverride = Run)

    @Test override fun algosHelloFromClassToTop() =
        runFunctionalTest(Ft.AlgosHelloFromClassToTop, runOverride = Run)

    @Test override fun algosHelloWorldObject() =
        runFunctionalTest(Ft.AlgosHelloWorldObject, runOverride = Run)

    @Test override fun algosMyersDiff() =
        runFunctionalTest(Ft.AlgosMyersDiff, runOverride = Run)

    @Test override fun classesDirectGetter() =
        runFunctionalTest(Ft.ClassesDirectGetter, runOverride = Run)

    @Test override fun classesPropertyOrder() =
        runFunctionalTest(Ft.ClassesPropertyOrder, runOverride = Run)

    @Test override fun controlFlowIfReturn() =
        runFunctionalTest(Ft.ControlFlowIfReturn, runOverride = Run)

    @Test override fun controlFlowLoops() =
        runFunctionalTest(Ft.ControlFlowLoops, runOverride = Run)

    @Test override fun functionsSimpleLocals() =
        runFunctionalTest(Ft.FunctionsSimpleLocals, runOverride = Run)

    @Test override fun functionsDefaulting() =
        runFunctionalTest(Ft.FunctionsDefaulting, runOverride = Run)

    @Test override fun functionsNamedArgs() =
        runFunctionalTest(Ft.FunctionsNamedArgs, runOverride = Run)

    @Test override fun interfacesEmpty() =
        runFunctionalTest(Ft.InterfacesEmpty, runOverride = Run)

    @Test override fun regressionMinimalRepro() =
        runFunctionalTest(Ft.RegressionMinimalRepro, runOverride = Run)

    @Test override fun semanticsBroken() =
        runFunctionalTest(Ft.SemanticsBroken, runOverride = Run)

    @Test override fun semanticsConstness() =
        runFunctionalTest(Ft.SemanticsConstness, runOverride = Run)

    @Test override fun typesIntBasics() =
        runFunctionalTest(Ft.TypesIntBasics, runOverride = Run)

    @Test override fun typesIntLimits() =
        runFunctionalTest(Ft.TypesIntLimits, runOverride = Run)

    @Test override fun typesFloatBasics() =
        runFunctionalTest(Ft.TypesFloatBasics, runOverride = Run)

    @Test override fun typesFloatOps() =
        runFunctionalTest(Ft.TypesFloatOps, runOverride = Run)

    @Test override fun typesListEmpty() =
        runFunctionalTest(Ft.TypesListEmpty, runOverride = Run)

    @Test override fun typesStringIsEmpty() =
        runFunctionalTest(Ft.TypesStringIsEmpty, runOverride = Run)

    @Test override fun typesStringRead() =
        runFunctionalTest(Ft.TypesStringRead, runOverride = Run)

    @Test override fun testingAsserts() =
        runFunctionalTest(Ft.TestingAsserts, runOverride = Run)

    // Additional tests to discover which ones pass
    @Test override fun castsAsExpr() =
        runFunctionalTest(Ft.CastsAsExpr, runOverride = Run)

    @Test override fun castsSpecific() =
        runFunctionalTest(Ft.CastsSpecific, runOverride = Run)

    @Test override fun classesAngleCall() =
        runFunctionalTest(Ft.ClassesAngleCall, runOverride = Run)

    @Test override fun classesCallOverrideFromSubtype() =
        runFunctionalTest(Ft.ClassesCallOverrideFromSubtype, runOverride = Run)

    @Test override fun classesInheritedGetter() =
        runFunctionalTest(Ft.ClassesInheritedGetter, runOverride = Run)

    @Test override fun classesObjectLiterals() =
        runFunctionalTest(Ft.ClassesObjectLiterals, runOverride = Run)

    @Test override fun classesPrivateMethod() =
        runFunctionalTest(Ft.ClassesPrivateMethod, runOverride = Run)

    @Test override fun classesSetters() =
        runFunctionalTest(Ft.ClassesSetters, runOverride = Run)

    @Test override fun classesStaticProperties() =
        runFunctionalTest(Ft.ClassesStaticProperties, runOverride = Run)

    @Test override fun classesStaticPropertiesScope() =
        runFunctionalTest(Ft.ClassesStaticPropertiesScope, runOverride = Run)

    @Test override fun controlFlowActorRun() =
        runFunctionalTest(Ft.ControlFlowActorRun, runOverride = Run)

    @Test override fun controlFlowAsync() =
        runFunctionalTest(Ft.ControlFlowAsync, runOverride = Run)

    @Test override fun controlFlowBubble() =
        runFunctionalTest(Ft.ControlFlowBubble, runOverride = Run)

    @Test override fun controlFlowLoopReenterable() =
        runFunctionalTest(Ft.ControlFlowLoopReenterable, runOverride = Run)

    @Test override fun functionsAsValues() =
        runFunctionalTest(Ft.FunctionsAsValues, runOverride = Run)

    @Test override fun functionsConstructorCallbacks() =
        runFunctionalTest(Ft.FunctionsConstructorCallbacks, runOverride = Run)

    @Test override fun functionsLocals() =
        runFunctionalTest(Ft.FunctionsLocals, runOverride = Run)

    @Test override fun functionsRestFormal() =
        runFunctionalTest(Ft.FunctionsRestFormal, runOverride = Run)

    @Test override fun importsFunctions() =
        runFunctionalTest(Ft.ImportsFunctions, runOverride = Run)

    @Test override fun importsTypes() =
        runFunctionalTest(Ft.ImportsTypes, runOverride = Run)

    @Test override fun importsValues() =
        runFunctionalTest(Ft.ImportsValues, runOverride = Run)

    @Test override fun interfacesPropertyMembers() =
        runFunctionalTest(Ft.InterfacesPropertyMembers, runOverride = Run)

    @Test override fun interfacesPureVirtual() =
        runFunctionalTest(Ft.InterfacesPureVirtual, runOverride = Run)

    @Test override fun namesNonascii() =
        runFunctionalTest(Ft.NamesNonascii, runOverride = Run)

    @Test override fun regexMatch() =
        runFunctionalTest(Ft.RegexMatch, runOverride = Run)

    @Test override fun regexZeroAdvance() =
        runFunctionalTest(Ft.RegexZeroAdvance, runOverride = Run)

    @Test override fun semanticsMutuallyReferencingTypes() =
        runFunctionalTest(Ft.SemanticsMutuallyReferencingTypes, runOverride = Run)

    @Test override fun semanticsTypeCheckedLocals() =
        runFunctionalTest(Ft.SemanticsTypeCheckedLocals, runOverride = Run)

    @Test override fun typesDate() =
        runFunctionalTest(Ft.TypesDate, runOverride = Run)

    @Test override fun typesDenseBitVector() =
        runFunctionalTest(Ft.TypesDenseBitVector, runOverride = Run)

    @Test override fun typesDeque() =
        runFunctionalTest(Ft.TypesDeque, runOverride = Run)

    @Test override fun typesJsonSyntaxTree() =
        runFunctionalTest(Ft.TypesJsonSyntaxTree, runOverride = Run)

    @Test override fun typesListOperations() =
        runFunctionalTest(Ft.TypesListOperations, runOverride = Run)

    @Test override fun typesListReduce() =
        runFunctionalTest(Ft.TypesListReduce, runOverride = Run)

    @Test override fun typesListSorting() =
        runFunctionalTest(Ft.TypesListSorting, runOverride = Run)

    @Test override fun typesMap() =
        runFunctionalTest(Ft.TypesMap, runOverride = Run)

    @Test override fun typesNetresponse() =
        runFunctionalTest(Ft.TypesNetresponse, runOverride = Run)

    @Test override fun typesStringBuild() =
        runFunctionalTest(Ft.TypesStringBuild, runOverride = Run)

    @Test override fun typesStringIndices() =
        runFunctionalTest(Ft.TypesStringIndices, runOverride = Run)

    override fun runGeneratedCode(
        backend: CppBackend,
        modules: List<Module>,
        outputRoot: OutputRoot,
        outputDir: OutDir,
        outputPaths: Map<ModuleName, FilePath>,
        test: FunctionalTestBase,
        request: ToolchainRequest,
    ) {
        if (!cliEnvImplemented) {
            return
        }

        val shellPreferences = ShellPreferences.functionalTests(console)

        CliEnv.using(Cpp11Specifics, shellPreferences, backend.cancelGroup) {
            copyOutputDir(outputRoot, FilePath.emptyPath)
            copyCppTemperCore(factory)
            val specifics = specifics as Cpp11Specifics
            val result = specifics.runBestEffort(
                cliEnv = this,
                request = request,
                code = outputRoot,
                dependencies = backend.getDependencies(),
            ).first().result
            var pass = false
            try {
                if (test.runAsTest) {
                    assertTestingTest(test, result)
                } else {
                    test.assertRunOutput(result)
                }
                pass = true
            } finally {
                if (!pass) {
                    dumpModuleBodies(modules)
                    // Print stderr from g++ compilation
                    val effort = result.failure?.effort
                    System.err.println("## effort type: ${effort?.let { it::class.simpleName }}")
                    if (effort is lang.temper.be.cli.EffortSuccess) {
                        val stderr = effort.auxOut[lang.temper.be.cli.Aux.Stderr]
                        System.err.println("## g++ stderr:\n$stderr")
                    }
                    // Write debug info to file for troubleshooting
                    val debugInfo = buildString {
                        appendLine("## effort type: ${effort?.let { it::class.simpleName }}")
                        appendLine("## result: $result")
                        appendLine("## failure: ${result.failure}")
                        if (result.failure != null) {
                            appendLine("## failure effort: ${result.failure?.effort}")
                        }
                    }
                    java.io.File("/tmp/cpp-debug.txt").writeText(debugInfo)
                    result.print(console, asError = true)
                }
            }
        }
    }
}
