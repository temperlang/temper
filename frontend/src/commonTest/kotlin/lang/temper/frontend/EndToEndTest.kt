package lang.temper.frontend

import lang.temper.stage.Stage
import kotlin.test.Ignore
import kotlin.test.Test

// TODO: turn these into functional tests and run the functional test suite via runtime-emulation
class EndToEndTest {
    @Ignore // multiAssign not implemented
    @Test
    fun multiAssign() = assertModuleAtStage(
        stage = Stage.Run,
        input = """
            let f(a, b, c) {
              let d = a + b;
              let e = c + 1;
              return [d, e];
            }
            let [x, y] = f(3, 4, 5);
            x * y
        """.trimIndent(),
        moduleResultNeeded = true,
        want = """
        {
          stageCompleted: "Runtime",
          runtime: [
            "42: Int32"
          ]
        }
        """,
    )

    @Test
    fun optionalArgumentPassing() = assertModuleAtStage(
        stage = Stage.Run,
        // %{...} -> ${...}
        input = """
            |let f(a: Int = 0, b: Int = 1): String { "a=%{a.toString()}, b=%{b.toString()}" };
            |"%{ f(2) }; %{ f(null, 2) }; %{ f(3, 2) }"
        """.trimMargin().replace('%', '$'),
        moduleResultNeeded = true,
        want = """
            |{
            |  stageCompleted: "Run",
            |  run:
            |    ```
            |    "a=2, b=1; a=0, b=2; a=3, b=2": String
            |    ```
            |}
        """.trimMargin(),
    )
}
