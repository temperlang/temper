package lang.temper.be.py

import lang.temper.be.Backend
import lang.temper.be.assertGeneratedCode
import lang.temper.be.inputFileMapFromJson
import lang.temper.common.stripDoubleHashCommentLinesToPutCommentsInlineBelow
import lang.temper.common.structure.FormattingStructureSink
import lang.temper.common.structure.StructureSink
import lang.temper.common.structure.Structured
import lang.temper.log.FilePath
import lang.temper.log.filePath
import org.junit.jupiter.api.Timeout
import kotlin.test.Test

@SuppressWarnings("MaxLineLength")
class PyBackendTest {
    @Test
    fun genericFunctions() = assertGeneratedCode(
        input = """
            |/** They're the same thing! */
            |export let identity<T>(/** The thing to return */t: T): T { t }
            |export class Thing<T> {
            |  public identity<U>(u: U, f: fn (U): T): T { f(u) }
            |}
        """.trimMargin(),
        want = """
            |from typing import Any as Any0, TypeVar as TypeVar1, Generic as Generic2, Callable as Callable3
            |T_1 = TypeVar1('T_1', bound = Any0)
            |U_3 = TypeVar1('U_3', bound = Any0)
            |class Thing(Generic2[T_1]):
            |    __slots__ = ()
            |    def identity(this_2, u_11: 'U_3', f_12: 'Callable3[[U_3], T_1]', /) -> 'T_1':
            |        return f_12(u_11)
            |    def __init__(this, /) -> None:
            |        pass
            |T_0 = TypeVar1('T_0', bound = Any0)
            |def identity(t_8: 'T_0', /) -> 'T_0':
            |    "They're the same thing!\n\nt__0: T__0\n  The thing to return\n"
            |    return t_8
            |
        """.trimMargin(),
    )

    @Test
    fun overloadedMethods() = assertGeneratedCode(
        input = """
            |export class IntMaker(public radix: Int32) {
            |   @overload("toInt")
            |   public int64ToInt(int: Int64): Int32 throws Bubble { int.toInt32() }
            |
            |   @overload("toInt")
            |   public stringToInt(string: String): Int32 throws Bubble { string.toInt32(radix) }
            |}
            |
            |export let crazySum(intMaker: IntMaker, int: Int64, string: String): Int throws Bubble {
            |   let intInt = intMaker.toInt(int);
            |   let stringInt = intMaker.toInt(string);
            |   intInt + stringInt
            |}
        """.trimMargin(),
        want = """
            |from builtins import int as int3, str as str4
            |from temper_core import int64_to_int32 as int64_to_int320, string_to_int32 as string_to_int321, int_add as int_add2
            |int64_to_int32_35 = int64_to_int320
            |string_to_int32_36 = string_to_int321
            |int_add_37 = int_add2
            |class IntMaker:
            |    radix_7: 'int3'
            |    __slots__ = ('radix_7',)
            |    def int64_to_int(this_0, int_9: 'int64_24', /) -> 'int3':
            |        return int64_to_int32_35(int_9)
            |    def string_to_int(this_1, string_12: 'str4', /) -> 'int3':
            |        return string_to_int32_36(string_12, this_1.radix_7)
            |    def __init__(this, /, radix: 'int3') -> None:
            |        this.radix_7 = radix
            |    @property
            |    def radix(this_24, /) -> 'int3':
            |        return this_24.radix_7
            |def crazy_sum(int_maker_16: 'IntMaker', int_17: 'int64_24', string_18: 'str4', /) -> 'int3':
            |    int_int_20: 'int3'
            |    int_int_20 = int_maker_16.int64_to_int(int_17)
            |    string_int_21: 'int3'
            |    string_int_21 = int_maker_16.string_to_int(string_18)
            |    return int_add_37(int_int_20, string_int_21)
            |
        """.trimMargin(),
    )

    @Test
    fun markdownDocComment() = assertGeneratedCode(
        name = "test.temper.md",
        input = """
            |I is an interface
            |
            |    export interface I {
            |
            |x is cool
            |
            |      @var
            |      @public
            |
            |x is nice, but this comment isn't gathered
            |
            |      let x: Int;
            |
            |      /** y also is cool */
            |      public y: Int;
            |    }
        """.trimMargin(),
        want = """
            |from abc import ABCMeta as ABCMeta0, abstractmethod as abstractmethod1
            |from builtins import int as int2
            |class I(metaclass = ABCMeta0):
            |    'I is an interface'
            |    @property
            |    @abstractmethod1
            |    def x(self) -> 'int2':
            |        'x is cool'
            |    @property
            |    @abstractmethod1
            |    def y(self) -> 'int2':
            |        'y also is cool'
            |
        """.trimMargin(),
    )

    @Test
    fun mapBuilderConnected() = assertGeneratedCode(
        input = """
            |export let f(): MapBuilder<String, Int> {
            |  new MapBuilder<String, Int>()
            |}
            |var m: MapBuilder<String, Int>? = null;
            |for (let e of [1, 2, 3]) {
            |  if (e == 2) {
            |    m = new MapBuilder<String, Int>()
            |  }
            |}
            |if (m is MapBuilder<String, Int>) {
            |  console.log("Allocated m");
            |}
        """.trimMargin(),
        want = """
            |from typing import Union as Union3, Dict as Dict4, Sequence as Sequence9
            |from builtins import str as str5, int as int6, bool as bool7, isinstance as isinstance10, len as len0
            |from temper_core import LoggingConsole as LoggingConsole8, list_get as list_get1, int_add as int_add2
            |len_38 = len0
            |list_get_39 = list_get1
            |int_add_40 = int_add2
            |t_34: 'Union3[(Dict4[str5, int6]), None]'
            |t_16: 'bool7'
            |t_31: 'LoggingConsole8' = LoggingConsole8(__name__)
            |def f() -> 'Dict4[str5, int6]':
            |    return {}
            |m_1: 'Union3[(Dict4[str5, int6]), None]' = None
            |this_19: 'Sequence9[int6]' = (1, 2, 3)
            |n_20: 'int6' = len_38(this_19)
            |i_21: 'int6' = 0
            |while i_21 < n_20:
            |    el_22: 'int6' = list_get_39(this_19, i_21)
            |    i_21 = int_add_40(i_21, 1)
            |    e_3: 'int6' = el_22
            |    if e_3 == 2:
            |        t_34 = {}
            |        m_1 = t_34
            |if not m_1 is None:
            |    t_16 = isinstance10(m_1, Dict4)
            |else:
            |    t_16 = False
            |if t_16:
            |    t_31.log('Allocated m')
            |
        """.trimMargin(),
    )

    @Test
    fun overriddenProperty() = assertGeneratedCode(
        input = """
            |export interface I {
            |  public x: Int;
            |}
            |export class J(public x: Int) extends I {}
        """.trimMargin(),
        want = """
            |from abc import ABCMeta as ABCMeta0, abstractmethod as abstractmethod1
            |from builtins import int as int2
            |class I(metaclass = ABCMeta0):
            |    @property
            |    @abstractmethod1
            |    def x(self) -> 'int2':
            |        pass
            |class J(I):
            |    x_3: 'int2'
            |    __slots__ = ('x_3',)
            |    def __init__(this, /, x: 'int2') -> None:
            |        this.x_3 = x
            |    @property
            |    def x(this_8, /) -> 'int2':
            |        return this_8.x_3
            |
        """.trimMargin(),
    )

    @Test
    fun listBuilderAppend() = assertGeneratedCode(
        input = """
            |do {
            |  let b = new ListBuilder<Int>();
            |  b.add(1); // At end
            |  b.add(2, 0); // At beginning
            |}
        """.trimMargin(),
        want = """
            |from typing import MutableSequence as MutableSequence1
            |from builtins import int as int2, list as list0
            |from temper_core import list_builder_add as list_builder_add3
            |list_13 = list0
            |b_0: 'MutableSequence1[int2]' = list_13()
            |b_0.append(1)
            |list_builder_add3(b_0, 2, 0)
            |
        """.trimMargin(),
    )

    @Test
    fun optionalArgs() = assertGeneratedCode(
        input = """
            |export let something(i: Int, j: Int = 5, k: Int = 6): Int { i + j + k }
        """.trimMargin(),
        want = """
            |from builtins import int as int1
            |from typing import Union as Union2
            |from temper_core import int_add as int_add0
            |int_add_13 = int_add0
            |def something(i_1: 'int1', j_5: 'Union2[int1, None]' = None, k_7: 'Union2[int1, None]' = None, /) -> 'int1':
            |    _j_5: 'Union2[int1, None]' = j_5
            |    _k_7: 'Union2[int1, None]' = k_7
            |    j_2: 'int1'
            |    if _j_5 is None:
            |        j_2 = 5
            |    else:
            |        j_2 = _j_5
            |    k_3: 'int1'
            |    if _k_7 is None:
            |        k_3 = 6
            |    else:
            |        k_3 = _k_7
            |    return int_add_13(int_add_13(i_1, j_2), k_3)
            |
        """.trimMargin(),
    )

    @Timeout(LONG_TEST_TIMEOUT_SECONDS)
    @Test
    fun importIncludesNeededTypes() = assertGeneratedCode(
        input = """
            |let { NullInterchangeContext, parseJson } = import("std/json");
            |
            |@json
            |export class C {}
        """.trimMargin(),
        want = """
            |from temper_std.json import parse_json as parse_json_3, JsonAdapter, JsonProducer, JsonSyntaxTree, InterchangeContext, JsonObject
            |from temper_core import cast_by_type as cast_by_type0
            |class CJsonAdapter_8(JsonAdapter['C']):
            |    __slots__ = ()
            |    def encode_to_json(this_21, x_16: 'C', p_17: 'JsonProducer', /) -> 'None':
            |        x_16.encode_to_json(p_17)
            |    def decode_from_json(this_22, t_18: 'JsonSyntaxTree', ic_19: 'InterchangeContext', /) -> 'C':
            |        return C.decode_from_json(t_18, ic_19)
            |    def __init__(this, /) -> None:
            |        pass
            |class C:
            |    __slots__ = ()
            |    def __init__(this, /) -> None:
            |        pass
            |    def encode_to_json(this_20, p_15: 'JsonProducer', /) -> 'None':
            |        p_15.start_object()
            |        p_15.end_object()
            |    @staticmethod
            |    def decode_from_json(t_5: 'JsonSyntaxTree', ic_6: 'InterchangeContext', /) -> 'C':
            |        obj_7: 'JsonObject'
            |        obj_7 = cast_by_type0(t_5, JsonObject)
            |        return C()
            |    @staticmethod
            |    def json_adapter() -> 'JsonAdapter[C]':
            |        return CJsonAdapter_8()
            |
        """.trimMargin(),
    )

    @Test
    fun importedClass() = assertGeneratedCode(
        inputs = inputFileMapFromJson(
            """
                |{
                |  a: {
                |    "a.temper":
                |      ```
                |      export class A(public let x: Int) {}
                |      ```,
                |  },
                |  b: {
                |    "b.temper":
                |      ```
                |      let { A } = import("../a");
                |      export let a = { x: 123 };
                |      ```
                |  },
                |}
            """.trimMargin(),
        ),
        want = """
            |{
            |  py: {
            |    "my-test-library": {
            |      "my_test_library": {
            |        "__init__.py": {
            |          content: ```
            |            import my_test_library.a as _0
            |            import my_test_library.b as _1
            |
            |            ```
            |        },
            |        "a.py": {
            |          content: ```
            |            from builtins import int as int0
            |            class A:
            |                x_2: 'int0'
            |                __slots__ = ('x_2',)
            |                def __init__(this, /, x: 'int0') -> None:
            |                    this.x_2 = x
            |                @property
            |                def x(this_7, /) -> 'int0':
            |                    return this_7.x_2
            |
            |            ```
            |        },
            |        "b.py": {
            |          content: ```
            |            from my_test_library.a import A
            |            a: 'A' = A(123)
            |
            |            ```
            |        },
            |        "__init__.py.map": "__DO_NOT_CARE__",
            |        "a.py.map": "__DO_NOT_CARE__",
            |        "b.py.map": "__DO_NOT_CARE__",
            |      }
            |    }
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun publicPrivateInstanceStatic() = assertGeneratedCode(
        // Copied somewhat from JsBackendTest cases.
        input = """
            |export class C {
            |  private var y: Int = 1;
            |  public var z: Int = 2;
            |  // Public get, private set.
            |  public get p(): Int { y - 1 }
            |  private set p(newP: Int): Void { y = newP + 1 }
            |  // No get, public set.
            |  public set q(newQ: Int): Void { p = newQ }
            |  // Private get, public set.
            |  private get r(): Int { p }
            |  public set r(newR: Int): Void { p = newR }
            |  // Private get, no set.
            |  private get s(): Int { p }
            |  // Public and private methods.
            |  public incr(): Int { p = r + 1 }
            |  private decr(): Int { p = r - 1 }
            |}
            |export class D {
            |  private static i: Int = 1;
            |  private static f(j: Int): Int { D.i + j }
            |  public static g(n: Int): Int { D.f(n) + n }
            |}
        """.trimMargin(),
        want = """
            |from builtins import int as int2, AttributeError as AttributeError4
            |from typing import Any as Any3, ClassVar as ClassVar5
            |from temper_core import int_sub as int_sub0, int_add as int_add1
            |int_sub_110 = int_sub0
            |int_add_111 = int_add1
            |class C:
            |    y_26: 'int2'
            |    z_27: 'int2'
            |    __slots__ = ('y_26', 'z_27')
            |    @property
            |    def p(this_0, /) -> 'int2':
            |        return int_sub_110(this_0.y_26, 1)
            |    def _set_p(this_1, new_p_31: 'int2', /) -> 'None':
            |        t_90: 'int2' = int_add_111(new_p_31, 1)
            |        this_1.y_26 = t_90
            |    @property
            |    def q(this_113, /) -> 'Any3':
            |        raise AttributeError4('q getter unavailable')
            |    @q.setter
            |    def q(this_2, new_q_34: 'int2', /) -> 'None':
            |        this_2._set_p(new_q_34)
            |    @property
            |    def r(this_3, /) -> 'int2':
            |        raise AttributeError4('r getter unavailable')
            |    def _get_r(this_3, /) -> 'int2':
            |        return this_3.p
            |    @r.setter
            |    def r(this_4, new_r_39: 'int2', /) -> 'None':
            |        this_4._set_p(new_r_39)
            |    def _get_s(this_5, /) -> 'int2':
            |        return this_5.p
            |    def incr(this_6, /) -> 'int2':
            |        return_20: 'int2'
            |        return_20 = int_add_111(this_6._get_r(), 1)
            |        this_6._set_p(return_20)
            |        return return_20
            |    def decr_45(this_7, /) -> 'int2':
            |        return_21: 'int2'
            |        return_21 = int_sub_110(this_7._get_r(), 1)
            |        this_7._set_p(return_21)
            |        return return_21
            |    def __init__(this, /) -> None:
            |        this.y_26 = 1
            |        this.z_27 = 2
            |    @property
            |    def z(this_62, /) -> 'int2':
            |        return this_62.z_27
            |    @z.setter
            |    def z(this_66, new_z_65: 'int2', /) -> 'None':
            |        this_66.z_27 = new_z_65
            |class D:
            |    _i: ClassVar5['int2']
            |    __slots__ = ()
            |    @staticmethod
            |    def f_49(j_50: 'int2', /) -> 'int2':
            |        return int_add_111(D._i, j_50)
            |    @staticmethod
            |    def g(n_53: 'int2', /) -> 'int2':
            |        return int_add_111(D.f_49(n_53), n_53)
            |    def __init__(this, /) -> None:
            |        pass
            |D._i = 1
            |
        """.trimMargin(),
    )

    @Test
    fun parameterizedCast() = assertGeneratedCode(
        input = """
            |export let probe(thing: AnyValue): Boolean throws Bubble {
            |  let things = thing as List<AnyValue>;
            |  things[0] is List<AnyValue>
            |}
        """.trimMargin(),
        // Key point is that we need non-parameterized types for `as` and `is` below.
        // Also, separately, downcasting to List should generate a Temper compiler error.
        // But generate as good of code as we can, anyway.
        want = """
            |from typing import Any as Any1, Sequence as Sequence3
            |from builtins import bool as bool2, RuntimeError as RuntimeError4, isinstance as isinstance6
            |from temper_core import cast_by_type as cast_by_type5, list_get as list_get0
            |list_get_14 = list_get0
            |def probe(thing_1: 'Any1', /) -> 'bool2':
            |    return_0: 'bool2'
            |    things_3: 'Sequence3[Any1]'
            |    if thing_1 is None:
            |        raise RuntimeError4()
            |    else:
            |        things_3 = cast_by_type5(thing_1, Sequence3)
            |    t_13: 'Any1' = list_get_14(things_3, 0)
            |    if not t_13 is None:
            |        return_0 = isinstance6(t_13, Sequence3)
            |    else:
            |        return_0 = False
            |    return return_0
            |
        """.trimMargin(),
        errors = listOf(
            "Types marked @mayDowncastTo(false) cannot be targeted with is or as runtime type checks because they may not be distinct on all backends: <[List<AnyValue>]> from AnyValue!",
            "Types marked @mayDowncastTo(false) cannot be targeted with is or as runtime type checks because they may not be distinct on all backends: <[List<AnyValue>]> from AnyValue!",
        ),
    )

    @Test
    fun topLevelReimportedByInit() = assertGeneratedCode(
        inputs = inputFileMapFromJson(
            """
                |{
                |  a: {
                |    "a.temper":
                |      ```
                |      export let a = "a";
                |      ```,
                |  },
                |  b: {
                |    "b.temper":
                |      ```
                |      export let b = "b";
                |      ```,
                |    c: {
                |      "whatever.temper":
                |        ```
                |        export let c = "c";
                |        ```
                |    },
                |  },
                |  top.temper:
                |    ```
                |    export let t = "t";
                |    ```
                |}
            """.trimMargin(),
        ),
        want = """
            |{
            |  py: {
            |    "my-test-library": {
            |      "my_test_library": {
            |        "__init__.py": {
            |          content: ```
            |            from .my_test_library import *
            |            import my_test_library.a as _1
            |            import my_test_library.b as _2
            |            import my_test_library.b.c as _3
            |
            |            ```
            |        },
            |        "a.py": {
            |          content: ```
            |            from builtins import str as str0
            |            a: 'str0' = 'a'
            |
            |            ```
            |        },
            |        b: {
            |          "__init__.py": {
            |            content: ```
            |              from builtins import str as str0
            |              b: 'str0' = 'b'
            |
            |              ```
            |          },
            |          "c.py": {
            |            content: ```
            |              from builtins import str as str0
            |              c: 'str0' = 'c'
            |
            |              ```
            |          },
            |          "__init__.py.map": "__DO_NOT_CARE__",
            |          "c.py.map": "__DO_NOT_CARE__",
            |        },
            |        "my_test_library.py": {
            |          content: ```
            |            from builtins import str as str0
            |            t: 'str0' = 't'
            |
            |            ```
            |        },
            |        "__init__.py.map": "__DO_NOT_CARE__",
            |        "a.py.map": "__DO_NOT_CARE__",
            |        "my_test_library.py.map": "__DO_NOT_CARE__",
            |      }
            |    }
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
            |  py: {
            |    my-test-library: {
            |      my_test_library: {
            |        foo.py: {
            |          content:
            |            ```
            |            from builtins import int as int0, bool as bool1
            |            def f1(a_4: 'int0', b_5: 'int0', /) -> 'bool1':
            |                return a_4 < b_5
            |            def f2(a_7: 'int0', b_8: 'int0', /) -> 'bool1':
            |                return a_7 >= b_8
            |            def f3(a_10: 'int0', b_11: 'int0', /) -> 'bool1':
            |                return a_10 <= b_11
            |            def f4(a_13: 'int0', /) -> 'bool1':
            |                return a_13 > -1
            |
            |            ```
            |         },
            |        foo.py.map: "__DO_NOT_CARE__",
            |        __init__.py: "__DO_NOT_CARE__",
            |        __init__.py.map: "__DO_NOT_CARE__",
            |      },
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
            |  py: {
            |    my-test-library: {
            |      my_test_library: {
            |        foo.py: {
            |          content:
            |            ```
            |            from temper_std.net import NetResponse, NetRequest
            |            from temper_core import LoggingConsole as LoggingConsole2, adapt_generator_factory as adapt_generator_factory3, str_cat as str_cat0, async_launch as async_launch1
            |            from typing import Union as Union5, Generator as Generator8
            |            from builtins import str as str6, Exception as Exception7
            |            str_cat_73 = str_cat0
            |            async_launch_74 = async_launch1
            |            console_5: 'LoggingConsole2' = LoggingConsole2(__name__)
            |            @adapt_generator_factory3
            |            def fn_65(do_await_4) -> 'Generator8[empty, None, None]':
            |                t_59: 'Union5[str6, None]'
            |                t_31: 'Union5[str6, None]'
            |                t_36: 'str6'
            |                try:
            |                    r_3: 'NetResponse'
            |                    r_3 = yield do_await_4(NetRequest('data:text/plain,Hello World!').send())
            |                    if r_3.status == 200:
            |                        body_4: 'str6'
            |                        t_31 = yield do_await_4(r_3.text)
            |                        if not t_31 is None:
            |                            body_4 = t_31
            |                        else:
            |                            body_4 = 'missing'
            |                        t_59 = r_3.content_type
            |                        if not t_59 is None:
            |                            subject_hash7_11: 'str6' = t_59
            |                            t_36 = subject_hash7_11
            |                        else:
            |                            t_36 = 'unknown'
            |                        console_5.log(str_cat_73('Got ', body_4, ' / ', t_36))
            |                except Exception7:
            |                    console_5.log('failed')
            |            async_launch_74(fn_65)
            |
            |            ```,
            |        },
            |        foo.py.map: "__DO_NOT_CARE__",
            |        __init__.py: "__DO_NOT_CARE__",
            |        __init__.py.map: "__DO_NOT_CARE__",
            |      }
            |    }
            |  }
            |}
        """.trimMargin().stripDoubleHashCommentLinesToPutCommentsInlineBelow(),
    )

    private fun assertGeneratedCode(
        inputs: List<Pair<FilePath, String>>,
        want: String,
    ) = assertGeneratedCode(
        inputs = inputs,
        want = want,
        backendConfig = Backend.Config.bundled,
        factory = PyBackend.Python3,
    )

    private fun assertGeneratedCode(
        input: String,
        want: String,
        errors: List<String> = listOf(),
        name: String = "test.temper",
    ) = assertGeneratedCode(
        inputs = listOf(
            filePath("src", "test", name) to input,
        ),
        // The $want section looks weird, but it needs to look like that to be indented in a way that the parser likes
        want = FormattingStructureSink.toJsonString(
            object : Structured {
                override fun destructure(structureSink: StructureSink) = structureSink.obj {
                    key("py") {
                        obj {
                            key("my-test-library") {
                                obj {
                                    key("my_test_library") {
                                        obj {
                                            key("src") {
                                                obj {
                                                    key("test.py") {
                                                        obj {
                                                            key("content") {
                                                                value(want)
                                                            }
                                                        }
                                                    }
                                                    key("test.py.map") {
                                                        value("__DO_NOT_CARE__")
                                                    }
                                                    for (dif in dunderInitFiles) {
                                                        key(dif) {
                                                            value("__DO_NOT_CARE__")
                                                        }
                                                    }
                                                }
                                            }
                                            for (dif in dunderInitFiles) {
                                                key(dif) {
                                                    value("__DO_NOT_CARE__")
                                                }
                                            }
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
            },
        ),
    )
}

private val dunderInitFiles = listOf("$DUNDER_INIT.py", "$DUNDER_INIT.py.map")

private const val LONG_TEST_TIMEOUT_SECONDS = 60L
