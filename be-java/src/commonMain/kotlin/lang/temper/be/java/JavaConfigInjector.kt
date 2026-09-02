package lang.temper.be.java

import lang.temper.builtin.BuiltinFuns
import lang.temper.frontend.BindingsInjector
import lang.temper.frontend.Module
import lang.temper.frontend.interpreterFeatureImplementations
import lang.temper.interp.MetadataDecorator
import lang.temper.interp.importExport.ExportDecorator
import lang.temper.log.LogSink
import lang.temper.name.ParsedName
import lang.temper.value.BlockTree
import lang.temper.value.InternalFeatureKeys
import lang.temper.value.TFunction
import lang.temper.value.Value
import lang.temper.value.complexArgSymbol
import lang.temper.value.publicSymbol
import lang.temper.value.typeSymbol
import lang.temper.value.vWordSymbol
import lang.temper.value.visibilitySymbol

object JavaConfigInjector : BindingsInjector {
    override fun inject(module: Module, root: BlockTree, logSink: LogSink) {
        root.insert {
            // Using the value directly here doesn't work.
            // Call(ExportDecorator) { ... }
            Call {
                Rn(ParsedName(ExportDecorator.name))
                Call(classMacro) {
                    V(vWordSymbol)
                    Ln(ParsedName("JavaConfig2"))
                    Call(publicDecorator) {
                        Block {
                            V(complexArgSymbol)
                            Rn(ParsedName("package"))
                            V(typeSymbol)
                            Call(BuiltinFuns.vOrNullFn) {
                                Rn(ParsedName("String"))
                            }
                        }
                    }
                    Fn { Block {} }
                }
            }
        }
    }

    private val classMacro = TFunction.unpack(interpreterFeatureImplementations[InternalFeatureKeys.Class.featureKey]!!)
    private val publicDecorator = MetadataDecorator(visibilitySymbol, "@public") { Value(publicSymbol) }
}
