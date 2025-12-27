package lang.temper.astbuild

import lang.temper.ast.flatten
import lang.temper.common.AtomicCounter
import lang.temper.common.ListBackedLogSink
import lang.temper.common.mightRepeatedTestsBeSlooow
import lang.temper.common.testCodeLocation
import lang.temper.common.testModuleName
import lang.temper.common.withRandomForTest
import lang.temper.lexer.Genre
import lang.temper.lexer.Lexer
import lang.temper.log.CodeLocation
import lang.temper.log.CodeLocationKey
import lang.temper.log.ConfigurationKey
import lang.temper.log.SharedLocationContext
import lang.temper.name.NamingContext
import lang.temper.parser.parse
import lang.temper.value.DependencyCategory
import lang.temper.value.DocumentContext
import lang.temper.value.Tree
import lang.temper.value.toLispy
import kotlin.test.Test

private data class Run(
    val i: Int,
    val input: String,
    val ast: Tree,
    val logSink: ListBackedLogSink,
)

class GenerativeGrammarFuzzTest {
    @Test
    fun fuzzy() = withRandomForTest { prng ->
        val nRuns = if (mightRepeatedTestsBeSlooow) 50 else 1000
        var nProgramsGenerated = 0
        var shortestFailing: Run? = null

        for (i in 0 until nRuns) {
            val logSink = ListBackedLogSink()
            val tokens = GenerativeGrammar.generate(prng, 50) ?: continue
            val input = tokens.joinToString(" ") { it.tokenText }

            nProgramsGenerated += 1

            val lexer = Lexer(testCodeLocation, logSink, input)
            val cst = parse(lexer, logSink)
            val context = object : ConfigurationKey, DocumentContext, SharedLocationContext, NamingContext() {
                override val loc = testModuleName
                override val definitionMutationCounter = AtomicCounter()
                override val namingContext: NamingContext get() = this
                override val genre = Genre.Library
                override val dependencyCategory = DependencyCategory.Production
                override val configurationKey: ConfigurationKey get() = this
                override val sharedLocationContext: SharedLocationContext get() = this
                override fun <T : Any> get(loc: CodeLocation, v: CodeLocationKey<T>): T? = null
            }
            val ast = buildTree(flatten(cst), StoredCommentTokens.empty, logSink, context)

            if (
                logSink.hasFatal &&
                (shortestFailing == null || shortestFailing.input.length > input.length)
            ) {
                shortestFailing = Run(i, input, ast, logSink)
            }
        }

        if (shortestFailing != null) {
            val (i, input, ast, logSink) = shortestFailing
            require(!logSink.hasFatal) {
                "$i: `${
                    input
                }`\n\n${
                    logSink.allEntries.joinToString("\n") {
                        it.messageText
                    }
                }\n${ ast.toLispy() }"
            }
        }

        require(nProgramsGenerated >= (nRuns / 8)) { "$nProgramsGenerated / $nRuns" }
    }
}
