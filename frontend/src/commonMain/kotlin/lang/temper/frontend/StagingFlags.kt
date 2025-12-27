package lang.temper.frontend

import lang.temper.name.BuiltinName
import lang.temper.name.TemperName
import kotlin.reflect.KVisibility
import kotlin.reflect.full.memberProperties

/**
 * Flags that may be sprinkled in the builtin environment to
 * affect how staging happens.
 *
 * [Module.addEnvironmentBindings] can be used to add a builtin for a flag.
 *
 * Since these are builtins, they can be checked for by passes invoked during
 * [interpretiveDanceStage].
 */
object StagingFlags {
    /**
     * Whether import stage should consider exports from the implicits module
     * when generating a list of implicit imports.
     *
     * This avoids processing the implicits module so comes in handy for testing
     * simple modules without requiring that the implicits module stage validly.
     */
    val skipImportImplicits = BuiltinName("skip-import-implicits")

    /**
     * If the module comes from a REPL session or a snippet of code in
     * documentation then we may want the value of the terminal expression.
     * If this name binds to [lang.temper.value.TBoolean.valueTrue] then
     * we add a path to export that value.
     */
    val moduleResultNeeded = BuiltinName("module-result-needed")

    /**
     * A marker name that is used to tell whether this module is a target of
     * `temper test` which requires that we add some extra instructions for
     * each test harness subclass.
     */
    val defineStageHookCreateAndRunClasses = BuiltinName("instantiate-classes")

    /**
     * Whether `await` is allowed at the top level.
     * This is allowed in the REPL environment (and documentation snippets)
     * but is not something we're sure we can translate so is forbidden in
     * non-REPL contexts.
     */
    val allowTopLevelAwait = BuiltinName("top-level-await")

    /**
     * Whether to halt processing during the define stage just before mixins would
     * be re-incorporated into the module.
     * This allows subsidiary modules which just bring mixin content up to the
     * same level of processing as the destination module before having their
     * content grafted back into the destination module.
     */
    val haltBeforeMixingIn = BuiltinName("halt-before-mixing-in")

    private val allFlagsCached = lazy {
        buildSet {
            StagingFlags::class.memberProperties.mapNotNullTo(this) {
                if (it.visibility == KVisibility.PUBLIC) {
                    it.get(this@StagingFlags) as? TemperName
                } else {
                    null
                }
            }
        }
    }
    fun allFlags() = allFlagsCached.value
}
