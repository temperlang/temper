@file:lang.temper.common.Generated("LoggerNameRegistryGenerator")
@file:Suppress("ktlint")

package lang.temper.log

/**
 * Allow fine or coarse grained logging by fetching a
 * [Console][lang.temper.common.Console] associated with a [CodeLocation] but
 * filtered by location specific preferences around what to log via syntax like
 *
 *     Debug.Frontend.TypeStage(location).log(message)
 *
 * # Tiers
 *
 * Logging is configured by tiers.
 * Tier 0 is broad component logging:
 * - `frontend`
 * - `backend`
 * - `docgen`
 *
 * Tier 1 is more fine-grained, for example `frontend.typeStage`, and unless
 * specifically configured the logging for `frontend.typeStage` falls back to that
 * for `frontend`.
 *
 * Tier 2 is even more fine-grained, and has an extra layer of dots, etc.
 *
 * See `logger-names.json` which defines the logger names.
 *
 * Logger names may be introspected over via [logConfigurationsByName]
 */
object Debug : LogConfigurations("*", null, listOf("frontend", "backend", "docgen")) {
    object Frontend : LogConfigurations("frontend", "*", listOf("frontend.lexStage", "frontend.parseStage", "frontend.importStage", "frontend.disAmbiguateStage", "frontend.syntaxMacroStage", "frontend.defineStage", "frontend.typeStage", "frontend.functionMacroStage", "frontend.exportStage", "frontend.queryStage", "frontend.generateCodeStage")) {
        object LexStage : LogConfigurations("frontend.lexStage", "frontend", listOf("frontend.lexStage.before", "frontend.lexStage.after")) {
            object Before : LogConfigurations("frontend.lexStage.before", "frontend.lexStage", listOf())
            object After : LogConfigurations("frontend.lexStage.after", "frontend.lexStage", listOf())
        }
        object ParseStage : LogConfigurations("frontend.parseStage", "frontend", listOf("frontend.parseStage.before", "frontend.parseStage.after")) {
            object Before : LogConfigurations("frontend.parseStage.before", "frontend.parseStage", listOf())
            object After : LogConfigurations("frontend.parseStage.after", "frontend.parseStage", listOf())
        }
        object ImportStage : LogConfigurations("frontend.importStage", "frontend", listOf("frontend.importStage.before", "frontend.importStage.after")) {
            object Before : LogConfigurations("frontend.importStage.before", "frontend.importStage", listOf())
            object After : LogConfigurations("frontend.importStage.after", "frontend.importStage", listOf())
        }
        object DisAmbiguateStage : LogConfigurations("frontend.disAmbiguateStage", "frontend", listOf("frontend.disAmbiguateStage.before", "frontend.disAmbiguateStage.after")) {
            object Before : LogConfigurations("frontend.disAmbiguateStage.before", "frontend.disAmbiguateStage", listOf())
            object After : LogConfigurations("frontend.disAmbiguateStage.after", "frontend.disAmbiguateStage", listOf())
        }
        object SyntaxMacroStage : LogConfigurations("frontend.syntaxMacroStage", "frontend", listOf("frontend.syntaxMacroStage.before", "frontend.syntaxMacroStage.afterInterpretation", "frontend.syntaxMacroStage.after")) {
            object Before : LogConfigurations("frontend.syntaxMacroStage.before", "frontend.syntaxMacroStage", listOf())
            object AfterInterpretation : LogConfigurations("frontend.syntaxMacroStage.afterInterpretation", "frontend.syntaxMacroStage", listOf())
            object After : LogConfigurations("frontend.syntaxMacroStage.after", "frontend.syntaxMacroStage", listOf())
        }
        object DefineStage : LogConfigurations("frontend.defineStage", "frontend", listOf("frontend.defineStage.before", "frontend.defineStage.afterInterpretation", "frontend.defineStage.afterInheritPropertyReassignability", "frontend.defineStage.desugarDotOperations", "frontend.defineStage.afterDesugarDotOperations", "frontend.defineStage.linkThis", "frontend.defineStage.afterLinkThis", "frontend.defineStage.convertClasses", "frontend.defineStage.afterConvertClasses", "frontend.defineStage.afterAddTemperTestInstructions", "frontend.defineStage.simplifyDeclarations", "frontend.defineStage.afterSimplifyDeclarations", "frontend.defineStage.convertObjectSyntax", "frontend.defineStage.after")) {
            object Before : LogConfigurations("frontend.defineStage.before", "frontend.defineStage", listOf())
            object AfterInterpretation : LogConfigurations("frontend.defineStage.afterInterpretation", "frontend.defineStage", listOf())
            object AfterInheritPropertyReassignability : LogConfigurations("frontend.defineStage.afterInheritPropertyReassignability", "frontend.defineStage", listOf())
            object DesugarDotOperations : LogConfigurations("frontend.defineStage.desugarDotOperations", "frontend.defineStage", listOf())
            object AfterDesugarDotOperations : LogConfigurations("frontend.defineStage.afterDesugarDotOperations", "frontend.defineStage", listOf())
            object LinkThis : LogConfigurations("frontend.defineStage.linkThis", "frontend.defineStage", listOf())
            object AfterLinkThis : LogConfigurations("frontend.defineStage.afterLinkThis", "frontend.defineStage", listOf())
            object ConvertClasses : LogConfigurations("frontend.defineStage.convertClasses", "frontend.defineStage", listOf())
            object AfterConvertClasses : LogConfigurations("frontend.defineStage.afterConvertClasses", "frontend.defineStage", listOf())
            object AfterAddTemperTestInstructions : LogConfigurations("frontend.defineStage.afterAddTemperTestInstructions", "frontend.defineStage", listOf())
            object SimplifyDeclarations : LogConfigurations("frontend.defineStage.simplifyDeclarations", "frontend.defineStage", listOf())
            object AfterSimplifyDeclarations : LogConfigurations("frontend.defineStage.afterSimplifyDeclarations", "frontend.defineStage", listOf())
            object ConvertObjectSyntax : LogConfigurations("frontend.defineStage.convertObjectSyntax", "frontend.defineStage", listOf())
            object After : LogConfigurations("frontend.defineStage.after", "frontend.defineStage", listOf())
        }
        object TypeStage : LogConfigurations("frontend.typeStage", "frontend", listOf("frontend.typeStage.before", "frontend.typeStage.beforeInterpretation", "frontend.typeStage.afterInterpretation", "frontend.typeStage.magicSecurityDust", "frontend.typeStage.afterSprinkle", "frontend.typeStage.weaver", "frontend.typeStage.afterWeave", "frontend.typeStage.simplifyFlow", "frontend.typeStage.afterSimplifyFlow", "frontend.typeStage.makeResultsExplicit", "frontend.typeStage.afterExplicitResults", "frontend.typeStage.type", "frontend.typeStage.afterTyper", "frontend.typeStage.useBeforeInit", "frontend.typeStage.afterUseBeforeInit", "frontend.typeStage.reorderArgs", "frontend.typeStage.afterReorderArgs", "frontend.typeStage.simplifyFlow2", "frontend.typeStage.afterSimplifyFlow2", "frontend.typeStage.cleanupTemporaries", "frontend.typeStage.afterCleanupTemporaries", "frontend.typeStage.simplifyFlow3", "frontend.typeStage.afterSimplifyFlow3", "frontend.typeStage.repairUnrealizedGoals", "frontend.typeStage.afterRepairUnrealizedGoals", "frontend.typeStage.after")) {
            object Before : LogConfigurations("frontend.typeStage.before", "frontend.typeStage", listOf())
            object BeforeInterpretation : LogConfigurations("frontend.typeStage.beforeInterpretation", "frontend.typeStage", listOf())
            object AfterInterpretation : LogConfigurations("frontend.typeStage.afterInterpretation", "frontend.typeStage", listOf())
            object MagicSecurityDust : LogConfigurations("frontend.typeStage.magicSecurityDust", "frontend.typeStage", listOf())
            object AfterSprinkle : LogConfigurations("frontend.typeStage.afterSprinkle", "frontend.typeStage", listOf())
            object Weaver : LogConfigurations("frontend.typeStage.weaver", "frontend.typeStage", listOf())
            object AfterWeave : LogConfigurations("frontend.typeStage.afterWeave", "frontend.typeStage", listOf())
            object SimplifyFlow : LogConfigurations("frontend.typeStage.simplifyFlow", "frontend.typeStage", listOf())
            object AfterSimplifyFlow : LogConfigurations("frontend.typeStage.afterSimplifyFlow", "frontend.typeStage", listOf())
            object MakeResultsExplicit : LogConfigurations("frontend.typeStage.makeResultsExplicit", "frontend.typeStage", listOf())
            object AfterExplicitResults : LogConfigurations("frontend.typeStage.afterExplicitResults", "frontend.typeStage", listOf())
            object Type : LogConfigurations("frontend.typeStage.type", "frontend.typeStage", listOf())
            object AfterTyper : LogConfigurations("frontend.typeStage.afterTyper", "frontend.typeStage", listOf())
            object UseBeforeInit : LogConfigurations("frontend.typeStage.useBeforeInit", "frontend.typeStage", listOf())
            object AfterUseBeforeInit : LogConfigurations("frontend.typeStage.afterUseBeforeInit", "frontend.typeStage", listOf())
            object ReorderArgs : LogConfigurations("frontend.typeStage.reorderArgs", "frontend.typeStage", listOf())
            object AfterReorderArgs : LogConfigurations("frontend.typeStage.afterReorderArgs", "frontend.typeStage", listOf())
            object SimplifyFlow2 : LogConfigurations("frontend.typeStage.simplifyFlow2", "frontend.typeStage", listOf())
            object AfterSimplifyFlow2 : LogConfigurations("frontend.typeStage.afterSimplifyFlow2", "frontend.typeStage", listOf())
            object CleanupTemporaries : LogConfigurations("frontend.typeStage.cleanupTemporaries", "frontend.typeStage", listOf())
            object AfterCleanupTemporaries : LogConfigurations("frontend.typeStage.afterCleanupTemporaries", "frontend.typeStage", listOf())
            object SimplifyFlow3 : LogConfigurations("frontend.typeStage.simplifyFlow3", "frontend.typeStage", listOf())
            object AfterSimplifyFlow3 : LogConfigurations("frontend.typeStage.afterSimplifyFlow3", "frontend.typeStage", listOf())
            object RepairUnrealizedGoals : LogConfigurations("frontend.typeStage.repairUnrealizedGoals", "frontend.typeStage", listOf())
            object AfterRepairUnrealizedGoals : LogConfigurations("frontend.typeStage.afterRepairUnrealizedGoals", "frontend.typeStage", listOf())
            object After : LogConfigurations("frontend.typeStage.after", "frontend.typeStage", listOf())
        }
        object FunctionMacroStage : LogConfigurations("frontend.functionMacroStage", "frontend", listOf("frontend.functionMacroStage.before", "frontend.functionMacroStage.after")) {
            object Before : LogConfigurations("frontend.functionMacroStage.before", "frontend.functionMacroStage", listOf())
            object After : LogConfigurations("frontend.functionMacroStage.after", "frontend.functionMacroStage", listOf())
        }
        object ExportStage : LogConfigurations("frontend.exportStage", "frontend", listOf("frontend.exportStage.before", "frontend.exportStage.after", "frontend.exportStage.exports")) {
            object Before : LogConfigurations("frontend.exportStage.before", "frontend.exportStage", listOf())
            object After : LogConfigurations("frontend.exportStage.after", "frontend.exportStage", listOf())
            object Exports : LogConfigurations("frontend.exportStage.exports", "frontend.exportStage", listOf())
        }
        object QueryStage : LogConfigurations("frontend.queryStage", "frontend", listOf("frontend.queryStage.before", "frontend.queryStage.after")) {
            object Before : LogConfigurations("frontend.queryStage.before", "frontend.queryStage", listOf())
            object After : LogConfigurations("frontend.queryStage.after", "frontend.queryStage", listOf())
        }
        object GenerateCodeStage : LogConfigurations("frontend.generateCodeStage", "frontend", listOf("frontend.generateCodeStage.before", "frontend.generateCodeStage.beforeInterpretation", "frontend.generateCodeStage.afterInterpretation", "frontend.generateCodeStage.magicSecurityDust", "frontend.generateCodeStage.afterSprinkle", "frontend.generateCodeStage.weaver", "frontend.generateCodeStage.afterWeave", "frontend.generateCodeStage.type", "frontend.generateCodeStage.afterTyper", "frontend.generateCodeStage.typeCheck", "frontend.generateCodeStage.afterTypeCheck", "frontend.generateCodeStage.simplifyFlow", "frontend.generateCodeStage.afterSimplifyFlow", "frontend.generateCodeStage.cleanupTemporaries", "frontend.generateCodeStage.afterCleanupTemporaries", "frontend.generateCodeStage.simplifyFlow2", "frontend.generateCodeStage.after")) {
            object Before : LogConfigurations("frontend.generateCodeStage.before", "frontend.generateCodeStage", listOf())
            object BeforeInterpretation : LogConfigurations("frontend.generateCodeStage.beforeInterpretation", "frontend.generateCodeStage", listOf())
            object AfterInterpretation : LogConfigurations("frontend.generateCodeStage.afterInterpretation", "frontend.generateCodeStage", listOf())
            object MagicSecurityDust : LogConfigurations("frontend.generateCodeStage.magicSecurityDust", "frontend.generateCodeStage", listOf())
            object AfterSprinkle : LogConfigurations("frontend.generateCodeStage.afterSprinkle", "frontend.generateCodeStage", listOf())
            object Weaver : LogConfigurations("frontend.generateCodeStage.weaver", "frontend.generateCodeStage", listOf())
            object AfterWeave : LogConfigurations("frontend.generateCodeStage.afterWeave", "frontend.generateCodeStage", listOf())
            object Type : LogConfigurations("frontend.generateCodeStage.type", "frontend.generateCodeStage", listOf())
            object AfterTyper : LogConfigurations("frontend.generateCodeStage.afterTyper", "frontend.generateCodeStage", listOf())
            object TypeCheck : LogConfigurations("frontend.generateCodeStage.typeCheck", "frontend.generateCodeStage", listOf())
            object AfterTypeCheck : LogConfigurations("frontend.generateCodeStage.afterTypeCheck", "frontend.generateCodeStage", listOf())
            object SimplifyFlow : LogConfigurations("frontend.generateCodeStage.simplifyFlow", "frontend.generateCodeStage", listOf())
            object AfterSimplifyFlow : LogConfigurations("frontend.generateCodeStage.afterSimplifyFlow", "frontend.generateCodeStage", listOf())
            object CleanupTemporaries : LogConfigurations("frontend.generateCodeStage.cleanupTemporaries", "frontend.generateCodeStage", listOf())
            object AfterCleanupTemporaries : LogConfigurations("frontend.generateCodeStage.afterCleanupTemporaries", "frontend.generateCodeStage", listOf())
            object SimplifyFlow2 : LogConfigurations("frontend.generateCodeStage.simplifyFlow2", "frontend.generateCodeStage", listOf())
            object After : LogConfigurations("frontend.generateCodeStage.after", "frontend.generateCodeStage", listOf())
        }
    }
    object Backend : LogConfigurations("backend", "*", listOf())
    object Docgen : LogConfigurations("docgen", "*", listOf())
}

val logConfigurationsByName: Map<String, LogConfigurations> = mapOf(
    "*" to Debug,
    "frontend" to Debug.Frontend,
    "frontend.lexStage" to Debug.Frontend.LexStage,
    "frontend.lexStage.before" to Debug.Frontend.LexStage.Before,
    "frontend.lexStage.after" to Debug.Frontend.LexStage.After,
    "frontend.parseStage" to Debug.Frontend.ParseStage,
    "frontend.parseStage.before" to Debug.Frontend.ParseStage.Before,
    "frontend.parseStage.after" to Debug.Frontend.ParseStage.After,
    "frontend.importStage" to Debug.Frontend.ImportStage,
    "frontend.importStage.before" to Debug.Frontend.ImportStage.Before,
    "frontend.importStage.after" to Debug.Frontend.ImportStage.After,
    "frontend.disAmbiguateStage" to Debug.Frontend.DisAmbiguateStage,
    "frontend.disAmbiguateStage.before" to Debug.Frontend.DisAmbiguateStage.Before,
    "frontend.disAmbiguateStage.after" to Debug.Frontend.DisAmbiguateStage.After,
    "frontend.syntaxMacroStage" to Debug.Frontend.SyntaxMacroStage,
    "frontend.syntaxMacroStage.before" to Debug.Frontend.SyntaxMacroStage.Before,
    "frontend.syntaxMacroStage.afterInterpretation" to Debug.Frontend.SyntaxMacroStage.AfterInterpretation,
    "frontend.syntaxMacroStage.after" to Debug.Frontend.SyntaxMacroStage.After,
    "frontend.defineStage" to Debug.Frontend.DefineStage,
    "frontend.defineStage.before" to Debug.Frontend.DefineStage.Before,
    "frontend.defineStage.afterInterpretation" to Debug.Frontend.DefineStage.AfterInterpretation,
    "frontend.defineStage.afterInheritPropertyReassignability" to Debug.Frontend.DefineStage.AfterInheritPropertyReassignability,
    "frontend.defineStage.desugarDotOperations" to Debug.Frontend.DefineStage.DesugarDotOperations,
    "frontend.defineStage.afterDesugarDotOperations" to Debug.Frontend.DefineStage.AfterDesugarDotOperations,
    "frontend.defineStage.linkThis" to Debug.Frontend.DefineStage.LinkThis,
    "frontend.defineStage.afterLinkThis" to Debug.Frontend.DefineStage.AfterLinkThis,
    "frontend.defineStage.convertClasses" to Debug.Frontend.DefineStage.ConvertClasses,
    "frontend.defineStage.afterConvertClasses" to Debug.Frontend.DefineStage.AfterConvertClasses,
    "frontend.defineStage.afterAddTemperTestInstructions" to Debug.Frontend.DefineStage.AfterAddTemperTestInstructions,
    "frontend.defineStage.simplifyDeclarations" to Debug.Frontend.DefineStage.SimplifyDeclarations,
    "frontend.defineStage.afterSimplifyDeclarations" to Debug.Frontend.DefineStage.AfterSimplifyDeclarations,
    "frontend.defineStage.convertObjectSyntax" to Debug.Frontend.DefineStage.ConvertObjectSyntax,
    "frontend.defineStage.after" to Debug.Frontend.DefineStage.After,
    "frontend.typeStage" to Debug.Frontend.TypeStage,
    "frontend.typeStage.before" to Debug.Frontend.TypeStage.Before,
    "frontend.typeStage.beforeInterpretation" to Debug.Frontend.TypeStage.BeforeInterpretation,
    "frontend.typeStage.afterInterpretation" to Debug.Frontend.TypeStage.AfterInterpretation,
    "frontend.typeStage.magicSecurityDust" to Debug.Frontend.TypeStage.MagicSecurityDust,
    "frontend.typeStage.afterSprinkle" to Debug.Frontend.TypeStage.AfterSprinkle,
    "frontend.typeStage.weaver" to Debug.Frontend.TypeStage.Weaver,
    "frontend.typeStage.afterWeave" to Debug.Frontend.TypeStage.AfterWeave,
    "frontend.typeStage.simplifyFlow" to Debug.Frontend.TypeStage.SimplifyFlow,
    "frontend.typeStage.afterSimplifyFlow" to Debug.Frontend.TypeStage.AfterSimplifyFlow,
    "frontend.typeStage.makeResultsExplicit" to Debug.Frontend.TypeStage.MakeResultsExplicit,
    "frontend.typeStage.afterExplicitResults" to Debug.Frontend.TypeStage.AfterExplicitResults,
    "frontend.typeStage.type" to Debug.Frontend.TypeStage.Type,
    "frontend.typeStage.afterTyper" to Debug.Frontend.TypeStage.AfterTyper,
    "frontend.typeStage.useBeforeInit" to Debug.Frontend.TypeStage.UseBeforeInit,
    "frontend.typeStage.afterUseBeforeInit" to Debug.Frontend.TypeStage.AfterUseBeforeInit,
    "frontend.typeStage.reorderArgs" to Debug.Frontend.TypeStage.ReorderArgs,
    "frontend.typeStage.afterReorderArgs" to Debug.Frontend.TypeStage.AfterReorderArgs,
    "frontend.typeStage.simplifyFlow2" to Debug.Frontend.TypeStage.SimplifyFlow2,
    "frontend.typeStage.afterSimplifyFlow2" to Debug.Frontend.TypeStage.AfterSimplifyFlow2,
    "frontend.typeStage.cleanupTemporaries" to Debug.Frontend.TypeStage.CleanupTemporaries,
    "frontend.typeStage.afterCleanupTemporaries" to Debug.Frontend.TypeStage.AfterCleanupTemporaries,
    "frontend.typeStage.simplifyFlow3" to Debug.Frontend.TypeStage.SimplifyFlow3,
    "frontend.typeStage.afterSimplifyFlow3" to Debug.Frontend.TypeStage.AfterSimplifyFlow3,
    "frontend.typeStage.repairUnrealizedGoals" to Debug.Frontend.TypeStage.RepairUnrealizedGoals,
    "frontend.typeStage.afterRepairUnrealizedGoals" to Debug.Frontend.TypeStage.AfterRepairUnrealizedGoals,
    "frontend.typeStage.after" to Debug.Frontend.TypeStage.After,
    "frontend.functionMacroStage" to Debug.Frontend.FunctionMacroStage,
    "frontend.functionMacroStage.before" to Debug.Frontend.FunctionMacroStage.Before,
    "frontend.functionMacroStage.after" to Debug.Frontend.FunctionMacroStage.After,
    "frontend.exportStage" to Debug.Frontend.ExportStage,
    "frontend.exportStage.before" to Debug.Frontend.ExportStage.Before,
    "frontend.exportStage.after" to Debug.Frontend.ExportStage.After,
    "frontend.exportStage.exports" to Debug.Frontend.ExportStage.Exports,
    "frontend.queryStage" to Debug.Frontend.QueryStage,
    "frontend.queryStage.before" to Debug.Frontend.QueryStage.Before,
    "frontend.queryStage.after" to Debug.Frontend.QueryStage.After,
    "frontend.generateCodeStage" to Debug.Frontend.GenerateCodeStage,
    "frontend.generateCodeStage.before" to Debug.Frontend.GenerateCodeStage.Before,
    "frontend.generateCodeStage.beforeInterpretation" to Debug.Frontend.GenerateCodeStage.BeforeInterpretation,
    "frontend.generateCodeStage.afterInterpretation" to Debug.Frontend.GenerateCodeStage.AfterInterpretation,
    "frontend.generateCodeStage.magicSecurityDust" to Debug.Frontend.GenerateCodeStage.MagicSecurityDust,
    "frontend.generateCodeStage.afterSprinkle" to Debug.Frontend.GenerateCodeStage.AfterSprinkle,
    "frontend.generateCodeStage.weaver" to Debug.Frontend.GenerateCodeStage.Weaver,
    "frontend.generateCodeStage.afterWeave" to Debug.Frontend.GenerateCodeStage.AfterWeave,
    "frontend.generateCodeStage.type" to Debug.Frontend.GenerateCodeStage.Type,
    "frontend.generateCodeStage.afterTyper" to Debug.Frontend.GenerateCodeStage.AfterTyper,
    "frontend.generateCodeStage.typeCheck" to Debug.Frontend.GenerateCodeStage.TypeCheck,
    "frontend.generateCodeStage.afterTypeCheck" to Debug.Frontend.GenerateCodeStage.AfterTypeCheck,
    "frontend.generateCodeStage.simplifyFlow" to Debug.Frontend.GenerateCodeStage.SimplifyFlow,
    "frontend.generateCodeStage.afterSimplifyFlow" to Debug.Frontend.GenerateCodeStage.AfterSimplifyFlow,
    "frontend.generateCodeStage.cleanupTemporaries" to Debug.Frontend.GenerateCodeStage.CleanupTemporaries,
    "frontend.generateCodeStage.afterCleanupTemporaries" to Debug.Frontend.GenerateCodeStage.AfterCleanupTemporaries,
    "frontend.generateCodeStage.simplifyFlow2" to Debug.Frontend.GenerateCodeStage.SimplifyFlow2,
    "frontend.generateCodeStage.after" to Debug.Frontend.GenerateCodeStage.After,
    "backend" to Debug.Backend,
    "docgen" to Debug.Docgen,
)
