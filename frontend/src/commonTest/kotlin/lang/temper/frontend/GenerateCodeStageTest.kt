@file:Suppress("MaxLineLength")

package lang.temper.frontend

import lang.temper.common.Log
import lang.temper.common.stripDoubleHashCommentLinesToPutCommentsInlineBelow
import lang.temper.common.testCodeLocation
import lang.temper.env.InterpMode
import lang.temper.interp.MetadataDecorator
import lang.temper.interp.connectedDecoratorName
import lang.temper.interp.vConnectedDecorator
import lang.temper.lexer.MarkdownLanguageConfig
import lang.temper.lexer.StandaloneLanguageConfig
import lang.temper.log.MessageTemplate
import lang.temper.name.BuiltinName
import lang.temper.name.Symbol
import lang.temper.name.TemperName
import lang.temper.stage.Stage
import lang.temper.type.WellKnownTypes
import lang.temper.type2.Signature2
import lang.temper.value.ActualValues
import lang.temper.value.BuiltinStatelessCallableValue
import lang.temper.value.FunctionSpecies
import lang.temper.value.InterpreterCallback
import lang.temper.value.NamedBuiltinFun
import lang.temper.value.PseudoCodeDetail
import lang.temper.value.TInt
import lang.temper.value.Value
import lang.temper.value.void
import kotlin.test.Ignore
import kotlin.test.Test

class GenerateCodeStageTest {
    @Test
    fun simpleDoNothingLoop() = assertModuleAtStage(
        stage = Stage.GenerateCode,
        input = """
        // This example is interesting because the infer result pass actually adds two assignments
        // to gather results from terminal expression.
        //
        // This may be a bug, but in the meantime, it leads to a nested assignment of temporaries:
        // `t#0 = t#1 = hs(fail#2, i < 3)`
        //
        // The generate code stage needs to unnest this assignment before the TmpL backend can
        // translate it.  If the TmpL backend were to try to handle this by creating temporaries,
        // those would miss type information.
        var i = 0;
        while (i < 3) { i += 1; }
        """.trimIndent(),
        want = """
        {
            "type": {
                "body":
                ```
                var i__0;
                i__0 = 0;
                while (i__0 < 3) {
                  i__0 = i__0 + 1
                }

                ```
            },
            "generateCode": {
                "body":
                ```
                var i__0;
                i__0 = 0;
                while (i__0 < 3) {
                  i__0 = i__0 + 1
                }

                ```
            }
        }
        """,
    )

    @Test
    fun assignmentsToTypedReturnAreChecked() = assertModuleAtStage(
        stage = Stage.GenerateCode,
        input = """
        fn f(x): Int { x }
        """.trimIndent(),
        moduleResultNeeded = true,
        want = """
        {
          generateCode: {
            body: ```
            let return__4, @fn f__0;
            f__0 = (@stay fn f(x__0 /* aka x */) /* return__1 */: Int32 {
                return__1 = x__0
            });
            return__4 = (fn f)

            ```,
          },
          errors: [
            "Cannot assign to Int32 from AnyValue!",
            "Expected subtype of Int32, but got AnyValue!"
          ]
        }
        """,
    )

    @Test
    fun docCommentInData() = assertModuleAtStage(
        stage = Stage.GenerateCode,
        languageConfig = MarkdownLanguageConfig(),
        input = """
            |    /** Is this a doc comment? */
            |    export let hi = List.of<Int>(
            |      1,
            |
            |Here is some text, don't you know.
            |
            |      2,
            |      /** How about this? */
            |      3,
            |    );
            |    export let f(/** docs */ a: Int): Int { g(/** here too? */ 1) }
            |    let g(b: Int): Int { b }
        """.trimMargin(),
        want = """
            |{
            |  generateCode: {
            |    body: ```
            |      @fn let `test//`.f, @fn @reach(\none) g__0, `test//`.hi;
            |      `test//`.hi = list<Int32>(1, 2, 3);
            |      g__0 = (@stay fn g(b__0 /* aka b */: Int32) /* return__0 */: Int32 {
            |          return__0 = b__0
            |      });
            |      `test//`.f = (@stay fn f(a__0 /* aka a */: Int32) /* return__1 */: Int32 {
            |          return__1 = 1
            |      })
            |
            |      ```,
            |    exports: {
            |      f: "fn f: Function",
            |      hi: null,
            |    },
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun doWhileContinuesToFalseCondition() = assertModuleAtStage(
        input = """
            |do {
            |  console.log("Done once");
            |  continue;
            |  console.log("Not done");
            |} while (false);
        """.trimMargin(),
        stage = Stage.Run,
        moduleResultNeeded = true,
        want = """
            |{
            |  run: "void: Void",
            |  generateCode: {
            |    body: ```
            |      let return__0;
            |      do_bind_log(getConsole())("Done once");
            |      return__0 = void
            |
            |      ```
            |  },
            |  stdout: ```
            |    Done once
            |
            |    ```
            |}
        """.trimMargin(),
    )

    @Test
    fun exportedNames() = assertModuleAtStage(
        stage = Stage.Run,
        input = "export let answer = 42; answer",
        moduleResultNeeded = true,
        want = """{
          run: "42: Int32",
          generateCode: {
              body: ```
                  let return__0, `test//`.answer;
                  `test//`.answer = 42;
                  return__0 = 42

                  ```,
              exports: {
                  answer: "42: Int32",
              }
          },
          export: {
              body: ```
                  let return__0, `test//`.answer;
                  `test//`.answer = 42;
                  return__0 = 42

                  ```,
              exports: {
                  answer: "42: Int32",
              }
          },
        }
        """,
    )

    @Suppress("SpellCheckingInspection") // getprop/setprop
    @Test
    fun getterSettersFinal() = assertModuleAtStage(
        stage = Stage.GenerateCode,
        input = """
        |class C(public var prop: Int) {}
        """.trimMargin(),
        moduleResultNeeded = true,
        pseudoCodeDetail = PseudoCodeDetail.default.copy(showTypeMemberMetadata = true),
        want = """
        |{
        |  generateCode: {
        |    body:
        |      ```
        |      let return__0;
        |      @constructorProperty @property(\prop) @visibility(\public) @stay @fromType(C__0) var prop__0: Int32;
        |      @fn @method(\constructor) @visibility(\public) @stay @fromType(C__0) let constructor__0;
        |      constructor__0 = (@stay fn constructor(@impliedThis(C__0) this__0: C__0, prop__1 /* aka prop */: Int32) /* return__1 */: Void {
        |          setp(prop__0, this__0, prop__1);
        |          return__1 = void
        |      });
        |      @getter @method(\prop) @fn @visibility(\public) @stay @fromType(C__0) let getprop__0;
        |      getprop__0 = (@stay fn (@impliedThis(C__0) this__1: C__0) /* return__2 */: Int32 {
        |          return__2 = getp(prop__0, this__1)
        |      });
        |      @setter @method(\prop) @fn @visibility(\public) @stay @fromType(C__0) let setprop__0;
        |      setprop__0 = (@stay fn (@impliedThis(C__0) this__2: C__0, newProp__0: Int32) /* return__3 */: Void {
        |          setp(prop__0, this__2, newProp__0);
        |          return__3 = void
        |      });
        |      @typeDecl(C__0) @stay let C__0;
        |      C__0 = type (C__0);
        |      return__0 = type (C__0)
        |
        |      ```
        |  }
        |}
        """.trimMargin(),
    )

    @Suppress("SpellCheckingInspection") // getprop/setprop
    @Test
    fun getterSettersVarOrNot() = assertModuleAtStage(
        stage = Stage.GenerateCode,
        input = """
        |export interface I {
        |  public get superGetter(): Int { 10 }
        |  public set superSetter(i: Int): Void { }
        |}
        |export class C extends I {
        |  public propNotVar: Int;
        |  public var propVar: Int;
        |  public constructor() {
        |    // These are both legal.
        |    propNotVar = 1;
        |    propVar = 2;
        |    // Seeing if explicity `this` is different.
        |    this.propVar = 3;
        |    // Check an entirely missing property, which needs explicit this.
        |    this.wrong = 4;
        |    // Go both ways on good and bad here. Even in constructor, wrong way should fail.
        |    // Some of the errors are confusing, but at least we get errors.
        |    extraGetter = extraSetter;
        |    extraSetter = extraGetter;
        |    extraSetter = superGetter;
        |    superGetter = propNotVar;
        |    // Check setter defined only in supertype.
        |    superSetter = propNotVar;
        |  }
        |  public update(i: Int): Void {
        |    // Update of propNotVar illegal.
        |    propNotVar = i;
        |    // Assign bad type.
        |    propVar = "hi";
        |  }
        |  public set extraSetter(k: Int): Void {
        |    propVar = k;
        |  }
        |  public get extraGetter(): Int {
        |    propVar
        |  }
        |}
        |export let alsoUpdate(c: C, j: Int): Void {
        |  // Again, update of propNotVar illegal.
        |  c.propNotVar = j;
        |  c.propVar = j;
        |  c.propVar = "bye";
        |  // c.propVar += j; // <-- Generates bad tree code!
        |  // Check more good and bad.
        |  c.extraSetter = j;
        |  c.extraGetter = j;
        |  c.extraWrong = j;
        |  // From outside, check setter defined only in supertype.
        |  c.superSetter = j;
        |}
        """.trimMargin(),
        want = """
        |{
        |  generateCode: {
        |    body:
        |      ```
        |      @typeDecl(I) @stay let `test//`.I;
        |      `test//`.I = type (I);
        |      @typeDecl(C) @stay let `test//`.C;
        |      `test//`.C = type (C);
        |      @fn let `test//`.alsoUpdate;
        |      @visibility(\public) @stay @fromType(I) let superGetter__0;
        |      @visibility(\public) @fn @stay @fromType(I) let nym`get.superGetter__1`;
        |      nym`get.superGetter__1` = (@stay fn nym`get.superGetter`(@impliedThis(I) this__0: I) /* return__0 */: Int32 {
        |          return__0 = 10
        |      });
        |      @visibility(\public) @stay @fromType(I) let superSetter__0;
        |      @visibility(\public) @fn @stay @fromType(I) let nym`set.superSetter__1`;
        |      nym`set.superSetter__1` = (@stay fn nym`set.superSetter`(@impliedThis(I) this__1: I, i__0 /* aka i */: Int32) /* return__1 */: Void {
        |          return__1 = void
        |      });
        |      @visibility(\public) @stay @fromType(C) let propNotVar__0: Int32;
        |      @visibility(\public) @stay @fromType(C) var propVar__0: Int32;
        |      @visibility(\public) @fn @stay @fromType(C) let constructor__0;
        |      constructor__0 = (@stay fn constructor(@impliedThis(C) this__2: C) /* return__2 */: Void {
        |          var t#0, t#1, t#2, t#3, t#4;
        |          setp(propNotVar__0, this__2, 1);
        |          setp(propVar__0, this__2, 2);
        |          setp(propVar__0, this__2, 3);
        |          do_iset_wrong(type (C), this__2, 4);
        |          t#0 = do_iget_extraSetter(type (C), this__2);
        |          do_iset_extraGetter(type (C), this__2, t#0);
        |          t#1 = do_iget_extraGetter(type (C), this__2);
        |          do_iset_extraSetter(type (C), this__2, t#1);
        |          t#2 = do_iget_superGetter(type (C), this__2);
        |          do_iset_extraSetter(type (C), this__2, t#2);
        |          t#3 = getp(propNotVar__0, this__2);
        |          do_iset_superGetter(type (C), this__2, t#3);
        |          t#4 = getp(propNotVar__0, this__2);
        |          do_iset_superSetter(type (C), this__2, t#4);
        |          return__2 = void
        |      });
        |      @visibility(\public) @fn @stay @fromType(C) let update__0;
        |      update__0 = (@stay fn update(@impliedThis(C) this__3: C, i__1 /* aka i */: Int32) /* return__3 */: Void {
        |          setp(propNotVar__0, this__3, i__1);
        |          setp(propVar__0, this__3, "hi");
        |          return__3 = void
        |      });
        |      @visibility(\public) @stay @fromType(C) let extraSetter__0;
        |      @visibility(\public) @fn @stay @fromType(C) let nym`set.extraSetter__1`;
        |      nym`set.extraSetter__1` = (@stay fn nym`set.extraSetter`(@impliedThis(C) this__4: C, k__0 /* aka k */: Int32) /* return__4 */: Void {
        |          setp(propVar__0, this__4, k__0);
        |          return__4 = void
        |      });
        |      @visibility(\public) @stay @fromType(C) let extraGetter__0;
        |      @visibility(\public) @fn @stay @fromType(C) let nym`get.extraGetter__1`;
        |      nym`get.extraGetter__1` = (@stay fn nym`get.extraGetter`(@impliedThis(C) this__5: C) /* return__5 */: Int32 {
        |          return__5 = getp(propVar__0, this__5)
        |      });
        |      @fn @visibility(\public) @stay @fromType(C) let getpropNotVar__0;
        |      getpropNotVar__0 = (@stay fn (@impliedThis(C) this__6: C) /* return__6 */: Int32 {
        |          return__6 = getp(propNotVar__0, this__6)
        |      });
        |      @fn @visibility(\public) @stay @fromType(C) let getpropVar__0;
        |      getpropVar__0 = (@stay fn (@impliedThis(C) this__7: C) /* return__7 */: Int32 {
        |          return__7 = getp(propVar__0, this__7)
        |      });
        |      @fn @visibility(\public) @stay @fromType(C) let setpropVar__0;
        |      setpropVar__0 = (@stay fn (@impliedThis(C) this__8: C, newPropVar__0: Int32) /* return__8 */: Void {
        |          setp(propVar__0, this__8, newPropVar__0);
        |          return__8 = void
        |      });
        |      `test//`.alsoUpdate = (@stay fn alsoUpdate(c__0 /* aka c */: C, j__0 /* aka j */: Int32) /* return__9 */: Void {
        |          var t#5, t#6, t#7, t#8;
        |          let t#9;
        |          t#9 = j__0;
        |          do_set_propNotVar(c__0, t#9);
        |          t#5 = j__0;
        |          do_set_propVar(c__0, t#5);
        |          do_set_propVar(c__0, "bye");
        |          t#6 = j__0;
        |          do_set_extraSetter(c__0, t#6);
        |          t#7 = j__0;
        |          do_set_extraGetter(c__0, t#7);
        |          t#8 = j__0;
        |          do_set_extraWrong(c__0, t#8);
        |          do_set_superSetter(c__0, j__0);
        |          return__9 = void
        |      })
        |
        |      ```,
        |    exports: {
        |      C: "C: Type",
        |      alsoUpdate: "fn alsoUpdate: Function",
        |      "I": "I: Type",
        |    },
        |  },
        |  errors: [
        |    "No member wrong in C | I!",
        |    "Wrong number of arguments.  Expected 2!",
        |    "Expected subtype of Type, but got C!",
        |    "Member extraGetter defined in C | I incompatible with usage!",
        |    "Member superGetter defined in C | I incompatible with usage!",
        |    "Member propNotVar defined in C incompatible with usage!",
        |    "Expected subtype of Int32, but got String!",
        |    "Member propNotVar defined in C | I incompatible with usage!",
        |    "Expected subtype of Int32, but got String!",
        |    "Member extraGetter defined in C | I incompatible with usage!",
        |    "No member extraWrong in C | I!",
        |  ],
        |}
        """.trimMargin(),
    )

    @Test
    fun fnType() = assertModuleAtStage(
        stage = Stage.GenerateCode,
        input = """
        |let f: fn (Int): Int = fn (x: Int): Int { x + 1 };
        |f(41)
        """.trimMargin(),
        moduleResultNeeded = true,
        want = """
        |{
        |    "generateCode": {
        |        "body":
        |        ```
        |        let return__0, @fn @reach(\none) f__0: (fn (Int32): Int32);
        |        f__0 = (@stay fn f(x__0 /* aka x */: Int32) /* return__1 */: Int32 {
        |            return__1 = x__0 + 1
        |        });
        |        return__0 = 42
        |
        |        ```
        |    }
        |}
        """.trimMargin(),
    )

    @Test
    fun catsAreNice() = assertModuleAtStage(
        stage = Stage.GenerateCode,
        input = """
        |let f(s: String): Void {
        |  cat(s);
        |  cat(s, s);
        |  cat(s, s, s);
        |  cat(s, s, s, s);
        |}
        """.trimMargin(),
        want = """
        |{
        |  generateCode: {
        |    body:
        |      ```
        |      @fn @reach(\none) let f__0 ⦂(fn (String): Void);
        |      f__0 = (@stay fn f(s__0 /* aka s */: String) /* return__1 */: Void {
        |          cat(s__0);
        |          cat(s__0, s__0);
        |          cat(s__0, s__0, s__0);
        |          cat(s__0, s__0, s__0, s__0);
        |          return__1 = void
        |      })
        |
        |      ```
        |  },
        |}
        """.trimMargin(),
        pseudoCodeDetail = PseudoCodeDetail(showInferredTypes = true),
    )

    @Test
    fun catsAreRadActually() = assertModuleAtStage(
        stage = Stage.GenerateCode,
        input = """
        |let f(s: String): Void {
        |  cat(0);
        |  cat(s, 0);
        |  cat(s, 0, s);
        |  cat(s, s, 0, s);
        |}
        """.trimMargin(),
        want = """
        |{
        |  generateCode: {
        |    body:
        |      ```
        |      @fn @reach(\none) let f__0;
        |      f__0 = (@stay fn f(s__0 /* aka s */: String) /* return__1 */: Void {
        |          var t#0, t#1, t#2;
        |          cat(do_bind_toString(0)());
        |          t#0 = do_bind_toString(0)();
        |          cat(s__0, t#0);
        |          t#1 = do_bind_toString(0)();
        |          cat(s__0, t#1, s__0);
        |          t#2 = do_bind_toString(0)();
        |          cat(s__0, s__0, t#2, s__0);
        |          return__1 = void
        |      })
        |
        |      ```
        |  },
        |}
        """.trimMargin(),
    )

    @Test
    fun catsPlayWithStringAndNull() = assertModuleAtStage(
        stage = Stage.GenerateCode,
        input = $$"""
        |let f(s: String, a: Int?): String {
        |  "${s}${a}${a ?? -1}"
        |}
        """.trimMargin(),
        want = """
        |{
        |  generateCode: {
        |    body:
        |      ```
        |      @fn @reach(\none) let f__0;
        |      f__0 = (@stay fn f(s__0 /* aka s */: String, a__0 /* aka a */: Int32?) /* return__1 */: String {
        |          var t#0, t#1, t#2, t#3;
        |          if (!isNull(a__0)) {
        |            let a#0;
        |            a#0 = notNull(a__0);
        |            t#3 = a#0
        |          } else {
        |            t#3 = -1
        |          };
        |          if (isNull(a__0)) {
        |            t#1 = "null"
        |          } else {
        |            t#0 = do_bind_toString(notNull(a__0))();
        |            t#1 = t#0
        |          };
        |          t#2 = do_bind_toString(t#3)();
        |          return__1 = cat(s__0, t#1, t#2)
        |      })
        |
        |      ```
        |  },
        |}
        """.trimMargin(),
    )

    /** No cats were harmed in the making of this test. */
    @Test
    fun rawCatsGetCooked() = assertModuleAtStage(
        stage = Stage.GenerateCode,
        input = $$"""
            |let f(s: String): Void {
            |  raw"${s}";
            |  // Also a call that will fail, so we make sure to test that.
            |  raw"${what}";
            |}
        """.trimMargin(),
        want = """
            |{
            |  generateCode: {
            |    body:
            |      ```
            |      @fn @reach(\none) let f__0;
            |      f__0 = (@stay fn f(s__0 /* aka s */: String) /* return__1 */: Void {
            |          cat(s__0);
            |          cat(what);
            |          return__1 = void
            |      })
            |
            |      ```
            |  },
            |  errors: [
            |    "No declaration for what!",
            |  ],
            |}
        """.trimMargin(),
    )

    @Ignore // TODO(mikesamuel): Fix typing of generic methods with explicit actuals
    @Test
    fun mapDroppingTypes() = assertModuleAtStage(
        stage = Stage.GenerateCode,
        input = """
            |let ls: List<Int> = [1, 2];
            |ls.mapDropping<String> { (x: Int): String => x.toString(10) }
        """.trimMargin(),
        want = """
            |{
            |  type: {
            |    body: ```
            |
            |    ```
            |  },
            |  generateCode: {
            |    body: ```
            |
            |    ```
            |  }
            |}
        """.trimMargin(),
        pseudoCodeDetail = PseudoCodeDetail(showInferredTypes = true),
    )

    @Ignore
    @Test
    fun banExportNotAtTopLevel() = assertModuleAtStage(
        stage = Stage.GenerateCode,
        input = "let f(x) { export let y = x; }",
        want = """
        {
          errors: [ "TODO" ]
        }
        """,
    )

    @Ignore
    @Test
    fun banExportsThatAreReAssignable() = assertModuleAtStage(
        stage = Stage.GenerateCode,
        input = "export var i = 1; i = 2",
        want = """
        {
          errors: [ "TODO" ]
        }
        """,
    )

    @Ignore
    @Test
    fun banExportInLoops() = assertModuleAtStage(
        stage = Stage.GenerateCode,
        input = "var i = 0; while (i <= 2) { export let x = i; i += 1 }",
        want = """
        {
          errors: [ "TODO" ]
        }
        """,
    )

    @Test
    fun banExportsExposingNonExported() = assertModuleAtStage(
        stage = Stage.GenerateCode,
        input = """
            |interface Hidden {}
            |export class Exported<HI extends Hidden>(public hi: Hidden) extends Hidden {
            |  public attempt(): Hidden { hi }
            |  public attempt2<H extends Hidden>(hmm: H): H { hmm }
            |  public static subvert(): Map<String, Hidden> { more }
            |  // This one should be fine, unlike all the others.
            |  private ha: Hidden = hi;
            |}
            |export let consider(hu: Hidden): Hidden? { hu }
            |export let sneak<H extends Hidden>(he: H): H { he }
            |export let more = new Map<String, Hidden>([]);
        """.trimMargin(),
        want = """
            |{
            |    generateCode: {
            |        body:
            |        ```
            |        @typeDecl(Hidden__0) @stay let Hidden__0;
            |        Hidden__0 = type (Hidden__0);
            |        @typeDecl(Exported<HI__0>) @stay let `test//`.Exported;
            |        `test//`.Exported = type (Exported);
            |        @fn let `test//`.consider, @fn `test//`.sneak, @typePlaceholder(Hidden__0) typePlaceholder#0: Empty;
            |        typePlaceholder#0 = {class: Empty__0};
            |        let `test//`.more;
            |        `test//`.more = new Map<String, Hidden__0>(list());
            |        @typeFormal(\HI) @typeDefined(HI__0) @fromType(Exported<HI__0>) let HI__0;
            |        HI__0 = type (HI__0);
            |        @constructorProperty @visibility(\public) @stay @fromType(Exported<HI__0>) let hi__0: Hidden__0;
            |        @visibility(\public) @fn @stay @fromType(Exported<HI__0>) let attempt__0;
            |        attempt__0 = (@stay fn attempt(@impliedThis(Exported<HI__0>) this__0: Exported<HI__0>) /* return__0 */: Hidden__0 {
            |            return__0 = getp(hi__0, this__0)
            |        });
            |        @visibility(\public) @fn @stay @fromType(Exported<HI__0>) let attempt2__0;
            |        @typeFormal(\H) @typeDecl(H__0) let H__0;
            |        H__0 = type (H__0);
            |        attempt2__0 = (@stay fn attempt2<H__0 extends Hidden__0>(@impliedThis(Exported<HI__0>) this__1: Exported<HI__0>, hmm__0 /* aka hmm */: H__0) /* return__1 */: H__0 {
            |            return__1 = hmm__0
            |        });
            |        @fn @static @visibility(\public) @stay @fromType(Exported<HI__0>) let subvert__0;
            |        subvert__0 = (@stay fn subvert /* return__2 */: (Map<String, Hidden__0>) {
            |            return__2 = `test//`.more
            |        });
            |        @visibility(\private) @stay @fromType(Exported<HI__0>) let ha__0: Hidden__0;
            |        @fn @visibility(\public) @stay @fromType(Exported<HI__0>) let constructor__0;
            |        constructor__0 = (@stay fn constructor(@impliedThis(Exported<HI__0>) this__2: Exported<HI__0>, hi__1 /* aka hi */: Hidden__0) /* return__3 */: Void {
            |            let t#0;
            |            t#0 = hi__1;
            |            setp(hi__0, this__2, t#0);
            |            setp(ha__0, this__2, hi__1);
            |            return__3 = void
            |        });
            |        @fn @visibility(\public) @stay @fromType(Exported<HI__0>) let gethi__0;
            |        gethi__0 = (@stay fn (@impliedThis(Exported<HI__0>) this__3: Exported<HI__0>) /* return__4 */: Hidden__0 {
            |            return__4 = getp(hi__0, this__3)
            |        });
            |        `test//`.consider = (@stay fn consider(hu__0 /* aka hu */: Hidden__0) /* return__5 */: (Hidden__0?) {
            |            return__5 = hu__0
            |        });
            |        @typeFormal(\H) @typeDecl(H__1) let H__1;
            |        H__1 = type (H__1);
            |        `test//`.sneak = (@stay fn sneak<H__1 extends Hidden__0>(he__0 /* aka he */: H__1) /* return__6 */: H__1 {
            |            return__6 = he__0
            |        })
            |
            |        ```,
            |        exports: {
            |            "Exported": "Exported: Type",
            |            "consider": "fn consider: Function",
            |            "sneak": "fn sneak: Function",
            |            "more": null,
            |        }
            |    },
            |    errors: [
            |      "Export depends publicly on non-exported symbol Hidden!",
            |      "Export depends publicly on non-exported symbol Hidden!",
            |      "Export depends publicly on non-exported symbol Hidden!",
            |      "Export depends publicly on non-exported symbol Hidden!",
            |      "Export depends publicly on non-exported symbol Hidden!",
            |      "Export depends publicly on non-exported symbol Hidden!",
            |      "Export depends publicly on non-exported symbol Hidden!",
            |      "Export depends publicly on non-exported symbol Hidden!",
            |      "Export depends publicly on non-exported symbol Hidden!",
            |      "Export depends publicly on non-exported symbol Hidden!",
            |    ]
            |}
        """.trimMargin(),
    )

    @Test
    fun banMixedExportsJustFunctionType() = assertModuleAtStage(
        stage = Stage.GenerateCode,
        input = """
            |class Hidden { }
            |export let sneak(hidden: fn (Hidden): Void): Void { }
        """.trimMargin(),
        want = """
            |{
            |    generateCode: {
            |        body:
            |        ```
            |        @typeDecl(Hidden__0) @stay @reach(\none) let Hidden__0;
            |        Hidden__0 = type (Hidden__0);
            |        @fn let `test//`.sneak;
            |        @fn @visibility(\public) @stay @fromType(Hidden__0) @reach(\none) let constructor__0;
            |        constructor__0 = (@stay fn constructor(@impliedThis(Hidden__0) this__0: Hidden__0) /* return__0 */: Void {
            |            return__0 = void
            |        });
            |        `test//`.sneak = (@stay fn sneak(hidden__0 /* aka hidden */: (fn (Hidden__0): Void)) /* return__1 */: Void {
            |            return__1 = void
            |        })
            |
            |        ```,
            |        exports: {
            |            sneak: "fn sneak: Function",
            |        }
            |    },
            |    errors: [
            |      "Export depends publicly on non-exported symbol Hidden!",
            |    ]
            |}
        """.trimMargin(),
    )

    @Test
    fun unalignedNamedArgs() = assertModuleAtStage(
        stage = Stage.GenerateCode,
        // TODO This only matters for constructors/factories going forward.
        // TODO And maybe we'll manage those positioned, so this test might be best removed sometime.
        input = """
            |let hi(name: String): Void { console.log(name); }
            |hi(\nom, "Alice");
        """.trimMargin(),
        want = """
            |{
            |  generateCode: {
            |    body:
            |      ```
            |      let console#0;
            |      console#0 = getConsole();
            |      @fn let hi__0;
            |      hi__0 = (@stay fn hi(name__0 /* aka name */: String) /* return__1 */: Void {
            |          do_bind_log(console#0)(name__0);
            |          return__1 = void
            |      });
            |      hi__0(\nom, "Alice")
            |
            |      ```,
            |  },
            |  errors: [
            |    "nom has not been declared!"
            |  ]
            |}
        """.trimMargin(),
    )

    @Test
    fun nestedAssignmentInResultPosition() = assertModuleAtStage(
        stage = Stage.Run,
        want = """
            |{
            |  run: "123: Int32",
            |  generateCode: {
            |    body: ```
            |      let return__0, @reach(\none) a__0, b__0;
            |      b__0 = oneTwoThree();
            |      a__0 = b__0;
            |      return__0 = b__0
            |
            |      ```
            |  }
            |}
        """.trimMargin(),
        moduleResultNeeded = true,
    ) { module, _ ->
        val input = """
            |let a, b;
            |a = b = oneTwoThree()
        """.trimMargin()
        module.deliverContent(
            ModuleSource(
                filePath = testCodeLocation, fetchedContent = input, languageConfig = StandaloneLanguageConfig,
            ),
        )
        module.addEnvironmentBindings(oneToThreeBindings)
    }

    @Test
    fun autoCastIs() = assertModuleAtStage(
        stage = Stage.GenerateCode,
        input = """
            |let some(maybe: StringIndexOption): StringIndex {
            |  if (maybe is StringIndex) {
            |    maybe
            |  } else {
            |    String.begin
            |  }
            |}
        """.trimMargin(),
        want = """
            |{
            |  generateCode: {
            |    body: ```
            |      @fn @reach(\none) let some__0;
            |      some__0 = (@stay fn some(maybe__0 /* aka maybe */: StringIndexOption) /* return__0 */: StringIndex {
            |          if (is(maybe__0, StringIndex)) {
            |            return__0 = assertAs(maybe__0, StringIndex)
            |          } else {
            |            return__0 = getStatic(String, \begin)
            |          }
            |      })
            |
            |      ```
            |  },
            |}
        """.trimMargin(),
    )

    @Test
    fun autoCastWhen() = assertModuleAtStage(
        stage = Stage.GenerateCode,
        input = """
            |let some(maybe: StringIndexOption): StringIndex {
            |  when (maybe) {
            |    is StringIndex -> maybe;
            |    else -> String.begin;
            |  }
            |}
        """.trimMargin(),
        want = """
            |{
            |  generateCode: {
            |    body: ```
            |      @fn @reach(\none) let some__0;
            |      some__0 = (@stay fn some(maybe__0 /* aka maybe */: StringIndexOption) /* return__0 */: StringIndex {
            |          if (is(maybe__0, StringIndex)) {
            |            return__0 = assertAs(maybe__0, StringIndex)
            |          } else {
            |            return__0 = getStatic(String, \begin)
            |          }
            |      })
            |
            |      ```
            |  },
            |}
        """.trimMargin(),
    )

    @Test
    fun nestedSetpInResultPosition() = assertModuleAtStage(
        stage = Stage.Run,
        want = """
            |{
            |  run: "C__0: Type",
            |  generateCode: {
            |    body: ```
            |      let return__0;
            |      @constructorProperty @property(\x) @visibility(\private) @stay @fromType(C__0) var x__0: Int32;
            |      @constructorProperty @property(\y) @visibility(\private) @stay @fromType(C__0) var y__0: Int32;
            |      @method(\f) @visibility(\public) @fn @stay @fromType(C__0) let f__0;
            |      f__0 = (@stay fn f(@impliedThis(C__0) this__0: C__0) /* return__1 */: Int32 {
            |          return__1 = oneTwoThree();
            |          setp(y__0, this__0, return__1);
            |          setp(x__0, this__0, return__1)
            |      });
            |      @fn @method(\constructor) @visibility(\public) @stay @fromType(C__0) let constructor__0;
            |      constructor__0 = (@stay fn constructor(@impliedThis(C__0) this__1: C__0, @optional(true) x__1 /* aka x */: Int32?, @optional(true) y__1 /* aka y */: Int32?) /* return__2 */: Void {
            |          let x__2 /* aka x */: Int32;
            |          if (isNull(x__1)) {
            |            x__2 = 0
            |          } else {
            |            x__2 = notNull(x__1)
            |          };
            |          let y__2 /* aka y */: Int32;
            |          if (isNull(y__1)) {
            |            y__2 = 0
            |          } else {
            |            y__2 = notNull(y__1)
            |          };
            |          setp(x__0, this__1, x__2);
            |          setp(y__0, this__1, y__2);
            |          return__2 = void
            |      });
            |      @typeDecl(C__0) @stay let C__0;
            |      C__0 = type (C__0);
            |      return__0 = type (C__0)
            |
            |      ```
            |  }
            |}
        """.trimMargin(),
        moduleResultNeeded = true,
        pseudoCodeDetail = PseudoCodeDetail.default.copy(showTypeMemberMetadata = true),
    ) { module, _ ->
        val input = """
            |class C(
            |  private var x: Int = 0,
            |  private var y: Int = 0,
            |) {
            |  public let f(): Int {
            |    this.x = this.y = oneTwoThree()
            |  }
            |}
        """.trimMargin()
        module.deliverContent(
            ModuleSource(
                filePath = testCodeLocation, fetchedContent = input, languageConfig = StandaloneLanguageConfig,
            ),
        )
        module.addEnvironmentBindings(oneToThreeBindings)
    }

    @Test
    fun nestedSetterInvocationsInResultPosition() = assertModuleAtStage(
        stage = Stage.Run,
        pseudoCodeDetail = PseudoCodeDetail.default.copy(showTypeMemberMetadata = true),
        want = """
            |{
            |  run: "123: Int32",
            |  stdout: ```
            |    Assigned 123
            |    Assigned 123
            |
            |    ```,
            |  generateCode: {
            |      body: ```
            |      let return__0, console#0;
            |      console#0 = getConsole();
            |      @property(\p) @visibility(\public) @stay @fromType(C__0) let p__0;
            |      @method(\p) @setter @visibility(\public) @fn @stay @fromType(C__0) let nym`set.p__1`;
            |      nym`set.p__1` = (@stay fn nym`set.p`(@impliedThis(C__0) this__0: C__0, newValue__0 /* aka newValue */: Int32) /* return__1 */: Void {
            |          var t#0;
            |          t#0 = do_bind_toString(newValue__0)(10);
            |          do_bind_log(console#0)(cat("Assigned ", t#0));
            |          return__1 = void
            |      });
            |      @fn @method(\constructor) @visibility(\public) @stay @fromType(C__0) let constructor__0;
            |      constructor__0 = (@stay fn constructor(@impliedThis(C__0) this__1: C__0) /* return__2 */: Void {
            |          return__2 = void
            |      });
            |      @typeDecl(C__0) @stay let C__0;
            |      C__0 = type (C__0);
            |      let c__0;
            |      c__0 = new C__0();
            |      return__0 = oneTwoThree();
            |      do_set_p(c__0, return__0);
            |      do_set_p(c__0, return__0)
            |
            |      ```
            |  }
            |}
        """.trimMargin(),
        moduleResultNeeded = true,
    ) { module, _ ->
        val input = $$"""
            |class C {
            |  public set p(newValue: Int) {
            |    console.log("Assigned ${newValue.toString(10)}");
            |  }
            |}
            |let c = new C();
            |c.p = c.p = oneTwoThree()
        """.trimMargin()
        module.deliverContent(
            ModuleSource(
                filePath = testCodeLocation, fetchedContent = input,
                languageConfig = StandaloneLanguageConfig,
            ),
        )
        module.addEnvironmentBindings(oneToThreeBindings)
    }

    /**
     * Not having argument or return types causes the errors here.
     * See also `TyperTest.assignedFnWithInferredSigTypes`.
     */
    @Test
    fun assignedFnWithInferredSigTypes() = assertModuleAtStage(
        stage = Stage.GenerateCode,
        input = """let funny: fn (Int): String = fn (n) { n.toString() };""",
        want = """
        {
          generateCode: {
            body: ```
                @fn @reach(\none) let funny__0: (fn (Int32): String);
                funny__0 = (@stay fn funny(n__0 /* aka n */) /* return__0 */{
                    return__0 = do_bind_toString(n__0)()
                })

                ```
          }
        }
        """,
    )

    @Test
    fun booleanTypeError() = assertModuleAtStage(
        stage = Stage.GenerateCode,
        input = "if (1) { 2 } else { 3 }",
        moduleResultNeeded = true,
        want = """
            |{
            |  generateCode: {
            |    body: ```
            |      let return__0;
            |      if (1) {
            |        return__0 = 2
            |      } else {
            |        return__0 = 3
            |      }
            |
            |      ```
            |  },
            |  errors: [
            |    "Expected value of type Boolean not Int32!"
            |  ]
            |}
        """.trimMargin(),
    )

    @Test
    fun lotsaLets() = assertModuleAtStage(
        input = """
            |// Issue 1408
            |let x1 = 1;
            |let x2 = 2;
            |let x3 = 3;
            |let x4 = 4;
            |let x5 = 5;
            |let x6 = 6;
            |let x7 = 7;
            |let x8 = 8;
            |let x9 = 9;
            |let x10 = 10;
            |let x11 = 11;
            |let x12 = 12;
            |let x13 = 13;
            |let x14 = 14;
            |let x15 = 15;
            |let x16 = 16;
            |let x17 = 17;
            |let x18 = 18;
            |let x19 = 19;
            |let x20 = 20;
            |let x21 = 21;
            |let x22 = 22;
            |let x23 = 23;
            |let x24 = 24;
            |let x25 = 25;
            |let x26 = 26;
            |let x27 = 27;
            |let x28 = 28;
            |let x29 = 29;
            |let x30 = 30;
        """.trimMargin(),
        stage = Stage.Run,
        want = """
            |{
            |  run: "void: Void",
            |}
        """.trimMargin(),
    )

    @Ignore
    @Test
    fun enumConstants() = assertModuleAtStage(
        stage = Stage.GenerateCode,
        input = """
            |enum E { A, B }
        """.trimMargin(),
        want = """
            |{
            |  generateCode: {
            |    code: ```
            |
            |        ```,
            |  },
            |}
        """.trimMargin(),
    )

    @Test
    fun emptyInterface() = assertModuleAtStage(
        stage = Stage.GenerateCode,
        input = """
            |interface I {}
        """.trimMargin(),
        moduleResultNeeded = true,
        want = """
            |{
            |  generateCode: {
            |    body: ```
            |        let return__0, @typePlaceholder(I__0) typePlaceholder#0: Empty;
            |        typePlaceholder#0 = {class: Empty__0};
            |        @typeDecl(I__0) @stay let I__0;
            |        I__0 = type (I__0);
            |        return__0 = type (I__0)
            |
            |        ```
            |  },
            |}
        """.trimMargin(),
    )

    @Test
    fun hideOverrideProperty() = assertModuleAtStage(
        stage = Stage.GenerateCode,
        input = """
            |interface I { public x: Int }
            |class C(protected x: Int) extends I {}
        """.trimMargin(),
        moduleResultNeeded = true,
        pseudoCodeDetail = PseudoCodeDetail.default.copy(showTypeMemberMetadata = true),
        want = """
            |{
            |  generateCode: {
            |    body: ```
            |        let return__0;
            |        @property(\x) @visibility(\public) @stay @fromType(I__0) let x__0: Int32;
            |        @typeDecl(I__0) @stay let I__0;
            |        I__0 = type (I__0);
            |        @typeDecl(C__0) @stay let C__0;
            |        C__0 = type (C__0);
            |        @constructorProperty @property(\x) @visibility(\protected) @stay @fromType(C__0) let x__1: Int32;
            |        @fn @method(\constructor) @visibility(\public) @stay @fromType(C__0) let constructor__0;
            |        constructor__0 = (@stay fn constructor(@impliedThis(C__0) this__0: C__0, x__2 /* aka x */: Int32) /* return__1 */: Void {
            |            setp(x__1, this__0, x__2);
            |            return__1 = void
            |        });
            |        return__0 = type (C__0)
            |
            |        ```
            |  },
            |  errors: [
            |    "Override has lower visibility than in I__0!",
            |  ]
            |}
        """.trimMargin(),
    )

    @Test
    fun hideOverrideMethod() = assertModuleAtStage(
        stage = Stage.GenerateCode,
        input = """
            |interface I { public f(): Int; }
            |class C extends I { protected f(): Int { 1 } }
        """.trimMargin(),
        pseudoCodeDetail = PseudoCodeDetail.default.copy(showTypeMemberMetadata = true),
        want = """
            |{
            |  generateCode: {
            |    body: ```
            |        @method(\f) @visibility(\public) @fn @stay @fromType(I__0) @reach(\none) let f__0;
            |        f__0 = (@stay fn f(@impliedThis(I__0) this__0: I__0) /* return__0 */: Int32 {
            |            pureVirtual()
            |        });
            |        @typeDecl(I__0) @stay @reach(\none) let I__0;
            |        I__0 = type (I__0);
            |        @typeDecl(C__0) @stay @reach(\none) let C__0;
            |        C__0 = type (C__0);
            |        @method(\f) @visibility(\protected) @fn @stay @fromType(C__0) @reach(\none) let f__1;
            |        f__1 = (@stay fn f(@impliedThis(C__0) this__1: C__0) /* return__1 */: Int32 {
            |            return__1 = 1
            |        });
            |        @fn @method(\constructor) @visibility(\public) @stay @fromType(C__0) @reach(\none) let constructor__0;
            |        constructor__0 = (@stay fn constructor(@impliedThis(C__0) this__2: C__0) /* return__3 */: Void {
            |            return__3 = void
            |        })
            |
            |        ```
            |  },
            |  errors: [
            |    "Override has lower visibility than in I__0!",
            |  ]
            |}
        """.trimMargin(),
    )

    @Test
    fun hideOverrideMethodGeneric() = assertModuleAtStage(
        stage = Stage.GenerateCode,
        input = """
            |interface I<T>          { public    f<A>(x: A, t: T, i: I<T>): T; }
            |class C<U> extends I<U> { protected f<B>(x: B, u: U, i: I<U>): U { u } }
        """.trimMargin(),
        pseudoCodeDetail = PseudoCodeDetail.default.copy(showTypeMemberMetadata = true),
        want = """
            |{
            |  generateCode: {
            |    body: ```
            |        @typeFormal(\T) @memberTypeFormal(\T) @typeDefined(T__0) @fromType(I__0<T__0>) @reach(\none) let T__0;
            |        T__0 = type (T__0);
            |        @method(\f) @visibility(\public) @fn @stay @fromType(I__0<T__0>) @reach(\none) let f__0;
            |        @typeFormal(\A) @typeDecl(A__0) @reach(\none) let A__0;
            |        A__0 = type (A__0);
            |        f__0 = (@stay fn f<A__0 extends AnyValue>(@impliedThis(I__0<T__0>) this__0: I__0<T__0>, x__0 /* aka x */: A__0, t__0 /* aka t */: T__0, i__0 /* aka i */: I__0<T__0>) /* return__1 */: T__0 {
            |            pureVirtual()
            |        });
            |        @typeDecl(I__0<T__0>) @stay @reach(\none) let I__0;
            |        I__0 = type (I__0);
            |        @typeDecl(C__0<U__0>) @stay @reach(\none) let C__0;
            |        C__0 = type (C__0);
            |        @typeFormal(\U) @memberTypeFormal(\U) @typeDefined(U__0) @fromType(C__0<U__0>) @reach(\none) let U__0;
            |        U__0 = type (U__0);
            |        @method(\f) @visibility(\protected) @fn @stay @fromType(C__0<U__0>) @reach(\none) let f__1;
            |        @typeFormal(\B) @typeDecl(B__0) @reach(\none) let B__0;
            |        B__0 = type (B__0);
            |        f__1 = (@stay fn f<B__0 extends AnyValue>(@impliedThis(C__0<U__0>) this__1: C__0<U__0>, x__1 /* aka x */: B__0, u__0 /* aka u */: U__0, i__1 /* aka i */: I__0<U__0>) /* return__2 */: U__0 {
            |            return__2 = u__0
            |        });
            |        @fn @method(\constructor) @visibility(\public) @stay @fromType(C__0<U__0>) @reach(\none) let constructor__0;
            |        constructor__0 = (@stay fn constructor(@impliedThis(C__0<U__0>) this__2: C__0<U__0>) /* return__3 */: Void {
            |            return__3 = void
            |        })
            |
            |        ```
            |  },
            |  errors: [
            |    "Illegal type parameter A. Overridable methods don't allow generics!",
            |    "Override has lower visibility than in I__0!",
            |  ]
            |}
        """.trimMargin(),
    )

    /**
     * No [lang.temper.log.MessageTemplate.CannotExtendConcrete] because of `<S extends String>`.
     * *S* can validly bind to *String* or *Never*.
     */
    @Test
    fun typeParameterCanExtendConcreteType() = assertModuleAtStage(
        stage = Stage.GenerateCode,
        input = """
            |interface I { public f<S extends String>(s: S): Void; }
        """.trimMargin(),
        pseudoCodeDetail = PseudoCodeDetail.default.copy(showTypeMemberMetadata = true),
        want = """
            |{
            |  generateCode: {
            |    body: ```
            |        @method(\f) @visibility(\public) @fn @stay @fromType(I__0) @reach(\none) let f__0;
            |        @typeFormal(\S) @typeDecl(S__0) @reach(\none) let S__0;
            |        S__0 = type (S__0);
            |        f__0 = (@stay fn f<S__0 extends String>(@impliedThis(I__0) this__0: I__0, s__0 /* aka s */: S__0) /* return__0 */: Void {
            |            pureVirtual()
            |        });
            |        @typeDecl(I__0) @stay @reach(\none) let I__0;
            |        I__0 = type (I__0)
            |
            |        ```
            |  },
            |  errors: [
            |    "Illegal type parameter S. Overridable methods don't allow generics!",
            |  ],
            |}
        """.trimMargin(),
    )

    @Test
    fun returnTypeRequired() = assertModuleAtStage(
        stage = Stage.GenerateCode,
        input = """
            |let hi() {}
        """.trimMargin(),
        want = """
            |{
            |  generateCode: {
            |    body: ```
            |        @fn @reach(\none) let hi__0;
            |        hi__0 = (@stay fn hi /* return__1 */{
            |            return__1 = void
            |        })
            |
            |        ```
            |  },
            |  errors: [
            |    "Explicit return type required!",
            |  ]
            |}
        """.trimMargin(),
    )

    @Test
    fun returnTypeOptionalForSomeCases() = assertModuleAtStage(
        stage = Stage.GenerateCode,
        input = """
            |class Something {
            |  public constructor() {} // return type implied
            |  public get blah() { 5 } // return type required but missing
            |  public set blah(x: Int) {} // return type implied
            |}
        """.trimMargin(),
        pseudoCodeDetail = PseudoCodeDetail.default.copy(showTypeMemberMetadata = true),
        want = """
            |{
            |  generateCode: {
            |    body: ```
            |        @method(\constructor) @visibility(\public) @fn @stay @fromType(Something__0) @reach(\none) let constructor__0;
            |        constructor__0 = (@stay fn constructor(@impliedThis(Something__0) this__0: Something__0) /* return__0 */: Void {
            |            return__0 = void
            |        });
            |        @property(\blah) @visibility(\public) @stay @fromType(Something__0) @reach(\none) let blah__0;
            |        @method(\blah) @getter @visibility(\public) @fn @stay @fromType(Something__0) @reach(\none) let nym`get.blah__1`;
            |        nym`get.blah__1` = (@stay fn nym`get.blah`(@impliedThis(Something__0) this__1: Something__0) /* return__1 */{
            |            return__1 = 5
            |        });
            |        @method(\blah) @setter @visibility(\public) @fn @stay @fromType(Something__0) @reach(\none) let nym`set.blah__2`;
            |        nym`set.blah__2` = (@stay fn nym`set.blah`(@impliedThis(Something__0) this__2: Something__0, x__0 /* aka x */: Int32) /* return__2 */: Void {
            |            return__2 = void
            |        });
            |        @typeDecl(Something__0) @stay @reach(\none) let Something__0;
            |        Something__0 = type (Something__0)
            |
            |        ```
            |  },
            |  errors: [
            |    "Explicit return type required!",
            |  ]
            |}
        """.trimMargin(),
    )

    @Test
    fun typeMetadata() = assertModuleAtStage(
        stage = Stage.GenerateCode,
        want = """
            |{
            |  generateCode: {
            |    body:
            |      ```
            |      @typeDecl(I__0) @stay @foo @reach(\none) let I__0;
            |      I__0 = type (I__0);
            |      @typePlaceholder(I__0) @reach(\none) let typePlaceholder#0: Empty;
            |      typePlaceholder#0 = {class: Empty__0}
            |
            |      ```,
            |    types: {
            |      I: {
            |        name: "I__0",
            |        abstract: true,
            |        supers: ["AnyValue__0"],
            |        metadata: {
            |          "foo": ["void: Void"],
            |          "reach": ["\\none: Symbol"],
            |        }
            |      },
            |      Empty: {
            |        supers: ["AnyValue__0", "Equatable__0"],
            |        methods: [
            |          {
            |            name: "constructor__0",
            |            visibility: "private",
            |            kind: "Constructor",
            |            open: false
            |          },
            |        ],
            |        metadata: {
            |          connected: ["\"Empty\": String"],
            |        }
            |      },
            |    },
            |  },
            |}
        """.trimMargin(),
    ) { module, _ ->
        val input = """
            |@foo interface I {}
        """.trimMargin()

        module.deliverContent(
            ModuleSource(
                filePath = testCodeLocation, fetchedContent = input, languageConfig = StandaloneLanguageConfig,
            ),
        )

        module.addEnvironmentBindings(
            mapOf(
                BuiltinName("@foo") to Value(MetadataDecorator(Symbol("foo")) { void }),
            ),
        )
    }

    @Test
    fun voidNotAValue() = assertModuleAtStage(
        stage = Stage.GenerateCode,
        // Implied and explicit void returns should be fine, but others should be errors.
        input = """
            |let a = [b()];
            |let b(): Void { console.log("hi"); }
            |let c(d: Void): Void { b() }
            |let e = c(a[0]);
            |c(void);
        """.trimMargin(),
        want = """
            |{
            |  generateCode: {
            |    body:
            |    ```
            |    let console#0;
            |    console#0 = getConsole();
            |    @fn let b__0, @fn c__0;
            |    b__0 = (@stay fn b /* return__0 */: Void {
            |        do_bind_log(console#0)("hi");
            |        return__0 = void
            |    });
            |    let a__0;
            |    b__0();
            |    a__0 = list(void);
            |    c__0 = (@stay fn c(d__0 /* aka d */: Void) /* return__1 */: Void {
            |        b__0();
            |        return__1 = void
            |    });
            |    @reach(\none) let e__0;
            |    do_bind_get(a__0)(0);
            |    c__0(void);
            |    e__0 = void;
            |    c__0(void)
            |
            |    ```
            |  },
            |  errors: [
            |    "Type formal <listT extends AnyValue> cannot bind to Void which does not fit upper bounds [AnyValue]!",
            |    "Void expressions cannot be used as values!",
            |    "Void expressions cannot be used as values!",
            |    "Void expressions cannot be used as values!",
            |    "Void expressions cannot be used as values!",
            |  ],
            |}
        """.trimMargin(),
    )

    @Test
    fun voidVsValue() = assertModuleAtStage(
        stage = Stage.GenerateCode,
        input = """
            |let trick(): Void { 123 }
            |let treat(): Int { 456 }
            |let trail(): Void { 789; }
        """.trimMargin(),
        want = """
            |{
            |  generateCode: {
            |    body:
            |    ```
            |    @fn @reach(\none) let trick__0, @fn @reach(\none) treat__0, @fn @reach(\none) trail__0;
            |    trick__0 = (@stay fn trick /* return__1 */: Void {
            |        return__1 = 123
            |    });
            |    treat__0 = (@stay fn treat /* return__2 */: Int32 {
            |        return__2 = 456
            |    });
            |    trail__0 = (@stay fn trail /* return__3 */: Void {
            |        return__3 = void
            |    })
            |
            |    ```
            |  },
            |  errors: [
            |    "Cannot assign to Void from Int32!",
            |    "Expected subtype of Void, but got Int32!",
            |  ],
            |}
        """.trimMargin(),
    )

    @Test
    fun impliedLambdaReturnType() = assertModuleAtStage(
        stage = Stage.GenerateCode,
        input = """
            |let f(g: fn (): Int): Int { g() }
            |let h(): Void { f { "hi" }; }
        """.trimMargin(),
        want = """
            |{
            |  generateCode: {
            |    body:
            |    ```
            |    @fn @reach(\none) let f__0, @fn @reach(\none) h__0;
            |    f__0 = (@stay fn f(g__0 /* aka g */: (fn (): Int32)) /* return__1 */: Int32 {
            |        return__1 = g__0()
            |    });
            |    h__0 = (@stay fn h /* return__2 */: Void {
            |        let fn__0;
            |        fn__0 = (@stay fn /* return__3 */{
            |            return__3 = "hi"
            |        });
            |        f__0(fn__0);
            |        return__2 = void
            |    })
            |
            |    ```
            |  },
            |  errors: [
            |    "Expected subtype of Int32, but got String!",
            |  ],
            |}
        """.trimMargin(),
    )

    @Test
    fun deadCode() = assertModuleAtStage(
        stage = Stage.GenerateCode,
        input = """
            |label: do {
            |  console.log("Logged");
            |  break label;
            |  console.log("Not logged");
            |}
        """.trimMargin(),
        want = """
            |{
            |  generateCode: {
            |    body: ```
            |      do_bind_log(getConsole())("Logged")
            |
            |      ```
            |  },
            |}
        """.trimMargin(),
    )

    @Test
    fun staticMethods() = assertModuleAtStage(
        stage = Stage.GenerateCode,
        input = """
            |class C {
            |  public static let f(i: Int): Int { i + 1 }
            |}
            |C.f(0)
        """.trimMargin(),
        moduleResultNeeded = true,
        pseudoCodeDetail = PseudoCodeDetail.default.copy(showTypeMemberMetadata = true),
        want = """
            |{
            |  generateCode: {
            |    body: ```
            |      let return__0;
            |      @staticProperty(\f) @fn @static @visibility(\public) @stay @fromType(C__0) @reach(\none) let f__0;
            |      f__0 = (@stay fn f(i__0 /* aka i */: Int32) /* return__1 */: Int32 {
            |          return__1 = i__0 + 1
            |      });
            |      @fn @method(\constructor) @visibility(\public) @stay @fromType(C__0) @reach(\none) let constructor__0;
            |      constructor__0 = (@stay fn constructor(@impliedThis(C__0) this__0: C__0) /* return__2 */: Void {
            |          return__2 = void
            |      });
            |      @typeDecl(C__0) @stay @reach(\none) let C__0;
            |      C__0 = type (C__0);
            |      return__0 = 1
            |
            |      ```
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun staticAccessGoodAndBad() = assertModuleAtStage(
        stage = Stage.GenerateCode,
        input = """
            |class C {
            |  private static ap: Int = 1;
            |  public static a: Int = ap + C.ap;
            |  private bp: Int = 1;
            |  private b: Int = bp + 1;
            |  public static f(i: Int): Int { i + a + C.a + ap + C.ap }
            |  private static fp(i: Int): Int { i + 1 }
            |  public g(i: Int): Int { 2 * C.f(i) * C.fp(i) * bp * b * this.bp * this.b }
            |  public h(i: Int): Int { 2 * f(i) * fp(i) * g(i) * this.g(i) }
            |  public static g2(i: Int): Int { 2 * C.f(i) * C.fp(i) }
            |  public static h2(i: Int): Int { 2 * f(i) * fp(i) }
            |}
            |let g3(i: Int): Int { 2 * C.f(i) * new C().g(i) * C.a * C.ap }
        """.trimMargin(),
        want = """
            |{
            |  "syntaxMacro": {
            |      "body":
            |      ```
            |      @typeDecl(C__0) @stay let C__0 = type (C__0);
            |      @fn let g3__0;
            |      class(\word, \C, \concrete, true, @typeDefined(C__0) fn {
            |          C__0 extends AnyValue;
            |          @static @visibility(\private) let ap__0: Int = 1;
            |          @static @visibility(\public) let a__0: Int = ap__0 + igetStatic(C__0, \ap);
            |          @maybeVar @visibility(\private) let bp__0: Int;
            |          @maybeVar @visibility(\private) let b__0: Int;
            |          @fn @static @visibility(\public) let f__0 = fn f(i__0 /* aka i */: Int) /* return__0 */: (Int) {
            |            fn__0: do {
            |              i__0 + a__0 + igetStatic(C__0, \a) + ap__0 + igetStatic(C__0, \ap)
            |            }
            |          };
            |          @fn @static @visibility(\private) let fp__0 = fn fp(i__1 /* aka i */: Int) /* return__1 */: (Int) {
            |            fn__1: do {
            |              i__1 + 1
            |            }
            |          };
            |          @visibility(\public) @fn let g__0 = fn g(@impliedThis(C__0) this__0: C__0, i__2 /* aka i */: Int) /* return__2 */: (Int) {
            |            fn__2: do {
            |              2 * igetStatic(C__0, \f)(i__2) * igetStatic(C__0, \fp)(i__2) * do_iget_bp(type (C__0), this(C__0)) * do_iget_b(type (C__0), this(C__0)) * do_iget_bp(type (C__0), this(C__0)) * do_iget_b(type (C__0), this(C__0))
            |            }
            |          };
            |          @visibility(\public) @fn let h__0 = fn h(@impliedThis(C__0) this__1: C__0, i__3 /* aka i */: Int) /* return__3 */: (Int) {
            |            fn__3: do {
            |              2 * f__0(i__3) * fp__0(i__3) * do_ibind_g(type (C__0), this(C__0))(i__3) * do_ibind_g(type (C__0), this(C__0))(i__3)
            |            }
            |          };
            |          @fn @static @visibility(\public) let g2__0 = fn g2(i__4 /* aka i */: Int) /* return__4 */: (Int) {
            |            fn__4: do {
            |              2 * igetStatic(C__0, \f)(i__4) * igetStatic(C__0, \fp)(i__4)
            |            }
            |          };
            |          @fn @static @visibility(\public) let h2__0 = fn h2(i__5 /* aka i */: Int) /* return__5 */: (Int) {
            |            fn__5: do {
            |              2 * f__0(i__5) * fp__0(i__5)
            |            }
            |          };
            |          @visibility(\public) let constructor__0 = fn constructor(@impliedThis(C__0) this__2: C__0) /* return__6 */: Void {
            |            do {
            |              do_iset_bp(type (C__0), this(C__0), 1);
            |              1
            |            };
            |            do {
            |              let t#0;
            |              do_iset_b(type (C__0), this(C__0), t#0 = do_iget_bp(type (C__0), this(C__0)) + 1);
            |              t#0
            |            };
            |          };
            |      });
            |      g3__0 = fn g3(i__6 /* aka i */: Int) /* return__7 */: (Int) {
            |        fn__6: do {
            |          2 * do_bind_f(C__0)(i__6) * do_bind_g(new C__0())(i__6) * do_get_a(C__0) * do_get_ap(C__0)
            |        }
            |      };
            |
            |      ```
            |  },
            |  "type": {
            |      "body":
            |      ```
            |      @typeDecl(C__0) @stay let C__0;
            |      C__0 = type (C__0);
            |      @fn let g3__0;
            |      @static @visibility(\private) @stay @fromType(C__0) let ap__0: Int32;
            |      ap__0 = 1;
            |      @static @visibility(\public) @stay @fromType(C__0) let a__0: Int32;
            |      igetStatic(C__0, \ap);
            |      a__0 = 2;
            |      @visibility(\private) @stay @fromType(C__0) let bp__0: Int32;
            |      @visibility(\private) @stay @fromType(C__0) let b__0: Int32;
            |      @fn @static @visibility(\public) @stay @fromType(C__0) let f__0;
            |      f__0 = (@stay fn f(i__0 /* aka i */: Int32) /* return__0 */: Int32 {
            |          fn__0: do {
            |            return__0 = i__0 + 2 + igetStatic(C__0, \a) + 1 + igetStatic(C__0, \ap)
            |          }
            |      });
            |      @fn @static @visibility(\private) @stay @fromType(C__0) let fp__0;
            |      fp__0 = (@stay fn fp(i__1 /* aka i */: Int32) /* return__1 */: Int32 {
            |          fn__1: do {
            |            return__1 = i__1 + 1
            |          }
            |      });
            |      @visibility(\public) @fn @stay @fromType(C__0) let g__0;
            |      g__0 = fn g(@impliedThis(C__0) this__0: C__0, i__2 /* aka i */: Int32) /* return__2 */: Int32 {
            |        void;
            |        fn__2: do {
            |          return__2 = 2 * igetStatic(C__0, \f)(i__2) * igetStatic(C__0, \fp)(i__2) * getp(bp__0, this__0) * getp(b__0, this__0) * getp(bp__0, this__0) * getp(b__0, this__0)
            |        }
            |      };
            |      @visibility(\public) @fn @stay @fromType(C__0) let h__0;
            |      h__0 = fn h(@impliedThis(C__0) this__1: C__0, i__3 /* aka i */: Int32) /* return__3 */: Int32 {
            |        void;
            |        fn__3: do {
            |          return__3 = 2 *(fn f)(i__3) *(fn fp)(i__3) * do_ibind_g(type (C__0), this__1)(i__3) * do_ibind_g(type (C__0), this__1)(i__3)
            |        }
            |      };
            |      @fn @static @visibility(\public) @stay @fromType(C__0) let g2__0;
            |      g2__0 = fn g2(i__4 /* aka i */: Int32) /* return__4 */: Int32 {
            |        void;
            |        fn__4: do {
            |          return__4 = 2 * igetStatic(C__0, \f)(i__4) * igetStatic(C__0, \fp)(i__4)
            |        }
            |      };
            |      @fn @static @visibility(\public) @stay @fromType(C__0) let h2__0;
            |      h2__0 = (@stay fn h2(i__5 /* aka i */: Int32) /* return__5 */: Int32 {
            |          fn__5: do {
            |            return__5 = 2 *(fn f)(i__5) *(fn fp)(i__5)
            |          }
            |      });
            |      @fn @visibility(\public) @stay @fromType(C__0) let constructor__0;
            |      constructor__0 = (@stay fn constructor(@impliedThis(C__0) this__2: C__0) /* return__6 */: Void {
            |          var t#1;
            |          setp(bp__0, this__2, 1);
            |          t#1 = getp(bp__0, this__2) + 1;
            |          setp(b__0, this__2, t#1);
            |          return__6 = void
            |      });
            |      g3__0 = fn g3(i__6 /* aka i */: Int32) /* return__7 */: Int32 {
            |        void;
            |        fn__6: do {
            |          return__7 = 2 * getStatic(C__0, \f)(i__6) * do_bind_g(new C__0())(i__6) * getStatic(C__0, \a) * getStatic(C__0, \ap)
            |        }
            |      }
            |
            |      ```
            |  },
            |  "generateCode": {
            |      "body":
            |      ```
            |      @typeDecl(C__0) @stay let C__0;
            |      C__0 = type (C__0);
            |      @fn @reach(\none) let g3__0;
            |      @static @visibility(\private) @stay @fromType(C__0) let ap__0: Int32;
            |      ap__0 = 1;
            |      @static @visibility(\public) @stay @fromType(C__0) let a__0: Int32;
            |      igetStatic(C__0, \ap);
            |      a__0 = 2;
            |      @visibility(\private) @stay @fromType(C__0) let bp__0: Int32;
            |      @visibility(\private) @stay @fromType(C__0) let b__0: Int32;
            |      @fn @static @visibility(\public) @stay @fromType(C__0) let f__0;
            |      f__0 = (@stay fn f(i__0 /* aka i */: Int32) /* return__0 */: Int32 {
            |          return__0 = i__0 + 2 + igetStatic(C__0, \a) + 1 + igetStatic(C__0, \ap)
            |      });
            |      @fn @static @visibility(\private) @stay @fromType(C__0) let fp__0;
            |      fp__0 = (@stay fn fp(i__1 /* aka i */: Int32) /* return__1 */: Int32 {
            |          return__1 = i__1 + 1
            |      });
            |      @visibility(\public) @fn @stay @fromType(C__0) let g__0;
            |      g__0 = (@stay fn g(@impliedThis(C__0) this__0: C__0, i__2 /* aka i */: Int32) /* return__2 */: Int32 {
            |          return__2 = 2 * igetStatic(C__0, \f)(i__2) * igetStatic(C__0, \fp)(i__2) * getp(bp__0, this__0) * getp(b__0, this__0) * getp(bp__0, this__0) * getp(b__0, this__0)
            |      });
            |      @visibility(\public) @fn @stay @fromType(C__0) let h__0;
            |      h__0 = (@stay fn h(@impliedThis(C__0) this__1: C__0, i__3 /* aka i */: Int32) /* return__3 */: Int32 {
            |          return__3 = 2 *(fn f)(i__3) *(fn fp)(i__3) * do_ibind_g(type (C__0), this__1)(i__3) * do_ibind_g(type (C__0), this__1)(i__3)
            |      });
            |      @fn @static @visibility(\public) @stay @fromType(C__0) let g2__0;
            |      g2__0 = (@stay fn g2(i__4 /* aka i */: Int32) /* return__4 */: Int32 {
            |          return__4 = 2 * igetStatic(C__0, \f)(i__4) * igetStatic(C__0, \fp)(i__4)
            |      });
            |      @fn @static @visibility(\public) @stay @fromType(C__0) let h2__0;
            |      h2__0 = (@stay fn h2(i__5 /* aka i */: Int32) /* return__5 */: Int32 {
            |          return__5 = 2 *(fn f)(i__5) *(fn fp)(i__5)
            |      });
            |      @fn @visibility(\public) @stay @fromType(C__0) let constructor__0;
            |      constructor__0 = (@stay fn constructor(@impliedThis(C__0) this__2: C__0) /* return__6 */: Void {
            |          var t#1;
            |          setp(bp__0, this__2, 1);
            |          t#1 = getp(bp__0, this__2) + 1;
            |          setp(b__0, this__2, t#1);
            |          return__6 = void
            |      });
            |      g3__0 = (@stay fn g3(i__6 /* aka i */: Int32) /* return__7 */: Int32 {
            |          return__7 = 2 * getStatic(C__0, \f)(i__6) * do_bind_g(new C__0())(i__6) * getStatic(C__0, \a) * getStatic(C__0, \ap)
            |      })
            |
            |      ```
            |  },
            |  errors: [
            |    "Type name required for accessing static member!",
            |    "Type name required for accessing static member!",
            |    "Type name required for accessing static member!",
            |    "Type name required for accessing static member!",
            |    "Type name required for accessing static member!",
            |    "Type name required for accessing static member!",
            |    "Type name required for accessing static member!",
            |    "Member ap defined in C__0 not publicly accessible!"
            |  ],
            |}
        """.trimMargin(),
    )

    @Test
    fun noInstantiateInterface() = assertModuleAtStage(
        stage = Stage.GenerateCode,
        input = """
            |interface Apple {}
            |class Banana {}
            |new Apple()
            |new Banana()
        """.trimMargin(),
        pseudoCodeDetail = PseudoCodeDetail.default.copy(showTypeMemberMetadata = true),
        want = """
            |{
            |  generateCode: {
            |    body: ```
            |        @typePlaceholder(Apple__0) let typePlaceholder#0: Empty;
            |        typePlaceholder#0 = {class: Empty__0};
            |        @typeDecl(Apple__0) @stay let Apple__0;
            |        Apple__0 = type (Apple__0);
            |        @typeDecl(Banana__0) @stay let Banana__0;
            |        Banana__0 = type (Banana__0);
            |        @fn @method(\constructor) @visibility(\public) @stay @fromType(Banana__0) let constructor__0;
            |        constructor__0 = (@stay fn constructor(@impliedThis(Banana__0) this__0: Banana__0) /* return__0 */: Void {
            |            return__0 = void
            |        });
            |        new Apple__0();
            |        new Banana__0()
            |
            |        ```
            |  },
            |  errors: [
            |    "Cannot instantiate abstract type Apple!",
            |  ],
            |}
        """.trimMargin(),
    )

    @Test
    fun exportSome() = assertModuleAtStage(
        stage = Stage.GenerateCode,
        // Includes examples of different kinds of roots and entities as well as transitive reachability and such.
        // Also includes an example of something reachable from both export and test roots.
        input = """
            |export let exportedInt = 1;
            |let unreachableInt = 2;
            |export let exportedFunction(b: Boolean): Void { if (b) { conditionallyExportReachable() } }
            |let conditionallyExportReachable(): Void { console.log("") }
            |let transitivelyTestReachable(): Void { console.log("") }
            |let exportAndTestReachable(): Void { console.log("") }
            |let initReachable(): Void { transitivelyInitReachable(); console.log("") }
            |let transitivelyInitReachable(): Void { console.log("") }
            |let unreachableFunction(): Void { console.log("") }
            |export class ExportedClass(
            |  private let propertyOfExportedClass: UsedOnlyAsPropertyType
            |) {
            |  private let methodOfExportedClass(): Void { exportAndTestReachable() }
            |}
            |class TestReachableClass {
            |  private let methodOfTestReachable(): Void { transitivelyTestReachable(); exportAndTestReachable() }
            |}
            |@test("testCase") let testCase(): Void { new TestReachableClass(); }
            |class UsedOnlyAsPropertyType {}
            |initReachable();
        """.trimMargin(),
        pseudoCodeDetail = PseudoCodeDetail.default.copy(showTypeMemberMetadata = true),
        want = """
            |{
            |  generateCode: {
            |    body: ```
            |        let console#0;
            |        console#0 = getConsole();
            |        @fn let `test//`.exportedFunction, @fn conditionallyExportReachable__0, @fn @reach(\test) transitivelyTestReachable__0, @fn exportAndTestReachable__0, @fn initReachable__0, @fn transitivelyInitReachable__0, @fn @reach(\none) unreachableFunction__0;
            |        @typeDecl(ExportedClass) @stay let `test//`.ExportedClass;
            |        `test//`.ExportedClass = type (ExportedClass);
            |        @typeDecl(TestReachableClass__0) @stay @reach(\test) let TestReachableClass__0;
            |        TestReachableClass__0 = type (TestReachableClass__0);
            |        @fn @test("testCase") let testCase__0;
            |        @typeDecl(UsedOnlyAsPropertyType__0) @stay let UsedOnlyAsPropertyType__0;
            |        UsedOnlyAsPropertyType__0 = type (UsedOnlyAsPropertyType__0);
            |        let `test//`.exportedInt;
            |        `test//`.exportedInt = 1;
            |        @reach(\none) let unreachableInt__0;
            |        unreachableInt__0 = 2;
            |        conditionallyExportReachable__0 = (@stay fn conditionallyExportReachable /* return__0 */: Void {
            |            do_bind_log(console#0)("");
            |            return__0 = void
            |        });
            |        `test//`.exportedFunction = (@stay fn exportedFunction(b__0 /* aka b */: Boolean) /* return__1 */: Void {
            |            if (b__0) {
            |              conditionallyExportReachable__0()
            |            };
            |            return__1 = void
            |        });
            |        transitivelyTestReachable__0 = (@stay fn transitivelyTestReachable /* return__2 */: Void {
            |            do_bind_log(console#0)("");
            |            return__2 = void
            |        });
            |        exportAndTestReachable__0 = (@stay fn exportAndTestReachable /* return__3 */: Void {
            |            do_bind_log(console#0)("");
            |            return__3 = void
            |        });
            |        transitivelyInitReachable__0 = (@stay fn transitivelyInitReachable /* return__4 */: Void {
            |            do_bind_log(console#0)("");
            |            return__4 = void
            |        });
            |        initReachable__0 = (@stay fn initReachable /* return__5 */: Void {
            |            transitivelyInitReachable__0();
            |            do_bind_log(console#0)("");
            |            return__5 = void
            |        });
            |        unreachableFunction__0 = (@stay fn unreachableFunction /* return__6 */: Void {
            |            do_bind_log(console#0)("");
            |            return__6 = void
            |        });
            |        @fn @method(\constructor) @visibility(\public) @stay @fromType(UsedOnlyAsPropertyType__0) let constructor__0;
            |        constructor__0 = (@stay fn constructor(@impliedThis(UsedOnlyAsPropertyType__0) this__0: UsedOnlyAsPropertyType__0) /* return__7 */: Void {
            |            return__7 = void
            |        });
            |        @constructorProperty @property(\propertyOfExportedClass) @visibility(\private) @stay @fromType(ExportedClass) let propertyOfExportedClass__0: UsedOnlyAsPropertyType__0;
            |        @method(\methodOfExportedClass) @visibility(\private) @fn @stay @fromType(ExportedClass) let methodOfExportedClass__0;
            |        methodOfExportedClass__0 = (@stay fn methodOfExportedClass(@impliedThis(ExportedClass) this__1: ExportedClass) /* return__8 */: Void {
            |            exportAndTestReachable__0();
            |            return__8 = void
            |        });
            |        @fn @method(\constructor) @visibility(\public) @stay @fromType(ExportedClass) let constructor__1;
            |        constructor__1 = (@stay fn constructor(@impliedThis(ExportedClass) this__2: ExportedClass, propertyOfExportedClass__1 /* aka propertyOfExportedClass */: UsedOnlyAsPropertyType__0) /* return__9 */: Void {
            |            setp(propertyOfExportedClass__0, this__2, propertyOfExportedClass__1);
            |            return__9 = void
            |        });
            |        @method(\methodOfTestReachable) @visibility(\private) @fn @stay @fromType(TestReachableClass__0) @reach(\test) let methodOfTestReachable__0;
            |        methodOfTestReachable__0 = (@stay fn methodOfTestReachable(@impliedThis(TestReachableClass__0) this__3: TestReachableClass__0) /* return__10 */: Void {
            |            transitivelyTestReachable__0();
            |            exportAndTestReachable__0();
            |            return__10 = void
            |        });
            |        @fn @method(\constructor) @visibility(\public) @stay @fromType(TestReachableClass__0) @reach(\test) let constructor__2;
            |        constructor__2 = (@stay fn constructor(@impliedThis(TestReachableClass__0) this__4: TestReachableClass__0) /* return__11 */: Void {
            |            return__11 = void
            |        });
            |        testCase__0 = (@stay fn testCase /* return__12 */: Void {
            |            new TestReachableClass__0();
            |            return__12 = void
            |        });
            |        initReachable__0()
            |
            |        ```,
            |    exports: {
            |      exportedFunction: "fn exportedFunction: Function",
            |      ExportedClass: "ExportedClass: Type",
            |      exportedInt: "1: Int32",
            |    },
            |  },
            |  errors: [
            |    "Export depends publicly on non-exported symbol UsedOnlyAsPropertyType!",
            |  ],
            |}
        """.trimMargin(),
    )

    @Test
    fun initAssignmentReachability() = assertModuleAtStage(
        stage = Stage.GenerateCode,
        input = """
            |// We don't eliminate var reassignments, so keep associated declarations.
            |var hi = 0;
            |hi = 1;
            |// Non-var for contrast.
            |let ha = 2;
        """.trimMargin(),
        want = """
            |{
            |  generateCode: {
            |    body: ```
            |        var hi__0;
            |        hi__0 = 0;
            |        hi__0 = 1;
            |        @reach(\none) let ha__0;
            |        ha__0 = 2
            |
            |        ```,
            |  },
            |}
        """.trimMargin(),
    )

    @Test
    fun blockLambdaEndToEnd() = assertModuleAtStage(
        stage = Stage.Run,
        moduleResultNeeded = true,
        input = """
            |let callIt(f: fn (x: Int): Int): Int { f(1) }
            |
            |callIt { (x: Int): Int extends Function =>
            |  let y = 41;
            |  x + y
            |}
        """.trimMargin(),
        want = """
            |{
            |  run: "42: Int32"
            |}
        """.trimMargin(),

    )

    @Test
    fun generatorInterpreted() = assertModuleAtStage(
        stage = Stage.Run,
        input = """
            |do {
            |  // Is thrice still a word?
            |  let runItThrice(factory: fn (): SafeGenerator<Empty>): Void {
            |    let generator: SafeGenerator<Empty> = factory();
            |    generator.next();
            |    console.log(",");
            |    generator.next();
            |    console.log(",");
            |    generator.next();
            |    console.log(".");
            |  }
            |
            |  runItThrice { (): GeneratorResult<Empty> extends GeneratorFn =>
            |    console.log("First");
            |    yield;
            |    console.log("Second");
            |    yield;
            |    console.log("Third");
            |    yield;
            |    // Not actually reached by runItThrice
            |    console.log("Fourth");
            |  }
            |}
        """.trimMargin(),
        want = """
            |{
            |  run: "void: Void",
            |  type: {
            |    body: ```
            |    let console#0;
            |    console#0 = getConsole();
            |    @fn let runItThrice__0;
            |    runItThrice__0 = fn runItThrice(factory__0 /* aka factory */: (fn (): SafeGenerator<Empty>)) /* return__0 */: Void {
            |      void;
            |      fn__0: do {
            |        let generator__0: SafeGenerator<Empty>;
            |        generator__0 = factory__0();${
            "" // Since it's a SafeGenerator, no error checking around do_bind_next(...)()
        }
            |        do_bind_next(generator__0)();
            |        do_bind_log(console#0)(",");
            |        do_bind_next(generator__0)();
            |        do_bind_log(console#0)(",");
            |        do_bind_next(generator__0)();
            |        do_bind_log(console#0)(".");
            |        return__0 = void
            |      }
            |    };
            |    runItThrice__0(fn /* return__1 */{${
            "" // Adapt call specialized to adaptGeneratorFnSafe
        }
            |        return__1 = adaptGeneratorFnSafe(@wrappedGeneratorFn fn /* return__2 */: (GeneratorResult<Empty>) implements GeneratorFn {
            |            do_bind_log(console#0)("First");
            |            yield();
            |            do_bind_log(console#0)("Second");
            |            yield();
            |            do_bind_log(console#0)("Third");
            |            yield();
            |            do_bind_log(console#0)("Fourth");
            |            return__2 = implicits.doneResult<Empty>()
            |        })
            |    });
            |
            |    ```
            |  },
            |  stdout: ```
            |    First
            |    ,
            |    Second
            |    ,
            |    Third
            |    .
            |
            |    ```
            |}
        """.trimMargin(),
    )

    @Test
    fun generatorInterpretedInLoop() = assertModuleAtStage(
        stage = Stage.Run,
        input = """
            |do {
            |  // Is thrice still a word?
            |  let runItThrice(factory: fn (): SafeGenerator<Empty>): Void {
            |    let generator: SafeGenerator<Empty> = factory();
            |    generator.next();
            |    console.log("Ran once");
            |    generator.next();
            |    console.log("Ran twice");
            |    generator.next();
            |    console.log("Ran thrice");
            |    generator.close();
            |  }
            |
            |  runItThrice { (): GeneratorResult<Empty> extends GeneratorFn =>
            |    while (true) {
            |      console.log("Pausing");
            |      yield;
            |      console.log("Resuming");
            |    }
            |  }
            |}
        """.trimMargin(),
        want = """
            |{
            |  run: "void: Void",
            |  type: {
            |    body: ```
            |    let console#0;
            |    console#0 = getConsole();
            |    @fn let runItThrice__0;
            |    runItThrice__0 = fn runItThrice(factory__0 /* aka factory */: (fn (): SafeGenerator<Empty>)) /* return__0 */: Void {
            |      void;
            |      fn__0: do {
            |        let generator__0: SafeGenerator<Empty>;
            |        generator__0 = factory__0();
            |## Since it's a SafeGenerator, no error checking around do_bind_next(...)()
            |        do_bind_next(generator__0)();
            |        do_bind_log(console#0)("Ran once");
            |        do_bind_next(generator__0)();
            |        do_bind_log(console#0)("Ran twice");
            |        do_bind_next(generator__0)();
            |        do_bind_log(console#0)("Ran thrice");
            |        do_bind_close(generator__0)();
            |        return__0 = void
            |      }
            |    };
            |    runItThrice__0(fn /* return__1 */{
            |## Adapt call specialized to adaptGeneratorFnSafe
            |        return__1 = adaptGeneratorFnSafe(@wrappedGeneratorFn fn /* return__2 */: (GeneratorResult<Empty>) implements GeneratorFn {
            |            return__2 = implicits.doneResult<Empty>();
            |## The interpreter needs to distinguish a legit return result with the result from a yield.
            |            void;
            |            while (true) {
            |              do_bind_log(console#0)("Pausing");
            |              yield();
            |              do_bind_log(console#0)("Resuming");
            |            }
            |        })
            |    });
            |
            |    ```
            |  },
            |  stdout: ```
            |    Pausing
            |    Ran once
            |    Resuming
            |    Pausing
            |    Ran twice
            |    Resuming
            |    Pausing
            |    Ran thrice
            |
            |    ```
            |}
        """.trimMargin().stripDoubleHashCommentLinesToPutCommentsInlineBelow(),
    )

    @Ignore
    @Test
    fun generatorResultsUsed() = assertModuleAtStage(
        stage = Stage.Run,
        input = $$"""
            |do {
            |  let adNauseam(factory: fn (): SafeGenerator<Int>): Void {
            |    let generator: SafeGenerator<Int> = factory();
            |    while (true) {
            |      let x = generator.next();
            |      when (x) {
            |        is DoneResult<Int> -> break;
            |        is ValueResult<Int> -> console.log("Received ${ x.value.toString() }");
            |      }
            |    }
            |    generator.close();
            |    console.log("Done");
            |  }
            |
            |  adNauseam { (): GeneratorResult<Int> extends GeneratorFn =>
            |    yield 1;
            |    yield 2;
            |  }
            |}
        """.trimMargin(),
        want = """
            |{
            |  run: "void: Void",
            |  stdout: ```
            |    Received 1
            |    Received 2
            |    Done
            |
            |    ```
            |}
        """.trimMargin(),
    )

    @Test
    fun forOfExample() = assertModuleAtStage(
        stage = Stage.Run,
        input = """
            |for (let i of [0, 1, 2, 3, 4, 5, 6, 7, 8, 9]) {
            |  if (i & 1 == 0) { continue }
            |  if (i == 7) { break }
            |  console.log(i.toString());
            |}
        """.trimMargin(),
        want = """
            |{
            |  run: "void: Void",
            |  stdout: ```
            |    1
            |    3
            |    5
            |
            |    ```
            |}
        """.trimMargin(),
    )

    @Test
    fun awaiting() = assertModuleAtStage(
        stage = Stage.Run,
        stagingFlags = setOf(StagingFlags.allowTopLevelAwait),
        input = """
            |let pb = new PromiseBuilder<String>();
            |let p = pb.promise;
            |async { (): GeneratorResult<Empty> extends GeneratorFn =>
            |  pb.complete("Hello, World!");
            |}
            |console.log(await p);
        """.trimMargin(),
        want = """
            |{
            |  run: "void: Void",
            |  stdout: ```
            |    Hello, World!
            |
            |    ```,
            |  generateCode: {
            |    body: ```
            |      var t#0, t#1, fail#0;
            |      t#0 = getConsole();
            |      let pb__0;
            |      pb__0 = new PromiseBuilder<String>();
            |      let p__0;
            |      p__0 = do_get_promise(pb__0);
            |      let fn__0;
            |      fn__0 = (@stay fn /* return__0 */{
            |          let fn__1;
            |          fn__1 = (@wrappedGeneratorFn fn /* return__1 */: (GeneratorResult<Empty>) implements GeneratorFn {
            |              do_bind_complete(pb__0)("Hello, World!");
            |              return__1 = (fn doneResult)<Empty>()
            |          });
            |          return__0 = adaptGeneratorFnSafe(fn__1)
            |      });
            |      async(fn__0);
            |      t#1 = hs(fail#0, await p__0);
            |      if (fail#0) {
            |        bubble()
            |      };
            |      do_bind_log(t#0)(t#1)
            |
            |      ```
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun invalidRtti() = assertModuleAtStage(
        stage = Stage.Run,
        // Check that is T and as T only operate
        // on types that can be distinguished at runtime.
        input = """
            |class UnconnectedUserType {}
            |
            |let f<T>(
            |  a: AnyValue,
            |  s: String,
            |  son: String?,
            |  i: Int,
            |  ion: Int?,
            |  f: Float64,
            |  fon: Float64?,
            |  b: Boolean,
            |  bon: Boolean?,
            |  n: Never<Int>?,
            |  k: MapKey,
            |): Void throws Bubble {
            |  // Illegal.  Multiple other types could connect to target language string type
            |  a as String orelse do {};
            |  a as Int orelse do {};
            |  a as Boolean orelse do {};
            |  k as String orelse do {};
            |  // Illegal, type formals can't be cast targets.
            |  a as T orelse do {};
            |  // Does nothing.
            |  s as String orelse do {};
            |  b as Boolean orelse do {};
            |  i as Int orelse do {};
            |  bon as Boolean?;
            |  n as Never<Int32>? orelse do {};
            |  // Ok.  Can always check nullity
            |  a as Never<AnyValue>? orelse do {};
            |  son as Never<String>? orelse do {};
            |  ion as Never<Int32>? orelse do {};
            |  fon as Never<Float64>? orelse do {};
            |  bon as Never<Boolean>? orelse do {};
            |  // Types are statically disjoint
            |  s as Int orelse do {};
            |  i as String orelse do {};
            |  b as Never<Boolean>? orelse do {};
            |  n as Int orelse do {};
            |  k as Float64 orelse do {};
            |  k as Int orelse do {};
            |  // ok to unconnected class type.
            |  a as UnconnectedUserType orelse do {};
            |
            |  // TODO: `is` equivalents of some of the above
            |}
        """.trimMargin(),
        logEntryWanted = {
            it.level >= Log.Warn ||
                // This is a low level message, but it's specific to these checks.
                it.template == MessageTemplate.UnnecessaryRttiCheck
        },
        want = """
            |{
            |  run: "void: Void",
            |  errors: [
            |    // a as String;
            |    "Types marked @mayDowncastTo(false) cannot be targeted with is or as runtime type checks because they may not be distinct on all backends: <[String]> from AnyValue!",
            |    // a as Int;
            |    "Types marked @mayDowncastTo(false) cannot be targeted with is or as runtime type checks because they may not be distinct on all backends: <[Int32]> from AnyValue!",
            |    // a as Boolean;
            |    "Types marked @mayDowncastTo(false) cannot be targeted with is or as runtime type checks because they may not be distinct on all backends: <[Boolean]> from AnyValue!",
            |    // k as String;
            |    "Types marked @mayDowncastTo(false) cannot be targeted with is or as runtime type checks because they may not be distinct on all backends: <[String]> from MapKey!",
            |    // a as T;
            |    "Type parameters cannot be targeted with is or as runtime type checks: <[T__1]> from AnyValue!",
            |    // s as String;
            |    "Unnecessary type check to String from expression with type String which is a subtype",
            |    // b as Boolean;
            |    "Unnecessary type check to Boolean from expression with type Boolean which is a subtype",
            |    // i as Int;
            |    "Unnecessary type check to Int32 from expression with type Int32 which is a subtype",
            |    // s as Int;
            |    "Runtime type check from String to Int32 can never succeed!",
            |    // i as String;
            |    "Runtime type check from Int32 to String can never succeed!",
            |    // n as Int;
            |    "Runtime type check from Null to Int32 can never succeed!",
            |    // k as Float64;
            |    "Unrelated types cannot be targeted with is or as runtime type checks: <[Float64]> from MapKey!",
            |    // k as Int;
            |    "Types marked @mayDowncastTo(false) cannot be targeted with is or as runtime type checks because they may not be distinct on all backends: <[Int32]> from MapKey!",
            |  ]
            |}
        """.trimMargin(),
    )

    @Test
    fun invalidRttiTypeArgs() = assertModuleAtStage(
        stage = Stage.Run,
        input = """
            |interface Sup<T> {}
            |class Sub<T> extends Sup<T> {}
            |interface Sup2<T, U> extends Sup<U> {}
            |class Sub2<T> extends Sup2<T, String> {} // sneak swap the meaning of T
            |class Sub3<T> extends Sup2<String, T> {} // weaves T through
            |class Sub4 extends Sup<String> {}
            |let badCast(value: AnyValue): Sub<String> throws Bubble {
            |  // Introduces String.
            |  value as Sub<String>
            |}
            |let alsoBad<T>(value: Sup<T>): Sub<String> throws Bubble {
            |  // Presumes known type arg for T.
            |  value as Sub<String>
            |}
            |let goodCast(value: Sup<String>): Sub<String> throws Bubble {
            |  // Keeps the known type arg.
            |  value as Sub<String>
            |}
            |let alsoGood<T>(value: Sup<T>): Sub<T> throws Bubble {
            |  // Also keeps the known type arg, which is also a type param.
            |  value as Sub<T>
            |}
            |let butThisIsBad<T>(value: Sup<T>): Sub2<T> throws Bubble {
            |  // The T args here aren't actually related. Presumes String as an arg for U.
            |  value as Sub2<T>
            |}
            |let alsoBadBecauseExtra<T, U>(value: Sup<U>): Sup2<T, U> throws Bubble {
            |  // Introduces T.
            |  value as Sup2<T, U>
            |}
            |let butThisIsGood<U>(value: Sup<U>): Sup2<U, U> throws Bubble {
            |  // Uses known U for both cases.
            |  value as Sup2<U, U>
            |}
            |let goodDespiteMiddle<T>(value: Sup<T>): Sub3<T> throws Bubble {
            |  // Invents String for Sup2 T, but that doesn't matter because it's not represented.
            |  value as Sub3<T>
            |}
            |let badNonGeneric<T>(value: Sup<T>): Sub4 throws Bubble {
            |  // Invents String for Sup T without any generics in Sub4 at all.
            |  value as Sub4
            |}
        """.trimMargin(),
        want = """
            |{
            |  run: "void: Void",
            |  errors: [
            |    "Type arguments cannot be introduced with is or as runtime type checks: <[Sub__0<String>]> from AnyValue!",
            |    "Unrelated types cannot be targeted with is or as runtime type checks: <[Sub__0<String>]> from Sup__0<T__0>!",
            |    "Unrelated types cannot be targeted with is or as runtime type checks: <[Sub2__0<T__1>]> from Sup__0<T__1>!",
            |    "Type arguments cannot be introduced with is or as runtime type checks: <[Sup2__0<T__2, U__0>]> from Sup__0<U__0>!",
            |    "Unrelated types cannot be targeted with is or as runtime type checks: <[Sub4__0]> from Sup__0<T__3>!"
            |  ]
            |}
        """.trimMargin(),
    )

    @Test
    fun invalidRttiNotInlined() = assertModuleAtStage(
        stage = Stage.GenerateCode,
        // Check that is T and as T that would be invalid
        // if translated aren't inlined.
        input = """
            |let s: AnyValue = "str";
            |s is String
        """.trimMargin(),
        moduleResultNeeded = true,
        logEntryWanted = {
            // UnnecessaryRttiCheck is low level, but relevant inside a REPL.
            it.level >= Log.Warn || it.template == MessageTemplate.UnnecessaryRttiCheck
        },
        want = """
            |{
            |  generateCode: {
            |    body: ```
            |      let return__0, @reach(\none) s__0: AnyValue;
            |      s__0 = "str";
            |      return__0 = is("str", String)
            |
            |      ```
            |  },
            |  errors: [
            |    "Unnecessary type check to String from expression with type String which is a subtype"
            |  ]
            |}
        """.trimMargin(),
    )

    @Test
    fun upcastOk() = assertModuleAtStage(
        stage = Stage.Run,
        // Use Map here because that was the original motivating example, even though it's not vital to the test.
        input = """
            |interface A {}
            |class B extends A {}
            |class C extends A {}
            |// Basic and upcast are fine. No warnings.
            |let bVals = new Map([new Pair("a", new B())]);
            |let aVals = new Map([new Pair("a", new B() as A)]);
            |// Samecast should still get a warning.
            |let bbVals = new Map([new Pair("a", new B() as B)]);
            |// Upcheck should also get a warning. Here we use a different subtype for clear message distinction.
            |let isSub = new C() is A;
        """.trimMargin(),
        logEntryWanted = {
            // UnnecessaryRttiCheck is low level, but relevant inside a REPL.
            it.level >= Log.Warn || it.template == MessageTemplate.UnnecessaryRttiCheck
        },
        // Key focus being no errors here.
        want = """
            |{
            |  run: "void: Void",
            |  errors: [
            |    "Unnecessary type check to B__0 from expression with type B__0 which is a subtype",
            |    "Unnecessary type check to A__0 from expression with type C__2 which is a subtype",
            |  ],
            |}
        """.trimMargin(),
    )

    @Test
    fun castAwayNullWorksAtRuntime() = assertModuleAtStage(
        stage = Stage.Run,
        input = $$"""
            |let f(x: Float64?): Float64? { (x as Float64) orelse null }
            |console.log("f(1.0) = ${ f(1.0) }");
        """.trimMargin(),
        want = """
            |{
            |  run: "void: Void",
            |  stdout: "f(1.0) = 1.0\n",
            |}
        """.trimMargin(),
    )

    @Test
    fun matchWithCharExprCases() = assertModuleAtStage(
        stage = Stage.GenerateCode,
        input = """
            |let abcStop(i: Int): String {
            |  when (i) {
            |    char 'a', char 'b', char 'c' -> "ok";
            |    else -> "stop";
            |  }
            |}
        """.trimMargin(),
        want = """
            |{
            |  generateCode: {
            |    body: ```
            |        @fn @reach(\none) let abcStop__0;
            |        abcStop__0 = (@stay fn abcStop(i__0 /* aka i */: Int32) /* return__0 */: String {
            |            var t#0, t#1;
            |            if (i__0 == 97) {
            |              t#1 = true
            |            } else {
            |              if (i__0 == 98) {
            |                t#0 = true
            |              } else {
            |                t#0 = i__0 == 99
            |              };
            |              t#1 = t#0
            |            };
            |            if (t#1) {
            |              return__0 = "ok"
            |            } else {
            |              return__0 = "stop"
            |            }
            |        })
            |
            |        ```
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun sealedConnectedCasts() = assertModuleAtStage(
        // comments in the cast checker describe why this is the way it is.
        // In short, a sealed, connected type must be able to distinguish
        // its subtypes, so the static expression type matters when casting.
        stage = Stage.Run,
        provisionModule = { module, _ ->
            module.addEnvironmentBindings(
                mapOf(connectedDecoratorName to vConnectedDecorator),
            )
            module.deliverContent(
                ModuleSource(
                    filePath = testCodeLocation,
                    fetchedContent = """
                        |@connected("S")
                        |export sealed interface S {}
                        |
                        |@connected("C")
                        |class C extends S {}
                        |@connected("D")
                        |class D extends S {}
                        |
                        |@connected("NS")
                        |interface NS extends S {}
                        |@connected("E")
                        |class E extends NS {}
                        |
                        |export let f(a: AnyValue, s: S): Void throws Bubble {
                        |  a as C;  // BAD: C is connected, and AnyValue is not.
                        |  s as C;  // OK.  C is a sub-type of S
                        |  s as E;  // BAD. E is a sub-type of S, but only via NS which is not-sealed.
                        |}
                    """.trimMargin(),
                    languageConfig = StandaloneLanguageConfig,
                ),
            )
        },
        want = """
            |{
            |  run: "void: Void",
            |  errors: [
            |    "Connected types cannot be targeted with is or as runtime type checks because multiple Temper types are allowed to connect to the same backend type: <[C__1]> from AnyValue!",
            |    "Connected types cannot be targeted with is or as runtime type checks because multiple Temper types are allowed to connect to the same backend type: <[E__4]> from S!",
            |  ],
            |}
        """.trimMargin(),
    )

    @Test
    fun stringNullEquality() = assertModuleAtStage(
        input = """
            |let f(s: String?): Boolean { s == null }
            |
            |!f("") && f(null)
        """.trimMargin(),
        stage = Stage.Run,
        moduleResultNeeded = true,
        want = """
            |{
            |  run: "true: Boolean",
            |}
        """.trimMargin(),
    )

    @Test
    fun asAndIsSimplification1() = assertModuleAtStage(
        input = """
            |let f(i: StringIndexOption?): Int throws Bubble {
            |  if (i is StringIndex) {
            |    0
            |  } else {
            |    1
            |  }
            |}
        """.trimMargin(),
        stage = Stage.GenerateCode,
        want = """
            |{
            |  generateCode: {
            |    body: ```
            |      @fn @reach(\none) let f__0;
            |      f__0 = (@stay fn f(i__0 /* aka i */: StringIndexOption?) /* return__0 */: (Int32 | Bubble) {
            |          var t#0;
            |          if (!isNull(i__0)) {
            |            t#0 = is(i__0, StringIndex)
            |          } else {
            |            t#0 = false
            |          };
            |          if (t#0) {
            |            return__0 = 0
            |          } else {
            |            return__0 = 1
            |          }
            |      })
            |
            |      ```
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun asAndIsSimplification2() = assertModuleAtStage(
        input = """
            |let f(i: StringIndexOption?): Int throws Bubble {
            |  if (i is StringIndexOption) {
            |    0
            |  } else {
            |    1
            |  }
            |}
        """.trimMargin(),
        stage = Stage.GenerateCode,
        want = """
            |{
            |  generateCode: {
            |    body: ```
            |      @fn @reach(\none) let f__0;
            |      f__0 = (@stay fn f(i__0 /* aka i */: StringIndexOption?) /* return__0 */: (Int32 | Bubble) {
            |          var t#0;
            |          if (!isNull(i__0)) {
            |            t#0 = is(i__0, StringIndexOption)
            |          } else {
            |            t#0 = false
            |          };
            |          if (t#0) {
            |            return__0 = 0
            |          } else {
            |            return__0 = 1
            |          }
            |      })
            |
            |      ```
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun asAndIsSimplification3() = assertModuleAtStage(
        input = """
            |let f(i: StringIndexOption?): Int throws Bubble {
            |  do {
            |    let j = i as StringIndex?;
            |    0
            |  } orelse 1
            |}
        """.trimMargin(),
        stage = Stage.GenerateCode,
        want = """
            |{
            |  generateCode: {
            |    body: ```
            |      @fn @reach(\none) let f__0;
            |      f__0 = (@stay fn f(i__0 /* aka i */: StringIndexOption?) /* return__0 */: (Int32 | Bubble) {
            |          var fail#0;
            |          orelse#0: {
            |            let j__0;
            |            if (isNull(i__0)) {
            |              j__0 = null
            |            } else {
            |              j__0 = hs(fail#0, as(i__0, StringIndex));
            |              if (fail#0) {
            |                break orelse#0;
            |              }
            |            };
            |            return__0 = 0
            |          } orelse {
            |            return__0 = 1
            |          }
            |      })
            |
            |      ```
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun asAndIsSimplification4() = assertModuleAtStage(
        input = """
            |let f(i: StringIndexOption?): Int throws Bubble {
            |  if (i is StringIndex?) {
            |    let j = i as StringIndex?;
            |    if (j is StringIndex) {
            |      1
            |    } else {
            |      2
            |    }
            |  } else {
            |    let n = i as Never<StringIndexOption>?;
            |    3
            |  }
            |}
        """.trimMargin(),
        stage = Stage.GenerateCode,
        want = """
            |{
            |  generateCode: {
            |    body: ```
            |      @fn @reach(\none) let f__0;
            |      f__0 = (@stay fn f(i__0 /* aka i */: StringIndexOption?) /* return__0 */: (Int32 | Bubble) {
            |          var t#0, t#1, t#2, t#3, fail#0;
            |          if (isNull(i__0)) {
            |            t#0 = true
            |          } else {
            |            t#0 = is(i__0, StringIndex)
            |          };
            |          if (t#0) {
            |            if (isNull(i__0)) {
            |              t#3 = null
            |            } else {
            |              t#3 = assertAs(i__0, StringIndex)
            |            };
            |            let j__0;
            |            if (isNull(t#3)) {
            |              j__0 = null
            |            } else {
            |              t#2 = hs(fail#0, as(t#3, StringIndex));
            |              if (fail#0) {
            |                bubble()
            |              };
            |              j__0 = t#2
            |            };
            |            if (!isNull(j__0)) {
            |              t#1 = is(j__0, StringIndex)
            |            } else {
            |              t#1 = false
            |            };
            |            if (t#1) {
            |              return__0 = 1
            |            } else {
            |              return__0 = 2
            |            }
            |          } else {
            |            let n__0;
            |            if (!isNull(i__0)) {
            |              bubble()
            |            };
            |            n__0 = null;
            |            return__0 = 3
            |          }
            |      })
            |
            |      ```
            |  }
            |}
        """.trimMargin(),
    )

    // Complex expressions caught in temporary
    @Test
    fun asAndIsSimplification5() = assertModuleAtStage(
        input = """
            |let g(s: String): StringIndexOption {
            |  s.end
            |}
            |let f(s: String): Boolean {
            |  g(s) is NoStringIndex
            |}
        """.trimMargin(),
        stage = Stage.GenerateCode,
        want = """
            |{
            |  generateCode: {
            |    body: ```
            |      @fn @reach(\none) let g__0, @fn @reach(\none) f__0;
            |      g__0 = (@stay fn g(s__0 /* aka s */: String) /* return__0 */: StringIndexOption {
            |          return__0 = do_get_end(s__0)
            |      });
            |      f__0 = (@stay fn f(s__1 /* aka s */: String) /* return__1 */: Boolean {
            |          return__1 = is((fn g)(s__1), NoStringIndex)
            |      })
            |
            |      ```
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun nullSimplification() = assertModuleAtStage(
        input = """
            |let f(s: String?): Boolean {
            |  s == null
            |}
            |let g(s: String?): Boolean {
            |  s != null
            |}
        """.trimMargin(),
        stage = Stage.GenerateCode,
        want = """
            |{
            |  generateCode: {
            |    body: ```
            |      @fn @reach(\none) let f__0, @fn @reach(\none) g__0;
            |      f__0 = (@stay fn f(s__0 /* aka s */: String?) /* return__0 */: Boolean {
            |          return__0 = isNull(s__0)
            |      });
            |      g__0 = (@stay fn g(s__1 /* aka s */: String?) /* return__1 */: Boolean {
            |          return__1 = !isNull(s__1)
            |      })
            |
            |      ```
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun sneakyBubble() = assertModuleAtStage(
        stage = Stage.GenerateCode,
        input = """
            |class Something(public let haha: Int?) {}
        """.trimMargin(),
        want = """
            |{
            |  generateCode: {
            |    body: ```
            |      @constructorProperty @visibility(\public) @stay @fromType(Something__0) @reach(\none) let haha__0: Int32?;
            |      @fn @visibility(\public) @stay @fromType(Something__0) @reach(\none) let constructor__0;
            |      constructor__0 = (@stay fn constructor(@impliedThis(Something__0) this__0: Something__0, haha__1 /* aka haha */: Int32?) /* return__0 */: Void {
            |          setp(haha__0, this__0, haha__1);
            |          return__0 = void
            |      });
            |      @fn @visibility(\public) @stay @fromType(Something__0) @reach(\none) let gethaha__0;
            |      gethaha__0 = (@stay fn (@impliedThis(Something__0) this__1: Something__0) /* return__1 */: (Int32?) {
            |          return__1 = getp(haha__0, this__1)
            |      });
            |      @typeDecl(Something__0) @stay @reach(\none) let Something__0;
            |      Something__0 = type (Something__0)
            |
            |      ```
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun bubbleOrElseNot() = assertModuleAtStage(
        stage = Stage.GenerateCode,
        // Explore bubbles both escaping and captured, both explicit and implicit, both builtin and user functions.
        // Just making sure to explore the space of how we handle things.
        input = """
            |let other(i: Int): Int throws Bubble {
            |  if (i % 2 == 0) {
            |    // Bubble allowed above.
            |    bubble()
            |  } else {
            |    i
            |  }
            |}
            |let something(nums: Map<Int, Int>, index: Int): Int {
            |  // No `| Bubble` above, so bubblies should error.
            |  if (index < 0) {
            |    bubble()
            |  } else if (index == 0) {
            |    other(index)
            |  } else if (index == 1) {
            |    nums[index]
            |  } else {
            |    do {
            |      if (index < nums[index]) {
            |        index + 1
            |      } else if (index > 10) {
            |        other(index + 1)
            |      } else {
            |        bubble()
            |      }
            |    } orelse index
            |  }
            |}
        """.trimMargin(),
        want = """
            |{
            |  generateCode: {
            |    body: ```
            |      @fn @reach(\none) let other__0, @fn @reach(\none) something__0;
            |      other__0 = (@stay fn other(i__0 /* aka i */: Int32) /* return__0 */: (Int32 | Bubble) {
            |          if (i__0 % 2 == 0) {
            |            bubble()
            |          };
            |          return__0 = i__0
            |      });
            |      something__0 = (@stay fn something(nums__0 /* aka nums */: Map<Int32, Int32>, index__0 /* aka index */: Int32) /* return__1 */: Int32 {
            |          var t#0, t#1, fail#0, fail#1, fail#2, fail#3;
            |          if (index__0 < 0) {
            |            bubble()
            |          } else if (index__0 == 0) {
            |            return__1 = hs(fail#0, (fn other)(index__0));
            |            if (fail#0) {
            |              bubble()
            |            }
            |          } else if (index__0 == 1) {
            |            return__1 = hs(fail#1, do_bind_get(nums__0)(index__0));
            |            if (fail#1) {
            |              bubble()
            |            }
            |          } else {
            |            orelse#0: {
            |              t#0 = hs(fail#2, do_bind_get(nums__0)(index__0));
            |              if (fail#2) {
            |                break orelse#0;
            |              };
            |              if (index__0 < t#0) {
            |                return__1 = index__0 + 1
            |              } else if (index__0 > 10) {
            |                t#1 = hs(fail#3, (fn other)(index__0 + 1));
            |                if (fail#3) {
            |                  break orelse#0;
            |                };
            |                return__1 = t#1
            |              } else {
            |                break orelse#0;
            |              }
            |            } orelse {
            |              return__1 = index__0
            |            }
            |          }
            |      })
            |
            |      ```
            |  },
            |  errors: [
            |    // Only for the 3 cases that actually bubble.
            |    "Cannot bubble from a function without Bubble in its return type!",
            |    "Cannot bubble from a function without Bubble in its return type!",
            |    "Cannot bubble from a function without Bubble in its return type!",
            |  ],
            |}
        """.trimMargin(),
    )

    @Test
    fun extensionMethodUse() = assertModuleAtStage(
        stage = Stage.Run,
        moduleResultNeeded = true,
        input = """
            |@extension("isPalindrome")
            |let stringIsPalindrome(s: String): Boolean {
            |  var i = String.begin;
            |  var j = s.end;
            |  while (i < j) {
            |    j = s.prev(j);
            |    if (s[i] != s[j]) { return false }
            |    i = s.next(i);
            |  }
            |  return true
            |}
            |"step on no pets".isPalindrome()
        """.trimMargin(),
        want = """
            |{
            |  define: {
            |    body: ```
            |      @fn @extension("isPalindrome") let stringIsPalindrome__0;
            |      stringIsPalindrome__0 = fn stringIsPalindrome(s__0 /* aka s */: String) /* return__0 */: Boolean {
            |        fn__0: do {
            |          var i__0;
            |          i__0 = getStatic(String, \begin);
            |          var j__0;
            |          j__0 = do_get_end(s__0);
            |          while(i__0 < j__0, fn {
            |              j__0 = do_bind_prev(s__0)(j__0);
            |              if(do_bind_get(s__0)(i__0) != do_bind_get(s__0)(j__0), fn {
            |                  do {
            |                    return__0 = false;
            |                    break(\label, fn__0)
            |                  }
            |              });
            |              i__0 = do_bind_next(s__0)(i__0);
            |          });
            |          do {
            |            return__0 = true;
            |            break(\label, fn__0)
            |          }
            |        }
            |      };
            |      (do_bind_isPalindrome[stringIsPalindrome__0])("step on no pets")()
            |
            |      ```
            |  },
            |  type: {
            |    body: ```
            |      let return__1, @fn @extension("isPalindrome") stringIsPalindrome__0;
            |      stringIsPalindrome__0 = fn stringIsPalindrome(s__0 /* aka s */: String) /* return__0 */: Boolean {
            |        var t#0, t#1, t#2;
            |        fn__0: do {
            |          var i__0;
            |          i__0 = getStatic(String, \begin);
            |          var j__0;
            |          t#0 = do_get_end(s__0);
            |          j__0 = t#0;
            |          while (i__0 < j__0) {
            |            t#1 = do_bind_prev(s__0)(j__0);
            |            j__0 = t#1;
            |            if (do_bind_get(s__0)(i__0) != do_bind_get(s__0)(j__0)) {
            |              return__0 = false;
            |              break fn__0;
            |            };
            |            t#2 = do_bind_next(s__0)(i__0);
            |            i__0 = t#2
            |          };
            |          return__0 = true
            |        }
            |      };
            |      return__1 = stringIsPalindrome__0("step on no pets");${
            "" // The do_call_isPalindrome got rewritten to the direct function reference
        }
            |
            |      ```
            |  },
            |  run: "true: Boolean"
            |}
        """.trimMargin(),
    )

    @Test
    fun jsonAdaptorWorks() = assertModuleAtStage(
        stage = Stage.Run,
        moduleResultNeeded = true,
        input = """
            |@json class C {}
            |C.jsonAdapter()
        """.trimMargin(),
        want = """
            |{
            |  run: "{}: CJsonAdapter__0"
            |}
        """.trimMargin(),
    )

    @Test
    fun jsonAdapterEncodesSealedTypes() = assertModuleAtStage(
        stage = Stage.Run,
        moduleResultNeeded = true,
        input = """
            |let {
            |  JsonTextProducer,
            |  listJsonAdapter,
            |} = import("std/json");
            |
            |@json sealed interface Animal {}
            |@json class Cat(public meowCount: Int)       extends Animal {}
            |@json class Dog(public hydrantsSniffed: Int) extends Animal {}
            |
            |let ls: List<Animal> = [new Cat(11), new Dog(111)];
            |
            |let p = new JsonTextProducer();
            |List.jsonAdapter(Animal.jsonAdapter()).encodeToJson(ls, p);
            |p.toJsonString()
        """.trimMargin(),
        want = """
            |{
            |  run: "\"[{\\\"meowCount\\\":11},{\\\"hydrantsSniffed\\\":111}]\": String"
            |}
        """.trimMargin(),
    )

    @Test
    fun jsonAdapterDecodesSealedTypes() = assertModuleAtStage(
        stage = Stage.Run,
        moduleResultNeeded = true,
        input = """
            |let {
            |  JsonTextProducer,
            |  listJsonAdapter,
            |  parseJson,
            |  NullInterchangeContext,
            |} = import("std/json");
            |
            |@json sealed interface Animal {}
            |@json class Cat(public meowCount: Int) extends Animal {}
            |@json class Dog(public hydrantsSniffed: Int) extends Animal {}
            |
            |let t = parseJson(
            |  ${"\"\"\""}
            |  "[
            |  "  { "meowCount": 137 },
            |  "  { "hydrantsSniffed": 1337 }
            |  "]
            |);
            |
            |List.jsonAdapter(Animal.jsonAdapter()).decodeFromJson(t, NullInterchangeContext.instance)
        """.trimMargin(),
        want = """
            |{
            |  run: "[{meowCount: 137}, {hydrantsSniffed: 1337}]: List"
            |}
        """.trimMargin(),
    )

    @Test
    fun nullableJsonField() = assertModuleAtStage(
        stage = Stage.Run,
        moduleResultNeeded = true,
        input = """
            |let {
            |  NullInterchangeContext,
            |  OrNullJsonAdapter,
            |  booleanJsonAdapter,
            |  listJsonAdapter,
            |  parseJson,
            |} = import("std/json");
            |let a = List.jsonAdapter(new OrNullJsonAdapter(Boolean.jsonAdapter()));
            |
            |a.decodeFromJson(parseJson("[null, false, true]"), NullInterchangeContext.instance)
        """.trimMargin(),
        want = """
            |{
            |  run: "[null, false, true]: List",
            |}
        """.trimMargin(),
    )

    @Test
    fun jsonInteropForwardsTypeInfoForNullableProps() = assertModuleAtStage(
        stage = Stage.Run,
        moduleResultNeeded = true,
        input = """
            |let { NullInterchangeContext, parseJson } = import("std/json");
            |
            |@json class C(public i: Int?) {}
            |
            |C.jsonAdapter().decodeFromJson(parseJson('{"i": null}'), NullInterchangeContext.instance)
        """.trimMargin(),
        want = """
            |{
            |  run: "{i: null}: C__0"
            |}
        """.trimMargin(),
    )

    @Test
    fun explicitBoundedTypeParametersInInterpreter() = assertModuleAtStage(
        stage = Stage.Run,
        input = """
            |interface I { x: String }
            |
            |class C(public x: String) extends I {}
            |
            |let least<T extends I>(a: T?, b: T?): T? {
            |  if (a != null) {
            |    if (b != null) {
            |      if (a.x < b.x) { a } else { b }
            |    } else {
            |      a
            |    }
            |  } else {
            |    b
            |  }
            |}
            |
            |let c = least<C>({ x: "foo" }, { x: "bar" });
            |console.log(c?.x ?? "NULL");
        """.trimMargin(),
        want = """
            |{
            |  run: "void: Void",
            |  stdout: "bar\n"
            |}
        """.trimMargin(),
    )

    @Test
    fun multiImport() = assertModuleAtStage(
        input = """
            |let { ... } = import("./nums");
            |
            |console.log((a + b + c + d + e).toString());
            |
            |$TEST_INPUT_MODULE_BREAK ./nums/nums.temper
            |export let a = 1;
            |export let b = 2;
            |export let c = 3;
            |export let d = 4;
            |export let e = 5;
        """.trimMargin(),
        stage = Stage.GenerateCode,
        want = """
            |{
            |  generateCode: {
            |    body: ```
            |      @stay @imported(\(`test//nums/`.a)) @reach(\none) let a__0;
            |      a__0 = 1;
            |      @imported(\(`test//nums/`.b)) @reach(\none) let b__0;
            |      b__0 = 2;
            |      @imported(\(`test//nums/`.c)) @reach(\none) let c__0;
            |      c__0 = 3;
            |      @imported(\(`test//nums/`.d)) @reach(\none) let d__0;
            |      d__0 = 4;
            |      @imported(\(`test//nums/`.e)) @reach(\none) let e__0;
            |      e__0 = 5;
            |      do_bind_log(getConsole())(do_bind_toString(15)())
            |
            |      ```
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun nullInTestingAssert() = assertModuleAtStage(
        stage = Stage.GenerateCode,
        input = """
            |let { C } = import("./c");
            |
            |test("to be or not to be null") {
            |  let c0 = { optionalString: "" };
            |  assert(c0.optionalString == "");
            |}
            |
            |$TEST_INPUT_MODULE_BREAK ./c/c.temper
            |export class C(public optionalString: String?) {}
        """.trimMargin(),
        want = """
            |{
            |  generateCode: {
            |    body:
            |        ```
            |        @stay @imported(\(`test//c/`.C)) @reach(\test) let C__0;
            |        C__0 = type (C);
            |        @stay @imported(\(`std//testing/`.Test)) @reach(\test) let Test__0;
            |        Test__0 = type (Test);
            |        @fn @test("to be or not to be null") let toBeOrNotToBeNull__0;
            |        toBeOrNotToBeNull__0 = (@stay fn toBeOrNotToBeNull(test#0: Test) /* return__0 */: (Void | Bubble) {
            |            var t#0;
            |            let c0__0;
            |            c0__0 = new C("");
            |            let actual#0;
            |            actual#0 = do_get_optionalString(c0__0);
            |## Here's the assertion predicate
            |            t#0 = actual#0 == "";
            |## Here's a block that computes the failure message if the predicate is false.
            |            let fn__0;
            |            fn__0 = (@stay fn /* return__1 */{
            |                var t#1, t#2;
            |## Here we're picking a string representation of the actual expression result
            |                if (isNull(actual#0)) {
            |                  t#2 = "null"
            |                } else {
            |                  t#1 = do_bind_toString(notNull(actual#0))();
            |                  t#2 = t#1
            |                };
            |                return__1 = cat("expected c0.optionalString == (", "", ") not (", t#2, ")")
            |            });
            |            do_bind_assert(test#0)(t#0, fn__0);
            |            return__0 = void
            |        })
            |
            |        ```
            |  }
            |}
        """.trimMargin().stripDoubleHashCommentLinesToPutCommentsInlineBelow(),
    )

    @Test
    fun longNullChain() = assertModuleAtStage(
        stage = Stage.GenerateCode,
        input = """
            |let {a} = import("./a");
            |a?.string?.isEmpty?.toString() ?? "NULL"
            |
            |////!module: ./a/a.temper
            |export class A(public string: String) {}
            |
            |export let a: A? = new A("a");
        """.trimMargin(),
        moduleResultNeeded = true,
        want = """
            |{
            |  syntaxMacro: {
            |    body: ```
            |      @stay @imported(\(`test//a/`.a)) let a__0 = `test//a/`.a;
            |      {
            |        let subject#0;
            |        subject#0 = {
            |          let subject#1;
            |          subject#1 = {
            |            if (isNull(a__0)) {
            |              null
            |            } else {
            |              do_get_string(notNull(a__0))
            |            }
            |          };
            |          if (isNull(subject#1)) {
            |            null
            |          } else {
            |            do_get_isEmpty(notNull(subject#1))
            |          }
            |        };
            |        if (isNull(subject#0)) {
            |          null
            |        } else {
            |          do_bind_toString(notNull(subject#0))()
            |        }
            |      }
            |      ?? "NULL"
            |
            |      ```
            |  },
            |  generateCode: {
            |    body: ```
            |      var t#0, t#1, t#2;
            |      let return__0;
            |      var t#3, t#4, t#5;
            |      @stay @imported(\(`test//a/`.a)) let a__0;
            |      a__0 = `test//a/`.a;
            |      if (isNull(a__0)) {
            |        t#3 = null
            |      } else {
            |        t#0 = do_get_string(notNull(a__0));
            |        t#3 = t#0
            |      };
            |      if (isNull(t#3)) {
            |        t#4 = null
            |      } else {
            |        t#1 = do_get_isEmpty(notNull(t#3));
            |        t#4 = t#1
            |      };
            |      if (isNull(t#4)) {
            |        t#5 = null
            |      } else {
            |        t#2 = do_bind_toString(notNull(t#4))();
            |        t#5 = t#2
            |      };
            |      if (!isNull(t#5)) {
            |        return__0 = notNull(t#5)
            |      } else {
            |        return__0 = "NULL"
            |      }
            |
            |      ```
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun nonNullInference() = assertModuleAtStage(
        stage = Stage.GenerateCode,
        input = """
            |let maybeLength(a: String?): Int? {
            |  // Because of non-null inference, `a.end` is ok here.
            |  a?.countBetween(String.begin, a.end)
            |}
        """.trimMargin(),
        want = """
            |{
            |  generateCode: {
            |    body: ```
            |      @fn @reach(\none) let maybeLength__0;
            |      maybeLength__0 = (@stay fn maybeLength(a__0 /* aka a */: String?) /* return__0 */: (Int32?) {
            |          var t#0;
            |          if (isNull(a__0)) {
            |            return__0 = null
            |          } else {
            |## In this branch, a is aliased to a#0 and is known to be not null.
            |            let a#0;
            |            a#0 = notNull(a__0);
            |            t#0 = do_get_end(a#0);
            |            return__0 = do_bind_countBetween(a#0)(getStatic(String, \begin), t#0)
            |          }
            |      })
            |
            |      ```
            |  }
            |}
        """.trimMargin().stripDoubleHashCommentLinesToPutCommentsInlineBelow(),
    )

    @Test
    fun complexAssignmentOfVarProperty() = assertModuleAtStage(
        stage = Stage.Run,

        input = $$"""
            |let { IntBox } = import("./int-box/");
            |
            |let ib = { i: -1 };
            |console.log("ib.i = ${ib.i}");
            |ib.i += 11;
            |console.log("ib.i = ${ib.i}");
            |ib.i *= 9;
            |console.log("ib.i = ${ib.i}");
            |ib.i -= 6;
            |console.log("ib.i = ${ib.i}");
            |ib.i /= 2;
            |console.log("ib.i = ${ib.i}");
            |
            |$$TEST_INPUT_MODULE_BREAK ./int-box/int-box.temper
            |export class IntBox(public var i: Int) {}
        """.trimMargin(),

        want = """
            |{
            |  stdout: ```
            |    ib.i = -1
            |    ib.i = 10
            |    ib.i = 90
            |    ib.i = 84
            |    ib.i = 42
            |
            |    ```,
            |
            |  generateCode: {
            |    body: ```
            |      var t#0;
            |      @stay @imported(\(`test//int-box/`.IntBox)) let IntBox__0;
            |      IntBox__0 = type (IntBox);
            |      t#0 = getConsole();
            |      let ib__0;
            |      ib__0 = new IntBox(-1);
            |      do_bind_log(t#0)(cat("ib.i = ", do_bind_toString(do_get_i(ib__0))()));
            |      let t#1;
            |      t#1 = ib__0;
            |## set-i of get-i pattern
            |## TODO: this might be a good test case for improving temporary elimination.
            |      do_set_i(t#1, do_get_i(t#1) + 11);
            |      do_bind_log(t#0)(cat("ib.i = ", do_bind_toString(do_get_i(ib__0))()));
            |      let t#2;
            |      t#2 = ib__0;
            |      do_set_i(t#2, do_get_i(t#2) * 9);
            |      do_bind_log(t#0)(cat("ib.i = ", do_bind_toString(do_get_i(ib__0))()));
            |      let t#3;
            |      t#3 = ib__0;
            |      do_set_i(t#3, do_get_i(t#3) - 6);
            |      do_bind_log(t#0)(cat("ib.i = ", do_bind_toString(do_get_i(ib__0))()));
            |      let t#4;
            |      t#4 = ib__0;
            |      do_set_i(t#4, do_get_i(t#4) / 2);
            |      do_bind_log(t#0)(cat("ib.i = ", do_bind_toString(do_get_i(ib__0))()))
            |
            |      ```
            |  },
            |
            |  run: "void: Void",
            |}
        """.trimMargin().stripDoubleHashCommentLinesToPutCommentsInlineBelow(),
    )

    @Test
    fun complexAssignmentOfGetExpr() = assertModuleAtStage(
        stage = Stage.Run,

        input = $$"""
            |let ls = new ListBuilder<Int>();
            |ls.add(0);
            |ls.add(3);
            |ls[0] += 10;
            |ls[1] *= 2;
            |
            |console.log("ls = [${ls.toList().join(", ") { (i: Int): String => i.toString(10) }}]");
        """.trimMargin(),

        want = """
            |{
            |  stdout: ```
            |    ls = [10, 6]
            |
            |    ```,
            |  run: "void: Void",
            |
            |  syntaxMacro: {
            |    body: ```
            |      let console#0 = getConsole(), ls__0 = new ListBuilder<Int>();
            |      do_bind_add(ls__0)(0);
            |      do_bind_add(ls__0)(3);
            |      do {
            |        let t#0;
            |        t#0 = ls__0;
            |## Here's a call to .set of a call to .get
            |        do_bind_set(t#0)(0, do_bind_get(t#0)(0) + 10)
            |      };
            |      do {
            |        let t#1;
            |        t#1 = ls__0;
            |        do_bind_set(t#1)(1, do_bind_get(t#1)(1) * 2)
            |      };
            |      do_bind_log(console#0)(cat("ls = [", do_bind_join(do_bind_toList(ls__0)())(", ", fn (i__0 /* aka i */: Int) /* return__0 */: (String) {
            |              do_bind_toString(i__0)(10)
            |          }), "]"));
            |
            |      ```
            |  },
            |}
        """.trimMargin().stripDoubleHashCommentLinesToPutCommentsInlineBelow(),
    )

    @Test
    fun whenElseBubble() = assertModuleAtStage(
        stage = Stage.GenerateCode,
        pseudoCodeDetail = PseudoCodeDetail(showInferredTypes = true),
        input = """
            |export let something(x: String?): String throws Bubble {
            |  /** Silly */
            |  when (x) {
            |    is String -> x;
            |    else -> bubble();
            |  }
            |}
        """.trimMargin(),
        want = """
            |{
            |  generateCode: {
            |    body: ```
            |      @fn let `test//`.something ⦂(fn (String?): String | Bubble);
            |      `test//`.something = (@stay fn something(x__0 /* aka x */: String?) /* return__0 */: (String | Bubble) {
            |          var t#0 ⦂ Boolean;
            |          if (!isNull ⋖ String ⋗(x__0)) {
            |            t#0 = is(x__0, String)
            |          } else {
            |            t#0 = false
            |          };
            |          if (t#0) {
            |            if (isNull ⋖ String ⋗(x__0)) {
            |              return__0 = panic ⋖ String ⋗()
            |            } else {
            |              return__0 = assertAs ⋖ String ⋗(x__0, String)
            |            }
            |          } else {
            |            bubble ⋖ String ⋗()
            |          }
            |      })
            |
            |      ```,
            |    exports: {
            |      something: {
            |        stateVector: "fn something",
            |        typeTag: "Function",
            |        abbrev: "fn something: Function"
            |      }
            |    }
            |  }
            |}
        """.trimMargin(),
    )
}

// Provide an extra binding to a function whose call does not inline so does not trigger any
// we-don't-need-to-capture-this-in-a-temporary paths in the Weaver.
private val oneToThreeBindings = mapOf<TemperName, Value<*>>(
    BuiltinName("oneTwoThree") to Value(
        object : BuiltinStatelessCallableValue, NamedBuiltinFun {
            override val name: String = "oneTwoThree"
            override val sigs = listOf(
                Signature2(
                    returnType2 = WellKnownTypes.intType2,
                    hasThisFormal = false,
                    requiredInputTypes = emptyList(),
                ),
            )

            override fun invoke(
                args: ActualValues,
                cb: InterpreterCallback,
                interpMode: InterpMode,
            ) = Value(123, TInt)

            override val functionSpecies = FunctionSpecies.Normal
            override val callMayFailPerSe: Boolean = false
        },
    ),
)
