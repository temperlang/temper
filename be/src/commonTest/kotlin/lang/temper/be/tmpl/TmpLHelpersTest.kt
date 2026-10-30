package lang.temper.be.tmpl

import lang.temper.be.Backend
import lang.temper.be.BackendSetup
import lang.temper.be.Dependencies
import lang.temper.be.NullDependencyResolver
import lang.temper.be.inputFileMapFromJson
import lang.temper.common.currents.makeCancelGroupForTest
import lang.temper.frontend.Module
import lang.temper.fs.NullSystemAccess
import lang.temper.fs.OutDir
import lang.temper.library.LibraryConfigurations
import lang.temper.log.dirPath
import lang.temper.name.ResolvedParsedName
import lang.temper.name.TemperName
import kotlin.test.Test
import kotlin.test.assertEquals

class TmpLHelpersTest {
    @Test
    fun isPlainToString() {
        val found = typeDeclarations(
            """
                |{
                |  foo: {
                |    foo.temper: ```
                |      export class Plain {
                |        public toString(): String { "Plain" }
                |      }
                |      export class WithArg {
                |        public toString(x: Int): String { "WithArg" }
                |      }
                |      export class Private {
                |        private toString(): String { "Private" }
                |      }
                |      export class Static {
                |        public static toString(): String { "Static" }
                |      }
                |      export class Generic {
                |        public toString<T>(): String { "Generic" }
                |      }
                |      export class NotString {
                |        public toString(): Int { 1 }
                |      }
                |      export class Other {
                |        public show(): String { "Other" }
                |      }
                |      export interface Iface {
                |        public toString(): String { "Iface" }
                |      }
                |      ```
                |  }
                |}
            """.trimMargin(),
        )
        assertEquals(
            mapOf(
                "Plain" to true,
                "WithArg" to false,
                "Private" to false,
                "Static" to false,
                "Generic" to false,
                "NotString" to false,
                "Other" to false,
                "Iface" to false,
            ),
            found.mapValues { (_, decl) -> decl.members.any { it.isPlainToString() } },
        )
    }

    /** Translates [inputJson] to TmpL and returns its type declarations by name. */
    private fun typeDeclarations(inputJson: String): Map<String, TmpL.TypeDeclaration> {
        val found = mutableMapOf<String, TmpL.TypeDeclaration>()
        object : TestCompiler(inputFileMapFromJson(inputJson)) {
            override fun backend(
                libraryConfigurations: LibraryConfigurations,
                modules: List<Module>,
                outDir: OutDir,
            ): Backend<*> {
                val cancelGroup = makeCancelGroupForTest()
                return object : TestBackend(
                    defaultTestSupportNetwork,
                    BackendSetup(
                        libraryConfigurations.currentLibraryConfiguration.libraryName,
                        Dependencies.Builder(libraryConfigurations.toBundle()),
                        modules,
                        outDir.systemAccess(cancelGroup),
                        NullSystemAccess(dirPath(), cancelGroup),
                        logSink,
                        NullDependencyResolver,
                        Backend.Config.bundled,
                    ),
                ) {
                    override fun translate(finished: TmpL.ModuleSet): List<OutputFileSpecification> {
                        for (module in finished.modules) {
                            for (topLevel in module.topLevels) {
                                if (topLevel is TmpL.TypeDeclaration) {
                                    found[topLevel.name.name.displayName()] = topLevel
                                }
                            }
                        }
                        return super.translate(finished)
                    }
                }
            }

            override fun onBackendsComplete(outputRoot: OutDir) = Unit
        }.compile()
        return found
    }
}

private fun TemperName.displayName(): String =
    (this as? ResolvedParsedName)?.baseName?.nameText ?: toString()
