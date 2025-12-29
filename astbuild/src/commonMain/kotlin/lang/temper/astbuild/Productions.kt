package lang.temper.astbuild

import lang.temper.ast.AstPart
import lang.temper.ast.CstPart
import lang.temper.ast.FinishTree
import lang.temper.ast.ProductionFailedEvent
import lang.temper.ast.SoftBlock
import lang.temper.ast.StartTree
import lang.temper.log.CodeLocation
import lang.temper.log.LogSink
import lang.temper.log.MessageTemplate
import lang.temper.log.Position
import lang.temper.value.Document
import lang.temper.value.DocumentContext
import kotlin.math.max

/** A grammar maps names to combinators. */
class Productions<out TREE>(private val lifter: (List<AstPart>, LogSink, Document) -> List<TREE>) {
    private val nameToCombinatorMap = mutableMapOf<String, Combinator>()

    internal fun declare(name: String, body: Combinator) {
        require(name !in nameToCombinatorMap) { "duplicate $name" }
        nameToCombinatorMap[name] = body
    }

    /**
     * Applies the named production to the input.
     * Returns {output, position} where position is after parse.
     * @param mustConsumeAllParts true to bundle any unparsed content into an error.
     */
    fun apply(
        startProduction: String,
        input: List<CstPart>,
        storedCommentTokens: StoredCommentTokens,
        logSink: LogSink,
        documentContext: DocumentContext,
        mustConsumeAllParts: Boolean,
    ): Pair<TREE, Int> {
        val rootLeft = input.firstOrNull()?.pos?.leftEdge ?: fallbackPosition

        val document = Document(documentContext)

        val context = CombinatorContext(
            productions = this,
            input = input,
            loc = rootLeft.loc,
            storedCommentTokens = storedCommentTokens,
        )

        context.output.add(StartTree(rootLeft))
        val inputElementsUsed = Ref(startProduction).apply(context, 0)
        val rootRight = if (inputElementsUsed > 0) {
            input[inputElementsUsed - 1].pos.rightEdge
        } else {
            rootLeft
        }

        if (mustConsumeAllParts && inputElementsUsed != input.size) {
            context.output.add(
                ProductionFailedEvent(
                    messageTemplate = MessageTemplate.Unparsable,
                    messageValues = listOf(startProduction),
                    parts = input.subList(max(0, inputElementsUsed), input.size),
                ),
            )
        }
        context.output.add(FinishTree(rootRight, SoftBlock))

        val lifted = lifter(context.output.toList(), logSink, document)
        // Since we bracket the output with a single StartTree and FinishTree event, there
        // should be exactly one tree on lifted.
        require(lifted.size == 1) {
            context.output.toString()
        }
        return lifted[0] to inputElementsUsed
    }

    internal fun getProduction(name: String) = nameToCombinatorMap[name]

    internal val productionNames get() = nameToCombinatorMap.keys
}

private object EmptyFile : CodeLocation {
    override val diagnostic = "<empty>"
}
private val fallbackPosition = Position(EmptyFile, 0, 0)
