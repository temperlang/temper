package lang.temper.value

import lang.temper.builtin.BuiltinFuns
import lang.temper.builtin.BuiltinLogicalOperators
import lang.temper.common.LeftOrRight
import lang.temper.common.TestDocumentContext
import lang.temper.common.assertStringsEqual
import lang.temper.common.compatRemoveLast
import lang.temper.common.console
import lang.temper.common.jsonEscaper
import lang.temper.common.mutSubListToEnd
import lang.temper.common.withRandomForTest
import lang.temper.log.Position
import lang.temper.name.BuiltinName
import lang.temper.name.ParsedName
import lang.temper.name.ResolvedName
import kotlin.math.max
import kotlin.random.Random
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

/**
 * A fuzzer test that randomly tests properties of control flow.
 *
 * - constructs random control flow where all the statements are print,
 * - converts the control flow to Java and runs `javac C.java && java -cp . C` to
 *   get the expected output
 * - interprets the original control flow and each simplified variant of it
 *
 * This provides some assurances that the following properties hold:
 *
 * - simplifyFlow does not change semantics
 * - the most simplified control flow fits within Java's unreachable code checks
 */
class ControlFlowFuzzTest {
    @Test
    fun fuzzIt() = withRandomForTest { rng ->
        val timeToRun = 10.seconds
        var timeTaken = 0.seconds
        var nRuns = 0
        while (true) {
            nRuns += 1
            timeTaken += measureTime {
                fuzzOne(rng)
            }
            if (timeTaken >= timeToRun) { break }
        }
        console.log("Fuzzed $nRuns tests in $timeTaken")
    }

    fun fuzzOne(rng: Random) {
        val randomizer = Randomizer(rng)
        val document = Document(TestDocumentContext())
        val block = document.treeFarm.grow(Position(document.context.namingContext.loc, 0, 0)) {
            Block {
                randomizer.randomStmtBlock(this)
            }
        }
        val originalBlock = block.copy() as BlockTree

        simplifyStructuredBlock(
            block,
            block.flow as StructuredFlow,
            assumeAllJumpsResolved = false,
            assumeResultsCaptured = false,
            logicalOperators = BuiltinLogicalOperators,
        )
        val simpleBlock = block.copy() as BlockTree
        simplifyStructuredBlock(
            block,
            block.flow as StructuredFlow,
            assumeAllJumpsResolved = false,
            assumeResultsCaptured = true,
            logicalOperators = BuiltinLogicalOperators,
        )
        val simplerBlock = block.copy() as BlockTree
        simplifyStructuredBlock(
            block,
            block.flow as StructuredFlow,
            assumeResultsCaptured = true,
            assumeAllJumpsResolved = true,
            logicalOperators = BuiltinLogicalOperators,
        )
        val simplestBlock = block.copy() as BlockTree

        val simplestControlFlow = (simplestBlock.flow as StructuredFlow).controlFlow

        val java = JavaControlFlowConverter(block).convertToJavaStatements(simplestControlFlow)
        var outputFromJava: String? = null
        fun dumpState() {
            console.group("Original") {
                originalBlock.toPseudoCode(console.textOutput)
            }
            console.group("Simple") {
                simpleBlock.toPseudoCode(console.textOutput)
            }
            console.group("Simpler") {
                simplerBlock.toPseudoCode(console.textOutput)
            }
            console.group("Simplest") {
                simplestBlock.toPseudoCode(console.textOutput)
            }
            console.group("Java") {
                console.log(java)
            }
            console.group("Output from Java") {
                console.log(outputFromJava ?: "<NO OUTPUT FROM JAVA>")
            }
        }

        var ok = false
        var cleanupTempFiles: () -> Unit = {}
        // Run the block as Java to get the expected result.
        try {
            compileAndRunJavaAndGetStdout(java).let {
                outputFromJava = it.first.trimEnd().replace("\r", "")
                cleanupTempFiles = it.second
            }

            // Then run in the interpreter with different levels of simplification.
            val differentBlocks = listOf(
                "original" to originalBlock,
                "simple" to simpleBlock,
                "simpler" to simplerBlock,
                "simplest" to simplestBlock,
            )
            for ((description, oneBlock) in differentBlocks) {
                val interpretResult = interpretBlockForTest(oneBlock, emptyMap())
                val got = interpretResult.stdout.trimEnd()
                assertStringsEqual(outputFromJava!!, got, "Result from $description")
            }

            ok = true
        } finally {
            if (ok) {
                cleanupTempFiles()
            } else {
                dumpState()
            }
        }
    }
}

private class Randomizer(val rng: Random) {
    val doc = Document(TestDocumentContext())

    val countersInScope = mutableListOf<ResolvedName>()
    val jumpTargetsInScope = mutableListOf<JumpTarget>()
    var orClauseDepth = 0

    var printCounter = 0

    fun BlockPlanting.randomControlFlow(depth: Int) {
        // As the depth increases, the likelihood we choose a break/continue/bubble
        // increases, and the chance we choose a nesting control flow structure decreases.
        val numNesting = (100 / ((depth + 1) * (depth + 1)))
        val numTransferOut = if (jumpTargetsInScope.isEmpty() && orClauseDepth == 0) {
            0
        } else {
            depth * depth
        }
        val numPrint = 16

        val nestingRange = 0 until numNesting
        val transferOutRange = nestingRange.thenMore(numTransferOut)
        val printRange = transferOutRange.thenMore(numPrint)
        val incrementRange = printRange.thenMore(countersInScope.size)
        val rangeMax = incrementRange.last

        when (rng.nextInt(until = rangeMax + 1)) {
            in nestingRange -> randomNesting(depth)
            in transferOutRange -> randomTransferOut()
            in incrementRange -> randomIncrement()
            else -> randomPrint()
        }
    }

    fun <T> BlockPlanting.withCounterInScope(action: (ResolvedName) -> T): T {
        val nameChar = ('a'.code + (countersInScope.size % 26)).toChar()
        val name = nameMaker.unusedSourceName(ParsedName("$nameChar"))
        countersInScope.add(name)
        Decl(name) {
            V(initSymbol)
            V(Value(0, TInt))
            V(varSymbol)
            V(void)
        }
        try {
            return action(name)
        } finally {
            countersInScope.compatRemoveLast()
        }
    }

    fun BlockPlanting.randomNesting(depth: Int) = when (rng.nextInt(5)) {
        0 -> randomStmtBlock(depth)
        1 -> randomIf(depth)
        2 -> {
            if (countersInScope.isEmpty() || rng.nextBoolean()) {
                withCounterInScope {
                    randomLoop(depth)
                }
            } else {
                randomLoop(depth)
            }
        }
        3 -> randomLabeled(depth)
        4 -> randomOrElse(depth)
        else -> error("unreachable")
    }

    fun BlockPlanting.randomTransferOut() {
        val nJumps = jumpTargetsInScope.size
        val nBubble = orClauseDepth
        val jumpRange = 0 until nJumps
        val bubbleRange = jumpRange.thenMore(nBubble)
        val rangeMax = bubbleRange.last
        when (val k = rng.nextInt(max(1, rangeMax))) {
            in jumpRange -> {
                val target = jumpTargetsInScope[k - jumpRange.first]
                val (kind, spec) = target
                val label = (spec as? NamedJumpSpecifier)?.label
                when (kind) {
                    BreakOrContinue.Break -> Break(label)
                    BreakOrContinue.Continue -> Continue(label)
                }
            }
            else -> Call(BubbleFn) {}
        }
    }

    fun Planting.randomIncrement() =
        incrementOf(countersInScope[rng.nextInt(countersInScope.size)])

    fun Planting.incrementOf(varName: ResolvedName): UnpositionedTreeTemplate<*> =
        Call(BuiltinFuns.setLocalFn) {
            Ln(varName)
            Call(BuiltinFuns.plusIntIntFn) {
                Rn(varName)
                V(Value(1, TInt))
            }
        }

    fun Planting.randomPrint(): UnpositionedTreeTemplate<CallTree> {
        val n = printCounter++
        return Call {
            Rn(printLnFnName)
            V(Value("$n", TString))
        }
    }

    fun randomStmtBlock(p: BlockPlanting) {
        p.run {
            randomStmtBlock(0)
        }
    }

    fun BlockPlanting.randomStmtBlock(depth: Int) {
        val nStatements = max(1, 16 / ((depth + 1) * (depth + 1)))
        repeat(nStatements) {
            randomControlFlow(depth + 1)
        }
    }

    fun Planting.randomCondition(withLoopVar: (ResolvedName) -> Unit): UnpositionedTreeTemplate<*> {
        // All counters are monotonic, so these kinds of conditions lead to termination
        // where each loop that samples a counter also increments that counter.
        return if (countersInScope.isEmpty()) {
            V(TBoolean.valueFalse)
        } else {
            val counter = countersInScope[rng.nextInt(countersInScope.size)]
            withLoopVar(counter)
            val limit = rng.nextInt(0, 10)
            Call(BuiltinFuns.lessEqualsFn) {
                Rn(counter)
                V(Value(limit, TInt))
            }
        }
    }

    fun BlockPlanting.randomIf(depth: Int) {
        If(
            { randomCondition {} },
            thn = { randomStmtBlock(depth) },
            els = {
                if (rng.nextBoolean()) {
                    randomStmtBlock(depth)
                }
            },
        )
    }

    fun BlockPlanting.randomLoop(depth: Int) {
        val label = if (rng.nextBoolean()) {
            nameMaker.unusedSourceName(ParsedName("loop"))
        } else {
            null
        }

        val checkPosition = if (rng.nextBoolean()) {
            LeftOrRight.Left
        } else {
            LeftOrRight.Right
        }

        var loopVar: ResolvedName? = null
        While(
            label = label,
            testAt = checkPosition,
            cond = {
                randomCondition { loopVar = it }
            },
            body = {
                val possibleJumps = buildList {
                    val specs = listOfNotNull(
                        label?.let { NamedJumpSpecifier(it) },
                        DefaultJumpSpecifier,
                    )
                    for (kind in BreakOrContinue.entries) {
                        for (spec in specs) {
                            add(JumpTarget(kind, spec))
                        }
                    }
                }
                val nJumpTargetsInScopeBefore = jumpTargetsInScope.size
                jumpTargetsInScope.addAll(possibleJumps)

                if (loopVar != null) {
                    incrementOf(loopVar)
                }
                randomStmtBlock(depth)
                jumpTargetsInScope.mutSubListToEnd(nJumpTargetsInScopeBefore).clear()
            },

            increment = {
                if (
                    countersInScope.isNotEmpty() && checkPosition == LeftOrRight.Left && rng.nextBoolean()
                ) {
                    randomIncrement()
                }
            },
        )
    }

    fun BlockPlanting.randomLabeled(depth: Int) {
        val label = nameMaker.unusedSourceName(ParsedName("label"))
        Do(label = label) {
            jumpTargetsInScope.add(JumpTarget(BreakOrContinue.Break, NamedJumpSpecifier(label)))
            randomStmtBlock(depth)
            jumpTargetsInScope.compatRemoveLast()
        }
    }

    fun BlockPlanting.randomOrElse(depth: Int) {
        OrElse(
            or = {
                orClauseDepth += 1
                randomStmtBlock(depth)
                orClauseDepth -= 1
            },
            els = {
                randomStmtBlock(depth)
            },
        )
    }
}

private fun IntRange.thenMore(n: Int) = (this.last + 1)..(this.last + n)

private val printLnFnName = BuiltinName("println")

private class JavaControlFlowConverter(
    val blockTree: BlockTree,
) : Appendable {
    fun convertToJavaStatements(controlFlow: ControlFlow): String {
        javaSourceText.clear()
        appendControlFlow(controlFlow)
        return "$javaSourceText"
    }

    private fun appendName(name: ResolvedName) {
        append("$name".replace("#", "___"))
    }

    private fun appendExpr(ref: BlockChildReference) =
        appendExpr(blockTree.dereference(ref)!!.target, skipParens = true)

    private fun appendExpr(t: Tree, skipParens: Boolean = false): Unit = when (t) {
        is ValueLeaf -> {
            val v = t.content
            val type = v.typeTag
            when (type) {
                TInt -> append("${TInt.unpack(v)}")
                TString -> appendIgnoreMetaChars(jsonEscaper.escape(TString.unpack(v)))
                TBoolean -> append(
                    if (TBoolean.unpack(v)) {
                        "true"
                    } else {
                        "false"
                    },
                )

                else -> untranslatable(t.toLispy())
            }
            Unit
        }

        is NameLeaf -> {
            appendName(t.content as ResolvedName)
        }
        is DeclTree -> {
            val parts = t.parts!!
            val name = parts.name.content as ResolvedName
            append("int ")
            appendName(name)
            val init = parts.metadataSymbolMap[initSymbol]
            if (init != null) {
                append(" = ")
                appendExpr(init.target)
            }
            Unit
        }

        is BlockTree, is EscTree, is FunTree, is StayLeaf -> untranslatable(t.toLispy())
        is CallTree -> {
            val calleeName: String? = when (val callee = t.child(0)) {
                is NameLeaf -> callee.content.builtinKey
                is ValueLeaf -> {
                    var fn = callee.functionContained
                    while (fn is CoverFunction) {
                        fn = fn.covered.first()
                    }
                    (fn as NamedBuiltinFun).name
                }
                else -> untranslatable(callee.toLispy())
            }
            when (calleeName) {
                "!" -> {
                    append("!(")
                    appendExpr(t.child(1), skipParens = true)
                    append(")")
                }
                "+", "<", "<=", "=" -> {
                    if (!skipParens) { append('(') }
                    appendExpr(t.child(1))
                    append(' ')
                    append(calleeName)
                    append(' ')
                    appendExpr(t.child(2))
                    if (!skipParens) { append(')') }
                }
                "println" -> {
                    append("System.out.println(")
                    appendExpr(t.child(1), skipParens = true)
                    append(")")
                }
                else -> untranslatable("Cannot translate fn named `$calleeName`")
            }
            Unit
        }
    }

    private fun untranslatable(problem: String): Nothing =
        throw IllegalArgumentException(problem)

    private fun appendControlFlow(cf: ControlFlow) {
        when (cf) {
            is ControlFlow.If -> {
                append("if (")
                appendExpr(cf.condition)
                append(") {\n")
                appendControlFlow(cf.thenClause)
                if (!cf.elseClause.isEmptyBlock()) {
                    append("} else {\n")
                    appendControlFlow(cf.elseClause)
                }
                append("}\n")
            }
            is ControlFlow.Loop -> {
                val label = cf.label
                if (label != null) {
                    appendName(label)
                    append(": ")
                }
                when {
                    cf.checkPosition == LeftOrRight.Right -> {
                        check(cf.increment.isEmptyBlock())
                        append("do {\n")
                        appendControlFlow(cf.body)
                        append("} while (")
                        appendExpr(cf.condition)
                        append(");\n")
                    }
                    cf.increment.isEmptyBlock() -> {
                        append("while (")
                        appendExpr(cf.condition)
                        append(") {\n")
                        appendControlFlow(cf.body)
                        append("}\n")
                    }
                    else -> {
                        append("for (; ")
                        appendExpr(cf.condition)
                        append("; ")
                        for ((i, stmt) in cf.increment.stmts.withIndex()) {
                            if (i != 0) {
                                append(", ")
                            }
                            appendExpr((stmt as ControlFlow.Stmt).ref)
                        }
                        append(") {\n")
                        appendControlFlow(cf.body)
                        append("}\n")
                    }
                }
            }
            is ControlFlow.Jump -> {
                when (cf.jumpKind) {
                    BreakOrContinue.Break -> append("break")
                    BreakOrContinue.Continue -> append("continue")
                }
                when (val target = cf.target) {
                    is NamedJumpSpecifier -> {
                        append(' ')
                        appendName(target.label)
                    }
                    is DefaultJumpSpecifier -> {}
                    is UnresolvedJumpSpecifier -> untranslatable("$target")
                }
                append(";\n")
            }
            is ControlFlow.Labeled -> {
                check(cf.continueLabel == null)
                appendName(cf.breakLabel)
                append(": {\n")
                appendControlFlow(cf.stmts)
                append("}\n")
            }
            is ControlFlow.OrElse -> {
                append("try {\n")
                appendControlFlow(cf.orClause.stmts)
                append("} catch (Throwable ")
                appendName(cf.orClause.breakLabel)
                append(") {\n")
                appendControlFlow(cf.elseClause)
                append("}\n")
            }
            is ControlFlow.Stmt -> {
                val t = blockTree.dereference(cf.ref)!!.target
                if (isBubbleCall(t)) {
                    append("throw new RuntimeException()")
                } else {
                    appendExpr(t, skipParens = true)
                }
                append(";\n")
            }
            is ControlFlow.StmtBlock -> {
                for (stmt in cf.stmts) {
                    appendControlFlow(stmt)
                }
            }
        }
    }

    private var indentLevel = 2 // leave room for class and main wrapper
    private var atStartOfLine = true
    private val javaSourceText = StringBuilder()
    private fun appendIgnoreMetaChars(chars: String) {
        javaSourceText.append(chars)
    }

    override fun append(csq: CharSequence?) =
        append(csq, 0, csq!!.length)

    override fun append(csq: CharSequence?, start: Int, end: Int): JavaControlFlowConverter {
        require(csq != null)
        for (i in start until end) {
            append(csq[i])
        }
        return this
    }

    override fun append(c: Char): JavaControlFlowConverter {
        var indentLevelAfterAppend = indentLevel
        var isSpace = false
        when (c) {
            '\n' -> {
                atStartOfLine = true
                isSpace = true
            }
            ' ', '\t' -> isSpace = true
            '{', '(', '[' -> {
                indentLevelAfterAppend += 1
            }
            '}', ')', ']' -> {
                indentLevel = max(0, indentLevel - 1)
                indentLevelAfterAppend = indentLevel
            }
        }
        if (!isSpace && atStartOfLine) {
            atStartOfLine = false
            repeat(indentLevel) {
                javaSourceText.append("  ")
            }
        }
        javaSourceText.append(c)
        indentLevel = indentLevelAfterAppend
        return this
    }
}

/**
 * Takes the content of a Java main method, compiles, and runs it returning the
 * standard out, and a function that can be called to delete any temp files created.
 */
expect fun compileAndRunJavaAndGetStdout(javaSourceText: String): Pair<String, () -> Unit>
