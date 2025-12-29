package lang.temper.frontend.typestage

import lang.temper.common.Console
import lang.temper.common.ListBackedLogSink
import lang.temper.common.SnapshotKey
import lang.temper.common.Snapshotter
import lang.temper.common.assertStringsEqual
import lang.temper.common.assertStructure
import lang.temper.common.console
import lang.temper.common.json.JsonValue
import lang.temper.common.json.JsonValueBuilder
import lang.temper.common.stripDoubleHashCommentLinesToPutCommentsInlineBelow
import lang.temper.common.structure.Structured
import lang.temper.common.testCodeLocation
import lang.temper.common.testModuleName
import lang.temper.frontend.AstSnapshotKey
import lang.temper.frontend.Module
import lang.temper.frontend.ModuleSource
import lang.temper.frontend.StagingFlags
import lang.temper.frontend.rewriteTNames
import lang.temper.frontend.syntax.isAssignment
import lang.temper.lexer.StandaloneLanguageConfig
import lang.temper.log.Debug
import lang.temper.name.PseudoCodeNameRenumberer
import lang.temper.stage.Stage
import lang.temper.type.MethodShape
import lang.temper.type.PropertyShape
import lang.temper.type.StaticPropertyShape
import lang.temper.type.TypeParameterShape
import lang.temper.value.BlockTree
import lang.temper.value.CallTree
import lang.temper.value.FunTree
import lang.temper.value.PseudoCodeDetail
import lang.temper.value.RightNameLeaf
import lang.temper.value.TBoolean
import lang.temper.value.Tree
import lang.temper.value.toPseudoCode
import kotlin.test.Test
import kotlin.test.assertEquals

class TyperPlanTest {
    @Test
    fun assumedLocalVariableTypes() {
        val plan = planFor(
            """
            |let x: Int;
            |var t0;
            |var t1;
            |
            |t0 = f();
            |t1 = t0; // Can infer type for t0 from t1
            |x = t1;  // Can infer type for t1 from x
            |         // Can infer type for t0 from x by transitivity.
            |
            |let y: Int;
            |y = x;   // Do not infer type for x because it has a declared type.
            |
            |// Similar to the above, but the assignments are nested.
            |let z: Int;
            |var t2;
            |var t3;
            |t3 = (t2 = f()); // Can infer type for t2 from t3
            |z = t3;          // Can infer type for t3 from z, and transitively t2 from z.
            """.trimMargin(),

            replaceTemporaries = true,
            stage = Stage.DisAmbiguate,
        )
        assertStructure(
            """
            |{
            |  "t0#0": [ "t1#1", "x" ],
            |  "t1#1": [ "x" ],
            |  "t2#2": [ "t3#3", "z" ],
            |  "t3#3": [ "z" ],
            |}
            """.trimMargin(),

            plan.mayInferTypeForVariableFromAsJson,
        )
    }

    @Test
    fun typingOrder() {
        val plan = planFor(
            """
            |if (condition) {
            |  f1();
            |  f2();
            |} else {
            |  g()
            |}
            |h()
            """.trimMargin(),

            stage = Stage.Type,
        )
        assertStructure(
            """
            |[
            |  "condition",
            |  "f1",
            |  "f2",
            |  "g",
            |  "h", // h is before g in depth-first, but not when we follow branches in parallel.
            |]
            """.trimMargin(),

            JsonValueBuilder.build(emptyMap()) {
                value(plan.typeOrder.mapNotNull { (it as? RightNameLeaf)?.content?.builtinKey })
            },
        )
    }

    @Test
    fun initializers() {
        val plan = planFor(
            // Below are the initializer examples from TyperPlan's documentation.
            """
            |let a = 0;
            |// (0) is the initializer for `a`
            |
            |let b;
            |b = 1;
            |// (1) is the initializer for `b`
            |
            |var c;
            |c = c;
            |// (c) is the initializer for `c` but the read of `c` types to Invalid.
            |
            |var d;
            |if (condition) {
            |    d = "f()";
            |} else {
            |    d = null;
            |}
            |// (f()) and (null) are both initializers for `d`.
            |d = "g()"; // (g()) is not an initializer for `d`.
            |
            |var e = null;
            |e = "foo()";
            |// (null) is the initializer for `e`, but (null) is usually
            |// a placeholder, so we keep looking.  (foo()) is not.
            |
            |let f;
            |// There are zero initializers for `f`.
            |
            |let g: String;
            |g = "whatever";
            |// g has a declared type, so its initializers are never used to infer a type.
            |
            |let h;
            |if (condition) {
            |    h = "[]";
            |} else {
            |    h = "[foo]";
            |}
            |
            |let i;
            |let j;
            |i = j = 2;
            """.trimMargin(),
            stage = Stage.Type,
        )
        assertStructure(
            """
            |{
            |  a__0:      ["0"],
            |  b__0:      ["1"],
            |  c__0:      ["c__0"],
            |  d__0:      ["\"f()\"", "null"],
            |  e__0:      ["null", "\"foo()\""],
            |  g__0:      ["\"whatever\""],
            |  h__0:      ["\"[]\"", "\"[foo]\""],
            |  "t#0":     ["2"],
            |  j__0:      ["t#0"],
            |  "t#1":     ["t#0"],
            |  i__0:      ["t#1"],
            |  return__0: ["void"], // Implied
            |}
            """.trimMargin(),
            plan.initializersAsJson(),
            postProcessor = postProcessor(),
        )
    }

    @Test
    fun moreInitializers() {
        val plan = planFor(
            // Don't bother to indent the big do block in this case.
            """
                |do {
                |var a;
                |if (condition) {
                |  call();
                |}
                |a = 1;
                |if (condition) {
                |  a = 2;  // Not an initializer
                |}
                |
                |var b;
                |if (condition) {
                |  call();
                |}
                |if (condition) {
                |  b = 1;
                |  call(b);
                |  b = 2; // Not
                |} else {
                |  b = 3;
                |  call();
                |  b = 4; // Not
                |}
                |b = 5; // Not
                |
                |var c;
                |// The assignment in the lambda appears lexically first, so
                |// we treat that as the initializer.
                |mightBeAffine {
                |  c = 1;
                |}
                |c = 2; // Not
                |
                |var d;
                |mightBeAffine {
                |  if (condition) {
                |    d = 1
                |  }
                |}
                |d = 2; // Not
                |
                |var e;
                |if (condition) {
                |  e = 1;
                |}
                |mightBeAffine {
                |  e = 2; // Not
                |}
                |
                |var f;
                |mightBeAffine {
                |  if (condition) {
                |    f = 1;
                |  }
                |}
                |mightBeAffine {
                |  if (!condition) {
                |    f = 2; // Not
                |  }
                |}
                |f = 3; // Not
                |call();
                |f = 4; // Not
                |call(f);
                |
                |var g;
                |let someFun() {
                |  call(g);
                |}
                |g = "assigned after def of using function";
                |someFun();
                |g = "another value assigned"; // Not
                |
                |var h = null;
                |if (condition) {
                |  h = "some not null value";
                |}
                |}
            """.trimMargin(),
            stage = Stage.Type,
        )

        assertStructure(
            """
            |{
            |  a__0:      ["1"],
            |  b__0:      ["1", "3"],
            |  c__0:      ["1", "2"], // TODO Shouldn't have "2" in it!!!
            |  d__0:      ["1"],
            |  e__0:      ["1"],
            |  f__0:      ["1"],
            |  g__0:      ["\"assigned after def of using function\""],
            |  h__0:      ["null", "\"some not null value\""],
            |}
            """.trimMargin(),
            plan.initializersAsJson {
                "#" !in it && !it.startsWith("return__") && !it.startsWith("someFun")
            },
            postProcessor = postProcessor(),
        )
    }

    @Test
    fun initializerMissingOnOneBranch() {
        val plan = planFor(
            """
            |var x;
            |
            |if (condition) {
            |  x = 0;
            |} else {
            |  // Missing initializer here
            |}
            |x = 1; // Not an initializer, for purposes of typing since at least one preceding
            |       // branch initializes it.
            |
            |void;
            """.trimMargin(),
            stage = Stage.Type,
        )
        assertStructure(
            """
            |{
            |  x__0: ["0"],
            |  return__0: ["void"],
            |}
            """.trimMargin(),
            plan.initializersAsJson(),
            postProcessor = postProcessor(),
        )
    }

    @Test
    fun initializersNestedFunctions() {
        val plan = planFor(
            """
            |var x;
            |
            |f() {
            |  x = 0; // This appears in a lambda but lexically before the x = 1.
            |         // If we had a way to distinguish affine lambdas from non-affine lambdas,
            |         // we could do something differently, but we're using these initializers
            |         // for type inference; we don't rely, in the Typer, on initializers executing
            |         // before other code.
            |}
            |
            |x = 1;
            """.trimMargin(),
            stage = Stage.Type,
        )
        assertStructure(
            """
            |{
            |  x__0: ["0", "1"], // TODO Shouldn't have "1" in it!!!
            |  return__0: ["void"],
            |  return__1: ["void"],
            |}
            """.trimMargin(),
            plan.initializersAsJson(),
            postProcessor = postProcessor(),
        )
    }

    @Test
    fun initializerAtStartOfLoop() {
        val plan = planFor(
            """
            |var i = 0;
            |while (i < 3) { i += 1; }
            |void;
            """.trimMargin(),
            Stage.Type,
        )
        assertStructure(
            """
            |{
            |    "i__0": ["0"],
            |    "return__0": ["void"]
            |}
            """.trimMargin(),
            plan.initializersAsJson(),
            postProcessor = postProcessor(),
        )
    }

    @Test
    fun maskingNames() {
        val plan = planFor(
            """
            |var x;
            |
            |f() {
            |  let x = 0; // This x masks the outer x, but their initializers are distinct.
            |}
            |
            |x = 1;
            """.trimMargin(),
            stage = Stage.Type,
        )
        assertStructure(
            """
            |{
            |  x__0: ["0"],
            |  return__0: ["void"],
            |  x__1: ["1"],
            |  return__1: ["void"],
            |}
            """.trimMargin(),
            plan.initializersAsJson(),
            postProcessor = postProcessor(),
        )
    }

    @Test
    fun multiVisited() {
        val plan = planFor(
            """
            |if (foo()) {
            |    foo()
            |} else {
            |}
            """.trimMargin(),
            stage = Stage.Type,
        )
        assertStructure(
            """
            |{
            |  "t#0": ["hs(fail#0, foo())"],
            |  "t#1": ["hs(fail#1, foo())"],
            |  "t#2": ["void", "t#1"],
            |  // There should only be one mention of t#2 here even though it's reached via both
            |  // branches through the `if`.
            |  "return__1": ["t#2"],
            |}
            """.trimMargin(),
            plan.initializersAsJson(),
            postProcessor = postProcessor(),
        )
    }

    @Test
    fun aliasedCall() {
        val plan = planFor(
            """
                |// f(0) is aliased via a
                |let a;
                |a = f(0);
                |g(a);
                |
                |// f(1) is aliased transitively via c
                |let b;
                |let c;
                |b = f(1);
                |c = b;
                |g(c);
                |
                |// f(2) is not aliased since d is multiply read.
                |let d;
                |d = f(2);
                |g(d, d);
                |
                |// f(3) is not aliases since its sole use is not used as a parameter to a call.
                |// It's just an initializer for e.
                |let e;
                |e = f(3);
                |// But there is just one use, so it meets our definition.
                |// We filter out uses that are not in regular calls in Typer.
                |e;
                |
            """.trimMargin(),
            Stage.DisAmbiguate,
        )
        assertStructure(
            """
            |{
            |  a: "f(0); a = f(0)",
            |  c: "f(1); b = f(1)",
            |  e: "f(3); e = f(3)",
            |}
            """.trimMargin(),
            plan.aliasedCallsAsJson,
        )
    }

    @Test
    fun expressionsUsedInConditions() {
        val plan = planFor(
            """
            |if (a) {
            |    x;
            |} else if (b) {
            |    y;
            |} else {
            |    while (c) {
            |        z;
            |    }
            |}
            """.trimMargin(),
            Stage.Type,
        )
        assertEquals(
            listOf("a", "b", "c"),
            plan.usedAsCondition.map { it.target.toPseudoCode() },
        )
    }

    @Test
    fun locallyDeclaredClass() {
        val plan = planFor(
            """
            |class C { private p: Int; }
            """.trimMargin(),
            Stage.Define,
        )
        assertStructure(
            """
            |{
            |  p__0: "PropertyShape(C__0.p)",
            |  constructor__0: "MethodShape(C__0.constructor, Constructor)",
            |}
            """.trimMargin(),
            plan.nameToLocalMemberShapesAsJson,
            postProcessor = postProcessor(),
        )
    }

    @Test
    fun locallyDeclaredInterface() {
        val plan = planFor(
            """
            |interface I {
            |  f(): Void;
            |  static let s = 42;
            |}
            """.trimMargin(),
            Stage.Define,
        )
        assertStructure(
            """
            |{
            |  f__0: "MethodShape(I__0.f, Normal)",
            |  s__0: "StaticPropertyShape(I__0.s)",
            |}
            """.trimMargin(),
            plan.nameToLocalMemberShapesAsJson,
            postProcessor = postProcessor(),
        )
    }

    @Test
    fun returnNames() {
        val plan = planFor(
            """
            |let f(): String { "from f" }
            |let nym`return` = "lies";
            |f()
            """.trimMargin(),
            Stage.Type,
        )
        assertStringsEqual(
            """
            |let return__4, @fn f__1;
            |f__1 = (@stay fn f /* return__0 */: String {
            |    fn__3: do {
            |      return__0 = "from f"
            |    }
            |});
            |let return__2;
            |return__2 = "lies";
            |return__4 = "from f"
            |
            """.trimMargin(),
            plan.root.toPseudoCode(singleLine = false),
        )
        assertEquals(
            listOf(
                // return__3 is from user code, but is not a return name
                "return__4",
                "return__0",
            ),
            plan.returnNames.map { it.rawDiagnostic },
        )
    }

    @Test
    fun deferredFun() {
        val plan = planFor(
            """
            |let f(i: Int, g: fn (Int): Int): Int { g(i) + 1 }
            |let j = f(1) { k => 2 * k };
            """.trimMargin(),
            stage = Stage.Type,
        )
        assertStructure(
            // The key point here is to see `f(...)` before `2 * k`.
            // That is, the call to `f` before the contents of the lambda.
            """
            |[
            |  "g(i)",
            |  "hs(fail, g(i))",
            |  "t + 1",
            |  "fn ...",
            |  "f(1, fn ...)",
            |  "2 * k",
            |  "hs(fail, f(1, fn ...))",
            |  "bubble()",
            |  "bubble()",
            |]
            """.trimMargin(),
            JsonValueBuilder.build(emptyMap()) {
                value(trimToNonAssignmentAndFnCalls(plan.typeOrder))
            },
        )
    }

    @Test
    fun mutuallyReferencingFunctions() {
        val plan = planFor(
            """
            |let sign(x: Int): Int { x == 0 ? 0 : x < 0 ? -1 : 1 }
            |let isOdd(x: Int): Boolean { x != 0 && isEven(x - sign(x)) }
            |let isEven(x: Int): Boolean { !isOdd(x - sign(x)) }
            """.trimMargin(),
            stage = Stage.Type,
        )
        assertStructure(
            // Arbitrary reorder for cyclical dependency.
            """
                |{
                |    "sign__4": [
                |        "@stay fn sign(x__7 /* aka x */: Int32) /* return__1 */: Int32 {...}"
                |    ],
                |    "isEven__6": [
                |        "@stay fn isEven(x__11 /* aka x */: Int32) /* return__3 */: Boolean {...}"
                |    ],
                |    "isOdd__5": [
                |        "@stay fn isOdd(x__9 /* aka x */: Int32) /* return__2 */: Boolean {...}"
                |    ],
                |}
            """.trimMargin(),
            plan.initializersAsJson(
                expressionDetail = PseudoCodeDetail.default.copy(elideFunctionBodies = true),
            ) {
                // Focus on the function expressions
                it.startsWith("sign") || it.startsWith("is")
            },
        )
    }

    @Test
    fun orElsePanic() {
        val plan = planFor(
            """
                |let f(s: String): Boolean {
                |  let x = s.toFloat64() orelse panic();
                |  x > 0.0
                |}
            """.trimMargin(),
            stage = Stage.Type,
        )
        assertStringsEqual(
            """
                |let return__9, @fn f__1;
                |f__1 = fn f(s__2 /* aka s */: String) /* return__0 */: Boolean {
                |  var t#7, t#8;
                |  fn__3: do {
                |    var fail#6;
                |    let x__4;
                |    orelse#5: {
                |      t#7 = hs(fail#6, do_bind_toFloat64(s__2)());
                |      if (fail#6) {
                |        break orelse#5;
                |      };
                |      t#8 = t#7
                |    } orelse {
                |## This is not an initializer for t#8
                |## And this assignment needs to be typed after t#8's initializer
                |## so that its context can be used to compute Never<Float64> as a type.
                |      t#8 = panic()
                |    };
                |    x__4 = t#8;
                |    return__0 = x__4 > 0.0
                |  }
                |};
                |return__9 = void
                |
            """.trimMargin().stripDoubleHashCommentLinesToPutCommentsInlineBelow(),
            plan.root.toPseudoCode(singleLine = false),
        )
        assertStructure(
            """
                |{
                |    "f__1": [
                |        "fn f(s__2 /* aka s */: String) /* return__0 */: Boolean {var t#7, t#8; fn__3: do {var fail#6; let x__4; orelse#5: {t#7 = hs(fail#6, do_bind_toFloat64(s__2)()); if (fail#6) {break orelse#5;}; t#8 = t#7} orelse {t#8 = panic()}; x__4 = t#8; return__0 = x__4 \u003e 0.0}}"
                |    ],
                |    "t#7": [
                |        "hs(fail#6, do_bind_toFloat64(s__2)())"
                |    ],
                |    "t#8": [
                |## No panic()
                |        "t#7"
                |    ],
                |    "x__4": [
                |        "t#8"
                |    ],
                |    "return__0": [
                |        "x__4 \u003e 0.0"
                |    ],
                |    "return__9": [
                |        "void"
                |    ]
                |}
            """.trimMargin().stripDoubleHashCommentLinesToPutCommentsInlineBelow(),
            plan.initializersAsJson(),
        )
        assertStructure(
            """
                |[
                |  "do_bind_toFloat64(s)",
                |  "do_bind_toFloat64(s)()",
                |  "hs(fail, do_bind_toFloat64(s)())",
                |  "x \u003e 0.0",
                |  "fn ...",
                |  "panic()",
                |]
            """.trimMargin(),
            JsonValueBuilder.build(emptyMap()) {
                value(trimToNonAssignmentAndFnCalls(plan.typeOrder))
            },
        )
    }

    private fun planFor(
        source: String,
        stage: Stage,
        moduleResultNeeded: Boolean = true,
        replaceTemporaries: Boolean = false,
    ): TyperPlan {
        var typerPlanRoot: BlockTree? = null

        val logSink = ListBackedLogSink()

        // Sample the module root before running the Typer.
        val snapshottingConsole = Console(
            console.textOutput,
            console.level,
            object : Snapshotter {
                override fun <IR : Structured> snapshot(
                    key: SnapshotKey<IR>,
                    stepId: String,
                    state: IR,
                ) {
                    if (stepId == Debug.Frontend.TypeStage.AfterExplicitResults.loggerName) {
                        AstSnapshotKey.useIfSame(key, state) {
                            typerPlanRoot = it.copy() as BlockTree
                        }
                    }
                }
            },
        )

        val module = Module(logSink, testModuleName, snapshottingConsole, { true })
        if (moduleResultNeeded) {
            module.addEnvironmentBindings(
                mapOf(StagingFlags.moduleResultNeeded to TBoolean.valueTrue),
            )
        }
        Debug.configure(module, snapshottingConsole)

        module.deliverContent(
            ModuleSource(
                filePath = testCodeLocation,
                fetchedContent = source,
                languageConfig = StandaloneLanguageConfig,
            ),
        )
        while (true) {
            val stageCompleted = module.stageCompleted
            if (stageCompleted != null && stageCompleted >= stage) { break }
            check(module.canAdvance()) { "$stageCompleted" }
            module.advance()
            if (replaceTemporaries && module.stageCompleted == stage) {
                module.treeForDebug?.rewriteTNames()
            }
        }
        Debug.configure(module, null)

        if (typerPlanRoot == null) {
            typerPlanRoot = module.treeForDebug
        }
        return TyperPlan(typerPlanRoot!!, module.outputName)
    }

    // Rewrite name suffix numbers in maps
    private fun postProcessor(): ((Structured) -> Structured) = { s ->
        PseudoCodeNameRenumberer.newStructurePostProcessor()(s)
    }
}

private val TyperPlan.mayInferTypeForVariableFromAsJson
    get() = let { plan ->
        JsonValueBuilder.build(emptyMap()) {
            obj {
                for ((name, pairs) in plan.mayInferTypeForVariableFrom) {
                    key(name.rawDiagnostic) {
                        value(pairs.map { it.rawDiagnostic }.sorted())
                    }
                }
            }
        }
    }

private fun TyperPlan.initializersAsJson(
    expressionDetail: PseudoCodeDetail = PseudoCodeDetail.default,
    filter: (String) -> Boolean = { true },
): JsonValue = let { plan ->
    JsonValueBuilder.build(emptyMap()) {
        obj {
            for ((name, expressions) in plan.initializers) {
                val nameKey = name.rawDiagnostic
                if (filter(nameKey)) {
                    key(nameKey) {
                        arr {
                            expressions.forEach {
                                value(it.toPseudoCode(detail = expressionDetail))
                            }
                        }
                    }
                }
            }
        }
    }
}

private val TyperPlan.aliasedCallsAsJson
    get() = let { plan ->
        JsonValueBuilder.build(emptyMap()) {
            obj {
                for ((name, aliasedCall) in plan.aliasedCalls) {
                    key(name.rawDiagnostic) {
                        value(
                            "${
                                aliasedCall.aliased.toPseudoCode()
                            }; ${
                                aliasedCall.assignment.toPseudoCode()
                            }",
                        )
                    }
                }
            }
        }
    }

private val TyperPlan.nameToLocalMemberShapesAsJson
    get() = let { plan ->
        JsonValueBuilder.build(emptyMap()) {
            obj {
                for ((name, m) in plan.namesToLocalMemberShapes) {
                    val t = m.enclosingType.name.rawDiagnostic
                    val symbol = m.symbol.text
                    key(name.rawDiagnostic) {
                        value(
                            when (m) {
                                is TypeParameterShape -> "TypeParameterShape($t.$symbol)"
                                is MethodShape -> "MethodShape($t.$symbol, ${m.methodKind})"
                                is PropertyShape -> "PropertyShape($t.$symbol)"
                                is StaticPropertyShape -> "StaticPropertyShape($t.$symbol)"
                            },
                        )
                    }
                }
            }
        }
    }

private fun trimToNonAssignmentAndFnCalls(order: Iterable<Tree>) = order
    .filter {
        when (it) {
            is CallTree -> !isAssignment(it)
            is FunTree -> true
            else -> false
        }
    }
    .map { it.toPseudoCode(detail = noFnDetail) }
    .map { it.replace(Regex("""(__|#)\d+|@\S+ """), "") }
    .map { it.replace(Regex("""fn [^}]+[}]"""), "fn ...") }

private val noFnDetail = PseudoCodeDetail.default.copy(elideFunctionBodies = true)
