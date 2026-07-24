@file:Suppress("MaxLineLength")

package lang.temper.frontend.parse

import lang.temper.frontend.StageTestDir
import lang.temper.frontend.assertModuleAtStage
import lang.temper.lexer.Genre
import kotlin.test.Test

class ParseStageTest {
    @Test
    fun appendix() = assertModuleAtStage(
        stageTestDir = StageTestDir("parse/appendix"),
    )

    @Test
    fun badUnicodeScalarValues() = assertModuleAtStage(
        stageTestDir = StageTestDir("parse/bad-unicode-scalar-values"),
    )

    @Test
    fun callJoinRewrite() = assertModuleAtStage(
        stageTestDir = StageTestDir("parse/call-join-rewrite"),
    )

    @Test
    fun callJoinRewriteForDocs() = assertModuleAtStage(
        stageTestDir = StageTestDir("parse/call-join-rewrite-for-docs"),
        genre = Genre.Documentation,
    )

    @Test
    fun angleBracketConfusionErrorMessageIsNotSuperTerrible() = assertModuleAtStage(
        stageTestDir = StageTestDir("parse/angle-bracket-confusion-error-message-is-not-super-terrible"),
    )

    @Test
    fun unrepresentableIntegersWarnedOn() = assertModuleAtStage(
        stageTestDir = StageTestDir("parse/unrepresentable-integers-warned-on"),
    )
}
