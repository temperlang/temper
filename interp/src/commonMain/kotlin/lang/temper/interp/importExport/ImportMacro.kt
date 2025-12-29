package lang.temper.interp.importExport

import lang.temper.common.Log
import lang.temper.common.temperEscaper
import lang.temper.env.InterpMode
import lang.temper.format.OutToks
import lang.temper.format.OutputToken
import lang.temper.format.OutputTokenType
import lang.temper.format.TokenSerializable
import lang.temper.format.TokenSink
import lang.temper.interp.importExport.ImportMacro.IMPORT_PENDING_FEATURE_KEY
import lang.temper.log.LogEntry
import lang.temper.log.MessageTemplate
import lang.temper.stage.Stage
import lang.temper.type2.Signature2
import lang.temper.value.BuiltinStatelessMacroValue
import lang.temper.value.DeclTree
import lang.temper.value.Fail
import lang.temper.value.InternalFeatureKey
import lang.temper.value.MacroEnvironment
import lang.temper.value.NamedBuiltinFun
import lang.temper.value.NotYet
import lang.temper.value.PartialResult
import lang.temper.value.StayLeaf
import lang.temper.value.TString
import lang.temper.value.Value
import lang.temper.value.ValueLeaf
import lang.temper.value.fileRestrictedBuiltinName
import lang.temper.value.importBuiltinName
import lang.temper.value.initSymbol
import lang.temper.value.staySymbol
import lang.temper.value.void

/**
 * Replaces `import` calls with contextualized imports so that the *BuildConductor* can scan the
 * tree and create dependencies between modules.
 *
 * This macro requires a few things of the environment:
 *
 * - There should be a feature implementation for [IMPORT_PENDING_FEATURE_KEY].
 *   Each call to the implementation should eventually result in an error or a call to
 *   [createLocalBindingsForImport].
 * - There should be an environment binding for [fileRestrictedBuiltinName] whose value is as
 *   per the output of [toValue]
 *
 * <!-- snippet: builtin/import -->
 * # `import`
 * The `import` function, in conjunction with [`export`][snippet/builtin/@export],
 * allows connecting multiple Temper modules together.
 *
 * One module may `export` a symbol.
 *
 * ```temper inert
 * export let world = "Earth";
 * ```
 *
 * and another may import it
 *
 * ```temper inert
 * let { world } = import("./earth");
 * console.log("Hello, ${ world }!"); //!outputs "Hello, Earth!"
 * ```
 *
 * As shown above, when importing from files under the same *work root*,
 * use a relative file path starting with one of `"./"` or `"../"`.
 * This allows multiple co-compiled libraries to link to one another.
 *
 * Import specifiers always use the URL file separator (`/`), regardless
 * of whether compilation happens on an operating-system like Linux which
 * uses the same separator or on Windows which uses `\\`.
 *
 * To link to separately compiled libraries:
 *
 * 1. start with the library name
 * 2. followed by a separator: `/`
 * 3. followed by the path to the source file relative to the library root
 *    using the URL file separator
 *
 * ```temper
 * // Temper's standard library uses the name `std`.
 * // The temporal module includes `class Date` and other time-related
 * // types and functions.
 * let { Date } = import("std/temporal");
 *
 * let d: Date = { year: 2023, month: 12, day: 13 };
 * console.log(d.toString()); //!outputs "2023-12-13"
 * ```
 */
object ImportMacro : BuiltinStatelessMacroValue, NamedBuiltinFun {
    override val name: String = importBuiltinName.builtinKey
    override val sigs: List<Signature2>? = null

    override fun invoke(macroEnv: MacroEnvironment, interpMode: InterpMode): PartialResult {
        if (macroEnv.stage < Stage.Import) {
            return NotYet
        }
        // Grab module path from scope
        // If we got a relative path, resolve it against the path.
        val args = macroEnv.args
        if (args.size != 1) {
            macroEnv.explain(MessageTemplate.ArityMismatch, values = listOf(1))
            return Fail
        }
        val moduleSpecifierResult = args.evaluate(0, InterpMode.Partial)
        val moduleSpecifier = TString.unpackOrNull(moduleSpecifierResult as? Value<*>)
        if (moduleSpecifier == null) {
            if (moduleSpecifierResult !is Fail) {
                macroEnv.explain(
                    MessageTemplate.ExpectedValueOfType,
                    pos = args.pos(0),
                    values = listOf(TString, moduleSpecifierResult),
                )
            }
            return Fail
        }
        val (pseudoProtocol, rest) = when (val colon = moduleSpecifier.indexOf(':')) {
            -1 -> null to moduleSpecifier
            else ->
                moduleSpecifier.substring(0, colon) to moduleSpecifier.substring(colon + 1)
        }
        val resolvedModuleSpecifier =
            if (
                pseudoProtocol == "file" ||
                (
                    pseudoProtocol == null && (
                        rest.startsWith("./") ||
                            rest.startsWith("../") ||
                            rest.startsWith("/") ||
                            rest == "." || rest == ".."
                        )
                    )
            ) {
                val filePath = macroEnv.environment.get(
                    fileRestrictedBuiltinName,
                    macroEnv,
                ).toFilePath()

                if (filePath == null) {
                    macroEnv.explain(MessageTemplate.BadImportEnvironment)
                    return Fail
                }

                val resolvedPath = resolveModuleSpecifier(
                    moduleSpecifier = rest,
                    basePath = filePath,
                    pos = macroEnv.pos,
                    logSink = macroEnv.logSink,
                ) ?: return Fail

                ResolvedModuleSpecifier("file:$resolvedPath")
            } else {
                ResolvedModuleSpecifier(moduleSpecifier)
            }

        // If the import is the initializer for a declaration, then we need to halt progress
        // beyond the current stage.
        val edgeToCall = macroEnv.call?.incoming
        val parent = edgeToCall?.source
        var importStayLeaf: StayLeaf? = null
        if (parent is DeclTree) {
            val parts = parent.partsIgnoringName
            if (parts?.metadataSymbolMap?.get(initSymbol) == edgeToCall) {
                // Make sure we have a stay node.
                importStayLeaf = parts.metadataSymbolMap[staySymbol]?.target as? StayLeaf
                    ?: run {
                        val newStayLeaf = parent.treeFarm.grow {
                            Stay(macroEnv.pos)
                        }
                        parent.replace(parent.size until parent.size) {
                            V(staySymbol)
                            Replant(newStayLeaf)
                        }
                        newStayLeaf
                    }
            }
        }
        val importProcessed =
            macroEnv.getFeatureImplementation(IMPORT_PENDING_FEATURE_KEY) as? Value<*>
                ?: run {
                    val err = LogEntry(
                        Log.Error,
                        MessageTemplate.UnsupportedByInterpreter,
                        macroEnv.pos,
                        values = listOf(IMPORT_PENDING_FEATURE_KEY),
                    )
                    err.logTo(macroEnv.logSink)
                    return@invoke Fail(err)
                }

        macroEnv.dispatchCallTo(
            ValueLeaf(macroEnv.document, macroEnv.pos, importProcessed),
            importProcessed,
            listOfNotNull(
                ValueLeaf(
                    macroEnv.document,
                    args.pos(0),
                    Value(resolvedModuleSpecifier.text, TString),
                ),
                importStayLeaf,
                // TODO: Decide how to transmit module parameter list
            ),
            interpMode,
        )

        macroEnv.replaceMacroCallWith {
            if (importStayLeaf != null) {
                Call(ContextualizedImport(resolvedModuleSpecifier)) {}
            } else {
                V(void)
            }
        }

        return NotYet
    }

    /**
     * An [InternalFeatureKey] for a macro that will be called to notify whatever schedules
     * stage advancements about an import.
     *
     * It will be called with:
     *
     * 1. The [ResolvedModuleSpecifier] as a string
     * 2. Any [StayLeaf] that needs to be replaced with exported names via
     *    [createLocalBindingsForImport]
     * 3. TODO: A module parameter list
     */
    const val IMPORT_PENDING_FEATURE_KEY: InternalFeatureKey = "importPending"
}

/**
 * A placeholder which the BuildConductor looks for so that it can schedule imports at the end
 * of the import stage.
 */
class ContextualizedImport(
    private val moduleSpecifier: ResolvedModuleSpecifier,
) : BuiltinStatelessMacroValue, TokenSerializable {
    override fun renderTo(tokenSink: TokenSink) {
        tokenSink.emit(OutputToken("import", OutputTokenType.Word))
        tokenSink.emit(OutToks.leftParen)
        tokenSink.emit(
            OutputToken(temperEscaper.escape(moduleSpecifier.text), OutputTokenType.QuotedValue),
        )
        tokenSink.emit(OutToks.rightParen)
    }

    override val sigs: List<Signature2>? get() = null

    override fun invoke(macroEnv: MacroEnvironment, interpMode: InterpMode): PartialResult {
        return NotYet
    }
}
