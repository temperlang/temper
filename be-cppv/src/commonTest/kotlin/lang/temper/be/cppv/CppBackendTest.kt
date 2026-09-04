package lang.temper.be.cppv

import lang.temper.be.Backend
import lang.temper.be.assertGeneratedCode
import lang.temper.be.inputFileMapFromJson
import lang.temper.log.FilePath
import kotlin.test.Ignore
import kotlin.test.Test

@SuppressWarnings("MaxLineLength")
class CppBackendTest {
    @Test
    fun classy() = assertGeneratedCode(
        inputs = inputFileMapFromJson(
            $$"""
                |{
                |  hi.temper: ```
                |    class C {
                |      public get place(): String { "Hilo, HI" }
                |    }
                |    let c = new C();
                |    console.log("Hello, ${c.place}!");
                |    ```,
                |}
            """.trimMargin(),
        ),
        want = """
            |{
            |  cppv: {
            |    "my-test-library": {
            |      "my-test-library.cpp": {
            |        content: ```
            |          #include <temper-core/core.hpp>
            |          namespace my_test_library {
            |            struct C {
            |              std::shared_ptr<std::string const> place();
            |            };
            |            std::shared_ptr<std::string const> C::place() {
            |              return std::make_shared<std::string const>("Hilo, HI", 8);
            |            }
            |            std::shared_ptr<C> c = std::make_shared<C>();
            |            namespace {
            |              struct _Init0 {
            |                _Init0() {
            |                  temper::core::log(temper::core::cat(std::make_shared<std::string const>("Hello, ", 7), c->place(), std::make_shared<std::string const>("!", 1)));
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
                |    export let other = do {
                |      var x = why(0 * one) orelse 0;
                |      x
                |    };
                |    var one = 1;
                |    one = one * one;
                |    ```,
                |}
            """.trimMargin(),
        ),
        want = """
            |{
            |  cppv: {
            |    "my-test-library": {
            |      "my-test-library.cpp": {
            |        content: ```
            |          #include <temper-core/core.hpp>
            |          namespace my_test_library {
            |            int32_t t_16;
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
            |                  temper::core::Expected<int32_t> fail_7 = why(temper::core::neg(one));
            |                  if( ! fail_7.has_value()) {
            |                    throw std::logic_error(fail_7.error());
            |                  }
            |                  that = * fail_7;
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
            |                      temper::core::Expected<int32_t> fail_9 = why(temper::core::mul(0, one));
            |                      if( ! fail_9.has_value())goto orelse_4;
            |                      t_16 = * fail_9;
            |                      other = t_16;
            |                      goto ok_18;
            |                    }
            |                    orelse_4 : {}
            |                    other = 0;
            |                  }
            |                  ok_18 : {}
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
            |  cppv: {
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
            |  cppv: {
            |    "my-test-library": {
            |      "my-test-library.cpp": {
            |        content: ```
            |          #include <temper-core/core.hpp>
            |          namespace my_test_library {
            |            void greet(std::shared_ptr<std::string const> name) {
            |              temper::core::log(std::make_shared<std::string const>("Hi:", 3));
            |              temper::core::log(name);
            |            }
            |            namespace {
            |              struct _Init0 {
            |                _Init0() {
            |                  greet(std::make_shared<std::string const>("world", 5));
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

    @Test
    fun listEmpty() = assertGeneratedCode(
        inputs = inputFileMapFromJson(
            """
                |{
                |  hi.temper: ```
                |    let f(b: List<Int>): Void {
                |      if (b.isEmpty) {
                |        console.log("empty")
                |      } else {
                |        console.log("not empty")
                |      }
                |    };
                |    f([2, 3]);
                |    f([]);
                |    ```,
                |}
            """.trimMargin(),
        ),
        want = """
            |{
            |  cppv: {
            |    "my-test-library": {
            |      "my-test-library.cpp": {
            |        content: ```
            |          #include <temper-core/core.hpp>
            |          namespace my_test_library {
            |            void f(std::shared_ptr<std::vector<int32_t> const> b) {
            |              if(b->empty()) {
            |                temper::core::log(std::make_shared<std::string const>("empty", 5));
            |              }else {
            |                temper::core::log(std::make_shared<std::string const>("not empty", 9));
            |              }
            |            }
            |            namespace {
            |              struct _Init0 {
            |                _Init0() {
            |                  f(temper::core::listify<int32_t>(2, 3));
            |                  f(temper::core::listify<int32_t>());
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

    // Need support for {Is,Unpack,Pack}OkResult in be-cppv.
    // Deferred until we know long-term status of this backend vs. be-cpp.
    @Ignore
    @Test
    fun brahmaguptasRevenge() = assertGeneratedCode(
        inputs = inputFileMapFromJson(
            """
                |{
                |  hithere.temper: ```
                |    export let f(x: Int, y: Int): Int {
                |      x / y orelse 0
                |    }
                |    ```,
                |}
            """.trimMargin(),
        ),
        want = """
            |{
            |  cppv: {
            |    "my-test-library": {
            |      "my-test-library.cpp": {
            |        content: ```
            |          #include <temper-core/core.hpp>
            |          namespace my_test_library {
            |            int32_t f(int32_t x, int32_t y) {
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
