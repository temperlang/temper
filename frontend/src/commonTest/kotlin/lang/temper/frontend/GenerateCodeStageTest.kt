@file:Suppress("MaxLineLength")

package lang.temper.frontend

import lang.temper.common.Log
import lang.temper.env.InterpMode
import lang.temper.interp.MetadataDecorator
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
        stageTestDir = StageTestDir("generate-code/simple-do-nothing-loop"),
        stage = Stage.GenerateCode,
        want = """
            |{
            |    "type": {
            |        "body":
            |        ```
            |        var i__0;
            |        i__0 = 0;
            |        while (i__0 < 3) {
            |          i__0 = i__0 + 1;
            |        }
            |
            |        ```
            |    },
            |    "generateCode": {
            |        "body":
            |        ```
            |        var i__0;
            |        i__0 = 0;
            |        while (i__0 < 3) {
            |          i__0 = i__0 + 1
            |        }
            |
            |        ```
            |    }
            |}
        """.trimMargin(),
    )

    @Test
    fun sealedWhen() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/sealed-when"),
        stage = Stage.GenerateCode,
        pseudoCodeDetail = PseudoCodeDetail.default.copy(showInferredTypes = true),
        want = """
            |{
            |  generateCode: {
            |    body:
            |      ```
            |      @typeDecl(Geometric) @stay let `test//`.Geometric ⦂ Type;
            |      `test//`.Geometric = type (Geometric);
            |      @typeDecl(Ray) @stay let `test//`.Ray ⦂ Type;
            |      `test//`.Ray = type (Ray);
            |      @typeDecl(Shape) @stay @sealedType let `test//`.Shape ⦂ Type;
            |      `test//`.Shape = type (Shape);
            |      @typeDecl(Circle) @stay let `test//`.Circle ⦂ Type;
            |      `test//`.Circle = type (Circle);
            |      @typeDecl(Square) @stay let `test//`.Square ⦂ Type;
            |      `test//`.Square = type (Square);
            |      @fn let `test//`.describeGeometric ⦂(fn (Geometric): String), @fn `test//`.describeShape ⦂(fn (Shape): String), @typePlaceholder(Geometric) typePlaceholder#0: Empty;
            |      typePlaceholder#0 = {class: Empty__0};
            |      @fn @visibility(\public) @stay @fromType(Ray) let constructor__0 ⦂(fn (Ray): Void);
            |      constructor__0 = (@stay fn constructor(@impliedThis(Ray) this__0: Ray) /* return__0 */: Void {
            |          return__0 = void
            |      });
            |      @typePlaceholder(Shape) let typePlaceholder#1: Empty;
            |      typePlaceholder#1 = {class: Empty__0};
            |      @fn @visibility(\public) @stay @fromType(Circle) let constructor__1 ⦂(fn (Circle): Void);
            |      constructor__1 = (@stay fn constructor(@impliedThis(Circle) this__1: Circle) /* return__1 */: Void {
            |          return__1 = void
            |      });
            |      @fn @visibility(\public) @stay @fromType(Square) let constructor__2 ⦂(fn (Square): Void);
            |      constructor__2 = (@stay fn constructor(@impliedThis(Square) this__2: Square) /* return__2 */: Void {
            |          return__2 = void
            |      });
            |      `test//`.describeGeometric = (@stay fn describeGeometric(g__0 /* aka g */: Geometric) /* return__3 */: String {
            |          if (g__0 is Circle) {
            |            return__3 = "circle"
            |          } else if (g__0 is Square) {
            |            return__3 = "square"
            |          } else {
            |            return__3 = void
            |          }
            |      });
            |      `test//`.describeShape = (@stay fn describeShape(s__0 /* aka s */: Shape) /* return__4 */: String {
            |          if (s__0 is Circle) {
            |            return__4 = "circle"
            |          } else if (s__0 is Square) {
            |            return__4 = "square"
            |          } else {
            |            return__4 = panic ⋖ String ⋗()
            |          }
            |      })
            |
            |      ```
            |  },
            |  errors: [
            |    "Cannot assign to String from Void!",
            |    "Expected subtype of String, but got Void!",
            |    "Void expressions cannot be used as values!",
            |  ]
            |}
        """.trimMargin(),
    )

    @Test
    fun assignmentsToTypedReturnAreChecked() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/assignments-to-typed-return-are-checked"),
        stage = Stage.GenerateCode,
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
        stageTestDir = StageTestDir("generate-code/doc-comment-in-data"),
        stage = Stage.GenerateCode,
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
        stageTestDir = StageTestDir("generate-code/do-while-continues-to-false-condition"),
        stage = Stage.Run,
        moduleResultNeeded = true,
        want = """
            |{
            |  run: "void: Void",
            |  generateCode: {
            |    body: ```
            |      let return__0;
            |      do_call_log(getConsole(), "Done once");
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
        stageTestDir = StageTestDir("generate-code/exported-names"),
        stage = Stage.Run,
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

    @Test
    fun simpleMethodCall() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/simple-method-call"),
        stage = Stage.Run,
        moduleResultNeeded = true,
        want = """
            |{
            |  run: ["1", "String"],
            |}
        """.trimMargin(),
    )

    @Suppress("SpellCheckingInspection") // getprop/setprop
    @Test
    fun getterSettersFinal() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/getter-setters-final"),
        stage = Stage.GenerateCode,
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
        stageTestDir = StageTestDir("generate-code/getter-setters-var-or-not"),
        stage = Stage.GenerateCode,
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
        stageTestDir = StageTestDir("generate-code/fn-type"),
        stage = Stage.GenerateCode,
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
        stageTestDir = StageTestDir("generate-code/cats-are-nice"),
        stage = Stage.GenerateCode,
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
        stageTestDir = StageTestDir("generate-code/cats-are-rad-actually"),
        stage = Stage.GenerateCode,
        want = """
        |{
        |  generateCode: {
        |    body:
        |      ```
        |      @fn @reach(\none) let f__0;
        |      f__0 = (@stay fn f(s__0 /* aka s */: String) /* return__1 */: Void {
        |          var t#0, t#1, t#2;
        |          cat(do_call_toString(0));
        |          t#0 = do_call_toString(0);
        |          cat(s__0, t#0);
        |          t#1 = do_call_toString(0);
        |          cat(s__0, t#1, s__0);
        |          t#2 = do_call_toString(0);
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
        stageTestDir = StageTestDir("generate-code/cats-play-with-string-and-null"),
        stage = Stage.GenerateCode,
        want = """
        |{
        |  generateCode: {
        |    body:
        |      ```
        |      @fn @reach(\none) let f__0;
        |      f__0 = (@stay fn f(s__0 /* aka s */: String, a__0 /* aka a */: Int32?) /* return__1 */: String {
        |          var t#0, t#1, t#2;
        |          if (isNull(a__0)) {
        |            t#0 = "null"
        |          } else {
        |            t#0 = do_call_toString(notNull(a__0))
        |          };
        |          if (isNull(a__0)) {
        |            t#2 = -1
        |          } else {
        |            t#2 = notNull(a__0)
        |          };
        |          t#1 = do_call_toString(t#2);
        |          return__1 = cat(s__0, t#0, t#1)
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
        stageTestDir = StageTestDir("generate-code/raw-cats-get-cooked"),
        stage = Stage.GenerateCode,
        want = """
            |{
            |  define: {
            |    body:
            |      ```
            |      @fn let f__0;
            |      f__0 = fn f(s__0 /* aka s */: String) /* return__1 */: Void {
            |        fn__0: do {
            |          cat(s__0);
            |          void;
            |          cat(what);
            |        }
            |      };
            |
            |      ```
            |  },
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
    fun mapTypeArg() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/map-type-arg"),
        stage = Stage.GenerateCode,
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
        stageTestDir = StageTestDir("generate-code/ban-export-not-at-top-level"),
        stage = Stage.GenerateCode,
        want = """
        {
          errors: [ "TODO" ]
        }
        """,
    )

    @Ignore
    @Test
    fun banExportsThatAreReAssignable() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/ban-exports-that-are-re-assignable"),
        stage = Stage.GenerateCode,
        want = """
        {
          errors: [ "TODO" ]
        }
        """,
    )

    @Ignore
    @Test
    fun banExportInLoops() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/ban-export-in-loops"),
        stage = Stage.GenerateCode,
        want = """
        {
          errors: [ "TODO" ]
        }
        """,
    )

    @Test
    fun banExportsExposingNonExported() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/ban-exports-exposing-non-exported"),
        stage = Stage.GenerateCode,
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
        stageTestDir = StageTestDir("generate-code/ban-mixed-exports-just-function-type"),
        stage = Stage.GenerateCode,
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
        stageTestDir = StageTestDir("generate-code/unaligned-named-args"),
        stage = Stage.GenerateCode,
        // TODO This only matters for constructors/factories going forward.
        // TODO And maybe we'll manage those positioned, so this test might be best removed sometime.
        want = """
            |{
            |  generateCode: {
            |    body:
            |      ```
            |      let console#0;
            |      console#0 = getConsole();
            |      @fn let hi__0;
            |      hi__0 = (@stay fn hi(name__0 /* aka name */: String) /* return__1 */: Void {
            |          do_call_log(console#0, name__0);
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
        stageTestDir = StageTestDir("generate-code/nested-assignment-in-result-position"),
        stage = Stage.Run,
        want = """
            |{
            |  run: "123: Int32",
            |  generateCode: {
            |    body: ```
            |      let return__0, a__0, b__0;
            |      b__0 = oneTwoThree();
            |      a__0 = b__0;
            |      return__0 = a__0
            |
            |      ```
            |  }
            |}
        """.trimMargin(),
        moduleResultNeeded = true,
    ) { module, moduleAdvancer, td, rfl ->
        module.addEnvironmentBindings(oneToThreeBindings)
        provisionModuleForStageTest(td, module, moduleAdvancer, rfl)
    }

    @Test
    fun autoCastIs() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/auto-cast-is"),
        stage = Stage.GenerateCode,
        want = """
            |{
            |  generateCode: {
            |    body: ```
            |      @fn @reach(\none) let some__0;
            |      some__0 = (@stay fn some(maybe__0 /* aka maybe */: StringIndexOption) /* return__0 */: StringIndex {
            |          if (maybe__0 is StringIndex) {
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
        stageTestDir = StageTestDir("generate-code/auto-cast-when"),
        stage = Stage.GenerateCode,
        want = """
            |{
            |  generateCode: {
            |    body: ```
            |      @fn @reach(\none) let some__0;
            |      some__0 = (@stay fn some(maybe__0 /* aka maybe */: StringIndexOption) /* return__0 */: StringIndex {
            |          if (maybe__0 is StringIndex) {
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
        stageTestDir = StageTestDir("generate-code/nested-setp-in-result-position"),
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
    ) { module, moduleAdvancer, td, rfl ->
        module.addEnvironmentBindings(oneToThreeBindings)
        provisionModuleForStageTest(td, module, moduleAdvancer, rfl)
    }

    @Test
    fun nestedSetterInvocationsInResultPosition() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/nested-setter-invocations-in-result-position"),
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
            |          t#0 = do_call_toString(newValue__0, 10);
            |          do_call_log(console#0, cat("Assigned ", t#0));
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
    ) { module, moduleAdvancer, td, rfl ->
        module.addEnvironmentBindings(oneToThreeBindings)
        provisionModuleForStageTest(
            td,
            module, moduleAdvancer, rfl,
        )
    }

    /**
     * Not having argument or return types causes the errors here.
     * See also `TyperTest.assignedFnWithInferredSigTypes`.
     */
    @Test
    fun assignedFnWithInferredSigTypes() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/assigned-fn-with-inferred-sig-types"),
        stage = Stage.GenerateCode,
        want = """
        {
          generateCode: {
            body: ```
                @fn @reach(\none) let funny__0: (fn (Int32): String);
                funny__0 = (@stay fn funny(n__0 /* aka n */) /* return__0 */{
                    return__0 = do_call_toString(n__0)
                })

                ```
          }
        }
        """,
    )

    @Test
    fun booleanTypeError() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/boolean-type-error"),
        stage = Stage.GenerateCode,
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
        stageTestDir = StageTestDir("generate-code/lotsa-lets"),
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
        stageTestDir = StageTestDir("generate-code/enum-constants"),
        stage = Stage.GenerateCode,
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
        stageTestDir = StageTestDir("generate-code/empty-interface"),
        stage = Stage.GenerateCode,
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
        stageTestDir = StageTestDir("generate-code/hide-override-property"),
        stage = Stage.GenerateCode,
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
        stageTestDir = StageTestDir("generate-code/hide-override-method"),
        stage = Stage.GenerateCode,
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
        stageTestDir = StageTestDir("generate-code/hide-override-method-generic"),
        stage = Stage.GenerateCode,
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
        stageTestDir = StageTestDir("generate-code/type-parameter-can-extend-concrete-type"),
        stage = Stage.GenerateCode,
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
        stageTestDir = StageTestDir("generate-code/return-type-required"),
        stage = Stage.GenerateCode,
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
    fun optionalArgumentPassing() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/optional-argument-passing"),
        stage = Stage.Run,
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

    @Test
    fun returnTypeOptionalForSomeCases() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/return-type-optional-for-some-cases"),
        stage = Stage.GenerateCode,
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
        stageTestDir = StageTestDir("generate-code/type-metadata"),
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
            |          connected: ["void: Void"],
            |          imu: ["void: Void"],
            |        }
            |      },
            |    },
            |  },
            |}
        """.trimMargin(),
    ) { module, moduleAdvancer, td, rfl ->
        module.addEnvironmentBindings(
            mapOf(
                BuiltinName("@foo") to Value(MetadataDecorator(Symbol("foo")) { void }),
            ),
        )

        provisionModuleForStageTest(td, module, moduleAdvancer, rfl)
    }

    @Test
    fun voidNotAValue() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/void-not-a-value"),
        stage = Stage.GenerateCode,
        // Implied and explicit void returns should be fine, but others should be errors.
        want = """
            |{
            |  generateCode: {
            |    body:
            |    ```
            |    let console#0;
            |    console#0 = getConsole();
            |    @fn let b__0, @fn c__0;
            |    b__0 = (@stay fn b /* return__0 */: Void {
            |        do_call_log(console#0, "hi");
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
            |    do_call_get(a__0, 0);
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
        stageTestDir = StageTestDir("generate-code/void-vs-value"),
        stage = Stage.GenerateCode,
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
        stageTestDir = StageTestDir("generate-code/implied-lambda-return-type"),
        stage = Stage.GenerateCode,
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
        stageTestDir = StageTestDir("generate-code/dead-code"),
        stage = Stage.GenerateCode,
        want = """
            |{
            |  generateCode: {
            |    body: ```
            |      do_call_log(getConsole(), "Logged")
            |
            |      ```
            |  },
            |}
        """.trimMargin(),
    )

    @Test
    fun staticMethods() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/static-methods"),
        stage = Stage.GenerateCode,
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
        stageTestDir = StageTestDir("generate-code/static-access-good-and-bad"),
        stage = Stage.GenerateCode,
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
            |              2 * f__0(i__3) * fp__0(i__3) * do_icall_g(type (C__0), this(C__0), i__3) * do_icall_g(type (C__0), this(C__0), i__3)
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
            |          2 * do_call_f(C__0, i__6) * do_call_g(new C__0(), i__6) * do_get_a(C__0) * do_get_ap(C__0)
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
            |      a__0 = 2;
            |      @visibility(\private) @stay @fromType(C__0) let bp__0: Int32;
            |      @visibility(\private) @stay @fromType(C__0) let b__0: Int32;
            |      @fn @static @visibility(\public) @stay @fromType(C__0) let f__0;
            |      f__0 = (@stay fn f(i__0 /* aka i */: Int32) /* return__0 */: Int32 {
            |          void;
            |          fn__0: do {
            |            return__0 = i__0 + 2 + igetStatic(C__0, \a) + 1 + igetStatic(C__0, \ap);
            |          }
            |      });
            |      @fn @static @visibility(\private) @stay @fromType(C__0) let fp__0;
            |      fp__0 = (@stay fn fp(i__1 /* aka i */: Int32) /* return__1 */: Int32 {
            |          void;
            |          fn__1: do {
            |            return__1 = i__1 + 1;
            |          }
            |      });
            |      @visibility(\public) @fn @stay @fromType(C__0) let g__0;
            |      g__0 = fn g(@impliedThis(C__0) this__0: C__0, i__2 /* aka i */: Int32) /* return__2 */: Int32 {
            |        void;
            |        fn__2: do {
            |          return__2 = 2 * igetStatic(C__0, \f)(i__2) * igetStatic(C__0, \fp)(i__2) * getp(bp__0, this__0) * getp(b__0, this__0) * getp(bp__0, this__0) * getp(b__0, this__0);
            |        }
            |      };
            |      @visibility(\public) @fn @stay @fromType(C__0) let h__0;
            |      h__0 = (@stay fn h(@impliedThis(C__0) this__1: C__0, i__3 /* aka i */: Int32) /* return__3 */: Int32 {
            |          void;
            |          fn__3: do {
            |            return__3 = 2 * (fn f)(i__3) * (fn fp)(i__3) * do_icall_g(type (C__0), this__1, i__3) * do_icall_g(type (C__0), this__1, i__3);
            |          }
            |      });
            |      @fn @static @visibility(\public) @stay @fromType(C__0) let g2__0;
            |      g2__0 = fn g2(i__4 /* aka i */: Int32) /* return__4 */: Int32 {
            |        void;
            |        fn__4: do {
            |          return__4 = 2 * igetStatic(C__0, \f)(i__4) * igetStatic(C__0, \fp)(i__4);
            |        }
            |      };
            |      @fn @static @visibility(\public) @stay @fromType(C__0) let h2__0;
            |      h2__0 = (@stay fn h2(i__5 /* aka i */: Int32) /* return__5 */: Int32 {
            |          void;
            |          fn__5: do {
            |            return__5 = 2 * (fn f)(i__5) * (fn fp)(i__5);
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
            |      g3__0 = (@stay fn g3(i__6 /* aka i */: Int32) /* return__7 */: Int32 {
            |          void;
            |          fn__6: do {
            |            return__7 = 2 * getStatic(C__0, \f)(i__6) * do_call_g(new C__0(), i__6) * getStatic(C__0, \a) * getStatic(C__0, \ap);
            |          }
            |      })
            |
            |      ```
            |  },
            |  "generateCode": {
            |      "body":
            |      ```
            |      @typeDecl(C__0) @stay @reach(\none) let C__0;
            |      C__0 = type (C__0);
            |      @fn @reach(\none) let g3__0;
            |      @static @visibility(\private) @stay @fromType(C__0) @reach(\none) let ap__0: Int32;
            |      ap__0 = 1;
            |      @static @visibility(\public) @stay @fromType(C__0) @reach(\none) let a__0: Int32;
            |      a__0 = 2;
            |      @visibility(\private) @stay @fromType(C__0) @reach(\none) let bp__0: Int32;
            |      @visibility(\private) @stay @fromType(C__0) @reach(\none) let b__0: Int32;
            |      @fn @static @visibility(\public) @stay @fromType(C__0) @reach(\none) let f__0;
            |      f__0 = (@stay fn f(i__0 /* aka i */: Int32) /* return__0 */: Int32 {
            |          return__0 = i__0 + 2 + igetStatic(C__0, \a) + 1 + igetStatic(C__0, \ap)
            |      });
            |      @fn @static @visibility(\private) @stay @fromType(C__0) @reach(\none) let fp__0;
            |      fp__0 = (@stay fn fp(i__1 /* aka i */: Int32) /* return__1 */: Int32 {
            |          return__1 = i__1 + 1
            |      });
            |      @visibility(\public) @fn @stay @fromType(C__0) @reach(\none) let g__0;
            |      g__0 = (@stay fn g(@impliedThis(C__0) this__0: C__0, i__2 /* aka i */: Int32) /* return__2 */: Int32 {
            |          return__2 = 2 * igetStatic(C__0, \f)(i__2) * igetStatic(C__0, \fp)(i__2) * getp(bp__0, this__0) * getp(b__0, this__0) * getp(bp__0, this__0) * getp(b__0, this__0)
            |      });
            |      @visibility(\public) @fn @stay @fromType(C__0) @reach(\none) let h__0;
            |      h__0 = (@stay fn h(@impliedThis(C__0) this__1: C__0, i__3 /* aka i */: Int32) /* return__3 */: Int32 {
            |          return__3 = 2 * (fn f)(i__3) * (fn fp)(i__3) * do_icall_g(type (C__0), this__1, i__3) * do_icall_g(type (C__0), this__1, i__3)
            |      });
            |      @fn @static @visibility(\public) @stay @fromType(C__0) @reach(\none) let g2__0;
            |      g2__0 = (@stay fn g2(i__4 /* aka i */: Int32) /* return__4 */: Int32 {
            |          return__4 = 2 * igetStatic(C__0, \f)(i__4) * igetStatic(C__0, \fp)(i__4)
            |      });
            |      @fn @static @visibility(\public) @stay @fromType(C__0) @reach(\none) let h2__0;
            |      h2__0 = (@stay fn h2(i__5 /* aka i */: Int32) /* return__5 */: Int32 {
            |          return__5 = 2 * (fn f)(i__5) * (fn fp)(i__5)
            |      });
            |      @fn @visibility(\public) @stay @fromType(C__0) @reach(\none) let constructor__0;
            |      constructor__0 = (@stay fn constructor(@impliedThis(C__0) this__2: C__0) /* return__6 */: Void {
            |          var t#1;
            |          setp(bp__0, this__2, 1);
            |          t#1 = getp(bp__0, this__2) + 1;
            |          setp(b__0, this__2, t#1);
            |          return__6 = void
            |      });
            |      g3__0 = (@stay fn g3(i__6 /* aka i */: Int32) /* return__7 */: Int32 {
            |          return__7 = 2 * getStatic(C__0, \f)(i__6) * do_call_g(new C__0(), i__6) * getStatic(C__0, \a) * getStatic(C__0, \ap)
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
        stageTestDir = StageTestDir("generate-code/no-instantiate-interface"),
        stage = Stage.GenerateCode,
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
        stageTestDir = StageTestDir("generate-code/export-some"),
        stage = Stage.GenerateCode,
        // Includes examples of different kinds of roots and entities as well as transitive reachability and such.
        // Also includes an example of something reachable from both export and test roots.
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
            |            do_call_log(console#0, "");
            |            return__0 = void
            |        });
            |        `test//`.exportedFunction = (@stay fn exportedFunction(b__0 /* aka b */: Boolean) /* return__1 */: Void {
            |            if (b__0) {
            |              conditionallyExportReachable__0()
            |            };
            |            return__1 = void
            |        });
            |        transitivelyTestReachable__0 = (@stay fn transitivelyTestReachable /* return__2 */: Void {
            |            do_call_log(console#0, "");
            |            return__2 = void
            |        });
            |        exportAndTestReachable__0 = (@stay fn exportAndTestReachable /* return__3 */: Void {
            |            do_call_log(console#0, "");
            |            return__3 = void
            |        });
            |        transitivelyInitReachable__0 = (@stay fn transitivelyInitReachable /* return__4 */: Void {
            |            do_call_log(console#0, "");
            |            return__4 = void
            |        });
            |        initReachable__0 = (@stay fn initReachable /* return__5 */: Void {
            |            transitivelyInitReachable__0();
            |            do_call_log(console#0, "");
            |            return__5 = void
            |        });
            |        unreachableFunction__0 = (@stay fn unreachableFunction /* return__6 */: Void {
            |            do_call_log(console#0, "");
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
        stageTestDir = StageTestDir("generate-code/init-assignment-reachability"),
        stage = Stage.GenerateCode,
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
        stageTestDir = StageTestDir("generate-code/block-lambda-end-to-end"),
        stage = Stage.Run,
        moduleResultNeeded = true,
        want = """
            |{
            |  run: "42: Int32"
            |}
        """.trimMargin(),

    )

    @Test
    fun generatorInterpreted() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/generator-interpreted"),
        stage = Stage.Run,
        want = """
            |{
            |  run: "void: Void",
            |  type: {
            |    body: ```
            |    let console#0;
            |    console#0 = doPure(@stay fn /* return__0 */: Console {
            |        return__0 = getConsole();
            |    });
            |    @fn let runItThrice__0;
            |    runItThrice__0 = fn runItThrice(factory__0 /* aka factory */: (fn (): SafeGenerator<Empty>)) /* return__1 */: Void {
            |      void;
            |      fn__0: do {
            |        let generator__0: SafeGenerator<Empty>;
            |        generator__0 = factory__0();${
            "" // Since it's a SafeGenerator, no error checking around do_call_next(...)
        }
            |        do_call_next(generator__0);
            |        do_call_log(console#0, ",");
            |        do_call_next(generator__0);
            |        do_call_log(console#0, ",");
            |        do_call_next(generator__0);
            |        do_call_log(console#0, ".");
            |        return__1 = void
            |      }
            |    };
            |    runItThrice__0(fn /* return__2 */{${
            "" // Adapt call specialized to adaptGeneratorFnSafe
        }
            |        return__2 = adaptGeneratorFnSafe(@wrappedGeneratorFn fn /* return__3 */: (GeneratorResult<Empty>) implements GeneratorFn {
            |            do_call_log(console#0, "First");
            |            yield();
            |            do_call_log(console#0, "Second");
            |            yield();
            |            do_call_log(console#0, "Third");
            |            yield();
            |            do_call_log(console#0, "Fourth");
            |            return__3 = core.doneResult<Empty>()
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
        stageTestDir = StageTestDir("generate-code/generator-interpreted-in-loop"),
        stage = Stage.Run,
        want = """
            |{
            |  run: "void: Void",
            |  type: {
            |    body: ```
            |    let console#0;
            |    console#0 = doPure(@stay fn /* return__0 */: Console {
            |        return__0 = getConsole();
            |    });
            |    @fn let runItThrice__0;
            |    runItThrice__0 = fn runItThrice(factory__0 /* aka factory */: (fn (): SafeGenerator<Empty>)) /* return__1 */: Void {
            |      void;
            |      fn__0: do {
            |        let generator__0: SafeGenerator<Empty>;
            |        generator__0 = factory__0();
            |## Since it's a SafeGenerator, no error checking around do_call_next(...)
            |        do_call_next(generator__0);
            |        do_call_log(console#0, "Ran once");
            |        do_call_next(generator__0);
            |        do_call_log(console#0, "Ran twice");
            |        do_call_next(generator__0);
            |        do_call_log(console#0, "Ran thrice");
            |        do_call_close(generator__0);
            |        return__1 = void
            |      }
            |    };
            |    runItThrice__0(fn /* return__2 */{
            |## Adapt call specialized to adaptGeneratorFnSafe
            |        return__2 = adaptGeneratorFnSafe(@wrappedGeneratorFn fn /* return__3 */: (GeneratorResult<Empty>) implements GeneratorFn {
            |            return__3 = core.doneResult<Empty>();
            |## The interpreter needs to distinguish a legit return result with the result from a yield.
            |            void;
            |            while (true) {
            |              do_call_log(console#0, "Pausing");
            |              yield();
            |              do_call_log(console#0, "Resuming");
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
        """.trimMargin(),
    )

    @Ignore
    @Test
    fun generatorResultsUsed() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/generator-results-used"),
        stage = Stage.Run,
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
        stageTestDir = StageTestDir("generate-code/for-of-example"),
        stage = Stage.Run,
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
        stageTestDir = StageTestDir("generate-code/awaiting"),
        stage = Stage.Run,
        stagingFlags = setOf(StagingFlags.allowTopLevelAwait),
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
            |              do_call_complete(pb__0, "Hello, World!");
            |              return__1 = (fn doneResult)<Empty>()
            |          });
            |          return__0 = adaptGeneratorFnSafe(fn__1)
            |      });
            |      async(fn__0);
            |      t#1 = hs(fail#0, await p__0);
            |      if (fail#0) {
            |        bubble()
            |      };
            |      do_call_log(t#0, t#1)
            |
            |      ```
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun invalidRtti() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/invalid-rtti"),
        stage = Stage.Run,
        // Check that is T and as T only operate
        // on types that can be distinguished at runtime.
        logEntryWanted = {
            it.level >= Log.Warn ||
                // This is a low-level message, but it's specific to these checks.
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
        stageTestDir = StageTestDir("generate-code/invalid-rtti-type-args"),
        stage = Stage.Run,
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
        stageTestDir = StageTestDir("generate-code/invalid-rtti-not-inlined"),
        stage = Stage.GenerateCode,
        // Check that is T and as T that would be invalid
        // if translated aren't inlined.
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
            |      return__0 = "str" is String
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
        stageTestDir = StageTestDir("generate-code/upcast-ok"),
        stage = Stage.Run,
        // Use Map here because that was the original motivating example, even though it's not vital to the test.
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
        stageTestDir = StageTestDir("generate-code/cast-away-null-works-at-runtime"),
        stage = Stage.Run,
        want = """
            |{
            |  run: "void: Void",
            |  stdout: "f(1.0) = 1.0\n",
            |}
        """.trimMargin(),
    )

    @Test
    fun matchWithCharExprCases() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/match-with-char-expr-cases"),
        stage = Stage.GenerateCode,
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
        stageTestDir = StageTestDir("generate-code/sealed-connected-casts"),
        // comments in the cast checker describe why this is the way it is.
        // In short, a sealed, connected type must be able to distinguish
        // its subtypes, so the static expression type matters when casting.
        stage = Stage.Run,
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
        stageTestDir = StageTestDir("generate-code/string-null-equality"),
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
        stageTestDir = StageTestDir("generate-code/as-and-is-simplification1"),
        stage = Stage.GenerateCode,
        want = """
            |{
            |  generateCode: {
            |    body: ```
            |      @fn @reach(\none) let f__0;
            |      f__0 = (@stay fn f(i__0 /* aka i */: StringIndexOption?) /* return__0 */: (Int32 | Bubble) {
            |          var t#0;
            |          if (!isNull(i__0)) {
            |            t#0 = i__0 is StringIndex
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
        stageTestDir = StageTestDir("generate-code/as-and-is-simplification2"),
        stage = Stage.GenerateCode,
        want = """
            |{
            |  generateCode: {
            |    body: ```
            |      @fn @reach(\none) let f__0;
            |      f__0 = (@stay fn f(i__0 /* aka i */: StringIndexOption?) /* return__0 */: (Int32 | Bubble) {
            |          var t#0;
            |          if (!isNull(i__0)) {
            |            t#0 = i__0 is StringIndexOption
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
        stageTestDir = StageTestDir("generate-code/as-and-is-simplification3"),
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
            |              j__0 = hs(fail#0, i__0 as StringIndex);
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
        stageTestDir = StageTestDir("generate-code/as-and-is-simplification4"),
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
            |            t#0 = i__0 is StringIndex
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
            |              t#1 = hs(fail#0, t#3 as StringIndex);
            |              if (fail#0) {
            |                bubble()
            |              };
            |              j__0 = t#1
            |            };
            |            if (!isNull(j__0)) {
            |              t#2 = j__0 is StringIndex
            |            } else {
            |              t#2 = false
            |            };
            |            if (t#2) {
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
        stageTestDir = StageTestDir("generate-code/as-and-is-simplification5"),
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
            |          return__1 = (fn g)(s__1) is NoStringIndex
            |      })
            |
            |      ```
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun nullSimplification() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/null-simplification"),
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
        stageTestDir = StageTestDir("generate-code/sneaky-bubble"),
        stage = Stage.GenerateCode,
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
        stageTestDir = StageTestDir("generate-code/bubble-or-else-not"),
        stage = Stage.GenerateCode,
        // Explore bubbles both escaping and captured, both explicit and implicit, both builtin and user functions.
        // Just making sure to explore the space of how we handle things.
        want = """
            |{
            |  generateCode: {
            |    body: ```
            |      @fn @reach(\none) let other__0, @fn @reach(\none) something__0;
            |      other__0 = (@stay fn other(i__0 /* aka i */: Int32) /* return__0 */: (Int32 | Bubble) {
            |          if (i__0 % 2 == 0) {
            |            bubble()
            |          } else {
            |            return__0 = i__0
            |          }
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
            |            return__1 = hs(fail#1, do_call_get(nums__0, index__0));
            |            if (fail#1) {
            |              bubble()
            |            }
            |          } else {
            |            orelse#0: {
            |              t#0 = hs(fail#2, do_call_get(nums__0, index__0));
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
        stageTestDir = StageTestDir("generate-code/extension-method-use"),
        stage = Stage.Run,
        moduleResultNeeded = true,
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
            |              j__0 = do_call_prev(s__0, j__0);
            |              if(do_call_get(s__0, i__0) != do_call_get(s__0, j__0), fn {
            |                  do {
            |                    return__0 = false;
            |                    break(\label, fn__0)
            |                  }
            |              });
            |              i__0 = do_call_next(s__0, i__0);
            |          });
            |          do {
            |            return__0 = true;
            |            break(\label, fn__0)
            |          }
            |        }
            |      };
            |      (do_call_isPalindrome[stringIsPalindrome__0])("step on no pets")
            |
            |      ```
            |  },
            |  type: {
            |    body: ```
            |      let return__1, @fn @extension("isPalindrome") stringIsPalindrome__0;
            |      stringIsPalindrome__0 = (@stay fn stringIsPalindrome(s__0 /* aka s */: String) /* return__0 */: Boolean {
            |          void;
            |          fn__0: do {
            |            var i__0;
            |            i__0 = getStatic(String, \begin);
            |            var j__0;
            |            j__0 = do_get_end(s__0);
            |            while (i__0 < j__0) {
            |              j__0 = do_call_prev(s__0, j__0);
            |              if (do_call_get(s__0, i__0) != do_call_get(s__0, j__0)) {
            |                return__0 = false;
            |                break fn__0;
            |              };
            |              i__0 = do_call_next(s__0, i__0);
            |            };
            |            return__0 = true
            |          }
            |      });
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
    fun jsonAdapterWorks() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/json-adapter-works"),
        stage = Stage.Run,
        moduleResultNeeded = true,
        want = """
            |{
            |  run: "{}: CJsonAdapter__0"
            |}
        """.trimMargin(),
    )

    @Test
    fun jsonAdapterEncodesSealedTypes() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/json-adapter-encodes-sealed-types"),
        stage = Stage.Run,
        moduleResultNeeded = true,
        want = """
            |{
            |  run: "\"[{\\\"meowCount\\\":11},{\\\"hydrantsSniffed\\\":111}]\": String"
            |}
        """.trimMargin(),
    )

    @Test
    fun jsonAdapterDecodesSealedTypes() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/json-adapter-decodes-sealed-types"),
        stage = Stage.Run,
        moduleResultNeeded = true,
        want = """
            |{
            |  run: "[{meowCount: 137}, {hydrantsSniffed: 1337}]: List"
            |}
        """.trimMargin(),
    )

    @Test
    fun nullableJsonField() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/nullable-json-field"),
        stage = Stage.Run,
        moduleResultNeeded = true,
        want = """
            |{
            |  run: "[null, false, true]: List",
            |}
        """.trimMargin(),
    )

    @Test
    fun jsonInteropForwardsTypeInfoForNullableProps() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/json-interop-forwards-type-info-for-nullable-props"),
        stage = Stage.Run,
        moduleResultNeeded = true,
        want = """
            |{
            |  run: "{i: null}: C__0"
            |}
        """.trimMargin(),
    )

    @Test
    fun rgxMacro() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/rgx-macro"),
        stage = Stage.Run,
        moduleResultNeeded = true,
        want = """
            |{
            |  run: "{data: {}, compiled: ƒ}: `std/regex/`.Regex"
            |}
        """.trimMargin(),
    )

    @Test
    fun complexStringExpr() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/complex-string-expr"),
        stage = Stage.Run,
        moduleResultNeeded = true,
        want = """
            |{
            |  import: {
            |    body: ```
            |          let guests = list("Hilo, HI", "you in the back in the hat");
            |          do {
            |            let accumulator#0: StringBuilder;
            |            accumulator#0 = new StringBuilder ();
            |            do {
            |              do_call_append(accumulator#0, "Hello, World");
            |              for((let guest of guests), fn {
            |                  do_call_append(accumulator#0, ", and ");
            |                  do_call_append(accumulator#0, str(guest));
            |              });
            |              do_call_append(accumulator#0, "!");
            |            };
            |            do_call_toString(accumulator#0)
            |          }
            |
            |          ```
            |  },
            |  run: ["Hello, World, and Hilo, HI, and you in the back in the hat!", "String"],
            |}
        """.trimMargin(),
    )

    @Test
    fun complexStringExprWithFormattingHole() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/complex-string-expr-with-formatting-hole"),
        stage = Stage.Run,
        moduleResultNeeded = true,
        want = """
            |{
            |  run: ["Things: 1, 2, 4, 8, 16, 32, 64, and so on", "String"],
            |}
        """.trimMargin(),
    )

    @Test
    fun complexStringExprWithFormattingHoleAndMore() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/complex-string-expr-with-formatting-hole-and-more"),
        stage = Stage.Run,
        moduleResultNeeded = true,
        want = """
            |{
            |  run: ["1, 2, 4, 8, 16, 32, 64, and so on", "String"],
            |}
        """.trimMargin(),
    )

    @Test
    fun explicitBoundedTypeParametersInInterpreter() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/explicit-bounded-type-parameters-in-interpreter"),
        stage = Stage.Run,
        want = """
            |{
            |  run: "void: Void",
            |  stdout: "bar\n"
            |}
        """.trimMargin(),
    )

    @Test
    fun invalidNonNullCheck() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/invalid-non-null-check"),
        stage = Stage.Run,
        want = """
            |{
            |  run: "void: Void",
            |  errors: ["Expected function type, but got (fn (Int32): Void)?!"],
            |}
        """.trimMargin(),
    )

    @Test
    fun multiImport() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/multi-import"),
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
            |      do_call_log(getConsole(), do_call_toString(15))
            |
            |      ```
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun nullInTestingAssert() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/null-in-testing-assert"),
        stage = Stage.GenerateCode,
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
            |                var t#1;
            |## Here we're picking a string representation of the actual expression result
            |                if (isNull(actual#0)) {
            |                  t#1 = "null"
            |                } else {
            |                  t#1 = do_call_toString(notNull(actual#0))
            |                };
            |                return__1 = cat("expected c0.optionalString == (", "", ") not (", t#1, ")")
            |            });
            |            do_call_assert(test#0, t#0, fn__0);
            |            return__0 = void
            |        })
            |
            |        ```
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun longNullChain() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/long-null-chain"),
        stage = Stage.GenerateCode,
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
            |          do_call_toString(notNull(subject#0))
            |        }
            |      }
            |      ?? "NULL"
            |
            |      ```
            |  },
            |  generateCode: {
            |    body: ```
            |      let return__0;
            |      var t#0, t#1, t#2;
            |      @stay @imported(\(`test//a/`.a)) let a__0;
            |      a__0 = `test//a/`.a;
            |      if (isNull(a__0)) {
            |        t#0 = null
            |      } else {
            |        t#0 = do_get_string(notNull(a__0))
            |      };
            |      if (isNull(t#0)) {
            |        t#1 = null
            |      } else {
            |        t#1 = do_get_isEmpty(notNull(t#0))
            |      };
            |      if (isNull(t#1)) {
            |        t#2 = null
            |      } else {
            |        t#2 = do_call_toString(notNull(t#1))
            |      };
            |      if (isNull(t#2)) {
            |        return__0 = "NULL"
            |      } else {
            |        return__0 = notNull(t#2)
            |      }
            |
            |      ```
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun nonNullInference() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/non-null-inference"),
        stage = Stage.GenerateCode,
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
            |            t#0 = notNull(a__0);
            |            return__0 = do_call_countBetween(t#0, getStatic(String, \begin), do_get_end(t#0))
            |          }
            |      })
            |
            |      ```
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun complexAssignmentOfVarProperty() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/complex-assignment-of-var-property"),
        stage = Stage.Run,

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
            |      do_call_log(t#0, cat("ib.i = ", do_call_toString(do_get_i(ib__0))));
            |      let t#1;
            |      t#1 = ib__0;
            |## set-i of get-i pattern
            |## TODO: this might be a good test case for improving temporary elimination.
            |      do_set_i(t#1, do_get_i(t#1) + 11);
            |      do_call_log(t#0, cat("ib.i = ", do_call_toString(do_get_i(ib__0))));
            |      let t#2;
            |      t#2 = ib__0;
            |      do_set_i(t#2, do_get_i(t#2) * 9);
            |      do_call_log(t#0, cat("ib.i = ", do_call_toString(do_get_i(ib__0))));
            |      let t#3;
            |      t#3 = ib__0;
            |      do_set_i(t#3, do_get_i(t#3) - 6);
            |      do_call_log(t#0, cat("ib.i = ", do_call_toString(do_get_i(ib__0))));
            |      let t#4;
            |      t#4 = ib__0;
            |      do_set_i(t#4, do_get_i(t#4) / 2);
            |      do_call_log(t#0, cat("ib.i = ", do_call_toString(do_get_i(ib__0))))
            |
            |      ```
            |  },
            |
            |  run: "void: Void",
            |}
        """.trimMargin(),
    )

    @Test
    fun complexAssignmentOfGetExpr() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/complex-assignment-of-get-expr"),
        stage = Stage.Run,

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
            |      let console#0 = doPure(fn: Console {
            |          getConsole()
            |      }), ls__0 = new ListBuilder<Int>();
            |      do_call_add(ls__0, 0);
            |      do_call_add(ls__0, 3);
            |      do {
            |        let t#0;
            |        t#0 = ls__0;
            |## Here's a call to .set of a call to .get
            |        do_call_set(t#0, 0, do_call_get(t#0, 0) + 10)
            |      };
            |      do {
            |        let t#1;
            |        t#1 = ls__0;
            |        do_call_set(t#1, 1, do_call_get(t#1, 1) * 2)
            |      };
            |      do_call_log(console#0, cat("ls = [", str(do_call_join(do_call_toList(ls__0), ", ", fn (i__0 /* aka i */: Int) /* return__1 */: (String) {
            |                do_call_toString(i__0, 10)
            |          })), "]"));
            |
            |      ```
            |  },
            |}
        """.trimMargin(),
    )

    @Test
    fun whenElseBubble() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/when-else-bubble"),
        stage = Stage.GenerateCode,
        pseudoCodeDetail = PseudoCodeDetail(showInferredTypes = true),
        want = """
            |{
            |  generateCode: {
            |    body: ```
            |      @fn let `test//`.something ⦂(fn (String?): String | Bubble);
            |      `test//`.something = (@stay fn something(x__0 /* aka x */: String?) /* return__0 */: (String | Bubble) {
            |          var t#0 ⦂ Boolean;
            |          if (!isNull ⋖ String ⋗(x__0)) {
            |            t#0 = x__0 is String
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

    @Test
    fun veryBigMapConstructor() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/very-big-map-constructor"),
        stage = Stage.Run,
        want = """
            |{
            |  run: "void: Void",
            |}
        """.trimMargin(),
    )

    @Test
    fun doPureRuns() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/do-pure-runs"),
        stage = Stage.Run,
        moduleResultNeeded = true,
        want = """
            |{
            |  generateCode: {
            |    body: ```
            |        let return__0, @stay @imported(\(`test//c/`.C)) C__0;
            |        C__0 = type (C);
            |        let c__0;
            |        c__0 = new C();
            |        return__0 = c__0
            |
            |        ```
            |  },
            |  run: "{}: `test-code/c/`.C",
            |}
        """.trimMargin(),
    )

    @Test
    fun pureVirtualMethodInConcreteClass() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/pure-virtual-method-in-concrete-class"),
        stage = Stage.Run,
        want = """
            |{
            |  run: "void: Void",
            |  errors: ["Type C must implement f from I.  Maybe add `public f(x: String): Void`!"]
            |}
        """.trimMargin(),
    )

    @Test
    fun nullAssignedToNonNullVarDevl() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/null-assigned-to-non-null-var-devl"),
        stage = Stage.GenerateCode,
        moduleResultNeeded = true,
        want = """
            |{
            |  errors: [
            |    "Expected subtype of StringBuilder, but got StringBuilder?!",
            |  ],
            |  generateCode: {
            |    body: ```
            |      let return__0, @fn f__0;
            |      f__0 = (@stay fn f(i__0 /* aka i */: Int32) /* return__1 */: String {
            |          var sbOrNull__0: StringBuilder;
            |          sbOrNull__0 = null;
            |          if (i__0 % 2 == 0) {
            |            let sbNow__0;
            |            sbNow__0 = sbOrNull__0;
            |            let sb__0;
            |            sb__0 = sbNow__0;
            |            do_call_append(sb__0, cat(do_call_toString(i__0)));
            |            sbOrNull__0 = sb__0
            |          };
            |          let finalSb__0;
            |          finalSb__0 = sbOrNull__0;
            |          return__1 = do_call_toString(finalSb__0)
            |      });
            |      return__0 = (fn f)(4)
            |
            |      ```
            |  },
            |}
        """.trimMargin(),
    )

    @Test
    fun stringCoercionOfRttiCheck() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/string-coercion-of-rtti-check"),
        stage = Stage.Run,
        want = """
            |{
            |  generateCode: {
            |    body: ```
            |      let console#0;
            |      console#0 = getConsole();
            |      @fn let f__0;
            |      f__0 = (@stay fn f(i__0 /* aka i */: StringIndexOption) /* return__0 */: Void {
            |          var t#0, t#1;
            |## str has erased to a .toString() call here
            |          t#0 = do_call_toString(i__0 is StringIndex);
            |          t#1 = do_call_toString(i__0 is NoStringIndex);
            |          do_call_log(console#0, cat("Yes ", t#0, ", no ", t#1));
            |          return__0 = void
            |      });
            |      f__0(getStatic(String, \begin))
            |
            |      ```
            |  },
            |  run: "void: Void",
            |  stdout: "Yes true, no false\n",
            |}
        """.trimMargin(),
    )

    @Test
    fun isAppliedToParameterizedType() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/is-applied-to-parameterized-type"),
        stage = Stage.Run,
        moduleResultNeeded = true,
        want = """
            |{
            |  "run": "[true, false]: List"
            |}
        """.trimMargin(),
    )

    @Test
    fun staticWithUnusedExtension() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/static-with-unused-extension"),
        stage = Stage.Run,
        want = """
            |{
            |  run: "void: Void",
            |
            |  stdout: ```
            |    C foo
            |
            |    ```,
            |}
        """.trimMargin(),
    )

    @Test
    fun declaringADataFile() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/declaring-a-data-file"),
        stage = Stage.GenerateCode,
        want = """
            |{
            |  generateCode: {
            |    body: ```
            |      @topLevelMetadata @stay @declareDataFile((["hello.txt", "text/plain", "Hellllllllllllllllll⋯llllllllllllo, World!"])) @reach(\none) let moduleMetadata#0: Empty;
            |
            |      ```
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun missingFunctionBody() = assertModuleAtStage(
        stageTestDir = StageTestDir("generate-code/missing-function-body"),
        stage = Stage.GenerateCode,
        want = """
            |{
            |  generateCode: {
            |    body:
            |      ```
            |      @fn @reach(\none) let hi__0;
            |      hi__0 = (@stay fn hi /* return__0 */: Void {
            |          abstractPanic();
            |          return__0 = void
            |      })
            |
            |      ```
            |  },
            |  errors: [
            |    "Function body required except for virtual methods or connected functions!"
            |  ],
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
