package lang.temper.frontend

import lang.temper.common.EnumRange
import lang.temper.common.json.JsonArray
import lang.temper.common.json.JsonObject
import lang.temper.common.json.JsonString
import lang.temper.common.testCodeLocation
import lang.temper.log.Position
import lang.temper.name.ParsedName
import lang.temper.stage.Stage
import lang.temper.value.BlockChildReference
import lang.temper.value.BlockTree
import lang.temper.value.ControlFlow
import lang.temper.value.Document
import lang.temper.value.LinearFlow
import lang.temper.value.RightNameLeaf
import lang.temper.value.StructuredFlow
import lang.temper.value.TStageRange
import lang.temper.value.TString
import lang.temper.value.Value
import kotlin.test.Test
import kotlin.test.assertTrue

class FunctionMacroStageTest {
    @Test
    fun compileLogExecutionOrder() = assertModuleAtStage(
        stage = Stage.FunctionMacro,
        want = """
        {
          functionMacro: {
            body:
            ```
            compilelog("1", @F);
            if (c) {
              compilelog("2", @F)
            } else {
              compilelog("3", @F)
            };
            compilelog("4", @F)

            ```
          },
          stdout:
          ```
          clog:F: 1
          clog:F: 2
          clog:F: 3
          clog:F: 4

          ```
        }
        """,
    ) { module, _ ->
        val loc = testCodeLocation
        val doc = Document(module)
        val pos = Position(loc, 0, 0)

        val vStageRange = Value(
            EnumRange(Stage.FunctionMacro, Stage.FunctionMacro),
            TStageRange,
        )
        fun logCall(text: String) = doc.treeFarm.grow(pos) {
            Call {
                Rn(ParsedName("compilelog"))
                V(Value(text, TString))
                V(vStageRange)
            }
        }

        // We build a tree representing this
        //     compilelog("1", @F)
        //     if (c) {
        //       compilelog("2", @F)
        //     } else {
        //       compilelog("3", @F)
        //     }
        //     compilelog("4", @F)
        // expressed using this flow
        //                     clog("2")#1
        //               (c)#3/         \
        //   clog("1")#2 -----           ----- clog("4")#0
        //                    \         /
        //                     clog("3")#4
        // where the numbers after the # are the block reference indices for those expressions

        val root = BlockTree(
            doc,
            pos,
            listOf(
                logCall("4"),
                logCall("2"),
                logCall("1"),
                RightNameLeaf(doc, pos, ParsedName("c")),
                logCall("3"),
            ),
            LinearFlow, // replaced
        )

        val controlFlow = ControlFlow.StmtBlock(
            pos,
            listOf(
                ControlFlow.Stmt(BlockChildReference(2, pos)),
                ControlFlow.If(
                    pos,
                    BlockChildReference(3, pos),
                    ControlFlow.StmtBlock.wrap(
                        ControlFlow.Stmt(BlockChildReference(1, pos)),
                    ),
                    ControlFlow.StmtBlock.wrap(
                        ControlFlow.Stmt(BlockChildReference(4, pos)),
                    ),
                ),
                ControlFlow.Stmt(BlockChildReference(0, pos)),
            ),
        )

        root.replaceFlow(StructuredFlow(controlFlow))

        module.deliverContent(
            root,
        )
    }

    @Test
    fun multiInitErrorInClass() = assertModuleAtStage(
        stage = Stage.FunctionMacro,
        input = "class Aha(private hmm: Int) {}; class Boo { let { hmm } = new Aha(1) }",
        manualCheck = { got ->
            val errors = (got["errors"] as JsonArray).map {
                (((it as JsonObject)["formatted"]) as JsonString).content
            }
            val wantedErrors = listOf(
                """Declaration is malformed""",
            )
            for (wantedError in wantedErrors) {
                assertTrue(errors.any { it.contains(Regex(wantedError)) }, "Expected: $wantedError")
            }
        },
    )
}
