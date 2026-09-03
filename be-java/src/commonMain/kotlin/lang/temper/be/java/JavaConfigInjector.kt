package lang.temper.be.java

import lang.temper.builtin.BuiltinFuns
import lang.temper.builtin.Types
import lang.temper.frontend.BindingsInjector
import lang.temper.frontend.Module
import lang.temper.frontend.interpreterFeatureImplementations
import lang.temper.interp.MetadataDecorator
import lang.temper.interp.importExport.ExportDecorator
import lang.temper.interp.imuDecorator
import lang.temper.log.LogSink
import lang.temper.name.ParsedName
import lang.temper.value.BlockTree
import lang.temper.value.InternalFeatureKeys
import lang.temper.value.Planting
import lang.temper.value.TFunction
import lang.temper.value.TNull
import lang.temper.value.Value
import lang.temper.value.complexArgSymbol
import lang.temper.value.defaultSymbol
import lang.temper.value.publicSymbol
import lang.temper.value.typeSymbol
import lang.temper.value.vWordSymbol
import lang.temper.value.visibilitySymbol

object JavaConfigInjector : BindingsInjector {
    override fun inject(module: Module, root: BlockTree, logSink: LogSink) {
        root.insert {
            buildConfigType(
                name = "JavaConfig",
                properties = mapOf(
                    "package" to {
                        V(Value(Types.string))
                    },
                    "dependencies" to {
                        Call(BuiltinFuns.angleFn) {
                            V(Value(Types.list))
                            V(Value(Types.string))
                        }
                    },
                ),
            )
        }
    }
}

/** Builds an imu data class with nullable constructor properties only. */
private fun Planting.buildConfigType(
    name: String,
    properties: Map<String, Planting.() -> Any?>,
) {
    Call {
        Rn(ParsedName(imuDecorator.name))
        // Using the value directly here doesn't work.
        // Call(ExportDecorator) { ... }
        Call {
            Rn(ParsedName(ExportDecorator.name))
            Call(classMacro) {
                V(vWordSymbol)
                Ln(ParsedName(name))
                for ((propName, planter) in properties) {
                    Call(publicDecorator) {
                        Block {
                            V(complexArgSymbol)
                            Rn(ParsedName(propName))
                            V(typeSymbol)
                            Call(BuiltinFuns.vOrNullFn, children = planter)
                            V(defaultSymbol)
                            V(TNull.value)
                        }
                    }
                }
                Fn { Block {} }
            }
        }
    }
}

// Cache these values.
private val classMacro = TFunction.unpack(interpreterFeatureImplementations[InternalFeatureKeys.Class.featureKey]!!)
private val publicDecorator = MetadataDecorator(visibilitySymbol, "@public") { Value(publicSymbol) }
