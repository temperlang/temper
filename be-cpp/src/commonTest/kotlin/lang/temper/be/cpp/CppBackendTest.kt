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
                |    void temper_init_something() {
                |      static bool initialized = false;
                |      if(initialized)return;
                |      initialized = true;
                |    }
                |    struct DepInit_5 {
                |      DepInit_5() {
                |        temper_init_something();
                |      }
                |    };
                |    DepInit_5 dep_init_instance_6;
                |    static temper::core::Object<temper::core::Console> console_0 = temper::core::get_console();
                |    temper::core::Object<temper::core::Void> greet(temper::core::Object<temper::core::String> name__0) {
                |      temper::core::log(console_0, "Hi:");
                |      temper::core::log(console_0, name__0);
                |      return;
                |    }
                |    struct Init_1 {
                |      Init_1() {
                |        greet("world");
                |      }
                |    };
                |    Init_1 init_instance_2;
                |    static temper::core::Object<temper::core::Int> x__0 = 3;
                |    struct Init_3 {
                |      Init_3() {
                |        greet(temper::core::cat("world ", temper::core::toString(3)));
                |      }
                |    };
                |    Init_3 init_instance_4;
                |  }
                |}
                |
            """,
            hpp = """
                |#if ! defined(TEMPER_HEADER_GUARD_7)
                |#define TEMPER_HEADER_GUARD_7
                |#include <temper-core/core.hpp>
                |namespace temper {
                |  namespace my_test_library {
                |    temper::core::Object<temper::core::Void> greet(temper::core::Object<temper::core::String>);
                |    void temper_init_something();
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
            |            "main.cpp": "__DO_NOT_CARE__",
            |        }
            |    }
            |}
        """.trimMargin(),
    )
}
