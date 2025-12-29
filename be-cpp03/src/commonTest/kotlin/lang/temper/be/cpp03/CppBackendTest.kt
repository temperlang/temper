package lang.temper.be.cpp03

import lang.temper.be.Backend
import lang.temper.be.assertGeneratedCode
import lang.temper.be.inputFileMapFromJson
import lang.temper.log.FilePath
import kotlin.test.Test

@SuppressWarnings("MaxLineLength")
class CppBackendTest {
    @Test
    fun crashyMath() = assertGeneratedCode(
        inputs = inputFileMapFromJson(
            """
                |{
                |  hi.temper: ```
                |    export let why(i: Int): Int throws Bubble {
                |      return -0x8000_0000 / i;
                |    }
                |    export let that = why(-one);
                |    export let other = why(0 * one) orelse 0;
                |    var one = 1;
                |    one = one * one;
                |    ```,
                |}
            """.trimMargin(),
        ),
        want = """
            |{
            |  cpp03: {
            |    "my-test-library": {
            |      "my-test-library.cpp": {
            |        content: ```
            |          #include <temper-core/core.hpp>
            |          namespace my_test_library {
            |            int32_t t_10;
            |            temper::core::Expected<int32_t> why(int32_t i) {
            |              int32_t return_;
            |              temper::core::Expected<int32_t> fail_5 = temper::core::div_checked(-2147483647 - 1, i);
            |              if( ! fail_5.has_value())return temper::core::Unexpected(fail_5.error());
            |              return_ = * fail_5;
            |              return return_;
            |            }
            |            int32_t one = 1;
            |            namespace {
            |              struct _Init0 {
            |                _Init0() {
            |                  one = temper::core::mul(one, one);
            |                }
            |              };
            |              _Init0 _init0;
            |            }
            |            int32_t that;
            |            namespace {
            |              struct _Init1 {
            |                _Init1() {
            |                  temper::core::Expected<int32_t> fail_6 = why(temper::core::neg(one));
            |                  if( ! fail_6.has_value()) {
            |                    throw std::logic_error(fail_6.error());
            |                  }
            |                  that = * fail_6;
            |                }
            |              };
            |              _Init1 _init1;
            |            }
            |            int32_t other;
            |            namespace {
            |              struct _Init2 {
            |                _Init2() {
            |                  {
            |                    {
            |                      temper::core::Expected<int32_t> fail_7 = why(temper::core::mul(0, one));
            |                      if( ! fail_7.has_value())goto orelse_4;
            |                      t_10 = * fail_7;
            |                      other = t_10;
            |                      goto ok_12;
            |                    }
            |                    orelse_4 : {}
            |                    other = 0;
            |                  }
            |                  ok_12 : {}
            |                }
            |              };
            |              _Init2 _init2;
            |            }
            |          }
            |
            |          ```
            |      },
            |      "my-test-library.cpp.map": "__DO_NOT_CARE__",
            |      "main.cpp": "__DO_NOT_CARE__",
            |    },
            |  },
            |}
        """.trimMargin(),
    )

    @Test
    fun fib() = assertGeneratedCode(
        inputs = inputFileMapFromJson(
            """
                |{
                |  hi.temper: ```
                |    export let fib(var i: Int): Int {
                |      var a: Int = 0;
                |      var b: Int = 1;
                |      while (i > 0) {
                |        let c = a + b;
                |        a = b;
                |        b = c;
                |        i -= 1
                |      }
                |      a
                |    }
                |    ```,
                |}
            """.trimMargin(),
        ),
        want = """
            |{
            |  cpp03: {
            |    "my-test-library": {
            |      "my-test-library.cpp": {
            |        content: ```
            |          #include <temper-core/core.hpp>
            |          namespace my_test_library {
            |            int32_t fib(int32_t i) {
            |              int32_t a = 0;
            |              int32_t b = 1;
            |              while(i> 0) {
            |                int32_t c = temper::core::add(a, b);
            |                a = b;
            |                b = c;
            |                i = temper::core::sub(i, 1);
            |              }
            |              return a;
            |            }
            |          }
            |
            |          ```
            |      },
            |      "my-test-library.cpp.map": "__DO_NOT_CARE__",
            |      "main.cpp": "__DO_NOT_CARE__",
            |    },
            |  },
            |}
        """.trimMargin(),
    )

    @Test
    fun hi() = assertGeneratedCode(
        inputs = inputFileMapFromJson(
            """
                |{
                |  hi.temper: ```
                |    greet("world");
                |    export let greet(name: String): Void {
                |      console.log("Hi:");
                |      console.log(name);
                |    }
                |    ```,
                |}
            """.trimMargin(),
        ),
        want = """
            |{
            |  cpp03: {
            |    "my-test-library": {
            |      "my-test-library.cpp": {
            |        content: ```
            |          #include <temper-core/core.hpp>
            |          namespace my_test_library {
            |            void greet(temper::core::Shared<std::string> name) {
            |              temper::core::log(temper::core::shared<std::string>("Hi:", 3));
            |              temper::core::log(name);
            |            }
            |            namespace {
            |              struct _Init0 {
            |                _Init0() {
            |                  greet(temper::core::shared<std::string>("world", 5));
            |                }
            |              };
            |              _Init0 _init0;
            |            }
            |          }
            |
            |          ```
            |      },
            |      "my-test-library.cpp.map": "__DO_NOT_CARE__",
            |      "main.cpp": "__DO_NOT_CARE__",
            |    },
            |  },
            |}
        """.trimMargin(),
    )
}

private fun assertGeneratedCode(
    inputs: List<Pair<FilePath, String>>,
    want: String,
) {
    assertGeneratedCode(
        inputs = inputs,
        want = want,
        factory = CppBackend.Factory,
        backendConfig = Backend.Config.production,
        moduleResultNeeded = false,
    )
}
