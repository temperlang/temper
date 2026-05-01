package lang.temper.be.data

import lang.temper.be.Backend
import lang.temper.be.assertGeneratedCode
import lang.temper.be.inputFileMapFromJson
import lang.temper.log.FilePath
import kotlin.test.Test

class DataBackendTest {
    @Test
    fun doingNormalThingsThatHaveNothingToDoWithDataFiles() {
        assertGenerated(
            inputs = inputFileMapFromJson(
                """
                    |{
                    |  foo: {
                    |    bar.temper: ```
                    |      export let pi = 3; // ish
                    |      ```
                    |  }
                    |}
                """.trimMargin(),
            ),
            want = """
                |{
                |  data: {
                |    my-test-library: {}
                |  }
                |}
            """.trimMargin(),
        )
    }

    @Test
    fun creatingSomeDataFiles() {
        assertGenerated(
            inputs = inputFileMapFromJson(
                """
                    |{
                    |  foo: {
                    |    bar: {
                    |      baz.temper: ```
                    |        // This path should be resolved relative to foo/bar
                    |        dataFile("baz/./something.json", "application/json", '"something content"');
                    |
                    |        // If it starts with / then it's resolved relative to the library root.
                    |        dataFile("/boo/other.json", "application/json", '["other content"]');
                    |
                    |        // A dataFile can go in the parent directory
                    |        dataFile("../parent.txt", "text/plain", "in parent dir");
                    |
                    |        export let pi = 3; // ish
                    |        ```
                    |    }
                    |  }
                    |}
                """.trimMargin(),
            ),
            want = """
                |{
                |  data: {
                |    my-test-library: {
                |      foo: {
                |        bar: {
                |          baz: {
                |            something.json: {
                |              content: "\"something content\"",
                |              jsonContent: "something content",
                |            }
                |          }
                |        },
                |        parent.txt: {
                |          content: "in parent dir",
                |        }
                |      },
                |      boo: {
                |        other.json: {
                |          content: "[\"other content\"]",
                |          jsonContent: ["other content"],
                |        }
                |      }
                |    }
                |  }
                |}
            """.trimMargin(),
        )
    }
}

private fun assertGenerated(inputs: List<Pair<FilePath, String>>, want: String) {
    assertGeneratedCode(
        backendConfig = Backend.Config.production,
        factory = DataBackend.Factory,
        inputs = inputs,
        moduleResultNeeded = false,
        want = want,
    )
}
