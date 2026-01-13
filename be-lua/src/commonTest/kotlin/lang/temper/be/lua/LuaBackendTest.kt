package lang.temper.be.lua

import lang.temper.be.Backend
import lang.temper.be.assertGeneratedCode
import lang.temper.log.filePath
import kotlin.test.Test

class LuaBackendTest {
    @Test
    fun classOrdering() {
        assertGenerated(
            temper = """
                |// Bubble return type had a bad impact here before.
                |let fuji = new Apple().maybe();
                |export class Apple {
                |  public maybe(): Apple throws Bubble { this }
                |}
            """.trimMargin(),
            lua = """
                |local temper = require('temper-core');
                |local Apple, fuji__0, exports;
                |Apple = temper.type('Apple');
                |Apple.methods.maybe = function(this__0)
                |  return this__0;
                |end;
                |Apple.constructor = function(this__1)
                |  return nil;
                |end;
                |fuji__0 = nil;
                |fuji__0 = Apple():maybe();
                |exports = {};
                |exports.Apple = Apple;
                |return exports;
                |
            """.trimMargin(),
        )
    }

    @Test
    fun endName() {
        // Check keywords in both property and var positions.
        assertGenerated(
            temper = """
                |export class Hi(public end: Int = 5) {}
                |export let end = new Hi().end;
            """.trimMargin(),
            lua = """
                |local temper = require('temper-core');
                |local Hi, end_, exports;
                |Hi = temper.type('Hi');
                |Hi.constructor = function(this__0, end__0)
                |  local end__1;
                |  if temper.is_null(end__0) then
                |    end__1 = 5;
                |  else
                |    end__1 = end__0;
                |  end
                |  this__0.end__2 = end__1;
                |  return nil;
                |end;
                |Hi.get.end_ = function(this__1)
                |  return this__1.end__2;
                |end;
                |end_ = (Hi()).end_;
                |exports = {};
                |exports.Hi = Hi;
                |exports.end_ = end_;
                |return exports;
                |
            """.trimMargin(),
        )
    }

    @Test
    fun ifElse() {
        assertGenerated(
            temper = """
                |export let signText(i: Int): String {
                |  if (i < 0) {
                |    "negative"
                |  } else if (i > 0) {
                |    "positive"
                |  } else if (i > 10) {
                |    "very positive"
                |  } else {
                |    "zero"
                |  }
                |}
            """.trimMargin(),
            lua = """
                |local temper = require('temper-core');
                |local signText, exports;
                |signText = function(i__0)
                |  local return__0;
                |  if (i__0 < 0) then
                |    return__0 = 'negative';
                |  elseif (i__0 > 0) then
                |    return__0 = 'positive';
                |  elseif (i__0 > 10) then
                |    return__0 = 'very positive';
                |  else
                |    return__0 = 'zero';
                |  end
                |  return return__0;
                |end;
                |exports = {};
                |exports.signText = signText;
                |return exports;
                |
            """.trimMargin(),
        )
    }

    @Test
    fun nullableProps() {
        assertGenerated(
            temper = """
                |export class Hi(
                |  public var nullable: Int?,
                |  public var nullDefault: Int? = null,
                |) {}
            """.trimMargin(),
            lua = """
                |local temper = require('temper-core');
                |local Hi, exports;
                |Hi = temper.type('Hi');
                |Hi.constructor = function(this__0, nullable__0, nullDefault__0)
                |  if (nullable__0 == nil) then
                |    nullable__0 = temper.null;
                |  end
                |  if (nullDefault__0 == nil) then
                |    nullDefault__0 = temper.null;
                |  end
                |  this__0.nullable__1 = nullable__0;
                |  this__0.nullDefault__1 = nullDefault__0;
                |  return nil;
                |end;
                |Hi.get.nullable = function(this__1)
                |  return this__1.nullable__1;
                |end;
                |Hi.set.nullable = function(this__2, newNullable__0)
                |  if (newNullable__0 == nil) then
                |    newNullable__0 = temper.null;
                |  end
                |  this__2.nullable__1 = newNullable__0;
                |  return nil;
                |end;
                |Hi.get.nullDefault = function(this__3)
                |  return this__3.nullDefault__1;
                |end;
                |Hi.set.nullDefault = function(this__4, newNullDefault__0)
                |  if (newNullDefault__0 == nil) then
                |    newNullDefault__0 = temper.null;
                |  end
                |  this__4.nullDefault__1 = newNullDefault__0;
                |  return nil;
                |end;
                |exports = {};
                |exports.Hi = Hi;
                |return exports;
                |
            """.trimMargin(),
        )
    }

    @Test
    fun publicPrivateInstanceStatic() = assertGenerated(
        // Copied somewhat from JsBackendTest cases.
        temper = """
            |export class C {
            |  private var y: Int = 1;
            |  public var z: Int = 2;
            |  public get p(): Int { y - 1 }
            |  private set p(newP: Int): Void { y = newP + 1 }
            |  public set q(newQ: Int): Void { p = newQ }
            |  public incr(): Int { p += 1 }
            |  private decr(): Int { p -= 1 }
            |}
            |export class D {
            |  private static i: Int = 1;
            |  private static f(j: Int): Int { D.i + j }
            |  public static g(n: Int): Int { D.f(n) + n }
            |}
        """.trimMargin(),
        lua = """
            |local temper = require('temper-core');
            |local C, D, exports;
            |C = temper.type('C');
            |C.get.p = function(this__0)
            |  return temper.int32_sub(this__0.y__0, 1);
            |end;
            |C.set.p = function(this__1, newP__0)
            |  local t_0;
            |  t_0 = temper.int32_add(newP__0, 1);
            |  this__1.y__0 = t_0;
            |  return nil;
            |end;
            |C.get.q = function(this_1)
            |end;
            |C.set.q = function(this__2, newQ__0)
            |  this__2['p'] = newQ__0;
            |  return nil;
            |end;
            |C.methods.incr = function(this__3)
            |  local return__0;
            |  return__0 = temper.int32_add(this__3.p, 1);
            |  this__3['p'] = return__0;
            |  return return__0;
            |end;
            |C.methods.decr = function(this__4)
            |  local return__1;
            |  return__1 = temper.int32_sub(this__4.p, 1);
            |  this__4['p'] = return__1;
            |  return return__1;
            |end;
            |C.constructor = function(this__5)
            |  this__5.y__0 = 1;
            |  this__5.z__0 = 2;
            |  return nil;
            |end;
            |C.get.z = function(this__6)
            |  return this__6.z__0;
            |end;
            |C.set.z = function(this__7, newZ__0)
            |  this__7.z__0 = newZ__0;
            |  return nil;
            |end;
            |D = temper.type('D');
            |D.f = function(j__0)
            |  return temper.int32_add(D.i, j__0);
            |end;
            |D.g = function(n__0)
            |  return temper.int32_add(D.f(n__0), n__0);
            |end;
            |D.constructor = function(this__8)
            |  return nil;
            |end;
            |D.i = 1;
            |exports = {};
            |exports.C = C;
            |exports.D = D;
            |return exports;
            |
        """.trimMargin(),
    )

    @Test
    fun simpleLogic() {
        assertGenerated(
            temper = """
                |export let hi(): String {
                |  let builder = new ListBuilder<String>();
                |  for (var i = 0; i < 3; i += 1) {
                |    builder.add("hi");
                |  } orelse panic();
                |  builder.join(" ") { it => it }
                |}
            """.trimMargin(),
            lua = """
                |local temper = require('temper-core');
                |local hi, exports;
                |hi = function()
                |  local builder__0, i__0, fn__0;
                |  builder__0 = temper.listbuilder_constructor();
                |  i__0 = 0;
                |  while (i__0 < 3) do
                |    temper.listbuilder_add(builder__0, 'hi');
                |    i__0 = temper.int32_add(i__0, 1);
                |  end
                |  fn__0 = function(it__0)
                |    return it__0;
                |  end;
                |  return temper.listed_join(builder__0, ' ', fn__0);
                |end;
                |exports = {};
                |exports.hi = hi;
                |return exports;
                |
            """.trimMargin(),
        )
    }

    @Test
    fun someGenerator() = assertGenerated(
        temper = """
            |let f(generatorFactory: fn (): SafeGenerator<Empty>): Void {
            |  generatorFactory().next();
            |}
            |f { (): GeneratorResult<Empty> extends GeneratorFn =>
            |  console.log("foo");
            |  yield;
            |  console.log("bar");
            |};
        """.trimMargin(),
        lua = """
            |local temper = require('temper-core');
            |local console_0, f__0, fn__0, exports;
            |console_0 = 0.0;
            |f__0 = function(generatorFactory__0)
            |  temper.generator_next(generatorFactory__0());
            |  return nil;
            |end;
            |fn__0 = temper.adapt_generator_fn(function()
            |  temper.log('foo');
            |  temper.yield();
            |  temper.log('bar');
            |end);
            |f__0(fn__0);
            |exports = {};
            |return exports;
            |
        """.trimMargin(),
    )

    @Test
    fun unicodeNames() = assertGenerated(
        temper = """
            |export let τó🐝 = "or not to be?";
        """.trimMargin(),
        lua = """
            |local temper = require('temper-core');
            |local x3c4ox301x1f41d, exports;
            |x3c4ox301x1f41d = 'or not to be?';
            |exports = {};
            |exports.x3c4ox301x1f41d = x3c4ox301x1f41d;
            |return exports;
            |
        """.trimMargin(),
    )
}

private fun assertGenerated(
    temper: String,
    lua: String,
) {
    val escaped = """
        |               "content":
        |```
        |$lua
        |```
    """.trimMargin()
    assertGeneratedCode(
        backendConfig = Backend.Config.production,
        factory = LuaBackend.Lua51,
        inputs = listOf(filePath("something", "something.temper") to temper),
        moduleResultNeeded = false,
        want = """
            |{
            |    "lua": {
            |        "my-test-library": {
            |            "something.lua": {
            |${escaped}
            |            },
            |            "init.lua": "__DO_NOT_CARE__",
            |            "something.lua.map": "__DO_NOT_CARE__",
            |            "my-test-library-dev-1.rockspec": "__DO_NOT_CARE__",
            |        }
            |    }
            |}
        """.trimMargin(),
    )
}
