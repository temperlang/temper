package lang.temper.interp

import lang.temper.builtin.Types
import lang.temper.env.Constness
import lang.temper.env.DeclarationBits
import lang.temper.env.InterpMode
import lang.temper.env.ReferentBitSet
import lang.temper.env.ReferentSource
import lang.temper.log.MessageTemplate
import lang.temper.value.BlockTree
import lang.temper.value.DeclTree
import lang.temper.value.Fail
import lang.temper.value.FunTree
import lang.temper.value.MacroEnvironment
import lang.temper.value.NameLeaf
import lang.temper.value.PartialResult
import lang.temper.value.TSymbol
import lang.temper.value.Tree
import lang.temper.value.and
import lang.temper.value.complexArgSymbol
import lang.temper.value.freeTarget
import lang.temper.value.freeTree
import lang.temper.value.infoOr
import lang.temper.value.outTypeSymbol
import lang.temper.value.returnParsedName
import lang.temper.value.spanningPosition
import lang.temper.value.vReturnDeclSymbol
import lang.temper.value.vTypeSymbol
import lang.temper.value.valueContained
import lang.temper.value.wordSymbol

/**
 * Unpacks a function declaration.  This does enough macro work to allow functions to work in
 * the interpreter without the multiple stages used by `lang.temper.frontend`.
 * It is not used in the main language, as it has been obviated by *SyntaxMacroStage*'s `let` and
 * `fn` macros.
 */
internal fun functionMacro(macroEnv: MacroEnvironment): PartialResult {
    val args = macroEnv.args
    var fail: Fail? = null
    var name: Tree? = null
    val n = args.size
    var i = 0

    val funTreeParts = macroEnv.treeFarm.seedAll {
        while (i < n && args.key(i) == wordSymbol) {
            val newName = args.valueTree(i)
            i += 1
            if (name == null) {
                name = newName
            } else {
                fail = macroEnv.fail(MessageTemplate.DeclarationHasTooManyNames, newName.pos)
            }
        }
        if (name == null) {
            fail = macroEnv.fail(MessageTemplate.MissingName)
        }

        while (i + 1 < n) {
            if (args.key(i) != null) { break }
            val formalTree = args.valueTree(i)
            i += 1
            when (formalTree) {
                // Already packaged as a declaration.  This is the case for the output variable
                // created from any return type, but this also serves to make this loop idempotent.
                is DeclTree -> Replant(freeTree(formalTree))
                is NameLeaf -> DeclS { Replant(freeTree(formalTree)) }
                is BlockTree -> {
                    var part = formalTree
                    if (formalTree.size != 0) {
                        val c0 = formalTree.child(0)
                        val value = c0.valueContained
                        if (complexArgSymbol == TSymbol.unpackOrNull(value)) {
                            val declParts = (1 until formalTree.size).map {
                                freeTarget(formalTree.edge(it))
                            }
                            part = macroEnv.treeFarm.grow {
                                Decl(formalTree.spanningPosition(1, formalTree.size)) {
                                    declParts.forEach { Replant(it) }
                                }
                            }
                        }
                    }
                    Replant(part)
                }
                else -> {
                    i -= 1 // Undo increment from above
                    break
                }
            }
        }

        while (i < n) {
            val symbol = args.key(i) ?: break
            val keyTree = args.keyTree(i)!!
            val valueTree = args.valueTree(i)
            i += 1

            when (symbol) {
                outTypeSymbol -> {
                    val rtPos = valueTree.pos
                    V(keyTree.pos, vReturnDeclSymbol)
                    Decl(rtPos) {
                        Ln(rtPos) { it.unusedSourceName(returnParsedName) }
                        V(rtPos, vTypeSymbol)
                        Replant(freeTree(valueTree))
                    }
                }
                else -> {
                    Replant(freeTree(keyTree))
                    Replant(freeTree(valueTree))
                }
            }
        }

        if (i + 1 != n) {
            fail = Fail
        }

        if (fail == null) {
            val body = args.valueTree(i)
            if (args.key(i) != null || body !is FunTree || body.size != 1) {
                fail = macroEnv.fail(MessageTemplate.MalformedDeclaration, body.pos)
            } else {
                Replant(freeTarget(body.edge(0)))
            }
        }
    }

    return if (fail == null) {
        val funTree = macroEnv.treeFarm.grow {
            Fn(macroEnv.pos) {
                Replant(funTreeParts)
            }
        }
        macroEnv.evaluateTree(funTree, InterpMode.Full).and { initialValue ->
            macroEnv.declareLocal(
                nameLeaf = name!!,
                DeclarationBits(
                    reifiedType = Types.vFunction,
                    initial = initialValue,
                    constness = Constness.NotConst,
                    referentSource = ReferentSource.Unknown,
                    missing = ReferentBitSet.empty,
                    declarationSite = macroEnv.pos,
                ),
            )
        }
    } else {
        (fail ?: Fail).infoOr {
            macroEnv.fail(
                MessageTemplate.MalformedDeclaration,
                if (i == n) { macroEnv.pos } else { args.pos(i) },
            )
        }
    }
}
