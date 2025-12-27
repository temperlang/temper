package lang.temper.compile

import ch.qos.logback.classic.BasicConfigurator
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.Configurator.ExecutionStatus

@Suppress("unused") // [LogbackConfigurator] is loaded reflectively by the SLF4J logging framework
class LogbackConfigurator : BasicConfigurator() {
    override fun configure(loggerContext: LoggerContext?): ExecutionStatus {
        // Do nothing. We don't use logback, and we don't want random logging out of it for ordinary situations.
        // We just had unpleasant automatic logging when using JGit, and we don't want that.
        return ExecutionStatus.DO_NOT_INVOKE_NEXT_IF_ANY
    }
}
