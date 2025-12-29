@file:Suppress("MaxLineLength")

package lang.temper.be.tmpl

import lang.temper.be.Backend
import lang.temper.be.BackendSetup
import lang.temper.be.Dependencies
import lang.temper.be.NullDependencyResolver
import lang.temper.be.inputFileMapFromJson
import lang.temper.be.syncstaging.applyBackendsSynchronously
import lang.temper.builtin.BuiltinFuns
import lang.temper.builtin.Types
import lang.temper.common.ListBackedLogSink
import lang.temper.common.Log
import lang.temper.common.assertStructure
import lang.temper.common.console
import lang.temper.common.currents.makeCancelGroupForTest
import lang.temper.common.stripDoubleHashCommentLinesToPutCommentsInlineBelow
import lang.temper.format.OutToks
import lang.temper.format.TokenSink
import lang.temper.frontend.Module
import lang.temper.frontend.StagingFlags
import lang.temper.frontend.staging.ModuleAdvancer
import lang.temper.frontend.staging.ModuleConfig
import lang.temper.frontend.staging.ModuleCustomizeHook
import lang.temper.frontend.staging.partitionSourceFilesIntoModules
import lang.temper.fs.FileFilterRules
import lang.temper.fs.FilteringFileSystemSnapshot
import lang.temper.fs.MemoryFileSystem
import lang.temper.fs.NullSystemAccess
import lang.temper.fs.OutDir
import lang.temper.fs.OutputRoot
import lang.temper.fs.fileTreeStructure
import lang.temper.interp.connectedDecoratorBindings
import lang.temper.interp.connectedDecoratorName
import lang.temper.interp.vConnectedDecorator
import lang.temper.lexer.Genre
import lang.temper.library.LibraryConfiguration
import lang.temper.library.LibraryConfigurations
import lang.temper.library.LibraryConfigurationsBundle
import lang.temper.log.FilePath
import lang.temper.log.FilePathSegment
import lang.temper.log.FilePositions
import lang.temper.log.Position
import lang.temper.log.dirPath
import lang.temper.log.last
import lang.temper.log.plus
import lang.temper.log.resolveDir
import lang.temper.name.BuiltinName
import lang.temper.name.DashedIdentifier
import lang.temper.name.ModuleName
import lang.temper.name.ParsedName
import lang.temper.name.PseudoCodeNameRenumberer
import lang.temper.name.SourceName
import lang.temper.name.Symbol
import lang.temper.type.WellKnownTypes
import lang.temper.type2.Signature2
import lang.temper.type2.Type2
import lang.temper.value.Document
import lang.temper.value.TBoolean
import lang.temper.value.TFloat64
import lang.temper.value.Value
import lang.temper.value.initSymbol
import lang.temper.value.typeSymbol
import lang.temper.value.vToStringSymbol
import kotlin.test.Ignore
import kotlin.test.Test

class TmpLBackendTest {
    @Suppress("SpellCheckingInspection") // SourceMap mappings are not English
    @Test
    fun aNumber() = assertGeneratedCode(
        inputs = inputFileMapFromJson(
            """
                |{
                |  aNumber: {
                |    src.temper: "123"
                |  }
                |}
            """.trimMargin(),
        ),
        moduleNeedsResult = true,
        want = """
            {
              tmpl: {
                "aNumber.tmpl": {
                    content:
                      ```
                      //// work//aNumber/ => aNumber.tmpl
                      let return__0: Int32 = 123;
                      export return__0;

                      ```
                },
                "aNumber.tmpl.map": {
                  jsonContent: {
                    version: 3,
                    file: "test-library/tmpl/aNumber.tmpl",
                    sources: ["work/aNumber/src.temper"],
                    sourcesContent: ["123"],
                    names: ["return"],
                    // Haven't checked.
                    mappings: "AAAA,wCAAAA,SAAA,aAAG,CAAA;AAAH,MAAG,CAAAA,SAAA",
                  }
                }
              }
            }
        """,
    )

    @Test
    fun noNamedDirectory() = assertGeneratedCode(
        inputs = inputFileMapFromJson(
            """
                |{
                |  // The file at root is in a directory module probably named "-work" or something similar
                |  fileAtRoot.temper: "export let f(): Int { 42 }",
                |
                |  // But the library name gives us a basis for a top-level module name
                |  config.temper.md: ```
                |    # My 'Hello World' library
                |
                |        export let name = "hello-world";
                |
                |    bye
                |    ```
                |}
            """.trimMargin(),
        ),
        want = """
            |{
            |  tmpl: {
            |    hello_world.tmpl: {
            |      content: ```
            |        //// work// => hello_world.tmpl
            |        @QName("hello-world.f()") let f(): Int32 {
            |          return 42;
            |        }
            |
            |        ```
            |    },
            |    hello_world.tmpl.map: "__DO_NOT_CARE__",
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun forLoop() = assertGeneratedCode(
        inputs = inputFileMapFromJson(
            """
                |{
                |  for: {
                |    for.temper: "for (var x = 0; x < 10; ++x) {  }"
                |  }
                |}
            """.trimMargin(),
        ),
        want = """
        {
            "tmpl": {
                "for.tmpl": {
                    "_name": "for.tmpl",
                    "_type": "txt",
                    "content":
                    ```
                    //// work//for/ => for.tmpl
                    let nym`<#1` = builtins.nym`<` /* (Int32?, Int32?) -> Boolean */;
                    let nym`+#2` = builtins.nym`+` /* (Int32, Int32) -> Int32 */;
                    @QName("test-library/for.x=") var x__0: Int32 = 0;
                    module init {
                      while (nym`<#1`(x__0, 10)) {
                        x__0 = nym`+#2`(x__0, 1);
                      }
                    }

                    ```
                },
                "for.tmpl.map": "__DO_NOT_CARE__"
            }
        }
        """,
    )

    @Test
    fun typedLocal() = assertGeneratedCode(
        inputs = inputFileMapFromJson(
            """
                |{
                |  foo: {
                |    foo.temper: "let i: Int = 0",
                |  }
                |}
            """.trimMargin(),
        ),
        want = """
            {
              tmpl: {
                "foo.tmpl": {
                  content:
                  ```
                  //// work//foo/ => foo.tmpl
                  @QName("test-library/foo.i") @reach(\none) let i__0: Int32 = 0;

                  ```
                },
                "foo.tmpl.map": "__DO_NOT_CARE__",
              }
            }
        """,
    )

    @Test
    fun makePeanoProud() = assertGeneratedCode(
        inputs = inputFileMapFromJson(
            """
                |{
                |  peano: {
                |    peano.temper: ```
                |      var one = 1;
                |      // Having multiple assignments tricks compiler into not-inlining whole thing to 2
                |      one = one;
                |      one + one
                |
                |      ```
                |  }
                |}
            """.trimMargin(),
        ),
        moduleNeedsResult = true,
        want = """
            {
                "tmpl": {
                    "peano.tmpl": {
                        "content":
                        ```
                        //// work//peano/ => peano.tmpl
                        let nym`+#2` = builtins.nym`+` /* (Int32, Int32) -> Int32 */;
                        @QName("test-library/peano.one") var one__0: Int32 = 1;
                        module init {
                          one__0 = one__0;
                        }
                        let return__0: Int32 = nym`+#2`(one__0, one__0);
                        export return__0;

                        ```
                    },
                    "peano.tmpl.map": "__DO_NOT_CARE__"
                }
            }
        """,
    )

    @Test
    fun makePeanoProudPedantically() = assertGeneratedCode(
        inputs = inputFileMapFromJson(
            """
                |{
                |  peano: {
                |    peano.temper: ```
                |      var one: Int = 1;
                |      // Having multiple assignments tricks compiler into not-inlining whole thing to 2
                |      one = one;
                |      one + one
                |      ```
                |  }
                |}
            """.trimMargin(),
        ),
        moduleNeedsResult = true,
        want = """
            {
                "tmpl": {
                    "peano.tmpl": {
                        "content":
                        ```
                        //// work//peano/ => peano.tmpl
                        let nym`+#2` = builtins.nym`+` /* (Int32, Int32) -> Int32 */;
                        @QName("test-library/peano.one") var one__0: Int32 = 1;
                        module init {
                          one__0 = one__0;
                        }
                        let return__0: Int32 = nym`+#2`(one__0, one__0);
                        export return__0;

                        ```
                    },
                    "peano.tmpl.map": "__DO_NOT_CARE__"
                }
            }
        """,
    )

    @Ignore // do we care about this?
    @Test
    fun definitionsAreShared() = assertGeneratedCode(
        inputs = inputFileMapFromJson(
            """
                |{
                |  first: {
                |    one.temper: ```
                |      var one: Int = 1;
                |      // Having multiple assignments tricks compiler into not-inlining whole thing to 2
                |      one = one;
                |      one + one
                |      ```,
                |  },
                |  second: {
                |    two.temper: ```
                |      var two: Int = 2;
                |      two = two;
                |      two + two
                |      ```
                |  }
                |}
            """.trimMargin(),
        ),
        moduleNeedsResult = true,
        want = """
            |{
            |    "tmpl": {
            |        "first.tmpl": {
            |            "content":
            |            ```
            |            //// work//first/ => first.tmpl
            |            let nym`+#2` = builtins.nym`+` /* (Int32, Int32) -> Int32 */;
            |            @QName("test-library/first.one") var one__0: Int32 = 1;
            |            module init {
            |              one__0 = one__0;
            |            }
            |            let return__0: Int32 = nym`+#2`(one__0, one__0);
            |            export return__0;
            |
            |            ```
            |        },
            |        "second.tmpl": {
            |            "content":
            |            ```
            |            //// work//second/ => second.tmpl
            |            @QName("test-library/second.two") var two__0: Int32 = 2;
            |            module init {
            |              two__0 = two__0;
            |            }
            |            let return__0: Int32 = nym`+#2`(two__0, two__0);
            |            export return__0;
            |
            |            ```
            |        },
            |        "first.tmpl.map": "__DO_NOT_CARE__",
            |        "second.tmpl.map": "__DO_NOT_CARE__"
            |    }
            |}
        """.trimMargin(),
    )

    @Test
    fun fib() = assertGeneratedCode(
        inputs = inputFileMapFromJson(
            """
                |{
                |  fib: {
                |    fib.temper: ```
                |      fn fib(var i: Int): Int {
                |        var a: Int = 0;
                |        var b: Int = 1;
                |        while (i > 0) {
                |          let c = a + b;
                |          a = b;
                |          b = c;
                |          i -= 1
                |        }
                |        a
                |      }
                |      ```
                |  }
                |}
            """.trimMargin(),
        ),
        moduleNeedsResult = true,
        want = (
            """
                |{
                |    "tmpl": {
                |        "fib.tmpl": {
                |            "content":
                |            ```
                |            //// work//fib/ => fib.tmpl
                |            let nym`>#9` = builtins.nym`>` /* (Int32?, Int32?) -> Boolean */;
                |            let nym`+#10` = builtins.nym`+` /* (Int32, Int32) -> Int32 */;
                |            let nym`-#11` = builtins.nym`-` /* (Int32, Int32) -> Int32 */;
                |            @QName("test-library/fib.fib()") let fib__0(@QName("test-library/fib.fib().(i)") var i__0: Int32): Int32 {
                |              @QName("test-library/fib.fib().a=") var a__0: Int32 = 0;
                |              @QName("test-library/fib.fib().b=") var b__0: Int32 = 1;
                |              while (nym`>#9`(i__0, 0)) {
                |                @QName("test-library/fib.fib().c=") let c__0: Int32 = nym`+#10`(a__0, b__0);
                |                a__0 = b__0;
                |                b__0 = c__0;
                |                i__0 = nym`-#11`(i__0, 1);
                |              }
                |              return a__0;
                |            }
                |            let return__1: fn (Int32): Int32 = fib__0;
                |            export return__1;
                |
                |            ```
                |        },
                |        "fib.tmpl.map": "__DO_NOT_CARE__"
                |    }
                |}
            """.trimMargin()
            ),
    )

    @Test
    fun gracefulFailureWithIfBubbleBranchStrategy() = assertGeneratedCode(
        inputs = inputFileMapFromJson(
            """
                |{
                |  "Brahmagupta'sRevenge": {
                |    revenge.temper: "do { 0 / 0 } orelse 0"
                |  }
                |}
            """.trimMargin(),
        ),
        moduleNeedsResult = true,
        want = """
          {
            tmpl: {
              "Brahmagupta'sRevenge.tmpl": {
                content:
                  ```
                  //// work//Brahmagupta'sRevenge/ => Brahmagupta'sRevenge.tmpl
                  let nym`/#7` = builtins.nym`/` /* (Int32, Int32) -> Result<Int32, Bubble> */;
                  let return__3: Int32;
                  var t#1: Int32;
                  @fail var fail#0: Boolean;
                  module init {
                    ok#0: {
                      orelse#0: {
                        t#1 = hs (fail#0, nym`/#7`(0, 0));
                        if (fail#0) {
                          break orelse#0;
                        }
                        return__3 = t#1;
                        break ok#0;
                      }
                      return__3 = 0;
                    }
                  }
                  export return__3;

                  ```
              },
              "Brahmagupta'sRevenge.tmpl.map": "__DO_NOT_CARE__",
            }
          }
        """,
        supportNetwork = defaultTestSupportNetwork.copy(
            bubbleStrategy = BubbleBranchStrategy.IfHandlerScopeVar,
        ),
    )

    @Test
    fun gracefulFailureWithCatchBubbleBranchStrategy() = assertGeneratedCode(
        inputs = inputFileMapFromJson(
            """
                |{
                |  "Brahmagupta'sRevenge": {
                |    revenge.temper: "do { 0 / 0 } orelse 0"
                |  }
                |}
            """.trimMargin(),
        ),
        moduleNeedsResult = true,
        want = """
          {
            tmpl: {
              "Brahmagupta'sRevenge.tmpl": {
                content:
                  ```
                  //// work//Brahmagupta'sRevenge/ => Brahmagupta'sRevenge.tmpl
                  let nym`/#6` = builtins.nym`/` /* (Int32, Int32) -> Result<Int32, Bubble> */;
                  let return__3: Int32;
                  var t#2: Int32;
                  module init {
                    try {
                      t#2 = nym`/#6`(0, 0);
                      return__3 = t#2;
                    } catch {
                      return__3 = 0;
                    }
                  }
                  export return__3;

                  ```
              },
              "Brahmagupta'sRevenge.tmpl.map": "__DO_NOT_CARE__",
            }
          }
        """,
        supportNetwork = defaultTestSupportNetwork.copy(
            bubbleStrategy = BubbleBranchStrategy.CatchBubble,
        ),
    )

    @Test
    fun unhandledBubbleAtTopLevel() = assertGeneratedCode(
        inputs = inputFileMapFromJson(
            """
                |{
                |  bubbly-arithmetic: {
                |    unhandledBubble.temper: "0 / 0",
                |  }
                |}
            """.trimMargin(),
        ),
        moduleNeedsResult = true,
        want = """
          {
            tmpl: {
              "bubbly-arithmetic.tmpl": {
                content:
                  ```
                  //// work//bubbly-arithmetic/ => bubbly-arithmetic.tmpl
                  let nym`/#3` = builtins.nym`/` /* (Int32, Int32) -> Result<Int32, Bubble> */;
                  let return__3: Int32;
                  module init {
                    return__3 = nym`/#3`(0, 0);
                  }
                  export return__3;

                  ```
              },
              "bubbly-arithmetic.tmpl.map": "__DO_NOT_CARE__",
            }
          }
        """,
        supportNetwork = defaultTestSupportNetwork.copy(
            bubbleStrategy = BubbleBranchStrategy.CatchBubble,
        ),
    )

    @Test
    fun unhandledBubbleInFunction() = assertGeneratedCode(
        inputs = inputFileMapFromJson(
            """
                |{
                |  bubbly-functions: {
                |    bubbly.temper: ```
                |      let f(): Int throws Bubble { 0 / 0 }
                |      ```,
                |  }
                |}
            """.trimMargin(),
        ),
        want = """
          {
            tmpl: {
              "bubbly-functions.tmpl": {
                content:
                  ```
                  //// work//bubbly-functions/ => bubbly-functions.tmpl
                  let nym`/#5` = builtins.nym`/` /* (Int32, Int32) -> Result<Int32, Bubble> */;
                  @QName("test-library/bubbly-functions.f()") @reach(\none) let f__0(): Int32 | Bubble {
                    return nym`/#5`(0, 0);
                  }

                  ```
              },
              "bubbly-functions.tmpl.map": "__DO_NOT_CARE__",
            }
          }
        """,
        supportNetwork = defaultTestSupportNetwork.copy(
            bubbleStrategy = BubbleBranchStrategy.CatchBubble,
        ),
    )

    @Test
    fun unhandledBubbleInFunctionWithFailVar() = assertGeneratedCode(
        inputs = inputFileMapFromJson(
            """
                |{
                |  bubbly-functions: {
                |    bubbly.temper: ```
                |      let f(): Int throws Bubble { 0 / 0 }
                |      ```,
                |  }
                |}
            """.trimMargin(),
        ),
        want = """
          {
            tmpl: {
              "bubbly-functions.tmpl": {
                content:
                  ```
                  //// work//bubbly-functions/ => bubbly-functions.tmpl
                  let nym`/#5` = builtins.nym`/` /* (Int32, Int32) -> Result<Int32, Bubble> */;
                  @QName("test-library/bubbly-functions.f()") @reach(\none) let f__0(): Int32 | Bubble {
                    @QName("test-library/bubbly-functions.f().return") let return__0: Int32;
                    @fail var fail#0: Boolean;
                    return__0 = hs (fail#0, nym`/#5`(0, 0));
                    if (fail#0) {
                      return failure;
                    }
                    return return__0;
                  }

                  ```
              },
              "bubbly-functions.tmpl.map": "__DO_NOT_CARE__",
            }
          }
        """,
        supportNetwork = defaultTestSupportNetwork.copy(
            bubbleStrategy = BubbleBranchStrategy.IfHandlerScopeVar,
        ),
    )

    @Test
    fun simpleDoNothingLoop() = assertGeneratedCode(
        inputs = inputFileMapFromJson(
            // See GenerateCodeTest.simpleDoNothingLoop
            """
                |{
                |  do-nothing: {
                |    foo.temper: ```
                |      var i = 0;
                |      while (i < 3) { i += 1; }
                |      ```
                |  }
                |}
            """.trimMargin(),
        ),
        want = """
        {
            "tmpl": {
                "do-nothing.tmpl": {
                    "content":
                    ```
                    //// work//do-nothing/ => do-nothing.tmpl
                    let nym`<#1` = builtins.nym`<` /* (Int32?, Int32?) -> Boolean */;
                    let nym`+#2` = builtins.nym`+` /* (Int32, Int32) -> Int32 */;
                    @QName("test-library/do-nothing.i") var i__0: Int32 = 0;
                    module init {
                      while (nym`<#1`(i__0, 3)) {
                        i__0 = nym`+#2`(i__0, 1);
                      }
                    }

                    ```
                },
                "do-nothing.tmpl.map": "__DO_NOT_CARE__"
            }
        }
        """.trimIndent(),
    )

    @Test
    fun classDefinitionLooksLikeClass() = assertGeneratedCode(
        inputs = inputFileMapFromJson(
            """
                |{
                |  C: {
                |    C.temper: ```
                |      class C(private prop: Int) {
                |        public method(): Int { prop }
                |      }
                |      ```
                |  }
                |}
            """.trimMargin(),
        ),
        want = """
        {
            "tmpl": {
                "C.tmpl": {
                    "content":
                    ```
                    //// work//C/ => C.tmpl
                    @QName("test-library/C.type C") @reach(\none) class C__0 / C {
                      @QName("test-library/C.type C.prop") @constructorProperty @reach(\none) let prop__5: Int32;
                      @QName("test-library/C.type C.method()") @reach(\none) let method__6(this = this__1, @QName("test-library/C.type C.method().(this)") @impliedThis(C__0) this__1: C__0): Int32 {
                        return /* this */ this__1.prop__5;
                      }
                      @QName("test-library/C.type C.constructor()") @reach(\none) constructor__8(this = this__2, @QName("test-library/C.type C.constructor().(this)") @impliedThis(C__0) this__2: C__0, @QName("test-library/C.type C.constructor().(prop)") prop__9: Int32) {
                        /* this */ this__2.prop__5 = prop__9;
                        return;
                      }
                    }

                    ```
                },
                "C.tmpl.map": "__DO_NOT_CARE__",
            }
        }
        """,
    )

    @Test
    fun impliedGettersAndSetters() = assertGeneratedCode(
        inputs = inputFileMapFromJson(
            """
                |{
                |  C: {
                |    C.temper: ```
                |      class C(public var prop: Int) {}
                |      ```
                |  }
                |}
            """.trimMargin(),
        ),
        want = """
        {
            "tmpl": {
                "C.tmpl": {
                    "content":
                    ```
                    //// work//C/ => C.tmpl
                    @QName("test-library/C.type C") @reach(\none) class C__0 / C {
                      @QName("test-library/C.type C.prop") @constructorProperty @reach(\none) var prop__4: Int32;
                      @QName("test-library/C.type C.constructor()") @reach(\none) constructor__5(this = this__1, @QName("test-library/C.type C.constructor().(this)") @impliedThis(C__0) this__1: C__0, @QName("test-library/C.type C.constructor().(prop)") prop__6: Int32) {
                        /* this */ this__1.prop__4 = prop__6;
                        return;
                      }
                      @reach(\none) get.prop -> getprop__8(this = this__9, @impliedThis(C__0) this__9: C__0): Int32 {
                        return /* this */ this__9.prop__4;
                      }
                      @reach(\none) set.prop -> setprop__11(this = this__13, @impliedThis(C__0) this__13: C__0, newProp__12: Int32): Void {
                        /* this */ this__13.prop__4 = newProp__12;
                        return;
                      }
                    }

                    ```
                },
                "C.tmpl.map": "__DO_NOT_CARE__",
            }
        }
        """,
    )

    @Test
    fun equalityCheck() = assertGeneratedCode(
        inputs = inputFileMapFromJson(
            """
                |{
                |  foo: {
                |    foo.temper: ```
                |      export let foo(bar:String): Boolean {bar == ""}
                |      ```
                |  }
                |}
            """.trimMargin(),
        ),
        want = """
        {
            "tmpl": {
                "foo.tmpl": {
                    "content":
                    ```
                    //// work//foo/ => foo.tmpl
                    let nym`==#3` = builtins.nym`==` /* (String?, String?) -> Boolean */;
                    @QName("test-library/foo.foo()") let foo(@QName("test-library/foo.foo().(bar)") bar__0: String): Boolean {
                      return nym`==#3`(bar__0, "");
                    }

                    ```
                },
                "foo.tmpl.map": "__DO_NOT_CARE__"
            }
        }
        """.trimIndent(),
    )

    @Test
    fun notOperator() = assertGeneratedCode(
        inputs = inputFileMapFromJson(
            """
                |{
                |  not: {
                |    not.temper: ```
                |      export let not(b: Boolean): Boolean { !b }
                |      not(false)
                |      ```
                |  }
                |}
            """.trimMargin(),
        ),
        moduleNeedsResult = true,
        want = """
            {
                "tmpl": {
                    "not.tmpl": {
                        "content": ```
                         //// work//not/ => not.tmpl
                         @QName("test-library/not.not()") let not(@QName("test-library/not.not().(b)") b__0: Boolean): Boolean {
                           return !b__0;
                         }
                         let return__0: Boolean = true;
                         export return__0;

                         ```
                    },
                    "not.tmpl.map": "__DO_NOT_CARE__"
                }
            }
        """.trimIndent(),
    )

    @Test
    fun connectedMethod() = assertGeneratedCode(
        inputs = inputFileMapFromJson(
            """
                |{
                |  isEmptyWrapper: {
                |    wrapper.temper: ```
                |      fn (s: String): Boolean { s.isEmpty }
                |      ```
                |  }
                |}
            """.trimMargin(),
        ),
        moduleNeedsResult = true,
        want = """
            |{
            |    tmpl: {
            |       "isEmptyWrapper.tmpl": {
            |           content:
            |               ```
            |               //// work//isEmptyWrapper/ => isEmptyWrapper.tmpl
            |               let StringIsEmpty#0 = builtins.StringIsEmpty;
            |               let return__0(@QName("test-library/isEmptyWrapper.(s)") s__1: String): Boolean {
            |                 return StringIsEmpty#0(s__1);
            |               }
            |               export return__0;
            |
            |               ```,
            |       },
            |       "isEmptyWrapper.tmpl.map": "__DO_NOT_CARE__"
            |    }
            |}
        """.trimMargin(),
    )

    @Test
    fun inlineConnectedMethod() = assertGeneratedCode(
        inputs = inputFileMapFromJson(
            """
                |{
                |  lengthWrapper: {
                |    wrapper.temper: ```
                |      fn (ls: List<Int>): Int { ls.length }
                |      ```
                |  }
                |}
            """.trimMargin(),
        ),
        moduleNeedsResult = true,
        want = """
            |{
            |    tmpl: {
            |       "lengthWrapper.tmpl": {
            |           content:
            |               ```
            |               //// work//lengthWrapper/ => lengthWrapper.tmpl
            |               let return__0(@QName("test-library/lengthWrapper.(ls)") ls__1: List<Int32>): Int32 {
            |                 return (inline ListLength)(ls__1);
            |               }
            |               export return__0;
            |
            |               ```,
            |       },
            |       "lengthWrapper.tmpl.map": "__DO_NOT_CARE__"
            |    }
            |}
        """.trimMargin(),
    )

    @Test
    fun inlineConnectedMethodAsTmpl() = assertGeneratedCode(
        inputs = inputFileMapFromJson(
            """
                |{
                |  intToStringWrapper: {
                |    wrapper.temper: ```
                |      fn (n: Int): String { n.toString(10) }
                |      ```
                |  }
                |}
            """.trimMargin(),
        ),
        moduleNeedsResult = true,
        want = """
            |{
            |    tmpl: {
            |       "intToStringWrapper.tmpl": {
            |           content:
            |               ```
            |               //// work//intToStringWrapper/ => intToStringWrapper.tmpl
            |               let return__0(@QName("test-library/intToStringWrapper.(n)") n__1: Int32): String {
            |                 return "" + n__1;
            |               }
            |               export return__0;
            |
            |               ```,
            |       },
            |       "intToStringWrapper.tmpl.map": "__DO_NOT_CARE__"
            |    }
            |}
        """.trimMargin(),
    )

    @Test
    fun connectedConstructor() = assertGeneratedCode(
        inputs = inputFileMapFromJson(
            """
                |{
                |  newDeque: {
                |    newDeque.temper: "new Deque<String>()"
                |  }
                |}
            """.trimMargin(),
        ),
        moduleNeedsResult = true,
        want = """
            |{
            |    tmpl: {
            |       "newDeque.tmpl": {
            |           content:
            |               ```
            |               //// work//newDeque/ => newDeque.tmpl
            |               let DequeConstructor#0 = builtins.DequeConstructor;
            |               let return__2: Deque<String> = DequeConstructor#0();
            |               export return__2;
            |
            |               ```,
            |       },
            |       "newDeque.tmpl.map": "__DO_NOT_CARE__"
            |    }
            |}
        """.trimMargin(),
    )

    @Test
    fun restFormal() = assertGeneratedCode(
        inputs = inputFileMapFromJson(
            """
                |{
                |  restFormal: {
                |    restFormal.temper: "fn (...s: List<String>): Boolean { false }"
                |  }
                |}
            """.trimMargin(),
        ),
        moduleNeedsResult = true,
        want = """
        {
            "tmpl": {
                "restFormal.tmpl": {
                    "content":
                    ```
                    //// work//restFormal/ => restFormal.tmpl
                    let return__0(@QName("test-library/restFormal.s") @restFormal ...s__1: String): Boolean {
                      return false;
                    }
                    export return__0;

                    ```
                },
                "restFormal.tmpl.map": "__DO_NOT_CARE__"
            },
        }
        """,
    )

    @Test
    fun defaultExpressionForParameter() = assertGeneratedCode(
        inputs = inputFileMapFromJson(
            """
                |{
                |  defaults: {
                |    defaults.temper: "export let xOrZero(x: Int = 0): Int { x }"
                |  }
                |}
            """.trimMargin(),
        ),
        want = """
        |{
        |    tmpl: {
        |        "defaults.tmpl": {
        |            content:
        |            ```
        |            //// work//defaults/ => defaults.tmpl
        |            let isNull#0 = builtins.isNull /* <isNullT extends AnyValue>(isNullT?) -> Boolean */;
        |            @QName("test-library/defaults.xOrZero()") let xOrZero(@QName("test-library/defaults.xOrZero().(x)") @optional(true) x__0: Int32 | Null = null): Int32 {
        |              @QName("test-library/defaults.xOrZero().(x)") let x__1: Int32;
        |              if (isNull#0(x__0)) {
        |                x__1 = 0;
        |              } else {
        |                x__1 = notNull (x__0);
        |              }
        |              return x__1;
        |            }
        |
        |            ```
        |        },
        |        "defaults.tmpl.map": "__DO_NOT_CARE__"
        |    }
        |}
        """.trimMargin(),
    )

    @Ignore
    @Test
    fun enumType() = assertGeneratedCode(
        inputs = inputFileMapFromJson(
            """
                |{
                |  E: {
                |    E.temper: "export enum E { A, B }"
                |  }
                |}
            """.trimMargin(),
        ),
        want = """
        |{
        |    tmpl: {
        |        "E.tmpl": {
        |            content:
        |            ```
        |
        |            ```
        |        },
        |        "E.tmpl.map": "__DO_NOT_CARE__"
        |    }
        |}
        """.trimMargin(),
    )

    @Test
    fun method() = assertGeneratedCode(
        inputs = inputFileMapFromJson(
            """
                |{
                |  method: {
                |    method.temper: ```
                |      class C {
                |        public let f(x: String): String { x }
                |      }
                |      ```
                |  }
                |}
            """.trimMargin(),
        ),
        want = """
        {
            "tmpl": {
                "method.tmpl": {
                    "content":
                    ```
                    //// work//method/ => method.tmpl
                    @QName("test-library/method.type C") @reach(\none) class C__0 / C {
                      @QName("test-library/method.type C.f()") @reach(\none) let f__6(this = this__1, @QName("test-library/method.type C.f().(this)") @impliedThis(C__0) this__1: C__0, @QName("test-library/method.type C.f().(x)") x__7: String): String {
                        return x__7;
                      }
                      @QName("test-library/method.type C.constructor()") @reach(\none) constructor__9(this = this__2, @QName("test-library/method.type C.constructor().(this)") @impliedThis(C__0) this__2: C__0) {
                        return;
                      }
                    }

                    ```
                },
                "method.tmpl.map": "__DO_NOT_CARE__"
            },
        }
        """,
    )

    @Test
    fun genericMethod() = assertGeneratedCode(
        inputs = inputFileMapFromJson(
            """
                |{
                |  genericMethod: {
                |    genericMethod.temper: ```
                |      // same as disambiguate stage generic method test
                |      class C {
                |        public let f<T>(x: T): T { x }
                |      }
                |      ```
                |  }
                |}
            """.trimMargin(),
        ),
        want = """
            {
                "tmpl": {
                    "genericMethod.tmpl": {
                        "content":
                        ```
                        //// work//genericMethod/ => genericMethod.tmpl
                        @QName("test-library/genericMethod.type C") @reach(\none) class C__0 / C {
                          @QName("test-library/genericMethod.type C.f()") @reach(\none) let f__7<T__2 extends AnyValue>(this = this__1, @QName("test-library/genericMethod.type C.f().(this)") @impliedThis(C__0) this__1: C__0, @QName("test-library/genericMethod.type C.f().(x)") x__8: T__2): T__2 {
                            return x__8;
                          }
                          @QName("test-library/genericMethod.type C.constructor()") @reach(\none) constructor__10(this = this__3, @QName("test-library/genericMethod.type C.constructor().(this)") @impliedThis(C__0) this__3: C__0) {
                            return;
                          }
                        }

                        ```
                    },
                    "genericMethod.tmpl.map": "__DO_NOT_CARE__"
                }
            }
        """,
    )

    @Test
    fun genericFunction() = assertGeneratedCode(
        inputs = inputFileMapFromJson(
            """
                |{
                |  genericFunction: {
                |    genericFunction.temper: ```
                |      // same as disambiguate stage generic method test
                |      let f<T>(x: T): T { x }
                |      ```
                |  }
                |}
            """.trimMargin(),
        ),
        want = """
            {
                "tmpl": {
                    "genericFunction.tmpl": {
                        "content":
                        ```
                        //// work//genericFunction/ => genericFunction.tmpl
                        @QName("test-library/genericFunction.f()") @reach(\none) let f__7<T__2 extends AnyValue>(@QName("test-library/genericFunction.f().(x)") x__8: T__2): T__2 {
                          return x__8;
                        }

                        ```
                    },
                    "genericFunction.tmpl.map": "__DO_NOT_CARE__"
                }
            }
        """,
    )

    @Test
    fun genericType() = assertGeneratedCode(
        inputs = inputFileMapFromJson(
            """
                |{
                |  genericType: {
                |    generic.temper: "interface I<T> {}"
                |  }
                |}
            """.trimMargin(),
        ),
        want = """
            |{
            |    tmpl: {
            |        "genericType.tmpl": {
            |            content: ```
            |                //// work//genericType/ => genericType.tmpl
            |                require temper-core;
            |                let InterfaceTypeSupport#0 = InterfaceTypeSupport;
            |                @QName("test-library/genericType.type I") @reach(\none) interface I__0 / I<T__0 extends AnyValue> {
            |                }
            |
            |                ```
            |        },
            |        "genericType.tmpl.map": "__DO_NOT_CARE__"
            |    }
            |}
        """.trimMargin(),
    )

    @Test
    fun documentedFunction() = assertGeneratedCode(
        inputJsonPathToContent = """
            |{
            |  fn: {
            |    fn.temper:
            |      ```
            |      /**
            |       * A short explanation.
            |       *
            |       * A longer, more detailed explanation.
            |       */
            |      export let overExplained(): Void {}
            |      ```
            |  }
            |}
        """.trimMargin(),
        want = """
            |{
            |  "tmpl": {
            |    "fn.tmpl": {
            |      content:
            |        ```
            |        //// work//fn/ => fn.tmpl
            |        @QName("test-library/fn.overExplained()") @docString(["A short explanation.", "A short explanation.\n\nA longer, more detailed explanation.", "work/fn/fn.temper"]) let overExplained(): Void {
            |          return;
            |        }
            |
            |        ```,
            |    },
            |    "fn.tmpl.map": "__DO_NOT_CARE__",
            |  },
            |}
        """.trimMargin(),
    )

    @Test
    fun documentedClass() = assertGeneratedCode(
        inputJsonPathToContent = """
            |{
            |  myClass: {
            |    "myClass.temper":
            |      ```
            |      /** Classy comment */
            |      export class C(
            |        /** Proper comment */
            |        public let x: Int,
            |      ) {
            |        /** Methodical comment */
            |        public let method(): Int { 1 }
            |        /** More than method() */
            |        public get y(): Int { 2 }
            |      }
            |      ```
            |  }
            |}
        """.trimMargin(),
        want = """
            |{
            |  tmpl: {
            |    "myClass.tmpl": {
            |      content:
            |        ```
            |        //// work//myClass/ => myClass.tmpl
            |        @QName("test-library/myClass.type C") @docString(["Classy comment", "Classy comment", "work/myClass/myClass.temper"]) class C / C {
            |          @QName("test-library/myClass.type C.x") @constructorProperty @docString(["Proper comment", "Proper comment", "work/myClass/myClass.temper"]) let x__0: Int32;
            |          @QName("test-library/myClass.type C.method()") @docString(["Methodical comment", "Methodical comment", "work/myClass/myClass.temper"]) let method__0(this = this__0, @QName("test-library/myClass.type C.method().(this)") @impliedThis(C) this__0: C): Int32 {
            |            return 1;
            |          }
            |          @QName("test-library/myClass.type C.y") let y__0: Int32;
            |          @QName("test-library/myClass.type C.get y()") @docString(["More than method()", "More than method()", "work/myClass/myClass.temper"]) get.y -> get.y__1(this = this__1, @QName("test-library/myClass.type C.get y().(this)") @impliedThis(C) this__1: C): Int32 {
            |            return 2;
            |          }
            |          @QName("test-library/myClass.type C.constructor()") constructor__0(this = this__2, @QName("test-library/myClass.type C.constructor().(this)") @impliedThis(C) this__2: C, @QName("test-library/myClass.type C.constructor().(x)") x__1: Int32) {
            |            /* this */ this__2.x__0 = x__1;
            |            return;
            |          }
            |          get.x -> getx__0(this = this__3, @impliedThis(C) this__3: C): Int32 {
            |            return /* this */ this__3.x__0;
            |          }
            |        }
            |
            |        ```
            |    },
            |    "myClass.tmpl.map": "__DO_NOT_CARE__",
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun emptyType() = assertGeneratedCode(
        inputs = inputFileMapFromJson(
            """
                |{
                |  emptyType: {
                |    I.temper: "interface I {}"
                |  }
                |}
            """.trimMargin(),
        ),
        want = """
            |{
            |    tmpl: {
            |        "emptyType.tmpl": {
            |            content: ```
            |                //// work//emptyType/ => emptyType.tmpl
            |                require temper-core;
            |                let InterfaceTypeSupport#0 = InterfaceTypeSupport;
            |                @QName("test-library/emptyType.type I") @reach(\none) interface I__0 / I {
            |                }
            |
            |                ```
            |        },
            |        "emptyType.tmpl.map": "__DO_NOT_CARE__"
            |    }
            |}
        """.trimMargin(),
    )

    @Test
    fun interfaceWithPropertyHasGetter() = assertGeneratedCode(
        inputs = inputFileMapFromJson(
            """
                |{
                |  aType: {
                |    I.temper: "export interface I { public x: String; }"
                |  }
                |}
            """.trimMargin(),
        ),
        want = """
            |{
            |    tmpl: {
            |        "aType.tmpl": {
            |            content: ```
            |                //// work//aType/ => aType.tmpl
            |                require temper-core;
            |                let InterfaceTypeSupport#0 = InterfaceTypeSupport;
            |                @QName("test-library/aType.type I") interface I / I {
            |                  @QName("test-library/aType.type I.x") let x__0: String;
            |                  get.x -> nym`get.x#0`(this = this#0, this#0: I): String;
            |                }
            |
            |                ```
            |        },
            |        "aType.tmpl.map": "__DO_NOT_CARE__"
            |    }
            |}
        """.trimMargin(),
    )

    @Test
    fun nestedClasses() = assertGeneratedCode(
        inputs = inputFileMapFromJson(
            """
                |{
                |  nestedClass: {
                |    f.temper: ```
                |      let f(): AnyValue {
                |          class C(private x: Int) {}
                |          return new C(42);
                |      }
                |      ```
                |  }
                |}
            """.trimMargin(),
        ),
        want = """
            |{
            |    tmpl: {
            |        "nestedClass.tmpl": {
            |            content: ```
            |                //// work//nestedClass/ => nestedClass.tmpl
            |                @QName("test-library/nestedClass.f().type C") @reach(\none) class C__0 / C {
            |                  @QName("test-library/nestedClass.f().type C.x") @constructorProperty @reach(\none) let x__0: Int32;
            |                  @QName("test-library/nestedClass.f().type C.constructor()") @reach(\none) constructor__0(this = this__0, @QName("test-library/nestedClass.f().type C.constructor().(this)") @impliedThis(C__0) this__0: C__0, @QName("test-library/nestedClass.f().type C.constructor().(x)") x__1: Int32) {
            |                    /* this */ this__0.x__0 = x__1;
            |                    return;
            |                  }
            |                }
            |                @QName("test-library/nestedClass.f()") @reach(\none) let f__0(): AnyValue {
            |                  return /*new*/ C__0(42);
            |                }
            |
            |                ```
            |        },
            |        "nestedClass.tmpl.map": "__DO_NOT_CARE__"
            |    }
            |}
        """.trimMargin(),
    )

    @Test
    fun localDeclarationsNotEaten() = assertGeneratedCode(
        inputs = inputFileMapFromJson(
            // This example was reduced from the Myers diff functional test code.
            // The variable `c` was pulled into a synthesized, labeled block, and so was not
            // visible to its use in `e`'s initializer.
            """
                |{
                |  example: {
                |    eg.temper: ```
                |      let f(d: Deque<String?>): Void throws Bubble {
                |        let c: String? =
                |            if (!d.isEmpty) { d.removeFirst() } else { null };
                |        var e: String = c as String;
                |        console.log(e);
                |      }
                |      ```
                |  }
                |}
            """.trimMargin(),
        ),
        want = """
            |{
            |    "tmpl": {
            |        "example.tmpl": {
            |            "content": ```
            |                //// work//example/ => example.tmpl
            |                let GetConsole#0 = builtins.GetConsole;
            |                let DequeIsEmpty#0 = builtins.DequeIsEmpty;
            |                let DequeRemoveFirst#0 = builtins.DequeRemoveFirst;
            |                let isNull#0 = builtins.isNull /* <isNullT extends AnyValue>(isNullT?) -> Boolean */;
            |                let ConsoleLog#0 = builtins.ConsoleLog;
            |                let console#0: Console = GetConsole#0();
            |                @QName("test-library/example.f()") @reach(\none) let f__0(@QName("test-library/example.f().(d)") d__0: Deque<String | Null>): Void | Bubble {
            |                  var t#0: String | Null;
            |                  @fail var fail#0: Boolean;
            |                  @QName("test-library/example.f().c=") let c__0: String | Null;
            |                  if (!DequeIsEmpty#0(d__0)) {
            |                    t#0 = DequeRemoveFirst#0(d__0);
            |                    c__0 = t#0;
            |                  } else {
            |                    c__0 = null;
            |                  }
            |                  @QName("test-library/example.f().e=") var e__0: String;
            |                  if (isNull#0(c__0)) {
            |                    return failure;
            |                  } else {
            |                    e__0 = hs (fail#0, notNull (c__0));
            |                    if (fail#0) {
            |                      return failure;
            |                    }
            |                  }
            |                  ConsoleLog#0(console#0, e__0);
            |                  return;
            |                }
            |
            |                ```
            |        },
            |        "example.tmpl.map": "__DO_NOT_CARE__"
            |    }
            |}
        """.trimMargin(),
    )

    @Test
    fun setterUse() = assertGeneratedCode(
        inputs = inputFileMapFromJson(
            """
                |{
                |  foo: {
                |    foo.temper: ```
                |      class C(public var p: Int) {}
                |      let c = new C(0);
                |      c.p = 1;
                |      ```
                |  }
                |}
            """.trimMargin(),
        ),
        want = """
            |{
            |    tmpl: {
            |        "foo.tmpl": {
            |            content: ```
            |            //// work//foo/ => foo.tmpl
            |            @QName("test-library/foo.type C") class C__0 / C {
            |              @QName("test-library/foo.type C.p") @constructorProperty var p__0: Int32;
            |              @QName("test-library/foo.type C.constructor()") constructor__0(this = this__0, @QName("test-library/foo.type C.constructor().(this)") @impliedThis(C__0) this__0: C__0, @QName("test-library/foo.type C.constructor().(p)") p__1: Int32) {
            |                /* this */ this__0.p__0 = p__1;
            |                return;
            |              }
            |              get.p -> getp__0(this = this__1, @impliedThis(C__0) this__1: C__0): Int32 {
            |                return /* this */ this__1.p__0;
            |              }
            |              set.p -> setp__0(this = this__2, @impliedThis(C__0) this__2: C__0, newP__0: Int32): Void {
            |                /* this */ this__2.p__0 = newP__0;
            |                return;
            |              }
            |            }
            |            @QName("test-library/foo.c") let c__0: C__0 = /*new*/ C__0(0);
            |            module init {
            |              c__0.p = 1;
            |            }
            |
            |            ```
            |        },
            |        "foo.tmpl.map": "__DO_NOT_CARE__",
            |    }
            |}
        """.trimMargin(),
    )

    @Test
    fun voidReified() = assertGeneratedCode(
        inputs = inputFileMapFromJson(
            """
                |{
                |  foo: {
                |    foo.temper: ```
                |      let g(): Void throws Bubble {
                |        console.log("hi");
                |      }
                |
                |      let f(): Void throws Bubble {
                |        g()
                |      }
                |      ```
                |  }
                |}
            """.trimMargin(),
        ),
        moduleNeedsResult = true, // So we can reify void from the module result
        want = """
            |{
            |  "tmpl": {
            |    "foo.tmpl": {
            |      content: ```
            |      //// work//foo/ => foo.tmpl
            |      let GetConsole#0 = builtins.GetConsole;
            |      let ConsoleLog#0 = builtins.ConsoleLog;
            |      let console#0: Console = GetConsole#0();
            |      @QName("test-library/foo.g()") @reach(\none) let g__0(): Void | Bubble {
            |        ConsoleLog#0(console#0, "hi");
            |        return;
            |      }
            |      @QName("test-library/foo.f()") @reach(\none) let f__0(): Void | Bubble {
            |        g__0();
            |        return;
            |      }
            |      let return__0: Void = void;
            |      export return__0;
            |
            |      ```
            |    },
            |    "foo.tmpl.map": "__DO_NOT_CARE__",
            |  }
            |}
        """.trimMargin(),
        supportNetwork = defaultTestSupportNetwork.copy(
            bubbleStrategy = BubbleBranchStrategy.CatchBubble,
            representationOfVoid = RepresentationOfVoid.ReifyVoid,
        ),
    )

    @Test
    fun voidNotReified() = assertGeneratedCode(
        inputs = inputFileMapFromJson(
            """
                |{
                |  foo: {
                |    foo.temper: ```
                |      let g(): Void throws Bubble {
                |        console.log("hi");
                |      }
                |
                |      let f(): Void throws Bubble {
                |        g()
                |      }
                |      ```
                |  }
                |}
            """.trimMargin(),
        ),
        want = """
            |{
            |  "tmpl": {
            |    "foo.tmpl": {
            |      content: ```
            |      //// work//foo/ => foo.tmpl
            |      let GetConsole#0 = builtins.GetConsole;
            |      let ConsoleLog#0 = builtins.ConsoleLog;
            |      let console#0: Console = GetConsole#0();
            |      @QName("test-library/foo.g()") @reach(\none) let g__0(): Void | Bubble {
            |        ConsoleLog#0(console#0, "hi");
            |      }
            |      @QName("test-library/foo.f()") @reach(\none) let f__0(): Void | Bubble {
            |        g__0();
            |      }
            |
            |      ```
            |    },
            |    "foo.tmpl.map": "__DO_NOT_CARE__",
            |  }
            |}
        """.trimMargin(),
        supportNetwork = defaultTestSupportNetwork.copy(
            // With CatchBubble, we don't expect handler scope calls in the generated tmpl.
            bubbleStrategy = BubbleBranchStrategy.CatchBubble,
            representationOfVoid = RepresentationOfVoid.DoNotReifyVoid,
        ),
    )

    @Ignore
    @Test
    fun assertCanInlineToStmt() {
        assertGeneratedCode(
            inputs = inputFileMapFromJson(
                """
                    |{
                    |  uses-tests: {
                    |    tests.temper: ```
                    |      test("testing") {
                    |        assert(1 != 0) { "1 is not zero" }
                    |        assert(2 != 0) { "2 is not zero" }
                    |        void
                    |      }
                    |      ```
                    |  }
                    |}
                """.trimMargin(),
            ),
            want = """
                |{
                |  "tmpl": {
                |    "uses-tests.tmpl": {
                |      content: ```
                |        //// work//uses-tests/ => uses-tests.tmpl
                |        let fn__0(): String {
                |          return "1 is not zero";
                |        }
                |        module init {
                |          (inline assert_true)(true, fn__0);
                |        }
                |        let fn__1(): String {
                |          return "2 is not zero";
                |        }
                |        module init {
                |          (inline assert_true)(true, fn__1);
                |        }
                |
                |        ```,
                |    },
                |    "uses-tests.tmpl.map": "__DO_NOT_CARE__",
                |  }
                |}
            """.trimMargin(),
        )
    }

    @Test
    fun testReachableImport() = assertGeneratedCode(
        inputJsonPathToContent = """
            |{
            |  foo: {
            |    "foo.temper": ```
            |      export class Point(public x: Int) {}
            |      export let inc(n: Int): Int { n + 1 }
            |      ```,
            |  },
            |  bar: {
            |    "bar.temper": ```
            |      let { Point, inc } = import("../foo");
            |      @test("blah") let something(): Void { inc({ x: 1 }.x); }
            |      ```,
            |  }
            |}
        """.trimMargin(),
        want = """
            |{
            |  tmpl: {
            |    "foo.tmpl": {
            |      content: ```
            |        //// work//foo/ => foo.tmpl
            |        let nym`+#13` = builtins.nym`+` /* (Int32, Int32) -> Int32 */;
            |        @QName("test-library/foo.type Point") class Point / Point {
            |          @QName("test-library/foo.type Point.x") @constructorProperty let x__0: Int32;
            |          @QName("test-library/foo.type Point.constructor()") constructor__0(this = this__0, @QName("test-library/foo.type Point.constructor().(this)") @impliedThis(Point) this__0: Point, @QName("test-library/foo.type Point.constructor().(x)") x__1: Int32) {
            |            /* this */ this__0.x__0 = x__1;
            |            return;
            |          }
            |          get.x -> getx__0(this = this__1, @impliedThis(Point) this__1: Point): Int32 {
            |            return /* this */ this__1.x__0;
            |          }
            |        }
            |        @QName("test-library/foo.inc()") let inc(@QName("test-library/foo.inc().(n)") n__0: Int32): Int32 {
            |          return nym`+#13`(n__0, 1);
            |        }
            |
            |        ```,
            |    },
            |    "foo.tmpl.map": "__DO_NOT_CARE__",
            |    "bar.tmpl": {
            |      content: ```
            |        //// work//bar/ => bar.tmpl
            |        @reach(\test) let {
            |          inc as inc__0
            |        }: @QName("test-library/foo.inc()") fn (Int32) -> Int32 = import ("./foo.tmpl");
            |        let {
            |          Point
            |        }: @QName("test-library/foo.type Point") type = import ("./foo.tmpl");
            |        @QName("test-library/bar.something()") @test("blah") @test let something__0() {
            |          inc__0(/*new*/ Point(1).x);
            |          return;
            |        }
            |
            |        ```,
            |    },
            |    "bar.tmpl.map": "__DO_NOT_CARE__",
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun typeReferencedCrossModule() = assertGeneratedCode(
        inputJsonPathToContent = """
            |{
            |  foo: {
            |    "foo.temper": ```
            |      export class Point(
            |        private let x: Int,
            |        private let y: Int,
            |      ) {}
            |      ```,
            |  },
            |  bar: {
            |    "bar.temper": ```
            |      let { Point } = import("../foo");
            |      export let p = { x: 1, y: 2 };
            |      ```,
            |  }
            |}
        """.trimMargin(),
        want = """
            |{
            |  tmpl: {
            |    "foo.tmpl": {
            |      content: ```
            |        //// work//foo/ => foo.tmpl
            |        @QName("test-library/foo.type Point") class Point / Point {
            |          @QName("test-library/foo.type Point.x") @constructorProperty let x__0: Int32;
            |          @QName("test-library/foo.type Point.y") @constructorProperty let y__0: Int32;
            |          @QName("test-library/foo.type Point.constructor()") constructor__0(this = this__0, @QName("test-library/foo.type Point.constructor().(this)") @impliedThis(Point) this__0: Point, @QName("test-library/foo.type Point.constructor().(x)") x__1: Int32, @QName("test-library/foo.type Point.constructor().(y)") y__1: Int32) {
            |            /* this */ this__0.x__0 = x__1;
            |            /* this */ this__0.y__0 = y__1;
            |            return;
            |          }
            |        }
            |
            |        ```,
            |    },
            |    "foo.tmpl.map": "__DO_NOT_CARE__",
            |    "bar.tmpl": {
            |      content: ```
            |        //// work//bar/ => bar.tmpl
            |        let {
            |          Point
            |        }: @QName("test-library/foo.type Point") type = import ("./foo.tmpl");
            |        @QName("test-library/bar.p") let p: Point = /*new*/ Point(1, 2);
            |
            |        ```,
            |    },
            |    "bar.tmpl.map": "__DO_NOT_CARE__",
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun functionReferencedCrossModule() = assertGeneratedCode(
        inputJsonPathToContent = $$"""
            |{
            |  foo: {
            |    "foo.temper": ```
            |      // A pure function
            |      export let emphatically(s: String): String { "${s}!!!" }
            |      // A side-effecting function.
            |      export let sayEmphatically(s: String): Void { console.log(emphatically(s)) }
            |      ```,
            |  },
            |  bar: {
            |    "bar.temper": ```
            |      let { emphatically, sayEmphatically } = import("../foo");
            |      sayEmphatically(emphatically("Hello"));
            |      ```,
            |  }
            |}
        """.trimMargin(),
        want = """
            |{
            |  tmpl: {
            |    "foo.tmpl": {
            |      content: ```
            |        //// work//foo/ => foo.tmpl
            |        let GetConsole#0 = builtins.GetConsole;
            |        let cat#0 = builtins.cat /* (...String) -> String */;
            |        let ConsoleLog#0 = builtins.ConsoleLog;
            |        let console#0: Console = GetConsole#0();
            |        @QName("test-library/foo.emphatically()") let emphatically(@QName("test-library/foo.emphatically().(s)") s__0: String): String {
            |          return cat#0(s__0, "!!!");
            |        }
            |        @QName("test-library/foo.sayEmphatically()") let sayEmphatically(@QName("test-library/foo.sayEmphatically().(s)") s__1: String): Void {
            |          ConsoleLog#0(console#0, emphatically(s__1));
            |          return;
            |        }
            |
            |        ```,
            |    },
            |    "foo.tmpl.map": "__DO_NOT_CARE__",
            |    "bar.tmpl": {
            |      content: ```
            |        //// work//bar/ => bar.tmpl
            |        @reach(\none) let {
            |          emphatically as emphatically__0
            |        }: @QName("test-library/foo.emphatically()") fn (String) -> String = import ("./foo.tmpl");
            |        let {
            |          sayEmphatically as sayEmphatically__0
            |        }: @QName("test-library/foo.sayEmphatically()") fn (String) -> Void = import ("./foo.tmpl");
            |        module init {
            |          sayEmphatically__0("Hello!!!");
            |        }
            |
            |        ```,
            |    },
            |    "bar.tmpl.map": "__DO_NOT_CARE__",
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun typeUsedButNotInterpretedIsImported() = assertGeneratedCode(
        inputJsonPathToContent = """
            |{
            |  foo: {
            |    "foo.temper": ```
            |      export class C {}
            |      export let c(): C { return new C(); }
            |      ```,
            |  },
            |  bar: {
            |    "bar.temper": ```
            |      let { c } = import("../foo");
            |      // The inferred type here is C which is exported.
            |      // The translation has to reference it even though it's not imported.
            |      let myC = c();
            |      myC
            |      ```,
            |  }
            |}
        """.trimMargin(),
        moduleNeedsResult = true,
        want = """
            |{
            |  tmpl: {
            |    "foo.tmpl": {
            |      content: ```
            |        //// work//foo/ => foo.tmpl
            |        @QName("test-library/foo.type C") class C / C {
            |          @QName("test-library/foo.type C.constructor()") constructor__0(this = this__0, @QName("test-library/foo.type C.constructor().(this)") @impliedThis(C) this__0: C) {
            |            return;
            |          }
            |        }
            |        @QName("test-library/foo.c()") let c(): C {
            |          return /*new*/ C();
            |        }
            |        let return__2: Void = void;
            |        export return__2;
            |
            |        ```
            |    },
            |    "foo.tmpl.map": "__DO_NOT_CARE__",
            |    "bar.tmpl": {
            |        "content":
            |        ```
            |        //// work//bar/ => bar.tmpl
            |        let {
            |          C
            |        }: @QName("test-library/foo.type C") type = import ("./foo.tmpl");
            |        let {
            |          c as c__0
            |        }: @QName("test-library/foo.c()") fn () -> C = import ("./foo.tmpl");
            |        @QName("test-library/bar.myC") let myC__0: C = c__0();
            |        let return__1: C = myC__0;
            |        export return__1;
            |
            |        ```,
            |    },
            |    "bar.tmpl.map": "__DO_NOT_CARE__",
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun localNameDefinedForNeededExternalDefinitionThatIsUsedButNotImported() {
        // Construct a module that refers to a name from another without
        // either an export or an import.
        var xNameCaptured: SourceName? = null

        assertGeneratedCode(
            inputJsonPathToContent = """
                |{
                |  a: { "a.temper": "$DELIVERED_BY_MODULE_MAKER" },
                |  b: { "b.temper": "$DELIVERED_BY_MODULE_MAKER" },
                |}
            """.trimMargin(),
            want = """
                |{
                |  tmpl: {
                |    "a.tmpl": {
                |      content: ```
                |        //// work//a/ => a.tmpl
                |        let nym`/#16` = builtins.nym`/` /* (Float64, Float64) -> Result<Float64, Bubble> */;
                |        @fail var fail#0: Boolean;
                |        @QName("test-library/a.x") let x__0: Float64;
                |        module init {
                |          x__0 = hs (fail#0, nym`/#16`(0.0, 0.0));
                |          if (fail#0) {
                |            abortLoad;
                |          }
                |        }
                |
                |        ```
                |    },
                |    "a.tmpl.map": "__DO_NOT_CARE__",
                |    "b.tmpl": {
                |        "content":
                |        ```
                |        //// work//b/ => b.tmpl
                |        let {
                |          x__0 as x__1
                |        }: @QName("test-library/a.x") const Float64 = import ("./a.tmpl");
                |        let GetConsole#0 = builtins.GetConsole;
                |        let Float64ToString#0 = builtins.Float64ToString;
                |        let ConsoleLog#0 = builtins.ConsoleLog;
                |        @fail var fail#1: Boolean;
                |        var t#0: Console = GetConsole#0();
                |        var t#1: Float64;
                |        module init {
                |          t#1 = hs (fail#1, cast (x__1, Float64));
                |          if (fail#1) {
                |            abortLoad;
                |          }
                |          ConsoleLog#0(t#0, Float64ToString#0(t#1));
                |        }
                |
                |        ```,
                |    },
                |    "b.tmpl.map": "__DO_NOT_CARE__",
                |  },
                |  errors: [
                |    "Missing type info for x__0!",
                |    "Cannot translate Invalid!",
                |  ]
                |}
            """.trimMargin(),
            supportNetwork = defaultTestSupportNetwork.copy(
                needsLocalNameForExternallyDefinedValue = true,
            ),
        ) customizeHook@{ module, isNew ->
            if (!isNew) {
                return@customizeHook
            }
            val doc = Document(module)
            val moduleName = module.loc as ModuleName

            val isA = "a" == moduleName.sourceFile.last().fullName
            if (isA) {
                xNameCaptured = doc.nameMaker.unusedSourceName(ParsedName("x"))
            }
            val xName = xNameCaptured!!

            module.deliverContent(
                doc.treeFarm.grow(Position(moduleName, 0, 0)) {
                    Block {
                        if (isA) {
                            Decl(xName) {
                                V(typeSymbol)
                                V(Types.vFloat64)
                                V(initSymbol)
                                Call(BuiltinFuns.divFn) {
                                    V(Value(0.0, TFloat64))
                                    V(Value(0.0, TFloat64))
                                }
                            }
                        } else {
                            Call {
                                Call {
                                    Rn(BuiltinName("."))
                                    Rn(BuiltinName("console"))
                                    V(Value(Symbol("log")))
                                }
                                Call {
                                    Call {
                                        Rn(BuiltinName("."))
                                        Call {
                                            Rn(BuiltinName("as"))
                                            Rn(xName)
                                            Rn(BuiltinName("Float64"))
                                        }
                                        V(vToStringSymbol)
                                    }
                                }
                            }
                        }
                    }
                },
            )
        }
    }

    @Test
    fun classBeforeInitDefn() = assertGeneratedCode(
        inputJsonPathToContent = """
            |{
            |  foo: {
            |    "foo.temper": ```
            |      // Issue #1631
            |      export class A(public a: String = hi()) {}
            |      let hi(): String { "hi" }
            |      void
            |      ```,
            |  }
            |}
        """.trimMargin(),
        want = """
            |{
            |  tmpl: {
            |    "foo.tmpl": {
            |      content: ```
            |        //// work//foo/ => foo.tmpl
            |        let isNull#0 = builtins.isNull /* <isNullT extends AnyValue>(isNullT?) -> Boolean */;
            |        @QName("test-library/foo.type A") class A / A {
            |          @QName("test-library/foo.type A.a") @constructorProperty let a__0: String;
            |          @QName("test-library/foo.type A.constructor()") constructor__0(this = this__0, @QName("test-library/foo.type A.constructor().(this)") @impliedThis(A) this__0: A, @QName("test-library/foo.type A.constructor().(a)") @optional(true) a__1: String | Null = null) {
            |            @QName("test-library/foo.type A.constructor().(a)") let a__2: String;
            |            if (isNull#0(a__1)) {
            |              a__2 = "hi";
            |            } else {
            |              a__2 = notNull (a__1);
            |            }
            |            /* this */ this__0.a__0 = a__2;
            |            return;
            |          }
            |          get.a -> geta__0(this = this__1, @impliedThis(A) this__1: A): String {
            |            return /* this */ this__1.a__0;
            |          }
            |        }
            |        @QName("test-library/foo.hi()") @reach(\none) let hi__0(): String {
            |          return "hi";
            |        }
            |
            |        ```
            |    },
            |    "foo.tmpl.map": "__DO_NOT_CARE__",
            |  },
            |}
        """.trimMargin(),
    )

    @Test
    fun controlFlowFuzz() = assertGeneratedCode(
        inputFileMapFromJson(
            """
                |{
                |  foo: {
                |    foo.temper: ```
                |      let f(): Void {
                |        var data = 0;
                |        for (var x: Int = 0; x <= 1; x += 1) {
                |          console.log("a");
                |          for (var y: Int = 75; y <= 77; y += 1) {
                |            console.log("b");
                |            if ((data++) >= 76) {
                |              console.log("c");
                |              break;
                |            } else {
                |              continue;
                |            }
                |          }
                |          console.log("d");
                |        }
                |      }
                |      ```
                |  }
                |}
            """.trimMargin(),
        ),
        want = """
            |{
            |  tmpl: {
            |    "foo.tmpl": {
            |      content: ```
            |        //// work//foo/ => foo.tmpl
            |        let GetConsole#0 = builtins.GetConsole;
            |        let nym`<=#28` = builtins.nym`<=` /* (Int32?, Int32?) -> Boolean */;
            |        let ConsoleLog#0 = builtins.ConsoleLog;
            |        let nym`+#30` = builtins.nym`+` /* (Int32, Int32) -> Int32 */;
            |        let nym`>=#31` = builtins.nym`>=` /* (Int32?, Int32?) -> Boolean */;
            |        let console#0: Console = GetConsole#0();
            |        @QName("test-library/foo.f()") @reach(\none) let f__0(): Void {
            |          @QName("test-library/foo.f().data=") var data__0: Int32 = 0;
            |          @QName("test-library/foo.f().x=") var x__0: Int32 = 0;
            |          while (nym`<=#28`(x__0, 1)) {
            |            ConsoleLog#0(console#0, "a");
            |            @QName("test-library/foo.f().y=") var y__0: Int32 = 75;
            |            while (nym`<=#28`(y__0, 77)) {
            |              ConsoleLog#0(console#0, "b");
            |              let postfixReturn#0: Int32 = data__0;
            |              data__0 = nym`+#30`(data__0, 1);
            |              if (nym`>=#31`(postfixReturn#0, 76)) {
            |                ConsoleLog#0(console#0, "c");
            |                break;
            |              }
            |              y__0 = nym`+#30`(y__0, 1);
            |            }
            |            ConsoleLog#0(console#0, "d");
            |            x__0 = nym`+#30`(x__0, 1);
            |          }
            |          return;
            |        }
            |
            |        ```
            |    },
            |    "foo.tmpl.map": "__DO_NOT_CARE__",
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun nonVarAssignedInHappyPathAndBubblePath() = assertGeneratedCode(
        inputs = inputFileMapFromJson(
            """
                |{
                |  foo: {
                |    foo.temper: ```
                |      export let f(a: Int, b: Int): Void {
                |        var x;
                |        let y;
                |        (
                |          do {
                |            x = a / b;
                |            y = b / a;
                |          }
                |        ) orelse (
                |          do {
                |            x = -1;
                |            y = -1;
                |          }
                |        );
                |        console.log(x.toString());
                |        console.log((x + y).toString());
                |      }
                |      ```
                |  }
                |}
            """.trimMargin(),
        ),
        want = """
            |{
            |  tmpl: {
            |    "foo.tmpl": {
            |      content: ```
            |        //// work//foo/ => foo.tmpl
            |        let GetConsole#0 = builtins.GetConsole;
            |        let nym`/#33` = builtins.nym`/` /* (Int32, Int32) -> Result<Int32, Bubble> */;
            |        let ConsoleLog#0 = builtins.ConsoleLog;
            |        let nym`+#36` = builtins.nym`+` /* (Int32, Int32) -> Int32 */;
            |        let console#0: Console = GetConsole#0();
            |        @QName("test-library/foo.f()") let f(@QName("test-library/foo.f().(a)") a__0: Int32, @QName("test-library/foo.f().(b)") b__0: Int32): Void {
            |          var t#0: Int32;
            |          var t#1: Int32;
            |          @QName("test-library/foo.f().x=") var x__0: Int32;
            |          @QName("test-library/foo.f().y=") let y__0: Int32;
            |          var y#0: Int32;${
            /* We synthesized this var late. */
            ""
        }
            |          try {
            |            t#0 = nym`/#33`(a__0, b__0);
            |            x__0 = t#0;
            |            t#1 = nym`/#33`(b__0, a__0);
            |            y#0 = t#1;
            |          } catch {
            |            x__0 = -1;
            |            y#0 = -1;
            |          }
            |          y__0 = y#0;${
            "" // Here's a single write back.
        }
            |          var t#2: String = "" + x__0;
            |          ConsoleLog#0(console#0, t#2);
            |          var t#3: String = "" + nym`+#36`(x__0, y__0);
            |          ConsoleLog#0(console#0, t#3);
            |          return;
            |        }
            |
            |        ```
            |    },
            |    "foo.tmpl.map": "__DO_NOT_CARE__",
            |  }
            |}
        """.trimMargin(),
        supportNetwork = defaultTestSupportNetwork.copy(
            bubbleStrategy = BubbleBranchStrategy.CatchBubble,
            mayAssignInBothTryAndRecover = false,
        ),
    )

    private fun runStaticConnectedMethodTest(
        want: String,
        supportNetwork: TestSupportNetwork,
    ) {
        assertGeneratedCode(
            inputs = inputFileMapFromJson(
                """
                    |{
                    |  defines: {
                    |    defines.temper: ```
                    |      @connected("C")
                    |      export class C {
                    |        @connected("C::f")
                    |        public static let f(): Void { console.log("f"); }
                    |        @connected("C::g")
                    |        public static let g(): Void { console.log("g"); }
                    |        @connected("C::constructor")
                    |        public constructor() {}
                    |      }
                    |      ```
                    |  },
                    |  uses: {
                    |    uses.temper: ```
                    |      let { C } = import("../defines");
                    |
                    |      C.f();
                    |      C.g();
                    |      ```
                    |  }
                    |}
                """.trimMargin(),
            ),
            want = want,
            supportNetwork = supportNetwork,
            customizeModule = allowConnectedDecoratorInTestInput,
        )
    }

    private val allowConnectedDecoratorInTestInput =
        ModuleCustomizeHook { module, isNew ->
            if (isNew) {
                module.addEnvironmentBindings(
                    mapOf(connectedDecoratorName to vConnectedDecorator),
                )
            }
        }

    @Test
    fun noStaticMethodConnected() = runStaticConnectedMethodTest(
        want = """
            |{
            |  "tmpl": {
            |    "defines.tmpl": {
            |      content: ```
            |        //// work//defines/ => defines.tmpl
            |        let GetConsole#0 = builtins.GetConsole;
            |        let console#0: Console = GetConsole#0();
            |        @QName("test-library/defines.type C") class C / C {
            |          @QName("test-library/defines.type C.f()") static let f__0(): Void {
            |            console#0.log("f");
            |            return;
            |          }
            |          @QName("test-library/defines.type C.g()") static let g__0(): Void {
            |            console#0.log("g");
            |            return;
            |          }
            |          @QName("test-library/defines.type C.constructor()") constructor__0(this = this__0, @QName("test-library/defines.type C.constructor().(this)") @impliedThis(C) this__0: C) {
            |            return;
            |          }
            |        }
            |
            |        ```,
            |    },
            |    "uses.tmpl": {
            |      content: ```
            |        //// work//uses/ => uses.tmpl
            |        let {
            |          C
            |        }: @QName("test-library/defines.type C") type = import ("./defines.tmpl");
            |        module init {
            |          C.f();
            |          C.g();
            |        }
            |
            |        ```,
            |    },
            |    "defines.tmpl.map": "__DO_NOT_CARE__",
            |    "uses.tmpl.map": "__DO_NOT_CARE__",
            |  }
            |}
        """.trimMargin(),
        supportNetwork = defaultTestSupportNetwork.copy(
            isConnected = { it == "::getConsole" },
        ),
    )

    @Test
    fun staticMethodFConnectedOnly() = runStaticConnectedMethodTest(
        want = """
            |{
            |  "tmpl": {
            |    "defines.tmpl": {
            |      content: ```
            |        //// work//defines/ => defines.tmpl
            |        let GetConsole#0 = builtins.GetConsole;
            |        let console#0: Console = GetConsole#0();
            |        @QName("test-library/defines.type C") class C / C {
            |          @QName("test-library/defines.type C.g()") static let g__0(): Void {
            |            console#0.log("g");
            |            return;
            |          }
            |          @QName("test-library/defines.type C.constructor()") constructor__0(this = this__0, @QName("test-library/defines.type C.constructor().(this)") @impliedThis(C) this__0: C) {
            |            return;
            |          }
            |        }
            |
            |        ```,
            |    },
            |    "uses.tmpl": {
            |      content: ```
            |        //// work//uses/ => uses.tmpl
            |        let {
            |          C
            |        }: @QName("test-library/defines.type C") type = import ("./defines.tmpl");
            |        let CF#0 = builtins.CF;
            |        module init {
            |          CF#0();
            |          C.g();
            |        }
            |
            |        ```,
            |    },
            |    "defines.tmpl.map": "__DO_NOT_CARE__",
            |    "uses.tmpl.map": "__DO_NOT_CARE__",
            |  }
            |}
        """.trimMargin(),
        supportNetwork = defaultTestSupportNetwork.copy(
            isConnected = { it == "::getConsole" || it == "C::f" },
        ),
    )

    @Test
    fun typeCAndStaticMethodFConnected() = runStaticConnectedMethodTest(
        // TODO: once we pre-compute name information, it'll be easier to do
        // things like convert a static method reference to a regular function
        // reference or to convert a class to a namespace.
        // In the meantime, it's easier to just generate a class, but generating
        // a class without a constructor is dodgy.
        want = """
            |{
            |  "tmpl": {
            |    "defines.tmpl": {
            |      content: ```
            |        //// work//defines/ => defines.tmpl
            |        let GetConsole#0 = builtins.GetConsole;
            |        let console#0: Console = GetConsole#0();
            |        @QName("test-library/defines.type C") class C connects C;
            |        @QName("test-library/defines.type C.g()") let g__0(): Void {
            |          console#0.log("g");
            |          return;
            |        }
            |
            |        ```,
            |    },
            |    "uses.tmpl": {
            |      content: ```
            |        //// work//uses/ => uses.tmpl
            |        let {
            |          g__0
            |        }: @QName("test-library/defines.type C.g()") fn () -> Void = import ("./defines.tmpl");
            |        let CF#0 = builtins.CF;
            |        module init {
            |          CF#0();
            |          g__0();
            |        }
            |
            |        ```,
            |    },
            |    "defines.tmpl.map": "__DO_NOT_CARE__",
            |    "uses.tmpl.map": "__DO_NOT_CARE__",
            |  }
            |}
        """.trimMargin(),
        supportNetwork = defaultTestSupportNetwork.copy(
            isConnected = { it == "::getConsole" || it == "C::f" || it == "C" || it == "C::constructor" },
        ),
    )

    @Test
    fun virtualInterfaceMethodsNotConnected() = assertGeneratedCode(
        customizeModule = allowConnectedDecoratorInTestInput,
        supportNetwork = defaultTestSupportNetwork.copy(
            isConnected = { connectedKey ->
                when (connectedKey) {
                    "I", "I::f", "I::getA" -> true
                    "I::g", "I::getB" -> false
                    else -> false
                }
            },
        ),
        inputs = inputFileMapFromJson(
            """
                |{
                |  foo: {
                |    foo.temper:
                |      ```
                |      @connected("I")
                |      export interface I {
                |        @connected("I::f")
                |        f(): Void {}
                |        @connected("I::g")
                |        g(): Void;
                |        @connected("I::getA")
                |        get a(): Int { 42 }
                |        @connected("I::getB")
                |        get b(): Int;
                |      }
                |      ```,
                |  }
                |}
            """.trimMargin(),
        ),
        want = """
            |{
            |  tmpl: {
            |    foo.tmpl: {
            |      content:
            |        ```
            |        //// work//foo/ => foo.tmpl
            |        require temper-core;
            |        let InterfaceTypeSupport#0 = InterfaceTypeSupport;
            |        let pureVirtual#0 = builtins.pureVirtual;
            |        @QName("test-library/foo.type I") interface I connects I;
            |
            |        ```
            |    },
            |    foo.tmpl.map: "__DO_NOT_CARE__",
            |  },
            |}
        """.trimMargin(),
    )

    @Test
    fun computedProperty() = assertGeneratedCode(
        inputJsonPathToContent = """
            |{
            |  foo: {
            |    foo.temper:
            |      ```
            |      class C {
            |        public get answer(): Int { 42 }
            |      }
            |      ```,
            |  }
            |}
        """.trimMargin(),
        want = """
            |{
            |  "tmpl": {
            |    "foo.tmpl": {
            |      content:
            |        ```
            |        //// work//foo/ => foo.tmpl
            |        @QName("test-library/foo.type C") @reach(\none) class C__0 / C {
            |          @QName("test-library/foo.type C.answer") @reach(\none) let answer__0: Int32;
            |          @QName("test-library/foo.type C.get answer()") @reach(\none) get.answer -> get.answer__1(this = this__0, @QName("test-library/foo.type C.get answer().(this)") @impliedThis(C__0) this__0: C__0): Int32 {
            |            return 42;
            |          }
            |          @QName("test-library/foo.type C.constructor()") @reach(\none) constructor__0(this = this__1, @QName("test-library/foo.type C.constructor().(this)") @impliedThis(C__0) this__1: C__0) {
            |            return;
            |          }
            |        }
            |
            |        ```
            |    },
            |    "foo.tmpl.map": "__DO_NOT_CARE__",
            |  },
            |}
        """.trimMargin(),
    )

    // Check that inline support code are not doubly called.
    // There was a problem in the C# and Lua backends where the connection
    // for static methods like `Date.yearsBetween(a, b)` came out as
    // `TemperCore.Temporal.yearsBetween()(a, b)`.
    @Test
    fun todaySeemsNice() = assertGeneratedCode(
        // We invoke this here with a customized TestSupportNetwork
        // that uses an inline support code wrapper for Date.today().
        // The next test checks the path where we do not inline it.
        inputJsonPathToContent = """
            |{
            |  foo: {
            |    "foo.temper": ```
            |      let { Date } = import("std/temporal");
            |      console.log(Date.today().toString());
            |      ```
            |  }
            |}
        """.trimMargin(),
        want = """
            |{
            |  "tmpl": {
            |    "foo.tmpl": {
            |      content: ```
            |          //// work//foo/ => foo.tmpl
            |          let GetConsole#0 = builtins.GetConsole;
            |          let DateToString#0 = builtins.DateToString;
            |          let ConsoleLog#0 = builtins.ConsoleLog;
            |          module init {
            |            ConsoleLog#0(GetConsole#0(), DateToString#0((inline today_inlined (...))()));
            |          }
            |
            |          ```
            |    },
            |    "foo.tmpl.map": "__DO_NOT_CARE__",
            |  },
            |}
        """.trimMargin(),
        supportNetwork = object : TestSupportNetwork(
            BubbleBranchStrategy.CatchBubble,
            CoroutineStrategy.TranslateToGenerator,
            RepresentationOfVoid.ReifyVoid,
        ) {
            override fun translateConnectedReference(
                pos: Position,
                connectedKey: String,
                genre: Genre,
            ): SupportCode? {
                if (connectedKey == "Date::today") {
                    return object : InlineSupportCode<TmpL.Tree, TmpLTranslator> {
                        override val needsThisEquivalent: Boolean get() = false
                        override fun inlineToTree(
                            pos: Position,
                            arguments: List<TypedArg<TmpL.Tree>>,
                            returnType: Type2,
                            translator: TmpLTranslator,
                        ): TmpL.Tree {
                            return TmpL.CallExpression(
                                pos = pos,
                                fn = TmpL.FnReference(
                                    TmpL.Id(pos, BuiltinName("myDateToday"), null),
                                    Signature2(returnType2 = WellKnownTypes.anyValueType2, false, listOf()),
                                ),
                                parameters = arguments.map { it.expr as TmpL.Actual },
                                type = returnType,
                            )
                        }

                        override fun renderTo(tokenSink: TokenSink) {
                            tokenSink.word("today_inlined")
                            tokenSink.emit(OutToks.leftParen)
                            tokenSink.emit(OutToks.ellipses)
                            tokenSink.emit(OutToks.rightParen)
                        }
                    }
                }
                return super.translateConnectedReference(pos, connectedKey, genre)
            }
        },
    )

    @Test
    fun todaySeemsNiceButNotWorthInlining() = assertGeneratedCode(
        // See the discussion for the test above with a similar, shorter name.
        inputJsonPathToContent = """
            |{
            |  foo: {
            |    "foo.temper": ```
            |      let { Date } = import("std/temporal");
            |      console.log(Date.today().toString());
            |      ```
            |  }
            |}
        """.trimMargin(),
        want = """
            |{
            |  "tmpl": {
            |    "foo.tmpl": {
            |      content: ```
            |          //// work//foo/ => foo.tmpl
            |          let GetConsole#0 = builtins.GetConsole;
            |          let DateToday#0 = builtins.DateToday;
            |          let DateToString#0 = builtins.DateToString;
            |          let ConsoleLog#0 = builtins.ConsoleLog;
            |          module init {
            |            ConsoleLog#0(GetConsole#0(), DateToString#0(DateToday#0()));
            |          }
            |
            |          ```
            |    },
            |    "foo.tmpl.map": "__DO_NOT_CARE__",
            |  },
            |}
        """.trimMargin(),
    )

    @Test
    fun dateFromIsoString() = assertGeneratedCode(
        """
            |{
            |  foo: {
            |    foo.temper: ```
            |      let { Date } = import("std/temporal");
            |      export let d = Date.fromIsoString("2001-02-03");
            |      ```
            |  }
            |}
        """.trimMargin(),
        want = """
            |{
            |  tmpl: {
            |    foo.tmpl: {
            |      content: ```
            |        //// work//foo/ => foo.tmpl
            |        let DateFromIsoString#0 = builtins.DateFromIsoString;
            |        @fail var fail#0: Boolean;
            |        @QName("test-library/foo.d") let d: Date;
            |        module init {
            |          d = hs (fail#0, DateFromIsoString#0("2001-02-03"));
            |          if (fail#0) {
            |            abortLoad;
            |          }
            |        }
            |
            |        ```
            |    },
            |    foo.tmpl.map: "__DO_NOT_CARE__",
            |  }
            |}
        """.trimMargin(),
    )

    companion object {
        private val simpleActorInputJsonPathToContent = """
            |{
            |  foo: {
            |    "foo.temper": ```
            |      let f(x: fn (): SafeGenerator<Empty>): SafeGenerator<Empty> { x() }
            |      do {
            |        let coro = f { (): GeneratorResult<Empty> extends GeneratorFn =>
            |          console.log("foo");
            |          yield;
            |          console.log("bar");
            |        };
            |        coro.next();
            |      }
            |      ```
            |  }
            |}
        """.trimMargin()
    }

    @Test
    fun coroutineAsGenerator() = assertGeneratedCode(
        // See the discussion for the test above with a similar, shorter name.
        inputJsonPathToContent = simpleActorInputJsonPathToContent,
        want = """
            |{
            |  "tmpl": {
            |    "foo.tmpl": {
            |      content: ```
            |          //// work//foo/ => foo.tmpl
            |          let GetConsole#0 = builtins.GetConsole;
            |          let ConsoleLog#0 = builtins.ConsoleLog;
            |          let SafeGeneratorNext#0 = builtins.SafeGeneratorNext;
            |          let console#0: Console = GetConsole#0();
            |          @QName("test-library/foo.f()") let f__0(@QName("test-library/foo.f().(x)") x__0: fn (): SafeGenerator<Empty>): SafeGenerator<Empty> {
            |            return x__0();
            |          }
            |          let * fn__0(): SafeGenerator<Empty> {
            |            ConsoleLog#0(console#0, "foo");
            |            yield;
            |            ConsoleLog#0(console#0, "bar");
            |          }
            |          @QName("test-library/foo.coro=") let coro__0: SafeGenerator<Empty> = f__0(fn__0);
            |          module init {
            |            SafeGeneratorNext#0(coro__0);
            |          }
            |
            |          ```
            |    },
            |    "foo.tmpl.map": "__DO_NOT_CARE__",
            |  },
            |}
        """.trimMargin(),
    )

    @Test
    fun controlFlowAsStateMachine() = assertGeneratedCode(
        supportNetwork = defaultTestSupportNetwork.copy(
            coroutineStrategy = CoroutineStrategy.TranslateToRegularFunction,
        ),
        inputJsonPathToContent = simpleActorInputJsonPathToContent,
        want = """
            |{
            |  "tmpl": {
            |    "foo.tmpl": {
            |      content: ```
            |          //// work//foo/ => foo.tmpl
            |          let GetConsole#0 = builtins.GetConsole;
            |          let ConsoleLog#0 = builtins.ConsoleLog;
            |          let Empty#0 = builtins.Empty;
            |          let ValueResultConstructor#0 = builtins.ValueResultConstructor;
            |          let DoneResult#0 = builtins.DoneResult;
            |          let adaptGeneratorFnSafe#0 = builtins.adaptGeneratorFnSafe /* <adaptGeneratorFnSafeYIELD extends AnyValue>(Fn__0<GeneratorResult<adaptGeneratorFnSafeYIELD>>) -> SafeGenerator<adaptGeneratorFnSafeYIELD> */;
            |          let SafeGeneratorNext#0 = builtins.SafeGeneratorNext;
            |          let console#0: Console = GetConsole#0();
            |          @QName("test-library/foo.f()") let f__0(@QName("test-library/foo.f().(x)") x__0: fn (): SafeGenerator<Empty>): SafeGenerator<Empty> {
            |            return x__0();
            |          }
            |          let fn__0(): SafeGenerator<Empty> {
            |            var caseIndex#0: Int32 = 0;
            |            let convertedCoroutine#0(generator#0: SafeGenerator<Empty>): GeneratorResult<Empty> {
            |              let caseIndexLocal#0: Int32 = caseIndex#0;
            |              caseIndex#0 = -1;
            |              when (caseIndexLocal#0) {
            |                0 -> do {
            |                  ConsoleLog#0(console#0, "foo");
            |                  caseIndex#0 = 1;
            |                  return ValueResultConstructor#0(Empty#0());
            |                }
            |                1 -> do {
            |                  ConsoleLog#0(console#0, "bar");
            |                  return DoneResult#0();
            |                }
            |                else -> do {
            |                  return DoneResult#0();
            |                }
            |              }
            |            }
            |            return adaptGeneratorFnSafe#0(convertedCoroutine#0);
            |          }
            |          @QName("test-library/foo.coro=") let coro__0: SafeGenerator<Empty> = f__0(fn__0);
            |          module init {
            |            SafeGeneratorNext#0(coro__0);
            |          }
            |
            |          ```
            |    },
            |    "foo.tmpl.map": "__DO_NOT_CARE__",
            |  },
            |}
        """.trimMargin(),
    )

    @Test
    fun coroutineConversionExtractsVariable() = assertGeneratedCode(
        supportNetwork = defaultTestSupportNetwork.copy(
            coroutineStrategy = CoroutineStrategy.TranslateToRegularFunction,
        ),
        inputJsonPathToContent = """
            |{
            |  foo: {
            |    "foo.temper": ```
            |      let initialI(): Int {
            |        console.log("Returning 123");
            |        123
            |      }
            |      let f(makeGenerator: fn (): SafeGenerator<Empty>): Void {
            |        let coroutine = makeGenerator();
            |         coroutine.next();
            |         coroutine.next();
            |      }
            |      f { (): GeneratorResult<Empty> extends GeneratorFn =>
            |        var i = initialI();
            |        while (true) {
            |          console.log(i.toString());
            |          i += 1;
            |          yield;
            |        }
            |      }
            |      ```
            |  }
            |}
        """.trimMargin(),
        want = """
            |{
            |  "tmpl": {
            |    "foo.tmpl": {
            |      content: ```
            |        //// work//foo/ => foo.tmpl
            |        let GetConsole#0 = builtins.GetConsole;
            |        let ConsoleLog#0 = builtins.ConsoleLog;
            |        let SafeGeneratorNext#0 = builtins.SafeGeneratorNext;
            |        let nym`+#58` = builtins.nym`+` /* (Int32, Int32) -> Int32 */;
            |        let Empty#0 = builtins.Empty;
            |        let ValueResultConstructor#0 = builtins.ValueResultConstructor;
            |        let DoneResult#0 = builtins.DoneResult;
            |        let adaptGeneratorFnSafe#0 = builtins.adaptGeneratorFnSafe /* <adaptGeneratorFnSafeYIELD extends AnyValue>(Fn__0<GeneratorResult<adaptGeneratorFnSafeYIELD>>) -> SafeGenerator<adaptGeneratorFnSafeYIELD> */;
            |        let console#0: Console = GetConsole#0();
            |        @QName("test-library/foo.initialI()") let initialI__0(): Int32 {
            |          ConsoleLog#0(console#0, "Returning 123");
            |          return 123;
            |        }
            |        @QName("test-library/foo.f()") let f__0(@QName("test-library/foo.f().(makeGenerator)") makeGenerator__0: fn (): SafeGenerator<Empty>): Void {
            |          @QName("test-library/foo.f().coroutine=") let coroutine__0: SafeGenerator<Empty> = makeGenerator__0();
            |          SafeGeneratorNext#0(coroutine__0);
            |          SafeGeneratorNext#0(coroutine__0);
            |          return;
            |        }
            |        let fn__0(): SafeGenerator<Empty> {
            |          var caseIndex#0: Int32 = 0;
            |          var t#0: String = "";
            |          @QName("test-library/foo.i=") var i__0: Int32 = 0;
            |## Here's the variable extracted with a zero-value.
            |          let convertedCoroutine#0(generator#0: SafeGenerator<Empty>): GeneratorResult<Empty> {
            |            while (true) {
            |              let caseIndexLocal#0: Int32 = caseIndex#0;
            |              caseIndex#0 = -1;
            |              when (caseIndexLocal#0) {
            |                0 -> do {
            |                  var t#1: Int32 = initialI__0();
            |                  i__0 = t#1;
            |## The initializer was left in place.
            |                  caseIndex#0 = 1;
            |                }
            |                1 -> do {
            |                  t#0 = "" + i__0;
            |                  ConsoleLog#0(console#0, t#0);
            |                  i__0 = nym`+#58`(i__0, 1);
            |                  caseIndex#0 = 2;
            |                  return ValueResultConstructor#0(Empty#0());
            |                }
            |                2 -> do {
            |                  caseIndex#0 = 1;
            |                }
            |                else -> do {
            |                  return DoneResult#0();
            |                }
            |              }
            |            }
            |          }
            |          return adaptGeneratorFnSafe#0(convertedCoroutine#0);
            |        }
            |        module init {
            |          f__0(fn__0);
            |        }
            |
            |        ```
            |    },
            |    "foo.tmpl.map": "__DO_NOT_CARE__",
            |  }
            |}
        """.trimMargin().stripDoubleHashCommentLinesToPutCommentsInlineBelow(),
    )

    @Test
    fun convertingCoroutineWithHelper() = assertGeneratedCode(
        supportNetwork = defaultTestSupportNetwork.copy(
            coroutineStrategy = CoroutineStrategy.TranslateToRegularFunction,
        ),
        inputJsonPathToContent = """
            |{
            |  foo: {
            |    "foo.temper": ```
            |      let f(makeGenerator: fn (): SafeGenerator<Empty>): Void {
            |        let generator = makeGenerator();
            |         generator.next();
            |         generator.next();
            |      }
            |      f { (): GeneratorResult<Empty> extends GeneratorFn =>
            |        // We extract `i` because it's used on both sides
            |        // of the `yield`.
            |        var i = 1;
            |        // If we extract `helper` we also need to extract `j`
            |        // so that it is visible.
            |        var j = 2;
            |        // No need to extract `k`
            |        var k = 3;
            |        // We extract `helper` because it's used on two sides
            |        // of a yield, but we shouldn't leave its initializer
            |        // in place; there's no reason to turn a function
            |        // declaration into an assignment to a function value.
            |        let helper(): Void { console.log((i + j).toString()); }
            |        j += 1;
            |        k += 1;
            |        console.log(k.toString());
            |        helper();
            |        yield;
            |        i += 1;
            |        helper();
            |      }
            |      ```
            |  }
            |}
        """.trimMargin(),
        want = """
            |{
            |  "tmpl": {
            |    "foo.tmpl": {
            |      content: ```
            |        //// work//foo/ => foo.tmpl
            |        let GetConsole#0 = builtins.GetConsole;
            |        let SafeGeneratorNext#0 = builtins.SafeGeneratorNext;
            |        let nym`+#67` = builtins.nym`+` /* (Int32, Int32) -> Int32 */;
            |        let ConsoleLog#0 = builtins.ConsoleLog;
            |        let Empty#0 = builtins.Empty;
            |        let ValueResultConstructor#0 = builtins.ValueResultConstructor;
            |        let DoneResult#0 = builtins.DoneResult;
            |        let adaptGeneratorFnSafe#0 = builtins.adaptGeneratorFnSafe /* <adaptGeneratorFnSafeYIELD extends AnyValue>(Fn__0<GeneratorResult<adaptGeneratorFnSafeYIELD>>) -> SafeGenerator<adaptGeneratorFnSafeYIELD> */;
            |        let console#0: Console = GetConsole#0();
            |        @QName("test-library/foo.f()") let f__0(@QName("test-library/foo.f().(makeGenerator)") makeGenerator__0: fn (): SafeGenerator<Empty>): Void {
            |          @QName("test-library/foo.f().generator=") let generator__0: SafeGenerator<Empty> = makeGenerator__0();
            |          SafeGeneratorNext#0(generator__0);
            |          SafeGeneratorNext#0(generator__0);
            |          return;
            |        }
            |        let fn__0(): SafeGenerator<Empty> {
            |          var caseIndex#0: Int32 = 0;
            |          @QName("test-library/foo.i=") var i__0: Int32 = 1;
            |          @QName("test-library/foo.j=") var j__0: Int32 = 2;
            |          @QName("test-library/foo.helper()") let helper__0(): Void {
            |            var t#0: String = "" + nym`+#67`(i__0, j__0);
            |            ConsoleLog#0(console#0, t#0);
            |            return;
            |          }
            |          let convertedCoroutine#0(generator#0: SafeGenerator<Empty>): GeneratorResult<Empty> {
            |            let caseIndexLocal#0: Int32 = caseIndex#0;
            |            caseIndex#0 = -1;
            |            when (caseIndexLocal#0) {
            |              0 -> do {
            |                @QName("test-library/foo.k=") var k__0: Int32 = 3;
            |                j__0 = nym`+#67`(j__0, 1);
            |                k__0 = nym`+#67`(k__0, 1);
            |                var t#1: String = "" + k__0;
            |                ConsoleLog#0(console#0, t#1);
            |                helper__0();
            |                caseIndex#0 = 1;
            |                return ValueResultConstructor#0(Empty#0());
            |              }
            |              1 -> do {
            |                i__0 = nym`+#67`(i__0, 1);
            |                helper__0();
            |                return DoneResult#0();
            |              }
            |              else -> do {
            |                return DoneResult#0();
            |              }
            |            }
            |          }
            |          return adaptGeneratorFnSafe#0(convertedCoroutine#0);
            |        }
            |        module init {
            |          f__0(fn__0);
            |        }
            |
            |        ```
            |    },
            |    "foo.tmpl.map": "__DO_NOT_CARE__",
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun nullAdjustmentForVariableExtractedFromConvertedCoroutine() = assertGeneratedCode(
        supportNetwork = defaultTestSupportNetwork.copy(
            coroutineStrategy = CoroutineStrategy.TranslateToRegularFunction,
        ),
        inputJsonPathToContent = """
            |{
            |  foo: {
            |    "foo.temper": ```
            |      let f(makeGenerator: fn (): SafeGenerator<Empty>): Void {
            |        let generator = makeGenerator();
            |        generator.next();
            |        generator.next();
            |        generator.next();
            |      }
            |
            |      class C(
            |        public s: String,
            |      ) {
            |        public constructor(s: String) {
            |          this.s = s;
            |          // This side-effect means that we can't create a
            |          // zero-value for `let c` below without triggering
            |          // a log out of order with `log("One")`.
            |          console.log("Made C");
            |        }
            |      }
            |
            |      f { (): GeneratorResult<Empty> extends GeneratorFn =>
            |        console.log("One");
            |        yield;
            |        // "Made C" happens between "One" and "Two".
            |        let c: C = new C("Three");
            |        console.log("Two");
            |        yield;
            |        console.log(c.s);
            |      }
            |      ```
            |  }
            |}
        """.trimMargin(),
        want = """
            |{
            |  "tmpl": {
            |    "foo.tmpl": {
            |      content: ```
            |        //// work//foo/ => foo.tmpl
            |        let GetConsole#0 = builtins.GetConsole;
            |        let ConsoleLog#0 = builtins.ConsoleLog;
            |        let SafeGeneratorNext#0 = builtins.SafeGeneratorNext;
            |        let Empty#0 = builtins.Empty;
            |        let ValueResultConstructor#0 = builtins.ValueResultConstructor;
            |        let DoneResult#0 = builtins.DoneResult;
            |        let adaptGeneratorFnSafe#0 = builtins.adaptGeneratorFnSafe /* <adaptGeneratorFnSafeYIELD extends AnyValue>(Fn__0<GeneratorResult<adaptGeneratorFnSafeYIELD>>) -> SafeGenerator<adaptGeneratorFnSafeYIELD> */;
            |        let console#0: Console = GetConsole#0();
            |        @QName("test-library/foo.type C") class C__0 / C {
            |          @QName("test-library/foo.type C.s") @constructorProperty let s__0: String;
            |          @QName("test-library/foo.type C.constructor()") constructor__0(this = this__0, @QName("test-library/foo.type C.constructor().(this)") @impliedThis(C__0) this__0: C__0, @QName("test-library/foo.type C.constructor().(s)") s__1: String) {
            |            /* this */ this__0.s__0 = s__1;
            |            ConsoleLog#0(console#0, "Made C");
            |            return;
            |          }
            |          get.s -> gets__0(this = this__1, @impliedThis(C__0) this__1: C__0): String {
            |            return /* this */ this__1.s__0;
            |          }
            |        }
            |        @QName("test-library/foo.f()") let f__0(@QName("test-library/foo.f().(makeGenerator)") makeGenerator__0: fn (): SafeGenerator<Empty>): Void {
            |          @QName("test-library/foo.f().generator=") let generator__0: SafeGenerator<Empty> = makeGenerator__0();
            |          SafeGeneratorNext#0(generator__0);
            |          SafeGeneratorNext#0(generator__0);
            |          SafeGeneratorNext#0(generator__0);
            |          return;
            |        }
            |        let fn__0(): SafeGenerator<Empty> {
            |          var caseIndex#0: Int32 = 0;
            |          var t#0: String = "";
            |## Originally `let c: C` but is `var c: C?` and initialized to null.
            |          @QName("test-library/foo.c=") var c__0: C__0 | Null = null;
            |          let convertedCoroutine#0(generator#0: SafeGenerator<Empty>): GeneratorResult<Empty> {
            |            let caseIndexLocal#0: Int32 = caseIndex#0;
            |            caseIndex#0 = -1;
            |            when (caseIndexLocal#0) {
            |              0 -> do {
            |                ConsoleLog#0(console#0, "One");
            |                caseIndex#0 = 1;
            |                return ValueResultConstructor#0(Empty#0());
            |              }
            |              1 -> do {
            |                c__0 = /*new*/ C__0("Three");
            |                ConsoleLog#0(console#0, "Two");
            |                caseIndex#0 = 2;
            |                return ValueResultConstructor#0(Empty#0());
            |              }
            |              2 -> do {
            |                t#0 = notNull (c__0).s;
            |                ConsoleLog#0(console#0, t#0);
            |                return DoneResult#0();
            |              }
            |              else -> do {
            |                return DoneResult#0();
            |              }
            |            }
            |          }
            |          return adaptGeneratorFnSafe#0(convertedCoroutine#0);
            |        }
            |        module init {
            |          f__0(fn__0);
            |        }
            |
            |        ```
            |    },
            |    "foo.tmpl.map": "__DO_NOT_CARE__",
            |  }
            |}
        """.trimMargin().stripDoubleHashCommentLinesToPutCommentsInlineBelow(),
    )

    private fun classOrdering(bubbleStrategy: BubbleBranchStrategy, want: String) = assertGeneratedCode(
        inputs = inputFileMapFromJson(
            """
                |{
                |  bubble-ordering: {
                |    foo.temper: ```
                |      // Bubble return type had a bad impact here before.
                |      export let juggle(some: Int): Apple { if (some <= 0) { fuji } else { gala } }
                |      export let fuji = new Apple().maybe();
                |      export let gala = new Apple().maybe();
                |      export class Apple {
                |        public maybe(): Apple throws Bubble { this }
                |      }
                |      ```
                |  }
                |}
            """.trimMargin(),
        ),
        want = want,
        supportNetwork = defaultTestSupportNetwork.copy(
            bubbleStrategy = bubbleStrategy,
        ),
    )

    @Test
    fun classOrderingCatch() = classOrdering(
        bubbleStrategy = BubbleBranchStrategy.CatchBubble,
        want = """
            |{
            |  tmpl: {
            |    "bubble-ordering.tmpl": {
            |      content:
            |      ```
            |      //// work//bubble-ordering/ => bubble-ordering.tmpl
            |      let nym`<=#24` = builtins.nym`<=` /* (Int32?, Int32?) -> Boolean */;
            |      @QName("test-library/bubble-ordering.type Apple") class Apple / Apple {
            |        @QName("test-library/bubble-ordering.type Apple.maybe()") let maybe__0(this = this__0, @QName("test-library/bubble-ordering.type Apple.maybe().(this)") @impliedThis(Apple) this__0: Apple): Apple | Bubble {
            |          return /* this */ this__0;
            |        }
            |        @QName("test-library/bubble-ordering.type Apple.constructor()") constructor__0(this = this__1, @QName("test-library/bubble-ordering.type Apple.constructor().(this)") @impliedThis(Apple) this__1: Apple) {
            |          return;
            |        }
            |      }
            |      @QName("test-library/bubble-ordering.fuji") let fuji: Apple;
            |      module init {
            |        fuji = /*new*/ Apple().maybe();
            |      }
            |      @QName("test-library/bubble-ordering.gala") let gala: Apple;
            |      module init {
            |        gala = /*new*/ Apple().maybe();
            |      }
            |      @QName("test-library/bubble-ordering.juggle()") let juggle(@QName("test-library/bubble-ordering.juggle().(some)") some__0: Int32): Apple {
            |        @QName("test-library/bubble-ordering.juggle().return") let return__0: Apple;
            |        if (nym`<=#24`(some__0, 0)) {
            |          return__0 = fuji;
            |        } else {
            |          return__0 = gala;
            |        }
            |        return return__0;
            |      }
            |
            |      ```
            |    },
            |    "bubble-ordering.tmpl.map": "__DO_NOT_CARE__",
            |  },
            |}
        """.trimMargin(),
    )

    @Test
    fun classOrderingIf() = classOrdering(
        bubbleStrategy = BubbleBranchStrategy.IfHandlerScopeVar,
        want = """
            |{
            |  tmpl: {
            |    "bubble-ordering.tmpl": {
            |      content:
            |      ```
            |      //// work//bubble-ordering/ => bubble-ordering.tmpl
            |      let nym`<=#24` = builtins.nym`<=` /* (Int32?, Int32?) -> Boolean */;
            |      @fail var fail#0: Boolean;
            |      @fail var fail#1: Boolean;
            |      @QName("test-library/bubble-ordering.type Apple") class Apple / Apple {
            |        @QName("test-library/bubble-ordering.type Apple.maybe()") let maybe__0(this = this__0, @QName("test-library/bubble-ordering.type Apple.maybe().(this)") @impliedThis(Apple) this__0: Apple): Apple | Bubble {
            |          return /* this */ this__0;
            |        }
            |        @QName("test-library/bubble-ordering.type Apple.constructor()") constructor__0(this = this__1, @QName("test-library/bubble-ordering.type Apple.constructor().(this)") @impliedThis(Apple) this__1: Apple) {
            |          return;
            |        }
            |      }
            |      @QName("test-library/bubble-ordering.fuji") let fuji: Apple;
            |      module init {
            |        fuji = hs (fail#0, /*new*/ Apple().maybe());
            |        if (fail#0) {
            |          abortLoad;
            |        }
            |      }
            |      @QName("test-library/bubble-ordering.gala") let gala: Apple;
            |      module init {
            |        gala = hs (fail#1, /*new*/ Apple().maybe());
            |        if (fail#1) {
            |          abortLoad;
            |        }
            |      }
            |      @QName("test-library/bubble-ordering.juggle()") let juggle(@QName("test-library/bubble-ordering.juggle().(some)") some__0: Int32): Apple {
            |        @QName("test-library/bubble-ordering.juggle().return") let return__0: Apple;
            |        if (nym`<=#24`(some__0, 0)) {
            |          return__0 = fuji;
            |        } else {
            |          return__0 = gala;
            |        }
            |        return return__0;
            |      }
            |
            |      ```
            |    },
            |    "bubble-ordering.tmpl.map": "__DO_NOT_CARE__",
            |  },
            |}
        """.trimMargin(),
    )

    @Test
    fun alwaysBubbling() {
        assertGeneratedCode(
            inputJsonPathToContent = """
                |{
                |  foo: {
                |    "foo.temper": ```
                |      export let f(): Never<Void> throws Bubble { bubble() }
                |      ```
                |  }
                |}
            """.trimMargin(),
            want = """
                |{
                |  "tmpl": {
                |    "foo.tmpl": {
                |      content: ```
                |        //// work//foo/ => foo.tmpl
                |        @QName("test-library/foo.f()") let f(): Void | Bubble {
                |          throw;
                |        }
                |
                |        ```
                |    },
                |    "foo.tmpl.map": "__DO_NOT_CARE__",
                |  }
                |}
            """.trimMargin(),
            supportNetwork = defaultTestSupportNetwork.copy(
                bubbleStrategy = BubbleBranchStrategy.CatchBubble,
            ),
        )
    }

    @Test
    fun bubbleInWhileTrueLoopWithExn() = assertGeneratedCode(
        inputJsonPathToContent = """
            |{
            |  foo: {
            |    "foo.temper": ```
            |      export let hi(): Int throws Bubble {
            |        while (true) {
            |          return bubble()
            |        }
            |      }
            |      ```
            |  }
            |}
        """.trimMargin(),
        want = """
            |{
            |  "tmpl": {
            |    "foo.tmpl": {
            |      content: ```
            |        //// work//foo/ => foo.tmpl
            |        @QName("test-library/foo.hi()") let hi(): Int32 | Bubble {
            |          return failure;
            |        }
            |
            |        ```
            |    },
            |    "foo.tmpl.map": "__DO_NOT_CARE__",
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun coroutineConversionOfAwait() = assertGeneratedCode(
        supportNetwork = defaultTestSupportNetwork.copy(
            coroutineStrategy = CoroutineStrategy.TranslateToRegularFunction,
        ),
        inputJsonPathToContent = $$"""
            |{
            |  foo: {
            |    "foo.temper":
            |      ```
            |      let p = new PromiseBuilder<String>();
            |
            |      let f(): Promise<String> { p.promise }
            |
            |      async { (): GeneratorResult<Empty> extends GeneratorFn =>
            |        console.log("Before");
            |        let x = await f() orelse panic();
            |        console.log("After ${x}");
            |      }
            |
            |      p.complete("Hi");
            |      ```
            |  }
            |}
        """.trimMargin(),
        want = """
            |{
            |  tmpl: {
            |    "foo.tmpl": {
            |      content:
            |        ```
            |        //// work//foo/ => foo.tmpl
            |        let GetConsole#0 = builtins.GetConsole;
            |        let PromiseBuilderConstructor#0 = builtins.PromiseBuilderConstructor;
            |        let PromiseBuilderGetPromise#0 = builtins.PromiseBuilderGetPromise;
            |        let ConsoleLog#0 = builtins.ConsoleLog;
            |        let awakeUpon#0 = builtins.awakeUpon /* <awakeUponY extends AnyValue>(Promise<awakeUponY>, Generator<awakeUponY>) -> Void */;
            |        let Empty#0 = builtins.Empty;
            |        let ValueResultConstructor#0 = builtins.ValueResultConstructor;
            |        let getPromiseResultSync#0 = builtins.getPromiseResultSync /* <getPromiseResultSyncY extends AnyValue>(Boolean, Promise<getPromiseResultSyncY>) -> getPromiseResultSyncY */;
            |        let panic#0 = builtins.panic;
            |        let cat#0 = builtins.cat /* (...String) -> String */;
            |        let DoneResult#0 = builtins.DoneResult;
            |        let adaptGeneratorFnSafe#0 = builtins.adaptGeneratorFnSafe /* <adaptGeneratorFnSafeYIELD extends AnyValue>(Fn__0<GeneratorResult<adaptGeneratorFnSafeYIELD>>) -> SafeGenerator<adaptGeneratorFnSafeYIELD> */;
            |        let async#0 = builtins.async /* (Fn__0<SafeGenerator<Empty>>) -> Void */;
            |        let PromiseBuilderComplete#0 = builtins.PromiseBuilderComplete;
            |        let console#0: Console = GetConsole#0();
            |        @QName("test-library/foo.p") let p__0: PromiseBuilder<String> = PromiseBuilderConstructor#0();
            |        @QName("test-library/foo.f()") let f__0(): Promise<String> {
            |          return PromiseBuilderGetPromise#0(p__0);
            |        }
            |        let fn__0(): SafeGenerator<Empty> {
            |          var caseIndex#0: Int32 = 0;
            |          var t#0: String = "";
            |          @fail var fail#0: Boolean = false;
            |          @QName("test-library/foo.x=") var x__0: String = "";
            |          var promise#0: Promise<String> | Null = null;
            |          let convertedCoroutine#0(generator#0: SafeGenerator<Empty>): GeneratorResult<Empty> {
            |            while (true) {
            |              let caseIndexLocal#0: Int32 = caseIndex#0;
            |              caseIndex#0 = -1;
            |              when (caseIndexLocal#0) {
            |                0 -> do {
            |                  ConsoleLog#0(console#0, "Before");
            |                  promise#0 = f__0();
            |                  caseIndex#0 = 1;
            |                  awakeUpon#0(notNull (promise#0), generator#0);
            |                  return ValueResultConstructor#0(Empty#0());
            |                }
            |                1 -> do {
            |                  t#0 = hs (fail#0, getPromiseResultSync#0(null, notNull (promise#0)));
            |                  if (fail#0) {
            |                    caseIndex#0 = 2;
            |                  } else {
            |                    caseIndex#0 = 3;
            |                  }
            |                }
            |                2 -> do {
            |                  x__0 = panic#0();
            |                  caseIndex#0 = 4;
            |                }
            |                3 -> do {
            |                  x__0 = t#0;
            |                  caseIndex#0 = 4;
            |                }
            |                4 -> do {
            |                  ConsoleLog#0(console#0, cat#0("After ", x__0));
            |                  return DoneResult#0();
            |                }
            |                else -> do {
            |                  return DoneResult#0();
            |                }
            |              }
            |            }
            |          }
            |          return adaptGeneratorFnSafe#0(convertedCoroutine#0);
            |        }
            |        module init {
            |          async#0(fn__0);
            |          PromiseBuilderComplete#0(p__0, "Hi");
            |        }
            |
            |        ```
            |    },
            |    "foo.tmpl.map": "__DO_NOT_CARE__",
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun connectedStatic() = assertGeneratedCode(
        inputJsonPathToContent = """
            |{
            |  foo: {
            |    "foo.temper": ```
            |      export let s = String.begin;
            |      ```
            |  }
            |}
        """.trimMargin(),
        want = """
            |{
            |  "tmpl": {
            |    "foo.tmpl": {
            |      content: ```
            |        //// work//foo/ => foo.tmpl
            |        let StringBegin#0 = builtins.StringBegin;
            |        @QName("test-library/foo.s") let s: StringIndex = StringBegin#0;
            |
            |        ```
            |    },
            |    "foo.tmpl.map": "__DO_NOT_CARE__",
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun modWithImpliedLabeledBlock() = assertGeneratedCode(
        inputJsonPathToContent = """
            |{
            |  foo: {
            |    "foo.temper": ```
            |      let mod(n: Int, d: Int): Int throws Bubble {
            |        if (!(d == 0)) {
            |          return n % d;
            |        }
            |        bubble();
            |      }
            |      ```
            |  }
            |}
        """.trimMargin(),
        want = """
            |{
            |  "tmpl": {
            |    "foo.tmpl": {
            |      content: ```
            |        //// work//foo/ => foo.tmpl
            |        let nym`==#7` = builtins.nym`==` /* (Int32?, Int32?) -> Boolean */;
            |        let nym`%#8` = builtins.nym`%` /* (Int32, Int32) -> Result<Int32, Bubble> */;
            |        @QName("test-library/foo.mod()") @reach(\none) let mod__0(@QName("test-library/foo.mod().(n)") n__0: Int32, @QName("test-library/foo.mod().(d)") d__0: Int32): Int32 | Bubble {
            |          @QName("test-library/foo.mod().return") let return__0: Int32;
            |          fn__0: {
            |            @fail var fail#0: Boolean;
            |            if (!nym`==#7`(d__0, 0)) {
            |              return__0 = hs (fail#0, nym`%#8`(n__0, d__0));
            |              if (fail#0) {
            |                return failure;
            |              }
            |              break fn__0;
            |            }
            |            return failure;
            |          }
            |          return return__0;
            |        }
            |
            |        ```
            |    },
            |    "foo.tmpl.map": "__DO_NOT_CARE__",
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun defaultStringIndexOptionRtti() = assertGeneratedCode(
        inputJsonPathToContent = """
            |{
            |  foo: {
            |    "foo.temper": ```
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
        want = """
            |{
            |  "tmpl": {
            |    "foo.tmpl": {
            |      content: ```
            |        //// work//foo/ => foo.tmpl
            |        let StringBegin#0 = builtins.StringBegin;
            |        @QName("test-library/foo.f()") let f(@QName("test-library/foo.f().(i)") i__0: StringIndexOption): StringIndex {
            |          @QName("test-library/foo.f().return") let return__0: StringIndex;
            |          if (i__0 >= 0) {
            |            return__0 = cast (i__0, StringIndex);
            |          } else {
            |            return__0 = StringBegin#0;
            |          }
            |          return return__0;
            |        }
            |
            |        ```
            |    },
            |    "foo.tmpl.map": "__DO_NOT_CARE__",
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun fnCallWithExplicitTypeActuals() = assertGeneratedCode(
        inputJsonPathToContent = """
            |{
            |  foo: {
            |    "foo.temper": ```
            |      let makeAList<T>(): List<T> { [] }
            |
            |      export let noStrings = makeAList<String>();
            |      ```
            |  }
            |}
        """.trimMargin(),
        want = """
            |{
            |  "tmpl": {
            |    "foo.tmpl": {
            |      content: ```
            |        //// work//foo/ => foo.tmpl
            |        let list#0 = builtins.list /* <listT extends AnyValue>(...listT) -> List<listT> */;
            |        @QName("test-library/foo.makeAList()") let makeAList__0<T__0 extends AnyValue>(): List<T__0> {
            |          return list#0();
            |        }
            |        @QName("test-library/foo.noStrings") let noStrings: List<String> = makeAList__0<String>();
            |
            |        ```
            |    },
            |    "foo.tmpl.map": "__DO_NOT_CARE__",
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun fancyCallback() = assertGeneratedCode(
        inputJsonPathToContent = """
            |{
            |  foo: {
            |    "foo.temper": ```
            |      export let something(i: Int, map: Map<Int, String>): List<Int> {
            |        map.toListWith { (key, value): Int => key + i }
            |      }
            |      ```
            |  }
            |}
        """.trimMargin(),
        want = """
            |{
            |  "tmpl": {
            |    "foo.tmpl": {
            |      content: ```
            |        //// work//foo/ => foo.tmpl
            |        let nym`+#13` = builtins.nym`+` /* (Int32, Int32) -> Int32 */;
            |        let MappedToListWith#0 = builtins.MappedToListWith;
            |        @QName("test-library/foo.something()") let something(@QName("test-library/foo.something().(i)") i__0: Int32, @QName("test-library/foo.something().(map)") map__0: Map<Int32, String>): List<Int32> {
            |          let fn__0(@QName("test-library/foo.something().(key)") key__0: Int32, @QName("test-library/foo.something().(value)") value__0: String): Int32 {
            |            return nym`+#13`(key__0, i__0);
            |          }
            |          return MappedToListWith#0(map__0, fn__0);
            |        }
            |
            |        ```
            |    },
            |    "foo.tmpl.map": "__DO_NOT_CARE__",
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun disconnectedMethod() = assertGeneratedCode(
        customizeModule = { module, isNew ->
            if (isNew) {
                module.addEnvironmentBindings(connectedDecoratorBindings)
            }
        },
        inputJsonPathToContent = $$"""
            |{
            |  foo: {
            |    "foo.temper": ```
            |      @connected("C")
            |      class C {
            |        @connected("C::constructor")
            |        public constructor(): Void {}
            |
            |        @connected("C::a")
            |        public a(): String { "" }
            |
            |        // not connected
            |
            |        public static b: String = "b";
            |        public static let c(b: Boolean): String {
            |          if (b) { console.log(""); }
            |          "c"
            |        }
            |        public d(): String { "${a()}d" }
            |        public get e(): String { "${a()}e" }
            |      }
            |
            |      let c = new C();
            |      console.log("b=${C.b}, c=${C.c(false)}, d=${c.d()}, e=${c.e}");
            |      ```
            |  }
            |}
        """.trimMargin(),
        want = """
            |{
            |  "tmpl": {
            |    "foo.tmpl": {
            |      content: ```
            |        //// work//foo/ => foo.tmpl
            |        let GetConsole#0 = builtins.GetConsole;
            |        let ConsoleLog#0 = builtins.ConsoleLog;
            |        let cat#0 = builtins.cat /* (...String) -> String */;
            |        let CA#0 = builtins.CA;
            |        let CConstructor#0 = builtins.CConstructor;
            |        let console#0: Console = GetConsole#0();
            |## Here we've got a class connection, but no class declaration because
            |## everything either connected or was pulled out.
            |        @QName("test-library/foo.type C") class C__0 connects C;
            |        @QName("test-library/foo.type C.b") let b__0: String = "b";
            |        @QName("test-library/foo.type C.c()") let c__0(@QName("test-library/foo.type C.c().(b)") b__1: Boolean): String {
            |          if (b__1) {
            |            ConsoleLog#0(console#0, "");
            |          }
            |          return "c";
            |        }
            |## the name is this__0 but there's no `this = ...` notation.
            |        @QName("test-library/foo.type C.d()") let d__0(@QName("test-library/foo.type C.d().(this)") @impliedThis(C__0) this__0: C): String {
            |          return cat#0(CA#0(this__0), "d");
            |        }
            |        @QName("test-library/foo.type C.get e()") let get.e__0(@QName("test-library/foo.type C.get e().(this)") @impliedThis(C__0) this__1: C): String {
            |          return cat#0(CA#0(this__1), "e");
            |        }
            |## When using the pulled-out members, we refer to them by the pulled out name.
            |        @QName("test-library/foo.c") let c__1: C = CConstructor#0();
            |        module init {
            |          ConsoleLog#0(console#0, cat#0("b=", b__0, ", c=", c__0(false), ", d=", d__0(c__1), ", e=", get.e__0(c__1)));
            |        }
            |
            |        ```
            |    },
            |    "foo.tmpl.map": "__DO_NOT_CARE__",
            |  }
            |}
        """.trimMargin().stripDoubleHashCommentLinesToPutCommentsInlineBelow(),
    )

    @Test
    fun multipleUsersOfSameConnected() = assertGeneratedCode(
        inputJsonPathToContent = """
            |{
            |  // Both of these use String::get
            |  foo: {
            |    "foo.temper": ```
            |      export let f(s: String): Int { s[String.begin] orelse 0 }
            |      ```,
            |  },
            |  bar: {
            |    "bar.temper": ```
            |      export let g(i: StringIndex): Int { "Hello, World!"[i] orelse 0 }
            |      ```
            |  }
            |}
        """.trimMargin(),
        want = """
            |{
            |  "tmpl": {
            |    "foo.tmpl": {
            |        "content":
            |        ```
            |        //// work//foo/ => foo.tmpl
            |        let StringBegin#0 = builtins.StringBegin;
            |        let StringGet#0 = builtins.StringGet;
            |        @QName("test-library/foo.f()") let f(@QName("test-library/foo.f().(s)") s__0: String): Int32 {
            |          return StringGet#0(s__0, StringBegin#0);
            |        }
            |
            |        ```
            |    },
            |    "foo.tmpl.map": "__DO_NOT_CARE__",
            |    "bar.tmpl": {
            |        "content":
            |        ```
            |        //// work//bar/ => bar.tmpl
            |        let StringGet#1 = builtins.StringGet;
            |        @QName("test-library/bar.g()") let g(@QName("test-library/bar.g().(i)") i__0: StringIndex): Int32 {
            |          return StringGet#1("Hello, World!", i__0);
            |        }
            |
            |        ```
            |    },
            |    "bar.tmpl.map": "__DO_NOT_CARE__"
            |  }
            |}
        """.trimMargin().stripDoubleHashCommentLinesToPutCommentsInlineBelow(),
    )

    @Test
    fun insertingBoxingAndUnboxingCallsBasedOnImplicitCastingChecks() = assertGeneratedCode(
        inputJsonPathToContent = """
            |{
            |  foo: {
            |    "foo.temper": ```
            |      // The TestSupportNetwork treats type names ending in "Boxes" specially.
            |      export class StringBoxes(public s: String) {}
            |
            |      export class Identity<T> {
            |        public identity(x: T): T { x }
            |      }
            |
            |      let logStringOrNot(s: StringBoxes): Void {
            |        // Here's where boxing and boxing would happen:
            |        // on transitions between a *Boxes actual type and a different declared type.
            |        let t = new Identity<StringBoxes>().identity(s);
            |        console.log(t.s);
            |      }
            |
            |      logStringOrNot(new StringBoxes("Hello, World!"));
            |    ```
            |  }
            |}
        """.trimMargin(),
        want = """
            |{
            |  tmpl: {
            |    "foo.tmpl": {
            |      content: ```
            |        //// work//foo/ => foo.tmpl
            |        let GetConsole#0 = builtins.GetConsole;
            |        let box#0 = builtins.box;
            |        let unbox#0 = builtins.unbox;
            |        let ConsoleLog#0 = builtins.ConsoleLog;
            |        let console#0: Console = GetConsole#0();
            |        @QName("test-library/foo.type StringBoxes") class StringBoxes / StringBoxes {
            |          @QName("test-library/foo.type StringBoxes.s") @constructorProperty let s__0: String;
            |          @QName("test-library/foo.type StringBoxes.constructor()") constructor__0(this = this__0, @QName("test-library/foo.type StringBoxes.constructor().(this)") @impliedThis(StringBoxes) this__0: StringBoxes, @QName("test-library/foo.type StringBoxes.constructor().(s)") s__1: String) {
            |            /* this */ this__0.s__0 = s__1;
            |            return;
            |          }
            |          get.s -> gets__0(this = this__1, @impliedThis(StringBoxes) this__1: StringBoxes): String {
            |            return /* this */ this__1.s__0;
            |          }
            |        }
            |        @QName("test-library/foo.type Identity") class Identity / Identity<T__0 extends AnyValue> {
            |          @QName("test-library/foo.type Identity.identity()") let identity__0(this = this__2, @QName("test-library/foo.type Identity.identity().(this)") @impliedThis(Identity<T__0>) this__2: Identity<T__0>, @QName("test-library/foo.type Identity.identity().(x)") x__0: T__0): T__0 {
            |            return x__0;
            |          }
            |          @QName("test-library/foo.type Identity.constructor()") constructor__1(this = this__3, @QName("test-library/foo.type Identity.constructor().(this)") @impliedThis(Identity<T__0>) this__3: Identity<T__0>) {
            |            return;
            |          }
            |        }
            |        @QName("test-library/foo.logStringOrNot()") let logStringOrNot__0(@QName("test-library/foo.logStringOrNot().(s)") s__2: StringBoxes): Void {
            |## Here are the injected box and unbox calls.
            |          @QName("test-library/foo.logStringOrNot().t=") let t__0: StringBoxes = unbox#0(/*new*/ Identity().identity(box#0(s__2)));
            |          var t#0: String = t__0.s;
            |          ConsoleLog#0(console#0, t#0);
            |          return;
            |        }
            |        module init {
            |          logStringOrNot__0(/*new*/ StringBoxes("Hello, World!"));
            |        }
            |
            |        ```
            |    },
            |    "foo.tmpl.map": "__DO_NOT_CARE__"
            |  }
            |}
        """.trimMargin().stripDoubleHashCommentLinesToPutCommentsInlineBelow(),
    )

    @Test
    fun typeAliasesAreCompilerFictions() = assertGeneratedCode(
        inputJsonPathToContent = """
            |{
            |  foo: {
            |    "foo.temper": ```
            |      export let Str = String;
            |      export let s: Str = "Helo whirled!";
            |      ```,
            |  },
            |  bar: {
            |    "bar.temper": ```
            |      let { Str } = import("../foo");
            |      export let t: Str = "Hallow eld!";
            |      ```,
            |  }
            |}
        """.trimMargin(),
        want = """
            |{
            |  tmpl: {
            |    "foo.tmpl": {
            |      content: ```
            |        //// work//foo/ => foo.tmpl
            |        @QName("test-library/foo.s") let s: String = "Helo whirled!";
            |
            |        ```
            |    },
            |    "bar.tmpl": {
            |      content: ```
            |        //// work//bar/ => bar.tmpl
            |        @QName("test-library/bar.t") let t: String = "Hallow eld!";
            |
            |        ```
            |    },
            |    "foo.tmpl.map": "__DO_NOT_CARE__",
            |    "bar.tmpl.map": "__DO_NOT_CARE__",
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
            |  tmpl: {
            |    foo.tmpl: {
            |      content: ```
            |        //// work//foo/ => foo.tmpl
            |        @fail var fail#0: Boolean;
            |        @QName("test-library/foo.m") var m__0: Int32 = 0;
            |        @QName("test-library/foo.f()") let f__0(): Int32 | Null | Bubble {
            |          m__0 = 5;
            |          return m__0;
            |        }
            |        @QName("test-library/foo.n") let n: Int32 | Null;
            |        module init {
            |          n = hs (fail#0, f__0());
            |          if (fail#0) {
            |            abortLoad;
            |          }
            |        }
            |
            |        ```
            |    },
            |    foo.tmpl.map: "__DO_NOT_CARE__",
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun superTypesOrderedBeforeSubTypes() = assertGeneratedCode(
        inputFileMapFromJson(
            """
                |{
                |  foo: {
                |    "foo.temper":
                |      ```
                |      export class C extends I {
                |        public f(): Int { 42 }
                |      }
                |
                |      export interface I {
                |        public f(): Int;
                |      }
                |      ```
                |  }
                |}
            """.trimMargin(),
        ),
        want = """
            |{
            |  tmpl: {
            |    "foo.tmpl": {
            |      content:
            |        ```
            |        //// work//foo/ => foo.tmpl
            |        require temper-core;
            |        let InterfaceTypeSupport#0 = InterfaceTypeSupport;
            |        let pureVirtual#0 = builtins.pureVirtual;
            |        @QName("test-library/foo.type I") interface I / I {
            |          @QName("test-library/foo.type I.f()") let f__0(this = this__0, @QName("test-library/foo.type I.f().(this)") @impliedThis(I) this__0: I): Int32 {
            |            pureVirtual#0();
            |          }
            |        }
            |        @QName("test-library/foo.type C") class C / C extends I {
            |          @QName("test-library/foo.type C.f()") let f__1(this = this__1, @QName("test-library/foo.type C.f().(this)") @impliedThis(C) this__1: C): Int32 {
            |            return 42;
            |          }
            |          @QName("test-library/foo.type C.constructor()") constructor__0(this = this__2, @QName("test-library/foo.type C.constructor().(this)") @impliedThis(C) this__2: C) {
            |            return;
            |          }
            |        }
            |
            |        ```
            |    },
            |    "foo.tmpl.map": "__DO_NOT_CARE__",
            |  }
            |}
        """.trimMargin(),
    )

    @Ignore // Not yet implemented
    @Test
    fun staticInitializersInitializedAfterTypeDeclared() = assertGeneratedCode(
        inputFileMapFromJson(
            """
                |{
                |  "foo.temper":
                |    ```
                |    export class C extends I {
                |      public f(): Int { 42 }
                |    }
                |
                |    export interface I {
                |      public f(): Int;
                |      public static let instance: I = new C();
                |    }
                |    ```
                |}
            """.trimMargin(),
        ),
        want = """
            |{
            |  tmpl: {
            |   test_library.tmpl: {
            |     content:
            |     ```
            |     TODO
            |     ```,
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
            |  tmpl: {
            |    foo.tmpl: {
            |      content:
            |        ```
            |        //// work//foo/ => foo.tmpl
            |        let StringIndexOptionCompareToLt#15 = builtins.StringIndexOptionCompareToLt;
            |        let StringIndexOptionCompareToGe#16 = builtins.StringIndexOptionCompareToGe;
            |        let StringIndexOptionCompareToLe#17 = builtins.StringIndexOptionCompareToLe;
            |        let StringIndexOptionCompareToGt#18 = builtins.StringIndexOptionCompareToGt;
            |        let StringIndexNone#0 = builtins.StringIndexNone;
            |        @QName("test-library/foo.f1()") let f1(@QName("test-library/foo.f1().(a)") a__0: StringIndexOption, @QName("test-library/foo.f1().(b)") b__0: StringIndexOption): Boolean {
            |          return StringIndexOptionCompareToLt#15(a__0, b__0);
            |        }
            |        @QName("test-library/foo.f2()") let f2(@QName("test-library/foo.f2().(a)") a__1: StringIndexOption, @QName("test-library/foo.f2().(b)") b__1: StringIndex): Boolean {
            |          return StringIndexOptionCompareToGe#16(a__1, b__1);
            |        }
            |        @QName("test-library/foo.f3()") let f3(@QName("test-library/foo.f3().(a)") a__2: StringIndex, @QName("test-library/foo.f3().(b)") b__2: StringIndex): Boolean {
            |          return StringIndexOptionCompareToLe#17(a__2, b__2);
            |        }
            |        @QName("test-library/foo.f4()") let f4(@QName("test-library/foo.f4().(a)") a__3: StringIndex): Boolean {
            |          return StringIndexOptionCompareToGt#18(a__3, StringIndexNone#0);
            |        }
            |
            |        ```
            |    },
            |    foo.tmpl.map: "__DO_NOT_CARE__",
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun functionValueCalledViaPropertyRef() = assertGeneratedCode(
        inputJsonPathToContent = """
            |{
            |  foo: {
            |    foo.temper:
            |        ```
            |        export class Callable<Return>(
            |          private func: fn(): Return,
            |        ) {
            |          public call(): Return {
            |            func()
            |          }
            |        }
            |        let hello(): String { "Hello" }
            |        new Callable(hello)
            |        ```
            |  }
            |}
        """.trimMargin(),
        want = """
            |{
            |  tmpl: {
            |    foo.tmpl: {
            |      content:
            |        ```
            |        //// work//foo/ => foo.tmpl
            |        @QName("test-library/foo.type Callable") class Callable / Callable<Return__0 extends AnyValue> {
            |          @QName("test-library/foo.type Callable.func") @constructorProperty let func__0: fn (): Return__0;
            |          @QName("test-library/foo.type Callable.call()") let call__0(this = this__0, @QName("test-library/foo.type Callable.call().(this)") @impliedThis(Callable<Return__0>) this__0: Callable<Return__0>): Return__0 {
            |            return (/* this */ this__0.func__0 as () -> Return__0)();
            |##                                             ↑↑
            |## Here, the `as` indicates that a conversion is happening to a () -> Return__0
            |          }
            |          @QName("test-library/foo.type Callable.constructor()") constructor__0(this = this__1, @QName("test-library/foo.type Callable.constructor().(this)") @impliedThis(Callable<Return__0>) this__1: Callable<Return__0>, @QName("test-library/foo.type Callable.constructor().(func)") func__1: fn (): Return__0) {
            |            /* this */ this__1.func__0 = func__1;
            |            return;
            |          }
            |        }
            |        @QName("test-library/foo.hello()") let hello__0(): String {
            |          return "Hello";
            |        }
            |        module init {
            |          /*new*/
            |          Callable(hello__0);
            |##                 ↑↑↑↑↑↑↑↑
            |## Here it'd be good to do the other conversion. TODO: once we have declaration skeletons that
            |## distinguish named functions from names containing function values, do that.
            |        }
            |
            |        ```
            |    },
            |    foo.tmpl.map: "__DO_NOT_CARE__",
            |  }
            |}
        """.trimMargin().stripDoubleHashCommentLinesToPutCommentsInlineBelow(),
    )

    @Test
    fun functionalInterfaceUsingFunctionTypes() = assertGeneratedCode(
        supportNetwork = TestSupportNetwork(functionTypeStrategy = FunctionTypeStrategy.ToFunctionType),
        inputs = inputFileMapFromJson(
            """
                |{
                |  foo: {
                |    foo.temper:
                |      ```
                |      export @fun interface MyFun(): Void;
                |      ```
                |  },
                |}
            """.trimMargin(),
        ),
        want = """
            |{
            |  tmpl: {
            |    foo.tmpl: {
            |      content:
            |        ```
            |        //// work//foo/ => foo.tmpl
            |        require temper-core;
            |        let InterfaceTypeSupport#0 = InterfaceTypeSupport;
            |        let pureVirtual#0 = builtins.pureVirtual;
            |
            |        ```
            |    },
            |    foo.tmpl.map: "__DO_NOT_CARE__",
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun functionalInterfaceUsingFunctionalInterfaces() = assertGeneratedCode(
        supportNetwork = TestSupportNetwork(functionTypeStrategy = FunctionTypeStrategy.ToFunctionalInterface),
        inputs = inputFileMapFromJson(
            """
                |{
                |  foo: {
                |    foo.temper:
                |      ```
                |      export @fun interface MyFun(): Void;
                |      ```
                |  },
                |}
            """.trimMargin(),
        ),
        want = """
            |{
            |  tmpl: {
            |    foo.tmpl: {
            |      content:
            |        ```
            |        //// work//foo/ => foo.tmpl
            |        require temper-core;
            |        let InterfaceTypeSupport#0 = InterfaceTypeSupport;
            |        let pureVirtual#0 = builtins.pureVirtual;
            |        @functionalInterface @QName("test-library/foo.type MyFun") interface MyFun / MyFun {
            |          @QName("test-library/foo.type MyFun.apply()") let apply__0(): Void {
            |            pureVirtual#0();
            |          }
            |        }
            |
            |        ```
            |    },
            |    foo.tmpl.map: "__DO_NOT_CARE__",
            |  }
            |}
        """.trimMargin(),
    )

    private fun assertGeneratedCode(
        inputJsonPathToContent: String,
        want: String,
        supportNetwork: TestSupportNetwork = defaultTestSupportNetwork,
        moduleNeedsResult: Boolean = false,
        customizeModule: ModuleCustomizeHook? = null,
    ) = assertGeneratedCode(
        inputs = inputFileMapFromJson(inputJsonPathToContent),
        want = want,
        supportNetwork = supportNetwork,
        moduleNeedsResult = moduleNeedsResult,
        customizeModule = customizeModule,
    )

    private fun assertGeneratedCode(
        inputs: List<Pair<FilePath, String>>,
        want: String,
        supportNetwork: TestSupportNetwork = defaultTestSupportNetwork,
        moduleNeedsResult: Boolean = false,
        customizeModule: ModuleCustomizeHook? = null,
    ): Unit = object : TestCompiler(
        inputs,
        moduleNeedsResult,
        customizeModule,
    ) {
        override fun backend(
            libraryConfigurations: LibraryConfigurations,
            modules: List<Module>,
            outDir: OutDir,
        ): Backend<*> {
            val cancelGroup = makeCancelGroupForTest()
            val buildFileCreator = outDir.systemAccess(cancelGroup)
            val keepFileCreator = NullSystemAccess(dirPath(), cancelGroup)
            return TestBackend(
                supportNetwork,
                BackendSetup(
                    libraryConfigurations.currentLibraryConfiguration.libraryName,
                    Dependencies.Builder(libraryConfigurations.toBundle()),
                    modules,
                    buildFileCreator,
                    keepFileCreator,
                    logSink,
                    NullDependencyResolver,
                    Backend.Config.bundled,
                ),
            )
        }

        override fun onBackendsComplete(outputRoot: OutDir) {
            logSink.toConsole(
                console,
                Log.Warn,
                inputs.associate {
                    it.first to (it.second to FilePositions.fromSource(it.first, it.second))
                },
            )
            // check that test completed by looking at the written content.
            assertStructure(
                want,
                logSink.wrapErrorsAround(outputRoot.fileTreeStructure()),
                postProcessor = { s ->
                    PseudoCodeNameRenumberer.newStructurePostProcessor()(s)
                },
            )
        }
    }.compile()
}

internal val defaultTestSupportNetwork = TestSupportNetwork()

private const val DELIVERED_BY_MODULE_MAKER = "//__DELIVERED_BY_MODULE_MAKER__"

/**
 * Bundles the complexity of setting up compilation.
 * Users can override the `onModulesCompiled` method to work with
 */
abstract class TestCompiler(
    private val inputs: List<Pair<FilePath, String>>,
    private val moduleNeedsResult: Boolean = false,
    customizeHook: ModuleCustomizeHook? = null,
) {
    internal abstract fun onBackendsComplete(outputRoot: OutDir)

    protected val logSink = ListBackedLogSink()
    private val projectRoot = FilePath.emptyPath.resolveDir("work")
    private val libraryName = DashedIdentifier("test-library")

    private val moduleAdvancer = ModuleAdvancer(
        projectLogSink = logSink,
        moduleConfig = ModuleConfig(
            moduleCustomizeHook = { module, isNew ->
                if (isNew && moduleNeedsResult) {
                    module.addEnvironmentBindings(
                        mapOf(StagingFlags.moduleResultNeeded to TBoolean.valueTrue),
                    )
                }
                customizeHook?.customize(module, isNew)
            },
        ),
    )

    private fun libraries(): Map<FilePath, Pair<LibraryConfiguration, List<Module>>> {
        moduleAdvancer.configureLibrary(
            libraryName = libraryName,
            libraryRoot = projectRoot,
        )

        val fs = MemoryFileSystem()
        for ((srcPath, content) in inputs) {
            fs.write(projectRoot + srcPath, content.toByteArray())
        }
        val snapshot = FilteringFileSystemSnapshot(
            fs,
            FileFilterRules.Allow,
            root = projectRoot,
        )
        partitionSourceFilesIntoModules(snapshot, moduleAdvancer, logSink, console, root = projectRoot)

        moduleAdvancer.advanceModules()
        return moduleAdvancer.getPartitionedModules()
    }

    abstract fun backend(
        libraryConfigurations: LibraryConfigurations,
        modules: List<Module>,
        outDir: OutDir,
    ): Backend<*>

    fun compile() {
        MemoryFileSystem().use { fs ->
            val libraries = libraries()
            val outputRoot = OutputRoot(fs)

            val backends = libraries.map { (_, library) ->
                val (libraryConfiguration, modules) = library
                val outputDir = outputRoot
                    .makeDir(FilePathSegment(libraryConfiguration.libraryName.text))
                    .makeDir(FilePathSegment("tmpl"))
                backend(
                    LibraryConfigurationsBundle.from(listOf(libraryConfiguration))
                        .withCurrentLibrary(libraryConfiguration),
                    modules,
                    outputDir,
                )
            }

            applyBackendsSynchronously(makeCancelGroupForTest(), backends)

            val libraryConfiguration = libraries.getValue(projectRoot).first
            onBackendsComplete(outputRoot.makeDir(FilePathSegment(libraryConfiguration.libraryName.text)))
        }
    }
}
