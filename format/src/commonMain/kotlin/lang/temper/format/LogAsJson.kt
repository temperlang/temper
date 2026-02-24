package lang.temper.format

expect val logAsJson: Boolean

expect fun <T> withLogAsJson(logAsJson: Boolean, body: () -> T): T
