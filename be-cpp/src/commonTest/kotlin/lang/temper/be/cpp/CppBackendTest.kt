package lang.temper.be.cpp

import lang.temper.be.Backend
import lang.temper.be.assertGeneratedCode
import lang.temper.log.filePath
import kotlin.test.Test

@SuppressWarnings("MaxLineLength")
class CppBackendTest {
    @Test
    fun classOrdering() {
        assertGenerated(
            temper = $$"""
                |greet("world");
                |greet("world ${x}");
                |let x = 1 + 2;
                |export let greet(name: String): Void {
                |  console.log("Hi:");
                |  console.log(name);
                |}
            """,
            cpp = """
                |#include <my-test-library/something.hpp>
                |namespace temper {
                |  namespace my_test_library {
                |    temper::core::Object<temper::core::Console> console_0 = temper::core::get_console();
                |    temper::core::Object<temper::core::Void> greet(temper::core::Object<temper::core::String> name__0) {
                |      temper::core::log(console_0, "Hi:");
                |      temper::core::log(console_0, name__0);
                |      return;
                |    }
                |    temper::core::Void tmp_1() {
                |      greet("world");
                |    }
                |    temper::core::Object<temper::core::Int> x__0 = 3;
                |    temper::core::Void tmp_2() {
                |      greet(temper::core::cat("world ", temper::core::toString(3)));
                |    }
                |  }
                |}
                |
            """,
            hpp = """
                |#if ! defined(TEMPER_HEADER_GUARD_3)
                |#define TEMPER_HEADER_GUARD_3
                |#include <temper-core/core.hpp>
                |namespace temper {
                |  namespace my_test_library {
                |    temper::core::Object<temper::core::Void> greet(temper::core::Object<temper::core::String>);
                |    extern temper::core::Object<temper::core::Console> console_0;
                |    extern temper::core::Object<temper::core::Int> x__0;
                |    temper::core::Void tmp_1();
                |    temper::core::Void tmp_2();
                |  }
                |}
                |#endif
                |
            """,
        )
    }
}

private fun assertGenerated(
    temper: String,
    cpp: String,
    hpp: String,
) {
    fun escaped(text: String) = """
        |               "content":
        |```
        |${text.trimMargin()}
        |```
    """.trimMargin()
    assertGeneratedCode(
        backendConfig = Backend.Config.production,
        factory = CppBackend.Cpp11,
        inputs = listOf(filePath("something", "something.temper") to temper.trimMargin()),
        moduleResultNeeded = false,
        want = """
            |{
            |    "cpp": {
            |        "my-test-library": {
            |            "something.cpp": {
            |${escaped(cpp)}
            |            },
            |            "something.hpp": {
            |${escaped(hpp)}
            |            },
            |            "something.cpp.map": "__DO_NOT_CARE__",
            |            "something.hpp.map": "__DO_NOT_CARE__",
            |        }
            |    }
            |}
        """.trimMargin(),
    )
}
