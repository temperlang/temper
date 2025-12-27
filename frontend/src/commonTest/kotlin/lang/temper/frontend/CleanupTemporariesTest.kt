@file:Suppress("MaxLineLength")

package lang.temper.frontend

import lang.temper.ast.TreeVisit
import lang.temper.builtin.BuiltinFuns
import lang.temper.common.Console
import lang.temper.common.ListBackedLogSink
import lang.temper.common.Log
import lang.temper.common.SnapshotKey
import lang.temper.common.Snapshotter
import lang.temper.common.assertStringsEqual
import lang.temper.common.assertStructure
import lang.temper.common.console
import lang.temper.common.stripDoubleHashCommentLinesToPutCommentsInlineBelow
import lang.temper.common.structure.Hints
import lang.temper.common.structure.StructureSink
import lang.temper.common.structure.Structured
import lang.temper.common.testCodeLocation
import lang.temper.common.testModuleName
import lang.temper.common.withCapturingConsole
import lang.temper.env.InterpMode
import lang.temper.frontend.staging.ModuleAdvancer
import lang.temper.frontend.staging.ModuleConfig
import lang.temper.frontend.staging.ModuleCustomizeHook
import lang.temper.frontend.staging.partitionSourceFilesIntoModules
import lang.temper.fs.FileFilterRules
import lang.temper.fs.FilteringFileSystemSnapshot
import lang.temper.fs.MemoryFileSystem
import lang.temper.log.CodeLocationKey
import lang.temper.log.Debug
import lang.temper.log.FilePath
import lang.temper.log.LogEntry
import lang.temper.log.Position
import lang.temper.log.SharedLocationContext
import lang.temper.log.filePath
import lang.temper.log.toReadablePosition
import lang.temper.name.BuiltinName
import lang.temper.name.DashedIdentifier
import lang.temper.name.ModuleName
import lang.temper.name.NameMaker
import lang.temper.name.ParsedName
import lang.temper.name.PseudoCodeNameRenumberer
import lang.temper.name.SourceName
import lang.temper.name.TemperName
import lang.temper.name.Temporary
import lang.temper.stage.Stage
import lang.temper.type.WellKnownTypes
import lang.temper.type2.MkType2
import lang.temper.type2.Signature2
import lang.temper.value.ActualValues
import lang.temper.value.BlockTree
import lang.temper.value.BuiltinStatelessCallableValue
import lang.temper.value.CallTree
import lang.temper.value.CallableValue
import lang.temper.value.DeclTree
import lang.temper.value.Document
import lang.temper.value.InterpreterCallback
import lang.temper.value.LeftNameLeaf
import lang.temper.value.NameLeaf
import lang.temper.value.NamedBuiltinFun
import lang.temper.value.NotYet
import lang.temper.value.PartialResult
import lang.temper.value.Planting
import lang.temper.value.RightNameLeaf
import lang.temper.value.StaylessMacroValue
import lang.temper.value.TBoolean
import lang.temper.value.TInt
import lang.temper.value.UnpositionedTreeTemplate
import lang.temper.value.Value
import lang.temper.value.ValueLeaf
import lang.temper.value.functionContained
import lang.temper.value.qNameSymbol
import lang.temper.value.toLispy
import lang.temper.value.toPseudoCode
import lang.temper.value.unpackOrFail
import lang.temper.value.unpackPositionedOr
import lang.temper.value.varSymbol
import lang.temper.value.void
import kotlin.test.Test
import kotlin.test.assertEquals

class CleanupTemporariesTest {
    @Test
    fun chainOfTemporaries() {
        val r = doCleanupTemporaries(
            """
                |do {
                |  let toLogOrNotToLog;
                |  var t1 = randomBool();
                |  var t2 = t1;
                |  var t3 = t2;
                |  var t4 = t3;
                |  toLogOrNotToLog = t4;
                |  if (toLogOrNotToLog) {
                |    console.log("k");
                |  }
                |}
            """.trimMargin(),
        )

        assertStructure(
            """
                |{
                |  pseudoCodeBefore: ```
                |    var t#0, fail#0, fail#1;
                |    let console#0;
                |    t#0 = getConsole();
                |    console#0 = t#0;
                |    let toLogOrNotToLog__0;
                |    var t1#0;
                |    t1#0 = randomBool();
                |    var t2#0;
                |    t2#0 = t1#0;
                |    var t3#0;
                |    t3#0 = t2#0;
                |    var t4#0;
                |    t4#0 = t3#0;
                |    toLogOrNotToLog__0 = t4#0;
                |    if (toLogOrNotToLog__0) {
                |      do_bind_log(console#0)("k")
                |    }
                |
                |    ```,
                |  allEdits: [
                |      [
                |        "Replace(L9: rename read console#0 to t#0)",
                |        "Replace(L1: console#0=... -> no-op)",
                |        "Replace(L6: rename written t4#0 to toLogOrNotToLog__0)",
                |        "Replace(L7: toLogOrNotToLog__0=... -> no-op)",
                |        "Replace(L5: rename read t2#0 to t1#0)",
                |        "Replace(L4: t2#0=... -> no-op)"
                |      ],
                |      [
                |        "Replace(L5: rename written t3#0 to toLogOrNotToLog__0)",
                |        "Replace(L6: toLogOrNotToLog__0=... -> no-op)"
                |      ],
                |      [
                |        "Replace(L3: rename written t1#0 to toLogOrNotToLog__0)",
                |        "Replace(L5: toLogOrNotToLog__0=... -> no-op)"
                |      ],
                |      [
                |        "Replace(L1: let fail#0 -> no-op)",
                |        "Replace(L1: let fail#1 -> no-op)",
                |        "Replace(L1: let console#0 -> no-op)",
                |        "Replace(L3: let t1#0 -> no-op)",
                |        "Replace(L4: let t2#0 -> no-op)",
                |        "Replace(L5: let t3#0 -> no-op)",
                |        "Replace(L6: let t4#0 -> no-op)"
                |      ],
                |      []
                |    ],
                |
                |  pseudoCodeAfter: ```
                |    var t#0;
                |    t#0 = getConsole();
                |    let toLogOrNotToLog__0;
                |    toLogOrNotToLog__0 = randomBool();
                |    if (toLogOrNotToLog__0) {
                |      do_bind_log(t#0)("k")
                |    }
                |
                |    ```,
                |}
            """.trimMargin(),
            r,
        )
    }

    @Test
    fun doNotOrderAssignmentBeforeDeclaration() {
        val r = doCleanupTemporaries(
            """
                |do {
                |  var t1 = randomBool();
                |  var t2 = t1;
                |  var t3 = t2;
                |  var t4 = t3;
                |  // This is like the last example, except that
                |  // the declaration for t_toLogOrNotToLog is
                |  // after the initializer for t4.  Naïvely
                |  // renaming `t4 = ...` to `t_toLogOrNotToLog = ...`
                |  // would result in initialization before
                |  // declaration which would be odd.
                |  let t_toLogOrNotToLog = t4;
                |  if (t_toLogOrNotToLog) {
                |    console.log("j");
                |  }
                |  // Also, there are two reads of t_toLogOrNotToLog, so
                |  // we do not inline the call to randomBool() which
                |  // makes sure we don't mis-order and then hide that fact.
                |  if (t_toLogOrNotToLog) {
                |    console.log("k");
                |  }
                |}
            """.trimMargin(),
            moduleResultNeeded = true,
        )

        assertStructure(
            """
                |{
                |  pseudoCodeAfter: ```
                |    let return__0;
                |    var t#0;
                |    t#0 = getConsole();
                |    var t1#0;
                |    t1#0 = randomBool();
                |    if (t1#0) {
                |      do_bind_log(t#0)("j")
                |    };
                |    if (t1#0) {
                |      do_bind_log(t#0)("k")
                |    };
                |    return__0 = void
                |
                |    ```,
                |}
            """.trimMargin(),
            r,
        )
    }

    @Test
    fun varAcrossTwoBranches() {
        val r = doCleanupTemporaries(
            input = """
                |do {
                |  // t is assigned in two locations to x
                |  var t; // `t` names are rewritten to temporaries by the test harness
                |  let x;
                |  if (randomBool()) {
                |    t = 1;
                |  } else {
                |    t = 2;
                |  }
                |  x = t;
                |  x.toString()
                |}
                |
            """.trimMargin(),
            moduleResultNeeded = true,
        )
        // Here's what reached CleanupTemporaries
        assertStructure(
            """
                |{
                |  pseudoCodeBefore: ```
                |    let return__9;
                |    var t#6, t#7, t#8, fail#5, t#3;
                |    let x__2;
                |    if (randomBool()) {
                |      t#3 = 1
                |    } else {
                |      t#3 = 2
                |    };
                |    x__2 = t#3;
                |    t#6 = do_bind_toString(x__2)();
                |    t#7 = t#6;
                |    t#8 = t#7;
                |    return__9 = t#8
                |
                |    ```,
                |
                |  readsAndWrites0: {
                |    "reads": {
                |      "return__9":                ["R(return__9 @ L12)"],  // implied read by caller
                |      "t#3":                      ["R(t#3 @ L10)"],
                |      "x__2":                     ["R(x__2 @ L11)"],
                |      "t#6":                      ["R(t#6 @ L11)"],
                |      "t#7":                      ["R(t#7 @ L1)"],
                |      "t#8":                      ["R(t#8 @ L1)"],
                |    },
                |    "writes": {
                |      "t#3":                      ["W(t#3 A @ L6)", "W(t#3 A @ L8)"],
                |      "x__2":                     ["W(x__2 A @ L10)"],
                |      "t#6":                      ["W(t#6 A @ L11)"],
                |      "t#7":                      ["W(t#7 A @ L11)"],
                |      "t#8":                      ["W(t#8 A @ L1)"],
                |      "return__9":                ["W(return__9 A @ L1)"]
                |    },
                |    "upstream": {
                |      "R(t#3 @ L10)":             ["W(t#3 A @ L6)", "W(t#3 A @ L8)"],
                |      "R(x__2 @ L11)":            ["W(x__2 A @ L10)"],
                |      "R(t#6 @ L11)":             ["W(t#6 A @ L11)"],
                |      "R(t#7 @ L1)":              ["W(t#7 A @ L11)"],
                |      "R(t#8 @ L1)":              ["W(t#8 A @ L1)"],
                |      "R(return__9 @ L12)":       ["W(return__9 A @ L1)"]
                |    },
                |    "downstream": {
                |      "W(t#3 A @ L6)":            ["R(t#3 @ L10)"],
                |      "W(t#3 A @ L8)":            ["R(t#3 @ L10)"],
                |      "W(x__2 A @ L10)":          ["R(x__2 @ L11)"],
                |      "W(t#6 A @ L11)":           ["R(t#6 @ L11)"],
                |      "W(t#7 A @ L11)":           ["R(t#7 @ L1)"],
                |      "W(t#8 A @ L1)":            ["R(t#8 @ L1)"],
                |      "W(return__9 A @ L1)":      ["R(return__9 @ L12)"]
                |    },
                |  },
                |
                |  allEdits: [
                |    // First round through, we reduce the number of names by
                |    // eliminating some temporaries.
                |    [
                |      "Replace(L1: rename written t#8 to return__9)",
                |      "Replace(L1: return__9=... -> no-op)",
                |      // Eliminating t#6..t#8 in favour of return__7 over two steps here and next.
                |      "Replace(L1: rename read t#7 to t#6)",
                |      "Replace(L11: t#7=... -> no-op)",
                |      // We prefer eliminating temporaries
                |      "Replace(L6: rename written t#3 to x__2)",
                |      "Replace(L8: rename written t#3 to x__2)",
                |      // We don't need `x__2 = t#3` since t#3 has gone away.
                |      "Replace(L10: x__2=... -> no-op)",
                |    ],
                |    [
                |      "Replace(L11: rename written t#6 to return__9)",
                |      "Replace(L1: return__9=... -> no-op)",
                |    ],
                |    // And finally we sweep up some declarations.
                |    [
                |      "Replace(L11: let t#6 -> no-op)",
                |      "Replace(L11: let t#7 -> no-op)",
                |      "Replace(L1: let t#8 -> no-op)",
                |      "Replace(L1: let fail#5 -> no-op)",
                |      "Replace(L3: let t#3 -> no-op)",
                |    ],
                |    // And when we find no edits, we know to stop.
                |    []
                |  ],
                |
                |  pseudoCodeAfter: ```
                |    let return__9;
                |    let x__2;
                |    if (randomBool()) {
                |      x__2 = 1
                |    } else {
                |      x__2 = 2
                |    };
                |    return__9 = do_bind_toString(x__2)();
                |
                |    ```,
                |}
            """.trimMargin(),
            r,
        )
    }

    @Test
    fun doNotSweepUpUseBeforeInitialization() {
        val r = doCleanupTemporaries(
            """
                |do {
                |  let t_a;
                |  var t_b;
                |  t_b = randomBool();
                |  if (randomBool()) {
                |    t_a = t_b;
                |  }
                |  t_a // ERROR: Not initialized in implied `else` above.
                |}
            """.trimMargin(),
            moduleResultNeeded = true,
        )

        @Suppress("SpellCheckingInspection") // cut-off word in error message
        assertStructure(
            """
                |{
                |  pseudoCodeBefore: ```
                |    let return__5;
                |    var t#3, t#4;
                |    let t_a#0;
                |    var t_b#2;
                |    t_b#2 = randomBool();
                |    if (randomBool()) {
                |      t_a#0 = t_b#2
                |    };
                |    t#3 = error (UseBeforeInitialization);
                |    t#4 = t#3;
                |    return__5 = t#4
                |
                |    ```,
                |  allEdits: [
                |    [
                |      "Replace(L1: rename written t#4 to return__5)",
                |      "Replace(L1: return__5=... -> no-op)",
                |      "Replace(L6: t_a#0=... -> no-op)",
                |    ],
                |    [
                |      "Replace(L8: rename written t#3 to return__5)",
                |      "Replace(L1: return__5=... -> no-op)",
                |    ],
                |    [
                |      "Replace(L4: simplify dead-store of t_b#2)",
                |    ],
                |    [
                |      "Replace(L8: let t#3 -> no-op)",
                |      "Replace(L1: let t#4 -> no-op)",
                |      "Replace(L2: let t_a#0 -> no-op)",
                |      "Replace(L3: let t_b#2 -> no-op)",
                |    ],
                |    []
                |  ],
                |  pseudoCodeAfter: ```
                |    let return__5;
                |    randomBool();
                |    if (randomBool()) {};
                |    return__5 = error (UseBeforeInitialization);
                |
                |    ```,
                |  consoleOutput: ```
                |      8: t_a // ERROR: Not initi
                |         ┗━┛
                |      [test/test.temper:8+2-5]@T: t_a#0 is not initialized along branches at [:7+3]
                |      7: }
                |          ⇧
                |
                |    ```,
                |}
            """.trimMargin(),
            r,
        )
    }

    @Test
    fun voidNotStoredInTemporary() {
        val r = doCleanupTemporaries(
            """
                |let f(): Void {
                |  if (randomBool()) {
                |    console.log("Random")
                |  };
                |}
                |f()
            """.trimMargin(),
            moduleResultNeeded = true,
        )

        assertStructure(
            """
                |{
                |  allEdits: [
                |    [
                |      "SplitAssignment(L6: split void assignment of t#0)",
                |      "SplitAssignment(L6: split void assignment of return__0)"
                |    ],
                |    [
                |      "Replace(L6: read t#0 -> no-op)"
                |    ],
                |    [
                |      "Replace(L6: simplify dead-store of t#0)",
                |    ],
                |    [
                |      "Replace(L1: inlined assignment of t#1 -> no-op)",
                |      "Replace(L1: inline value assigned to t#1 at sole read)"
                |    ],
                |    [
                |      "Replace(L1: let t#1 -> no-op)",
                |      "Replace(L6: let t#0 -> no-op)",
                |      "Replace(L1: let fail#0 -> no-op)",
                |      "Replace(L1: let fail#1 -> no-op)",
                |    ],
                |    [],
                |    [
                |      "SplitAssignment(L3: split void assignment of t#2)"
                |    ],
                |    [
                |      "Replace(L3: read t#2 -> no-op)"
                |    ],
                |    [
                |      "Replace(L3: simplify dead-store of t#2)"
                |    ],
                |    [
                |      "Replace(L3: let t#2 -> no-op)",
                |      "Replace(L1: let fail#2 -> no-op)",
                |    ],
                |    []
                |  ],
                |
                |  pseudoCodeAfter: ```
                |    let return__0;
                |    let console#0;
                |    console#0 = getConsole();
                |    @fn let f__0;
                |    f__0 = fn f /* return__1 */: Void {
                |      fn__0: do {
                |        if (randomBool()) {
                |          do_bind_log(console#0)("Random");
                |        };
                |        return__1 = void
                |      }
                |    };
                |    f__0();
                |    return__0 = void
                |
                |    ```,
                |}
            """.trimMargin(),
            r,
        )
    }

    @Test
    fun doNotEatExports() {
        val r = doCleanupTemporaries(
            """
                |export let answer = 42
            """.trimMargin(),
            // Test that declarations result in `void` even in a REPL-like context.
            moduleResultNeeded = true,
        )

        assertStructure(
            """
                |{
                |  pseudoCodeAfter: ```
                |    let return__0, `test//`.answer;
                |    `test//`.answer = 42;
                |    return__0 = void
                |
                |    ```,
                |}
            """.trimMargin(),
            r,
        )
    }

    @Test
    fun enumMembersNotLeftInTemporaryLimbo() {
        val r = doCleanupTemporaries(
            """
                |enum E {
                |  A, B
                |}
            """.trimMargin(),
        )

        @Suppress("SpellCheckingInspection") // auto-generated names
        assertStructure(
            """
                |{
                |  pseudoCodeBefore: ```
                |    var t#0, t#1, fail#0, fail#1;
                |    @constructorProperty @visibility(\public) @stay @fromType(E__0) let ordinal__0: Int32;
                |    @constructorProperty @visibility(\public) @stay @fromType(E__0) let name__0: String;
                |    @visibility(\public) @enumMember @static @stay @fromType(E__0) let A__0;${
                "" // Here, the initializer for a member got pulled out into a temporary
            }
                |    t#0 = new E__0(0, "A");
                |    A__0 = t#0;
                |    @visibility(\public) @enumMember @static @stay @fromType(E__0) let B__0;
                |    t#1 = new E__0(1, "B");
                |    B__0 = t#1;
                |    @fn @visibility(\public) @stay @fromType(E__0) let constructor__0;
                |    constructor__0 = (@stay fn constructor(@impliedThis(E__0) this__0: E__0, ordinal__1 /* aka ordinal */: Int32, name__1 /* aka name */: String) /* return__1 */: Void {
                |        var t#2, t#3, t#4;
                |        let t#5;
                |        t#5 = ordinal__1;
                |        t#2 = t#5;
                |        setp(ordinal__0, this__0, t#2);
                |        t#5;
                |        let t#6;
                |        t#6 = name__1;
                |        t#3 = t#6;
                |        setp(name__0, this__0, t#3);
                |        t#4 = t#6;
                |        t#4;
                |        return__1 = void
                |    });
                |    @fn @visibility(\public) @stay @fromType(E__0) let getordinal__0;
                |    getordinal__0 = (@stay fn (@impliedThis(E__0) this__1: E__0) /* return__2 */: Int32 {
                |        return__2 = getp(ordinal__0, this__1)
                |    });
                |    @fn @visibility(\public) @stay @fromType(E__0) let getname__0;
                |    getname__0 = (@stay fn (@impliedThis(E__0) this__2: E__0) /* return__3 */: String {
                |        return__3 = getp(name__0, this__2)
                |    });
                |    @typeDecl(E__0) @stay let E__0;
                |    E__0 = type (E__0)
                |
                |    ```,
                |
                |  pseudoCodeAfter: ```
                |    @constructorProperty @visibility(\public) @stay @fromType(E__0) let ordinal__0: Int32;
                |    @constructorProperty @visibility(\public) @stay @fromType(E__0) let name__0: String;
                |    @visibility(\public) @enumMember @static @stay @fromType(E__0) let A__0;${
                "" // Now the temporaries are directly initialized
            }
                |    A__0 = new E__0(0, "A");
                |    @visibility(\public) @enumMember @static @stay @fromType(E__0) let B__0;
                |    B__0 = new E__0(1, "B");
                |    @fn @visibility(\public) @stay @fromType(E__0) let constructor__0;
                |    constructor__0 = (@stay fn constructor(@impliedThis(E__0) this__0: E__0, ordinal__1 /* aka ordinal */: Int32, name__1 /* aka name */: String) /* return__1 */: Void {
                |        setp(ordinal__0, this__0, ordinal__1);
                |        setp(name__0, this__0, name__1);
                |        return__1 = void
                |    });
                |    @fn @visibility(\public) @stay @fromType(E__0) let getordinal__0;
                |    getordinal__0 = (@stay fn (@impliedThis(E__0) this__1: E__0) /* return__2 */: Int32 {
                |        return__2 = getp(ordinal__0, this__1)
                |    });
                |    @fn @visibility(\public) @stay @fromType(E__0) let getname__0;
                |    getname__0 = (@stay fn (@impliedThis(E__0) this__2: E__0) /* return__3 */: String {
                |        return__3 = getp(name__0, this__2)
                |    });
                |    @typeDecl(E__0) @stay let E__0;
                |    E__0 = type (E__0)
                |
                |    ```,
                |}
            """.trimMargin(),
            r,
        )
    }

    @Test
    fun nonVarAdoptsVarFromRewritten() {
        val r = doCleanupTemporaries(
            """
                |do {
                |  let x;
                |  var t;
                |  t = "foo";
                |  if (randomBool()) {
                |    t = "bar";
                |  }
                |  x = t;
                |  console.log(x);
                |}
            """.trimMargin(),
        )
        assertStructure(
            """
                |{
                |  pseudoCodeAfter: ```
                |    var t#0;
                |    t#0 = getConsole();
                |    var x__0;
                |    x__0 = "foo";
                |    if (randomBool()) {
                |      x__0 = "bar"
                |    };
                |    do_bind_log(t#0)(x__0);
                |
                |    ```,
                |}
            """.trimMargin(),
            r,
        )
    }

    @Test
    fun exportsDoNotAdoptVarFromRewritten() {
        val r = doCleanupTemporaries(
            """
                |export let x;
                |do {
                |  var t;
                |  t = "foo";
                |  if (randomBool()) {
                |    t = "bar";
                |  }
                |  x = t;
                |  console.log(x);
                |}
            """.trimMargin(),
            moduleResultNeeded = true,
        )
        assertStructure(
            """
                |{
                |  pseudoCodeAfter: ```
                |    let return__0;
                |    var t#0;
                |    t#0 = getConsole();
                |    let `test//`.x;
                |    var t#1;
                |    t#1 = "foo";
                |    if (randomBool()) {
                |      t#1 = "bar"
                |    };
                |    `test//`.x = t#1;
                |    do_bind_log(t#0)(`test//`.x);
                |    return__0 = void
                |
                |    ```,
                |}
            """.trimMargin(),
            r,
        )
    }

    @Test
    fun temporaryAssignedTwiceInSeries() {
        val r = doCleanupTemporaries(
            """
                |do {
                |  var t_a;
                |  var t_b;
                |  t_b = 1;
                |  t_a = t_b;
                |  t_b = 2;
                |  t_a = t_b;
                |  console.log(t_a.toString());
                |}
            """.trimMargin(),
        )

        assertStructure(
            """
                |{
                |  pseudoCodeAfter: ```
                |    do_bind_log(getConsole())(do_bind_toString(2)())
                |
                |    ```,
                |}
            """.trimMargin(),
            r,
        )
    }

    @Test
    fun doNotInlineReadsAcrossChangingBoundaries() {
        val r = doCleanupTemporaries(
            """
                |let f(a: Int, b: Int): Void { console.log((a + b).toString()) }
                |do {
                |  var x = 0;
                |  let incr(): Int { x = x + 1; x }
                |
                |  // Here's what we don't want to inline.
                |  // If the incr call happens first, then
                |  // then f gets 1 as its first argument.
                |
                |  // But if we turn f(x, t) into f(x, incr())
                |  // then the call to incr happens after we've
                |  // read x and f gets 0 as
                |
                |  var t;
                |  t = incr();
                |  f(t, x); // Ok to inline this one
                |  t = incr();
                |  f(x, t) // But not this one
                |}
            """.trimMargin(),
            moduleResultNeeded = true,
        )

        assertStructure(
            """
                |{
                |  pseudoCodeAfter: ```
                |    let return__0;
                |    var t#0;
                |    let console#0;
                |    console#0 = getConsole();
                |    @fn let f__0;
                |    f__0 = fn f(a__0 /* aka a */: Int32, b__0 /* aka b */: Int32) /* return__1 */: Void {
                |      var t#1;
                |      fn__0: do {
                |        t#1 = do_bind_toString(a__0 + b__0)();
                |        do_bind_log(console#0)(t#1);
                |        return__1 = void
                |      }
                |    };
                |    @fn let incr__0;
                |    var x__0;
                |    x__0 = 0;
                |    incr__0 = fn incr /* return__2 */: Int32 {
                |      fn__1: do {
                |        x__0 = x__0 + 1;
                |        return__2 = x__0
                |      }
                |    };
                |    f__0(incr__0(), x__0);${
                "" // Ok to inline the first call. It does not cross x__0
            }
                |    t#0 = incr__0();${
                "" // This is not inlined across x__0
            }
                |    f__0(x__0, t#0);
                |    return__0 = void
                |
                |    ```,
                |}
            """.trimMargin(),
            r,
        )
    }

    @Test
    fun temporaryAssignedTwiceToSameNameWhichIsReadInBetween() {
        val r = doCleanupTemporaries(
            """
                |do {
                |  var y;
                |  var t;
                |  t = 1;
                |  y = t;
                |  t = 2;${
                "" // If we renamed `t` to `y` we would print "2" here instead of "1"
            }
                |  console.log(y.toString());
                |  y = t;
                |  console.log(y.toString());
                |}
            """.trimMargin(),
        )
        assertStructure(
            """
                |{
                |  pseudoCodeAfter: ```
                |    var t#0;
                |    t#0 = getConsole();
                |    var y__0, t#1;
                |    y__0 = 1;
                |    t#1 = 2;
                |    do_bind_log(t#0)(do_bind_toString(y__0)());
                |    y__0 = t#1;
                |    do_bind_log(t#0)(do_bind_toString(y__0)());
                |
                |    ```,
                |}
            """.trimMargin(),
            r,
        )
    }

    @Test
    fun varUsedAcrossFnBoundary() {
        val r = doCleanupTemporaries(
            """
                |do {
                |  var t;
                |  let x;
                |  t = 0;
                |  export let f(): Void {
                |    t += 1;
                |  }
                |  x = t;
                |  console.log(x.toString())
                |}
            """.trimMargin(),
            moduleResultNeeded = true,
        )
        // If we did not notice reads/writes in nested functions,
        // we might conclude that `t` could be renamed to `x`.
        assertStructure(
            """
                |{
                |  pseudoCodeAfter: ```
                |    let return__0;
                |    var t#0;
                |    t#0 = getConsole();
                |    @fn let `test//`.f;
                |    var t#1;
                |    let x__0;
                |    t#1 = 0;
                |    `test//`.f = fn f /* return__1 */: Void {
                |      fn__0: do {
                |        t#1 = t#1 + 1;
                |        return__1 = void
                |      }
                |    };
                |    x__0 = t#1;
                |    do_bind_log(t#0)(do_bind_toString(x__0)());
                |    return__0 = void
                |
                |    ```,
                |}
            """.trimMargin(),
            r,
        )
    }

    @Test
    fun useBeforeInitInFn() {
        val r = doCleanupTemporaries(
            """
            export let f(): Int {
              let t;
              if (randomBool()) {
                t = randomInt(0, 4)
              }
              t
            }
            """.trimMargin(),
        )

        assertStructure(
            """
                |{
                |  consoleOutput: ```
                |      6: t
                |         ⇧
                |      [test/test.temper:6+14-15]@T: t#3 is not initialized along branches at [:5+15]
                |      5: }
                |          ⇧
                |
                |    ```,
                |}
            """.trimMargin(),
            r,
        )
    }

    @Test
    fun assignmentToTypedMayBeInHs() {
        val r = doCleanupTemporaries(
            """
            let t: Int;
            t = randomBool();
            t
            """.trimMargin(),
            moduleResultNeeded = true,
        )

        assertStructure(
            """
                |{
                |  pseudoCodeBefore: ```
                |    let return__0, t#0: Int32;
                |    t#0 = randomBool();
                |    return__0 = t#0
                |
                |    ```,
                |  consoleOutput: "",
                |  pseudoCodeAfter: ```
                |    let return__0, t#0: Int32;${
                "" // Cannot eliminate t#0.  It has a declared type.
            }
                |    t#0 = randomBool();
                |    return__0 = t#0
                |
                |    ```,
                |}
            """.trimMargin(),
            r,
        )
    }

    @Test
    fun mayFail() {
        val r = doCleanupTemporaries(
            """
                |randomInt(0, 10) / randomInt(0, 10)
            """.trimMargin(),
            moduleResultNeeded = true,
        )

        assertStructure(
            """
                |{
                |  pseudoCodeAfter: ```
                |    let return__0;
                |    var fail#0;
                |    return__0 = hs(fail#0, randomInt(0, 10) / randomInt(0, 10));
                |    if (fail#0) {
                |      bubble()
                |    };
                |
                |    ```,
                |}
            """.trimMargin(),
            r,
        )
    }

    @Test
    fun doNotEliminateCanonicalNamesForFunctionDefinitions() {
        val r = doCleanupTemporaries(
            """
                |let f(): Void {}
            """.trimMargin(),
        )

        assertStructure(
            """
                |{
                |  pseudoCodeAfter: ```
                |    @fn let f__0;
                |    f__0 = (@stay fn f /* return__0 */: Void {
                |        return__0 = void;
                |        fn__0: do {}
                |    })
                |
                |    ```,
                |}
            """.trimMargin(),
            r,
        )
    }

    @Test
    fun optionalArguments() {
        val r = doCleanupTemporaries(
            """
                |let f(a: Int = 1, b: Int = 2): Int { a + b }
            """.trimMargin(),
        )

        assertStructure(
            """
                |{
                |  pseudoCodeBefore: ```
                |    @fn let f__0;
                |    f__0 = (@stay fn f(@optional(true) a__0 /* aka a */: Int32?, @optional(true) b__0 /* aka b */: Int32?) /* return__0 */: Int32 {
                |        var t#0, t#1;
                |        fn__0: do {
                |          let a__1 /* aka a */: Int32;
                |          if (isNull(a__0)) {
                |            t#0 = 1
                |          } else {
                |            let a#0;
                |            a#0 = notNull(a__0);
                |            t#0 = a#0
                |          };
                |          a__1 = t#0;
                |          let b__1 /* aka b */: Int32;
                |          if (isNull(b__0)) {
                |            t#1 = 2
                |          } else {
                |            let b#0;
                |            b#0 = notNull(b__0);
                |            t#1 = b#0
                |          };
                |          b__1 = t#1;
                |          return__0 = a__1 + b__1
                |        }
                |    })
                |
                |    ```,
                |  pseudoCodeAfter: ```
                |    @fn let f__0;
                |    f__0 = (@stay fn f(@optional(true) a__0 /* aka a */: Int32?, @optional(true) b__0 /* aka b */: Int32?) /* return__0 */: Int32 {
                |        fn__0: do {
                |          let a__1 /* aka a */: Int32;
                |          if (isNull(a__0)) {
                |            a__1 = 1
                |          } else {
                |            a__1 = notNull(a__0)
                |          };
                |          let b__1 /* aka b */: Int32;
                |          if (isNull(b__0)) {
                |            b__1 = 2
                |          } else {
                |            b__1 = notNull(b__0)
                |          };
                |          return__0 = a__1 + b__1
                |        }
                |    })
                |
                |    ```,
                |}
            """.trimMargin(),
            r,
        )
    }

    @Test
    fun multipleAssignmentsIncludingGuarded() {
        val r = doCleanupTemporaries(
            """
                |do {
                |  var x: Int = 10;
                |  x = x * 3;
                |  x = x / 5;
                |  x
                |}
            """.trimMargin(),
            moduleResultNeeded = true,
        )

        assertStructure(
            """
                |{
                |  pseudoCodeBefore: ```
                |      let return__0;
                |      var t#0, t#1, t#2, fail#0, x__0: Int32;
                |      x__0 = 10;
                |      x__0 = x__0 * 3;
                |      t#0 = hs(fail#0, x__0 / 5);${
                "" // This used to have compound assignment `t#0 = (x__0 = x__0 * 3);` but we no longer produce it.
                // TODO Some other way to conjure one for testing?
            }
                |      if (fail#0) {
                |        bubble()
                |      };
                |      x__0 = t#0;
                |      t#1 = x__0;
                |      t#2 = t#1;
                |      return__0 = t#2
                |
                |      ```,
                |
                |  pseudoCodeAfter: ```
                |      let return__0;
                |      var t#0;
                |      var fail#0, x__0: Int32;
                |      x__0 = 10;
                |      x__0 = x__0 * 3;${
                "" // We don't rewrite the `t#0 =` below currently because
                // x__0 is a multiply assigned var.  We could fix that by
                // recognizing that x__0 has one live write.
            }
                |      t#0 = hs(fail#0, x__0 / 5);
                |      if (fail#0) {
                |        bubble()
                |      };
                |      x__0 = t#0;
                |      return__0 = x__0;
                |
                |      ```
                |}
            """.trimMargin(),
            r,
        )
    }

    @Test
    fun chainedAssignments() {
        val r = doCleanupTemporaries(
            """
                |var t0, t1, t2, t3;
                |t0 = t1 = t2 = t3 = randomInt();
                |t0 + t1 + t2 + t3
            """.trimMargin(),
            moduleResultNeeded = true,
        )

        assertStructure(
            """
                |{
                |  pseudoCodeBefore: ```
                |      let return__0;
                |      var t#0, t#1, t#2, t0#3, t1#4, t2#5, t3#6;
                |      t3#6 = randomInt();
                |      t#0 = t3#6;
                |      t2#5 = t#0;
                |      t#1 = t2#5;
                |      t1#4 = t#1;
                |      t#2 = t1#4;
                |      t0#3 = t#2;
                |      return__0 = t0#3 + t1#4 + t2#5 + t3#6
                |
                |      ```,
                |
                |  pseudoCodeAfter: ```
                |      let return__0;
                |      var t0#3;
                |      t0#3 = randomInt();
                |      return__0 = t0#3 + t0#3 + t0#3 + t0#3
                |
                |      ```,
                |}
            """.trimMargin(),
            r,
        )
    }

    @Test
    fun collapseRegression() {
        val r = doCleanupTemporaries(
            """
                |var t0;
                |let t1;
                |t1 = randomInt(1, 4);
                |randomInt(0, t1);
                |t0 = t1;
                |randomInt(0, t0);
                |t0
            """.trimMargin(),
            moduleResultNeeded = true,
        )
        assertStructure(
            """
                |{
                |  pseudoCodeAfter: ```
                |    let return__4;
                |    return__4 = randomInt(1, 4);
                |    randomInt(0, return__4);
                |    randomInt(0, return__4);
                |
                |    ```
                |}
            """.trimMargin(),
            r,
        )
    }

    @Test
    fun setter() {
        val r = doCleanupTemporaries(
            """
                |class C {
                |  public set p(newValue: Boolean) {}
                |}
                |let c = new C();
                |c.p = c.p = randomBool()
                |
            """.trimMargin(),
            moduleResultNeeded = true,
        )

        assertStructure(
            """
                |{
                |//pseudoCodeBefore: ```
                |//
                |//    ```,
                |  pseudoCodeAfter: ```
                |      let return__0;
                |      @visibility(\public) @stay @fromType(C__0) let p__0;
                |      @visibility(\public) @fn @stay @fromType(C__0) let nym`set.p__1`;
                |      nym`set.p__1` = (@stay fn nym`set.p`(@impliedThis(C__0) this__0: C__0, newValue__0 /* aka newValue */: Boolean) /* return__1 */: Void {
                |          return__1 = void;
                |          fn__0: do {}
                |      });
                |      @fn @visibility(\public) @stay @fromType(C__0) let constructor__0;
                |      constructor__0 = (@stay fn constructor(@impliedThis(C__0) this__1: C__0) /* return__2 */: Void {
                |          return__2 = void
                |      });
                |      @typeDecl(C__0) @stay let C__0;
                |      C__0 = type (C__0);
                |      let c__0;
                |      c__0 = new C__0();
                |      return__0 = randomBool();
                |      do_set_p(c__0, return__0);
                |      do_set_p(c__0, return__0);
                |
                |      ```,
                |}
            """.trimMargin(),
            r,
        )
    }

    @Test
    fun typePlaceholderNotDeadStore() {
        val r = doCleanupTemporaries(
            """
                |interface I {};
                |
            """.trimMargin(),
        )

        // The assignment to the type placeholder variable is not treated as a dead-store.
        assertStructure(
            """
                |{
                |  pseudoCodeAfter: ```
                |      @typePlaceholder(I__0) let typePlaceholder#0: Empty;
                |      typePlaceholder#0 = {class: Empty__0};
                |      @typeDecl(I__0) @stay let I__0;
                |      I__0 = type (I__0)
                |
                |      ```,
                |}
            """.trimMargin(),
            r,
        )
    }

    @Test
    fun orElseThat() {
        val r = doCleanupTemporaries(
            """
                |console.log(
                |  (0.0 / 0.0).toString()
                |  orelse "Bubble"
                |);
            """.trimMargin(),
        )
        assertStructure(
            """
                |{
                |  pseudoCodeBefore: ```
                |    var t#0, t#1, t#2, t#3, t#4, fail#0, fail#1, fail#2, fail#3;
                |    let console#0;
                |    t#0 = getConsole();
                |    console#0 = t#0;
                |    orelse#1: {
                |      t#1 = hs(fail#3, 0.0 / 0.0);
                |      if (fail#3) {
                |        break orelse#1;
                |      };
                |      t#2 = do_bind_toString(t#1)();
                |      t#3 = t#2
                |    } orelse {
                |      t#3 = "Bubble"
                |    };
                |    t#4 = do_bind_log(console#0)(t#3);
                |    t#4
                |
                |    ```,
                |
                |  pseudoCodeAfter: ```
                |    var t#0, t#1, t#2, t#3;
                |    var fail#3;
                |    t#0 = getConsole();
                |    orelse#1: {
                |      t#1 = hs(fail#3, 0.0 / 0.0);
                |      if (fail#3) {
                |        break orelse#1;
                |      };
                |      t#2 = do_bind_toString(t#1)();
                |      t#3 = t#2
                |    } orelse {
                |      t#3 = "Bubble"
                |    };
                |    do_bind_log(t#0)(t#3);
                |
                |    ```
                |}
            """.trimMargin(),
            r,
        )
    }

    @Test
    fun noOrElse() {
        val r = doCleanupTemporaries(
            """
                |let f(): Int throws Bubble { 0 / 0 }
            """.trimMargin(),
        )
        assertStructure(
            """
                |{
                |  pseudoCodeAfter: ```
                |    @fn let f__0;
                |    f__0 = (@stay fn f /* return__1 */: (Int32 | Bubble) {
                |        fn__0: do {
                |          var fail#0;
                |          return__1 = hs(fail#0, 0 / 0);
                |          if (fail#0) {
                |            bubble()
                |          };
                |        }
                |    })
                |
                |    ```
                |}
            """.trimMargin(),
            r,
        )
    }

    @Test
    fun doNotMaskIllegalAssignmentsToNonVar() {
        val r = doCleanupTemporaries(
            """
                |do {
                |  // `x` is defined using `let`, not `var`, so it can't be multiply assigned.
                |  let x = randomBool();
                |  x = randomBool();
                |}
            """.trimMargin(),
            moduleResultNeeded = true,
        )
        assertStructure(
            """
                |{
                |  pseudoCodeAfter: ```
                |    let return__0;
                |    var x__0;${
                "" // Fixed that for you, after issuing an error.
            }
                |    x__0 = randomBool();
                |    x__0 = randomBool();
                |    return__0 = void
                |
                |    ```,
                |  consoleOutput: ```
                |      4: x = randomBool();
                |         ┗━━━━━━━━━━━━━━┛
                |      [test/test.temper:4+2-18]@T: x__0 is reassigned after :3+10-22 but is not declared `var` at :3+2-22
                |      3: let x = randomBool();
                |                 ┗━━━━━━━━━━┛
                |      3: let x = randomBool();
                |         ┗━━━━━━━━━━━━━━━━━━┛
                |
                |    ```
                |}
            """.trimMargin(),
            r,
        )
    }

    @Test
    fun whatAboutParams() {
        val r = doCleanupTemporaries(
            """
                |// `x` and `y` aren't var, but `z` is.
                |// Optional `w` is here to ensure its handling works fine in context.
                |let hi(x: Int, y: Int, var z: Int, w: Int = 3): Int {
                |  // This is already a reassignment and needs recognized as such.
                |  x += 1;
                |  // Multiple reassignments for contrast.
                |  y += 2;
                |  y += 3;
                |  // Double assignment for var params is ok.
                |  z += 4;
                |  z += 5;
                |  return x + y + z + w;
                |}
            """.trimMargin(),
        )
        assertStructure(
            """
                |{
                |  pseudoCodeAfter: ```
                |    @fn let hi__0;
                |    hi__0 = (@stay fn hi(var x__0 /* aka x */: Int32, var y__0 /* aka y */: Int32, var z__0 /* aka z */: Int32, @optional(true) w__0 /* aka w */: Int32?) /* return__0 */: Int32 {
                |        fn__0: do {
                |          let w__1 /* aka w */: Int32;
                |          if (isNull(w__0)) {
                |            w__1 = 3
                |          } else {
                |            w__1 = notNull(w__0)
                |          };
                |          x__0 = x__0 + 1;
                |          y__0 = y__0 + 2;
                |          y__0 = y__0 + 3;
                |          z__0 = z__0 + 4;
                |          z__0 = z__0 + 5;
                |          return__0 = x__0 + y__0 + z__0 + w__1
                |        }
                |    })
                |
                |    ```,
                |  consoleOutput: ```
                |      5: x += 1;
                |         ┗━━━━┛
                |      [test/test.temper:5+2-8]@T: x__0 is reassigned after :3+7-13 but is not declared `var` at :3+7-13
                |      3: let hi(x: Int, y: Int, var z: Int
                |                ┗━━━━┛
                |      7: y += 2;
                |         ┗━━━━┛
                |      [test/test.temper:7+2-8]@T: y__0 is reassigned after :3+15-21 but is not declared `var` at :3+15-21
                |      3: let hi(x: Int, y: Int, var z: Int, w: Int
                |                        ┗━━━━┛
                |      8: y += 3;
                |         ┗━━━━┛
                |      [test/test.temper:8+2-8]@T: y__0 is reassigned after :7+2-8 but is not declared `var` at :3+15-21
                |      7: y += 2;
                |         ┗━━━━┛
                |      3: let hi(x: Int, y: Int, var z: Int, w: Int
                |                        ┗━━━━┛
                |
                |    ```
                |}
            """.trimMargin(),
            r,
        )
    }

    @Test
    fun whatIsGoingOnHere() {
        val r = doCleanupTemporaries(
            """
                |do {
                |  let b = randomBool();
                |  let c = if (b) {
                |    "yes"
                |  } else {
                |    "no"
                |  };
                |  let d = if (b) {
                |    "yes"
                |  } else {
                |    "no"
                |  };
                |  c == d
                |}
                |
            """.trimMargin(),
            moduleResultNeeded = true,
        )
        assertStructure(
            """
                |{
                |  pseudoCodeBefore: ```
                |    let return__9;
                |    var t#5, t#6, t#7, t#8;
                |    let b__2;
                |    b__2 = randomBool();
                |    let c__3;
                |    if (b__2) {
                |      t#5 = "yes"
                |    } else {
                |      t#5 = "no"
                |    };
                |    c__3 = t#5;
                |    let d__4;
                |    if (b__2) {
                |      t#6 = "yes"
                |    } else {
                |      t#6 = "no"
                |    };
                |    d__4 = t#6;
                |    t#7 = c__3 == d__4;
                |    t#8 = t#7;
                |    return__9 = t#8
                |
                |    ```,
                |  pseudoCodeAfter: ```
                |    let return__9;
                |    let b__2;
                |    b__2 = randomBool();
                |    let c__3;
                |    if (b__2) {
                |      c__3 = "yes"
                |    } else {
                |      c__3 = "no"
                |    };
                |    let d__4;
                |    if (b__2) {
                |      d__4 = "yes"
                |    } else {
                |      d__4 = "no"
                |    };
                |    return__9 = c__3 == d__4;
                |
                |    ```,
                |}
            """.trimMargin(),
            r,
        )
    }

    @Test
    fun unusedCode() {
        val r = doCleanupTemporaries(
            """
                |label: do {
                |  console.log("foo"); //!outputs "foo"
                |  break label;
                |  console.log("bar"); // never executed
                |}
            """.trimMargin(),
        )
        assertStructure(
            """
                |{
                |  pseudoCodeAfter: ```
                |    label__0: do {
                |      do_bind_log(getConsole())("foo")
                |    }
                |
                |    ```
                |}
            """.trimMargin(),
            r,
        )
    }

    @Test
    fun postIncrementInLoop() {
        val r = doCleanupTemporaries(
            """
                |do {
                |  var i = 0;
                |  while (i < 3) {
                |    console.log((i++).toString());
                |  }
                |}
            """.trimMargin(),
        )
        assertStructure(
            """
                |{
                |  pseudoCodeBefore: ```
                |    var t#0, t#1, t#2, fail#0, fail#1, fail#2;
                |    let console#0;
                |    t#0 = getConsole();
                |    console#0 = t#0;
                |    var i__0;
                |    i__0 = 0;
                |    while (i__0 < 3) {
                |      let postfixReturn#0;
                |      postfixReturn#0 = i__0;
                |      i__0 = i__0 + 1;
                |      t#1 = postfixReturn#0;
                |      t#2 = do_bind_toString(t#1)();
                |      do_bind_log(console#0)(t#2)
                |    }
                |
                |    ```,
                |  pseudoCodeAfter: ```
                |    var t#0;
                |    t#0 = getConsole();
                |    var i__0;
                |    i__0 = 0;
                |    while (i__0 < 3) {
                |      let postfixReturn#0;
                |      postfixReturn#0 = i__0;
                |      i__0 = i__0 + 1;
                |      do_bind_log(t#0)(do_bind_toString(postfixReturn#0)())
                |    }
                |
                |    ```,
                |}
            """.trimMargin(),
            r,
        )
    }

    @Test
    fun postIncrementInLoopInReplLikeContext() {
        val r = doCleanupTemporaries(
            """
                |do {
                |  var i = 0;
                |  while (i < 3) {
                |    console.log((i++).toString());
                |  }
                |}
            """.trimMargin(),
            moduleResultNeeded = true,
        )
        assertStructure(
            """
                |{
                |  pseudoCodeBefore: ```
                |    let return__0;
                |    var t#0, t#1, t#2, fail#0, fail#1, fail#2;
                |    let console#0;
                |    t#0 = getConsole();
                |    console#0 = t#0;
                |    var i__0;
                |    i__0 = 0;
                |    while (i__0 < 3) {
                |      let postfixReturn#0;
                |      postfixReturn#0 = i__0;
                |      i__0 = i__0 + 1;
                |      t#1 = postfixReturn#0;
                |      t#2 = do_bind_toString(t#1)();
                |      do_bind_log(console#0)(t#2)
                |    };
                |    return__0 = void
                |
                |    ```,
                |  pseudoCodeAfter: ```
                |    let return__0;
                |    var t#0;
                |    t#0 = getConsole();
                |    var i__0;
                |    i__0 = 0;
                |    while (i__0 < 3) {
                |      let postfixReturn#0;
                |      postfixReturn#0 = i__0;
                |      i__0 = i__0 + 1;
                |      do_bind_log(t#0)(do_bind_toString(postfixReturn#0)())
                |    };
                |    return__0 = void
                |
                |    ```,
                |}
            """.trimMargin(),
            r,
        )
    }

    @Test
    fun orElseInFnBody() {
        val r = doCleanupTemporaries(
            """
                |export let f(n: Int, d: Int): Int { n / d orelse -1 }
            """.trimMargin(),
            moduleResultNeeded = true,
        )
        assertStructure(
            """
                |{
                |  pseudoCodeBefore: ```
                |    let return__7, @fn `test//`.f;
                |    `test//`.f = (@stay fn f(n__1 /* aka n */: Int32, d__2 /* aka d */: Int32) /* return__0 */: Int32 {
                |        var t#5, t#6;
                |        fn__0: do {
                |          var fail#4;
                |          orelse#0: {
                |            t#5 = hs(fail#4, n__1 / d__2);
                |            if (fail#4) {
                |              break orelse#0;
                |            };
                |            t#6 = t#5
                |          } orelse {
                |            t#6 = -1
                |          };
                |          return__0 = t#6
                |        }
                |    });
                |    return__7 = void
                |
                |    ```,
                |  pseudoCodeAfter: ```
                |    let return__7, @fn `test//`.f;
                |    `test//`.f = (@stay fn f(n__1 /* aka n */: Int32, d__2 /* aka d */: Int32) /* return__0 */: Int32 {
                |        var t#5;
                |        fn__0: do {
                |          var fail#4;
                |          orelse#0: {
                |            t#5 = hs(fail#4, n__1 / d__2);
                |            if (fail#4) {
                |              break orelse#0;
                |            };
                |            return__0 = t#5
                |          } orelse {
                |            return__0 = -1
                |          };
                |        }
                |    });
                |    return__7 = void
                |
                |    ```,
                |}
            """.trimMargin(),
            r,
        )
    }

    @Test
    fun doNotReplaceLocalNameWithExportedName() {
        val r = doCleanupTemporaries(
            input = """
                |let { f as localName } = import("./other");
                |
                |console.log(localName().aString());
            """.trimMargin(),
            writeOtherModules = { fs ->
                fs.write(
                    testModuleName.libraryRoot().resolve(filePath("other", "other.temper")),
                    """
                        |class C {
                        |  public aString(): String {
                        |    "foo"
                        |  }
                        |}
                        |export let f(): C {
                        |  new C()
                        |}
                    """.trimMargin().toByteArray(),
                )
            },
            advanceTo = Stage.GenerateCode,
        )

        assertStructure(
            """
                |{
                |  pseudoCodeAfter: ```
                |    @stay @imported(\(`test//other/`.f)) @fn let localName__0;
                |    localName__0 = (fn f);${
                "" // This is not a dead store
            }
                |    do_bind_log(getConsole())(do_bind_aString((fn f)())());
                |
                |    ```,
                |  "consoleOutput": ```
                |    6: export let f(): C {
                |                       ⇧
                |    [test/other/other.temper:6+16-17]@G: Export depends publicly on non-exported symbol C
                |
                |    ```
                |}
            """.trimMargin(),
            r,
        )
    }

    @Test
    fun genericFn() {
        val r = doCleanupTemporaries(
            """
                |export let f<T>(x: T): T { x }
            """.trimMargin(),
        )
        assertStructure(
            """
                |{
                |  pseudoCodeAfter: ```
                |    @fn let `test//`.f;
                |    @typeFormal(\T) @typeDecl(T__0) let T__0;
                |    T__0 = type (T__0);
                |    `test//`.f = (@stay fn f<T__0 extends AnyValue>(x__0 /* aka x */: T__0) /* return__1 */: T__0 {
                |        fn__0: do {
                |          return__1 = x__0
                |        }
                |    })
                |
                |    ```,
                |}
            """.trimMargin(),
            r,
        )
    }

    @Test
    fun sourceNamesAreBetterThanTemporaries() = assertPostCleanupWithoutExtraStagingMachinery(
        """
        |let f__0;
        |f__0 = fn {};
        |
        """.trimMargin(),
    ) { nameMaker ->
        val f = nameMaker.unusedSourceName(ParsedName("f")) // f__0
        val t1 = nameMaker.unusedTemporaryName("t")
        val t2 = nameMaker.unusedTemporaryName("t")
        val t3 = nameMaker.unusedTemporaryName("t")
        Block {
            // var t#1;
            Decl(t1) {
                V(varSymbol)
                V(void)
            }
            Decl(t2) {
                V(varSymbol)
                V(void)
            }
            Decl(t3) {
                V(varSymbol)
                V(void)
            }
            // let f__0;
            Decl(f) {}
            // t#1 = fn {};
            Call(BuiltinFuns.vSetLocalFn) {
                Ln(t1)
                Fn {
                    Block {}
                }
            }
            // Purposely order temp numbers both up and down here.
            // t#3 = t#1;
            Call(BuiltinFuns.vSetLocalFn) {
                Ln(t3)
                Rn(t1)
            }
            // t#2 = t#3;
            Call(BuiltinFuns.vSetLocalFn) {
                Ln(t2)
                Rn(t3)
            }
            // f__0 == t#2;
            Call(BuiltinFuns.vSetLocalFn) {
                Ln(f)
                Rn(t2)
            }
        }
    }

    @Test
    fun illegalReassignmentFlagged() {
        val r = doCleanupTemporaries(
            """
                |do {
                |  let x = 0; // Should be var
                |  x += 1;
                |}
            """.trimMargin(),
        )
        assertEquals(
            listOf(
                "x__0 is reassigned after test/test.temper+15-16 but is not declared `var` at test/test.temper+7-16!",
            ),
            r.log.mapNotNull {
                if (it.level >= Log.Warn) {
                    it.messageText
                } else {
                    null
                }
            },
        )
    }

    @Test
    fun unObviouslyReachableOrElseReached() {
        val r = doCleanupTemporaries(
            // Make sure that if a renamed variable is mentioned in an
            // `orelse`'s second clause even when the first doesn't bubble,
            // all the uses of it get renamed.
            """
                |var t_a = randomInt(0, 10);
                |let t_b = t_a;
                |var result;
                |do {
                |  result = 1 + t_b;
                |} orelse do {
                |  result = 2 + t_b;
                |};
                |result
            """.trimMargin(),
        )
        assertStructure(
            """
                |{
                |  pseudoCodeAfter: ```
                |    var t_a#0;
                |    t_a#0 = randomInt(0, 10);
                |    var result__0;
                |    orelse#0: {
                |      result__0 = 1 + t_a#0
                |    } orelse {
                |      result__0 = 2 + t_a#0
                |    };
                |    result__0
                |
                |    ```
                |}
            """.trimMargin(),
            r,
        )
    }

    @Test
    fun awaitNotInlinedBack() {
        // `await` is one of the specials that needs to be left as
        val r = doCleanupTemporaries("ignore(await p())") { module, isNew ->
            // (await p) should be pulled out into a temporary.
            // See Weaver for why it needs to be at statement level, or the RHS of a simple
            // assignment at statement level.

            if (isNew) {
                // Define p as a Fn (): Promise<String>
                val pMacro = object : StaylessMacroValue, CallableValue {
                    override val sigs = listOf(
                        Signature2(
                            returnType2 = MkType2(WellKnownTypes.promiseTypeDefinition)
                                .actuals(listOf(WellKnownTypes.booleanType2))
                                .get(),
                            hasThisFormal = false,
                            requiredInputTypes = listOf(),
                        ),
                    )

                    override fun invoke(
                        args: ActualValues,
                        cb: InterpreterCallback,
                        interpMode: InterpMode,
                    ): PartialResult = NotYet
                }

                module.addEnvironmentBindings(
                    mapOf(BuiltinName("p") to Value(pMacro)),
                )
            }
        }
        assertStructure(
            """
                |{
                |  pseudoCodeAfter: ```
                |    var t#0, fail#0;
                |    t#0 = hs(fail#0, await p());
                |    if (fail#0) {
                |      bubble()
                |    };
                |    (fn ignore)(t#0)
                |
                |    ```
                |}
            """.trimMargin(),
            r,
        )
    }

    @Test
    fun inlineStaticCallees() {
        val r = doCleanupTemporaries(
            """
                |class IntUtil {
                |  public static plusOne(n: Int) { n + 1 }
                |}
                |
                |let intOrBubble(): Int throws Bubble {
                |  let i = randomInt();
                |  if (i == 0) { bubble() }
                |  i
                |}
                |
                |console.log(IntUtil.plusOne(intOrBubble()))
            """.trimMargin(),
        )
        assertEquals(
            """
                |var t#16, t#17;
                |var fail#15;
                |t#16 = getConsole();
                |@typeDecl(IntUtil__0) @stay let IntUtil__0;
                |IntUtil__0 = type (IntUtil__0);
                |@fn let intOrBubble__4;
                |@fn @static @visibility(\public) @stay @fromType(IntUtil__0) let plusOne__5;
                |plusOne__5 = (@stay fn plusOne(n__6 /* aka n */: Int32) /* return__21 */{
                |    fn__7: do {
                |      return__21 = n__6 + 1
                |    }
                |});
                |@fn @visibility(\public) @stay @fromType(IntUtil__0) let constructor__8;
                |constructor__8 = (@stay fn constructor(@impliedThis(IntUtil__0) this__1: IntUtil__0) /* return__2 */: Void {
                |    return__2 = void
                |});
                |intOrBubble__4 = (@stay fn intOrBubble /* return__3 */: (Int32 | Bubble) {
                |    fn__9: do {
                |      let i__10;
                |      i__10 = randomInt();
                |      if (i__10 == 0) {
                |        bubble()
                |      };
                |      return__3 = i__10
                |    }
                |});
                |#########################################################################
                |## BY A STRICT READING OF ORDER OF OPERATIONS, THE READ OF Int.plusOne ##
                |## OCCURS HERE, BEFORE ARGUMENT EVALUATION.                            ##
                |#########################################################################
                |t#17 = hs(fail#15, (fn intOrBubble)());
                |if (fail#15) {
                |  bubble()
                |};
                |do_bind_log(t#16)(getStatic(IntUtil__0, \plusOne)(t#17));
                |
            """.trimMargin().stripDoubleHashCommentLinesToPutCommentsInlineBelow(),
            r.pseudoCodeAfter,
        )
    }

    private fun renamer() =
        PseudoCodeNameRenumberer.newStructurePostProcessor()

    // Use the renumberer for identifiers in source code
    private fun assertStructure(want: String, got: Structured) {
        assertStructure(want, got, postProcessor = { renamer()(it) })
    }

    private fun doCleanupTemporaries(
        input: String,
        moduleResultNeeded: Boolean = false,
        advanceTo: Stage = Stage.Type,
        writeOtherModules: (MemoryFileSystem) -> Unit = {},
        moduleCustomizeHook: ModuleCustomizeHook? = null,
    ): CleanupTemporariesResult {
        val projectLogSink = ListBackedLogSink()
        val libraryRoot = testModuleName.libraryRoot()
        val (makeResult, consoleOutput) = withCapturingConsole { cConsole ->
            var moduleAdvancer: ModuleAdvancer? = null

            // Capture state at key steps
            var pseudoCodeBefore: String? = null
            var pseudoCodeAfter: String? = null
            val possibleProblems = mutableListOf<String>()
            var dataTables = CleanupTemporaries.DataTablesList(emptyList(), testModuleName)
            val captureAfterCleanupTemporaries = object : Snapshotter {
                override fun <IR : Structured> snapshot(key: SnapshotKey<IR>, stepId: String, state: IR) {
                    when (stepId) {
                        Debug.Frontend.TypeStage.AfterSimplifyFlow2.loggerName -> {
                            AstSnapshotKey.useIfSame(key, state) {
                                if (it.pos.loc == testCodeLocation) {
                                    pseudoCodeBefore = snapshotBlock(it)
                                }
                            }
                        }
                        Debug.Frontend.TypeStage.CleanupTemporaries.loggerName ->
                            CleanupTemporaries.DebugAstSnapshotKey.useIfSame(key, state) {
                                if (it.loc == testModuleName) {
                                    dataTables = it
                                }
                            }
                        Debug.Frontend.TypeStage.AfterCleanupTemporaries.loggerName ->
                            AstSnapshotKey.useIfSame(key, state) {
                                if (it.pos.loc == testCodeLocation) {
                                    pseudoCodeAfter = snapshotBlock(it)
                                    scanForPossibleProblems(
                                        moduleAdvancer!!.sharedLocationContext,
                                        it,
                                        possibleProblems,
                                    )
                                }
                            }
                    }
                }
            }
            val moduleConsole = Console(
                textOutput = cConsole.textOutput,
                logLevel = Log.Warn,
                snapshotter = captureAfterCleanupTemporaries,
            )
            moduleAdvancer = ModuleAdvancer(
                projectLogSink = projectLogSink,
                moduleConfig = ModuleConfig(
                    moduleCustomizeHook = { module, isNew ->
                        if (isNew) {
                            module.addEnvironmentBindings(
                                extraEnvironmentBindings +
                                    (StagingFlags.moduleResultNeeded to TBoolean.value(moduleResultNeeded)),
                            )
                        }
                        if (module.stageCompleted == Stage.SyntaxMacro) {
                            // rewrite SourceNames like `t__0`, `t123__0` and `t_foo__0`
                            // to Temporaries so that we can test whether we bias towards
                            // preserving source names.
                            module.treeForDebug?.rewriteTNames()
                        }
                        moduleCustomizeHook?.customize(module, isNew = isNew)
                    },
                ),
            )
            moduleAdvancer.configureLibrary(
                libraryName = DashedIdentifier("cleanup-temporaries-test"),
                libraryRoot = libraryRoot,
            )
            val fs = MemoryFileSystem()
            fs.write(testCodeLocation, input.toByteArray())
            writeOtherModules(fs)
            partitionSourceFilesIntoModules(
                FilteringFileSystemSnapshot(fs, FileFilterRules.Allow),
                moduleAdvancer,
                projectLogSink,
                moduleConsole,
                root = libraryRoot,
            )

            val module = moduleAdvancer.getAllModules().first { m ->
                m.loc == testModuleName
            }
            Debug.configure(module, moduleConsole)
            moduleAdvancer.advanceModules(stopBefore = Stage.after(advanceTo))
            Debug.configure(module, consoleForKey = null)

            val makeResult = { logEntries: List<LogEntry>, consoleOutput: String ->
                CleanupTemporariesResult(
                    pseudoCodeBefore = pseudoCodeBefore ?: MISSING_PSEUDO_CODE_PLACEHOLDER,
                    pseudoCodeAfter = pseudoCodeAfter ?: MISSING_PSEUDO_CODE_PLACEHOLDER,
                    dataTables = dataTables,
                    log = logEntries,
                    consoleOutput = consoleOutput,
                    possibleProblems = possibleProblems.toList(),
                    ok = module.ok,
                )
            }
            makeResult
        }
        return makeResult(projectLogSink.allEntries, consoleOutput)
    }

    private fun assertPostCleanupWithoutExtraStagingMachinery(
        wantedPseudoCode: String,
        makeInputTree: (Planting).(nameMaker: NameMaker) -> UnpositionedTreeTemplate<BlockTree>,
    ) {
        val logSink = ListBackedLogSink()
        val advancer = ModuleAdvancer(logSink)
        advancer.configureLibrary(
            DashedIdentifier.from("test")!!,
            FilePath.emptyPath,
        )
        val module = advancer.createModule(
            ModuleName(
                filePath("foo.temper"),
                libraryRootSegmentCount = 0,
                isPreface = false,
            ),
            console,
        )

        val document = Document(module)
        val pos = Position(module.loc, 0, 0)

        val root = document.treeFarm.grow(pos) {
            this.makeInputTree(document.nameMaker)
        }
        structureBlock(root)

        module.deliverContent(root)

        CleanupTemporaries.cleanup(
            moduleRoot = root,
            module = module,
            beforeResultsExplicit = false,
        )

        val actualPseudoCode = snapshotBlock(root)

        assertStringsEqual(wantedPseudoCode, actualPseudoCode)
    }
}

private data class CleanupTemporariesResult(
    val pseudoCodeBefore: String,
    val pseudoCodeAfter: String,
    val dataTables: CleanupTemporaries.DataTablesList,
    val log: List<LogEntry>,
    val consoleOutput: String,
    val possibleProblems: List<String>,
    val ok: Boolean,
) : Structured {
    override fun destructure(structureSink: StructureSink) = structureSink.obj {
        key("pseudoCodeBefore", Hints.u) { value(pseudoCodeBefore) }
        key("allEdits", Hints.u) { value(allEdits) }
        key("readsAndWrites0", Hints.u) { value(dataTables[0].readsAndWrites) }
        key("pseudoCodeAfter", Hints.u) { value(pseudoCodeAfter) }
        key("consoleOutput", isDefault = consoleOutput.isEmpty()) { value(consoleOutput) }
        key("possibleProblems", isDefault = possibleProblems.isEmpty()) { value(possibleProblems) }
        key("ok", isDefault = ok) { value(ok) }
    }
}

private val CleanupTemporariesResult.allEdits get() = object : Structured {
    override fun destructure(structureSink: StructureSink) = structureSink.arr {
        dataTables.dataTables.forEach {
            value(it.edits)
        }
    }
}

internal const val MISSING_PSEUDO_CODE_PLACEHOLDER = "MISSING PSEUDO CODE"

private val tNamePattern = Regex("^t(?:[0-9]*|_.*)$")
internal fun BlockTree.rewriteTNames() {
    val root = this
    val replacements = buildList {
        val nameMaker = root.document.nameMaker
        val renames = mutableMapOf<TemperName, Temporary>()
        TreeVisit.startingAt(root)
            .forEachContinuing trees@{ tree ->
                if (tree is NameLeaf) {
                    val name = tree.content
                    val nameText = when (name) {
                        is ParsedName -> name.nameText
                        is SourceName -> name.baseName.nameText
                        else -> return@trees
                    }
                    if (tNamePattern.matches(nameText)) {
                        val replacement = renames.getOrPut(name) {
                            nameMaker.unusedTemporaryName(nameText)
                        }
                        add(tree to replacement)
                    }
                }
            }
            .visitPreOrder()
    }
    replacements.forEach { (nameLeaf, temporaryName) ->
        val edge = nameLeaf.incoming!!
        edge.replace {
            when (nameLeaf) {
                is RightNameLeaf -> Rn(temporaryName)
                is LeftNameLeaf -> Ln(temporaryName)
            }
        }
        val parent = edge.source
        if (parent is DeclTree) {
            // Temporaries shouldn't have QName metadata
            val parts = parent.parts
            if (parts != null && parts.name.content is Temporary) {
                parts.metadataSymbolMultimap[qNameSymbol]
                    ?.forEach { metadataEdge ->
                        val edgeIndex = metadataEdge.edgeIndex
                        parent.replace(edgeIndex - 1..edgeIndex) {
                            // Replace key and value edges with nothing
                        }
                    }
            }
        }
    }
}

internal object RandomBool : BuiltinStatelessCallableValue, NamedBuiltinFun {
    override val name: String get() = "randomBool"
    override val callMayFailPerSe: Boolean get() = false
    override val sigs = listOf(
        Signature2(WellKnownTypes.booleanType2, false, listOf()),
    )

    override fun invoke(
        args: ActualValues,
        cb: InterpreterCallback,
        interpMode: InterpMode,
    ): PartialResult {
        if (cb.stage != Stage.Run) {
            // We don't expose randomness during compilation.
            return NotYet
        }
        return if ((0..1).random() == 0) {
            TBoolean.valueFalse
        } else {
            TBoolean.valueTrue
        }
    }
}

internal object RandomInt : BuiltinStatelessCallableValue, NamedBuiltinFun {
    override val name: String get() = "randomInt"
    override val callMayFailPerSe: Boolean get() = false
    override val sigs = listOf(
        Signature2(
            returnType2 = WellKnownTypes.intType2,
            hasThisFormal = false,
            requiredInputTypes = listOf(WellKnownTypes.intType2, WellKnownTypes.intType2),
        ),
    )

    override fun invoke(
        args: ActualValues,
        cb: InterpreterCallback,
        interpMode: InterpMode,
    ): PartialResult {
        if (cb.stage != Stage.Run) {
            // We don't expose randomness during compilation.
            return NotYet
        }
        args.unpackPositionedOr(2, cb) { return@invoke it }
        val min = TInt.unpackOrFail(args, 0, cb, interpMode) { return@invoke it }
        val max = TInt.unpackOrFail(args, 1, cb, interpMode) { return@invoke it }
        return Value((min..max).random(), TInt)
    }
}

private val extraEnvironmentBindings = mapOf<TemperName, Value<*>>(
    // Expose a well-typed builtin that we can use for conditions in test code
    // which does not collapse any control flow.
    BuiltinName(RandomBool.name) to Value(RandomBool),
    BuiltinName(RandomInt.name) to Value(RandomInt),
)

private fun scanForPossibleProblems(
    sharedLocationContext: SharedLocationContext,
    root: BlockTree,
    out: MutableList<String>,
) {
    TreeVisit.startingAt(root)
        .forEachContinuing {
            var problemText: String? = null
            if (it is CallTree) {
                val callee = it.childOrNull(0)?.functionContained
                if (callee?.assignsArgumentOne == true) {
                    val assigned = it.childOrNull(1)
                    if (assigned !is LeftNameLeaf) {
                        problemText = "Assignee of $callee is not a LeftName, is ${assigned?.treeType?.name}"
                    }
                }
            }
            if (it is ValueLeaf || it is NameLeaf) {
                if (it.typeInferences == null) {
                    problemText = "Missing type info in ${it.toLispy(multiline = false)}"
                }
            }
            if (problemText != null) {
                val pos = it.pos
                val fps = sharedLocationContext[pos.loc, CodeLocationKey.FilePositionsKey]
                val posStr = fps?.spanning(pos)?.toReadablePosition(pos.loc.diagnostic)
                    ?: "$pos"
                out.add("$posStr: $problemText")
            }
        }
        .visitPreOrder()
}

private fun snapshotBlock(root: BlockTree) =
    // Snapshotting in between simplification and trim passes
    // leads to a lot of garbage `void;` lines.
    root.toPseudoCode(singleLine = false)
        .split("\n")
        .filter { line -> line.trim() != "void;" }
        .joinToString("\n")
