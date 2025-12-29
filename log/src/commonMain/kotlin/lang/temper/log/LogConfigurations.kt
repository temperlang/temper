package lang.temper.log

import lang.temper.common.Console
import lang.temper.common.Log
import lang.temper.common.SnapshotKey
import lang.temper.common.console
import lang.temper.common.mutableWeakMapOf
import lang.temper.common.structure.Structured
import kotlin.jvm.Synchronized

open class LogConfigurations internal constructor(
    @Suppress("MemberVisibilityCanBePrivate") val loggerName: String,
    @Suppress("MemberVisibilityCanBePrivate") val parentLoggerName: String?,
    @Suppress("MemberVisibilityCanBePrivate") val childKeys: List<String>,
) {
    private val consoles = mutableWeakMapOf<ConfigurationKey, Config>()

    operator fun invoke(holder: ConfigurationKey.Holder?) = invoke(holder?.configurationKey)
    operator fun invoke(key: ConfigurationKey?): Console {
        if (key == null) {
            return NullConsole
        }
        return getOrMakeConsole(key)
    }

    /**
     * Cause [invoke] with [key] to return [consoleForKey] until a subsequent [configure]
     * call.  This also affects descendants unless/until they have been explicitly configured.
     *
     * This overrides any inherited configuration from an ancestor.
     *
     * If [consoleForKey] is `null` then undoes a previous call to [configure];
     * re-inherit configuration from any parent.
     */
    fun configure(key: ConfigurationKey, consoleForKey: Console?) {
        val config = when {
            consoleForKey != null -> Config(consoleForKey, inherited = false)
            parentLoggerName != null -> {
                val parent = logConfigurationsByName[parentLoggerName]!!
                val parentConfig = parent.configOrNull(key)
                parentConfig?.copy(inherited = true)
            }
            else -> null
        }
        update(key, config, skipIfHasOverride = false)
        val childConfig =
            if (config?.inherited != false) { config } else { config.copy(inherited = true) }
        for (childKey in childKeys) {
            logConfigurationsByName[childKey]?.maybeAdopt(key, childConfig)
        }
    }

    /**
     * Configures the given code location with a console that emits to the standard log channel
     * with the given log level.
     *
     * This overrides any inherited console so that a change to an ancestor will not affect
     * the result of [invoke] with [key].
     */
    fun configure(key: ConfigurationKey, logLevel: Log.LevelFilter) {
        configure(key, Console(console.textOutput, logLevel = logLevel))
    }

    @Synchronized
    private fun getOrMakeConsole(key: ConfigurationKey): Console {
        var config = consoles[key]
        if (config == null) {
            config = Config(defaultConsole, inherited = true)
            maybeAdopt(key, config)
        }
        return config.console
    }

    @Synchronized
    private fun configOrNull(key: ConfigurationKey): Config? = consoles[key]

    private fun maybeAdopt(key: ConfigurationKey, inheritedConfig: Config?) {
        if (update(key, inheritedConfig, skipIfHasOverride = true)) {
            // Propagate to children.
            for (childKey in childKeys) {
                logConfigurationsByName[childKey]?.maybeAdopt(key, inheritedConfig)
            }
        }
    }

    /**
     * Sets the config for the given location.
     *
     * @return false to indicate no work done if there is a local override and [skipIfHasOverride].
     */
    @Synchronized
    private fun update(
        key: ConfigurationKey,
        config: Config?,
        skipIfHasOverride: Boolean,
    ): Boolean {
        if (skipIfHasOverride && consoles[key]?.inherited == false) {
            return false
        }
        if (config == null) {
            consoles.remove(key)
        } else {
            consoles[key] = config
        }
        return true
    }

    companion object {
        private val defaultConsole = Console(NullTextOutput, logLevel = Log.None)
    }

    override fun toString(): String = "(LogConfigurations $loggerName)"
}

fun <IR : Structured> (LogConfigurations).snapshot(
    configurationKey: ConfigurationKey,
    snapshotKey: SnapshotKey<IR>,
    currentState: IR,
) {
    val console = this(configurationKey)
    console.snapshot(snapshotKey, this.loggerName, currentState)
}

fun <IR : Structured> (LogConfigurations).snapshot(
    configurationKeyHolder: ConfigurationKey.Holder,
    snapshotKey: SnapshotKey<IR>,
    currentState: IR,
) = snapshot(
    configurationKey = configurationKeyHolder.configurationKey,
    snapshotKey = snapshotKey,
    currentState = currentState,
)

private data class Config(val console: Console, val inherited: Boolean)
