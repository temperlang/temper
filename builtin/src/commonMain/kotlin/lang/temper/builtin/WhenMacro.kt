package lang.temper.builtin

import lang.temper.env.InterpMode
import lang.temper.log.FailLog
import lang.temper.log.MessageTemplate
import lang.temper.log.Position
import lang.temper.name.BuiltinName
import lang.temper.name.Symbol
import lang.temper.name.TemperName
import lang.temper.stage.Stage
import lang.temper.value.BlockTree
import lang.temper.value.CallTree
import lang.temper.value.Fail
import lang.temper.value.FunTree
import lang.temper.value.LeftNameLeaf
import lang.temper.value.LinearFlow
import lang.temper.value.MacroEnvironment
import lang.temper.value.NameLeaf
import lang.temper.value.NotYet
import lang.temper.value.PartialResult
import lang.temper.value.Planting
import lang.temper.value.PostponedCaseMacro
import lang.temper.value.Tree
import lang.temper.value.TreeTemplate
import lang.temper.value.Value
import lang.temper.value.caseCaseSymbol
import lang.temper.value.caseIsSymbol
import lang.temper.value.caseSymbol
import lang.temper.value.defaultSymbol
import lang.temper.value.elseIfSymbol
import lang.temper.value.elseSymbol
import lang.temper.value.freeTree
import lang.temper.value.functionContained
import lang.temper.value.ifBuiltinName
import lang.temper.value.logicalOrBuiltinName
import lang.temper.value.symbolContained
import lang.temper.value.vInitSymbol
import lang.temper.value.void

/**
 * <!-- snippet: builtin/when -->
 * # `when`
 * You can check when an expression matches types or values.
 *
 * ```temper
 * interface I {}
 * class A(public message: String) extends I {}
 * class B extends I {}
 * class C extends I {}
 * class D extends I {}
 *
 * let nameOfI(x: I): String {
 *   when (x) {
 *     is A -> "A ${x.message}"; // Auto cast for single `is` type.
 *     is B, is C -> "B or C";
 *     else -> do {
 *       let a = nameOfI(new A("at all"));
 *       let b = nameOfI(new B());
 *       "not ${a} or ${b}"
 *     }
 *   }
 * }
 *
 * console.log(nameOfI(new A("here"))); //!outputs "A here"
 * console.log(nameOfI(new B())); //!outputs "B or C"
 * console.log(nameOfI(new D())); //!outputs "not A at all or B or C"
 *
 * console.log(
 *   when (2) {
 *     0 -> "none";
 *     1, 2, 3 -> "little";
 *     else -> "lots or negative";
 *   }
 * ); //!outputs "little"
 * ```
 */
internal object WhenMacro : BuiltinMacro("when", null, nameIsKeyword = true) {
    override fun invoke(macroEnv: MacroEnvironment, interpMode: InterpMode): PartialResult {
        val call = macroEnv.call ?: return Fail
        if (interpMode != InterpMode.Partial) {
            return macroEnv.fail(MessageTemplate.CannotInvokeMacroAsFunction, macroEnv.pos)
        }
        // During SyntaxMacro stage, we should remove comments.  REM(...) calls in
        // class bodies and when blocks are problematic since they are not runs of
        // statements.
        // We do the bulk of the work during define stage, where we have name
        // resolution which helps for swapping out vars.
        if (macroEnv.stage > Stage.Define) {
            macroEnv.replaceMacroCallWithErrorNode()
            return Fail
        }
        if (macroEnv.args.size != 2) {
            return macroEnv.fail(MessageTemplate.ArityMismatch, macroEnv.pos, listOf(2))
        }
        val block = macroEnv.args.valueTree(1).childOrNull(0) as? BlockTree ?: run {
            macroEnv.failLog.fail(MessageTemplate.SignatureMismatch, macroEnv.pos)
            return@invoke Fail
        }
        if (block.flow !is LinearFlow) {
            return macroEnv.fail(MessageTemplate.SignatureMismatch, macroEnv.pos)
        }
        if (macroEnv.stage == Stage.Import) {
            // Comments are useless in case position since there are no declarations
            // for them to attach to.
            var i = block.children.size
            while (--i >= 0) {
                if (isRemCall(block.child(i))) {
                    block.replace(i..i) {}
                }
            }
            return NotYet
        }
        if (macroEnv.stage < Stage.Define) {
            return NotYet
        }

        // At some future time, we might want to detect when we can use special forms for easy translation to backend
        // `switch` blocks, but for the moment, just turn it into nested if/else.
        macroEnv.replaceMacroCallWith {
            Block(call.pos) {
                // Figure out if we need to create a temporary subject.
                val originalSubject = freeTree(macroEnv.args.valueTree(0))
                val subject = if (originalSubject is NameLeaf) {
                    originalSubject
                } else {
                    val subject = macroEnv.treeFarm.grow {
                        Decl(originalSubject.pos, macroEnv.nameMaker.unusedTemporaryName("subject")) {
                            V(originalSubject.pos, vInitSymbol)
                            Replant(originalSubject)
                        }
                    }
                    Replant(subject)
                    subject.child(0) as NameLeaf
                }
                // Later cases will be nested inside earlier ones, so fold them from the right.
                val branches = buildBranches(
                    kids = block.children,
                    failLog = macroEnv.failLog,
                    fullPos = call.pos,
                )
                val first = branches.firstOrNull()
                branches.foldRight(null as Pair<Symbol, Tree>?) { branch, acc ->
                    val firstCase = branch.cases.first()
                    val nextSymbol = if (firstCase.kind == Case.Kind.Default) {
                        elseSymbol
                    } else {
                        elseIfSymbol
                    }
                    nextSymbol to macroEnv.treeFarm.grow {
                        @Suppress("UNCHECKED_CAST") // Always FunTree if from acc, but Kotlin doesn't know.
                        when (firstCase.kind) {
                            Case.Kind.Default -> growDefault(isFirst = branch === first, branch = branch)
                            else -> growCaseBranch(
                                isFirst = branch === first,
                                subject = subject,
                                branch = branch,
                                join = acc as? Pair<Symbol, FunTree>,
                            )
                        }
                    }
                }?.let { Replant(it.second) }
            }
        }
        return NotYet
    }
}

private class Branch(val cases: List<Case>, val pos: Position, val value: Tree)

private class Case(val kind: Kind, val pos: Position, val test: Tree?) {
    enum class Kind { Default, Eq, Is, Case }
}

/** Gather up all the cases in the statement list, so we can grow nested trees from them elsewhere in reverse order. */
private fun buildBranches(kids: List<Tree>, failLog: FailLog, fullPos: Position) = buildList {
    var kidIndex = 0
    var hasDefault = false
    loopBranches@ while (kidIndex < kids.size) {
        val isFirst = kidIndex == 0
        val branchStartIndex = kidIndex
        val branchFirstKid = kids[kidIndex]
        val isExcess = hasDefault
        var hasInvalidCases = false
        val cases = buildList cases@{
            while (true) {
                val kid = kids[kidIndex]
                val kind = caseKind(kid) ?: return@cases
                kidIndex += 1
                val test = if (kind == Case.Kind.Default) {
                    hasDefault = true
                    null
                } else {
                    val test = kids.getOrNull(kidIndex++) ?: run {
                        invalidCaseCondition(kid, failLog)
                        return@cases
                    }
                    if (kind == Case.Kind.Case && !isPostponedCaseCall(test)) {
                        invalidCaseCondition(test, failLog)
                        hasInvalidCases = true
                    }
                    test
                }
                add(Case(kind, kid.pos, test))
            }
        }
        if (cases.isEmpty()) {
            if (branchFirstKid.content !== void) {
                // Comments can cause void, and we don't care about that, but error on anything else unexpected.
                failLog.fail(MessageTemplate.InvalidBlockContent, branchFirstKid.pos)
            }
            if (kidIndex == branchStartIndex) {
                kidIndex += 1
            }
            continue@loopBranches
        }
        val value = kids.getOrNull(kidIndex++) ?: run {
            // For now, require a value for each case.
            failLog.fail(MessageTemplate.MissingCaseValue, kids[kidIndex - 2].pos.rightEdge)
            return@buildList
        }
        if (isExcess) {
            // Nothing allowed after default, but try to consume the whole case above to keep better in sync.
            failLog.fail(MessageTemplate.ElseMustBeLast, branchFirstKid.pos)
        } else if (!hasInvalidCases) {
            // Add the case with stretched out pos.
            val pos = when (isFirst) {
                // For the first, we want to capture the entire when block for communicating it as an expression.
                true -> fullPos
                // Later blocks exist only inside the block structure, so just grab the local extent.
                false -> Position(branchFirstKid.pos.loc, branchFirstKid.pos.left, value.pos.right)
            }
            add(Branch(cases, pos, value))
        }
    }
}

private fun caseKind(kindKid: Tree) = when (kindKid.symbolContained) {
    defaultSymbol -> Case.Kind.Default
    caseSymbol -> Case.Kind.Eq
    caseIsSymbol -> Case.Kind.Is
    caseCaseSymbol -> Case.Kind.Case
    else -> null
}

@Suppress("UNCHECKED_CAST")
private fun <TREE : Tree> Planting.growCase(
    pos: Position,
    isFirst: Boolean,
    growContent: Planting.(fnName: TemperName) -> TreeTemplate<TREE>,
) = if (isFirst) {
    growContent(ifBuiltinName)
} else {
    // Function in the style of `rewriteCallJoin`.
    var parameter: TemperName? = null
    Fn(pos) {
        Decl(pos) {
            Ln(pos) {
                it.unusedTemporaryName("f").also { newName ->
                    parameter = newName
                }
            }
        }
        growContent(parameter!!)
    }
} as TreeTemplate<Tree>

private fun Planting.growCaseBranch(
    isFirst: Boolean,
    subject: NameLeaf,
    branch: Branch,
    join: Pair<Symbol, FunTree>?,
) = growCase(pos = branch.pos, isFirst = isFirst) { ifName ->
    growIfCall(ifName = ifName, branch = branch, subject = subject, join = join)
}

private fun Planting.growDefault(isFirst: Boolean, branch: Branch) = if (isFirst) {
    // First is simple.
    Replant(freeTree(branch.value))
} else {
    // Reuse generic case logic for non-first. And still always last here, so still fairly simple.
    val pos = branch.cases.first().pos
    growCase(pos = pos, isFirst = false) { fnName ->
        Call(pos) {
            Rn(pos, fnName)
            Fn(pos) {
                Block(pos) {
                    Replant(freeTree(branch.value))
                }
            }
        }
    }
}

private fun Planting.growIfCall(
    ifName: TemperName,
    branch: Branch,
    subject: NameLeaf,
    join: Pair<Symbol, FunTree>?,
) = Call(branch.pos) {
    val testPos = branch.pos
    Rn(testPos, ifName)
    // Grow disjunction of cases.
    branch.cases.foldRight(null as Tree?) { case, acc ->
        val test = subject.treeFarm.grow {
            when (case.kind) {
                Case.Kind.Eq -> growEq(subject, case.test!!)
                Case.Kind.Is -> growIs(subject, case.test!!)
                Case.Kind.Case -> growCase(subject, case.test!!)

                // Should be at end
                Case.Kind.Default -> error("unexpected ${case.kind}")
            }
        }
        when (acc) {
            null -> test
            else -> subject.treeFarm.grow {
                Call(case.pos) {
                    Rn(logicalOrBuiltinName)
                    Replant(test)
                    Replant(acc)
                }
            }
        }
    }.let { Replant(it!!) }
    // Grow body.
    Fn(branch.value.pos) {
        Block(branch.value.pos) {
            Replant(freeTree(branch.value))
        }
    }
    growJoin(join)
}

private fun Planting.growIs(subject: NameLeaf, typeRef: Tree) =
    Call(typeRef.pos) {
        V(typeRef.pos.leftEdge, Value(IsFunction))
        Rn(subject.pos, subject.content)
        Replant(freeTree(typeRef))
    }

private const val POSTPONED_CALL_SUBJECT_INDEX = 2
private fun Planting.growCase(subject: NameLeaf, postponedCaseCall: Tree): TreeTemplate<CallTree> {
    check(isPostponedCaseCall(postponedCaseCall) && postponedCaseCall is CallTree)
    // Otherwise was marked invalid
    // Insert subject reference into call.
    //    postponedCase([tokens], subject, symbolNamePairs)
    // And filter out any resolutions that are free names / BuiltinNames.
    postponedCaseCall.add(POSTPONED_CALL_SUBJECT_INDEX, subject.copyRight())
    var i = POSTPONED_CALL_SUBJECT_INDEX + 1
    while (i + 1 in postponedCaseCall.indices) {
        val key = postponedCaseCall.child(i)
        val value = postponedCaseCall.child(i + 1)
        if (key.symbolContained != null && value is LeftNameLeaf) {
            if (value.content is BuiltinName) {
                // Resolution provides no info
                postponedCaseCall.replace(i..(i + 1)) {}
            } else {
                i += 2
            }
        } else {
            break
        }
    }

    return Replant(freeTree(postponedCaseCall))
}

private fun Planting.growEq(subject: NameLeaf, test: Tree) = Call(test.pos) {
    V(test.pos, Value(BuiltinFuns.equalsFn))
    Rn(subject.pos, subject.content)
    Replant(freeTree(test))
}

private fun Planting.growJoin(join: Pair<Symbol, FunTree>?) {
    join ?: return
    V(join.second.pos, join.first)
    Replant(join.second)
}

private fun invalidCaseCondition(tree: Tree, failLog: FailLog): Boolean {
    failLog.fail(MessageTemplate.InvalidCaseCondition, tree.pos)
    return false
}

private fun isPostponedCaseCall(tree: Tree) =
    tree is CallTree && tree.childOrNull(0)?.functionContained == PostponedCaseMacro
