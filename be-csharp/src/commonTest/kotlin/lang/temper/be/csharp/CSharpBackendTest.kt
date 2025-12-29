package lang.temper.be.csharp

import lang.temper.be.Backend
import lang.temper.be.assertGeneratedCode
import lang.temper.be.inputFileMapFromJson
import lang.temper.common.stripDoubleHashCommentLinesToPutCommentsInlineBelow
import lang.temper.common.structure.FormattingStructureSink
import lang.temper.common.structure.StructureSink
import lang.temper.common.structure.Structured
import lang.temper.log.FilePath
import lang.temper.log.filePath
import kotlin.test.Test

@SuppressWarnings("MaxLineLength")
class CSharpBackendTest {
    @Test
    fun creativeModuleName() {
        assertGenerateWanted(
            temper = """
                |export let something(): Void {}
            """.trimMargin(),
            classes = mapOf(
                "RosettaCasedGlobal" to Content(
                    usings = "",
                    decls = """
                        |public static class RosettaCasedGlobal
                        |{
                        |    public static void Something()
                        |    {
                        |    }
                        |}
                    """.trimMargin(),
                ),
            ),
            // Capitalized first word was messing up naming. But include later oddness for testing also.
            // This naming convention is used in at least some Rosetta Code examples.
            moduleName = "Rosetta_cased",
            namespaceName = "RosettaCased",
        )
    }

    @Test
    fun outputFileAndNamespaceStructure() = assertGeneratedFileTree(
        inputs = inputFileMapFromJson(
            """
                |{
                |  top.temper: "export class TopHat {}",
                |  bowler: {
                |    b.temper: "export class BowlerHat {}",
                |  },
                |  derby: {
                |    d.temper: "export class DerbyHat {}",
                |  },
                |}
            """.trimMargin(),
        ),
        wantJson = """
            |{
            |  csharp: {
            |    my-test-library: {
            |      src: {
            |        MyTestLibrary.csproj: "__DO_NOT_CARE__",
            |        MyTestLibraryGlobal.cs: "__DO_NOT_CARE__",
            |        MyTestLibraryGlobal.cs.map: "__DO_NOT_CARE__",
            |        TopHat.cs: {
            |          content: ```
            |            namespace MyTestLibrary
            |            {
            |                public class TopHat
            |                {
            |                    public TopHat()
            |                    {
            |                    }
            |                }
            |            }
            |
            |            ```
            |        },
            |        TopHat.cs.map: "__DO_NOT_CARE__",
            |        Bowler: {
            |          BowlerGlobal.cs: "__DO_NOT_CARE__",
            |          BowlerGlobal.cs.map: "__DO_NOT_CARE__",
            |          BowlerHat.cs: {
            |            content: ```
            |              namespace MyTestLibrary.Bowler
            |              {
            |                  public class BowlerHat
            |                  {
            |                      public BowlerHat()
            |                      {
            |                      }
            |                  }
            |              }
            |
            |              ```
            |          },
            |          BowlerHat.cs.map: "__DO_NOT_CARE__",
            |        },
            |        Derby: {
            |          DerbyGlobal.cs: "__DO_NOT_CARE__",
            |          DerbyGlobal.cs.map: "__DO_NOT_CARE__",
            |          DerbyHat.cs: "__DO_NOT_CARE__",
            |          DerbyHat.cs.map: "__DO_NOT_CARE__",
            |        },
            |        Logging.cs: "__DO_NOT_CARE__",
            |      },
            |      program: {
            |        Program.cs: "__DO_NOT_CARE__",
            |        MyTestLibraryProgram.csproj: "__DO_NOT_CARE__",
            |      },
            |    }
            |  }
            |}
        """.trimMargin().stripDoubleHashCommentLinesToPutCommentsInlineBelow(),
    )

    @Test
    fun defaultInterfaceMethod() {
        assertGenerateWanted(
            temper = $$"""
                |export interface Sup {
                |  public grab(map: Mapped<String, Int>): Int throws Bubble { map["hi"] }
                |  public message(name: String): String;
                |  public repeat(name: String): String {
                |    "${message(name)} ${message(name)}"
                |  }
                |}
                |export interface Mid extends Sup {}
                |export class Sub extends Mid {
                |  public message(name: String): String {
                |    "Hi, ${name}!"
                |  }
                |}
            """.trimMargin(),
            classes = mapOf(
                "ISup" to Content(
                    usings = """
                        |using G = System.Collections.Generic;
                    """.trimMargin(),
                    decls = """
                        |public interface ISup
                        |{
                        |    protected static int GrabDefault(ISup this__0, G::IReadOnlyDictionary<string, int> map__0)
                        |    {
                        |        return map__0["hi"];
                        |    }
                        |    int Grab(G::IReadOnlyDictionary<string, int> map__0)
                        |    {
                        |        return GrabDefault(this, map__0);
                        |    }
                        |    string Message(string name__0);
                        |    protected static string RepeatDefault(ISup this__1, string name__1)
                        |    {
                        |        return this__1.Message(name__1) + " " + this__1.Message(name__1);
                        |    }
                        |    string Repeat(string name__1)
                        |    {
                        |        return RepeatDefault(this, name__1);
                        |    }
                        |}
                    """.trimMargin(),
                ),
                "IMid" to Content(
                    usings = "",
                    decls = """
                        |public interface IMid: ISup
                        |{}
                    """.trimMargin(),
                ),
                "Sub" to Content(
                    usings = """
                        |using G = System.Collections.Generic;
                    """.trimMargin(),
                    decls = """
                        |public class Sub: IMid
                        |{
                        |    public string Message(string name__2)
                        |    {
                        |        return "Hi, " + name__2 + "!";
                        |    }
                        |    public Sub()
                        |    {
                        |    }
                        |    public int Grab(G::IReadOnlyDictionary<string, int> map___0)
                        |    {
                        |        return ISup.GrabDefault(this, map___0);
                        |    }
                        |    public string Repeat(string name___0)
                        |    {
                        |        return ISup.RepeatDefault(this, name___0);
                        |    }
                        |}
                    """.trimMargin(),
                ),
            ),
        )
    }

    @Test
    fun defaultArgNoOverload() {
        // This was generating three duplicate overload signatures all of IReadOnlyList.
        assertGeneratedGlobalClass(
            temper = """
                |export let ponder(things: Listed<Int> = []): Void {}
            """.trimMargin(),
            usings = """
                |using G = System.Collections.Generic;
                |using C = TemperLang.Core;
            """.trimMargin(),
            csharp = """
                |public static void Ponder(G::IReadOnlyList<int> ? things = null)
                |{
                |    G::IReadOnlyList<int> things__0;
                |    if (things == null)
                |    {
                |        things__0 = C::Listed.CreateReadOnlyList<int>();
                |    }
                |    else
                |    {
                |        things__0 = things!;
                |    }
                |}
            """.trimMargin(),
        )
    }

    @Test
    fun fib() {
        // Somewhat for fun, but also checks that we generate a global class when we have functions only.
        assertGeneratedGlobalClass(
            temper = """
                |export let fib(var i: Int): Int {
                |  var a: Int = 0;
                |  var b: Int = 1;
                |  while (i > 0) {
                |    let c = a + b;
                |    a = b;
                |    b = c;
                |    i -= 1;
                |  }
                |  a
                |}
            """.trimMargin(),
            usings = "",
            csharp = """
                |public static int Fib(int i__0)
                |{
                |    int a__0 = 0;
                |    int b__0 = 1;
                |    while (i__0 > 0)
                |    {
                |        int c__0 = a__0 + b__0;
                |        a__0 = b__0;
                |        b__0 = c__0;
                |        i__0 = i__0 - 1;
                |    }
                |    return a__0;
                |}
            """.trimMargin(),
        )
    }

    @Test
    fun genericFunctionCall() {
        // The goal here is to deal with generic callback functions
        // and function & constructor type actual inference.
        assertGenerateWanted(
            temper = """
                |export class Hi<T>(public ha: fn (T): T) {}
                |export let makeHi<T>(ha: fn (T): T): Hi<T> { new Hi(ha) }
                |export let makeIntHi(j: Int): Hi<Int> { makeHi { (i: Int): Int => i + j } }
                |export let newIntHi(j: Int): Hi<Int> { new Hi(fn (i: Int): Int { i + j }) }
            """.trimMargin(),
            classes = mapOf(
                "Hi" to Content(
                    usings = """
                        |using S = System;
                    """.trimMargin(),
                    decls = """
                        |public class Hi<T__0>
                        |{
                        |    readonly S::Func<T__0, T__0> ha__0;
                        |    public Hi(S::Func<T__0, T__0> ha__1)
                        |    {
                        |        this.ha__0 = ha__1;
                        |    }
                        |    public S::Func<T__0, T__0> Ha
                        |    {
                        |        get
                        |        {
                        |            return this.ha__0;
                        |        }
                        |    }
                        |}
                    """.trimMargin(),
                ),
                "TestGlobal" to Content(
                    usings = """
                        |using S = System;
                    """.trimMargin(),
                    decls = """
                        |public static class TestGlobal
                        |{
                        |    public static Hi<T__1> MakeHi<T__1>(S::Func<T__1, T__1> ha__2)
                        |    {
                        |        return new Hi<T__1>((S::Func<T__1, T__1>) ha__2);
                        |    }
                        |    public static Hi<int> MakeIntHi(int j__0)
                        |    {
                        |        int fn__0(int i__0)
                        |        {
                        |            return i__0 + j__0;
                        |        }
                        |        return MakeHi((S::Func<int, int>) fn__0);
                        |    }
                        |    public static Hi<int> NewIntHi(int j__1)
                        |    {
                        |        int fn__1(int i__1)
                        |        {
                        |            return i__1 + j__1;
                        |        }
                        |        return new Hi<int>((S::Func<int, int>) fn__1);
                        |    }
                        |}
                    """.trimMargin(),
                ),
            ),
        )
    }

    @Test
    fun importRegexValue() {
        // Made just for looking at trees at first, but discovered a bug, so kept it and fixed that.
        assertGeneratedGlobalClass(
            temper = """
                |let { Dot } = import("std/regex");
                |export let blah = Dot;
            """.trimMargin(),
            usings = """
                |using R = TemperLang.Std.Regex;
            """.trimMargin(),
            csharp = """
                |public static R::ISpecial Blah;
                |static TestGlobal()
                |{
                |    Blah = R::RegexGlobal.Dot;
                |}
            """.trimMargin(),
        )
    }

    @Test
    fun init() {
        // Mix side effects and top-level values to ensure we make good code.
        assertGeneratedGlobalClass(
            temper = """
                |console.log("Hi!");
                |let value = calc();
                |console.log(value.toString(16));
                |let calc(): Int {
                |  console.log("Bye!");
                |  return 123;
                |}
            """.trimMargin(),
            usings = """
                |using S0 = MyTestLibrary.Support;
                |using S1 = System;
                |using C = TemperLang.Core;
            """.trimMargin(),
            csharp = """
                |internal static C::ILoggingConsole console___0;
                |internal static int calc__0()
                |{
                |    console___0.Log("Bye!");
                |    return 123;
                |}
                |internal static int value__0;
                |static TestGlobal()
                |{
                |    console___0 = S0::Logging.LoggingConsoleFactory.CreateConsole("MyTestLibrary.Test");
                |    console___0.Log("Hi!");
                |    value__0 = calc__0();
                |    console___0.Log(S1::Convert.ToString(value__0, 16));
                |}
            """.trimMargin(),
        )
    }

    @Test
    fun listedItems() {
        assertGeneratedGlobalClass(
            temper = """
                |export let things = new ListBuilder<Listed<Int>>();
                |let subs = new ListBuilder<Int>();
                |things.add(subs);
                |export let sures: Listed<Int> = subs;
            """.trimMargin(),
            usings = """
                |using G = System.Collections.Generic;
                |using C = TemperLang.Core;
            """.trimMargin(),
            csharp = """
                |public static G::IList<G::IReadOnlyList<int>> Things;
                |internal static G::IList<int> subs__0;
                |public static G::IReadOnlyList<int> Sures;
                |static TestGlobal()
                |{
                |    Things = new G::List<G::IReadOnlyList<int>>();
                |    subs__0 = new G::List<int>();
                |    C::Listed.Add(Things, C::Listed.AsReadOnly(subs__0));
                |    Sures = C::Listed.AsReadOnly(subs__0);
                |}
            """.trimMargin(),
        )
    }

    @Test
    fun mapBuilderToMapped() {
        assertGeneratedGlobalClass(
            temper = """
                |let builder: MapBuilder<String, String> = new MapBuilder<String, String>();
                |export let mapped: Mapped<String, String> = builder;
            """.trimMargin(),
            usings = """
                |using G = System.Collections.Generic;
                |using C = TemperLang.Core;
            """.trimMargin(),
            csharp = """
                |internal static G::IDictionary<string, string> builder__0;
                |public static G::IReadOnlyDictionary<string, string> Mapped;
                |static TestGlobal()
                |{
                |    builder__0 = new C::OrderedDictionary<string, string>();
                |    Mapped = C::Mapped.AsReadOnly(builder__0);
                |}
            """.trimMargin(),
        )
    }

    @Test
    fun pureVirtualDefault() = assertGeneratedUserClass(
        temper = """
            |export interface A {
            |  public hi(i: Int = 5): Int;
            |}
        """.trimMargin(),
        cSharpClassName = "IA",
        csharp = """
            |public interface IA
            |{
            |    int Hi(int ? i = null);
            |}
        """.trimMargin(),
    )

    @Test
    fun simplePanicValue() {
        assertGeneratedGlobalClass(
            temper = """
                |export let something(i: Int): Int { panic() }
            """.trimMargin(),
            usings = """
                |using S = System;
            """.trimMargin(),
            csharp = """
                |public static int Something(int i__0)
                |{
                |    throw new S::Exception();
                |}
            """.trimMargin(),
        )
    }

    @Test
    fun userClass() {
        assertGeneratedUserClass(
            temper = """
                |export class Test(public var name: String) {
                |  // Custom getter is nice for contrast with automated sometimes.
                |  public get that(): String { thing() }
                |  public thing(): String {
                |    punctuate("Hi, ${"$"}{name}")
                |  }
                |  private punctuate(message: String): String {
                |    "${"$"}{message}!"
                |  }
                |}
            """.trimMargin(),
            csharp = """
                |public class Test
                |{
                |    string name__0;
                |    public string That
                |    {
                |        get
                |        {
                |            return this.Thing();
                |        }
                |    }
                |    public string Thing()
                |    {
                |        return this.Punctuate("Hi, " + this.name__0);
                |    }
                |    string Punctuate(string message__0)
                |    {
                |        return message__0 + "!";
                |    }
                |    public Test(string name__1)
                |    {
                |        this.name__0 = name__1;
                |    }
                |    public string Name
                |    {
                |        get
                |        {
                |            return this.name__0;
                |        }
                |        set
                |        {
                |            this.name__0 = value;
                |        }
                |    }
                |}
            """.trimMargin(),
        )
    }

    @Test
    fun optionals() {
        // Compare optionals for value and reference types, including a side effect for motivation.
        assertGeneratedGlobalClass(
            temper = """
                |let hi(i: Int = do { console.log("heh"); 1 }): Int { i + 1 }
                |export let ha(s: String = "hum"): String { hi(); "${"$"}{s}bug" }
            """.trimMargin(),
            usings = """
                |using S = MyTestLibrary.Support;
                |using C = TemperLang.Core;
            """.trimMargin(),
            csharp = """
                |internal static C::ILoggingConsole console___0;
                |internal static int hi__0(int ? i = null)
                |{
                |    int i__0;
                |    if (i == null)
                |    {
                |        console___0.Log("heh");
                |        i__0 = 1;
                |    }
                |    else
                |    {
                |        i__0 = i.Value;
                |    }
                |    return i__0 + 1;
                |}
                |public static string Ha(string ? s = null)
                |{
                |    string s__0;
                |    if (s == null)
                |    {
                |        s__0 = "hum";
                |    }
                |    else
                |    {
                |        s__0 = s!;
                |    }
                |    hi__0();
                |    return s__0 + "bug";
                |}
                |static TestGlobal()
                |{
                |    console___0 = S::Logging.LoggingConsoleFactory.CreateConsole("MyTestLibrary.Test");
                |}
            """.trimMargin(),
        )
    }

    @Test
    fun connectedDateTodayStaticMethod() = assertGeneratedGlobalClass(
        temper = """
            |let { Date } = import("std/temporal");
            |console.log(Date.today().toString());
        """.trimMargin(),
        usings = """
            |using S = MyTestLibrary.Support;
            |using T = TemperLang.Std.Temporal;
        """.trimMargin(),
        csharp = """
            |static TestGlobal()
            |{
            |    S::Logging.LoggingConsoleFactory.CreateConsole("MyTestLibrary.Test").Log(T::TemporalSupport.Today().ToString("yyyy-MM-dd"));
            |}
        """.trimMargin(),
    )

    @Test
    fun generatorTranslation() = assertGeneratedGlobalClass(
        temper = """
            |let callIt(f: fn (): SafeGenerator<Empty>): Void {
            |  f().next();
            |}
            |
            |callIt { (): GeneratorResult<Empty> extends GeneratorFn =>
            |  console.log("foo");
            |  yield;
            |  console.log("bar");
            |}
        """.trimMargin(),
        usings = """
            |using S0 = MyTestLibrary.Support;
            |using S1 = System;
            |using G = System.Collections.Generic;
            |using C = TemperLang.Core;
        """.trimMargin(),
        csharp = """
            |internal static C::ILoggingConsole console___0;
            |internal static void callIt__0(S1::Func<G::IEnumerable<S1::Tuple<object ?>>> f__0)
            |{
            |    C::Core.GeneratorNext(f__0());
            |}
            |static G::IEnumerable<S1::Tuple<object ?>> coroHelperfn__0()
            |{
            |    console___0.Log("foo");
            |    yield return null;
            |    console___0.Log("bar");
            |}
            |internal static C::IGenerator<S1::Tuple<object ?>> fn__0()
            |{
            |    return C::Core.AdaptGenerator<S1::Tuple<object ?>>(coroHelperfn__0);
            |}
            |static TestGlobal()
            |{
            |    console___0 = S0::Logging.LoggingConsoleFactory.CreateConsole("MyTestLibrary.Test");
            |    callIt__0((S1::Func<G::IEnumerable<S1::Tuple<object ?>>>) fn__0);
            |}
        """.trimMargin(),
    )

    @Test
    fun yieldMovedOutOfTry() = assertGeneratedGlobalClass(
        // `yield` are not allowed in `try` blocks that have `catch`es.
        // See explanation on YieldOrDoNotYieldThereIsNoTry.
        temper = """
            |let callIt(f: fn (): SafeGenerator<Empty>): Void {
            |  f().next();
            |}
            |
            |let mayFail(s: String): Void throws Bubble {
            |  console.log(s);
            |}
            |
            |callIt { (): GeneratorResult<Empty> extends GeneratorFn =>
            |  do {
            |    mayFail("foo");
            |    yield;
            |    mayFail("bar");
            |  } orelse do {
            |    console.log("baz");
            |  }
            |}
        """.trimMargin(),
        usings = """
            |using S0 = MyTestLibrary.Support;
            |using S1 = System;
            |using G = System.Collections.Generic;
            |using C = TemperLang.Core;
        """.trimMargin(),
        csharp = """
            |internal static C::ILoggingConsole console___0;
            |internal static void callIt__0(S1::Func<G::IEnumerable<S1::Tuple<object ?>>> f__0)
            |{
            |    C::Core.GeneratorNext(f__0());
            |}
            |internal static void mayFail__0(string s__0)
            |{
            |    console___0.Log(s__0);
            |}
            |static G::IEnumerable<S1::Tuple<object ?>> coroHelperfn__0()
            |{
            |    try
            |    {
            |        mayFail__0("foo");
            |    }
            |    catch
            |    {
            |        goto CATCH___0;
            |    }
            |    yield return null;
            |    try
            |    {
            |        mayFail__0("bar");
            |    }
            |    catch
            |    {
            |        goto CATCH___0;
            |    }
            |    goto OK___0;
            |    CATCH___0:
            |    {
            |        console___0.Log("baz");
            |    }
            |    OK___0:
            |    {
            |    }
            |}
            |internal static C::IGenerator<S1::Tuple<object ?>> fn__0()
            |{
            |    return C::Core.AdaptGenerator<S1::Tuple<object ?>>(coroHelperfn__0);
            |}
            |static TestGlobal()
            |{
            |    console___0 = S0::Logging.LoggingConsoleFactory.CreateConsole("MyTestLibrary.Test");
            |    callIt__0((S1::Func<G::IEnumerable<S1::Tuple<object ?>>>) fn__0);
            |}
        """.trimMargin(),
    )

    @Test
    fun asyncUses() = assertGeneratedGlobalClass(
        temper = """
            |let pb = new PromiseBuilder<String>();
            |
            |async { (): GeneratorResult<Empty> extends GeneratorFn =>
            |  console.log(await pb.promise orelse "broken");
            |}
        """.trimMargin(),
        usings = """
            |using S0 = MyTestLibrary.Support;
            |using S1 = System;
            |using G = System.Collections.Generic;
            |using T = System.Threading.Tasks;
            |using C = TemperLang.Core;
        """.trimMargin(),
        csharp = """
            |internal static C::ILoggingConsole console___0;
            |internal static T::TaskCompletionSource<string> pb__0;
            |static G::IEnumerable<S1::Tuple<object ?>> coroHelperfn__0()
            |{
            |    string t___0;
            |    string t___1;
            |    T::Task<string> promise___0;
            |    try
            |    {
            |        promise___0 = pb__0.Task;
            |    }
            |    catch
            |    {
            |        goto CATCH___0;
            |    }
            |    yield return C::Async.AwakeUpon(promise___0);
            |    try
            |    {
            |        t___0 = promise___0.Result;
            |        t___1 = t___0;
            |    }
            |    catch
            |    {
            |        goto CATCH___0;
            |    }
            |    goto OK___0;
            |    CATCH___0:
            |    {
            |        t___1 = "broken";
            |    }
            |    OK___0:
            |    {
            |    }
            |    console___0.Log(t___1);
            |}
            |internal static C::IGenerator<S1::Tuple<object ?>> fn__0()
            |{
            |    return C::Core.AdaptGenerator<S1::Tuple<object ?>>(coroHelperfn__0);
            |}
            |static TestGlobal()
            |{
            |    console___0 = S0::Logging.LoggingConsoleFactory.CreateConsole("MyTestLibrary.Test");
            |    pb__0 = new T::TaskCompletionSource<string>();
            |    C::Async.LaunchGeneratorAsync((S1::Func<G::IEnumerable<S1::Tuple<object ?>>>) fn__0);
            |}
        """.trimMargin(),
    )

    @Test
    fun asyncNoAwait() = assertGeneratedGlobalClass(
        temper = """
            |let pb = new PromiseBuilder<String>();
            |
            |async { (): GeneratorResult<Empty> extends GeneratorFn =>
            |  console.log("Logged async");
            |}
        """.trimMargin(),
        usings = """
            |using S0 = MyTestLibrary.Support;
            |using S1 = System;
            |using G = System.Collections.Generic;
            |using T = System.Threading.Tasks;
            |using C = TemperLang.Core;
        """.trimMargin(),
        csharp = """
            |internal static C::ILoggingConsole console___0;
            |internal static T::TaskCompletionSource<string> pb__0;
            |static G::IEnumerable<S1::Tuple<object ?>> coroHelperfn__0()
            |{
            |    if (false)
            |    {
            |        yield return null;
            |    }
            |    console___0.Log("Logged async");
            |}
            |internal static C::IGenerator<S1::Tuple<object ?>> fn__0()
            |{
            |    return C::Core.AdaptGenerator<S1::Tuple<object ?>>(coroHelperfn__0);
            |}
            |static TestGlobal()
            |{
            |    console___0 = S0::Logging.LoggingConsoleFactory.CreateConsole("MyTestLibrary.Test");
            |    pb__0 = new T::TaskCompletionSource<string>();
            |    C::Async.LaunchGeneratorAsync((S1::Func<G::IEnumerable<S1::Tuple<object ?>>>) fn__0);
            |}
        """.trimMargin(),
    )

    @Test
    fun stringBuilderUse() = assertGeneratedGlobalClass(
        temper = """
            |let sb = new StringBuilder();
            |sb.append("Hello, ");
            |sb.append("World");
            |sb.append("!");
            |console.log(sb.toString());
        """.trimMargin(),
        usings = """
            |using S = MyTestLibrary.Support;
            |using T = System.Text;
            |using C = TemperLang.Core;
        """.trimMargin(),
        csharp = """
            |internal static C::ILoggingConsole t___0;
            |internal static T::StringBuilder sb__0;
            |static TestGlobal()
            |{
            |    t___0 = S::Logging.LoggingConsoleFactory.CreateConsole("MyTestLibrary.Test");
            |    sb__0 = new T::StringBuilder();
            |    sb__0.Append("Hello, ");
            |    sb__0.Append("World");
            |    sb__0.Append("!");
            |    t___0.Log(sb__0.ToString());
            |}
        """.trimMargin(),
    )

    @Test
    fun castingAwayNull() = assertGeneratedGlobalClass(
        temper = """
            |export let f<T>(x: T?, fallback: T): T { if (x == null) { fallback } else { x } }
        """.trimMargin(),
        usings = "using C = TemperLang.Core;",
        csharp = """
            |public static T__0 F<T__0>(C::Optional<T__0> x__0, T__0 fallback__0)
            |{
            |    T__0 return__0;
            |    if (!x__0.HasValue)
            |    {
            |        return__0 = fallback__0;
            |    }
            |    else
            |    {
            |        return__0 = x__0.Value;
            |    }
            |    return return__0;
            |}
        """.trimMargin(),
    )

    @Test
    fun publicGetterPrivateSetter() = assertGeneratedUserClass(
        // Also include private readonly and rw fields for variety.
        temper = """
            |export class Test(private x: Int) {
            |  private var y: Int = 1;
            |  public get p(): Int { y - x }
            |  private set p(newP: Int): Void { y = newP + 1 }
            |  public incr(): Int { p += 1 }
            |}
        """.trimMargin(),
        csharp = """
            |public class Test
            |{
            |    readonly int x__0;
            |    int y__0;
            |    public int P
            |    {
            |        get
            |        {
            |            return this.y__0 - this.x__0;
            |        }
            |        private set
            |        {
            |            int t___0 = value + 1;
            |            this.y__0 = t___0;
            |        }
            |    }
            |    public int Incr()
            |    {
            |        int return__0;
            |        return__0 = this.P + 1;
            |        this.P = return__0;
            |        return return__0;
            |    }
            |    public Test(int x__1)
            |    {
            |        this.x__0 = x__1;
            |        this.y__0 = 1;
            |    }
            |}
        """.trimMargin(),
    )

    @Test
    fun listTypeParameters() = assertGeneratedGlobalClass(
        temper = """
            |export let f(x: Int): List<Int> {
            |  let b = new ListBuilder<Int>();
            |  b.add(x);
            |  b.add(x);
            |  b.toList()
            |}
        """.trimMargin(),
        usings = """
            |using G = System.Collections.Generic;
            |using C = TemperLang.Core;
        """.trimMargin(),
        csharp = """
            |public static G::IReadOnlyList<int> F(int x__0)
            |{
            |    G::IList<int> b__0 = new G::List<int>();
            |    C::Listed.Add(b__0, x__0);
            |    C::Listed.Add(b__0, x__0);
            |    return C::Listed.ToReadOnlyList(b__0);
            |}
        """.trimMargin(),
    )

    @Test
    fun nullableTypeParameters() {
        assertGenerateWanted(
            temper = """
                |export interface Foo<T> {
                |  public f(x: T): Boolean;
                |}
                |export interface Bar<T> extends Foo<T?> {
                |  public f(x: T?): Boolean {
                |    x != null
                |  }
                |}
                |export class Baz extends Foo<Int> {
                |  public f(x: Int): Boolean {
                |    x == 0
                |  }
                |}
                |export class Boo extends Bar<Int> {
                |  public f(x: Int?): Boolean {
                |    x == 0
                |  }
                |}
                |export class Far<T> extends Bar<T> {
                |  public g(x: T?, render: fn (T): String): Void {
                |    if (x != null) {
                |      console.log(render(x));
                |    }
                |  }
                |}
                |
                |do {
                |  let far = new Far<Int>();
                |  console.log(far.f(0).toString());
                |  console.log(far.f(null).toString());
                |}
            """.trimMargin(),
            classes = mapOf(
                "IFoo" to Content(
                    usings = """
                    """.trimMargin(),
                    decls = """
                        |public interface IFoo<T__0>
                        |{
                        |    bool F(T__0 x__0);
                        |}
                    """.trimMargin(),
                ),
                "IBar" to Content(
                    usings = """
                        |using C = TemperLang.Core;
                    """.trimMargin(),
                    decls = """
                        |public interface IBar<T__1>: IFoo<C::Optional<T__1>>
                        |{
                        |    protected static bool FDefault(IBar<T__1> this__0, C::Optional<T__1> x__1)
                        |    {
                        |        return x__1.HasValue;
                        |    }
                        |    bool F(C::Optional<T__1> x__1)
                        |    {
                        |        return FDefault(this, x__1);
                        |    }
                        |}
                    """.trimMargin(),
                ),
                "Baz" to Content(
                    usings = """
                    """.trimMargin(),
                    decls = """
                        |public class Baz: IFoo<int>
                        |{
                        |    public bool F(int x__2)
                        |    {
                        |        return x__2 == 0;
                        |    }
                        |    public Baz()
                        |    {
                        |    }
                        |}
                    """.trimMargin(),
                ),
                "Boo" to Content(
                    usings = """
                        |using C = TemperLang.Core;
                    """.trimMargin(),
                    decls = """
                        |public class Boo: IBar<int>
                        |{
                        |    public bool F(C::Optional<int> x__3)
                        |    {
                        |        return f__0((int ?) x__3);
                        |    }
                        |    bool f__0(int ? x__3)
                        |    {
                        |        return x__3 == 0;
                        |    }
                        |    public Boo()
                        |    {
                        |    }
                        |}
                    """.trimMargin(),
                ),
                "Far" to Content(
                    usings = """
                        |using T = MyTestLibrary.Test;
                        |using S = System;
                        |using C = TemperLang.Core;
                    """.trimMargin(),
                    decls = """
                        |public class Far<T__2>: IBar<T__2>
                        |{
                        |    public void G(C::Optional<T__2> x__4, S::Func<T__2, string> render__0)
                        |    {
                        |        string t___0;
                        |        if (x__4.HasValue)
                        |        {
                        |            T__2 x___0 = x__4.Value;
                        |            t___0 = render__0(x___0);
                        |            T::TestGlobal.console___0.Log(t___0);
                        |        }
                        |    }
                        |    public Far()
                        |    {
                        |    }
                        |    public bool F(C::Optional<T__2> x___1)
                        |    {
                        |        return IBar.FDefault(this, x___1);
                        |    }
                        |}
                    """.trimMargin(),
                ),
                "TestGlobal" to Content(
                    usings = """
                        |using S = MyTestLibrary.Support;
                        |using C = TemperLang.Core;
                    """.trimMargin(),
                    decls = """
                        |public static class TestGlobal
                        |{
                        |    internal static C::ILoggingConsole console___0;
                        |    internal static Far<int> far__0;
                        |    static TestGlobal()
                        |    {
                        |        console___0 = S::Logging.LoggingConsoleFactory.CreateConsole("MyTestLibrary.Test");
                        |        far__0 = new Far<int>();
                        |        console___0.Log(far__0.F(C::Optional.Of<int>(0)).ToString().ToLower());
                        |        console___0.Log(far__0.F(C::Optional<int>.None).ToString().ToLower());
                        |    }
                        |}
                    """.trimMargin(),
                ),
            ),
        )
    }

    @Test
    fun nullAssignedToTypeReferenceOrNull() = assertGeneratedUserClass(
        temper = """
            |export class Test<T> {
            |  public f(x: T, b: Boolean): T? {
            |    if (b) { x } else { null }
            |  }
            |}
        """.trimMargin(),
        usings = "using C = TemperLang.Core;",
        csharp = """
            |public class Test<T__0>
            |{
            |    public C::Optional<T__0> F(T__0 x__0, bool b__0)
            |    {
            |        C::Optional<T__0> return__0;
            |        if (b__0)
            |        {
            |            return__0 = x__0;
            |        }
            |        else
            |        {
            |            return__0 = C::Optional<T__0>.None;
            |        }
            |        return return__0;
            |    }
            |    public Test()
            |    {
            |    }
            |}
        """.trimMargin(),
    )

    @Test
    fun identityOrNullInsertingExplicitCasts() = assertGenerateWanted(
        temper = """
            |export class Identity<T> {
            |  public identity(x: T?): T? { x }
            |}
            |
            |let logStringOrNot(s: String?): Void {
            |  let t = new Identity<String>().identity(s);
            |  console.log(t ?? "not");
            |}
            |
            |logStringOrNot("Hello, World!");
        """.trimMargin(),
        classes = mapOf(
            "Identity" to Content(
                usings = "using C = TemperLang.Core;",
                decls = """
                    |public class Identity<T__0>
                    |{
                    |    public C::Optional<T__0> Identity_(C::Optional<T__0> x__0)
                    |    {
                    |        return x__0;
                    |    }
                    |    public Identity()
                    |    {
                    |    }
                    |}
                """.trimMargin(),
            ),
            "TestGlobal" to Content(
                usings = """
                    |using S = MyTestLibrary.Support;
                    |using C = TemperLang.Core;
                """.trimMargin(),
                decls = """
                    |public static class TestGlobal
                    |{
                    |    internal static C::ILoggingConsole console___0;
                    |    internal static void logStringOrNot__0(string ? s__0)
                    |    {
                    |        string t___0;
                    |        string ? t__0 = C::Optional.OrNull<string>(new Identity<string>().Identity_(C::Optional.Of<string>(s__0)));
                    |        if (!(t__0 == null))
                    |        {
                    |            string t___1 = t__0!;
                    |            t___0 = t___1;
                    |        }
                    |        else
                    |        {
                    |            t___0 = "not";
                    |        }
                    |        console___0.Log(t___0);
                    |    }
                    |    static TestGlobal()
                    |    {
                    |        console___0 = S::Logging.LoggingConsoleFactory.CreateConsole("MyTestLibrary.Test");
                    |        logStringOrNot__0("Hello, World!");
                    |    }
                    |}
                """.trimMargin(),
            ),
        ),
    )

    @Test
    fun genericComparisonShouldNotRequireNullBoxing() = assertGeneratedGlobalClass(
        temper = """
            |export let f(i: StringIndex, j: StringIndex): Boolean { i < j }
        """.trimMargin(),
        usings = """""".trimMargin(),
        csharp = """
            |public static bool F(int i__0, int j__0)
            |{
            |    return i__0 < j__0;
            |}
        """.trimMargin(),
    )

    @Test
    fun instantiateInterface() = assertGeneratedGlobalClass(
        temper = """
            |new Listed();
        """.trimMargin(),
        usings = """
            |using C = TemperLang.Core;
        """.trimMargin(),
        csharp = """
            |static TestGlobal()
            |{
            |    C::Core.Garbage("Invalid type for `new`");
            |}
        """.trimMargin(),
        errors = listOf(
            "Cannot instantiate abstract type Listed!",
        ),
    )

    @Test
    fun unboxOptionalEvenWhenReturnedFromSpecializedType() = assertGenerateWanted(
        temper = """
            |export interface Base<T> { f(): T? }
            |
            |export class C extends Base<Int> {
            |  public f(): Int { 0 }
            |}
            |
            |// The initializer needs unboxing because C.f's type is adjusted to C::Optional<int>
            |export let i: Int = new C().f();
        """.trimMargin(),
        classes = mapOf(
            "IBase" to Content(
                usings = """
                    |using C = TemperLang.Core;
                """.trimMargin(),
                decls = """
                    |public interface IBase<T__0>
                    |{
                    |    C::Optional<T__0> F();
                    |}
                """.trimMargin(),
            ),
            "C" to Content(
                usings = """
                    |using C = TemperLang.Core;
                """.trimMargin(),
                decls = """
                    |public class C: IBase<int>
                    |{
                    |    public C::Optional<int> F()
                    |    {
                    |        return C::Optional.Of<int>(f__0());
                    |    }
                    |    int f__0()
                    |    {
                    |        return 0;
                    |    }
                    |    public C()
                    |    {
                    |    }
                    |}
                """.trimMargin(),
            ),
            "TestGlobal" to Content(
                usings = """
                    |using C = TemperLang.Core;
                """.trimMargin(),
                decls = """
                    |public static class TestGlobal
                    |{
                    |    public static int I;
                    |    static TestGlobal()
                    |    {
                    |        I = C::Optional.ToNullable<int>(new C().F());
                    |    }
                    |}
                """.trimMargin(),
            ),
        ),
    )

    @Test
    fun callToFunctionWithGenericTypeParameter() = assertGeneratedGlobalClass(
        temper = """
            |export let or<T>(x: T, y: T?): T {
            |  if (y == null) { x } else { y }
            |}
            |export let f(x: String, y: String?): String {
            |  or(x, y)
            |}
            |export let g(x: String, y: String?): String {
            |  or<String>(x, y)
            |}
        """.trimMargin(),
        usings = """
            |using C = TemperLang.Core;
        """.trimMargin(),
        csharp = """
            |public static T__0 Or<T__0>(T__0 x__0, C::Optional<T__0> y__0)
            |{
            |    T__0 return__0;
            |    if (!y__0.HasValue)
            |    {
            |        return__0 = x__0;
            |    }
            |    else
            |    {
            |        return__0 = y__0.Value;
            |    }
            |    return return__0;
            |}
            |public static string F(string x__1, string ? y__1)
            |{
            |    return Or(x__1, C::Optional.Of<string>(y__1));
            |}
            |public static string G(string x__2, string ? y__2)
            |{
            |    return Or(x__2, C::Optional.Of<string>(y__2));
            |}
        """.trimMargin(),
    )

    @Test
    fun propertyInInterface() = assertGeneratedUserClass(
        temper = """
            |export interface I {
            |  public x: String;
            |}
        """.trimMargin(),
        cSharpClassName = "II",
        csharp = """
            |public interface II
            |{
            |    string X
            |    {
            |        get;
            |    }
            |}
        """.trimMargin(),
    )

    @Test
    fun translatedTestMethodsShouldHaveNameMetadata() = assertGeneratedFileTree(
        // Records the current state of test translation but with some
        // possible leads on fixing things below.
        inputs = inputFileMapFromJson(
            """
                |{
                |  test: {
                |    impl.temper: ```
                |      test("custom test name") {
                |        assert(1 == 1);
                |      }
                |      ```
                |  }
                |}
            """.trimMargin(),
        ),
        wantJson = """
            |{
            |  csharp: {
            |    my-test-library: {
            |      tests: {
            |        Test: {
            |          TestTests.cs: {
            |            content: ```
            |              using U = Microsoft.VisualStudio.TestTools.UnitTesting;
            |              using S = System;
            |              using T = TemperLang.Std.Testing;
            |              namespace MyTestLibrary.Test
            |              {
            |                  [U::TestClass]
            |                  public class TestTests
            |                  {
            |                      [U::TestMethod]
            |                      public void customTestName__0()
            |                      {
            |## TODO: Could we make this Test instance into a U.TestContext object that is
            |## stored globally and create it in a [TestInitialize] Setup() method?
            |## If that works, then would setting .TestName to the Temper test name
            |## at the top of the test method affect the JUnit output?
            |                          T::Test test___0 = new T::Test();
            |                          try
            |                          {
            |                              string fn__0()
            |                              {
            |                                  return "expected 1 == (" + S::Convert.ToString(1) + ") not (" + S::Convert.ToString(1) + ")";
            |                              }
            |                              test___0.Assert(true, (S::Func<string>) fn__0);
            |                          }
            |                          finally
            |                          {
            |                              test___0.SoftFailToHard();
            |                          }
            |                      }
            |                  }
            |              }
            |
            |              ```
            |          },
            |          TestTests.cs.map: "__DO_NOT_CARE__",
            |        },
            |        MyTestLibraryTest.csproj: "__DO_NOT_CARE__",
            |      },
            |      src: {
            |        Test: {
            |          TestGlobal.cs: "__DO_NOT_CARE__",
            |          TestGlobal.cs.map: "__DO_NOT_CARE__",
            |        },
            |        MyTestLibrary.csproj: "__DO_NOT_CARE__",
            |        MyTestLibraryGlobal.cs: "__DO_NOT_CARE__",
            |        Logging.cs: "__DO_NOT_CARE__",
            |      },
            |      program: {
            |        Program.cs: "__DO_NOT_CARE__",
            |        MyTestLibraryProgram.csproj: "__DO_NOT_CARE__",
            |      },
            |    }
            |  }
            |}
        """.trimMargin().stripDoubleHashCommentLinesToPutCommentsInlineBelow(),
    )
}

private fun assertGenerateWanted(
    temper: String,
    classes: Map<String, Content>,
    errors: List<String> = listOf(),
    moduleName: String = "test",
    namespaceName: String = moduleName.camelToPascal(),
) = assertGenerateWanted(
    inputs = listOf(filePath(moduleName, "impl.temper") to temper),
    classes = classes,
    errors = errors,
    moduleName = moduleName,
    namespaceName = namespaceName,
)
private fun assertGenerateWanted(
    inputs: List<Pair<FilePath, String>>,
    classes: Map<String, Content>,
    errors: List<String> = listOf(),
    moduleName: String = "test",
    namespaceName: String = moduleName.camelToPascal(),
) {
    val want = object : Structured {
        override fun destructure(structureSink: StructureSink) = structureSink.run {
            obj {
                key("csharp") {
                    obj {
                        key("my-test-library") {
                            obj {
                                key("program") {
                                    obj {
                                        key("MyTestLibraryProgram.csproj") { value("__DO_NOT_CARE__") }
                                        key("Program.cs") { value("__DO_NOT_CARE__") }
                                    }
                                }
                                key("src") {
                                    obj {
                                        key(namespaceName) {
                                            obj {
                                                classes.entries.forEach { (className, content) ->
                                                    key("$className.cs") {
                                                        obj {
                                                            key("content") {
                                                                value(
                                                                    buildString {
                                                                        if (content.usings.isNotEmpty()) {
                                                                            append(content.usings)
                                                                            append("\n")
                                                                        }
                                                                        append("namespace MyTestLibrary.")
                                                                        append(namespaceName)
                                                                        append("\n")
                                                                        append("{\n")
                                                                        append(content.decls.prependIndent("    "))
                                                                        append("\n}\n")
                                                                    },
                                                                )
                                                            }
                                                        }
                                                    }
                                                    key("$className.cs.map") {
                                                        value("__DO_NOT_CARE__")
                                                    }
                                                }
                                                val globalName = "${namespaceName}Global"
                                                if (globalName !in classes) {
                                                    key("$globalName.cs") {
                                                        value("__DO_NOT_CARE__")
                                                    }
                                                    key("$globalName.cs.map") {
                                                        value("__DO_NOT_CARE__")
                                                    }
                                                }
                                            }
                                        }
                                        key("Logging.cs") { value("__DO_NOT_CARE__") }
                                        key("MyTestLibrary.csproj") { value("__DO_NOT_CARE__") }
                                        key("MyTestLibraryGlobal.cs") { value("__DO_NOT_CARE__") }
                                    }
                                }
                            }
                        }
                    }
                }
                if (errors.isNotEmpty()) {
                    key("errors") {
                        arr {
                            for (error in errors) {
                                value(error)
                            }
                        }
                    }
                }
            }
        }
    }

    assertGeneratedFileTree(
        inputs = inputs,
        wantJson = FormattingStructureSink.toJsonString(want),
    )
}

private fun assertGeneratedGlobalClass(
    temper: String,
    usings: String,
    csharp: String,
    errors: List<String> = listOf(),
) {
    assertGenerateWanted(
        temper = temper,
        classes = mapOf(
            "TestGlobal" to Content(
                usings = usings,
                decls = """
                    |public static class TestGlobal
                    |{
                    |${csharp.prependIndent("    ")}
                    |}
                """.trimMargin(),
            ),
        ),
        errors = errors,
    )
}

private fun assertGeneratedUserClass(
    temper: String,
    csharp: String,
    usings: String = "",
    cSharpClassName: String = "Test",
) {
    assertGenerateWanted(
        temper = temper,
        classes = mapOf(
            cSharpClassName to Content(
                usings = usings,
                decls = """
                    |${csharp}
                """.trimMargin(),
            ),
        ),
    )
}

private fun assertGeneratedFileTree(
    inputs: List<Pair<FilePath, String>>,
    wantJson: String,
) = assertGeneratedCode(
    backendConfig = Backend.Config.production,
    factory = CSharpBackend.Factory,
    inputs = inputs,
    moduleResultNeeded = false,
    want = wantJson,
)

internal class Content(val usings: String, val decls: String)
