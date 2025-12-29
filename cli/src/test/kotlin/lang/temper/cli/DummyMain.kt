package lang.temper.cli

import kotlinx.cli.ExperimentalCli

/** For tests to validate that a subcommand exits properly. */
@ExperimentalCli
open class DummyMain : Main() {
    override fun exitProcess(exitCode: Int): Nothing = throw when (exitCode) {
        0 -> Fake0Exit()
        1 -> Fake1Exit()
        else -> FakeExit(exitCode)
    }
}

open class FakeExit(exitCode: Int) : Throwable("Exited with $exitCode")
class Fake0Exit : FakeExit(0)
class Fake1Exit : FakeExit(1)
