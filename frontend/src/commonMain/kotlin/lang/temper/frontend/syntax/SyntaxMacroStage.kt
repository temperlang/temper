package lang.temper.frontend.syntax

import lang.temper.ast.TreeVisit
import lang.temper.builtin.BuiltinFuns
import lang.temper.common.Either
import lang.temper.common.Log
import lang.temper.frontend.AstSnapshotKey
import lang.temper.frontend.Module
import lang.temper.frontend.StageOutputs
import lang.temper.frontend.disambiguate.atBeginning
import lang.temper.frontend.flipDeclaredNames
import lang.temper.frontend.interpretiveDanceStage
import lang.temper.lexer.Genre
import lang.temper.log.Debug
import lang.temper.log.FailLog
import lang.temper.log.LogEntry
import lang.temper.log.LogSink
import lang.temper.log.MessageTemplate
import lang.temper.log.snapshot
import lang.temper.name.ImplicitsCodeLocation
import lang.temper.name.ParsedName
import lang.temper.name.TemperName
import lang.temper.stage.Stage
import lang.temper.value.BlockTree
import lang.temper.value.CallTree
import lang.temper.value.DeclTree
import lang.temper.value.FunTree
import lang.temper.value.LinearFlow
import lang.temper.value.NameLeaf
import lang.temper.value.RightNameLeaf
import lang.temper.value.TBoolean
import lang.temper.value.TEdge
import lang.temper.value.Tree
import lang.temper.value.consoleBuiltinName
import lang.temper.value.fnParsedName
import lang.temper.value.fnSymbol
import lang.temper.value.freeTarget
import lang.temper.value.freeTree
import lang.temper.value.getConsoleBuiltinName
import lang.temper.value.initSymbol
import lang.temper.value.labelSymbol
import lang.temper.value.nameContained
import lang.temper.value.outTypeSymbol
import lang.temper.value.returnParsedName
import lang.temper.value.symbolContained
import lang.temper.value.typeFormalSymbol
import lang.temper.value.vFnSymbol
import lang.temper.value.vHoistLeftSymbol
import lang.temper.value.vInitSymbol
import lang.temper.value.vLabelSymbol
import lang.temper.value.vReturnDeclSymbol
import lang.temper.value.vReturnedFromSymbol
import lang.temper.value.vTypeFormalSymbol
import lang.temper.value.vTypeSymbol
import lang.temper.value.vWordSymbol
import lang.temper.value.varSymbol
import lang.temper.value.void
import lang.temper.value.wordSymbol
import kotlin.math.min

internal class SyntaxMacroStage(
    private val module: Module,
    private val root: BlockTree,
    private val failLog: FailLog,
    private val logSink: LogSink,
) {
    fun process(callback: (StageOutputs) -> Unit) {
        val configKey = root.configurationKey
        val outputs = Debug.Frontend.SyntaxMacroStage(configKey).group("SyntaxMacro Stage") {
            interpretiveDanceStage(
                stage = Stage.SyntaxMacro,
                root = root,
                failLog = failLog,
                logSink = logSink,
                module = module,
                beforeInterpretation = { root, _ ->
                    Debug.Frontend.SyntaxMacroStage.Before.snapshot(configKey, AstSnapshotKey, root)
                },
                afterInterpretation = { (root), _ ->
                    Debug.Frontend.SyntaxMacroStage.AfterInterpretation
                        .snapshot(configKey, AstSnapshotKey, root)

                    attachQNameMetadata(module, root)
                    turnEmbeddedCommentsIntoDocStrings(root)
                    flipDeclaredNames(root)
                    promoteOutTypesToReturnDecls(root)
                    hoistLeft(root)
                    resolveNames(root, logSink)
                    simplifyMultiAssignments(root)
                    DotOperationDesugarer(root, logSink, considerExtensions = true).desugar()
                    declareModuleConsole(root)
                    // Convert object literals before sorting, so we know any references are in place.
                    ConvertObjectLiteralNew(failLog = failLog).process(root)
                    sortTopLevels(root)
                    if (module.genre == Genre.Documentation) {
                        pullDocFunctionTypeFormalsIntoFold(root)
                    } else {
                        simplifyAssignedGroupingBlocks(root)
                    }

                    Debug.Frontend.SyntaxMacroStage.After.snapshot(configKey, AstSnapshotKey, root)
                },
            )
        }

        callback(outputs)
    }
}

private fun declareModuleConsole(root: BlockTree) {
    root.document.context.genre == Genre.Documentation && return
    root.pos.loc is ImplicitsCodeLocation && return
    val consoleRefs = buildList {
        TreeVisit.startingAt(root).forEachContinuing { node ->
            if ((node as? RightNameLeaf)?.content == consoleBuiltinName) {
                add(node)
            }
        }.visitPreOrder()
    }
    consoleRefs.isEmpty() && return
    var consoleName: TemperName? = null
    // Insert console at top but after any labels in case root is labeled.
    root.replace(root.atActualBeginning()) {
        Decl {
            Ln { it.unusedTemporaryName("console").also { name -> consoleName = name } }
            V(vInitSymbol)
            Call {
                Rn(getConsoleBuiltinName)
            }
        }
    }
    for (ref in consoleRefs) {
        ref.incoming!!.replace { Rn(consoleName!!) }
    }
}

/**
 * Skips label(s? or does anything else need skipped) at the beginning of a block.
 * Returns the empty range after that.
 */
fun Tree.atActualBeginning(): IntRange {
    return when (childOrNull(0)?.symbolContained) {
        labelSymbol -> childOrNull(1)?.nameContained?.let { IntRange(2, 1) }
        else -> null
    } ?: atBeginning
}

// We add labels to function bodies so the desugaring of return to break
// finds the right label.
private fun labelFunctionBody(body: Tree): Tree {
    require(body.document.context.genre != Genre.Documentation)
    val pos = body.pos
    val leftPos = pos.leftEdge
    return if (
        body is BlockTree &&
        body.flow == LinearFlow &&
        body.parts.label == null
    ) {
        body.replace(atBeginning) {
            V(leftPos, vLabelSymbol)
            Ln(leftPos) { fnParsedName } // Safe during syntax stage
        }
        body
    } else {
        body.treeFarm.grow {
            Block(pos) {
                V(leftPos, vLabelSymbol)
                Ln(leftPos) { fnParsedName } // Safe during syntax stage
                Replant(body)
            }
        }
    }
}

/**
 * Convert parsed function definitions to actual function trees or to function types.
 *
 * This takes a form like
 *
 *     (Call
 *         fn
 *         \word       functionName
 *         \typeArg    typeFormal
 *         complexArg0
 *         argName1
 *         \outType typeExpression
 *         body
 *     )
 *
 * and converts it to a form compatible with [decomposeFun][lang.temper.value.decomposeFun]:
 *
 *     (Fun
 *         formal0
 *         formal1
 *         \word       functionName
 *         \typeFormal reifiedTypeFormal
 *         \returnDecl declaration
 *         body
 *     )
 *
 * Given a function construct without a body, it treats it like a function definition.
 *
 *     (Call
 *         fn
 *         inputType
 *         \outType typeExpression
 *     )
 *
 * becomes a call to
 *
 *     (Call
 *         fnType
 *         inputType
 *         \outType
 *     )
 *
 * It may surround either with a block so that type formals scope properly.
 */
internal fun rewriteFun(
    call: CallTree,
    isDeclaration: Boolean,
): Either<Tree, LogEntry> {
    var wordSymbolEdge: TEdge? = null
    var name: NameLeaf? = null
    val decls = mutableListOf<DeclTree>()
    var outputType: TEdge? = null

    // `fn (T): T` is a type expression because it lacks a trailing block function that should
    // become a function body as in `fn (x): T { ... }`.
    val isTypeExpr = when {
        isDeclaration -> false // `let` form defines and declares a function
        call.size >= 2 && call.child(call.size - 1) is FunTree -> false // There's a trailing block
        else -> true
    }

    var i = 1 // skip callee
    // Look for the word first.
    if (!isTypeExpr && i + 2 < call.size && call.child(i).symbolContained == wordSymbol) {
        val target = call.child(i + 1)
        if (target !is NameLeaf) {
            return Either.Right(
                LogEntry(Log.Error, MessageTemplate.IsNotAName, target.pos, emptyList()),
            )
        }
        wordSymbolEdge = call.edge(i)
        name = target
        i += 2
    }

    // Do we need to create a declaration or can we reuse one that already exists?
    var reuseOuterDeclaration = false
    if (!isDeclaration && !isTypeExpr) {
        // Look upwards to see if we're in a construct like
        //     let f = fn (...) { ... };
        // Adopt `f` as the name.  This change aids debugging and leads to a cleaner AST for
        // method declarations.
        val callEdge = call.incoming
        val parent = callEdge?.source
        val parentParts = (parent as? DeclTree)?.parts
        if (
            parentParts != null && callEdge == parentParts.metadataSymbolMap[initSymbol] &&
            // Don't reuse the name if the value could be overridden because it's a `var` decl.
            varSymbol !in parentParts.metadataSymbolMap
        ) {
            when {
                parentParts.name.content == name?.content -> { // Same name
                    reuseOuterDeclaration = true
                }
                name == null && parentParts.name.content is ParsedName -> { // Adopt name
                    reuseOuterDeclaration = true
                    name = parentParts.name.copyRight()
                    call.replace(i until i) {
                        V(name.pos.leftEdge, wordSymbol)
                        Replant(name)
                    }
                    wordSymbolEdge = call.edge(i)
                    i += 2
                }
                else -> Unit
            }
            if (reuseOuterDeclaration) {
                val metadataPos = callEdge.target.pos.leftEdge
                if (parent.parts?.metadataSymbolMap?.containsKey(fnSymbol) != true) {
                    parent.insert {
                        V(metadataPos, fnSymbol)
                        V(metadataPos, void)
                    }
                }
            }
        }
    }

    // This is used as a limit for loops below, but in a type expr there isn't a body.
    val bodyIndex = if (isTypeExpr) call.size else call.size - 1
    if (!isTypeExpr && bodyIndex < 0) {
        return Either.Right(
            LogEntry(Log.Error, MessageTemplate.MalformedDeclaration, call.pos, emptyList()),
        )
    }
    // Consume type formals
    val typeFormals = mutableListOf<Tree>()
    while (i + 1 < bodyIndex) {
        val symbol = call.child(i).symbolContained
        if (symbol != typeFormalSymbol) { break }
        val follower = call.child(i + 1)
        typeFormals.add(follower)
        i += 2
    }
    val typeFormalStatements = mutableListOf<Tree>()
    // Split type formals so that the declaration and any type macros are scoped properly.
    //     { let T = ...; T extends ...; T }
    // leaves the first 2 on typeFormalStatements and the last on typeFormals.
    for (typeFormalIndex in typeFormals.indices) {
        val typeFormal = typeFormals[typeFormalIndex]
        if (typeFormal is BlockTree && typeFormal.flow is LinearFlow && typeFormal.size != 0) {
            val lastIndexInBlock = typeFormal.size - 1
            typeFormals[typeFormalIndex] = typeFormal.child(lastIndexInBlock)
            typeFormalStatements.addAll(
                typeFormal.children.subList(0, lastIndexInBlock),
            )
            typeFormal.replace(0..lastIndexInBlock) { }
        }
    }

    if (isTypeExpr) {
        // We've done all the early processing we need.
        // Produce
        // (Block
        //    ...typeFormalStatements
        //     (Call `fnType` \typeFormal typeFormal0 ... \typeFormal typeFormalN ...restOfArgs)
        var replacement: Tree = call.treeFarm.grow {
            Call(call.pos) {
                V(call.child(0).pos, BuiltinFuns.vFnTypeFn)
                for (typeFormal in typeFormals) {
                    V(typeFormal.pos.leftEdge, vTypeFormalSymbol)
                    Replant(typeFormal)
                }
                for (index in i until call.size) {
                    Replant(freeTree(call.child(index)))
                }
            }
        }
        if (typeFormalStatements.isNotEmpty()) {
            replacement = call.treeFarm.grow {
                Block(call.pos) {
                    typeFormalStatements.forEach {
                        Replant(freeTree(it))
                    }
                    Replant(replacement)
                }
            }
        }
        return Either.Left(replacement)
    }

    // Consume decls
    while (i < bodyIndex) {
        val target = call.child(i) as? DeclTree ?: break
        decls.add(target)
        i += 1
    }

    if (i + 1 < bodyIndex && call.child(i).symbolContained == outTypeSymbol) {
        outputType = call.edge(i + 1)
        i += 2
    }

    // Look for a body.
    if (i != bodyIndex) {
        // If both body and name are missing, then bodyIndex could be 0,
        // and *i* could be 1 and past the end of the child list.
        val errPos = call.childOrNull(min(i, bodyIndex))?.pos ?: call.pos
        return Either.Right(
            LogEntry(Log.Error, MessageTemplate.MalformedDeclaration, errPos, emptyList()),
        )
    }
    val bodyTree = call.child(bodyIndex)
    if (bodyTree !is FunTree) {
        return Either.Right(
            LogEntry(Log.Error, MessageTemplate.MalformedDeclaration, bodyTree.pos, emptyList()),
        )
    }

    if (isDeclaration && name == null) {
        return Either.Right(
            LogEntry(Log.Error, MessageTemplate.MissingName, call.pos, emptyList()),
        )
    }

    val nameLeaf = name?.copyLeft()
    val nameMaker = call.document.nameMaker

    val function = call.treeFarm.grow {
        Fn(call.pos) {
            for (formal in decls) {
                Replant(freeTree(formal))
            }
            if (outputType != null) {
                val outputTypeNode = outputType.target
                V(outputTypeNode.pos.leftEdge, vReturnDeclSymbol)
                Decl(outputTypeNode.pos, nameMaker.unusedSourceName(returnParsedName)) {
                    V(vTypeSymbol)
                    Replant(freeTarget(outputType))
                }
            }
            // Mark this as the target of a `return` statement.
            V(bodyTree.pos.rightEdge, vReturnedFromSymbol)
            V(bodyTree.pos.rightEdge, TBoolean.valueTrue)
            // Retain any existing metadata already on the function,
            // especially for @connected functions.
            for (bodyKid in bodyTree.children.subList(0, bodyTree.size - 1)) {
                Replant(freeTree(bodyKid))
            }
            // Store the name as metadata for diagnostic purposes
            when (val nameSymbol = nameLeaf?.content?.toSymbol()) {
                null -> Unit
                else -> {
                    val namePos = nameLeaf.pos
                    V(wordSymbolEdge?.target?.pos ?: namePos.leftEdge, vWordSymbol)
                    V(namePos, nameSymbol)
                }
            }
            // Emit any type formals.
            for (typeFormal in typeFormals) {
                V(typeFormal.pos.leftEdge, vTypeFormalSymbol)
                Replant(freeTree(typeFormal))
            }
            // Add the body last so that code that needs a body index can quickly find it.
            val bodyLast = freeTarget(bodyTree.edge(bodyTree.size - 1))
            Replant(
                when (call.document.context.genre) {
                    Genre.Library -> labelFunctionBody(bodyLast)
                    Genre.Documentation -> bodyLast
                },
            )
        }
    }
    val functionOrBlock = if (typeFormalStatements.isEmpty()) {
        function
    } else {
        call.treeFarm.grow(call.pos) {
            Block {
                typeFormalStatements.forEach {
                    Replant(it)
                }
                Replant(function)
            }
        }
    }
    val decl = when {
        nameLeaf == null -> null
        reuseOuterDeclaration -> null
        else ->
            call.treeFarm.grow(call.pos) {
                Decl {
                    Replant(nameLeaf.copyLeft())
                    V(nameLeaf.pos.rightEdge, vInitSymbol)
                    Replant(functionOrBlock)
                    V(call.pos.rightEdge, vHoistLeftSymbol)
                    V(call.pos.rightEdge, TBoolean.valueFalse) // Do not hoist initializer
                    V(call.pos.rightEdge, vFnSymbol)
                    V(call.pos.rightEdge, void)
                }
            }
    }

    val replacement = when {
        isDeclaration -> decl!!
        decl != null -> {
            // { let f = /* function value */; f }
            val selfRef = nameLeaf!!.copyRight()
            call.treeFarm.grow {
                Block(decl.pos) {
                    Replant(decl)
                    Replant(selfRef)
                }
            }
        }
        else -> functionOrBlock
    }
    return Either.Left(replacement)
}
