package lang.temper.be.java

import lang.temper.be.inputFileMapFromJson
import lang.temper.common.stripDoubleHashCommentLinesToPutCommentsInlineBelow
import kotlin.test.Test

@SuppressWarnings("MaxLineLength")
class JavaBackendTest {
    @Test
    fun simpleEqualityInt() {
        assertGeneratedJava(
            """
                |/** true iff passed 1 */
                |export let t(/** x could be 1 or not*/ x: Int): Boolean { return x == 1; }
            """.trimMargin(),
            """
                |/**
                | * true iff passed 1
                | *
                | * @param x__2
                | *   x could be 1 or not
                | */
                |public static boolean t(int x__2) {
                |    return x__2 == 1;
                |}
            """.javaMethod(),
        )
    }

    @Test
    fun nullableEqualityInt() {
        assertGeneratedJava(
            """
                |/** is it 1? */
                |export let t(x: Int?): Boolean { return x == 1; }
            """.trimMargin(),
            // There is no blank @param entry for x.  Javadoc warns on those.
            """
                |/**
                | * is it 1?
                | */
                |public static boolean t(@Nullable Integer x__0) {
                |    return Core.boxedEq(x__0, 1);
                |}
            """.javaMethod("import temper.core.Core;", "import temper.core.Nullable;"),
        )
    }

    @Test
    fun nullableEqualityFlippedInt() {
        assertGeneratedJava(
            "export let t(x: Int?): Boolean { return 1 == x; }",
            """
            |public static boolean t(@Nullable Integer x__0) {
            |    return Core.boxedEqRev(1, x__0);
            |}
            """.javaMethod("import temper.core.Core;", "import temper.core.Nullable;"),
        )
    }

    @Test
    fun nullableEqualityBothInt() {
        assertGeneratedJava(
            "export let t(x: Int?, y: Int?): Boolean { return x == y; }",
            """
            |public static boolean t(@Nullable Integer x__0, @Nullable Integer y__0) {
            |    return Objects.equals(x__0, y__0);
            |}
            """.javaMethod("import temper.core.Nullable;", "import java.util.Objects;"),
        )
    }

    @Test
    fun simpleInequalityInt() {
        assertGeneratedJava(
            "export let t(x: Int): Boolean { return x != 1; }",
            """
            |public static boolean t(int x__2) {
            |    return x__2 != 1;
            |}
            """.javaMethod(),
        )
    }

    @Test
    fun nullableInequalityInt() {
        assertGeneratedJava(
            "export let t(x: Int?): Boolean { return x != 1; }",
            """
            |public static boolean t(@Nullable Integer x__0) {
            |    return !Core.boxedEq(x__0, 1);
            |}
            """.javaMethod("import temper.core.Core;", "import temper.core.Nullable;"),
        )
    }

    @Test
    fun nullableInequalityFlippedInt() {
        assertGeneratedJava(
            "export let t(x: Int?): Boolean { return 1 != x; }",
            """
            |public static boolean t(@Nullable Integer x__0) {
            |    return !Core.boxedEqRev(1, x__0);
            |}
            """.javaMethod("import temper.core.Core;", "import temper.core.Nullable;"),
        )
    }

    @Test
    fun nullableInequalityBothInt() {
        assertGeneratedJava(
            "export let t(x: Int?, y: Int?): Boolean { return x != y; }",
            """
            |public static boolean t(@Nullable Integer x__0, @Nullable Integer y__0) {
            |    return !Objects.equals(x__0, y__0);
            |}
            """.javaMethod("import temper.core.Nullable;", "import java.util.Objects;"),
        )
    }

    @Test
    fun simpleEqualityFloat64() {
        assertGeneratedJava(
            "export let t(x: Float64): Boolean { return x == 1.5; }",
            """
            |public static boolean t(double x__0) {
            |    return Double.doubleToLongBits(x__0) == Double.doubleToLongBits(1.5D);
            |}
            """.javaMethod(),
        )
    }

    @Test
    fun nullableEqualityFloat64() {
        assertGeneratedJava(
            "export let t(x: Float64?): Boolean { return x == 1.5; }",
            """
            |public static boolean t(@Nullable Double x__0) {
            |    return Core.boxedEq(x__0, 1.5D);
            |}
            """.javaMethod("import temper.core.Core;", "import temper.core.Nullable;"),
        )
    }

    @Test
    fun nullableEqualityFlippedFloat64() {
        assertGeneratedJava(
            "export let t(x: Float64?): Boolean { return 1.5 == x; }",
            """
            |public static boolean t(@Nullable Double x__0) {
            |    return Core.boxedEqRev(1.5D, x__0);
            |}
            """.javaMethod("import temper.core.Core;", "import temper.core.Nullable;"),
        )
    }

    @Test
    fun nullableEqualityBothFloat64() {
        assertGeneratedJava(
            "export let t(x: Float64?, y: Float64?): Boolean { return x == y; }",
            """
            |public static boolean t(@Nullable Double x__0, @Nullable Double y__0) {
            |    return Objects.equals(x__0, y__0);
            |}
            """.javaMethod("import temper.core.Nullable;", "import java.util.Objects;"),
        )
    }

    @Test
    fun simpleInequalityFloat64() {
        assertGeneratedJava(
            "export let t(x: Float64): Boolean { return x != 1.5; }",
            """
            |public static boolean t(double x__0) {
            |    return Double.doubleToLongBits(x__0) != Double.doubleToLongBits(1.5D);
            |}
            """.javaMethod(),
        )
    }

    @Test
    fun nullableInequalityFloat64() {
        assertGeneratedJava(
            "export let t(x: Float64?): Boolean { return x != 1.5; }",
            """
            |public static boolean t(@Nullable Double x__0) {
            |    return !Core.boxedEq(x__0, 1.5D);
            |}
            """.javaMethod("import temper.core.Core;", "import temper.core.Nullable;"),
        )
    }

    @Test
    fun nullableInequalityFlippedFloat64() {
        assertGeneratedJava(
            "export let t(x: Float64?): Boolean { return 1.5 != x; }",
            """
            |public static boolean t(@Nullable Double x__0) {
            |    return !Core.boxedEqRev(1.5D, x__0);
            |}
            """.javaMethod("import temper.core.Core;", "import temper.core.Nullable;"),
        )
    }

    @Test
    fun nullableInequalityBothFloat64() {
        assertGeneratedJava(
            "export let t(x: Float64?, y: Float64?): Boolean { return x != y; }",
            """
            |public static boolean t(@Nullable Double x__0, @Nullable Double y__0) {
            |    return !Objects.equals(x__0, y__0);
            |}
            """.javaMethod("import temper.core.Nullable;", "import java.util.Objects;"),
        )
    }

    @Test
    fun simpleEqualityComparable() {
        assertGeneratedJava(
            "export let t(x: String): Boolean { return x == \"foo\"; }",
            """
            |public static boolean t(String x__2) {
            |    return x__2.equals("foo");
            |}
            """.javaMethod(),
        )
    }

    @Test
    fun nullableEqualityComparable() {
        assertGeneratedJava(
            "export let t(x: String?): Boolean { return x == \"foo\"; }",
            """
            |public static boolean t(@Nullable String x__0) {
            |    return Objects.equals(x__0, "foo");
            |}
            """.javaMethod("import java.util.Objects;", "import temper.core.Nullable;"),
        )
    }

    @Test
    fun nullableEqualityFlippedComparable() {
        assertGeneratedJava(
            "export let t(x: String?): Boolean { return \"foo\" == x; }",
            """
            |public static boolean t(@Nullable String x__0) {
            |    return "foo".equals(x__0);
            |}
            """.javaMethod("import temper.core.Nullable;"),
        )
    }

    @Test
    fun nullableEqualityBothComparable() {
        assertGeneratedJava(
            "export let t(x: String?, y: String?): Boolean { return x == y; }",
            """
            |public static boolean t(@Nullable String x__0, @Nullable String y__0) {
            |    return Objects.equals(x__0, y__0);
            |}
            """.javaMethod("import temper.core.Nullable;", "import java.util.Objects;"),
        )
    }

    @Test
    fun simpleInequalityComparable() {
        assertGeneratedJava(
            "export let t(x: String): Boolean { return x != \"foo\"; }",
            """
            |public static boolean t(String x__2) {
            |    return !x__2.equals("foo");
            |}
            """.javaMethod(),
        )
    }

    @Test
    fun nullableInequalityComparable() {
        assertGeneratedJava(
            "export let t(x: String?): Boolean { return x != \"foo\"; }",
            """
            |public static boolean t(@Nullable String x__0) {
            |    return !Objects.equals(x__0, "foo");
            |}
            """.javaMethod("import java.util.Objects;", "import temper.core.Nullable;"),
        )
    }

    @Test
    fun nullableInequalityFlippedComparable() {
        assertGeneratedJava(
            "export let t(x: String?): Boolean { return \"foo\" != x; }",
            """
            |public static boolean t(@Nullable String x__0) {
            |    return !"foo".equals(x__0);
            |}
            """.javaMethod("import temper.core.Nullable;"),
        )
    }

    @Test
    fun nullableInequalityBothComparable() {
        assertGeneratedJava(
            "export let t(x: String?, y: String?): Boolean { return x != y; }",
            """
            |public static boolean t(@Nullable String x__0, @Nullable String y__0) {
            |    return !Objects.equals(x__0, y__0);
            |}
            """.javaMethod("import temper.core.Nullable;", "import java.util.Objects;"),
        )
    }

    @Test
    fun failure() {
        assertGeneratedJava(
            """
            |export let foo(src: String, flag: Boolean): String throws Bubble {
            |  if (flag) {
            |    return src;
            |  } else {
            |    bubble();
            |  }
            |}
            """.trimMargin(),
            """
            |public static String foo(String src__0, boolean flag__0) {
            |    String return__0;
            |    if (flag__0) {
            |        return__0 = src__0;
            |    } else {
            |        throw Core.bubble();
            |    }
            |    return return__0;
            |}
            """.javaMethod("import temper.core.Core;"),
        )
    }

    @Test
    fun returnFailure() {
        assertGeneratedJava(
            """
            |export let foo(src: String, flag: Boolean): String throws Bubble {
            |  if (flag) {
            |    return src;
            |  } else {
            |    return bubble();
            |  }
            |}
            """.trimMargin(),
            """
            |public static String foo(String src__0, boolean flag__0) {
            |    String return__0;
            |    if (flag__0) {
            |        return__0 = src__0;
            |    } else {
            |        throw Core.bubble();
            |    }
            |    return return__0;
            |}
            """.javaMethod("import temper.core.Core;"),
        )
    }

    @Test
    fun consoleLog() {
        assertGeneratedJava(
            // Use the console.log as the return value to verify we skip that in Java.
            """export let greet(): Void { console.log("Hi!") }; greet()""",
            javaParts(
                fields = """
                |static final Console console_2;
                """,
                imports = listOf(
                    "import temper.core.Core;",
                    "import java.util.logging.Logger;",
                    "import temper.core.Core.Console;",
                ),
                methods = """
                |public static void greet() {
                |    console_2.log("Hi!");
                |}
                |static {
                |    console_2 = Core.getConsole(Logger.getLogger("my_test_library.test"));
                |    TestGlobal.greet();
                |}
                """,
            ),
        )
    }

    @Test
    fun moduleFields() {
        assertGeneratedJava(
            """
                |export let apple = do { cherry = 4; banana + cherry };
                |let banana = do { cherry = 1; cherry * 2 };
                |var cherry = 3;
            """.trimMargin(),
            javaParts(
                fields = """
                    |static int cherry__0;
                    |static final int banana__0;
                    |public static final int apple;
                """,
                methods = """
                    |static {
                    |    cherry__0 = 3;
                    |    cherry__0 = 1;
                    |    banana__0 = cherry__0 * 2;
                    |    cherry__0 = 4;
                    |    apple = banana__0 + cherry__0;
                    |}
                """,
            ),
        )
    }

    @Test
    fun badCapture() {
        assertGeneratedJava(
            $$"""
                |export let hi(n: Int): Void {
                |  for (var i = 0; i < n; i += 1) {
                |    var a = "${i}";
                |    let blah(m: String): Void {
                |      a = m;
                |    }
                |    blah("hi");
                |  }
                |}
            """.trimMargin(),
            """
                |public static void hi(int n__0) {
                |    class Local_1 {
                |        String t_12;
                |    }
                |    final Local_1 local$1 = new Local_1();
                |    int i__0 = 0;
                |    while (i__0 < n__0) {
                |        local$1.t_12 = Integer.toString(i__0);
                |        class Local_2 {
                |            String a__0 = local$1.t_12;
                |        }
                |        final Local_2 local$2 = new Local_2();
                |        Consumer<String> blah__0 = m__0 -> {
                |            local$2.a__0 = m__0;
                |        };
                |        blah__0.accept("hi");
                |        i__0 = i__0 + 1;
                |    }
                |}
            """.javaMethod("import java.util.function.Consumer;"),
        )
    }

    @Test
    fun constantCapture() {
        assertGeneratedJava(
            """
            |export let foo(y: Int): Void {
            |  const x: Int = 55 + y;
            |  let a(): Int { x }
            |}
            """.trimMargin(),
            """
            |public static void foo(int y__0) {
            |    int x__0 = 55 + y__0;
            |    IntSupplier a__0 = () -> x__0;
            |}
            """.javaMethod("import java.util.function.IntSupplier;"),
        )
    }

    @Test
    fun mutableCapture() {
        assertGeneratedJava(
            """
            |export let foo(y: Int): Void {
            |  var x: Int = y;
            |  let a(): Void {
            |    x += 2;
            |  }
            |}
            """.trimMargin(),
            """
            |public static void foo(int y__0) {
            |    class Local_1 {
            |        int x__0 = y__0;
            |    }
            |    final Local_1 local$1 = new Local_1();
            |    Runnable a__0 = () -> {
            |        local$1.x__0 = local$1.x__0 + 2;
            |    };
            |}
            """.javaMethod(),
        )
    }

    @Test
    fun moreMutableCapture() {
        assertGeneratedJava(
            """
                |export let thresh(values: Listed<Int>, var value: Int): List<Int> {
                |  value += 1;
                |  values.filter { it => it > value }
                |}
                |export let thresh2(values: Listed<Int>, value: Int): List<Int> {
                |  var value = value;
                |  value += 1;
                |  values.filter { it => it > value }
                |}
            """.trimMargin(),
            javaParts(
                imports = listOf(
                    "import java.util.List;",
                    "import temper.core.Core;",
                    "import java.util.function.IntPredicate;",
                ),
                methods = $$"""
                    |public static List<Integer> thresh(List<Integer> values__0, int value__0) {
                    |    int value__0$capture = value__0;
                    |    class Local_1 {
                    |        int value__0 = value__0$capture;
                    |    }
                    |    final Local_1 local$1 = new Local_1();
                    |    local$1.value__0 = local$1.value__0 + 1;
                    |    IntPredicate fn__0 = it__0 -> it__0 > local$1.value__0;
                    |    return Core.listFilterInt(values__0, fn__0);
                    |}
                    |public static List<Integer> thresh2(List<Integer> values__1, int value__1) {
                    |    class Local_2 {
                    |        int value__2 = value__1;
                    |    }
                    |    final Local_2 local$2 = new Local_2();
                    |    local$2.value__2 = local$2.value__2 + 1;
                    |    IntPredicate fn__1 = it__1 -> it__1 > local$2.value__2;
                    |    return Core.listFilterInt(values__1, fn__1);
                    |}
                """,
            ),
        )
    }

    @Test
    fun selfRecursion() {
        assertGeneratedJava(
            """
            |export let foo(): Void {
            |  let a(): Void {
            |    a();
            |  }
            |}
            """.trimMargin(),
            """
            |public static void foo() {
            |    class Local_1 {
            |        void a__0() {
            |            this.a__0();
            |        }
            |    }
            |    final Local_1 local$1 = new Local_1();
            |}
            """.javaMethod(),
        )
    }

    @Test
    fun selfRecursionAwkwardCapture() {
        // This was making code that tries to forward reference a local.
        assertGeneratedJava(
            """
            |export let blah(): Void {
            |  var a = 1;
            |  let hi(): Void {
            |    if (a > 0) {
            |      a -= 1;
            |      hi();
            |    }
            |  }
            |  a = 2;
            |  hi();
            |}
            """.trimMargin(),
            $$"""
            |public static void blah() {
            |    class Local_1 {
            |        int a__0 = 1;
            |        void hi__0() {
            |            if (this.a__0 > 0) {
            |                this.a__0 = this.a__0 - 1;
            |                this.hi__0();
            |            }
            |        }
            |    }
            |    final Local_1 local$1 = new Local_1();
            |    local$1.a__0 = 2;
            |    local$1.hi__0();
            |}
            """.javaMethod(),
        )
    }

    @Test
    fun mutualRecursion() {
        assertGeneratedJava(
            """
            |export let foo(y: Int): Void {
            |  var x = y;
            |  let a(): Void {
            |    x += 1;
            |    b();
            |  }
            |  let b(): Void {
            |    if (x < 3) {
            |      a();
            |    }
            |  }
            |  if (x > 0) {
            |    var z = x;
            |    let c(): Void {
            |      z += y;
            |      b();
            |    }
            |    c();
            |    x += z;
            |  }
            |  b();
            |}
            """.trimMargin(),
            """
            |public static void foo(int y__0) {
            |    class Local_1 {
            |        void b__0() {
            |            if (x__0 < 3) {
            |                a__0.run();
            |            }
            |        }
            |        int x__0 = y__0;
            |        Runnable a__0;
            |    }
            |    final Local_1 local$1 = new Local_1();
            |    local$1.a__0 = () -> {
            |        local$1.x__0 = local$1.x__0 + 1;
            |        local$1.b__0();
            |    };
            |    if (local$1.x__0 > 0) {
            |        class Local_2 {
            |            int z__0 = local$1.x__0;
            |        }
            |        final Local_2 local$2 = new Local_2();
            |        Runnable c__0 = () -> {
            |            local$2.z__0 = local$2.z__0 + y__0;
            |            local$1.b__0();
            |        };
            |        c__0.run();
            |        local$1.x__0 = local$1.x__0 + local$2.z__0;
            |    }
            |    local$1.b__0();
            |}
            """.javaMethod(),
        )
    }

    @Test
    fun classes() {
        assertGeneratedJavaRaw(
            """
                |class Apple extends Cherry {}
                |/** Yellow, but not a lemon */
                |export class Banana<T>(
                |  private let grape: Int,
                |  public honeydew: T,
                |  /** not the bird */
                |  public var kiwi: Int = 2,
                |) {
                |  private eggplant(): Apple { new Apple() }
                |  /** Figures */
                |  public fig(): Void {}
                |  private set icaco(/** how plummy can you go? */i: Int): Void {}
                |  public get jackfruit(): Int { 5 }
                |  public static lemon: Int = 6;
                |}
                |/** Sweet? Sour? ¯\_(ツ)_/¯ emoji */
                |interface Cherry {}
                |export interface Durian {}
            """.trimMargin(),
            """
                |"pom.xml": "__DO_NOT_CARE__",
                |"src": {
                |    "main": {
                |        "java": {
                |            "my_test_library": {
                |                "test": {
                |                    "TestGlobal.java": "__DO_NOT_CARE__",
                |                    "TestGlobal.java.map": "__DO_NOT_CARE__",
                |                    "TestMain.java": "__DO_NOT_CARE__",
                |                    "TestMain.java.map": "__DO_NOT_CARE__",
                |                    "Apple.java": {
                |                        "content":
                |                        ```
                |                        package my_test_library.test;
                |                        final class Apple implements Cherry {
                |                            public Apple() {
                |                            }
                |                        }
                |
                |                        ```
                |                    },
                |                    "Apple.java.map": "__DO_NOT_CARE__",
                |                    "Banana.java": {
                |                        "content":
                |                        ```
                |                        package my_test_library.test;
                |                        import temper.core.Nullable;
                |                        /**
                |                         * Yellow, but not a lemon
                |                         */
                |                        public final class Banana<T__0> {
                |                            final int grape;
                |                            public final T__0 honeydew;
                |                            /**
                |                             * not the bird
                |                             */
                |                            public int kiwi;
                |                            Apple eggplant() {
                |                                return new Apple();
                |                            }
                |                            /**
                |                             * Figures
                |                             */
                |                            public void fig() {
                |                            }
                |## There is no type defined for the icaco abstract property, so this isn't clearly wrong.
                |                            abstract Object getIcaco();
                |                            /**
                |                             *
                |                             * @param i__0
                |                             *   how plummy can you go?
                |                             */
                |                            void setIcaco(int i__0) {
                |                            }
                |                            public int getJackfruit() {
                |                                return 5;
                |                            }
                |                            public static final int lemon = 6;
                |                            public static final class Builder<T__0> {
                |                                int grape;
                |                                boolean grape__set;
                |                                public Builder<T__0> grape(int grape) {
                |                                    grape__set = true;
                |                                    this.grape = grape;
                |                                    return this;
                |                                }
                |                                T__0 honeydew;
                |                                public Builder<T__0> honeydew(T__0 honeydew) {
                |                                    this.honeydew = honeydew;
                |                                    return this;
                |                                }
                |                                @Nullable Integer kiwi;
                |                                public Builder<T__0> kiwi(@Nullable Integer kiwi) {
                |                                    this.kiwi = kiwi;
                |                                    return this;
                |                                }
                |                                public Banana<T__0> build() {
                |                                    if (!grape__set || honeydew == null) {
                |                                        StringBuilder _message = new StringBuilder("Missing required fields:");
                |                                        if (!grape__set) {
                |                                            _message.append(" grape");
                |                                        }
                |                                        if (honeydew == null) {
                |                                            _message.append(" honeydew");
                |                                        }
                |                                        throw new IllegalStateException(_message.toString());
                |                                    }
                |                                    return new Banana<>(grape, honeydew, kiwi);
                |                                }
                |                            }
                |                            public Banana(int grape__0, T__0 honeydew__0, @Nullable Integer kiwi__0) {
                |                                int kiwi__1;
                |                                if (kiwi__0 == null) {
                |                                    kiwi__1 = 2;
                |                                } else {
                |                                    kiwi__1 = kiwi__0;
                |                                }
                |                                this.grape = grape__0;
                |                                this.honeydew = honeydew__0;
                |                                this.kiwi = kiwi__1;
                |                            }
                |                            public Banana(int grape__0, T__0 honeydew__0) {
                |                                this(grape__0, honeydew__0, null);
                |                            }
                |                            public T__0 getHoneydew() {
                |                                return this.honeydew;
                |                            }
                |                            /**
                |                             * not the bird
                |                             */
                |                            public int getKiwi() {
                |                                return this.kiwi;
                |                            }
                |                            public void setKiwi(int newKiwi__0) {
                |                                this.kiwi = newKiwi__0;
                |                            }
                |                        }
                |
                |                        ```
                |                    },
                |                    "Banana.java.map": "__DO_NOT_CARE__",
                |                    "Cherry.java": {
                |                        "content":
                |                        ```
                |                        package my_test_library.test;
                |                        /**
                |                         * Sweet? Sour? ¯&#92;_(ツ)_/¯ emoji
                |                         */
                |                        interface Cherry {
                |                        }
                |
                |                        ```
                |                    },
                |                    "Cherry.java.map": "__DO_NOT_CARE__",
                |                    "Durian.java": {
                |                        "content":
                |                        ```
                |                        package my_test_library.test;
                |                        public interface Durian {
                |                        }
                |
                |                        ```
                |                    },
                |                    "Durian.java.map": "__DO_NOT_CARE__"
                |                },
                |                "MyTestLibraryGlobal.java": "__DO_NOT_CARE__",
                |                "MyTestLibraryMain.java": "__DO_NOT_CARE__",
                |            }
                |        }
                |    }
                |}
            """.trimMargin().stripDoubleHashCommentLinesToPutCommentsInlineBelow(),
        )
    }

    @Test
    fun coroutineConversion() = assertGeneratedJavaRaw(
        """
            |let f(factory: fn (): SafeGenerator<Empty>): Void {
            |  factory().next();
            |}
            |
            |f { (): GeneratorResult<Empty> extends GeneratorFn =>
            |  console.log("foo");
            |  yield;
            |  console.log("bar");
            |}
        """.trimMargin(),
        """
            |"pom.xml": "__DO_NOT_CARE__",
            |"src": {
            |    "main": {
            |        "java": {
            |            "my_test_library": {
            |                "test": {
            |                    "TestGlobal.java": {
            |                      content: ```
            |                          package my_test_library.test;
            |                          import java.util.Optional;
            |                          import temper.core.Generator;
            |                          import temper.core.Generator.DoneResult;
            |                          import temper.core.Core;
            |                          import temper.core.Generator.Result;
            |                          import java.util.logging.Logger;
            |                          import temper.core.Core.Console;
            |                          import java.util.function.Supplier;
            |                          import java.util.function.Function;
            |                          import temper.core.Generator.ValueResult;
            |                          public final class TestGlobal {
            |                              private TestGlobal() {
            |                              }
            |                              static final Console console_5;
            |                              static void f__0(Supplier<Generator<Optional<? super Object>>> factory__0) {
            |                                  factory__0.get().get();
            |                              }
            |                              static Generator<Optional<? super Object>> fn__0() {
            |                                  class Local_1 {
            |                                      int caseIndex_38 = 0;
            |                                  }
            |                                  final Local_1 local$1 = new Local_1();
            |                                  Function<Generator<Optional<? super Object>>, Result<Optional<? super Object>>> convertedCoroutine_42 = generator_37 -> {
            |                                      int caseIndexLocal_39 = local$1.caseIndex_38;
            |                                      local$1.caseIndex_38 = -1;
            |                                      switch (caseIndexLocal_39) {
            |                                          case 0:
            |                                              {
            |                                              console_5.log("foo");
            |                                              local$1.caseIndex_38 = 1;
            |                                              return new ValueResult<>(Optional.empty());
            |                                          }
            |                                          case 1:
            |                                              {
            |                                              console_5.log("bar");
            |                                              return DoneResult.get();
            |                                          }
            |                                          default:
            |                                              {
            |                                              return DoneResult.get();
            |                                          }
            |                                      }
            |                                  };
            |                                  return Core.safeAdaptGeneratorFn(convertedCoroutine_42 :: apply);
            |                              }
            |                              static {
            |                                  console_5 = Core.getConsole(Logger.getLogger("my_test_library.test"));
            |                                  TestGlobal.f__0(TestGlobal :: fn__0);
            |                              }
            |                          }
            |
            |                          ```
            |                    },
            |                    "TestGlobal.java.map": "__DO_NOT_CARE__",
            |                    "TestMain.java": "__DO_NOT_CARE__",
            |                    "TestMain.java.map": "__DO_NOT_CARE__",
            |                },
            |                "MyTestLibraryGlobal.java": "__DO_NOT_CARE__",
            |                "MyTestLibraryMain.java": "__DO_NOT_CARE__",
            |            }
            |        }
            |    }
            |}
        """.trimMargin(),
    )

    @Test
    fun importStatic() = assertGeneratedJava(
        """
            |let { Dot, Special } = import("std/regex");
            |export let dot(): Special { Dot }
        """.trimMargin(),
        """
            |public static Special dot() {
            |    return Dot;
            |}
        """.javaMethod(
            "import temper.std.regex.Special;",
            "import static temper.std.regex.RegexGlobal.Dot;",
        ),
    )

    @Test
    fun staticConnected() = assertGeneratedJava(
        input = """
            |let { Date } = import("std/temporal");
            |export let f(): Date { Date.fromIsoString("2001-02-03") orelse panic() }
        """.trimMargin(),
        want = """
            |public static LocalDate f() {
            |    LocalDate t_5;
            |    t_5 = LocalDate.parse("2001-02-03");
            |    return t_5;
            |}
        """.javaMethod(
            "import java.time.LocalDate;",
        ),
    )

    @Test
    fun sorting() = assertGeneratedJavaRaw(
        """
            |// Test custom generic function.
            |export let sortedThings<T>(things: List<T>, compare: fn (T, T): Int): List<T> {
            |   things.sorted(compare)
            |}
            |// And builtin connected sorting.
            |export let sortedInts(ints: List<Int>): List<Int> {
            |   ints.sorted { a, b => a - b }
            |}
            |export let sortedIntsOther(ints: List<Int>): List<Int> {
            |   sortedThings(ints) { a: Int, b: Int => a - b }
            |}
            |export let sortedFloats(floats: List<Float64>): List<Float64> {
            |   floats.sorted { a, b => (a - b).sign().toInt32Unsafe() }
            |}
            |export let sortedFloatsOther(floats: List<Float64>): List<Float64> {
            |   sortedThings(floats) { a: Float64, b: Float64 => (a - b).sign().toInt32Unsafe() }
            |}
        """.trimMargin(),
        """
            |"pom.xml": "__DO_NOT_CARE__",
            |"src": {
            |    "main": {
            |        "java": {
            |            "my_test_library": {
            |                "test": {
            |                    "TestGlobal.java": {
            |                        "content":
            |                        ```
            |                        package my_test_library.test;
            |                        import java.util.List;
            |                        import temper.core.Core;
            |                        import my_test_library.test.function.Float64Float64Int32;
            |                        import java.util.function.IntBinaryOperator;
            |                        import java.util.function.ToIntBiFunction;
            |                        public final class TestGlobal {
            |                            private TestGlobal() {
            |                            }
            |                            public static<T__0> List<T__0> sortedThings(List<T__0> things__0, ToIntBiFunction<T__0, T__0> compare__0) {
            |                                return Core.listSorted(things__0, compare__0 :: applyAsInt);
            |                            }
            |                            public static List<Integer> sortedInts(List<Integer> ints__0) {
            |                                IntBinaryOperator fn__0 = (a__0, b__0) -> a__0 - b__0;
            |                                return Core.listSortedInt(ints__0, fn__0);
            |                            }
            |                            public static List<Integer> sortedIntsOther(List<Integer> ints__1) {
            |                                IntBinaryOperator fn__1 = (a__1, b__1) -> a__1 - b__1;
            |                                return TestGlobal.sortedThings(ints__1, fn__1 :: applyAsInt);
            |                            }
            |                            public static List<Double> sortedFloats(List<Double> floats__0) {
            |                                Float64Float64Int32 fn__2 = (a__2, b__2) ->(int) Math.signum(a__2 - b__2);
            |                                return Core.listSorted(floats__0, fn__2 :: applyAsInt);
            |                            }
            |                            public static List<Double> sortedFloatsOther(List<Double> floats__1) {
            |                                Float64Float64Int32 fn__3 = (a__3, b__3) ->(int) Math.signum(a__3 - b__3);
            |                                return TestGlobal.sortedThings(floats__1, fn__3 :: applyAsInt);
            |                            }
            |                        }
            |
            |                        ```
            |                    },
            |                    "TestGlobal.java.map": "__DO_NOT_CARE__",
            |                    "TestMain.java": "__DO_NOT_CARE__",
            |                    "TestMain.java.map": "__DO_NOT_CARE__",
            |                    "function": {
            |                        "Float64Float64Int32.java": "__DO_NOT_CARE__",
            |                        "Float64Float64Int32.java.map": "__DO_NOT_CARE__"
            |                    }
            |                },
            |                "MyTestLibraryGlobal.java": "__DO_NOT_CARE__",
            |                "MyTestLibraryMain.java": "__DO_NOT_CARE__",
            |            }
            |        }
            |    }
            |}
        """.trimMargin(),
    )

    @Test
    fun fancyCallback() = assertGeneratedJavaRaw(
        """
            |export let something(i: Int, map: Map<Int, String>): List<Int> {
            |   map.toListWith { (key, value): Int => key + i }
            |}
        """.trimMargin(),
        """
            |"pom.xml": "__DO_NOT_CARE__",
            |"src": {
            |    "main": {
            |        "java": {
            |            "my_test_library": {
            |                "test": {
            |                    "TestGlobal.java": {
            |                      content: ```
            |                          package my_test_library.test;
            |                          import java.util.Map;
            |                          import java.util.List;
            |                          import temper.core.Core;
            |                          import my_test_library.test.function.Int32StringInt32;
            |                          public final class TestGlobal {
            |                              private TestGlobal() {
            |                              }
            |                              public static List<Integer> something(int i__0, Map<Integer, String> map__0) {
            |                                  Int32StringInt32 fn__0 = (key__0, value__0) -> key__0 + i__0;
            |                                  return Core.mappedToListWith(map__0, fn__0 :: applyAsInt);
            |                              }
            |                          }
            |
            |                          ```
            |                    },
            |                    "TestGlobal.java.map": "__DO_NOT_CARE__",
            |                    "TestMain.java": "__DO_NOT_CARE__",
            |                    "TestMain.java.map": "__DO_NOT_CARE__",
            |                    "function": {
            |                        "Int32StringInt32.java": {
            |                            content: ```
            |                                package my_test_library.test.function;
            |                                public interface Int32StringInt32 {
            |                                    int applyAsInt(int arg1, String arg2);
            |                                }
            |
            |                                ```,
            |                            "__DO_NOT_CARE__": "__DO_NOT_CARE__"
            |                        },
            |                        "Int32StringInt32.java.map": "__DO_NOT_CARE__",
            |                    }
            |                },
            |                "MyTestLibraryGlobal.java": "__DO_NOT_CARE__",
            |                "MyTestLibraryMain.java": "__DO_NOT_CARE__",
            |            }
            |        }
            |    }
            |}
        """.trimMargin(),
    )

    @Test
    fun genericConstructorCall() = assertGeneratedJavaRaw(
        // The goal here is to deal with generic callback functions
        // and function & constructor type actual inference.
        """
            |export class Hi<T>(public ha: fn (T): T) {}
            |export let makeHi<T>(ha: fn (T): T): Hi<T> { new Hi(ha) }
            |export let makeIntHi(j: Int): Hi<Int> { makeHi { (i: Int): Int => i + j } }
            |export let newIntHi(j: Int): Hi<Int> { new Hi(fn (i: Int): Int { i + j }) }
        """.trimMargin(),
        """
            |"pom.xml": "__DO_NOT_CARE__",
            |"src": {
            |    "main": {
            |        "java": {
            |            "my_test_library": {
            |                "test": {
            |                    "Hi.java": {
            |                      "content":
            |                          ```
            |                          package my_test_library.test;
            |                          import java.util.function.Function;
            |                          public final class Hi<T__0> {
            |                              public final Function<T__0, T__0> ha;
            |                              public Hi(Function<T__0, T__0> ha__0) {
            |                                  this.ha = ha__0;
            |                              }
            |                              public Function<T__0, T__0> getHa() {
            |                                  return this.ha;
            |                              }
            |                          }
            |
            |                          ```
            |                    },
            |                    "Hi.java.map": "__DO_NOT_CARE__",
            |                    "TestGlobal.java": {
            |                      content: ```
            |                          package my_test_library.test;
            |                          import java.util.function.IntUnaryOperator;
            |                          import java.util.function.Function;
            |                          public final class TestGlobal {
            |                              private TestGlobal() {
            |                              }
            |                              public static<T__1> Hi<T__1> makeHi(Function<T__1, T__1> ha__1) {
            |                                  return new Hi<>(ha__1);
            |                              }
            |                              public static Hi<Integer> makeIntHi(int j__0) {
            |                                  IntUnaryOperator fn__0 = i__0 -> i__0 + j__0;
            |                                  return TestGlobal.makeHi(fn__0 :: applyAsInt);
            |                              }
            |                              public static Hi<Integer> newIntHi(int j__1) {
            |                                  IntUnaryOperator fn__1 = i__1 -> i__1 + j__1;
            |                                  return new Hi<>(fn__1 :: applyAsInt);
            |                              }
            |                          }
            |
            |                          ```
            |                    },
            |                    "TestGlobal.java.map": "__DO_NOT_CARE__",
            |                    "TestMain.java": "__DO_NOT_CARE__",
            |                    "TestMain.java.map": "__DO_NOT_CARE__",
            |                },
            |                "MyTestLibraryGlobal.java": "__DO_NOT_CARE__",
            |                "MyTestLibraryMain.java": "__DO_NOT_CARE__",
            |            }
            |        }
            |    }
            |}
        """.trimMargin(),
    )

    @Test
    fun reduceAll() {
        assertGeneratedJava(
            """
                |export let all<T>(items: Listed<T>, check: fn (T): Boolean): Boolean {
                |  items.reduceFrom(true) { (result: Boolean, it: T): Boolean => result && check(it) }
                |}
            """.trimMargin(),
            javaParts(
                imports = listOf(
                    "import java.util.List;",
                    "import temper.core.Core;",
                    "import java.util.function.Predicate;",
                    "import java.util.function.BiPredicate;",
                ),
                methods = """
                    |public static<T__0> boolean all(List<T__0> items__0, Predicate<T__0> check__0) {
                    |    BiPredicate<Boolean, T__0> fn__0 = (result__0, it__0) -> {
                    |        boolean return__0;
                    |        if (result__0) {
                    |            return__0 = check__0.test(it__0);
                    |        } else {
                    |            return__0 = false;
                    |        }
                    |        return return__0;
                    |    };
                    |    return Core.listedReduceObjToBool(items__0, true, fn__0 :: test);
                    |}
                """,
            ),
        )
    }

    @Test
    fun connectionOfPromiseTypes() = assertGeneratedJavaRaw(
        input = """
            |let b = new PromiseBuilder<String>();
            |let p = b.promise;
            |async { (): GeneratorResult<Empty> extends GeneratorFn =>
            |  console.log(await p orelse "broken");
            |}
            |b.complete("Hi");
        """.trimMargin(),
        want = """
            |"pom.xml": "__DO_NOT_CARE__",
            |"src": {
            |  "main": {
            |    "java": {
            |      "my_test_library": {
            |        "test": {
            |          "TestMain.java": "__DO_NOT_CARE__",
            |          "TestGlobal.java": {
            |            "content":
            |              ```
            |              package my_test_library.test;
            |              import java.util.concurrent.CompletableFuture;
            |              import java.util.Optional;
            |              import temper.core.Core;
            |              import temper.core.Generator.DoneResult;
            |              import temper.core.Generator;
            |              import temper.core.Generator.Result;
            |              import java.util.logging.Logger;
            |              import temper.core.Core.Console;
            |              import java.util.function.Function;
            |              import temper.core.Generator.ValueResult;
            |              import java.util.concurrent.ExecutionException;
            |              public final class TestGlobal {
            |                  private TestGlobal() {
            |                  }
            |                  static final Console console_3;
            |                  static final CompletableFuture<String> b__0;
            |                  static final CompletableFuture<String> p__0;
            |                  static Generator<Optional<? super Object>> fn__0() {
            |                      class Local_1 {
            |                          int caseIndex_41 = 0;
            |                          String t_13 = "";
            |                          String t_14 = "";
            |                          boolean fail_11 = false;
            |                      }
            |                      final Local_1 local$1 = new Local_1();
            |                      Function<Generator<Optional<? super Object>>, Result<Optional<? super Object>>> convertedCoroutine_47 = generator_40 -> {
            |                          while (true) {
            |                              int caseIndexLocal_42 = local$1.caseIndex_41;
            |                              local$1.caseIndex_41 = -1;
            |                              switch (caseIndexLocal_42) {
            |                                  case 0:
            |                                      {
            |                                      local$1.caseIndex_41 = 1;
            |                                      p__0.handle((ignored$1, ignored$2) -> {
            |                                              generator_40.get();
            |                                              return null;
            |                                      });
            |                                      return new ValueResult<>(Optional.empty());
            |                                  }
            |                                  case 1:
            |                                      {
            |                                      local$1.fail_11 = false;
            |                                      try {
            |                                          local$1.t_13 = p__0.get();
            |                                      } catch (InterruptedException ignored$3) {
            |                                          break;
            |                                      } catch (ExecutionException ignored$4) {
            |                                          local$1.fail_11 = true;
            |                                      }
            |                                      if (local$1.fail_11) {
            |                                          local$1.caseIndex_41 = 2;
            |                                      } else {
            |                                          local$1.caseIndex_41 = 3;
            |                                      }
            |                                      break;
            |                                  }
            |                                  case 2:
            |                                      {
            |                                      local$1.t_14 = "broken";
            |                                      local$1.caseIndex_41 = 4;
            |                                      break;
            |                                  }
            |                                  case 3:
            |                                      {
            |                                      local$1.t_14 = local$1.t_13;
            |                                      local$1.caseIndex_41 = 4;
            |                                      break;
            |                                  }
            |                                  case 4:
            |                                      {
            |                                      console_3.log(local$1.t_14);
            |                                      return DoneResult.get();
            |                                  }
            |                                  default:
            |                                      {
            |                                      return DoneResult.get();
            |                                  }
            |                              }
            |                          }
            |                      };
            |                      return Core.safeAdaptGeneratorFn(convertedCoroutine_47 :: apply);
            |                  }
            |                  static {
            |                      console_3 = Core.getConsole(Logger.getLogger("my_test_library.test"));
            |                      b__0 = new CompletableFuture<>();
            |                      p__0 = b__0;
            |                      Core.runAsync(TestGlobal :: fn__0);
            |                      b__0.complete("Hi");
            |                  }
            |              }
            |
            |              ```
            |          },
            |          "TestMain.java.map": "__DO_NOT_CARE__",
            |          "TestGlobal.java.map": "__DO_NOT_CARE__",
            |        },
            |        "MyTestLibraryGlobal.java": "__DO_NOT_CARE__",
            |        "MyTestLibraryMain.java": "__DO_NOT_CARE__",
            |      }
            |    }
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun promiseRejectionCaught() = assertGeneratedJavaRaw(
        input = """
            |let p = new PromiseBuilder<Empty>().promise;
            |async { (): GeneratorResult<Empty> extends GeneratorFn =>
            |  await p orelse panic();
            |}
        """.trimMargin(),
        want = """
            |"pom.xml": "__DO_NOT_CARE__",
            |"src": {
            |  "main": {
            |    "java": {
            |      "my_test_library": {
            |        "test": {
            |          "TestMain.java": "__DO_NOT_CARE__",
            |          "TestGlobal.java": {
            |            "content":
            |              ```
            |              package my_test_library.test;
            |              import java.util.Optional;
            |              import temper.core.Core;
            |              import temper.core.Generator.DoneResult;
            |              import java.util.concurrent.CompletableFuture;
            |              import temper.core.Generator;
            |              import temper.core.Generator.Result;
            |              import java.util.function.Function;
            |              import temper.core.Generator.ValueResult;
            |              import java.util.concurrent.ExecutionException;
            |              public final class TestGlobal {
            |                  private TestGlobal() {
            |                  }
            |                  static final CompletableFuture<Optional<? super Object>> p__0;
            |                  static Generator<Optional<? super Object>> fn__0() {
            |                      class Local_1 {
            |                          int caseIndex_26 = 0;
            |                          boolean fail_7 = false;
            |                      }
            |                      final Local_1 local$1 = new Local_1();
            |                      Function<Generator<Optional<? super Object>>, Result<Optional<? super Object>>> convertedCoroutine_33 = generator_25 -> {
            |                          while (true) {
            |                              int caseIndexLocal_27 = local$1.caseIndex_26;
            |                              local$1.caseIndex_26 = -1;
            |                              switch (caseIndexLocal_27) {
            |                                  case 0:
            |                                      {
            |                                      local$1.caseIndex_26 = 1;
            |                                      p__0.handle((ignored$1, ignored$2) -> {
            |                                              generator_25.get();
            |                                              return null;
            |                                      });
            |                                      return new ValueResult<>(Optional.empty());
            |                                  }
            |                                  case 1:
            |                                      {
            |                                      local$1.fail_7 = false;
            |                                      try {
            |                                          p__0.get();
            |                                      } catch (InterruptedException ignored$3) {
            |                                          break;
            |                                      } catch (ExecutionException ignored$4) {
            |                                          local$1.fail_7 = true;
            |                                      }
            |                                      if (local$1.fail_7) {
            |                                          local$1.caseIndex_26 = 2;
            |                                      } else {
            |                                          local$1.caseIndex_26 = 3;
            |                                      }
            |                                      break;
            |                                  }
            |                                  case 2:
            |                                      {
            |                                      Core.throwBubble();
            |                                      local$1.caseIndex_26 = 3;
            |                                      break;
            |                                  }
            |                                  case 3:
            |                                      {
            |                                      return DoneResult.get();
            |                                  }
            |                                  default:
            |                                      {
            |                                      return DoneResult.get();
            |                                  }
            |                              }
            |                          }
            |                      };
            |                      return Core.safeAdaptGeneratorFn(convertedCoroutine_33 :: apply);
            |                  }
            |                  static {
            |                      p__0 = new CompletableFuture<>();
            |                      Core.runAsync(TestGlobal :: fn__0);
            |                  }
            |              }
            |
            |              ```
            |          },
            |          "TestMain.java.map": "__DO_NOT_CARE__",
            |          "TestGlobal.java.map": "__DO_NOT_CARE__",
            |        },
            |        "MyTestLibraryGlobal.java": "__DO_NOT_CARE__",
            |        "MyTestLibraryMain.java": "__DO_NOT_CARE__",
            |      }
            |    }
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun stringBuilderUse() = assertGeneratedJava(
        """
            |let sb = new StringBuilder();
            |sb.append("Hello, ");
            |sb.append("World");
            |sb.append("!");
            |console.log(sb.toString());
        """.trimMargin(),
        """
            |static Console t_23;
            |static final StringBuilder sb__0;
            |static {
            |    t_23 = Core.getConsole(Logger.getLogger("my_test_library.test"));
            |    sb__0 = new StringBuilder();
            |    sb__0.append("Hello, ");
            |    sb__0.append("World");
            |    sb__0.append("!");
            |    t_23.log(sb__0.toString());
            |}
        """.javaMethod(
            "import temper.core.Core;",
            "import java.util.logging.Logger;",
            "import temper.core.Core.Console;",
        ),
    )

    @Test
    fun genericTypeSubTypedWithJavaPrimitiveTypeActual() = assertGeneratedJavaRaw(
        $$"""
            |export interface I<T> {
            |  public f(x: T): T;
            |}
            |export class NotX extends I<Boolean> {
            |  public f(x: Boolean): Boolean { !x }
            |}
            |export class TwiceX extends I<String> {
            |  public f(x: String): String { "${x}${x}" }
            |}
        """.trimMargin(),
        want = """
            |"pom.xml": "__DO_NOT_CARE__",
            |"src": {
            |  "main": {
            |    "java": {
            |      "my_test_library": {
            |        "test": {
            |          "TestMain.java": "__DO_NOT_CARE__",
            |          "TestGlobal.java": "__DO_NOT_CARE__",
            |          "I.java": {
            |            "content":
            |              ```
            |              package my_test_library.test;
            |              public interface I<T__0> {
            |                  T__0 f(T__0 x__0);
            |              }
            |
            |              ```
            |          },
            |          "NotX.java": {
            |            "content":
            |              ```
            |              package my_test_library.test;
            |              public final class NotX implements I<Boolean> {
            |                  private boolean f__0(boolean x__1) {
            |                      return !x__1;
            |                  }
            |                  public Boolean f(Boolean x__1) {
            |                      return f__0(x__1.booleanValue());
            |                  }
            |                  public NotX() {
            |                  }
            |              }
            |
            |              ```
            |          },
            |          "TwiceX.java": {
            |            "content":
            |              ```
            |              package my_test_library.test;
            |              public final class TwiceX implements I<String> {
            |                  public String f(String x__2) {
            |                      return x__2 + x__2;
            |                  }
            |                  public TwiceX() {
            |                  }
            |              }
            |
            |              ```
            |          },
            |          "I.java.map": "__DO_NOT_CARE__",
            |          "NotX.java.map": "__DO_NOT_CARE__",
            |          "TwiceX.java.map": "__DO_NOT_CARE__",
            |          "TestGlobal.java.map": "__DO_NOT_CARE__",
            |          "TestMain.java.map": "__DO_NOT_CARE__"
            |        },
            |        "MyTestLibraryGlobal.java": "__DO_NOT_CARE__",
            |        "MyTestLibraryMain.java": "__DO_NOT_CARE__"
            |      }
            |    }
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun boundedTypeVariable() = assertGeneratedJavaRaw(
        """
            |export interface I {
            |  public x: String;
            |}
            |export class C(public x: String) extends I {}
            |
            |export let least<I_T extends I>(i: I_T, j: I_T): I_T {
            |  if (i.x < j.x) { i } else { j }
            |}
            |
            |console.log(least<C>({ x: "foo" }, { x: "bar" }).x);
        """.trimMargin(),
        want = """
            |"pom.xml": "__DO_NOT_CARE__",
            |"src": {
            |  "main": {
            |    "java": {
            |      "my_test_library": {
            |        "test": {
            |          "I.java": {
            |            "content":
            |              ```
            |              package my_test_library.test;
            |              public interface I {
            |                  String getX();
            |              }
            |
            |              ```
            |          },
            |          "C.java": {
            |            "content":
            |              ```
            |              package my_test_library.test;
            |              public final class C implements I {
            |                  public final String x;
            |                  public C(String x__0) {
            |                      this.x = x__0;
            |                  }
            |                  public String getX() {
            |                      return this.x;
            |                  }
            |              }
            |
            |              ```
            |          },
            |          "TestMain.java": {
            |            "content":
            |              ```
            |              package my_test_library.test;
            |              import temper.core.Core;
            |              public final class TestMain {
            |                  private TestMain() {
            |                  }
            |                  public static void main(String[] args) throws ClassNotFoundException {
            |                      Core.initSimpleLogging();
            |                      Class.forName("my_test_library.test.TestGlobal");
            |                      Core.waitUntilTasksComplete();
            |                  }
            |              }
            |
            |              ```
            |          },
            |          "TestGlobal.java": {
            |            "content":
            |              ```
            |              package my_test_library.test;
            |              import temper.core.Core;
            |              import java.util.logging.Logger;
            |              import temper.core.Core.Console;
            |              public final class TestGlobal {
            |                  private TestGlobal() {
            |                  }
            |                  static Console t_46;
            |                  public static<I_T__0 extends my_test_library.test.I> I_T__0 least(I_T__0 i__0, I_T__0 j__0) {
            |                      I_T__0 return__0;
            |                      if (i__0.getX().compareTo(j__0.getX()) < 0) {
            |                          return__0 = i__0;
            |                      } else {
            |                          return__0 = j__0;
            |                      }
            |                      return return__0;
            |                  }
            |                  static {
            |                      t_46 = Core.getConsole(Logger.getLogger("my_test_library.test"));
            |                      t_46.log(TestGlobal.least(new C("foo"), new C("bar")).getX());
            |                  }
            |              }
            |
            |              ```
            |          },
            |          "I.java.map": "__DO_NOT_CARE__",
            |          "C.java.map": "__DO_NOT_CARE__",
            |          "TestGlobal.java.map": "__DO_NOT_CARE__",
            |          "TestMain.java.map": "__DO_NOT_CARE__"
            |        },
            |        "MyTestLibraryGlobal.java": "__DO_NOT_CARE__",
            |        "MyTestLibraryMain.java": "__DO_NOT_CARE__"
            |      }
            |    }
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun staticsInInterfaces() = assertGeneratedJavaRaw(
        input = """
            |export interface I {
            |  public static f(): Int { 42 }
            |}
        """.trimMargin(),
        want = """
            |"pom.xml": "__DO_NOT_CARE__",
            |"src": {
            |  "main": {
            |    "java": {
            |      "my_test_library": {
            |        "test": {
            |          "TestMain.java": "__DO_NOT_CARE__",
            |          "TestGlobal.java": "__DO_NOT_CARE__",
            |          "I.java": {
            |            "content":
            |              ```
            |              package my_test_library.test;
            |              public interface I {
            |                  static int f() {
            |                      return 42;
            |                  }
            |              }
            |
            |              ```
            |          },
            |          "I.java.map": "__DO_NOT_CARE__",
            |          "TestGlobal.java.map": "__DO_NOT_CARE__",
            |          "TestMain.java.map": "__DO_NOT_CARE__"
            |        },
            |        "MyTestLibraryGlobal.java": "__DO_NOT_CARE__",
            |        "MyTestLibraryMain.java": "__DO_NOT_CARE__"
            |      }
            |    }
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun labeledLoop() = assertGeneratedJava(
        input = """
            |export let doThings(n: Int): Int {
            |  var i = 0;
            |  top: for (; i < n; i += 1) {
            |    if (n % i == 0 orelse false) {
            |      break top;
            |    }
            |  }
            |  i
            |}
        """.trimMargin(),
        want = """
            |public static int doThings(int n__0) {
            |    int t_7;
            |    boolean t_8;
            |    int i__0 = 0;
            |    top__0: while (i__0 < n__0) {
            |        try {
            |            t_7 = Core.modIntInt(n__0, i__0);
            |            t_8 = t_7 == 0;
            |        } catch (RuntimeException ignored$1) {
            |            t_8 = false;
            |        }
            |        if (t_8) {
            |            break top__0;
            |        }
            |        i__0 = i__0 + 1;
            |    }
            |    return i__0;
            |}
        """.javaMethod("import temper.core.Core;"),
    )

    @Test
    fun wrappedInOrElse() = assertGeneratedJava(
        input = """
            |export let doThings(n: Int): Int {
            |  do {
            |    if (n < 0) {
            |      return -1;
            |    }
            |    if (n == 0) {
            |      bubble();
            |    }
            |    n
            |  } orelse -2
            |}
        """.trimMargin(),
        want = """
            |public static int doThings(int n__0) {
            |    int return__0;
            |    fn__0: {
            |        int return_7;
            |        escapeLabel_9: try {
            |            if (n__0 < 0) {
            |                return_7 = -1;
            |                break escapeLabel_9;
            |            }
            |            if (n__0 == 0) {
            |                throw Core.bubble();
            |            }
            |            return_7 = n__0;
            |        } catch (RuntimeException ignored$1) {
            |            return_7 = -2;
            |        }
            |        return__0 = return_7;
            |    }
            |    return return__0;
            |}
        """.javaMethod("import temper.core.Core;"),
    )

    @Test
    fun wrappedInOrElseNested() = assertGeneratedJava(
        input = """
            |export let doThings(n: Int): Int {
            |  // Logic here is nonsense, just for triggering different escape hatch conditions.
            |  var result = 0;
            |  while (result == 0) {
            |    outer: do {
            |      let more: Int;
            |      do {
            |        if (n < 3) {
            |          bubble();
            |        }
            |        for (var i = 2; i < n; i += 1) {
            |          if (n % i == 0) {
            |            break;
            |          }
            |          result += i;
            |        }
            |        if (n > 25) {
            |          // Continue should be a different escape case than break.
            |          continue;
            |        }
            |        if (n > 20) {
            |          break outer;
            |        }
            |        if (n > 15) {
            |          // This raw break needs to go to the loop.
            |          break;
            |        }
            |        if (n > 10) {
            |          // Two breaks to the same loop should be the same escape case.
            |          break outer;
            |        }
            |        more = 1;
            |      } orelse do {
            |        more = 2;
            |      }
            |      result += more;
            |    }
            |  }
            |  result
            |}
        """.trimMargin(),
        want = """
            |public static int doThings(int n__0) {
            |    int t_9;
            |    int result__0 = 0;
            |    label_14: while (result__0 == 0) {
            |        outer__0: {
            |            int more__0;
            |            int escape_12 = 0;
            |            int more_11;
            |            escapeLabel_13: try {
            |                if (n__0 < 3) {
            |                    throw Core.bubble();
            |                }
            |                int i__0 = 2;
            |                while (i__0 < n__0) {
            |                    t_9 = Core.modIntInt(n__0, i__0);
            |                    if (t_9 == 0) {
            |                        break;
            |                    }
            |                    result__0 = result__0 + i__0;
            |                    i__0 = i__0 + 1;
            |                }
            |                if (n__0 > 25) {
            |                    escape_12 = 1;
            |                    break escapeLabel_13;
            |                }
            |                if (n__0 > 20) {
            |                    escape_12 = 2;
            |                    break escapeLabel_13;
            |                }
            |                if (n__0 > 15) {
            |                    escape_12 = 3;
            |                    break escapeLabel_13;
            |                }
            |                if (n__0 > 10) {
            |                    escape_12 = 2;
            |                    break escapeLabel_13;
            |                }
            |                more_11 = 1;
            |            } catch (RuntimeException ignored$1) {
            |                more_11 = 2;
            |            }
            |            more__0 = more_11;
            |            switch (escape_12) {
            |                case 1:
            |                    {
            |                    continue;
            |                }
            |                case 2:
            |                    {
            |                    break outer__0;
            |                }
            |                case 3:
            |                    {
            |                    break label_14;
            |                }
            |                default:
            |                    {
            |                    break;
            |                }
            |            }
            |            result__0 = result__0 + more__0;
            |        }
            |    }
            |    return result__0;
            |}
        """.javaMethod("import temper.core.Core;"),
    )

    /** Like [wrappedInOrElse] except doesn't cause breaks out of try/catch. Nice for comparison. */
    @Test
    fun wrappedInOrElseEasier() = assertGeneratedJava(
        input = """
            |export let doThingsEasier(n: Int): Int {
            |  do {
            |    if (n == 0) {
            |      bubble();
            |    }
            |    n
            |  } orelse -2
            |}
        """.trimMargin(),
        want = """
            |public static int doThingsEasier(int n__0) {
            |    int return_7;
            |    try {
            |        if (n__0 == 0) {
            |            throw Core.bubble();
            |        }
            |        return_7 = n__0;
            |    } catch (RuntimeException ignored$1) {
            |        return_7 = -2;
            |    }
            |    return return_7;
            |}
        """.javaMethod("import temper.core.Core;"),
    )

    @Test
    fun listOfInts() = assertGeneratedJavaRaw(
        // Pooling definitions across modules in the same library can lead to some odd rendering in the sharing module.
        // Especially when a local temporary name is auto-created for an import of a pooled support code.
        //
        //     daysInMonth__30 = TemporalGlobal.temper$2ecore$2eCore$2elistOf_2850(
        //       0, 31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31);
        //
        // That should be
        //
        //    daysInMonth__30 = Core.listOf_2850(0, 31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31);
        inputs = inputFileMapFromJson(
            """
                |{
                |  foo: {
                |    ints.temper: "let ints = [1, 2, 3, 4];"
                |  },
                |  bar: {
                |    ints.temper: "let moreInts = [5, 6, 7, 8];"
                |  },
                |}
            """.trimMargin(),
        ),
        want = """
            |src: { main: { java: { my_test_library: {
            |  foo: {
            |    FooGlobal.java: { content: ```
            |      package my_test_library.foo;
            |      import java.util.List;
            |      import temper.core.Core;
            |      public final class FooGlobal {
            |          private FooGlobal() {
            |          }
            |          static final List<Integer> ints__0;
            |          static {
            |              ints__0 = Core.listOf(1, 2, 3, 4);
            |          }
            |      }
            |
            |      ```
            |    },
            |    FooGlobal.java.map: "__DO_NOT_CARE__",
            |    FooMain.java: "__DO_NOT_CARE__",
            |    FooMain.java.map: "__DO_NOT_CARE__",
            |  },
            |  bar: {
            |    BarGlobal.java: { content: ```
            |      package my_test_library.bar;
            |      import java.util.List;
            |      import temper.core.Core;
            |      public final class BarGlobal {
            |          private BarGlobal() {
            |          }
            |          static final List<Integer> moreInts__0;
            |          static {
            |              moreInts__0 = Core.listOf(5, 6, 7, 8);
            |          }
            |      }
            |
            |      ```
            |    },
            |    BarGlobal.java.map: "__DO_NOT_CARE__",
            |    BarMain.java: "__DO_NOT_CARE__",
            |    BarMain.java.map: "__DO_NOT_CARE__",
            |  },
            |  "MyTestLibraryGlobal.java": "__DO_NOT_CARE__",
            |  "MyTestLibraryMain.java": "__DO_NOT_CARE__"
            |} } } },
            |pom.xml: "__DO_NOT_CARE__"
        """.trimMargin(),
        // Java17 uses java.util.List.of instead of Core.listOf
        langs = listOf(JavaLang.Java8),
    )

    @Test
    fun multipleModuleOutputStructure() = assertGeneratedJavaRaw(
        inputs = inputFileMapFromJson(
            """
                |{
                |  config.temper.md: ```
                |    # My test library
                |
                |        export let javaPackage = "com.example";
                |    ```,
                |  top.temper: "export class InPackageComExample {}",
                |  foo: {
                |    foo.temper: "export class Foo {}",
                |  },
                |  bar: {
                |    ints.temper: "export let pi = 3;"
                |  },
                |}
            """.trimMargin(),
        ),
        want = """
            |src: { main: { java: { com: { example: {
            |  ExampleGlobal.java: "__DO_NOT_CARE__",
            |  ExampleGlobal.java.map: "__DO_NOT_CARE__",
            |  ExampleMain.java: "__DO_NOT_CARE__",
            |  ExampleMain.java.map: "__DO_NOT_CARE__",
            |  InPackageComExample.java: {
            |    content: ```
            |      package com.example;
            |      public final class InPackageComExample {
            |          public InPackageComExample() {
            |          }
            |      }
            |
            |      ```
            |  },
            |  InPackageComExample.java.map: "__DO_NOT_CARE__",
            |  bar: {
            |    BarGlobal.java: { content: ```
            |      package com.example.bar;
            |      public final class BarGlobal {
            |          private BarGlobal() {
            |          }
            |          public static final int pi;
            |          static {
            |              pi = 3;
            |          }
            |      }
            |
            |      ```
            |    },
            |    BarGlobal.java.map: "__DO_NOT_CARE__",
            |    BarMain.java: "__DO_NOT_CARE__",
            |    BarMain.java.map: "__DO_NOT_CARE__",
            |  },
            |  foo: {
            |    Foo.java: {
            |      content: ```
            |        package com.example.foo;
            |        public final class Foo {
            |            public Foo() {
            |            }
            |        }
            |
            |        ```,
            |    },
            |    Foo.java.map: "__DO_NOT_CARE__",
            |    FooGlobal.java: "__DO_NOT_CARE__",
            |    FooGlobal.java.map: "__DO_NOT_CARE__",
            |    FooMain.java: "__DO_NOT_CARE__",
            |    FooMain.java.map: "__DO_NOT_CARE__",
            |  },
            |} } } } },
            |pom.xml: "__DO_NOT_CARE__"
        """.trimMargin().stripDoubleHashCommentLinesToPutCommentsInlineBelow(),
    )

    @Test
    fun stringIndexOptionComparison() {
        assertGeneratedJava(
            """
                |export let f1(a: StringIndexOption, b: StringIndexOption): Boolean {
                |   a < b
                |}
            """.trimMargin(),
            """
            |public static boolean f1(int a__2, int b__3) {
            |    return a__2 < b__3;
            |}
            """.javaMethod(),
        )
    }

    @Test
    fun instanceOfUsesReferenceType() = assertGeneratedJava(
        """
            |export let orZero(x: Int?): Int {
            |  when (x) {
            |    is Int -> x;
            |    else -> 0;
            |  }
            |}
        """.trimMargin(),
        """
            |public static int orZero(@Nullable Integer x__0) {
            |    int return__0;
            |    boolean t_10;
            |    if (x__0 != null) {
            |        t_10 = x__0 instanceof Integer;
            |    } else {
            |        t_10 = false;
            |    }
            |    if (t_10) {
            |        if (x__0 == null) {
            |            throw Core.bubble();
            |        } else {
            |            return__0 = x__0;
            |        }
            |    } else {
            |        return__0 = 0;
            |    }
            |    return return__0;
            |}
        """.trimIndent().javaMethod(
            "import temper.core.Core;",
            "import temper.core.Nullable;",
        ),
    )

    @Test
    fun longStrings() = assertGeneratedJava(
        """
            |console.log("lo${"o".repeat(70_000)}ng string");
        """.trimMargin(),
        """
            |static {
            |    Core.getConsole(Logger.getLogger("my_test_library.test")).log(new StringBuilder(70011).append("l${"o".repeat(65532)}").append("${"o".repeat(4469)}ng string").toString());
            |}
        """.javaMethod(
            "import temper.core.Core;",
            "import java.util.logging.Logger;",
        ),
    )

    @Test
    fun samTypesWithPrimitives() = assertGeneratedJava(
        $$"""
            |export let mungeInts(ints: List<Int>): Void {
            |  let builder = ints.toListBuilder();
            |  builder.sort { a, b => a - b };
            |  console.log(ints.sorted { a, b => a - b }.join(", ") { a => "${a}" });
            |  console.log(builder.join(", ") { a => "${a}" });
            |}
            |mungeInts([]);
        """.trimMargin(),
        want = """
            |static final Console console_10;
            |public static void mungeInts(List<Integer> ints__0) {
            |    List<Integer> builder__0 = new ArrayList<>(ints__0);
            |    IntBinaryOperator fn__0 = (a__0, b__0) -> a__0 - b__0;
            |    Core.listSortInt(builder__0, fn__0);
            |    IntBinaryOperator fn__1 = (a__1, b__1) -> a__1 - b__1;
            |    List<Integer> t_54 = Core.listSortedInt(ints__0, fn__1);
            |    IntFunction<String> fn__2 = a__2 -> Integer.toString(a__2);
            |    String t_56 = Core.listJoinInt(t_54, ", ", fn__2);
            |    console_10.log(t_56);
            |    IntFunction<String> fn__3 = a__3 -> Integer.toString(a__3);
            |    String t_59 = Core.listJoinInt(builder__0, ", ", fn__3);
            |    console_10.log(t_59);
            |}
            |static {
            |    console_10 = Core.getConsole(Logger.getLogger("my_test_library.test"));
            |    TestGlobal.mungeInts(List.of());
            |}
            |
        """.trimMargin().javaMethod(
            """
                |import temper.core.Core;
                |import java.util.List;
                |import java.util.function.IntFunction;
                |import java.util.function.IntBinaryOperator;
                |import java.util.logging.Logger;
                |import temper.core.Core.Console;
                |import java.util.ArrayList;
            """.trimMargin(),
        ),
        langs = listOf(JavaLang.Java17), // listOf
    )
}
