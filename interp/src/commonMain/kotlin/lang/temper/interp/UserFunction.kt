package lang.temper.interp

import lang.temper.ast.TreeVisit
import lang.temper.ast.VisitCue
import lang.temper.common.Either
import lang.temper.common.withCapturingConsole
import lang.temper.env.Environment
import lang.temper.env.InterpMode
import lang.temper.format.OutToks
import lang.temper.format.TokenSerializable
import lang.temper.format.TokenSink
import lang.temper.log.MessageTemplate
import lang.temper.log.Position
import lang.temper.name.ParsedName
import lang.temper.name.ResolvedName
import lang.temper.name.Symbol
import lang.temper.name.TemperName
import lang.temper.type.SuperTypeTree
import lang.temper.type.WellKnownTypes
import lang.temper.type.helpfulFromMetadata
import lang.temper.type2.InterpValueFormal
import lang.temper.type2.Signature2
import lang.temper.value.ActualValues
import lang.temper.value.Actuals
import lang.temper.value.CallableValue
import lang.temper.value.Fail
import lang.temper.value.FunTree
import lang.temper.value.Helpful
import lang.temper.value.InterpreterCallback
import lang.temper.value.OccasionallyHelpful
import lang.temper.value.PartialResult
import lang.temper.value.StayLeaf
import lang.temper.value.StaySink
import lang.temper.value.TEdge
import lang.temper.value.Tree

/**
 * Allows any kind of object or [Fail].
 * TODO Put this somewhere more general?
 */
typealias OrFail<Result> = Either<Result, Fail>

/**
 * A function backed by user code.
 */
sealed class UserFunction(
    val pos: Position,
    val fnName: Symbol?,
    val closedOverEnvironment: Environment,
    val signature: Signature2,
    val interpFormals: List<InterpValueFormal>?,
    val superTypes: SuperTypeTree,
    val formalNamesByIndex: List<TemperName>,
    val isSelfContained: Boolean,
) : CallableValue, TokenSerializable, OccasionallyHelpful {
    override val sigs = listOf(signature)

    abstract val yieldedAt: InProgressEvaluation?

    val hasYielded get() = yieldedAt != null

    override val callMayFailPerSe: Boolean
        get() =
            // It is a static error for a user function's return type to exclude Bubble when
            // there's a path from the body start to the failure exit.
            // This is reliable when that check passes.
            signature.returnType2.definition == WellKnownTypes.resultTypeDefinition

    override fun renderTo(tokenSink: TokenSink) {
        if (fnName != null) {
            tokenSink.emit(OutToks.fnWord)
            tokenSink.emit(ParsedName(fnName.text).toToken(inOperatorPosition = false))
        } else {
            tokenSink.emit(OutToks.functionDisplayName)
        }
    }

    protected abstract fun bodyAndReturnDecl(
        cb: InterpreterCallback,
    ): OrFail<BodyAndReturnDecl>

    internal fun invokeUnpositioned(
        args: Actuals,
        cb: InterpreterCallback,
        interpMode: InterpMode,
    ): PartialResult {
        val (body, returnDecl) = when (val maybe = bodyAndReturnDecl(cb)) {
            is Either.Left -> maybe.item
            is Either.Right -> return maybe.item
        }
        return Interpreter.interpreterFor(cb).interpretFunctionBody(
            userFunction = this,
            pos = pos,
            fnName = fnName,
            closedOverEnvironment = closedOverEnvironment,
            signature = signature,
            interpFormals = interpFormals,
            formalNamesByIndex = formalNamesByIndex,
            returnDecl = returnDecl,
            actuals = args,
            body = body,
            cb = cb,
            im = interpMode,
        )
    }

    override fun invoke(
        args: ActualValues,
        cb: InterpreterCallback,
        interpMode: InterpMode,
    ): PartialResult = invokeUnpositioned(args, cb, interpMode)

    protected abstract fun getFnNodeBestEffort(): FunTree?

    override fun prettyPleaseHelp(): Helpful? {
        val fnNode = getFnNodeBestEffort()
        val parts = fnNode?.parts ?: return null
        return UserFunctionHelp(
            fnName,
            helpfulFromMetadata(parts.metadataSymbolMultimap),
            parts.formals.mapNotNull { formalDecl ->
                formalDecl.parts?.let { formalParts ->
                    formalParts.name.content to
                        helpfulFromMetadata(formalParts.metadataSymbolMultimap)
                }
            },
        ).prettyPleaseHelp()
    }

    data class BodyAndReturnDecl(
        val body: Tree,
        val returnDecl: TEdge?,
    )
}

/**
 * A user function that need not survive past a single stage evaluation.
 */
internal class TransientUserFunction(
    pos: Position,
    fnName: Symbol?,
    closedOverEnvironment: Environment,
    signature: Signature2,
    interpFormals: List<InterpValueFormal>?,
    formalNamesByIndex: List<TemperName>,
    isSelfContained: Boolean,
    val returnDecl: TEdge?,
    superTypes: SuperTypeTree,
    val body: Tree,
) : UserFunction(
    pos = pos,
    fnName = fnName,
    closedOverEnvironment = closedOverEnvironment,
    signature = signature,
    interpFormals = interpFormals,
    superTypes = superTypes,
    formalNamesByIndex = formalNamesByIndex,
    isSelfContained = isSelfContained,
) {
    override val isPure: Boolean get() = false

    override var yieldedAt: InProgressEvaluation? =
        null

    override fun bodyAndReturnDecl(cb: InterpreterCallback) =
        Either.Left(BodyAndReturnDecl(body, returnDecl))

    override fun addStays(s: StaySink) {
        for (signature in sigs) {
            signature.addStays(s)
        }
        closedOverEnvironment.addStays(s)
        for (tree in listOfNotNull(returnDecl?.target, body)) {
            TreeVisit.startingAt(tree)
                .forEach {
                    if (it is StayLeaf) {
                        s.add(it)
                    }
                    VisitCue.Continue
                }
                .visitPreOrder()
        }
    }

    override fun getFnNodeBestEffort(): FunTree? = body.incoming?.source as? FunTree
}

/**
 * A user function that may survive all the way through to the backend.
 */
class LongLivedUserFunction(
    pos: Position,
    fnName: Symbol?,
    closedOverEnvironment: Environment,
    signature: Signature2,
    interpFormals: List<InterpValueFormal>,
    formalNamesByIndex: List<ResolvedName>,
    isSelfContained: Boolean,
    superTypes: SuperTypeTree,
    val stayLeaf: StayLeaf,
) : UserFunction(
    pos = pos,
    fnName = fnName,
    closedOverEnvironment = closedOverEnvironment,
    signature = signature,
    interpFormals = interpFormals,
    superTypes = superTypes,
    formalNamesByIndex = formalNamesByIndex,
    isSelfContained = isSelfContained,
) {
    override val yieldedAt: Nothing? get() = null

    override fun bodyAndReturnDecl(
        cb: InterpreterCallback,
    ): OrFail<BodyAndReturnDecl> {
        val fnTree = stayLeaf.incoming?.source as? FunTree
            ?: return Either.Right(cb.fail(MessageTemplate.DidNotStayConsistent))
        val fnParts = fnTree.parts
            ?: return Either.Right(cb.fail(MessageTemplate.MalformedFunction))
        val body = fnParts.body
        val returnDecl = fnParts.returnDecl
        return Either.Left(
            BodyAndReturnDecl(body, returnDecl?.incoming),
        )
    }

    override val isPure: Boolean get() =
        isSelfContained && closedOverEnvironment is EmptyEnvironment

    override fun addStays(s: StaySink) {
        s.add(stayLeaf)
        for (signature in sigs) {
            signature.addStays(s)
        }
        closedOverEnvironment.addStays(s)
    }

    override fun getFnNodeBestEffort(): FunTree? =
        stayLeaf.incoming?.source as? FunTree
}

private class UserFunctionHelp(
    val fnName: Symbol?,
    val fnHelp: OccasionallyHelpful?,
    val paramHelp: List<Pair<TemperName, OccasionallyHelpful?>>,
) : OccasionallyHelpful {
    override fun prettyPleaseHelp(): Helpful? {
        val fnHelpful = fnHelp?.prettyPleaseHelp()
        val paramHelpful = paramHelp.map {
            it.first to it.second?.prettyPleaseHelp()
        }
        if (fnHelpful == null && paramHelpful.all { it.second == null }) {
            return null
        }
        return object : Helpful {
            override fun briefHelp(): String =
                fnHelpful?.briefHelp() ?: fnName?.text ?: "anonymous function"

            override fun longHelp(): String = withCapturingConsole { docConsole ->
                if (fnHelpful != null) {
                    docConsole.log(fnHelpful.longHelp())
                    docConsole.log("")
                }
                for ((paramName, paramHelp) in paramHelpful) {
                    docConsole.group("- $paramName") {
                        if (paramHelp != null) {
                            docConsole.log(paramHelp.longHelp())
                        }
                    }
                    docConsole.log("")
                }
            }.second.trimEnd()
        }
    }
}
