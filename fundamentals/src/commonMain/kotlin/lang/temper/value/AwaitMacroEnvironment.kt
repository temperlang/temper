package lang.temper.value

/** A macro environment in which *await* calls can access the current awaiter. */
interface AwaitMacroEnvironment : MacroEnvironment {
    val awaiter: Promises.Awaiter?
}
