@file:Suppress("MaxLineLength")

package lang.temper.be.js

import lang.temper.be.inputFileMapFromJson
import lang.temper.common.stripDoubleHashCommentLinesToPutCommentsInlineBelow
import lang.temper.log.filePath
import kotlin.test.Test

class JsBackendTest {
    @Suppress("SpellCheckingInspection") // SourceMap mappings are not English
    @Test
    fun aNumber() = assertGeneratedCode(
        inputs = inputFileMapFromJson(
            """
                |{
                |  src: {
                |    foo: {
                |      foo.temper: "123"
                |    }
                |  }
                |}
            """.trimMargin(),
        ),
        moduleResultNeeded = true,
        want = """
            |{
            |  js: {
            |    "my-test-library": {
            |      src: {
            |        "foo.js": {
            |          content:
            |            ```
            |            /** @type {number} */
            |            const return_2 = 123;
            |            export default return_2;
            |
            |            ```,
            |            mimeType: "text/javascript",
            |        },
            |        "foo.js.map": {
            |          mimeType: "application/json",
            |          jsonContent: {
            |            version: 3,
            |            file: "js/my-test-library/src/foo.js",
            |            sources: ["src/foo/foo.temper"],
            |            sourcesContent: ["123"],
            |            names: ["return"],
            |            // Haven't checked.
            |            mappings: "AAAA;AAAA,MAAAA,QAAA,MAAG,AAAH;AAAG,eAAAA,QAAA",
            |          },
            |        }
            |      },
            |$OUTPUT_BOILERPLATE
            |    }
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun packageJsonAndIndexJs() = assertGeneratedCode(
        inputs = inputFileMapFromJson(
            """
                |{
                |  src: {
                |    foo: {
                |      foo.temper: ```
                |        console.log("Hello, Foo!");
                |        ```
                |    }
                |  },
                |  bar.temper: ```
                |    console.log("Hello, Bar!");
                |    ```
                |}
            """.trimMargin(),
        ),
        want = """
            |{
            |  js: {
            |    "my-test-library": {
            |      "src": {
            |        "foo.js": "__DO_NOT_CARE__",
            |        "foo.js.map": "__DO_NOT_CARE__",
            |      },
            |## Top level module translated.
            |      "my_test_library.js": "__DO_NOT_CARE__",
            |      "my_test_library.js.map": "__DO_NOT_CARE__",
            |## The generated index.js should load the modules in order and re-export any top-level module.
            |      "index.js": {
            |        content: ```
            |          export * from "./my_test_library.js";
            |          import "./src/foo.js";
            |
            |          ```
            |      },
            |      "package.json": {
            |        jsonContent: {
            |          "name": "my-test-library",
            |          "type": "module",
            |          "exports": {
            |            "./my_test_library": "./my_test_library.js",
            |            "./src/foo": "./src/foo.js",
            |            ".": "./index.js"
            |          },
            |          "dependencies": {
            |              "@temperlang/core": "0.6.0"
            |          }
            |        }
            |      },
            |    },
            |  },
            |}
        """.trimMargin().stripDoubleHashCommentLinesToPutCommentsInlineBelow(),
    )

    @Test
    fun typedLocal() = assertGeneratedCode(
        inputs = inputFileMapFromJson(
            """
                |{
                |  src: {
                |    foo: {
                |      foo.temper: "export let i: Int = 0"
                |    }
                |  }
                |}
            """.trimMargin(),
        ),
        want = """
            |{
            |  js: {
            |    "my-test-library": {
            |      "src": {
            |        "foo.js": {
            |          content:
            |          ```
            |          /** @type {number} */
            |          export const i = 0;
            |
            |          ```
            |        },
            |        "foo.js.map": "__DO_NOT_CARE__"
            |      },
            |$OUTPUT_BOILERPLATE
            |    }
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun makePeanoProud() = assertGeneratedCode(
        inputs = inputFileMapFromJson(
            """
                |{
                |  src: {
                |    foo: {
                |      foo.temper: ```
                |        var one = 1;
                |        // Having multiple assignments tricks compiler into not-inlining whole thing to 2
                |        one = one;
                |        one + one
                |        ```
                |    }
                |  }
                |}
            """.trimMargin(),
        ),
        moduleResultNeeded = true,
        want = """
            |{
            |  "js": {
            |    "my-test-library": {
            |      "src": {
            |        "foo.js": {
            |          "content":
            |          ```
            |          /** @type {number} */
            |          let one_0 = 1;
            |          one_0 = one_0;
            |          /** @type {number} */
            |          const return_0 = one_0 + one_0 | 0;
            |          export default return_0;
            |
            |          ```
            |        },
            |        "foo.js.map": "__DO_NOT_CARE__"
            |      },
            |$OUTPUT_BOILERPLATE
            |    }
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun fib() {
        val input = """
            |let fib(var i: Int): Int {
            |  console.log(i.toString());
            |  var a: Int = 0;
            |  var b: Int = 1;
            |  while (i > 0) {
            |    let c = a + b;
            |    a = b;
            |    b = c;
            |    i -= 1
            |  }
            |  a
            |}
            |export let fibber(i: Int): Int { fib(i) }
            |test("fib") { assert(fib(0) == 0) }
        """.trimMargin()

        val want = """
            |{
            |  "js": {
            |    "my-test-library": {
            |      "src": {
            |        "fib.js": {
            |          "content":
            |          ```
            |          export {
            |            fibber
            |          } from "./fib.internal.js";
            |
            |          ```
            |        },
            |        "fib.js.map": "__DO_NOT_CARE__",
            |        "fib.internal.js": {
            |          "content":
            |          ```
            |          import {
            |            globalConsole as globalConsole_0
            |          } from "@temperlang/core";
            |          /** @type {Console_0} */
            |          const console_0 = globalConsole_0;
            |          /**
            |           * @param {number} i_0
            |           * @returns {number}
            |           */
            |          function fib_0(i_0) {
            |            let t_0 = i_0.toString();
            |            console_0.log(t_0);
            |            let a_0 = 0;
            |            let b_0 = 1;
            |            while (i_0 > 0) {
            |              const c_0 = a_0 + b_0 | 0;
            |              a_0 = b_0;
            |              b_0 = c_0;
            |              i_0 = i_0 - 1 | 0;
            |            }
            |            return a_0;
            |          }
            |          /**
            |           * @param {number} i_1
            |           * @returns {number}
            |           */
            |          export function fibber(i_1) {
            |            return fib_0(i_1);
            |          };
            |          export {
            |            fib_0
            |          };
            |
            |          ```
            |        },
            |        "fib.internal.js.map": "__DO_NOT_CARE__",
            |      },
            |      "index.js": {
            |        "content":
            |        ```
            |        import "./src/fib.js";
            |
            |        ```
            |      },
            |      "package.json": "__DO_NOT_CARE__",
            |      "test": {
            |        "src": {
            |          "fib.js": {
            |            "content":
            |            ```
            |            import {
            |              fib_0
            |            } from "../../src/fib.internal.js";
            |            import {
            |              Test as Test_0
            |            } from "@temperlang/std/testing";
            |            it("fib", function () {
            |                const test_0 = new Test_0();
            |                try {
            |                  const actual_0 = fib_0(0);
            |                  let t_1 = actual_0 === 0;
            |                  function fn_0() {
            |                    return "expected fib(0) == (" + 0 .toString() + ") not (" + actual_0.toString() + ")";
            |                  }
            |                  test_0.assert(t_1, fn_0);
            |                  return;
            |                } finally {
            |                  test_0.softFailToHard();
            |                }
            |            });
            |
            |            ```
            |          },
            |          "fib.js.map": "__DO_NOT_CARE__",
            |        },
            |      },
            |    }
            |  }
            |}
        """.trimMargin()

        assertGeneratedCode(
            listOf(filePath("src", "fib", "whatever.temper") to input),
            want = want,
        )
    }

    @Test
    fun onlyTestCode() {
        // No exported code still needs side effects run, so check that.
        val input = $$"""
            |let greet(name: String): Void {
            |  console.log("Hi, ${name}!");
            |}
            |console.log("Here be side effects.");
            |test("greet") { greet("world"); }
        """.trimMargin()

        // The transfer of console from internal is odd but not broken.
        // It's still unavailable from the public js module.
        val want = """
            |{
            |  "js": {
            |    "my-test-library": {
            |        "sub.js": {
            |          "content":
            |          ```
            |          export {} from "./sub.internal.js";
            |
            |          ```
            |        },
            |        "sub.js.map": "__DO_NOT_CARE__",
            |        "sub.internal.js": {
            |          "content":
            |          ```
            |          import {
            |            globalConsole as globalConsole_0
            |          } from "@temperlang/core";
            |          /** @type {Console_0} */
            |          const console_0 = globalConsole_0;
            |          console_0.log("Here be side effects.");
            |          export {
            |            console_0
            |          };
            |
            |          ```
            |        },
            |        "sub.internal.js.map": "__DO_NOT_CARE__",
            |      "index.js": {
            |        "content":
            |        ```
            |        import "./sub.js";
            |
            |        ```
            |      },
            |      "package.json": "__DO_NOT_CARE__",
            |      "test": {
            |          "sub.js": {
            |            "content":
            |            ```
            |            import {
            |              console_0
            |            } from "../sub.internal.js";
            |            import {
            |              Test as Test_0
            |            } from "@temperlang/std/testing";
            |            /** @param {string} name_0 */
            |            function greet_0(name_0) {
            |              console_0.log("Hi, " + name_0 + "!");
            |              return;
            |            }
            |            it("greet", function () {
            |                const test_0 = new Test_0();
            |                try {
            |                  greet_0("world");
            |                  return;
            |                } finally {
            |                  test_0.softFailToHard();
            |                }
            |            });
            |
            |            ```
            |          },
            |          "sub.js.map": "__DO_NOT_CARE__",
            |      },
            |    }
            |  }
            |}
        """.trimMargin()

        assertGeneratedCode(
            listOf(filePath("sub", "whatever.temper") to input),
            want = want,
        )
    }

    @Test
    fun gracefulFailure() = assertGeneratedCode(
        inputs = inputFileMapFromJson(
            """
                |{
                |  src: {
                |    "Brahmagupta'sRevenge": {
                |      "umm, B, seriously chill.temper": ```
                |        do { 0 / 0 } orelse 0
                |        ```
                |    }
                |  }
                |}
            """.trimMargin(),
        ),
        moduleResultNeeded = true,
        want = """
            |{
            |  js: {
            |    "my-test-library": {
            |      src: {
            |        "Brahmagupta'sRevenge.js": {
            |          content:
            |            ```
            |            import {
            |              divIntInt as divIntInt_0
            |            } from "@temperlang/core";
            |            /** @type {number} */
            |            let return_0;
            |            /** @type {number} */
            |            let t_0;
            |            try {
            |              t_0 = divIntInt_0(0, 0);
            |              return_0 = t_0;
            |            } catch {
            |              return_0 = 0;
            |            }
            |            export default return_0;
            |
            |            ```
            |        },
            |        "Brahmagupta'sRevenge.js.map": "__DO_NOT_CARE__",
            |      },
            |$OUTPUT_BOILERPLATE
            |    }
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun classDefinitionLooksLikeClass() = assertGeneratedCode(
        inputs = inputFileMapFromJson(
            """
                |{
                |  src: {
                |    C: {
                |      C.temper: ```
                |        export class C<T, U>(
                |          private prop: T,
                |          public more: U,
                |        ) {
                |          public method(): T { prop }
                |        }
                |        ```
                |    }
                |  }
                |}
            """.trimMargin(),
        ),
        want = """
            |{
            |  js: {
            |    "my-test-library": {
            |      "src": {
            |        "C.js": {
            |          content:
            |            ```
            |            import {
            |              type as type_0
            |            } from "@temperlang/core";
            |            /**
            |             * @template T_0
            |             * @template U_0
            |             */
            |            export class C extends type_0() {
            |              /** @type {T_0} */
            |              #prop_13;
            |              /** @type {U_0} */
            |              #more_0;
            |              /** @returns {T_0} */
            |              method() {
            |                return this.#prop_13;
            |              }
            |              /**
            |               * @template T_0
            |               * @template U_0
            |               * @param {{
            |               *   prop: T_0, more: U_0
            |               * }}
            |               * props
            |               * @returns {C<T_0, U_0>}
            |               */
            |              static["new"](props) {
            |                return new C(props.prop, props.more);
            |              }
            |              /**
            |               * @param {T_0} prop_1
            |               * @param {U_0} more_1
            |               */
            |              constructor(prop_1, more_1) {
            |                super ();
            |                this.#prop_13 = prop_1;
            |                this.#more_0 = more_1;
            |                return;
            |              }
            |              /** @returns {U_0} */
            |              get more() {
            |                return this.#more_0;
            |              }
            |            };
            |
            |            ```,
            |        },
            |        "C.js.map": "__DO_NOT_CARE__",
            |      },
            |$OUTPUT_BOILERPLATE
            |    }
            |  }
            |}
        """.trimMargin(),
    )

    /** This tests the interface support code in the runtime support library */
    @Test
    fun interfacePrototypeEditingMachinery() = assertGeneratedCode(
        inputs = inputFileMapFromJson(
            """
                |{
                |  src: {
                |    C: {
                |      C.temper: ```
                |        export interface I {
                |          get x(): Int { 123 }
                |          y(): Int { 456 }
                |        }
                |        export interface J<T> { set x(newX: T) {} }
                |        export class C extends I, J<Int> {}
                |        ```
                |    }
                |  }
                |}
            """.trimMargin(),
        ),
        want = """
            |{
            |    "js": {
            |        "my-test-library": {
            |            "src": {
            |                "C.js": {
            |                    "content":
            |                    ```
            |                    import {
            |                      type as type_0
            |                    } from "@temperlang/core";
            |                    export class I extends type_0() {
            |                      /** @returns {number} */
            |                      get x() {
            |                        return 123;
            |                      }
            |                      /** @returns {number} */
            |                      y() {
            |                        return 456;
            |                      }
            |                    };
            |                    /** @template T_0 */
            |                    export class J extends type_0() {
            |                      /** @param {T_0} newX_0 */
            |                      set x(newX_0) {
            |                        return;
            |                      }
            |                    };
            |                    export class C extends type_0(I, J) {
            |                      constructor() {
            |                        super ();
            |                        return;
            |                      }
            |                    };
            |
            |                    ```
            |                },
            |                "C.js.map": "__DO_NOT_CARE__"
            |            },
            |            "package.json": "__DO_NOT_CARE__",
            |            "index.js": "__DO_NOT_CARE__"
            |        }
            |    }
            |}
        """.trimMargin(),
    )

    @Test
    fun equality() = assertGeneratedCode(
        inputs = inputFileMapFromJson(
            """
                    |{
                    |  src: {
                    |    eq: {
                    |      eq.temper: ```
                    |        export let foo(bar:String): Boolean {bar == ""}
                    |        ```
                    |    }
                    |  }
                    |}
            """.trimMargin(),
        ),
        want = """
            |{
            |  "js": {
            |    "my-test-library": {
            |      "src": {
            |        "eq.js": {
            |          "content":
            |          ```
            |          /**
            |           * @param {string} bar_0
            |           * @returns {boolean}
            |           */
            |          export function foo(bar_0) {
            |            return bar_0 === "";
            |          };
            |
            |          ```
            |        },
            |        "eq.js.map": "__DO_NOT_CARE__"
            |      },
            |$OUTPUT_BOILERPLATE
            |    }
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun lists() = assertGeneratedCode(
        inputs = inputFileMapFromJson(
            """
                |{
                |  src: {
                |    list: {
                |      list.temper: "[3, 4]"
                |    }
                |  }
                |}
            """.trimMargin(),
        ),
        moduleResultNeeded = true,
        want = """
            |{
            |  "js": {
            |    "my-test-library": {
            |      "src": {
            |        "list.js": {
            |          "_name": "list.js",
            |          "_type": "txt",
            |          "content": "/** @type {Array\u003cnumber\u003e} */\nconst return_0 = Object.freeze([3, 4]);\nexport default return_0;\n"
            |        },
            |        "list.js.map": "__DO_NOT_CARE__"
            |      },
            |$OUTPUT_BOILERPLATE
            |    }
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun importExport() = assertGeneratedCode(
        inputs = inputFileMapFromJson(
            """
                |{
                |  src: {
                |    a: {
                |      a.temper: ```
                |        export let f(): Void { console.log("f") }
                |        ```
                |    },
                |    b: {
                |      b.temper: ```
                |        let { f } = import("../a");
                |        f()
                |        ```
                |    }
                |  }
                |}
            """.trimMargin(),
        ),
        moduleResultNeeded = true,
        want = """
            |{
            |  js: {
            |    "my-test-library": {
            |      src: {
            |        "a.js": {
            |          content: ```
            |            import {
            |              globalConsole as globalConsole_0
            |            } from "@temperlang/core";
            |            /** @type {Console_0} */
            |            const console_0 = globalConsole_0;
            |            export function f() {
            |              console_0.log("f");
            |              return;
            |            };
            |            /** @type {void} */
            |            const return_1 = void 0;
            |            export default return_1;
            |
            |            ```,
            |        },
            |        "b.js": {
            |          content: ```
            |            import {
            |              f as f_0
            |            } from "./a.js";
            |            f_0();
            |            /** @type {void} */
            |            const return_2 = void 0;
            |            export default return_2;
            |
            |            ```,
            |        },
            |        "a.js.map": "__DO_NOT_CARE__",
            |        "b.js.map": "__DO_NOT_CARE__",
            |      },
            |$OUTPUT_BOILERPLATE
            |    }
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun importsIncludeNeededTypes() = assertGeneratedCode(
        inputs = inputFileMapFromJson(
            """
                |{
                |  a: {
                |    a.temper: ```
                |      let { NullInterchangeContext, parseJson } = import("std/json");
                |
                |      @json
                |      export class C {}
                |
                |      //test("decode c") {
                |      //  let t = parseJson("{}");
                |      //  C.jsonAdapter().decodeFromJson(t, NullInterchangeContext.instance);
                |      //}
                |      ```,
                |  }
                |}
            """.trimMargin(),
        ),
        want = """
            |{
            |  "js": {
            |    "my-test-library": {
            |      a.js: {
            |        content:
            |          ```
            |          import {
            |            JsonProducer as JsonProducer_0, JsonSyntaxTree as JsonSyntaxTree_0, InterchangeContext as InterchangeContext_0, JsonObject as JsonObject_0, JsonAdapter as JsonAdapter_0
            |          } from "@temperlang/std/json";
            |          import {
            |            type as type_0, requireInstanceOf as requireInstanceOf_0, marshalToJsonObject as marshalToJsonObject_0
            |          } from "@temperlang/core";
            |          class CJsonAdapter_0 extends type_0() {
            |            /**
            |             * @param {C} x_0
            |             * @param {JsonProducer_0} p_0
            |             */
            |            encodeToJson(x_0, p_0) {
            |              x_0.encodeToJson(p_0);
            |              return;
            |            }
            |            /**
            |             * @param {JsonSyntaxTree_0} t_0
            |             * @param {InterchangeContext_0} ic_0
            |             * @returns {C}
            |             */
            |            decodeFromJson(t_0, ic_0) {
            |              return C.decodeFromJson(t_0, ic_0);
            |            }
            |            constructor() {
            |              super ();
            |              return;
            |            }
            |          }
            |          export class C extends type_0() {
            |            constructor() {
            |              super ();
            |              return;
            |            }
            |            /** @param {JsonProducer_0} p_1 */
            |            encodeToJson(p_1) {
            |              p_1.startObject();
            |              p_1.endObject();
            |              return;
            |            }
            |            /**
            |             * @param {JsonSyntaxTree_0} t_1
            |             * @param {InterchangeContext_0} ic_1
            |             * @returns {C}
            |             */
            |            static decodeFromJson(t_1, ic_1) {
            |              let obj_0;
            |              obj_0 = requireInstanceOf_0(t_1, JsonObject_0);
            |              return new C();
            |            }
            |            /** @returns {JsonAdapter_0<C>} */
            |            static jsonAdapter() {
            |              return new CJsonAdapter_0();
            |            }
            |            /** @returns {unknown} */
            |            toJSON() {
            |              return marshalToJsonObject_0(C.jsonAdapter(), this);
            |            }
            |          };
            |
            |          ```
            |      },
            |      "a.js.map": "__DO_NOT_CARE__",
            |      $OUTPUT_BOILERPLATE
            |    },
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun stringExtensionMethods() = assertGeneratedCode(
        inputs = inputFileMapFromJson(
            """
                |{
                |  src: {
                |    strings: {
                |      strings.temper: ```
                |        fn f(s: String): Boolean {
                |          s.split(",").length == 1
                |        }
                |        ```
                |    }
                |  }
                |}
            """.trimMargin(),
        ),
        moduleResultNeeded = true,
        want = """
            |{
            |  "js": {
            |    "my-test-library": {
            |      "src": {
            |        "strings.js": {
            |          "content":
            |          ```
            |          import {
            |            stringSplit as stringSplit_0
            |          } from "@temperlang/core";
            |          /**
            |           * @param {string} s_0
            |           * @returns {boolean}
            |           */
            |          function f_0(s_0) {
            |            return stringSplit_0(s_0, ",").length === 1;
            |          }
            |          /** @type {(arg0: string) => boolean} */
            |          const return_1 = f_0;
            |          export default return_1;
            |
            |          ```,
            |          "mimeType": "text/javascript"
            |        },
            |        "strings.js.map": "__DO_NOT_CARE__"
            |      },
            |$OUTPUT_BOILERPLATE
            |    }
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun nullableProps() = assertGeneratedCode(
        inputs = inputFileMapFromJson(
            """
                |{
                |  src: {
                |    props: {
                |      props.temper: ```
                |        export class Hi(
                |          public var nullable: Int?,
                |          public var nullDefault: Int? = null,
                |        ) {}
                |        ```
                |    }
                |  }
                |}
            """.trimMargin(),
        ),
        want = """
            |{
            |  "js": {
            |    "my-test-library": {
            |      "src": {
            |        "props.js": {
            |          "content":
            |          ```
            |          import {
            |            type as type_0
            |          } from "@temperlang/core";
            |          export class Hi extends type_0() {
            |            /** @type {number | null} */
            |            #nullable_0;
            |            /** @type {number | null} */
            |            #nullDefault_0;
            |            /**
            |             * @param {{
            |             *   nullable: number | null, nullDefault ?: number | null
            |             * }}
            |             * props
            |             * @returns {Hi}
            |             */
            |            static["new"](props) {
            |              return new Hi(props.nullable, props.nullDefault);
            |            }
            |            /**
            |             * @param {number | null} nullable_1
            |             * @param {number | null} [nullDefault_1]
            |             */
            |            constructor(nullable_1, nullDefault_1) {
            |              super ();
            |              if (nullDefault_1 === void 0) {
            |                nullDefault_1 = null;
            |              }
            |              this.#nullable_0 = nullable_1;
            |              this.#nullDefault_0 = nullDefault_1;
            |              return;
            |            }
            |            /** @returns {number | null} */
            |            get nullable() {
            |              return this.#nullable_0;
            |            }
            |            /** @param {number | null} newNullable_0 */
            |            set nullable(newNullable_0) {
            |              this.#nullable_0 = newNullable_0;
            |              return;
            |            }
            |            /** @returns {number | null} */
            |            get nullDefault() {
            |              return this.#nullDefault_0;
            |            }
            |            /** @param {number | null} newNullDefault_0 */
            |            set nullDefault(newNullDefault_0) {
            |              this.#nullDefault_0 = newNullDefault_0;
            |              return;
            |            }
            |          };
            |
            |          ```,
            |          "mimeType": "text/javascript"
            |        },
            |        "props.js.map": "__DO_NOT_CARE__"
            |      },
            |$OUTPUT_BOILERPLATE
            |    }
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun skippedArg() = assertGeneratedCode(
        inputs = inputFileMapFromJson(
            """
                |{
                |  src: {
                |    skipped: {
                |      skipped.temper: ```
                |        let hi(a: Int = 1, b: Int = 2): Void { console.log((a + b).toString()) }
                |        hi(null, 3);
                |        ```
                |    }
                |  }
                |}
            """.trimMargin(),
        ),
        want = """
            |{
            |  "js": {
            |    "my-test-library": {
            |      "src": {
            |        "skipped.js": {
            |          "content":
            |          ```
            |          import {
            |            globalConsole as globalConsole_0
            |          } from "@temperlang/core";
            |          /** @type {Console_0} */
            |          const console_0 = globalConsole_0;
            |          /**
            |           * @param {number | null} [a_0]
            |           * @param {number | null} [b_0]
            |           */
            |          function hi_0(a_0, b_0) {
            |            let a_1;
            |            if (a_0 == null) {
            |              a_1 = 1;
            |            } else {
            |              a_1 = a_0;
            |            }
            |            let b_1;
            |            if (b_0 == null) {
            |              b_1 = 2;
            |            } else {
            |              b_1 = b_0;
            |            }
            |            let t_0 = (a_1 + b_1 | 0).toString();
            |            console_0.log(t_0);
            |            return;
            |          }
            |          hi_0(null, 3);
            |
            |          ```,
            |          "mimeType": "text/javascript"
            |        },
            |        "skipped.js.map": "__DO_NOT_CARE__"
            |      },
            |$OUTPUT_BOILERPLATE
            |    }
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun dequeConstruction() = assertGeneratedCode(
        inputs = inputFileMapFromJson(
            """
                |{
                |  src: {
                |    newDeque: {
                |      newDeque.temper: ```
                |        let x = new Deque<String>();
                |        let y = new Deque<String>();
                |        ```
                |    }
                |  }
                |}
            """.trimMargin(),
        ),
        want = """
            |{
            |  js: {
            |    "my-test-library": {
            |      src: {
            |        "newDeque.js": {
            |          content:
            |            ```
            |            import {
            |              dequeConstructor as dequeConstructor_0
            |            } from "@temperlang/core";
            |            /** @type {Deque_0<string>} */
            |            const x_0 = dequeConstructor_0();
            |            /** @type {Deque_0<string>} */
            |            const y_0 = dequeConstructor_0();
            |
            |            ```,
            |          mimeType: "text/javascript"
            |        },
            |        "newDeque.js.map": "__DO_NOT_CARE__",
            |      },
            |$OUTPUT_BOILERPLATE
            |    }
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun logs() = assertGeneratedCode(
        inputs = inputFileMapFromJson(
            """
                |{
                |  src: {
                |    logs: {
                |      logs.temper: ```
                |        console.log("Hello, JS");
                |        ```
                |    }
                |  }
                |}
            """.trimMargin(),
        ),
        want = """
            |{
            |  js: {
            |    "my-test-library": {
            |      src: {
            |        "logs.js": {
            |          content:
            |            ```
            |            import {
            |              globalConsole as globalConsole_0
            |            } from "@temperlang/core";
            |            globalConsole_0.log("Hello, JS");
            |
            |            ```,
            |          mimeType: "text/javascript"
            |        },
            |        "logs.js.map": "__DO_NOT_CARE__",
            |      },
            |$OUTPUT_BOILERPLATE
            |    }
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun typeReferencedCrossModule() = assertGeneratedCode(
        inputs = inputFileMapFromJson(
            """
                |{
                |  foo: {
                |    foo.temper: ```
                |      export class Point(
                |        private let x: Int,
                |        private let y: Int,
                |      ) {}
                |      ```
                |  },
                |  bar: {
                |    bar.temper: ```
                |      let { Point } = import("../foo");
                |      export let p = { x: 1, y: 2 };
                |      ```
                |  },
                |}
            """.trimMargin(),
        ),
        want = """
            |{
            |  js: {
            |    "my-test-library": {
            |      "foo.js": {
            |        content: ```
            |          import {
            |            type as type_0
            |          } from "@temperlang/core";
            |          export class Point extends type_0() {
            |            /** @type {number} */
            |            #x_0;
            |            /** @type {number} */
            |            #y_0;
            |            /**
            |             * @param {{
            |             *   x: number, y: number
            |             * }}
            |             * props
            |             * @returns {Point}
            |             */
            |            static["new"](props) {
            |              return new Point(props.x, props.y);
            |            }
            |            /**
            |             * @param {number} x_1
            |             * @param {number} y_1
            |             */
            |            constructor(x_1, y_1) {
            |              super ();
            |              this.#x_0 = x_1;
            |              this.#y_0 = y_1;
            |              return;
            |            }
            |          };
            |
            |          ```,
            |      },
            |      "foo.js.map": "__DO_NOT_CARE__",
            |      "bar.js": {
            |        content: ```
            |          import {
            |            Point as Point_0
            |          } from "./foo.js";
            |          /** @type {Point_0} */
            |          export const p = new Point_0(1, 2);
            |
            |          ```,
            |      },
            |      "bar.js.map": "__DO_NOT_CARE__",
            |$OUTPUT_BOILERPLATE
            |    }
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun typeOrNull() = assertGeneratedCode(
        inputs = inputFileMapFromJson(
            """
                |{
                |  foo: {
                |    foo.temper: ```
                |      export let n: Int? = f();
                |      var m: Int = 0;
                |      let f(): Int? throws Bubble { m = 5; m }
                |      ```
                |  }
                |}
            """.trimMargin(),
        ),
        want = """
            |{
            |  js: {
            |    "my-test-library": {
            |      "foo.js": {
            |        content: ```
            |          /** @type {number} */
            |          let m_0 = 0;
            |          /** @returns {number | null} */
            |          function f_0() {
            |            m_0 = 5;
            |            return m_0;
            |          }
            |          /** @type {number | null} */
            |          export let n;
            |          n = f_0();
            |
            |          ```,
            |      },
            |      "foo.js.map": "__DO_NOT_CARE__",
            |$OUTPUT_BOILERPLATE
            |    }
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun never() = assertGeneratedCode(
        inputs = inputFileMapFromJson(

            """
                |{
                |  foo: {
                |    foo.temper: ```
                |      export let f(): Never<Void> throws Bubble { bubble() }
                |      bubble()
                |      ```
                |  }
                |}
            """.trimMargin(),
        ),
        want = """
            |{
            |  js: {
            |    "my-test-library": {
            |      "foo.js": {
            |        content: ```
            |          /** @returns {void} */
            |          export function f() {
            |            throw Error();
            |          };
            |          throw Error();
            |
            |          ```,
            |      },
            |      "foo.js.map": "__DO_NOT_CARE__",
            |$OUTPUT_BOILERPLATE
            |    }
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun supportCodesThatDependOnSupportCode() = assertGeneratedCode(
        inputs = inputFileMapFromJson(
            """
                |{
                |  div-example: {
                |    example.temper: ```
                |      export let half(n: Int): Int { n / 2 }
                |      ```
                |  }
                |}
            """.trimMargin(),
        ),
        want = """
            |{
            |  js: {
            |    "my-test-library": {
            |      "div-example.js": {
            |        content: ```
            |          /**
            |           * @param {number} n_0
            |           * @returns {number}
            |           */
            |          export function half(n_0) {
            |            return n_0 / 2 | 0;
            |          };
            |
            |          ```,
            |      },
            |      "div-example.js.map": "__DO_NOT_CARE__",
            |$OUTPUT_BOILERPLATE
            |    }
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun computedProperty() = assertGeneratedCode(
        inputs = inputFileMapFromJson(
            """
                |{
                |  foo: {
                |    foo.temper: ```
                |      export class C {
                |        public get answer(): Int { 42 }
                |      }
                |      ```
                |  }
                |}
            """.trimMargin(),
        ),
        want = """
            |{
            |  "js": {
            |    "my-test-library": {
            |      "foo.js": {
            |        content:
            |          ```
            |          import {
            |            type as type_0
            |          } from "@temperlang/core";
            |          export class C extends type_0() {
            |            /** @returns {number} */
            |            get answer() {
            |              return 42;
            |            }
            |            constructor() {
            |              super ();
            |              return;
            |            }
            |          };
            |
            |          ```
            |      },
            |      "foo.js.map": "__DO_NOT_CARE__",
            |$OUTPUT_BOILERPLATE
            |    }
            |  },
            |}
        """.trimMargin(),
    )

    @Test
    fun noEmptyImports() = assertGeneratedCode(
        inputs = inputFileMapFromJson(
            """
            |{
            |  foo: {
            |    foo.temper:
            |      ```
            |      let { Date } = import("std/temporal");
            |      export let d = { year: 2000, month: 1, day: 1 };
            |      ```
            |  }
            |}
            """.trimMargin(),
        ),
        want = """
            |{
            |  js: {
            |    "my-test-library": {
            |      "foo.js": {
            |        content:
            |          // If we generated an import any time there was a dependency,
            |          // even one that was satisfied by connecting to a JS builtin,
            |          // then there would be a useless JS `import` statement below.
            |          ```
            |          /** @type {globalThis.Date} */
            |          export let d;
            |          d = new (globalThis.Date)(globalThis.Date.UTC(2000, 1 - 1, 1));
            |
            |          ```
            |      },
            |      "foo.js.map": "__DO_NOT_CARE__",
            |$OUTPUT_BOILERPLATE
            |    }
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun escapedImportExport() = assertGeneratedCode(
        inputs = inputFileMapFromJson(
            // Use js reserved words in different contexts across two files.
            $$"""
                |{
                |  src: {
                |    a: {
                |      a.temper: ```
                |        export let switch(with: String): Void {
                |          console.log("switch ${with}");
                |        }
                |        export class catch(
                |          public if: String,
                |        ) {
                |          public else(): Void { switch("blah") }
                |        }
                |        let nym`in` = new catch("something");
                |        console.log(nym`in`.if);
                |        ```
                |    },
                |    b: {
                |      b.temper: ```
                |        let { catch, switch } = import("../a");
                |        export let case = new catch("other");
                |        switch(case.if);
                |        case.else();
                |        ```
                |    }
                |  }
                |}
            """.trimMargin(),
        ),
        want = """
            |{
            |  js: {
            |    "my-test-library": {
            |      src: {
            |        "a.js": {
            |          content: ```
            |            import {
            |              globalConsole as globalConsole_0, type as type_0
            |            } from "@temperlang/core";
            |            /** @type {Console_0} */
            |            const console_0 = globalConsole_0;
            |            export class catch_ extends type_0() {
            |              /** @type {string} */
            |              #if_0;
            |              else_() {
            |                switch_("blah");
            |                return;
            |              }
            |              /** @param {string} if_1 */
            |              constructor(if_1) {
            |                super ();
            |                this.#if_0 = if_1;
            |                return;
            |              }
            |              /** @returns {string} */
            |              get if_() {
            |                return this.#if_0;
            |              }
            |            };
            |            /** @param {string} with_0 */
            |            export function switch_(with_0) {
            |              console_0.log("switch " + with_0);
            |              return;
            |            };
            |            /** @type {catch_} */
            |            const in_0 = new catch_("something");
            |            console_0.log(in_0.if_);
            |
            |            ```,
            |        },
            |        "b.js": {
            |          content: ```
            |            import {
            |              switch_ as switch_0, catch_ as catch_0
            |            } from "./a.js";
            |            /** @type {catch_0} */
            |            export const case_ = new catch_0("other");
            |            switch_0(case_.if_);
            |            case_.else_();
            |
            |            ```,
            |        },
            |        "a.js.map": "__DO_NOT_CARE__",
            |        "b.js.map": "__DO_NOT_CARE__",
            |      },
            |$OUTPUT_BOILERPLATE
            |    }
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun connectionOfPromiseTypes() = assertGeneratedCode(
        inputs = inputFileMapFromJson(
            """
            |{
            |  foo: {
            |    foo.temper: ```
            |      let b = new PromiseBuilder<String>();
            |      let p = b.promise;
            |      async { (): GeneratorResult<Empty> extends GeneratorFn =>
            |        console.log(await p orelse panic());
            |      }
            |      b.complete("Hi");
            |      ```
            |  }
            |}
            """.trimMargin(),
        ),
        want = """
            |{
            |  js: {
            |    "my-test-library": {
            |      "foo.js": {
            |        content:
            |          ```
            |          import {
            |            globalConsole as globalConsole_0, PromiseBuilder as PromiseBuilder_0, adaptAwaiter as adaptAwaiter_0, panic as panic_0, runAsync as runAsync_0
            |          } from "@temperlang/core";
            |          /** @type {Console_0} */
            |          const console_0 = globalConsole_0;
            |          /** @type {PromiseBuilder_0<string>} */
            |          const b_0 = new PromiseBuilder_0();
            |          /** @type {globalThis.Promise<string>} */
            |          const p_0 = b_0.promise;
            |          /** @returns {Generator<{}>} */
            |          const fn_0 = adaptAwaiter_0(function* fn_0(await_0) {
            |              let t_0;
            |              let t_1;
            |              try {
            |                t_0 = yield await_0(p_0);
            |                t_1 = t_0;
            |              } catch {
            |                t_1 = panic_0();
            |              }
            |              console_0.log(t_1);
            |          });
            |          runAsync_0(fn_0);
            |          b_0.complete("Hi");
            |
            |          ```
            |      },
            |      "foo.js.map": "__DO_NOT_CARE__",
            |$OUTPUT_BOILERPLATE
            |    }
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun stringBuilderUse() = assertGeneratedCode(
        inputs = inputFileMapFromJson(
            """
            |{
            |  foo: {
            |    foo.temper:
            |      ```
            |      let sb = new StringBuilder();
            |      sb.append("Hello, ");
            |      sb.append("World");
            |      sb.append("!");
            |      console.log(sb.toString());
            |      ```
            |  }
            |}
            """.trimMargin(),
        ),
        want = """
            |{
            |  js: {
            |    "my-test-library": {
            |      "foo.js": {
            |        content:
            |          ```
            |          import {
            |            globalConsole as globalConsole_0
            |          } from "@temperlang/core";
            |          /** @type {Console_0} */
            |          let t_0 = globalConsole_0;
            |          /** @type {globalThis.Array<string>} */
            |          const sb_0 = [""];
            |          sb_0[0] += "Hello, ";
            |          sb_0[0] += "World";
            |          sb_0[0] += "!";
            |          t_0.log(sb_0[0]);
            |
            |          ```
            |      },
            |      "foo.js.map": "__DO_NOT_CARE__",
            |$OUTPUT_BOILERPLATE
            |    }
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun stringCodeAt() = assertGeneratedCode(
        inputs = inputFileMapFromJson(
            """
            |{
            |  foo: {
            |    foo.temper:
            |      ```
            |      export let firstCodePoint(s: String): Int throws Bubble {
            |        s[String.begin]
            |      }
            |      ```
            |  }
            |}
            """.trimMargin(),
        ),
        want = """
            |{
            |  js: {
            |    "my-test-library": {
            |      "foo.js": {
            |        content:
            |          ```
            |          import {
            |            stringGet as stringGet_0
            |          } from "@temperlang/core";
            |          /**
            |           * @param {string} s_0
            |           * @returns {number}
            |           */
            |          export function firstCodePoint(s_0) {
            |            return stringGet_0(s_0, 0);
            |          };
            |
            |          ```
            |      },
            |      "foo.js.map": "__DO_NOT_CARE__",
            |$OUTPUT_BOILERPLATE
            |    }
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun netUse() = assertGeneratedCode(
        inputs = inputFileMapFromJson(
            $$"""
            |{
            |  foo: {
            |    foo.temper:
            |      ```
            |      let { NetRequest, NetResponse } = import("std/net");
            |      async { (): GeneratorResult<Empty> extends GeneratorFn =>
            |        do {
            |          let r: NetResponse = await (new NetRequest("data:text/plain,Hello World!").send());
            |
            |          if (r.status == 200) {
            |            let body: String = (await r.bodyContent) ?? "missing";
            |            console.log("Got ${body} / ${r.contentType ?? "unknown"}");
            |          }
            |        } orelse console.log("failed");
            |      }
            |      ```
            |  }
            |}
            """.trimMargin(),
        ),
        want = """
            |{
            |  js: {
            |    "my-test-library": {
            |      "foo.js": {
            |        content:
            |          ```
            |          import {
            |            NetRequest as NetRequest_0
            |          } from "@temperlang/std/net";
            |          import {
            |            globalConsole as globalConsole_0, adaptAwaiter as adaptAwaiter_0, netResponseGetStatus as netResponseGetStatus_0, netResponseGetBodyContent as netResponseGetBodyContent_0, netResponseGetContentType as netResponseGetContentType_0, runAsync as runAsync_0
            |          } from "@temperlang/core";
            |          /** @type {Console_0} */
            |          const console_0 = globalConsole_0;
            |          /** @returns {Generator<{}>} */
            |          const fn_0 = adaptAwaiter_0(function* fn_0(await_0) {
            |              let t_0;
            |              let t_1;
            |              let t_2;
            |              try {
            |                let r_0;
            |## NetRequest is defined in Temper, so it doesn't need to connect
            |                r_0 = yield await_0(new NetRequest_0("data:text/plain,Hello World!").send());
            |## Getting a status from the response involves a helper function.
            |## We could use a custom jobby to just do `.status`.
            |                if (netResponseGetStatus_0(r_0) === 200) {
            |                  let body_0;
            |## Similarly for body content.
            |                  t_1 = yield await_0(netResponseGetBodyContent_0(r_0));
            |                  if (!(t_1 == null)) {
            |                    body_0 = t_1;
            |                  } else {
            |                    body_0 = "missing";
            |                  }
            |                  t_0 = netResponseGetContentType_0(r_0);
            |                  if (!(t_0 == null)) {
            |                    const subjectHash7_0 = t_0;
            |                    t_2 = subjectHash7_0;
            |                  } else {
            |                    t_2 = "unknown";
            |                  }
            |                  console_0.log("Got " + body_0 + " / " + t_2);
            |                }
            |              } catch {
            |                console_0.log("failed");
            |              }
            |          });
            |          runAsync_0(fn_0);
            |
            |          ```
            |      },
            |      "foo.js.map": "__DO_NOT_CARE__",
            |$OUTPUT_BOILERPLATE
            |    }
            |  }
            |}
        """.trimMargin().stripDoubleHashCommentLinesToPutCommentsInlineBelow(),
    )

    @Test
    fun coalesce() = assertGeneratedCode(
        // For now, we get long-form expansions. TODO Recognize such and put the toothpaste back in the tube in TmpL?
        inputs = inputFileMapFromJson(
            """
                |{
                |  foo: {
                |    foo.temper: ```
                |      export let prod(i: Int, j: Int?): Int { i * (j ?? 1) }
                |      export let prodWrap(i: Int, j: List<Int?>): Int { i * (j[0] ?? 1) orelse 0 }
                |      export let maybeLength(a: String?): Int? { a?.countBetween(String.begin, a.end) }
                |      ```
                |  }
                |}
            """.trimMargin(),
        ),
        want = """
            |{
            |  "js": {
            |    "my-test-library": {
            |      "foo.js": {
            |        content: ```
            |          const {
            |            imul: imul_0
            |          } = globalThis.Math;
            |          import {
            |            listedGet as listedGet_0, stringCountBetween as stringCountBetween_0
            |          } from "@temperlang/core";
            |          /**
            |           * @param {number} i_0
            |           * @param {number | null} j_0
            |           * @returns {number}
            |           */
            |          export function prod(i_0, j_0) {
            |            let t_0;
            |            if (!(j_0 == null)) {
            |              const j_1 = j_0;
            |              t_0 = j_1;
            |            } else {
            |              t_0 = 1;
            |            }
            |            return imul_0(i_0, t_0);
            |          };
            |          /**
            |           * @param {number} i_1
            |           * @param {Array<number | null>} j_2
            |           * @returns {number}
            |           */
            |          export function prodWrap(i_1, j_2) {
            |            let t_1;
            |            let t_2 = listedGet_0(j_2, 0);
            |            if (!(t_2 == null)) {
            |              const subjectHash11_0 = t_2;
            |              t_1 = subjectHash11_0;
            |            } else {
            |              t_1 = 1;
            |            }
            |            return imul_0(i_1, t_1);
            |          };
            |          /**
            |           * @param {string | null} a_0
            |           * @returns {number | null}
            |           */
            |          export function maybeLength(a_0) {
            |            let return_0;
            |            let t_3;
            |            if (a_0 == null) {
            |              return_0 = null;
            |            } else {
            |              const a_1 = a_0;
            |              t_3 = a_1.length;
            |              return_0 = stringCountBetween_0(a_1, 0, t_3);
            |            }
            |            return return_0;
            |          };
            |
            |          ```
            |      },
            |      "foo.js.map": "__DO_NOT_CARE__",
            |      $OUTPUT_BOILERPLATE
            |    },
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun matchClause() = assertGeneratedCode(
        inputs = inputFileMapFromJson(
            """
                |{
                |  foo: {
                |    foo.temper: ```
                |      export let f(x: DenseBitVector?): Void {
                |        when (x) {
                |          is DenseBitVector -> console.log("dense");
                |          null -> console.log("not dense");
                |        }
                |      }
                |      ```
                |  }
                |}
            """.trimMargin(),
        ),
        // TODO: do we need to connect DenseBitVector to a JS type?
        // Or do we need to make sure imports get added for types that are
        // only used in type expressions and type tags?
        // TODO: improve translation of x == null?
        want = """
            |{
            |  "js": {
            |    "my-test-library": {
            |      "foo.js": {
            |        content: ```
            |          import {
            |            globalConsole as globalConsole_0
            |          } from "@temperlang/core";
            |          /** @type {Console_0} */
            |          const console_0 = globalConsole_0;
            |          /** @param {DenseBitVector_0 | null} x_0 */
            |          export function f(x_0) {
            |            let t_0;
            |            if (!(x_0 == null)) {
            |              t_0 = x_0 instanceof DenseBitVector_0;
            |            } else {
            |              t_0 = false;
            |            }
            |            if (t_0) {
            |              console_0.log("dense");
            |            } else if (x_0 == null) {
            |              console_0.log("not dense");
            |            }
            |            return;
            |          };
            |
            |          ```
            |      },
            |      "foo.js.map": "__DO_NOT_CARE__",
            |      $OUTPUT_BOILERPLATE
            |    },
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun defaultStringIndexOptionRtti() = assertGeneratedCode(
        inputs = inputFileMapFromJson(
            """
                |{
                |  foo: {
                |    foo.temper: ```
                |      export let f(i: StringIndexOption): StringIndex {
                |        if (i is StringIndex) {
                |          i
                |        } else {
                |          String.begin
                |        }
                |      }
                |      ```
                |  }
                |}
            """.trimMargin(),
        ),
        want = """
            |{
            |  "js": {
            |    "my-test-library": {
            |      "foo.js": {
            |        content: ```
            |            import {
            |              requireStringIndex as requireStringIndex_0
            |            } from "@temperlang/core";
            |            /**
            |             * @param {globalThis.number} i_0
            |             * @returns {globalThis.number}
            |             */
            |            export function f(i_0) {
            |              let return_0;
            |              if (i_0 >= 0) {
            |                return_0 = requireStringIndex_0(i_0);
            |              } else {
            |                return_0 = 0;
            |              }
            |              return return_0;
            |            };
            |
            |            ```
            |      },
            |      "foo.js.map": "__DO_NOT_CARE__",
            |      $OUTPUT_BOILERPLATE
            |    },
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun privateMethods() = assertGeneratedCode(
        inputs = inputFileMapFromJson(
            """
                |{
                |  foo: {
                |    foo.temper: ```
                |      export class C {
                |        private constructor() {}
                |        private var y: Int;
                |        private get x(): Int { y + 1 }
                |        private set x(newX: Int): Void { y = newX - 1 }
                |        private f(): Int { x }
                |        public g(): Int { f() / 2 }
                |      }
                |      ```
                |  }
                |}
            """.trimMargin(),
        ),
        want = """
            |{
            |  "js": {
            |    "my-test-library": {
            |      "foo.js": {
            |        content: ```
            |            import {
            |              type as type_0
            |            } from "@temperlang/core";
            |            export class C extends type_0() {
            |              constructor() {
            |                super ();
            |                return;
            |              }
            |              /** @type {number} */
            |              #y_0;
            |              /** @returns {number} */
            |              get #x_0() {
            |                return this.#y_0 + 1 | 0;
            |              }
            |              /** @param {number} newX_0 */
            |              set #x_0(newX_0) {
            |                let t_0 = newX_0 - 1 | 0;
            |                this.#y_0 = t_0;
            |                return;
            |              }
            |              /** @returns {number} */
            |              #f_0() {
            |                return this.#x_0;
            |              }
            |              /** @returns {number} */
            |              g() {
            |                return this.#f_0() / 2 | 0;
            |              }
            |            };
            |
            |            ```
            |      },
            |      "foo.js.map": "__DO_NOT_CARE__",
            |      $OUTPUT_BOILERPLATE
            |    },
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun publicGetterPrivateSetter() = assertGeneratedCode(
        inputs = inputFileMapFromJson(
            """
                |{
                |  foo: {
                |    foo.temper: ```
                |      export class C {
                |        private var y: Int = 1;
                |        public get p(): Int { y - 1 }
                |        private set p(newP: Int): Void { y = newP + 1 }
                |        public incr(): Int { p += 1 }
                |      }
                |      ```
                |  }
                |}
            """.trimMargin(),
        ),
        want = """
            |{
            |  "js": {
            |    "my-test-library": {
            |      "foo.js": {
            |        content: ```
            |            import {
            |              type as type_0
            |            } from "@temperlang/core";
            |            export class C extends type_0() {
            |              /** @type {number} */
            |              #y_0;
            |              /** @returns {number} */
            |              get p() {
            |                return this.#y_0 - 1 | 0;
            |              }
            |              /** @param {number} newP_0 */
            |              set #p_0(newP_0) {
            |                let t_0 = newP_0 + 1 | 0;
            |                this.#y_0 = t_0;
            |                return;
            |              }
            |              /** @returns {number} */
            |              incr() {
            |                let return_0;
            |                return_0 = this.p + 1 | 0;
            |                this.#p_0 = return_0;
            |                return return_0;
            |              }
            |              constructor() {
            |                super ();
            |                this.#y_0 = 1;
            |                return;
            |              }
            |            };
            |
            |            ```
            |      },
            |      "foo.js.map": "__DO_NOT_CARE__",
            |      $OUTPUT_BOILERPLATE
            |    },
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun privateStatic() = assertGeneratedCode(
        inputs = inputFileMapFromJson(
            """
                |{
                |  foo: {
                |    foo.temper: ```
                |      export class C {
                |        private static i: Int = 1;
                |        private static f(j: Int): Int { C.i + j }
                |        public static g(n: Int): Int { C.f(n) + n }
                |      }
                |      ```
                |  }
                |}
            """.trimMargin(),
        ),
        want = """
            |{
            |  "js": {
            |    "my-test-library": {
            |      "foo.js": {
            |        content: ```
            |            import {
            |              type as type_0
            |            } from "@temperlang/core";
            |            export class C extends type_0() {
            |              /** @type {number} */
            |              static #i_0 = 1;
            |              /**
            |               * @param {number} j_0
            |               * @returns {number}
            |               */
            |              static #f_0(j_0) {
            |                return C.#i_0 + j_0 | 0;
            |              }
            |              /**
            |               * @param {number} n_0
            |               * @returns {number}
            |               */
            |              static g(n_0) {
            |                return C.#f_0(n_0) + n_0 | 0;
            |              }
            |              constructor() {
            |                super ();
            |                return;
            |              }
            |            };
            |
            |            ```
            |      },
            |      "foo.js.map": "__DO_NOT_CARE__",
            |      $OUTPUT_BOILERPLATE
            |    },
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun stringIndexOptionComparison() = assertGeneratedCode(
        inputFileMapFromJson(
            """
                |{
                |  foo: {
                |    foo.temper:
                |      ```
                |      export let f1(a: StringIndexOption, b: StringIndexOption): Boolean {
                |        a < b
                |      }
                |      export let f2(a: StringIndexOption, b: StringIndex): Boolean {
                |        a >= b
                |      }
                |      export let f3(a: StringIndex, b: StringIndex): Boolean {
                |        a <= b
                |      }
                |      export let f4(a: StringIndex): Boolean {
                |        a > StringIndex.none
                |      }
                |      ```
                |  },
                |}
            """.trimMargin(),
        ),
        want = """
            |{
            |  js: {
            |    my-test-library: {
            |      foo.js: {
            |        content:
            |          ```
            |          /**
            |           * @param {globalThis.number} a_0
            |           * @param {globalThis.number} b_0
            |           * @returns {boolean}
            |           */
            |          export function f1(a_0, b_0) {
            |            return a_0 < b_0;
            |          };
            |          /**
            |           * @param {globalThis.number} a_1
            |           * @param {globalThis.number} b_1
            |           * @returns {boolean}
            |           */
            |          export function f2(a_1, b_1) {
            |            return a_1 >= b_1;
            |          };
            |          /**
            |           * @param {globalThis.number} a_2
            |           * @param {globalThis.number} b_2
            |           * @returns {boolean}
            |           */
            |          export function f3(a_2, b_2) {
            |            return a_2 <= b_2;
            |          };
            |          /**
            |           * @param {globalThis.number} a_3
            |           * @returns {boolean}
            |           */
            |          export function f4(a_3) {
            |            return a_3 > -1;
            |          };
            |
            |          ```
            |      },
            |      foo.js.map: "__DO_NOT_CARE__",
            |      index.js: "__DO_NOT_CARE__",
            |      package.json: "__DO_NOT_CARE__",
            |    }
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun unicodeNames() = assertGeneratedCode(
        inputs = inputFileMapFromJson(
            """
                |{
                |  src: {
                |    foo: {
                |      foo.temper: ```
                |        export let τó🐝 = "or not to be?";
                |        ```
                |    }
                |  }
                |}
            """.trimMargin(),
        ),
        want = """
            |{
            |  "js": {
            |    "my-test-library": {
            |      "src": {
            |        "foo.js": {
            |          "content":
            |          ```
            |          /** @type {string} */
            |          export const τó = "or not to be?";
            |
            |          ```
            |        },
            |        "foo.js.map": "__DO_NOT_CARE__"
            |      },
            |      "package.json": "__DO_NOT_CARE__",
            |      "index.js": "__DO_NOT_CARE__",
            |    }
            |  }
            |}
        """.trimMargin(),
    )
}

private const val OUTPUT_BOILERPLATE = """
    "package.json": "__DO_NOT_CARE__", "index.js": "__DO_NOT_CARE__",
"""
