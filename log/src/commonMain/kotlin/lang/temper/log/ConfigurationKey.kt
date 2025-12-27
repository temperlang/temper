package lang.temper.log

/**
 * A key which may be used to configure logging as via [LogConfigurations].
 *
 * This must be a suitable key in a weak map; it will be compared by identity, not structurally.
 */
interface ConfigurationKey {
    interface Holder {
        val configurationKey: ConfigurationKey
    }
}
