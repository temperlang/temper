package lang.temper.be.js

import kotlin.test.Ignore
import kotlin.test.Test

class DocJsTranslatorTest {
    @Test
    fun justAString() = assertGeneratedDocs(
        "\"foo\"",
        want = """
            |"foo";
        """.trimMargin(),
    )

    @Test
    fun stringConcat() = assertGeneratedDocs(
        """
            |export let bar: String;
            |;;;
            |"foo ${'$'}{ bar }"
        """.trimMargin(),
        want = """
            |// #region __BOILERPLATE__ {{{
            |/** @type {string} */
            |export let bar;
            |// #endregion }}}
            |`foo ${'$'}{ bar }`;
        """.trimMargin(),
    )

    @Test
    fun justABool() = assertGeneratedDocs(
        "true",
        want = "true;",
    )

    @Test
    fun explanatory() = assertGeneratedDocs(
        input = """
            |// Prints "Hello, World!"
            |console.log("Hello, World!");
        """.trimMargin(),
        want = """
            |// Prints "Hello, World!"
            |console.log("Hello, World!");
        """.trimMargin(),
    )
    // TODO(mikesamuel, doc-gen): test that comments in class/interface
    // definitions get grouped into the translated class definition

    @Test
    fun callWithArg() = assertGeneratedDocs(
        """
        |export let foo(x: String): Void { console.log(x) }
        |;;;
        |foo("bar")
        """.trimMargin(),
        want = """
        |// #region __BOILERPLATE__ {{{
        |import {
        |  globalConsole as globalConsole__0
        |} from "@temperlang/core";
        |/** @type {Console_2} */
        |const console_0 = globalConsole__0;
        |/** @param {string} x_3 */
        |export function foo(x_3) {
        |  console_0.log(x_3);
        |  return;
        |};
        |// #endregion }}}
        |foo("bar");
        """.trimMargin(),
    )

    @Test
    fun ifs() = assertGeneratedDocs(
        """
            |export let foo(): Boolean { true }
            |export let bar(): Void {}
            |export let baz(): Void {}
            |
            |;;;
            |
            |if (foo()) {
            |   bar()
            |} else {
            |   baz()
            |}
        """.trimMargin(),
        want = """
            |// #region __BOILERPLATE__ {{{
            |/** @returns {boolean} */
            |export function foo() {
            |  return true;
            |};
            |export function bar() {
            |  return;
            |};
            |export function baz() {
            |  return;
            |};
            |// #endregion }}}
            |if (foo()) {
            |  bar();
            |} else {
            |  baz();
            |}
        """.trimMargin(),
    )

    @Test
    fun whileLoop() = assertGeneratedDocs(
        """
        |export let foo(): Void {}
        |;;;
        |while(true) {foo()}
        """.trimMargin(),
        """
        |// #region __BOILERPLATE__ {{{
        |export function foo() {
        |  return;
        |};
        |// #endregion }}}
        |while (true) {
        |  foo();
        |}
        """.trimMargin(),
    )

    @Ignore
    @Test
    fun forLoop() {
        assertGeneratedDocs(
            "for (x = 0; x < 10; ++x) { f(x); }",
            """
                let x = 0;
                while (true) {
                  f(x);
                  {
                    x = x + 1;
                    x;
                  }
                  if (x < 10) {
                    continue;
                  } else {
                    break;
                  }
                }
            """.trimIndent(),
        )
    }

    @Ignore
    @Test
    fun postIncrement() {
        assertGeneratedDocs(
            "x++",
            """
                |x = x + 1;
            """.trimMargin(),
        )
    }

    @Test
    fun numericLiteral() {
        assertGeneratedDocs("1.1", "1.1;")
    }

    @Ignore // punt this and instantiateAThingThatDoesNotExist until after the naming for exported types works right
    @Test
    fun identifierIsReservedWord() {
        assertGeneratedDocs("let with = 1", "")
    }

    @Test
    fun defineAVariable() {
        assertGeneratedDocs("let a = 1;", want = "const a = 1;")
    }

    @Ignore
    @Test
    fun instantiateAThingThatDoesNotExist() {
        assertGeneratedDocs(
            input = """
                    |export class Foo {}
                    |;;;
                    |new Foo()
            """.trimMargin(),
            want = """
                    |// #region __BOILERPLATE__ {{{
                    |//============================
                    |// #endregion }}}
                    |new Foo();
            """.trimMargin(),
            ignoreFolded = true,
        )
    }

    @Ignore
    @Test
    fun constructorWithArgs() {
        assertGeneratedDocs("""new Foo("bar")""", """new Foo("bar");""")
    }

    @Ignore
    @Test
    fun instantiateGenericThing() {
        // JS doesn't do generics so not that different
        assertGeneratedDocs("new Foo<Bar>()", "new Foo();")
    }

    @Ignore
    @Test
    fun instantiateNestedGenericThing() {
        assertGeneratedDocs("new Foo<List<Bar>>()", "new Foo();")
    }

    @Test
    fun list() {
        assertGeneratedDocs("[3, 4]", "[3, 4];")
    }

    @Ignore // TODO(mikesamuel, doc-gen): pull interfacetype support into fold.
    @Test
    fun iface() {
        assertGeneratedDocs(
            input = """
                    |interface Foo<T, U> {}
            """.trimMargin(),
            want = """
                    |
            """.trimMargin(),
        )
    }

    @Ignore
    @Test
    fun interfaceGetter() {
        assertGeneratedDocsContain("""interface Foo {get place(): String}""", "place")
    }

    @Ignore
    @Test
    fun setter() {
        assertGeneratedDocsContain(
            """
            |interface C {
            |  public set place(name: String)
            |}
            """.trimMargin(),
            "place",
        )
    }

    @Ignore
    @Test
    fun classExtendingThing() {
        // T, String, U don't show up in the output due to JS not doing generics.
        // Not sure if the Bar/Baz part is good or not
        assertGeneratedDocs(
            "class Foo<T extends String, U> extends Bar, Baz{}",
            """
                class Foo {
                }
                Bar.implementedBy(Foo);
                Baz.implementedBy(Foo);
            """.trimIndent(),
        )
    }

    // TODO(mikesamuel, doc-gen): We shouldn't have trailing constructor reference
    // at the end, below: `Foo;`
    @Test
    fun genericWithMultipleConstraints() {
        // This case doesn't make much sense in JS but want to document it for the future
        assertGeneratedDocs(
            """
                |class Foo<T extends Listed<String>, Baz> {}
                |void
            """.trimMargin(),
            """
                |import {
                |  type as type__0
                |} from "@temperlang/core";
                |class Foo extends type__0() {
                |  constructor() {
                |    super ();
                |  }
                |}
                |void 0;
            """.trimMargin(),
            ignoreFolded = true,
        )
    }

    @Ignore
    @Test
    fun classMethodsEtc() {
        assertGeneratedDocs(
            """
                |class C {
                |  public get place(): String {
                |    return "Byron Bay"
                |  }
                |  protected bar: Int;
                |}
            """.trimMargin(),
            // The name for bar will change when exported names get fixed later
            """
                    class C {
                      get place() {
                        foo();
                        return "Byron Bay";
                      }
                      #bar_0;
                    }
            """.trimIndent(),
        )
    }

    @Test
    fun sample() {
        assertGeneratedDocs(
            input = """
                |// We defined variables needed but not in scope in
                |export interface DiffResult { public isEmpty: Boolean }
                |// Frontend interp fails below because of this panic.
                |export let diff(a: String, b: String): DiffResult { panic() };
                |export let stringA = "foo";
                |export let stringB = "bar";
                |
                |;;;
                |
                |let differences = diff(stringA, stringB);
                |if (differences.isEmpty) {
                |    console.log("they're the same")
                |}
            """.trimMargin(),
            want = """
                |// #region __BOILERPLATE__ {{{
                |import {
                |  type as type__0, panic as panic_5
                |} from "@temperlang/core";
                |export class DiffResult_0 extends type__0() {
                |};
                |/**
                | * @param {string} a_3
                | * @param {string} b_4
                | * @returns {DiffResult_0}
                | */
                |export function diff(a_3, b_4) {
                |  return panic_5();
                |};
                |/** @type {string} */
                |export const stringA = "foo";
                |/** @type {string} */
                |export const stringB = "bar";
                |// #endregion }}}
                |const differences = diff(stringA, stringB);
                |if (differences.isEmpty) {
                |  console.log("they're the same");
                |}
            """.trimMargin(),
        )
    }

    @Test
    fun simpleFunction() {
        assertGeneratedDocs(
            input = """
                |let a(): Int { return 1 }
            """.trimMargin(),
            want = """
                |function a() {
                |  return 1;
                |}
            """.trimMargin(),
        )
    }

    @Ignore
    @Test
    fun complexFunction() {
        assertGeneratedDocs(
            input = """
                let a(b: Int, c: Int, ...ints:List<Int>): Int {
                  if(ints.length > 0) {
                    return c + 1;
                  }
                  return b;
                }
            """.trimIndent(),
            want = """function a(b, c, ...ints) {
                |  if (ints.length > 0) {
                |    return c + 1;
                |  }
                |  return b;
                |}
            """.trimMargin(),
        )
    }

    // TODO(mikesamuel): Pull type parameter declarations into fold
    // and do not rely on weaving to pull type formals out of blocks
    // used to narrow scope of type parameters.
    @Test
    fun genericFunction() {
        // Not sure if this really works since the generic doesn't come back out
        assertGeneratedDocs(
            input = """
                |let identity<T>(it: T): T {
                |  return it;
                |}
            """.trimMargin(),
            // TODO Clean up void after return.
            want = """
                |function identity(it) {
                |  return it;
                |  void 0;
                |}
            """.trimMargin(),
        )
    }

    @Ignore
    @Test
    fun untypedFunctionArgument() {
        assertGeneratedDocs(
            input = """
                let hi(x) {
                    return x + 1
                }
            """.trimIndent(),
            want = """
                function hi(x) {
                  return x + 1;
                }
            """.trimIndent(),
        )
    }

    @Test
    fun implicitReturn() {
        assertGeneratedDocs(
            input = """
                |let hi(x: Int) {
                |  x + 1
                |}
            """.trimMargin(),
            want = """
                |function hi(x) {
                |  return x + 1 | 0;
                |}
            """.trimMargin(),
        )
    }

    // Ignored since this doesn't appear to be supported in the main compiler yet,
    // so I'm uncertain what the output should look like
    @Ignore
    @Test
    fun explicitConstructor() {
        assertGeneratedDocs("", "")
    }

/*
    // Ternary if is not currently supported since there isn't enough context to differentiate between this and a
    // statement if
    @Test
    fun ternaryIf(){}


    // TODO other builtins?
 */
}
