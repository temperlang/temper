package lang.temper.be.tmpl

import lang.temper.library.LibraryConfiguration
import lang.temper.log.Position
import lang.temper.log.Positioned

/**
 * The pieces needed to build a [TmpL.Module] but where it's still easy
 * to mutate the list of [TmpL.TopLevel]s.
 */
internal data class NascentModule(
    override val pos: Position,
    val codeLocation: TmpL.CodeLocationMetadata,
    val moduleMetadata: TmpL.ModuleMetadata,
    val topLevels: MutableList<TmpL.TopLevel>,
    val result: TmpL.Expression?,
    val translator: TmpLTranslator,
) : Positioned {
    val isTranslationNeeded: Boolean
        get() {
            val sourceModuleName = codeLocation.codeLocation
            return when {
                sourceModuleName.isPreface && topLevels.isEmpty() ->
                    // Most prefaces are empty so do not need to be translated.
                    false
                !sourceModuleName.isPreface &&
                    sourceModuleName.sourceFile.lastOrNull() == LibraryConfiguration.fileName ->
                    // config.temper.md files need not be translated
                    false
                else -> true
            }
        }
}
