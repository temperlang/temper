package lang.temper.be.java

import lang.temper.frontend.BindingsInjector
import lang.temper.frontend.Module
import lang.temper.frontend.interpreterFeatureImplementations
import lang.temper.log.LogSink
import lang.temper.value.BlockTree
import lang.temper.value.InternalFeatureKeys
import lang.temper.value.TFunction
import lang.temper.value.vWordSymbol

object JavaConfigInjector : BindingsInjector {
    override fun inject(module: Module, root: BlockTree, logSink: LogSink) {
        root.document.treeFarm.grow(root.pos) {
            val classMacroValue = interpreterFeatureImplementations[InternalFeatureKeys.Class.featureKey]!!
            val classMacro = TFunction.unpack(classMacroValue)
            Call(classMacro) {
                V(vWordSymbol)
                Rn { nameMaker -> nameMaker.parsedName("JavaConfig")!! }
            }
        }
    }
}
