package lang.temper.be.java

import lang.temper.frontend.BindingsInjector
import lang.temper.frontend.Module
import lang.temper.log.LogSink
import lang.temper.value.BlockTree

object JavaConfigInjector : BindingsInjector {
    override fun inject(module: Module, root: BlockTree, logSink: LogSink) {
        module.console
    }
}
