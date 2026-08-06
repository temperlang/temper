package lang.temper.be.cpp

import lang.temper.be.Backend
import lang.temper.be.assertGeneratedCode
import lang.temper.be.generateCode
import lang.temper.common.ListBackedLogSink
import lang.temper.fs.MemoryFileSystem
import lang.temper.lexer.Genre
import lang.temper.log.filePath
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.text.trimMargin

@SuppressWarnings("MaxLineLength")
class CppBackendTest {
    @Test
    fun classOrdering() {
        assertGenerated(
            temper = $$"""
                |greet("world");
                |greet("world ${x}");
                |let x = 1 + 2;
                |export let greet(name: String): Void {
                |  console.log("Hi:");
                |  console.log(name);
                |}
            """,
            cpp = """
                |#include <my-test-library/something.hpp>
                |namespace my_test_library {
                |  static std::shared_ptr<temper::core::Console::Type> console_0;
                |  static int32_t x;
                |  void greet(std::string name) {
                |    temper::core::Console::log(console_0, "Hi:");
                |    temper::core::Console::log(console_0, name);
                |    return;
                |  }
                |  void global_init_something() {
                |    static bool initialized = false;
                |    if(initialized) {
                |      return;
                |    }
                |    initialized = true;
                |    console_0 = temper::core::Console::get_console();
                |    greet("world");
                |    x = 3;
                |    greet(temper::core::cat("world ", temper::core::Int::toString(3)));
                |  }
                |}
                |
            """,
            hpp = """
                |#pragma once
                |#include <temper-core/core.hpp>
                |namespace my_test_library {
                |  void greet(std::string);
                |  void global_init_something();
                |}
                |
            """,
        )
    }

    @Test
    fun dates() {
        assertGeneratedContains(
            temper = """
                |let { Date } = import("std/temporal");
                |export let day(date: Date): Int {
                |  date.day
                |}
            """,
            cppContains = listOf(
                """
                    |  int32_t day(std::shared_ptr<temper_std::Date> const & date) {
                    |    return temper::core::Date::getDay(date);
                    |  }
                """.trimMargin(),
            ),
        )
    }

    @Test
    fun simpleFunction() {
        assertGenerated(
            temper = """
                |export let add(a: Int, b: Int): Int {
                |  return a + b;
                |}
            """,
            cpp = """
                |#include <my-test-library/something.hpp>
                |namespace my_test_library {
                |  int32_t add(int32_t a, int32_t b) {
                |    return temper::core::Int::add(a, b);
                |  }
                |  void global_init_something() {
                |    static bool initialized = false;
                |    if(initialized) {
                |      return;
                |    }
                |    initialized = true;
                |  }
                |}
                |
            """,
            hpp = """
                |#pragma once
                |#include <temper-core/core.hpp>
                |namespace my_test_library {
                |  int32_t add(int32_t, int32_t);
                |  void global_init_something();
                |}
                |
            """,
        )
    }

    @Test
    fun exportedVariable() {
        assertGenerated(
            temper = """
                |export let x = 42;
            """,
            cpp = """
                |#include <my-test-library/something.hpp>
                |namespace my_test_library {
                |  int32_t x;
                |  void global_init_something() {
                |    static bool initialized = false;
                |    if(initialized) {
                |      return;
                |    }
                |    initialized = true;
                |    x = 42;
                |  }
                |}
                |
            """,
            hpp = """
                |#pragma once
                |#include <temper-core/core.hpp>
                |namespace my_test_library {
                |  extern int32_t x;
                |  void global_init_something();
                |}
                |
            """,
        )
    }

    @Test
    fun booleanFunction() {
        assertGenerated(
            temper = """
                |export let isPositive(x: Int): Boolean {
                |  return x > 0;
                |}
            """,
            cpp = """
                |#include <my-test-library/something.hpp>
                |namespace my_test_library {
                |  bool isPositive(int32_t x) {
                |    return x> 0;
                |  }
                |  void global_init_something() {
                |    static bool initialized = false;
                |    if(initialized) {
                |      return;
                |    }
                |    initialized = true;
                |  }
                |}
                |
            """,
            hpp = """
                |#pragma once
                |#include <temper-core/core.hpp>
                |namespace my_test_library {
                |  bool isPositive(int32_t);
                |  void global_init_something();
                |}
                |
            """,
        )
    }

    @Test
    fun multipleParams() {
        assertGenerated(
            temper = """
                |export let multiply(a: Int, b: Int): Int {
                |  return a * b;
                |}
            """,
            cpp = """
                |#include <my-test-library/something.hpp>
                |namespace my_test_library {
                |  int32_t multiply(int32_t a, int32_t b) {
                |    return temper::core::Int::mul(a, b);
                |  }
                |  void global_init_something() {
                |    static bool initialized = false;
                |    if(initialized) {
                |      return;
                |    }
                |    initialized = true;
                |  }
                |}
                |
            """,
            hpp = """
                |#pragma once
                |#include <temper-core/core.hpp>
                |namespace my_test_library {
                |  int32_t multiply(int32_t, int32_t);
                |  void global_init_something();
                |}
                |
            """,
        )
    }

    @Test
    fun interfaceInheritance() {
        assertGeneratedContains(
            temper = """
                |interface Animal {
                |  public get name(): String;
                |}
                |class Dog extends Animal {
                |  public get name(): String { "Rex" }
                |}
                |export let makeDog(): Animal {
                |  return new Dog();
                |}
            """,
            cppContains = listOf(
                "virtual public temper::core::AnyValueBase",
                "virtual public Animal",
                "virtual ~Animal",
                "std::shared_ptr<Dog>",
            ),
        )
    }

    @Test
    fun nullableValueType() {
        assertGeneratedContains(
            temper = """
                |export let maybeAdd(a: Int, b: Int?): Int {
                |  if (b == null) { return a; }
                |  return a + b;
                |}
            """,
            cppContains = listOf(
                "NullableParam<int32_t>",
            ),
        )
    }

    @Test
    fun instanceOfValueTypeOnAnyValue() {
        // `x is Int` where x is a boxed AnyValue must check the boxed payload's type,
        // not merely that the box is non-null (which would accept a box holding anything).
        assertGeneratedContains(
            temper = """
                |export let isInt(x: AnyValue): Boolean {
                |  return x is Int;
                |}
            """,
            cppContains = listOf(
                "temper::core::is_box<temper::core::Int32>",
            ),
        )
    }

    @Test
    fun ifReturn() {
        assertGeneratedContains(
            temper = """
                |export let abs(x: Int): Int {
                |  if (x < 0) { return -x; }
                |  return x;
                |}
            """,
            cppContains = listOf(
                "int32_t abs(int32_t x)",
                "if(x<0)",
                // Unary negation lowers to the overflow-defined core helper, not native `-x`.
                "temper::core::Int::neg(x)",
            ),
        )
    }

    @Test
    fun whileLoop() {
        assertGeneratedContains(
            temper = """
                |export let countdown(n: Int): Int {
                |  var i = n;
                |  while (i > 0) {
                |    i = i - 1;
                |  }
                |  return i;
                |}
            """,
            cppContains = listOf(
                "while(",
                "i> 0",
                // Subtraction lowers to the overflow-defined core helper, not native `i - 1`.
                "i = temper::core::Int::sub(i, 1)",
            ),
        )
    }

    @Test
    fun forOfLoop() {
        assertGeneratedContains(
            temper = """
                |export let sum(items: List<Int>): Int {
                |  var total = 0;
                |  for (item of items) {
                |    total = total + item;
                |  }
                |  return total;
                |}
            """,
            cppContains = listOf(
                "int32_t sum(",
                "int32_t total = 0",
            ),
        )
    }

    @Test
    fun stringConcat() {
        assertGeneratedContains(
            temper = $$"""
                |export let greet(name: String): String {
                |  return "Hello, ${name}!";
                |}
            """,
            cppContains = listOf(
                "std::string greet(std::string",
                "temper::core::cat(",
            ),
        )
    }

    @Test
    fun classWithGetter() {
        assertGeneratedContains(
            temper = """
                |class Point {
                |  public get x(): Int;
                |  public get y(): Int;
                |}
                |export let makePoint(): Point {
                |  return new Point(1, 2);
                |}
            """,
            cppContains = listOf(
                // A plain (rootless) struct carries its own CRTP enable_shared_from_this so
                // borrow_this can hand out an owning shared_ptr for `this`.
                "struct Point : public std::enable_shared_from_this<Point>",
                "int32_t get_x()",
                "int32_t get_y()",
                "std::shared_ptr<Point>",
            ),
        )
    }

    @Test
    fun classWithMethod() {
        assertGeneratedContains(
            temper = """
                |class Counter {
                |  public get value(): Int;
                |  public increment(): Counter {
                |    new Counter(this.value + 1)
                |  }
                |}
                |export let makeCounter(): Counter {
                |  return new Counter(0);
                |}
            """,
            cppContains = listOf(
                "struct Counter",
                "int32_t get_value()",
                "increment",
                "std::shared_ptr<Counter>",
            ),
        )
    }

    @Test
    fun staticProperty() {
        assertGeneratedContains(
            temper = """
                |class Config {
                |  static let defaultValue: Int = 42;
                |}
                |export let getDefault(): Int {
                |  return Config.defaultValue;
                |}
            """,
            cppContains = listOf(
                "42",
                "int32_t getDefault(",
            ),
        )
    }

    @Test
    fun localFunction() {
        assertGeneratedContains(
            temper = """
                |let helper(x: Int): Int { x * 2 }
                |export let run(): Int {
                |  return helper(21);
                |}
            """,
            cppContains = listOf(
                "int32_t helper(",
                "int32_t run(",
            ),
        )
    }

    // Exercises passing a local as a call argument (not closure capture, which the functional
    // suite covers end-to-end).
    @Test
    fun functionCallWithLocalArgument() {
        assertGeneratedContains(
            temper = """
                |let add(a: Int, b: Int): Int { a + b }
                |export let result(): Int {
                |  let x = 10;
                |  return add(x, 5);
                |}
            """,
            cppContains = listOf(
                "int32_t add(",
                "int32_t result(",
                // The local must be initialized and then passed to add(...).
                "int32_t x = 10",
            ),
        )
    }

    @Test
    fun defaultArgument() {
        assertGeneratedContains(
            temper = $$"""
                |export let greet(name: String = "World"): String {
                |  return "Hello, ${name}!";
                |}
            """,
            cppContains = listOf(
                "std::string greet(",
                "World",
            ),
        )
    }

    @Test
    fun listOperations() {
        assertGeneratedContains(
            temper = """
                |export let first(items: List<Int>): Int {
                |  return items[0];
                |}
            """,
            cppContains = listOf(
                "int32_t first(",
                "temper::core::List",
            ),
        )
    }

    @Test
    fun optionalParameter() {
        assertGeneratedContains(
            temper = """
                |export let addOrZero(a: Int, b: Int?): Int {
                |  if (b == null) { return a; }
                |  return a + b;
                |}
            """,
            cppContains = listOf(
                "NullableParam<int32_t>",
                "int32_t addOrZero(",
            ),
        )
    }

    @Test
    fun multipleExports() {
        assertGeneratedContains(
            temper = """
                |export let add(a: Int, b: Int): Int { a + b }
                |export let sub(a: Int, b: Int): Int { a - b }
            """,
            cppContains = listOf(
                "int32_t add(int32_t",
                "int32_t sub(int32_t",
            ),
        )
    }

    @Test
    fun callOverrideFromSubtype() {
        assertGeneratedContains(
            temper = """
                |interface Shape {
                |  area(): Float64;
                |}
                |class Circle extends Shape {
                |  public get radius(): Float64;
                |  area(): Float64 {
                |    3.14159 * this.radius * this.radius
                |  }
                |}
                |export let circleArea(r: Float64): Float64 {
                |  let c = new Circle(r);
                |  return c.area();
                |}
            """,
            cppContains = listOf(
                "struct Shape",
                "struct Circle",
                "virtual public",
                "get_radius()",
                "area",
            ),
        )
    }

    @Test
    fun callThisMethods() {
        assertGeneratedContains(
            temper = """
                |export interface Apple {
                |  thing(i: Int): Int;
                |  twiceThing(i: Int): Int { 2 * thing(i) }
                |}
            """,
            cppContains = listOf(
                """
                    |  int32_t Apple::twiceThing(int32_t i_8)const {
                    |    auto this_1 = temper::core::borrow_this(this);
                    |    return temper::core::Int::mul(2, this_1->thing(i_8));
                    |  }
                """.trimMargin(),
            ),
        )
    }

    @Test
    fun importsBetweenModules() {
        assertGeneratedContains(
            temper = """
                |let { log } = import("console");
                |export let hello(): Void {
                |  console.log("hello");
                |}
            """,
            cppContains = listOf(
                "temper::core::Console::log",
            ),
        )
    }

    @Test
    fun floatOps() {
        assertGeneratedContains(
            temper = """
                |export let avg(a: Float64, b: Float64): Float64 {
                |  return (a + b) / 2.0;
                |}
            """,
            cppContains = listOf(
                "double avg(double a, double b)",
                "a + b",
                "2.0",
            ),
        )
    }

    @Test
    fun divisionWithCheck() {
        assertGeneratedContains(
            temper = """
                |export let safeDivide(a: Int, b: Int): Int {
                |  if (b == 0) { return 0; }
                |  return a / b;
                |}
            """,
            cppContains = listOf(
                "int32_t safeDivide(int32_t a, int32_t b)",
                "b == 0",
                "div_wrap",
            ),
        )
    }

    @Test
    fun privateMethod() {
        assertGeneratedContains(
            temper = """
                |class Foo {
                |  helper(): Int { 42 }
                |  public result(): Int { this.helper() }
                |}
                |export let run(): Int {
                |  let f = new Foo();
                |  return f.result();
                |}
            """,
            cppContains = listOf(
                "struct Foo",
                "helper",
                "result",
            ),
        )
    }

    @Test
    fun setterProperty() {
        assertGeneratedContains(
            temper = """
                |class MutableBox {
                |  public get value(): Int;
                |  public set value(v: Int);
                |}
                |export let setBox(b: MutableBox, v: Int): Void {
                |  b.value = v;
                |}
            """,
            cppContains = listOf(
                "struct MutableBox",
                "int32_t get_value()",
                "void set_value(int32_t)",
                "setBox",
            ),
        )
    }

    @Test
    fun connected() {
        val connecteds = listOf(
            filePath("something", "_connected.cpp") to """
                    |#include "_connected.hpp"
                    |#include "support.hpp"
                    |
                    |namespace work {
                    |namespace _connected {
                    |
                    |std::int32_t sum(std::int32_t i, std::int32_t j, std::int32_t bonus) {
                    |    return i + j + bonus;
                    |}
                    |
                    |} // namespace _connected
                    |} // namespace work
                """.trimMargin(),
            filePath("something", "_connected.hpp") to """
                    |#pragma once
                    |
                    |#include <cstdint>
                    |#include <work/init.hpp>
                    |
                    |namespace work {
                    |namespace _connected {
                    |
                    |using namespace work;
                    |
                    |std::int32_t sum(std::int32_t i, std::int32_t j, std::int32_t bonus);
                    |
                    |} // namespace _connected
                    |} // namespace work
                """.trimMargin(),
            filePath("other", "thing", "whatever.cpp") to """
                    |// Content doesn't really matter here.
                """.trimMargin(),
        )
        assertGeneratedCode(
            backendConfig = Backend.Config.production,
            factory = CppBackend.Cpp,
            inputs = listOf(
                filePath("something", "fun.temper") to """
                    |let { prod } = import("./deeper");
                    |
                    |@connected
                    |export let sum(i: Int, j: Int, bonus: Int = 0): Int;
                    |
                    |export let inc(i: Int): Int {
                    |    sum(i, 1)
                    |}
                    |
                    |export let twice(i: Int): Int {
                    |  prod(i, 2)
                    |}
                """.trimMargin(),
                filePath("something", "deeper", "more-fun.temper") to """
                    |export let prod(i: Int, j: Int): Int {
                    |    i * j
                    |}
                """.trimMargin(),
            ) + connecteds,
            want = """
                |{
                |    "cpp": {
                |        "my-test-library": {
                |            "something.cpp": {
                |                content: ```
                |                  #include <my-test-library/something.hpp>
                |                  #include "something/_connected.hpp"
                |                  namespace my_test_library {
                |                    int32_t sum(int32_t i_4, int32_t j_5, temper::core::NullableParam<int32_t> bonus) {
                |                      int32_t bonus_6;
                |                      if(temper::core::is_null(bonus)) {
                |                        {
                |                          bonus_6 = 0;
                |                        }
                |                      }else {
                |                        {
                |                          bonus_6 = temper::core::not_null(bonus);
                |                        }
                |                      }
                |                      return _connected::sum(i_4, j_5, bonus_6);
                |                    }
                |                    int32_t sum(int32_t i_4, int32_t j_5) {
                |                      return sum(i_4, j_5, nullptr);
                |                    }
                |                    int32_t inc(int32_t i_8) {
                |                      return sum(i_8, 1);
                |                    }
                |                    int32_t twice(int32_t i_10) {
                |                      return my_test_library::prod(i_10, 2);
                |                    }
                |                    void global_init_something() {
                |                      static bool initialized = false;
                |                      if(initialized) {
                |                        return;
                |                      }
                |                      initialized = true;
                |                      my_test_library::global_init_deeper();
                |                    }
                |                  }
                |
                |                  ```
                |            },
                |            "something.hpp": {
                |                "content": ```
                |                  #pragma once
                |                  #include <temper-core/core.hpp>
                |                  #include <my-test-library/something/deeper.hpp>
                |                  namespace my_test_library {
                |                    int32_t sum(int32_t, int32_t, temper::core::NullableParam<int32_t>);
                |                    int32_t sum(int32_t, int32_t);
                |                    int32_t inc(int32_t);
                |                    int32_t twice(int32_t);
                |                    void global_init_something();
                |                  }
                |
                |                  ```
                |            },
                |            "something": {
                |                "_connected.cpp": "__DO_NOT_CARE__",
                |                "_connected.hpp": "__DO_NOT_CARE__",
                |                "deeper.cpp": "__DO_NOT_CARE__",
                |                "deeper.hpp": "__DO_NOT_CARE__",
                |                "deeper.hpp.map": "__DO_NOT_CARE__",
                |                "deeper.cpp.map": "__DO_NOT_CARE__",
                |            },
                |            "something.cpp.map": "__DO_NOT_CARE__",
                |            "something.hpp.map": "__DO_NOT_CARE__",
                |            "main.cpp": "__DO_NOT_CARE__",
                |            "other": {
                |                "thing": {
                |                    "whatever.cpp": "__DO_NOT_CARE__",
                |                },
                |            },
                |        },
                |    },
                |}
            """.trimMargin(),
        )
    }
}

private fun assertGeneratedContains(
    temper: String,
    cppContains: List<String>,
) {
    val logSink = ListBackedLogSink()
    val result = generateCode(
        inputs = listOf(filePath("something", "something.temper") to temper.trimMargin()),
        factory = CppBackend.Cpp,
        backendConfig = Backend.Config.production,
        genre = Genre.Library,
        moduleResultNeeded = false,
        logSink = logSink,
    )
    val memfs = result.fs as MemoryFileSystem
    val allContent = buildString {
        fun walk(file: MemoryFileSystem.FileOrDirectory) {
            when (file) {
                is MemoryFileSystem.File -> {
                    if (!file.absolutePath.toString().endsWith(".map")) {
                        append(file.textContent)
                        append("\n")
                    }
                }
                is MemoryFileSystem.SubDirectory -> file.ls().forEach(::walk)
            }
        }
        memfs.root.ls().forEach(::walk)
    }
    for (expected in cppContains) {
        assertTrue(
            allContent.contains(expected),
            "Generated code should contain '$expected' but didn't.\n$allContent",
        )
    }
}

private fun assertGenerated(
    temper: String,
    cpp: String,
    hpp: String,
) {
    fun escaped(text: String) = """
        |               "content":
        |```
        |${text.trimMargin()}
        |```
    """.trimMargin()
    assertGeneratedCode(
        backendConfig = Backend.Config.production,
        factory = CppBackend.Cpp,
        inputs = listOf(filePath("something", "something.temper") to temper.trimMargin()),
        moduleResultNeeded = false,
        want = """
            |{
            |    "cpp": {
            |        "my-test-library": {
            |            "something.cpp": {
            |${escaped(cpp)}
            |            },
            |            "something.hpp": {
            |${escaped(hpp)}
            |            },
            |            "something.cpp.map": "__DO_NOT_CARE__",
            |            "something.hpp.map": "__DO_NOT_CARE__",
            |            "main.cpp": "__DO_NOT_CARE__",
            |        }
            |    }
            |}
        """.trimMargin(),
    )
}
