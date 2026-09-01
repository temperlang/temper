package lang.temper.be.rust

import lang.temper.be.Backend
import lang.temper.be.assertGeneratedCode
import lang.temper.be.inputFileMapFromJson
import lang.temper.common.stripDoubleHashCommentLinesToPutCommentsInlineBelow
import lang.temper.log.FilePath
import lang.temper.log.filePath
import lang.temper.name.DashedIdentifier
import kotlin.test.Test

@SuppressWarnings("MaxLineLength")
class RustBackendTest {
    @Test
    fun fileLayout() = assertGeneratedFileTree(
        inputs = inputFileMapFromJson(
            $$"""
                |{
                |  foo.temper: ```
                |    console.log("Foo")
                |    // Also test https://github.com/temperlang/temper/issues/216
                |    export let hi(nums: Listed<Int>): String {
                |      let a = nums.join("", stringify);
                |      let b = nums.join("", stringifyValue);
                |      let c = nums.join("", stringifyHere);
                |      let d = nums.join("", stringifyValueHere);
                |      "${a}${b}${c}${d}"
                |    }
                |    export let makeTalkHere(talker: Talker): Void { talker.talk(); }
                |    let { stringify, stringifyValue, Talker } = import("./bar");
                |    let stringifyHere(i: Int): String { i.toString() }
                |    let stringifyValueHere = stringifyHere;
                |    ```,
                |  bar: {
                |    baz.temper: ```
                |      export let stringify(i: Int): String { i.toString() }
                |      export let stringifyValue = stringify;
                |      console.log("Baz")
                |      export interface Talker {
                |        talk(): Void;
                |      }
                |      ```,
                |    boo.temper: ```
                |      console.log("Boo")
                |      export let makeTalk(talker: Talker): Void { talker.talk(); }
                |      ```,
                |  },
                |}
            """.trimMargin(),
        ),
        want = """
            |{
            |  rust: {
            |    my-test-library: {
            |      Cargo.toml: {
            |        content: ```
            |          [package]
            |          name = "my-test-library"
            |          version = "0.0.1"
            |          edition = "2021"
            |          rust-version = "1.71.1"
            |
            |          [dependencies]
            |          temper-core = { path = "../temper-core", version = "=${DashedIdentifier.temperCoreLibraryVersion}" }
            |
            |          ```
            |      },
            |      src: {
            |        lib.rs: {
            |          content: ```
            |            #![allow(warnings)]
            |            #![allow(dependency_on_unit_never_type_fallback)]
            |            pub mod bar;
            |            mod r#mod;
            |            pub use r#mod::*;
            |            mod support;
            |            pub (crate) use support::*;
            |            pub fn init(config: Option<temper_core::Config>) -> temper_core::Result<temper_core::AsyncRunner> {
            |                crate::CONFIG.get_or_init(| | config.unwrap_or_else(| | temper_core::Config::default()));
            |                bar::init() ? ;
            |                r#mod::init() ? ;
            |                Ok(crate::config().runner().clone())
            |            }
            |
            |            ```
            |        },
            |        lib.rs.map: "__DO_NOT_CARE__",
            |        main.rs: "__DO_NOT_CARE__",
            |        mod.rs: {
            |          content: ```
            |            #![allow(warnings)]
            |            #![allow(dependency_on_unit_never_type_fallback)]
            |            use temper_core::AnyValueTrait;
            |            use temper_core::AsAnyValue;
            |            use temper_core::Pair;
            |            use crate::bar::TalkerTrait;
            |            pub (crate) fn init() -> temper_core::Result<()> {
            |                static INIT_ONCE: std::sync::OnceLock<temper_core::Result<()>> = std::sync::OnceLock::new();
            |                INIT_ONCE.get_or_init(| |{
            |                        let stringifyValue__0: std::sync::Arc<dyn Fn (i32) -> std::sync::Arc<String> + std::marker::Send + std::marker::Sync> = std::sync::Arc::new(crate::bar::stringify.clone());
            |                        println!("{}", "Foo");
            |                        let stringifyValueHere__0: std::sync::Arc<dyn Fn (i32) -> std::sync::Arc<String> + std::marker::Send + std::marker::Sync> = std::sync::Arc::new(stringifyHere__0.clone());
            |                        Ok(())
            |                }).clone()
            |            }
            |            fn stringifyHere__0(i__0: i32) -> std::sync::Arc<String> {
            |                return temper_core::int_to_string(i__0, None);
            |            }
            |            pub fn hi(nums__0: impl temper_core::ToListed<i32>) -> std::sync::Arc<String> {
            |                let nums__0 = nums__0.to_listed();
            |                let a__0: std::sync::Arc<String> = temper_core::listed::join( & ( * nums__0), std::sync::Arc::new("".to_string()), & crate::bar::stringify.clone());
            |                let b__0: std::sync::Arc<String> = temper_core::listed::join( & ( * nums__0), std::sync::Arc::new("".to_string()), & crate::bar::stringify.clone());
            |                let c__0: std::sync::Arc<String> = temper_core::listed::join( & ( * nums__0), std::sync::Arc::new("".to_string()), & stringifyHere__0.clone());
            |                let d__0: std::sync::Arc<String> = temper_core::listed::join( & ( * nums__0), std::sync::Arc::new("".to_string()), & stringifyHere__0.clone());
            |                return std::sync::Arc::new(format!("{}{}{}{}", a__0, b__0.clone(), c__0.clone(), d__0.clone()));
            |            }
            |            pub fn make_talk_here(talker__0: crate::bar::Talker) {
            |                talker__0.talk();
            |            }
            |
            |            ```
            |        },
            |        mod.rs.map: "__DO_NOT_CARE__",
            |        bar: {
            |          mod.rs: {
            |            content: ```
            |              #![allow(warnings)]
            |              #![allow(dependency_on_unit_never_type_fallback)]
            |              use temper_core::AnyValueTrait;
            |              use temper_core::AsAnyValue;
            |              use temper_core::Pair;
            |              pub (crate) fn init() -> temper_core::Result<()> {
            |                  static INIT_ONCE: std::sync::OnceLock<temper_core::Result<()>> = std::sync::OnceLock::new();
            |                  INIT_ONCE.get_or_init(| |{
            |                          STRINGIFY_VALUE.set(std::sync::Arc::new(stringify.clone())).unwrap_or_else(| _ | panic!());
            |                          println!("{}", "Baz");
            |                          println!("{}", "Boo");
            |                          Ok(())
            |                  }).clone()
            |              }
            |              static STRINGIFY_VALUE: std::sync::OnceLock<std::sync::Arc<dyn Fn (i32) -> std::sync::Arc<String> + std::marker::Send + std::marker::Sync>> = std::sync::OnceLock::new();
            |              pub fn stringify_value() -> std::sync::Arc<dyn Fn (i32) -> std::sync::Arc<String> + std::marker::Send + std::marker::Sync> {
            |                  ( * STRINGIFY_VALUE.get().unwrap()).clone()
            |              }
            |              pub trait TalkerTrait: temper_core::AsAnyValue + temper_core::AnyValueTrait + std::marker::Send + std::marker::Sync {
            |                  fn clone_boxed(& self) -> Talker;
            |                  fn talk(& self);
            |              }
            |              #[derive(Clone)]
            |              pub struct Talker(std::sync::Arc<dyn TalkerTrait>);
            |              impl Talker {
            |                  pub fn new(selfish: impl TalkerTrait + 'static) -> Talker {
            |                      Talker(std::sync::Arc::new(selfish))
            |                  }
            |              }
            |              impl TalkerTrait for Talker {
            |                  fn clone_boxed(& self) -> Talker {
            |                      TalkerTrait::clone_boxed( & ( * self.0))
            |                  }
            |                  fn talk(& self) -> () {
            |                      TalkerTrait::talk( & ( * self.0))
            |                  }
            |              }
            |              temper_core::impl_any_value_trait_for_interface!(Talker);
            |              impl std::ops::Deref for Talker {
            |                  type Target = dyn TalkerTrait;
            |                  fn deref(& self) -> & Self::Target {
            |                      & ( * self.0)
            |                  }
            |              }
            |              pub fn stringify(i__1: i32) -> std::sync::Arc<String> {
            |                  return temper_core::int_to_string(i__1, None);
            |              }
            |              pub fn make_talk(talker__1: Talker) {
            |                  talker__1.talk();
            |              }
            |
            |              ```
            |          },
            |          mod.rs.map: "__DO_NOT_CARE__",
            |        },
            |        $SUPPORT_FILES_DO_NOT_CARE
            |      }
            |    }
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun connected() = assertGeneratedFileTree(
        inputs = inputFileMapFromJson(
            """
                |{
                |## Test using a submodule.
                |  sub: {
                |    things.temper: ```
                |      @connected
                |      export let sum(i: Int, j: Int, bonus: Int = 0): Int;
                |      export let inc(i: Int): Int {
                |          sum(i, 1)
                |      }
                |      ```,
                |    _connected.rs: ```
                |## This submodule declaration in connected code is why we make a subdir later.
                |      mod whatever;
                |      pub(crate) fn sum(i: i32, j: i32, bonus: i32) -> i32 {
                |          i + j + bonus
                |      }
                |      ```,
                |## Include a bonus rust file to show where it ends up relative to connected code.
                |    whatever.rs: ```
                |      // Content doesn't matter.
                |      ```,
                |  },
                |}
            """.trimMargin().stripDoubleHashCommentLinesToPutCommentsInlineBelow(),
        ),
        want = """
            |{
            |  rust: {
            |    my-test-library: {
            |      Cargo.toml: "__DO_NOT_CARE__",
            |      src: {
            |        lib.rs: "__DO_NOT_CARE__",
            |        lib.rs.map: "__DO_NOT_CARE__",
            |        main.rs: "__DO_NOT_CARE__",
            |        sub: {
            |          mod.rs: {
            |            content: ```
            |              #![allow(warnings)]
            |              #![allow(dependency_on_unit_never_type_fallback)]
            |              mod _connected;
            |              use temper_core::AnyValueTrait;
            |              use temper_core::AsAnyValue;
            |              use temper_core::Pair;
            |              pub (crate) fn init() -> temper_core::Result<()> {
            |                  static INIT_ONCE: std::sync::OnceLock<temper_core::Result<()>> = std::sync::OnceLock::new();
            |                  INIT_ONCE.get_or_init(| |{
            |                          Ok(())
            |                  }).clone()
            |              }
            |              pub fn sum(i__0: i32, j__0: i32, bonus__0: Option<i32>) -> i32 {
            |                  let bonus__1: i32;
            |                  if bonus__0.is_none() {
            |                      bonus__1 = 0;
            |                  } else {
            |                      bonus__1 = bonus__0.unwrap();
            |                  }
            |                  _connected::sum(i__0, j__0, bonus__1)
            |              }
            |              pub fn inc(i__1: i32) -> i32 {
            |                  return sum(i__1, 1, None);
            |              }
            |
            |              ```
            |          },
            |          _connected: {
            |## Raw rust code goes into this subdir so other raw files are below it.
            |## The original "_connected.rs" gets renamed to "mod.rs", which leaves it
            |## effectively still just named `_connected`, and "whatever.rs" is now
            |## `_connected::whatever` relative to anything above.
            |            mod.rs: "__DO_NOT_CARE__",
            |            whatever.rs: "__DO_NOT_CARE__",
            |          },
            |          mod.rs.map: "__DO_NOT_CARE__",
            |        },
            |        $SUPPORT_FILES_DO_NOT_CARE
            |      }
            |    }
            |  }
            |}
        """.trimMargin().stripDoubleHashCommentLinesToPutCommentsInlineBelow(),
    )

    @Test
    fun async() {
        assertGenerateWanted(
            temper = """
                |class A(public a: String) {}
                |let b = new PromiseBuilder<A>();
                |let p = b.promise;
                |async { (): GeneratorResult<Empty> extends GeneratorFn =>
                |  console.log((await p).a orelse "");
                |}
                |b.complete(new A("Hi"));
            """.trimMargin(),
            rust = """
                |pub (crate) fn init() -> temper_core::Result<()> {
                |    static INIT_ONCE: std::sync::OnceLock<temper_core::Result<()>> = std::sync::OnceLock::new();
                |    INIT_ONCE.get_or_init(| |{
                |            let b__0: temper_core::PromiseBuilder<A> = temper_core::PromiseBuilder::new();
                |            P.set(b__0.promise()).unwrap_or_else(| _ | panic!());
                |            crate::run_async(std::sync::Arc::new(fn__0.clone()).clone());
                |            b__0.complete(A::new("Hi"));
                |            Ok(())
                |    }).clone()
                |}
                |static P: std::sync::OnceLock<temper_core::Promise<A>> = std::sync::OnceLock::new();
                |fn p() -> temper_core::Promise<A> {
                |    ( * P.get().unwrap()).clone()
                |}
                |struct AStruct {
                |    a: std::sync::Arc<String>
                |}
                |#[derive(Clone)]
                |pub (crate) struct A(std::sync::Arc<AStruct>);
                |impl A {
                |    pub fn new(a__0: impl temper_core::ToArcString) -> A {
                |        let a__0 = a__0.to_arc_string();
                |        let a;
                |        a = a__0.clone();
                |        let selfish = A(std::sync::Arc::new(AStruct {
                |                    a
                |        }));
                |        return selfish;
                |    }
                |    pub fn a(& self) -> std::sync::Arc<String> {
                |        return self.0.a.clone();
                |    }
                |}
                |temper_core::impl_any_value_trait!(A, []);
                |fn fn__0() -> temper_core::SafeGenerator<()> {
                |    let mut caseIndex___0: std::sync::Arc<std::sync::RwLock<i32>> = std::sync::Arc::new(std::sync::RwLock::new(0));
                |    let mut t___0: std::sync::Arc<std::sync::RwLock<Option<A>>> = std::sync::Arc::new(std::sync::RwLock::new(None));
                |    let mut t___1: std::sync::Arc<std::sync::RwLock<std::sync::Arc<String>>> = std::sync::Arc::new(std::sync::RwLock::new(std::sync::Arc::new("".to_string())));
                |    #[derive(Clone)]
                |    struct ClosureGroup___0 {
                |        caseIndex___0: std::sync::Arc<std::sync::RwLock<i32>>, t___0: std::sync::Arc<std::sync::RwLock<Option<A>>>, t___1: std::sync::Arc<std::sync::RwLock<std::sync::Arc<String>>>
                |    }
                |    impl ClosureGroup___0 {
                |        fn convertedCoroutine___0(& self, generator___0: temper_core::SafeGenerator<()>) -> Option<()> {
                |            'loop___0: loop {
                |                let caseIndexLocal___0: i32 = temper_core::read_locked( & self.caseIndex___0);
                |                {
                |                    * self.caseIndex___0.write().unwrap() = -1;
                |                }
                |                match caseIndexLocal___0.clone() {
                |                    0 => {
                |                        {
                |                            * self.caseIndex___0.write().unwrap() = 1;
                |                        }
                |                        p().clone().on_ready(std::sync::Arc::new(move | |{
                |                                    generator___0.clone().next();
                |                        }));
                |                        return Some(().clone());
                |                    },
                |                    1 => {
                |                        match p().clone().get() {
                |                            Ok(x) => {
                |                                {
                |                                    * self.t___0.write().unwrap() = Some(x);
                |                                }
                |                                {
                |                                    * self.caseIndex___0.write().unwrap() = 3;
                |                                }
                |                            },
                |                            _ => {
                |                                * self.caseIndex___0.write().unwrap() = 2;
                |                            }
                |                        };
                |                    },
                |                    2 => {
                |                        {
                |                            * self.t___1.write().unwrap() = std::sync::Arc::new("".to_string());
                |                        }
                |                        {
                |                            * self.caseIndex___0.write().unwrap() = 4;
                |                        }
                |                    },
                |                    3 => {
                |                        {
                |                            * self.t___1.write().unwrap() = temper_core::read_locked( & self.t___0).clone().unwrap().a();
                |                        }
                |                        {
                |                            * self.caseIndex___0.write().unwrap() = 4;
                |                        }
                |                    },
                |                    4 => {
                |                        println!("{}", temper_core::read_locked( & self.t___1).clone());
                |                        return None;
                |                    },
                |                    _ => {
                |                        return None;
                |                    }
                |                }
                |            }
                |        }
                |    }
                |    let closure_group = ClosureGroup___0 {
                |        caseIndex___0: caseIndex___0.clone(), t___0: t___0.clone(), t___1: t___1.clone()
                |    };
                |    let convertedCoroutine___0 = {
                |        let closure_group = closure_group.clone();
                |        std::sync::Arc::new(move | generator___0: temper_core::SafeGenerator<()> | closure_group.convertedCoroutine___0(generator___0))
                |    };
                |    return temper_core::SafeGenerator::from_fn(convertedCoroutine___0.clone().clone());
                |}
            """.trimMargin(),
        )
    }

    @Test
    fun bubblyOption() {
        assertGenerateWanted(
            temper = """
                |export let blah(i: Int): Int throws Bubble { something(i) orelse 0 }
                |export let something(var i: Int?): Int throws Bubble {
                |  when (i) {
                |    is Int -> 5 % (i as Int); // cast needed because var
                |    else -> 1;
                |  }
                |}
            """.trimMargin(),
            rust = """
                |pub (crate) fn init() -> temper_core::Result<()> {
                |    static INIT_ONCE: std::sync::OnceLock<temper_core::Result<()>> = std::sync::OnceLock::new();
                |    INIT_ONCE.get_or_init(| |{
                |            Ok(())
                |    }).clone()
                |}
                |pub fn something(mut i__0: Option<i32>) -> temper_core::Result<i32> {
                |    let return__0: i32;
                |    let t___0: bool;
                |    if ! i__0.is_none() {
                |        t___0 = i__0.is_some();
                |    } else {
                |        t___0 = false;
                |    }
                |    if t___0 {
                |        let t___1: i32;
                |        if i__0.is_none() {
                |            return Err(temper_core::Error::new());
                |        } else {
                |            let result___0: temper_core::Result<i32> = Ok(i__0.unwrap());
                |            if ! result___0.is_ok() {
                |                return result___0;
                |            }
                |            t___1 = result___0.unwrap();
                |        }
                |        let return___0: temper_core::Result<i32> = Ok(temper_core::int_rem(5, t___1));
                |        if ! return___0.is_ok() {
                |            return return___0;
                |        }
                |        return__0 = return___0.unwrap();
                |    } else {
                |        return__0 = 1;
                |    }
                |    return Ok(return__0);
                |}
                |pub fn blah(i__1: i32) -> temper_core::Result<i32> {
                |    let mut return__1: i32;
                |    'ok___0: {
                |        'orelse___0: {
                |            let return___1: temper_core::Result<i32> = Ok(something(Some(i__1)));
                |            if ! return___1.is_ok() {
                |                break 'orelse___0;
                |            }
                |            return__1 = return___1.unwrap();
                |            break 'ok___0;
                |        }
                |        return__1 = 0;
                |    }
                |    return Ok(return__1);
                |}
            """.trimMargin(),
        )
    }

    @Test
    fun bubblyConstructor() = assertGenerateWanted(
        temper = """
            |class C {
            |  public x: Boolean; // Always false
            |  public constructor(x: Boolean): Void throws Bubble {
            |    if (x) { bubble() }
            |    this.x = x;
            |  }
            |}
            |let a = new C(false);
        """.trimMargin(),
        rust = """
            |pub (crate) fn init() -> temper_core::Result<()> {
            |    static INIT_ONCE: std::sync::OnceLock<temper_core::Result<()>> = std::sync::OnceLock::new();
            |    INIT_ONCE.get_or_init(| |{
            |            let a___0: temper_core::Result<C> = Ok(C::new(false));
            |            if ! a___0.is_ok() {
            |                return Err(temper_core::Error::new());
            |            }
            |            let a__0: C = a___0.unwrap();
            |            Ok(())
            |    }).clone()
            |}
            |struct CStruct {
            |    x: bool
            |}
            |#[derive(Clone)]
            |pub (crate) struct C(std::sync::Arc<CStruct>);
            |impl C {
            |    pub fn new(x__0: bool) -> temper_core::Result<C> {
            |        let x;
            |        let return__0: ();
            |        return__0 = ();
            |        if x__0 {
            |            return Err(temper_core::Error::new());
            |        }
            |        x = x__0;
            |        return Ok(return__0);
            |        let selfish = C(std::sync::Arc::new(CStruct {
            |                    x
            |        }));
            |    }
            |    pub fn x(& self) -> bool {
            |        return self.0.x;
            |    }
            |}
            |temper_core::impl_any_value_trait!(C, []);
        """.trimMargin(),
    )

    @Test
    fun captureUnderNesting() = assertGenerateWanted(
        temper = $$"""
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
        rust = """
            |pub (crate) fn init() -> temper_core::Result<()> {
            |    static INIT_ONCE: std::sync::OnceLock<temper_core::Result<()>> = std::sync::OnceLock::new();
            |    INIT_ONCE.get_or_init(| |{
            |            Ok(())
            |    }).clone()
            |}
            |pub fn hi(n__0: i32) {
            |    let mut i__0: i32 = 0;
            |    'loop___0: while i__0 < n__0 {
            |        let mut a__0: std::sync::Arc<std::sync::RwLock<std::sync::Arc<String>>> = std::sync::Arc::new(std::sync::RwLock::new(std::sync::Arc::new(format!("{}", i__0))));
            |        #[derive(Clone)]
            |        struct ClosureGroup___0 {
            |            a__0: std::sync::Arc<std::sync::RwLock<std::sync::Arc<String>>>
            |        }
            |        impl ClosureGroup___0 {
            |            fn blah__0(& self, m__0: impl temper_core::ToArcString) {
            |                let m__0 = m__0.to_arc_string();
            |                {
            |                    * self.a__0.write().unwrap() = m__0.clone();
            |                }
            |            }
            |        }
            |        let closure_group = ClosureGroup___0 {
            |            a__0: a__0.clone()
            |        };
            |        let blah__0 = {
            |            let closure_group = closure_group.clone();
            |            std::sync::Arc::new(move | m__0: std::sync::Arc<String> | closure_group.blah__0(m__0))
            |        };
            |        blah__0(std::sync::Arc::new("hi".to_string()));
            |        i__0 = i__0.wrapping_add(1);
            |    }
            |}
        """.trimMargin(),
    )

    @Test
    fun captureMutInTest() = assertGenerateWanted(
        temper = """
            |test("main") {
            |  var sum = 0;
            |  repeat(3) { i =>
            |    sum += i;
            |  }
            |  assert(sum == 3);
            |}
            |@fun interface RepeatActor(i: Int): Void;
            |let repeat(times: Int, act: RepeatActor): Void {
            |  for (var i = 0; i < times; ++i) {
            |    act(i);
            |  }
            |}
        """.trimMargin(),
        rust = """
            |pub (crate) fn init() -> temper_core::Result<()> {
            |    static INIT_ONCE: std::sync::OnceLock<temper_core::Result<()>> = std::sync::OnceLock::new();
            |    INIT_ONCE.get_or_init(| |{
            |            Ok(())
            |    }).clone()
            |}
            |fn repeat__0(times__0: i32, act__0: std::sync::Arc<dyn Fn (i32) + std::marker::Send + std::marker::Sync>) {
            |    let mut i__0: i32 = 0;
            |    'loop___0: while i__0 < times__0 {
            |        act__0(i__0);
            |        i__0 = i__0.wrapping_add(1);
            |    }
            |}
            |#[cfg(test)]
            |mod tests {
            |    #[test]
            |    fn main__0() -> temper_core::Result<()> {
            |        crate::init(None);
            |        temper_std::init(None);
            |        let test___0 = temper_std::testing::Test::new();
            |        let return__0: ();
            |        return__0 = ();
            |        let mut sum__0: std::sync::Arc<std::sync::RwLock<i32>> = std::sync::Arc::new(std::sync::RwLock::new(0));
            |        #[derive(Clone)]
            |        struct ClosureGroup___0 {
            |            sum__0: std::sync::Arc<std::sync::RwLock<i32>>
            |        }
            |        impl ClosureGroup___0 {
            |            fn fn__0(& self, i__1: i32) {
            |                {
            |                    * self.sum__0.write().unwrap() = temper_core::read_locked( & self.sum__0).wrapping_add(i__1);
            |                }
            |            }
            |        }
            |        let closure_group = ClosureGroup___0 {
            |            sum__0: sum__0.clone()
            |        };
            |        let fn__0 = {
            |            let closure_group = closure_group.clone();
            |            std::sync::Arc::new(move | i__1: i32 | closure_group.fn__0(i__1))
            |        };
            |        repeat__0(3, fn__0.clone());
            |        let actual___0: i32 = temper_core::read_locked( & sum__0);
            |        #[derive(Clone)]
            |        struct ClosureGroup___1 {
            |            actual___0: i32
            |        }
            |        impl ClosureGroup___1 {
            |            fn fn__1(& self) -> std::sync::Arc<String> {
            |                return std::sync::Arc::new(format!("expected sum == ({}) not ({})", 3, self.actual___0));
            |            }
            |        }
            |        let closure_group = ClosureGroup___1 {
            |            actual___0
            |        };
            |        let fn__1 = {
            |            let closure_group = closure_group.clone();
            |            std::sync::Arc::new(move | | closure_group.fn__1())
            |        };
            |        test___0.assert(Some(actual___0) == Some(3), fn__1.clone());
            |        return Ok(return__0);
            |        test___0.soft_fail_to_hard()
            |    }
            |    use super::*;
            |}
        """.trimMargin(),
    )

    @Test
    fun castGeneric() = assertGenerateWanted(
        temper = """
            |let things(n: Int): List<Int> throws Bubble {
            |  [n]
            |}
            |export let other(n: Int): Listed<Int> {
            |  things(n) orelse panic()
            |}
        """.trimMargin(),
        rust = """
            |pub (crate) fn init() -> temper_core::Result<()> {
            |    static INIT_ONCE: std::sync::OnceLock<temper_core::Result<()>> = std::sync::OnceLock::new();
            |    INIT_ONCE.get_or_init(| |{
            |            Ok(())
            |    }).clone()
            |}
            |fn things__0(n__0: i32) -> temper_core::Result<temper_core::List<i32>> {
            |    let return__0: temper_core::List<i32>;
            |    return__0 = std::sync::Arc::new(vec![n__0]);
            |    return Ok( & return__0);
            |}
            |pub fn other(n__1: i32) -> temper_core::Listed<i32> {
            |    let mut return__1: temper_core::Listed<i32>;
            |    'ok___0: {
            |        'orelse___0: {
            |            let return___0: temper_core::Result<temper_core::List<i32>> = Ok(things__0(n__1));
            |            if ! return___0.is_ok() {
            |                break 'orelse___0;
            |            }
            |            return__1 = temper_core::ToListed::to_listed(return___0.unwrap());
            |            break 'ok___0;
            |        }
            |        return temper_core::cast::<temper_core::Listed<i32>>(panic!()).unwrap();
            |    }
            |    return return__1.clone();
            |}
        """.trimMargin(),
    )

    @Test
    fun closure() = assertGenerateWanted(
        temper = $$"""
            |let callIt(i: Int, f: fn (Int): Int): Int {
            |  3 * f(i)
            |}
            |let enclose(i: Int): Void {
            |  var k = i;
            |  let result = callIt(2) { j: Int =>
            |    k += 1;
            |    i + j
            |  };
            |  console.log("${result} and ${k}");
            |}
            |enclose(1);
        """.trimMargin(),
        rust = """
            |pub (crate) fn init() -> temper_core::Result<()> {
            |    static INIT_ONCE: std::sync::OnceLock<temper_core::Result<()>> = std::sync::OnceLock::new();
            |    INIT_ONCE.get_or_init(| |{
            |            enclose__0(1);
            |            Ok(())
            |    }).clone()
            |}
            |fn callIt__0(i__0: i32, f__0: std::sync::Arc<dyn Fn (i32) -> i32 + std::marker::Send + std::marker::Sync>) -> i32 {
            |    return (3 as i32).wrapping_mul(f__0(i__0));
            |}
            |fn enclose__0(i__1: i32) {
            |    let mut k__0: std::sync::Arc<std::sync::RwLock<i32>> = std::sync::Arc::new(std::sync::RwLock::new(i__1));
            |    #[derive(Clone)]
            |    struct ClosureGroup___0 {
            |        k__0: std::sync::Arc<std::sync::RwLock<i32>>, i__1: i32
            |    }
            |    impl ClosureGroup___0 {
            |        fn fn__0(& self, j__0: i32) -> i32 {
            |            {
            |                * self.k__0.write().unwrap() = temper_core::read_locked( & self.k__0).wrapping_add(1);
            |            }
            |            return self.i__1.wrapping_add(j__0);
            |        }
            |    }
            |    let closure_group = ClosureGroup___0 {
            |        k__0: k__0.clone(), i__1
            |    };
            |    let fn__0 = {
            |        let closure_group = closure_group.clone();
            |        std::sync::Arc::new(move | j__0: i32 | closure_group.fn__0(j__0))
            |    };
            |    let result__0: i32 = callIt__0(2, fn__0.clone());
            |    println!("{} and {}", result__0, temper_core::read_locked( & k__0));
            |}
        """.trimMargin(),
    )

    @Test
    fun floatComparisons() = assertGenerateWanted(
        temper = """
            |export let f(a: Float64, b: Float64): Boolean {
            |  a != b && a < b + 1.0
            |}
        """.trimMargin(),
        rust = """
            |pub (crate) fn init() -> temper_core::Result<()> {
            |    static INIT_ONCE: std::sync::OnceLock<temper_core::Result<()>> = std::sync::OnceLock::new();
            |    INIT_ONCE.get_or_init(| |{
            |            Ok(())
            |    }).clone()
            |}
            |pub fn f(a__0: f64, b__0: f64) -> bool {
            |    if temper_core::float64::cmp_option(Some(a__0), Some(b__0)) != 0 {
            |        return temper_core::float64::cmp(a__0, b__0 + 1.0f64) < 0;
            |    } else {
            |        return false;
            |    }
            |}
        """.trimMargin(),
    )

    @Test
    fun funArgWrong() = assertGenerateWanted(
        temper = """
            |@fun interface Handler(): Void;
            |class Hub {
            |  private handlers: ListBuilder<Handler> = new ListBuilder();
            |  public onAction(handler: Handler): Void {
            |    handlers.add(handler);
            |  }
            |}
        """.trimMargin(),
        rust = """
            |pub (crate) fn init() -> temper_core::Result<()> {
            |    static INIT_ONCE: std::sync::OnceLock<temper_core::Result<()>> = std::sync::OnceLock::new();
            |    INIT_ONCE.get_or_init(| |{
            |            Ok(())
            |    }).clone()
            |}
            |struct HubStruct {
            |    handlers: temper_core::ListBuilder<std::sync::Arc<dyn Fn () + std::marker::Send + std::marker::Sync>>
            |}
            |#[derive(Clone)]
            |pub (crate) struct Hub(std::sync::Arc<HubStruct>);
            |impl Hub {
            |    pub fn on_action(& self, handler__0: std::sync::Arc<dyn Fn () + std::marker::Send + std::marker::Sync>) {
            |        temper_core::listed::add( & self.0.handlers, handler__0.clone(), None);
            |    }
            |    pub fn new() -> Hub {
            |        let handlers;
            |        let t___0: temper_core::ListBuilder<std::sync::Arc<dyn Fn () + std::marker::Send + std::marker::Sync>> = temper_core::listed::new_builder();
            |        handlers = t___0.clone();
            |        let selfish = Hub(std::sync::Arc::new(HubStruct {
            |                    handlers
            |        }));
            |        return selfish;
            |    }
            |}
            |temper_core::impl_any_value_trait!(Hub, []);
        """.trimMargin(),
    )

    @Test
    fun bubblyAndUnBubblyFunctionValues() = assertGenerateWanted(
        temper = """
            |let passes(f: fn (): Void throws Bubble): Boolean {
            |  var passed = true;
            |  f() orelse do { passed = false; }
            |  passed
            |}
            |let doIt(f: fn (): Void): Boolean {
            |  f()
            |  true
            |}
        """.trimMargin(),
        rust = """
            |pub (crate) fn init() -> temper_core::Result<()> {
            |    static INIT_ONCE: std::sync::OnceLock<temper_core::Result<()>> = std::sync::OnceLock::new();
            |    INIT_ONCE.get_or_init(| |{
            |            Ok(())
            |    }).clone()
            |}
            |fn passes__0(f__0: std::sync::Arc<dyn Fn () -> temper_core::Result<()> + std::marker::Send + std::marker::Sync>) -> bool {
            |    let mut passed__0: bool = true;
            |    'ok___0: {
            |        'orelse___0: {
            |            let result___0: temper_core::Result<()> = Ok(f__0());
            |            if ! result___0.is_ok() {
            |                break 'orelse___0;
            |            }
            |            break 'ok___0;
            |        }
            |        passed__0 = false;
            |    }
            |    return passed__0;
            |}
            |fn doIt__0(f__1: std::sync::Arc<dyn Fn () + std::marker::Send + std::marker::Sync>) -> bool {
            |    f__1();
            |    return true;
            |}
        """.trimMargin(),
    )

    @Test
    fun captureOfTest() = assertGenerateWanted(
        temper = """
            |test("sure") {
            |  let nums = [0, 1];
            |  nums.forEach { i =>
            |    assert(i < nums.length);
            |  }
            |}
        """.trimMargin(),
        rust = """
            |pub (crate) fn init() -> temper_core::Result<()> {
            |    static INIT_ONCE: std::sync::OnceLock<temper_core::Result<()>> = std::sync::OnceLock::new();
            |    INIT_ONCE.get_or_init(| |{
            |            Ok(())
            |    }).clone()
            |}
            |#[cfg(test)]
            |mod tests {
            |    #[test]
            |    fn sure__0() -> temper_core::Result<()> {
            |        crate::init(None);
            |        temper_std::init(None);
            |        let test___0 = temper_std::testing::Test::new();
            |        let return__0: ();
            |        let nums__0: temper_core::List<i32> = std::sync::Arc::new(vec![0, 1]);
            |        let this__0: temper_core::List<i32> = nums__0.clone();
            |        let n__0: i32 = temper_core::ListedTrait::len( & this__0);
            |        let mut i__0: i32 = 0;
            |        'loop___0: while i__0 < n__0 {
            |            let el__0: i32 = temper_core::ListedTrait::get( & this__0, i__0);
            |            i__0 = i__0.wrapping_add(1);
            |            let i__1: i32 = el__0;
            |            #[derive(Clone)]
            |            struct ClosureGroup___0 {}
            |            impl ClosureGroup___0 {
            |                fn fn__0(& self) -> std::sync::Arc<String> {
            |                    return std::sync::Arc::new("expected i < nums.length".to_string());
            |                }
            |            }
            |            let closure_group = ClosureGroup___0 {};
            |            let fn__0 = {
            |                let closure_group = closure_group.clone();
            |                std::sync::Arc::new(move | | closure_group.fn__0())
            |            };
            |            test___0.assert(i__1 < temper_core::ListedTrait::len( & nums__0), fn__0.clone());
            |        }
            |        return__0 = ();
            |        return Ok(return__0);
            |        test___0.soft_fail_to_hard()
            |    }
            |    use super::*;
            |}
        """.trimMargin(),
    )

    @Test
    fun coreUpcast() = assertGenerateWanted(
        temper = """
            |// These are translated well.
            |let handyThings = List.of<AnyValue>(1, 2);
            |let thing: AnyValue = 1;
            |// TODO Those below have a variety of issues.
            |let things = [1, 2] as List<AnyValue>; // making a list of ints but expect list of AnyValue
            |let more = [1 as AnyValue, 2 as AnyValue]; // cast elided in frontend?
            |let still: List<AnyValue> = [1, 2]; // again list of ints treated as list of AnyValue
            |let yet = [1, "two"];
            |let yetAgain: List<MapKey> = yet; // MapKey only supported for constraint, not yet explicit value type
        """.trimMargin(),
        // TODO Some would be fixed by changes recommended in `TyperTest.typeContextWinsOverInsidesButNotYet` comments.
        // TODO But especially trying to `ok_or_else` with no Result-producing code probably needs some RustTranslator
        // TODO treatment.
        rust = """
            |pub (crate) fn init() -> temper_core::Result<()> {
            |    static INIT_ONCE: std::sync::OnceLock<temper_core::Result<()>> = std::sync::OnceLock::new();
            |    INIT_ONCE.get_or_init(| |{
            |            let handyThings__0: temper_core::List<temper_core::AnyValue> = std::sync::Arc::new(vec![1.as_any_value(), 2.as_any_value()]);
            |            let thing__0: temper_core::AnyValue = 1.as_any_value();
            |            let things___0: temper_core::Result<temper_core::List<temper_core::AnyValue>> = Ok(std::sync::Arc::new(vec![1, 2]).unwrap());
            |            if ! things___0.is_ok() {
            |                return Err(temper_core::Error::new());
            |            }
            |            let things__0: temper_core::List<temper_core::AnyValue> = things___0.unwrap();
            |            let more__0: temper_core::List<i32> = std::sync::Arc::new(vec![1, 2]);
            |            let still__0: temper_core::List<temper_core::AnyValue> = std::sync::Arc::new(vec![1, 2]);
            |            let yet__0: temper_core::List<temper_core::MapKey> = std::sync::Arc::new(vec![temper_core::MapKey::new(1), temper_core::MapKey::new(std::sync::Arc::new("two".to_string()))]);
            |            let yetAgain__0: temper_core::List<temper_core::MapKey> = yet__0.clone();
            |            Ok(())
            |    }).clone()
            |}
        """.trimMargin(),
    )

    @Test
    fun downcast() {
        assertGenerateWanted(
            // Down-casting in be-rust originally used sealed enums.
            // No need for sealed now, but keep it for still seeing the effect.
            temper = """
                |export sealed interface Apple {}
                |export class Banana extends Apple {}
                |export class Carrot extends Apple {}
                |let thing: Apple = new Banana();
                |let maybe: Apple? = thing;
                |let nope = thing as Carrot;
                |let alsoNope = maybe as Carrot;
            """.trimMargin(),
            rust = """
                |pub (crate) fn init() -> temper_core::Result<()> {
                |    static INIT_ONCE: std::sync::OnceLock<temper_core::Result<()>> = std::sync::OnceLock::new();
                |    INIT_ONCE.get_or_init(| |{
                |            let thing__0: Apple = Apple::new(Banana::new());
                |            let maybe__0: Option<Apple> = Some(thing__0.clone());
                |            let nope___0: temper_core::Result<Carrot> = Ok(temper_core::cast::<Carrot>(thing__0.clone()).unwrap());
                |            if ! nope___0.is_ok() {
                |                return Err(temper_core::Error::new());
                |            }
                |            let nope__0: Carrot = nope___0.unwrap();
                |            let alsoNope__0: Carrot;
                |            if maybe__0.is_none() {
                |                return Err(temper_core::Error::new());
                |            } else {
                |                let alsoNope___0: temper_core::Result<Carrot> = Ok(maybe__0.clone().and_then(| x | temper_core::cast::<Carrot>(x)).unwrap());
                |                if ! alsoNope___0.is_ok() {
                |                    return Err(temper_core::Error::new());
                |                }
                |                alsoNope__0 = alsoNope___0.unwrap();
                |            }
                |            Ok(())
                |    }).clone()
                |}
                |pub enum AppleEnum {
                |    Banana(Banana), Carrot(Carrot)
                |}
                |pub trait AppleTrait: temper_core::AsAnyValue + temper_core::AnyValueTrait + std::marker::Send + std::marker::Sync {
                |    fn as_enum(& self) -> AppleEnum;
                |    fn clone_boxed(& self) -> Apple;
                |}
                |#[derive(Clone)]
                |pub struct Apple(std::sync::Arc<dyn AppleTrait>);
                |impl Apple {
                |    pub fn new(selfish: impl AppleTrait + 'static) -> Apple {
                |        Apple(std::sync::Arc::new(selfish))
                |    }
                |}
                |impl AppleTrait for Apple {
                |    fn as_enum(& self) -> AppleEnum {
                |        AppleTrait::as_enum( & ( * self.0))
                |    }
                |    fn clone_boxed(& self) -> Apple {
                |        AppleTrait::clone_boxed( & ( * self.0))
                |    }
                |}
                |temper_core::impl_any_value_trait_for_interface!(Apple);
                |impl std::ops::Deref for Apple {
                |    type Target = dyn AppleTrait;
                |    fn deref(& self) -> & Self::Target {
                |        & ( * self.0)
                |    }
                |}
                |struct BananaStruct {}
                |#[derive(Clone)]
                |pub struct Banana(std::sync::Arc<BananaStruct>);
                |impl Banana {
                |    pub fn new() -> Banana {
                |        let selfish = Banana(std::sync::Arc::new(BananaStruct {}));
                |        return selfish;
                |    }
                |}
                |impl AppleTrait for Banana {
                |    fn as_enum(& self) -> AppleEnum {
                |        AppleEnum::Banana(self.clone())
                |    }
                |    fn clone_boxed(& self) -> Apple {
                |        Apple::new(self.clone())
                |    }
                |}
                |temper_core::impl_any_value_trait!(Banana, [Apple]);
                |struct CarrotStruct {}
                |#[derive(Clone)]
                |pub struct Carrot(std::sync::Arc<CarrotStruct>);
                |impl Carrot {
                |    pub fn new() -> Carrot {
                |        let selfish = Carrot(std::sync::Arc::new(CarrotStruct {}));
                |        return selfish;
                |    }
                |}
                |impl AppleTrait for Carrot {
                |    fn as_enum(& self) -> AppleEnum {
                |        AppleEnum::Carrot(self.clone())
                |    }
                |    fn clone_boxed(& self) -> Apple {
                |        Apple::new(self.clone())
                |    }
                |}
                |temper_core::impl_any_value_trait!(Carrot, [Apple]);
            """.trimMargin(),
        )
    }

    @Test
    fun downcastAsserted() {
        assertGenerateWanted(
            temper = """
                |class Blah {}
                |public let something(value: AnyValue): Blah {
                |  when (value) {
                |    is Blah -> value;
                |    else -> new Blah();
                |  }
                |}
            """.trimMargin(),
            rust = """
                |pub (crate) fn init() -> temper_core::Result<()> {
                |    static INIT_ONCE: std::sync::OnceLock<temper_core::Result<()>> = std::sync::OnceLock::new();
                |    INIT_ONCE.get_or_init(| |{
                |            Ok(())
                |    }).clone()
                |}
                |struct BlahStruct {}
                |#[derive(Clone)]
                |pub (crate) struct Blah(std::sync::Arc<BlahStruct>);
                |impl Blah {
                |    pub fn new() -> Blah {
                |        let selfish = Blah(std::sync::Arc::new(BlahStruct {}));
                |        return selfish;
                |    }
                |}
                |temper_core::impl_any_value_trait!(Blah, []);
                |fn something__0(value__0: temper_core::AnyValue) -> Blah {
                |    let t___0: bool;
                |    if ! value__0.is_none() {
                |        t___0 = temper_core::is::<Blah>(value__0.clone());
                |    } else {
                |        t___0 = false;
                |    }
                |    if t___0 {
                |        if value__0.is_none() {
                |            return panic!();
                |        } else {
                |            return temper_core::cast::<Blah>(value__0.clone()).unwrap();
                |        }
                |    } else {
                |        return Blah::new();
                |    }
                |}
            """.trimMargin(),
        )
    }

    @Test
    fun empty() {
        assertGenerateWanted(listOf())
    }

    @Test
    fun fib() {
        assertGenerateWanted(
            temper = $$"""
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
                |var zero = 0;
                |zero = zero;
                |console.log("fib(10)=${ fib(zero + 10).toString() }");
            """.trimMargin(),
            rust = """
                |pub (crate) fn init() -> temper_core::Result<()> {
                |    static INIT_ONCE: std::sync::OnceLock<temper_core::Result<()>> = std::sync::OnceLock::new();
                |    INIT_ONCE.get_or_init(| |{
                |            let mut zero__0: i32 = 0;
                |            zero__0 = zero__0;
                |            println!("fib(10)={}", fib(zero__0.wrapping_add(10)));
                |            Ok(())
                |    }).clone()
                |}
                |pub fn fib(mut i__0: i32) -> i32 {
                |    let mut a__0: i32 = 0;
                |    let mut b__0: i32 = 1;
                |    'loop___0: while i__0 > 0 {
                |        let c__0: i32 = a__0.wrapping_add(b__0);
                |        a__0 = b__0;
                |        b__0 = c__0;
                |        i__0 = i__0.wrapping_sub(1);
                |    }
                |    return a__0;
                |}
            """.trimMargin(),
        )
    }

    @Test
    fun fileLayoutSkipLevelsIncludingTop() = assertGeneratedFileTree(
        inputs = inputFileMapFromJson(
            """
                |{
                |  bar: {
                |    baz.temper: ```
                |      console.log("Baz")
                |      ```,
                |    boo.temper: ```
                |      console.log("Boo")
                |      ```,
                |  },
                |  bob: {
                |    bill: {
                |      beth: {
                |        barry.temper: ```
                |          console.log("Barry")
                |          ```
                |      },
                |    },
                |  },
                |}
            """.trimMargin(),
        ),
        want = """
            |{
            |  rust: {
            |    my-test-library: {
            |      Cargo.toml: "__DO_NOT_CARE__",
            |      src: {
            |        lib.rs: {
            |          content: ```
            |            #![allow(warnings)]
            |            #![allow(dependency_on_unit_never_type_fallback)]
            |            pub mod bar;
            |            pub mod bob;
            |            mod support;
            |            pub (crate) use support::*;
            |            pub fn init(config: Option<temper_core::Config>) -> temper_core::Result<temper_core::AsyncRunner> {
            |                crate::CONFIG.get_or_init(| | config.unwrap_or_else(| | temper_core::Config::default()));
            |                bar::init() ? ;
            |                bob::bill::beth::init() ? ;
            |                Ok(crate::config().runner().clone())
            |            }
            |
            |            ```
            |        },
            |        lib.rs.map: "__DO_NOT_CARE__",
            |        main.rs: "__DO_NOT_CARE__",
            |        bar: {
            |          mod.rs: {
            |            content: ```
            |              #![allow(warnings)]
            |              #![allow(dependency_on_unit_never_type_fallback)]
            |              use temper_core::AnyValueTrait;
            |              use temper_core::AsAnyValue;
            |              use temper_core::Pair;
            |              pub (crate) fn init() -> temper_core::Result<()> {
            |                  static INIT_ONCE: std::sync::OnceLock<temper_core::Result<()>> = std::sync::OnceLock::new();
            |                  INIT_ONCE.get_or_init(| |{
            |                          println!("{}", "Baz");
            |                          println!("{}", "Boo");
            |                          Ok(())
            |                  }).clone()
            |              }
            |
            |              ```
            |          },
            |          mod.rs.map: "__DO_NOT_CARE__",
            |        },
            |        "bob": {
            |          "mod.rs": {
            |            content: ```
            |              pub mod bill;
            |
            |              ```,
            |          },
            |          "mod.rs.map": "__DO_NOT_CARE__",
            |          "bill": {
            |            "mod.rs": {
            |              content: ```
            |                pub mod beth;
            |
            |                ```,
            |            },
            |            "mod.rs.map": "__DO_NOT_CARE__",
            |            "beth": {
            |              "mod.rs": {
            |                content: ```
            |                  #![allow(warnings)]
            |                  #![allow(dependency_on_unit_never_type_fallback)]
            |                  use temper_core::AnyValueTrait;
            |                  use temper_core::AsAnyValue;
            |                  use temper_core::Pair;
            |                  pub (crate) fn init() -> temper_core::Result<()> {
            |                      static INIT_ONCE: std::sync::OnceLock<temper_core::Result<()>> = std::sync::OnceLock::new();
            |                      INIT_ONCE.get_or_init(| |{
            |                              println!("{}", "Barry");
            |                              Ok(())
            |                      }).clone()
            |                  }
            |
            |                  ```,
            |              },
            |              "mod.rs.map": "__DO_NOT_CARE__",
            |            }
            |          }
            |        },
            |        $SUPPORT_FILES_DO_NOT_CARE
            |      }
            |    }
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun helloWorldObject() {
        assertGenerateWanted(
            // All constructor params are required.
            temper = $$"""
                |export class C(
                |  public x: String,
                |  public y: String,
                |) {
                |  public var z: String = "${this.x}${y}";
                |  // Be ridiculous to ensure we can handle it.
                |  public w: String = do { let v = z; z = "hi"; v };
                |  public get v(): String { "ciao" }
                |  // And see what methods also do when referencing properties.
                |  public get u(): String { "${x}${y}" }
                |  public echo(input: String): Void {
                |    console.log(input);
                |  }
                |}
                |let c = new C("Hello", "World");
                |console.log("${c.x}, ${c.y}!")
                |c.echo("Hello World, again!");
            """.trimMargin(),
            rust = """
                |pub (crate) fn init() -> temper_core::Result<()> {
                |    static INIT_ONCE: std::sync::OnceLock<temper_core::Result<()>> = std::sync::OnceLock::new();
                |    INIT_ONCE.get_or_init(| |{
                |            let c__0: C = C::new("Hello", "World");
                |            println!("{}, {}!", c__0.x(), c__0.y());
                |            c__0.echo("Hello World, again!");
                |            Ok(())
                |    }).clone()
                |}
                |struct CStruct {
                |    x: std::sync::Arc<String>, y: std::sync::Arc<String>, z: std::sync::Arc<String>, w: std::sync::Arc<String>
                |}
                |#[derive(Clone)]
                |pub struct C(std::sync::Arc<std::sync::RwLock<CStruct>>);
                |impl C {
                |    pub fn v(& self) -> std::sync::Arc<String> {
                |        return std::sync::Arc::new("ciao".to_string());
                |    }
                |    pub fn u(& self) -> std::sync::Arc<String> {
                |        return std::sync::Arc::new(format!("{}{}", self.0.read().unwrap().x, self.0.read().unwrap().y.clone()));
                |    }
                |    pub fn echo(& self, input__0: impl temper_core::ToArcString) {
                |        let input__0 = input__0.to_arc_string();
                |        println!("{}", input__0.clone());
                |    }
                |    pub fn new(x__0: impl temper_core::ToArcString, y__0: impl temper_core::ToArcString) -> C {
                |        let x__0 = x__0.to_arc_string();
                |        let y__0 = y__0.to_arc_string();
                |        let x;
                |        let y;
                |        let mut z;
                |        let w;
                |        x = x__0.clone();
                |        let t___0: std::sync::Arc<String> = y__0.clone();
                |        y = t___0.clone();
                |        let t___1: std::sync::Arc<String> = std::sync::Arc::new(format!("{}{}", x, y__0.clone()));
                |        z = t___1.clone();
                |        let v__0: std::sync::Arc<String> = z.clone();
                |        z = std::sync::Arc::new("hi".to_string());
                |        w = v__0.clone();
                |        let selfish = C(std::sync::Arc::new(std::sync::RwLock::new(CStruct {
                |                        x, y, z, w
                |        })));
                |        return selfish;
                |    }
                |    pub fn x(& self) -> std::sync::Arc<String> {
                |        return self.0.read().unwrap().x.clone();
                |    }
                |    pub fn y(& self) -> std::sync::Arc<String> {
                |        return self.0.read().unwrap().y.clone();
                |    }
                |    pub fn z(& self) -> std::sync::Arc<String> {
                |        return self.0.read().unwrap().z.clone();
                |    }
                |    pub fn set_z(& self, newZ__0: impl temper_core::ToArcString) {
                |        let newZ__0 = newZ__0.to_arc_string();
                |        self.0.write().unwrap().z = newZ__0.clone();
                |    }
                |    pub fn w(& self) -> std::sync::Arc<String> {
                |        return self.0.read().unwrap().w.clone();
                |    }
                |}
                |temper_core::impl_any_value_trait!(C, []);
                |pub mod builders {
                |    #[derive(Clone)]
                |    pub struct CBuilder {
                |        pub x: std::sync::Arc<String>, pub y: std::sync::Arc<String>
                |    }
                |    impl CBuilder {
                |        pub fn build(self) -> C {
                |            C::new(self.x, self.y)
                |        }
                |    }
                |    use super::*;
                |}
            """.trimMargin(),
        )
    }

    @Test
    fun genericConstraints() {
        assertGenerateWanted(
            temper = """
                |interface A {
                |  // Use different ways of declaring props.
                |  public var prop: String;
                |  public get thing(): String;
                |  public set thing(that: String): Void;
                |  public greeting(): String { "Hi!" }
                |  public whatever(): String;
                |}
                |interface B<T extends A> extends A {
                |  // No greeting here.
                |  public whatever(): String { "blah" }
                |}
                |class C<T extends A>(
                |  public var prop: String,
                |  public var thing: String,
                |) extends B<T> {
                |  public greeting(): String { "Ha!" }
                |  public spawn(): C<B<A>> { new C<B<A>>("", "") }
                |}
                |// D provides alternate paths for override resolution.
                |interface D<T> extends A {
                |  public get prop(): String { "Hello!" }
                |  public set prop(value: String): Void {}
                |  public set thing(value: String): Void {}
                |  public whatever(): String { "sure" }
                |}
                |// E provides indirection on type bindings to D.
                |interface E<T> extends D<T> {}
                |// Alternate prop/thing set/get vs D above.
                |class F extends B<C<A>> & E<Int> {
                |  public get thing(): String { "Hello!" }
                |}
            """.trimMargin(),
            rust = """
                |pub (crate) fn init() -> temper_core::Result<()> {
                |    static INIT_ONCE: std::sync::OnceLock<temper_core::Result<()>> = std::sync::OnceLock::new();
                |    INIT_ONCE.get_or_init(| |{
                |            Ok(())
                |    }).clone()
                |}
                |trait ATrait: temper_core::AsAnyValue + temper_core::AnyValueTrait + std::marker::Send + std::marker::Sync {
                |    fn clone_boxed(& self) -> A;
                |    fn prop(& self) -> std::sync::Arc<String>;
                |    fn set_prop(& self, newprop___0: std::sync::Arc<String>);
                |    fn thing(& self) -> std::sync::Arc<String>;
                |    fn set_thing(& self, that__0: std::sync::Arc<String>);
                |    fn greeting(& self) -> std::sync::Arc<String> {
                |        return std::sync::Arc::new("Hi!".to_string());
                |    }
                |    fn whatever(& self) -> std::sync::Arc<String>;
                |}
                |#[derive(Clone)]
                |struct A(std::sync::Arc<dyn ATrait>);
                |impl A {
                |    pub fn new(selfish: impl ATrait + 'static) -> A {
                |        A(std::sync::Arc::new(selfish))
                |    }
                |}
                |impl ATrait for A {
                |    fn clone_boxed(& self) -> A {
                |        ATrait::clone_boxed( & ( * self.0))
                |    }
                |    fn thing(& self) -> std::sync::Arc<String> {
                |        ATrait::thing( & ( * self.0))
                |    }
                |    fn set_thing(& self, value: std::sync::Arc<String>) {
                |        ATrait::set_thing( & ( * self.0), value)
                |    }
                |    fn greeting(& self) -> std::sync::Arc<String> {
                |        ATrait::greeting( & ( * self.0))
                |    }
                |    fn whatever(& self) -> std::sync::Arc<String> {
                |        ATrait::whatever( & ( * self.0))
                |    }
                |    fn prop(& self) -> std::sync::Arc<String> {
                |        ATrait::prop( & ( * self.0))
                |    }
                |    fn set_prop(& self, value: std::sync::Arc<String>) {
                |        ATrait::set_prop( & ( * self.0), value)
                |    }
                |}
                |temper_core::impl_any_value_trait_for_interface!(A);
                |impl std::ops::Deref for A {
                |    type Target = dyn ATrait;
                |    fn deref(& self) -> & Self::Target {
                |        & ( * self.0)
                |    }
                |}
                |trait BTrait<T: ATrait + Clone + std::marker::Send + std::marker::Sync + 'static>: temper_core::AsAnyValue + temper_core::AnyValueTrait + std::marker::Send + std::marker::Sync + ATrait {
                |    fn clone_boxed(& self) -> B<T>;
                |    fn whatever(& self) -> std::sync::Arc<String> {
                |        return std::sync::Arc::new("blah".to_string());
                |    }
                |}
                |#[derive(Clone)]
                |struct B<T: ATrait + Clone + std::marker::Send + std::marker::Sync + 'static>(std::sync::Arc<dyn BTrait<T>>);
                |impl<T: ATrait + Clone + std::marker::Send + std::marker::Sync + 'static> B<T> {
                |    pub fn new(selfish: impl BTrait<T> + 'static) -> B<T> {
                |        B(std::sync::Arc::new(selfish))
                |    }
                |}
                |impl<T: ATrait + Clone + std::marker::Send + std::marker::Sync + 'static> BTrait<T> for B<T> {
                |    fn clone_boxed(& self) -> B<T> {
                |        BTrait::clone_boxed( & ( * self.0))
                |    }
                |    fn whatever(& self) -> std::sync::Arc<String> {
                |        BTrait::whatever( & ( * self.0))
                |    }
                |}
                |impl<T: ATrait + Clone + std::marker::Send + std::marker::Sync + 'static> ATrait for B<T> {
                |    fn clone_boxed(& self) -> A {
                |        ATrait::clone_boxed( & ( * self.0))
                |    }
                |    fn thing(& self) -> std::sync::Arc<String> {
                |        ATrait::thing( & ( * self.0))
                |    }
                |    fn set_thing(& self, value: std::sync::Arc<String>) {
                |        ATrait::set_thing( & ( * self.0), value)
                |    }
                |    fn greeting(& self) -> std::sync::Arc<String> {
                |        ATrait::greeting( & ( * self.0))
                |    }
                |    fn whatever(& self) -> std::sync::Arc<String> {
                |        ATrait::whatever( & ( * self.0))
                |    }
                |    fn prop(& self) -> std::sync::Arc<String> {
                |        ATrait::prop( & ( * self.0))
                |    }
                |    fn set_prop(& self, value: std::sync::Arc<String>) {
                |        ATrait::set_prop( & ( * self.0), value)
                |    }
                |}
                |temper_core::impl_any_value_trait_for_interface!(B<T> where T: ATrait);
                |impl<T: ATrait + Clone + std::marker::Send + std::marker::Sync + 'static> std::ops::Deref for B<T> {
                |    type Target = dyn BTrait<T>;
                |    fn deref(& self) -> & Self::Target {
                |        & ( * self.0)
                |    }
                |}
                |struct CStruct<T: ATrait + Clone + std::marker::Send + std::marker::Sync + 'static> {
                |    prop: std::sync::Arc<String>, thing: std::sync::Arc<String>, phantom_T: std::marker::PhantomData<T>
                |}
                |#[derive(Clone)]
                |pub (crate) struct C<T: ATrait + Clone + std::marker::Send + std::marker::Sync + 'static>(std::sync::Arc<std::sync::RwLock<CStruct<T>>>);
                |impl<T: ATrait + Clone + std::marker::Send + std::marker::Sync + 'static> C<T> {
                |    pub fn greeting(& self) -> std::sync::Arc<String> {
                |        return std::sync::Arc::new("Ha!".to_string());
                |    }
                |    pub fn spawn(& self) -> C<B<A>> {
                |        return C::new("", "");
                |    }
                |    pub fn new(prop__0: impl temper_core::ToArcString, thing__0: impl temper_core::ToArcString) -> C<T> {
                |        let prop__0 = prop__0.to_arc_string();
                |        let thing__0 = thing__0.to_arc_string();
                |        let prop;
                |        let thing;
                |        prop = prop__0.clone();
                |        thing = thing__0.clone();
                |        let selfish = C(std::sync::Arc::new(std::sync::RwLock::new(CStruct {
                |                        prop, thing, phantom_T: std::marker::PhantomData
                |        })));
                |        return selfish;
                |    }
                |    pub fn prop(& self) -> std::sync::Arc<String> {
                |        return self.0.read().unwrap().prop.clone();
                |    }
                |    pub fn set_prop(& self, newProp__0: impl temper_core::ToArcString) {
                |        let newProp__0 = newProp__0.to_arc_string();
                |        self.0.write().unwrap().prop = newProp__0.clone();
                |    }
                |    pub fn thing(& self) -> std::sync::Arc<String> {
                |        return self.0.read().unwrap().thing.clone();
                |    }
                |    pub fn set_thing(& self, newThing__0: impl temper_core::ToArcString) {
                |        let newThing__0 = newThing__0.to_arc_string();
                |        self.0.write().unwrap().thing = newThing__0.clone();
                |    }
                |}
                |impl<T: ATrait + Clone + std::marker::Send + std::marker::Sync + 'static> BTrait<T> for C<T> {
                |    fn clone_boxed(& self) -> B<T> {
                |        B::new(self.clone())
                |    }
                |}
                |impl<T: ATrait + Clone + std::marker::Send + std::marker::Sync + 'static> ATrait for C<T> {
                |    fn clone_boxed(& self) -> A {
                |        A::new(self.clone())
                |    }
                |    fn thing(& self) -> std::sync::Arc<String> {
                |        self.thing()
                |    }
                |    fn set_thing(& self, newThing__0: std::sync::Arc<String>) {
                |        self.set_thing(newThing__0)
                |    }
                |    fn greeting(& self) -> std::sync::Arc<String> {
                |        self.greeting()
                |    }
                |    fn whatever(& self) -> std::sync::Arc<String> {
                |        BTrait::whatever(self)
                |    }
                |    fn prop(& self) -> std::sync::Arc<String> {
                |        self.prop()
                |    }
                |    fn set_prop(& self, newProp__0: std::sync::Arc<String>) {
                |        self.set_prop(newProp__0)
                |    }
                |}
                |temper_core::impl_any_value_trait!(C<T>, [B<T>, A] where T: ATrait);
                |trait DTrait<T: Clone + std::marker::Send + std::marker::Sync + 'static>: temper_core::AsAnyValue + temper_core::AnyValueTrait + std::marker::Send + std::marker::Sync + ATrait {
                |    fn clone_boxed(& self) -> D<T>;
                |    fn prop(& self) -> std::sync::Arc<String> {
                |        return std::sync::Arc::new("Hello!".to_string());
                |    }
                |    fn set_prop(& self, value__0: std::sync::Arc<String>) {}
                |    fn thing(& self) -> std::sync::Arc<String>;
                |    fn set_thing(& self, value__1: std::sync::Arc<String>) {}
                |    fn whatever(& self) -> std::sync::Arc<String> {
                |        return std::sync::Arc::new("sure".to_string());
                |    }
                |}
                |#[derive(Clone)]
                |struct D<T: Clone + std::marker::Send + std::marker::Sync + 'static>(std::sync::Arc<dyn DTrait<T>>);
                |impl<T: Clone + std::marker::Send + std::marker::Sync + 'static> D<T> {
                |    pub fn new(selfish: impl DTrait<T> + 'static) -> D<T> {
                |        D(std::sync::Arc::new(selfish))
                |    }
                |}
                |impl<T: Clone + std::marker::Send + std::marker::Sync + 'static> DTrait<T> for D<T> {
                |    fn clone_boxed(& self) -> D<T> {
                |        DTrait::clone_boxed( & ( * self.0))
                |    }
                |    fn prop(& self) -> std::sync::Arc<String> {
                |        DTrait::prop( & ( * self.0))
                |    }
                |    fn set_prop(& self, value: std::sync::Arc<String>) {
                |        DTrait::set_prop( & ( * self.0), value)
                |    }
                |    fn set_thing(& self, value: std::sync::Arc<String>) {
                |        DTrait::set_thing( & ( * self.0), value)
                |    }
                |    fn whatever(& self) -> std::sync::Arc<String> {
                |        DTrait::whatever( & ( * self.0))
                |    }
                |    fn thing(& self) -> std::sync::Arc<String> {
                |        DTrait::thing( & ( * self.0))
                |    }
                |}
                |impl<T: Clone + std::marker::Send + std::marker::Sync + 'static> ATrait for D<T> {
                |    fn clone_boxed(& self) -> A {
                |        ATrait::clone_boxed( & ( * self.0))
                |    }
                |    fn thing(& self) -> std::sync::Arc<String> {
                |        ATrait::thing( & ( * self.0))
                |    }
                |    fn set_thing(& self, value: std::sync::Arc<String>) {
                |        ATrait::set_thing( & ( * self.0), value)
                |    }
                |    fn greeting(& self) -> std::sync::Arc<String> {
                |        ATrait::greeting( & ( * self.0))
                |    }
                |    fn whatever(& self) -> std::sync::Arc<String> {
                |        ATrait::whatever( & ( * self.0))
                |    }
                |    fn prop(& self) -> std::sync::Arc<String> {
                |        ATrait::prop( & ( * self.0))
                |    }
                |    fn set_prop(& self, value: std::sync::Arc<String>) {
                |        ATrait::set_prop( & ( * self.0), value)
                |    }
                |}
                |temper_core::impl_any_value_trait_for_interface!(D<T>);
                |impl<T: Clone + std::marker::Send + std::marker::Sync + 'static> std::ops::Deref for D<T> {
                |    type Target = dyn DTrait<T>;
                |    fn deref(& self) -> & Self::Target {
                |        & ( * self.0)
                |    }
                |}
                |trait ETrait<T: Clone + std::marker::Send + std::marker::Sync + 'static>: temper_core::AsAnyValue + temper_core::AnyValueTrait + std::marker::Send + std::marker::Sync + DTrait<T> {
                |    fn clone_boxed(& self) -> E<T>;
                |}
                |#[derive(Clone)]
                |struct E<T: Clone + std::marker::Send + std::marker::Sync + 'static>(std::sync::Arc<dyn ETrait<T>>);
                |impl<T: Clone + std::marker::Send + std::marker::Sync + 'static> E<T> {
                |    pub fn new(selfish: impl ETrait<T> + 'static) -> E<T> {
                |        E(std::sync::Arc::new(selfish))
                |    }
                |}
                |impl<T: Clone + std::marker::Send + std::marker::Sync + 'static> ETrait<T> for E<T> {
                |    fn clone_boxed(& self) -> E<T> {
                |        ETrait::clone_boxed( & ( * self.0))
                |    }
                |}
                |impl<T: Clone + std::marker::Send + std::marker::Sync + 'static> DTrait<T> for E<T> {
                |    fn clone_boxed(& self) -> D<T> {
                |        DTrait::clone_boxed( & ( * self.0))
                |    }
                |    fn prop(& self) -> std::sync::Arc<String> {
                |        DTrait::prop( & ( * self.0))
                |    }
                |    fn set_prop(& self, value: std::sync::Arc<String>) {
                |        DTrait::set_prop( & ( * self.0), value)
                |    }
                |    fn set_thing(& self, value: std::sync::Arc<String>) {
                |        DTrait::set_thing( & ( * self.0), value)
                |    }
                |    fn whatever(& self) -> std::sync::Arc<String> {
                |        DTrait::whatever( & ( * self.0))
                |    }
                |    fn thing(& self) -> std::sync::Arc<String> {
                |        DTrait::thing( & ( * self.0))
                |    }
                |}
                |impl<T: Clone + std::marker::Send + std::marker::Sync + 'static> ATrait for E<T> {
                |    fn clone_boxed(& self) -> A {
                |        ATrait::clone_boxed( & ( * self.0))
                |    }
                |    fn thing(& self) -> std::sync::Arc<String> {
                |        ATrait::thing( & ( * self.0))
                |    }
                |    fn set_thing(& self, value: std::sync::Arc<String>) {
                |        ATrait::set_thing( & ( * self.0), value)
                |    }
                |    fn greeting(& self) -> std::sync::Arc<String> {
                |        ATrait::greeting( & ( * self.0))
                |    }
                |    fn whatever(& self) -> std::sync::Arc<String> {
                |        ATrait::whatever( & ( * self.0))
                |    }
                |    fn prop(& self) -> std::sync::Arc<String> {
                |        ATrait::prop( & ( * self.0))
                |    }
                |    fn set_prop(& self, value: std::sync::Arc<String>) {
                |        ATrait::set_prop( & ( * self.0), value)
                |    }
                |}
                |temper_core::impl_any_value_trait_for_interface!(E<T>);
                |impl<T: Clone + std::marker::Send + std::marker::Sync + 'static> std::ops::Deref for E<T> {
                |    type Target = dyn ETrait<T>;
                |    fn deref(& self) -> & Self::Target {
                |        & ( * self.0)
                |    }
                |}
                |struct FStruct {}
                |#[derive(Clone)]
                |pub (crate) struct F(std::sync::Arc<FStruct>);
                |impl F {
                |    pub fn thing(& self) -> std::sync::Arc<String> {
                |        return std::sync::Arc::new("Hello!".to_string());
                |    }
                |    pub fn new() -> F {
                |        let selfish = F(std::sync::Arc::new(FStruct {}));
                |        return selfish;
                |    }
                |    fn whatever(& self) -> std::sync::Arc<String> {
                |        BTrait::whatever(self)
                |    }
                |    fn greeting(& self) -> std::sync::Arc<String> {
                |        ATrait::greeting(self)
                |    }
                |}
                |impl BTrait<C<A>> for F {
                |    fn clone_boxed(& self) -> B<C<A>> {
                |        B::new(self.clone())
                |    }
                |}
                |impl ATrait for F {
                |    fn clone_boxed(& self) -> A {
                |        A::new(self.clone())
                |    }
                |    fn thing(& self) -> std::sync::Arc<String> {
                |        self.thing()
                |    }
                |    fn set_thing(& self, value: std::sync::Arc<String>) {
                |        DTrait::set_thing(self, value)
                |    }
                |    fn whatever(& self) -> std::sync::Arc<String> {
                |        BTrait::whatever(self)
                |    }
                |    fn prop(& self) -> std::sync::Arc<String> {
                |        DTrait::prop(self)
                |    }
                |    fn set_prop(& self, value: std::sync::Arc<String>) {
                |        DTrait::set_prop(self, value)
                |    }
                |}
                |impl ETrait<i32> for F {
                |    fn clone_boxed(& self) -> E<i32> {
                |        E::new(self.clone())
                |    }
                |}
                |impl DTrait<T> for F {
                |    fn clone_boxed(& self) -> D<T> {
                |        D::new(self.clone())
                |    }
                |    fn whatever(& self) -> std::sync::Arc<String> {
                |        BTrait::whatever(self)
                |    }
                |    fn thing(& self) -> std::sync::Arc<String> {
                |        self.thing()
                |    }
                |}
                |temper_core::impl_any_value_trait!(F, [B<C<A>>, A, E<i32>, D<T>]);
            """.trimMargin(),
        )
    }

    @Test
    fun genericEnum() {
        assertGenerateWanted(
            temper = """
                |export interface I {}
                |export sealed interface Hi<T extends I> {}
                |export class Lo<T extends I> extends Hi<T> {}
                |// Piggyback a subtypeless sealed interface with a non-sealed supertype.
                |export sealed interface AllAlone extends I {}
            """.trimMargin(),
            rust = """
                |pub (crate) fn init() -> temper_core::Result<()> {
                |    static INIT_ONCE: std::sync::OnceLock<temper_core::Result<()>> = std::sync::OnceLock::new();
                |    INIT_ONCE.get_or_init(| |{
                |            Ok(())
                |    }).clone()
                |}
                |pub trait ITrait: temper_core::AsAnyValue + temper_core::AnyValueTrait + std::marker::Send + std::marker::Sync {
                |    fn clone_boxed(& self) -> I;
                |}
                |#[derive(Clone)]
                |pub struct I(std::sync::Arc<dyn ITrait>);
                |impl I {
                |    pub fn new(selfish: impl ITrait + 'static) -> I {
                |        I(std::sync::Arc::new(selfish))
                |    }
                |}
                |impl ITrait for I {
                |    fn clone_boxed(& self) -> I {
                |        ITrait::clone_boxed( & ( * self.0))
                |    }
                |}
                |temper_core::impl_any_value_trait_for_interface!(I);
                |impl std::ops::Deref for I {
                |    type Target = dyn ITrait;
                |    fn deref(& self) -> & Self::Target {
                |        & ( * self.0)
                |    }
                |}
                |pub enum HiEnum<T: ITrait + Clone + std::marker::Send + std::marker::Sync + 'static> {
                |    Lo(Lo<T>)
                |}
                |pub trait HiTrait<T: ITrait + Clone + std::marker::Send + std::marker::Sync + 'static>: temper_core::AsAnyValue + temper_core::AnyValueTrait + std::marker::Send + std::marker::Sync {
                |    fn as_enum(& self) -> HiEnum<T>;
                |    fn clone_boxed(& self) -> Hi<T>;
                |}
                |#[derive(Clone)]
                |pub struct Hi<T: ITrait + Clone + std::marker::Send + std::marker::Sync + 'static>(std::sync::Arc<dyn HiTrait<T>>);
                |impl<T: ITrait + Clone + std::marker::Send + std::marker::Sync + 'static> Hi<T> {
                |    pub fn new(selfish: impl HiTrait<T> + 'static) -> Hi<T> {
                |        Hi(std::sync::Arc::new(selfish))
                |    }
                |}
                |impl<T: ITrait + Clone + std::marker::Send + std::marker::Sync + 'static> HiTrait<T> for Hi<T> {
                |    fn as_enum(& self) -> HiEnum<T> {
                |        HiTrait::as_enum( & ( * self.0))
                |    }
                |    fn clone_boxed(& self) -> Hi<T> {
                |        HiTrait::clone_boxed( & ( * self.0))
                |    }
                |}
                |temper_core::impl_any_value_trait_for_interface!(Hi<T> where T: ITrait);
                |impl<T: ITrait + Clone + std::marker::Send + std::marker::Sync + 'static> std::ops::Deref for Hi<T> {
                |    type Target = dyn HiTrait<T>;
                |    fn deref(& self) -> & Self::Target {
                |        & ( * self.0)
                |    }
                |}
                |struct LoStruct<T: ITrait + Clone + std::marker::Send + std::marker::Sync + 'static> {
                |    phantom_T: std::marker::PhantomData<T>
                |}
                |#[derive(Clone)]
                |pub struct Lo<T: ITrait + Clone + std::marker::Send + std::marker::Sync + 'static>(std::sync::Arc<LoStruct<T>>);
                |impl<T: ITrait + Clone + std::marker::Send + std::marker::Sync + 'static> Lo<T> {
                |    pub fn new() -> Lo<T> {
                |        let selfish = Lo(std::sync::Arc::new(LoStruct {
                |                    phantom_T: std::marker::PhantomData
                |        }));
                |        return selfish;
                |    }
                |}
                |impl<T: ITrait + Clone + std::marker::Send + std::marker::Sync + 'static> HiTrait<T> for Lo<T> {
                |    fn as_enum(& self) -> HiEnum<T> {
                |        HiEnum::Lo(self.clone())
                |    }
                |    fn clone_boxed(& self) -> Hi<T> {
                |        Hi::new(self.clone())
                |    }
                |}
                |temper_core::impl_any_value_trait!(Lo<T>, [Hi<T>] where T: ITrait);
                |pub enum AllAloneEnum {}
                |pub trait AllAloneTrait: temper_core::AsAnyValue + temper_core::AnyValueTrait + std::marker::Send + std::marker::Sync + ITrait {
                |    fn as_enum(& self) -> AllAloneEnum;
                |    fn clone_boxed(& self) -> AllAlone;
                |}
                |#[derive(Clone)]
                |pub struct AllAlone(std::sync::Arc<dyn AllAloneTrait>);
                |impl AllAlone {
                |    pub fn new(selfish: impl AllAloneTrait + 'static) -> AllAlone {
                |        AllAlone(std::sync::Arc::new(selfish))
                |    }
                |}
                |impl AllAloneTrait for AllAlone {
                |    fn as_enum(& self) -> AllAloneEnum {
                |        AllAloneTrait::as_enum( & ( * self.0))
                |    }
                |    fn clone_boxed(& self) -> AllAlone {
                |        AllAloneTrait::clone_boxed( & ( * self.0))
                |    }
                |}
                |impl ITrait for AllAlone {
                |    fn clone_boxed(& self) -> I {
                |        ITrait::clone_boxed( & ( * self.0))
                |    }
                |}
                |temper_core::impl_any_value_trait_for_interface!(AllAlone);
                |impl std::ops::Deref for AllAlone {
                |    type Target = dyn AllAloneTrait;
                |    fn deref(& self) -> & Self::Target {
                |        & ( * self.0)
                |    }
                |}
            """.trimMargin(),
        )
    }

    @Test
    fun needlesslyGenericBuilder() {
        assertGenerateWanted(
            temper = """
                |export class Ha<T>(public i: Int, public j: Int) {}
            """.trimMargin(),
            rust = """
                |pub (crate) fn init() -> temper_core::Result<()> {
                |    static INIT_ONCE: std::sync::OnceLock<temper_core::Result<()>> = std::sync::OnceLock::new();
                |    INIT_ONCE.get_or_init(| |{
                |            Ok(())
                |    }).clone()
                |}
                |struct HaStruct<T: Clone + std::marker::Send + std::marker::Sync + 'static> {
                |    i: i32, j: i32, phantom_T: std::marker::PhantomData<T>
                |}
                |#[derive(Clone)]
                |pub struct Ha<T: Clone + std::marker::Send + std::marker::Sync + 'static>(std::sync::Arc<HaStruct<T>>);
                |impl<T: Clone + std::marker::Send + std::marker::Sync + 'static> Ha<T> {
                |    pub fn new(i__0: i32, j__0: i32) -> Ha<T> {
                |        let i;
                |        let j;
                |        i = i__0;
                |        j = j__0;
                |        let selfish = Ha(std::sync::Arc::new(HaStruct {
                |                    i, j, phantom_T: std::marker::PhantomData
                |        }));
                |        return selfish;
                |    }
                |    pub fn i(& self) -> i32 {
                |        return self.0.i;
                |    }
                |    pub fn j(& self) -> i32 {
                |        return self.0.j;
                |    }
                |}
                |temper_core::impl_any_value_trait!(Ha<T>, []);
                |pub mod builders {
                |    #[derive(Clone)]
                |    pub struct HaBuilder {
                |        pub i: i32, pub j: i32
                |    }
                |    impl HaBuilder {
                |        pub fn build<T: Clone + std::marker::Send + std::marker::Sync + 'static>(self) -> Ha<T> {
                |            Ha::new(self.i, self.j)
                |        }
                |    }
                |    use super::*;
                |}
            """.trimMargin(),
        )
    }

    @Test
    fun genericStruct() {
        assertGenerateWanted(
            // Both required and optional constructor params here.
            temper = """
                |export class Hi<T, U extends MapKey>(
                |  public t: T?,
                |  private u: U,
                |  public i: Int = 42,
                |) {}
            """.trimMargin(),
            rust = """
                |pub (crate) fn init() -> temper_core::Result<()> {
                |    static INIT_ONCE: std::sync::OnceLock<temper_core::Result<()>> = std::sync::OnceLock::new();
                |    INIT_ONCE.get_or_init(| |{
                |            Ok(())
                |    }).clone()
                |}
                |struct HiStruct<T: Clone + std::marker::Send + std::marker::Sync + 'static, U: std::cmp::Eq + std::hash::Hash + Clone + std::marker::Send + std::marker::Sync + 'static> {
                |    t: Option<T>, u: U, i: i32, phantom_T: std::marker::PhantomData<T>, phantom_U: std::marker::PhantomData<U>
                |}
                |#[derive(Clone)]
                |pub struct Hi<T: Clone + std::marker::Send + std::marker::Sync + 'static, U: std::cmp::Eq + std::hash::Hash + Clone + std::marker::Send + std::marker::Sync + 'static>(std::sync::Arc<HiStruct<T, U>>);
                |impl<T: Clone + std::marker::Send + std::marker::Sync + 'static, U: std::cmp::Eq + std::hash::Hash + Clone + std::marker::Send + std::marker::Sync + 'static> Hi<T, U> {
                |    pub fn new(t__0: Option<T>, u__0: U, i__0: Option<i32>) -> Hi<T, U> {
                |        let t;
                |        let u;
                |        let i;
                |        let i__1: i32;
                |        if i__0.is_none() {
                |            i__1 = 42;
                |        } else {
                |            i__1 = i__0.unwrap();
                |        }
                |        t = t__0.clone();
                |        u = u__0.clone();
                |        i = i__1;
                |        let selfish = Hi(std::sync::Arc::new(HiStruct {
                |                    t, u, i, phantom_T: std::marker::PhantomData, phantom_U: std::marker::PhantomData
                |        }));
                |        return selfish;
                |    }
                |    pub fn t(& self) -> Option<T> {
                |        return self.0.t.clone();
                |    }
                |    pub fn i(& self) -> i32 {
                |        return self.0.i;
                |    }
                |}
                |temper_core::impl_any_value_trait!(Hi<T, U>, [] where U: std::cmp::Eq + std::hash::Hash);
                |pub mod builders {
                |    #[derive(Clone)]
                |    pub struct HiBuilder<T: Clone + std::marker::Send + std::marker::Sync + 'static, U: std::cmp::Eq + std::hash::Hash + Clone + std::marker::Send + std::marker::Sync + 'static> {
                |        pub t: Option<T>, pub u: U
                |    }
                |    #[derive(Clone)]
                |    pub struct HiOptions<T: Clone + std::marker::Send + std::marker::Sync + 'static, U: std::cmp::Eq + std::hash::Hash + Clone + std::marker::Send + std::marker::Sync + 'static> {
                |        selfish: HiBuilder<T, U>, i: Option<i32>
                |    }
                |    impl<T: Clone + std::marker::Send + std::marker::Sync + 'static, U: std::cmp::Eq + std::hash::Hash + Clone + std::marker::Send + std::marker::Sync + 'static> HiOptions<T, U> {
                |        pub fn new(selfish: HiBuilder<T, U>) -> Self {
                |            Self {
                |                selfish, i: None
                |            }
                |        }
                |        pub fn i(mut self, i: i32) -> Self {
                |            self.i = Some(i);
                |            self
                |        }
                |        pub fn build(self) -> Hi<T, U> {
                |            Hi::new(self.selfish.t, self.selfish.u, self.i)
                |        }
                |    }
                |    impl<T: Clone + std::marker::Send + std::marker::Sync + 'static, U: std::cmp::Eq + std::hash::Hash + Clone + std::marker::Send + std::marker::Sync + 'static> HiBuilder<T, U> {
                |        pub fn build(self) -> Hi<T, U> {
                |            self.options().build()
                |        }
                |        pub fn options(self) -> HiOptions<T, U> {
                |            HiOptions::new(self)
                |        }
                |    }
                |    use super::*;
                |}
            """.trimMargin(),
        )
    }

    @Test
    fun imports() = assertGenerateWanted(
        listOf(
            ModuleInfo(
                name = "exporter",
                temper = $$"""
                    |export let sayHiTo(name: String): Void {
                    |  console.log("Hi, ${name}!");
                    |}
                """.trimMargin(),
                rust = """
                    |pub (crate) fn init() -> temper_core::Result<()> {
                    |    static INIT_ONCE: std::sync::OnceLock<temper_core::Result<()>> = std::sync::OnceLock::new();
                    |    INIT_ONCE.get_or_init(| |{
                    |            Ok(())
                    |    }).clone()
                    |}
                    |pub fn say_hi_to(name__0: impl temper_core::ToArcString) {
                    |    let name__0 = name__0.to_arc_string();
                    |    println!("Hi, {}!", name__0.clone());
                    |}
                """.trimMargin(),
            ),
            ModuleInfo(
                name = "importer",
                temper = """
                    |let { sayHiTo } = import("../exporter");
                    |sayHiTo("World");
                """.trimMargin(),
                rust = """
                    |pub (crate) fn init() -> temper_core::Result<()> {
                    |    static INIT_ONCE: std::sync::OnceLock<temper_core::Result<()>> = std::sync::OnceLock::new();
                    |    INIT_ONCE.get_or_init(| |{
                    |            crate::exporter::say_hi_to("World");
                    |            Ok(())
                    |    }).clone()
                    |}
                """.trimMargin(),
            ),
        ),
    )

    @Test
    fun list() = assertGenerateWanted(
        temper = """
            |f([2, 3]);
            |let f(vals: List<Int>): Void {
            |  console.log(if (vals.isEmpty) { "empty" } else { "not empty" });
            |}
        """.trimMargin(),
        // Including `f__0([2, 3])` in our tested gen here ensures we keep conversion helpers available to others.
        rust = """
            |pub (crate) fn init() -> temper_core::Result<()> {
            |    static INIT_ONCE: std::sync::OnceLock<temper_core::Result<()>> = std::sync::OnceLock::new();
            |    INIT_ONCE.get_or_init(| |{
            |            f__0([2, 3]);
            |            Ok(())
            |    }).clone()
            |}
            |fn f__0(vals__0: impl temper_core::ToList<i32>) {
            |    let vals__0 = vals__0.to_list();
            |    let t___0: std::sync::Arc<String>;
            |    if temper_core::ListedTrait::is_empty( & vals__0) {
            |        t___0 = std::sync::Arc::new("empty".to_string());
            |    } else {
            |        t___0 = std::sync::Arc::new("not empty".to_string());
            |    }
            |    println!("{}", t___0.clone());
            |}
        """.trimMargin(),
    )

    @Test
    fun loopJumping() = assertGenerateWanted(
        temper = """
            |let a(n: Int, nums: List<Int>): Void {
            |  // This one currently makes a continue.
            |  outer: while (true) {
            |    for (var j = 0; j < n; j += 1) {
            |      continue outer;
            |    }
            |    break;
            |  }
            |  // And this one tries to unlabeled break past a label.
            |  // But that's illegal in Rust, including for `continue`, so we have to force labels.
            |  more: for (var i = 0; i < nums.length; i += 1) {
            |    for (var j = 0; j < n; j += 1) {
            |      continue more;
            |    }
            |  }
            |}
        """.trimMargin(),
        rust = """
            |pub (crate) fn init() -> temper_core::Result<()> {
            |    static INIT_ONCE: std::sync::OnceLock<temper_core::Result<()>> = std::sync::OnceLock::new();
            |    INIT_ONCE.get_or_init(| |{
            |            Ok(())
            |    }).clone()
            |}
            |fn a__0(n__0: i32, nums__0: impl temper_core::ToList<i32>) {
            |    let nums__0 = nums__0.to_list();
            |    'outer__0: loop {
            |        let mut j__0: i32 = 0;
            |        'loop___0: while j__0 < n__0 {
            |            continue 'outer__0;
            |        }
            |        break;
            |    }
            |    let mut i__0: i32 = 0;
            |    'loop___1: while i__0 < temper_core::ListedTrait::len( & nums__0) {
            |        let mut j__1: i32 = 0;
            |        'loop___2: while j__1 < n__0 {
            |            break;
            |        }
            |        i__0 = i__0.wrapping_add(1);
            |    }
            |}
        """.trimMargin(),
    )

    @Test
    fun mutualRecursion() {
        assertGenerateWanted(
            temper = """
                |// Purposely use camelCase names here, because we had a problem with that.
                |export let partOne(i: Int): Int {
                |  if (i < 0) {
                |    partTwo(i)
                |  } else {
                |    i
                |  }
                |}
                |export let partTwo(i: Int): Int {
                |  partOne(i + 1)
                |}
                |// Now do static method versions, and yes these would recurse infinitely, but meh.
                |// This also checks type name references, which are lower risk right now, but might as well.
                |class PartThree {
                |  public static partFour(i: Int): Int { PartFive.partSix(i) }
                |}
                |class PartFive {
                |  public static partSix(i: Int): Int { PartThree.partFour(i) }
                |}
            """.trimMargin(),
            rust = """
                |pub (crate) fn init() -> temper_core::Result<()> {
                |    static INIT_ONCE: std::sync::OnceLock<temper_core::Result<()>> = std::sync::OnceLock::new();
                |    INIT_ONCE.get_or_init(| |{
                |            Ok(())
                |    }).clone()
                |}
                |pub fn part_one(i__0: i32) -> i32 {
                |    if i__0 < 0 {
                |        return part_two(i__0);
                |    } else {
                |        return i__0;
                |    }
                |}
                |struct PartThreeStruct {}
                |#[derive(Clone)]
                |pub (crate) struct PartThree(std::sync::Arc<PartThreeStruct>);
                |impl PartThree {
                |    pub fn part_four(i__1: i32) -> i32 {
                |        return PartFive::part_six(i__1);
                |    }
                |    pub fn new() -> PartThree {
                |        let selfish = PartThree(std::sync::Arc::new(PartThreeStruct {}));
                |        return selfish;
                |    }
                |}
                |temper_core::impl_any_value_trait!(PartThree, []);
                |struct PartFiveStruct {}
                |#[derive(Clone)]
                |pub (crate) struct PartFive(std::sync::Arc<PartFiveStruct>);
                |impl PartFive {
                |    pub fn part_six(i__2: i32) -> i32 {
                |        return PartThree::part_four(i__2);
                |    }
                |    pub fn new() -> PartFive {
                |        let selfish = PartFive(std::sync::Arc::new(PartFiveStruct {}));
                |        return selfish;
                |    }
                |}
                |temper_core::impl_any_value_trait!(PartFive, []);
                |pub fn part_two(i__3: i32) -> i32 {
                |    return part_one(i__3.wrapping_add(1));
                |}
            """.trimMargin(),
        )
    }

    @Test
    fun nullFun() {
        assertGenerateWanted(
            temper = """
                |export let a: (fn(Int): Int)? = null;
            """.trimMargin(),
            rust = """
                |pub (crate) fn init() -> temper_core::Result<()> {
                |    static INIT_ONCE: std::sync::OnceLock<temper_core::Result<()>> = std::sync::OnceLock::new();
                |    INIT_ONCE.get_or_init(| |{
                |            A.set(None).unwrap_or_else(| _ | panic!());
                |            Ok(())
                |    }).clone()
                |}
                |static A: std::sync::OnceLock<Option<std::sync::Arc<dyn Fn (i32) -> i32 + std::marker::Send + std::marker::Sync>>> = std::sync::OnceLock::new();
                |pub fn a() -> Option<std::sync::Arc<dyn Fn (i32) -> i32 + std::marker::Send + std::marker::Sync>> {
                |    ( * A.get().unwrap()).clone()
                |}
            """.trimMargin(),
        )
    }

    @Test
    fun objectOptionals() = assertGenerateWanted(
        // All constructor params are optional.
        temper = """
            |export class Vec2(public x: Float64 = 0.0, public y: Float64 = 0.0) {}
        """.trimMargin(),
        rust = """
            |pub (crate) fn init() -> temper_core::Result<()> {
            |    static INIT_ONCE: std::sync::OnceLock<temper_core::Result<()>> = std::sync::OnceLock::new();
            |    INIT_ONCE.get_or_init(| |{
            |            Ok(())
            |    }).clone()
            |}
            |struct Vec2Struct {
            |    x: f64, y: f64
            |}
            |#[derive(Clone)]
            |pub struct Vec2(std::sync::Arc<Vec2Struct>);
            |impl Vec2 {
            |    pub fn new(x__0: Option<f64>, y__0: Option<f64>) -> Vec2 {
            |        let x;
            |        let y;
            |        let x__1: f64;
            |        if x__0.is_none() {
            |            x__1 = 0.0f64;
            |        } else {
            |            x__1 = x__0.unwrap();
            |        }
            |        let y__1: f64;
            |        if y__0.is_none() {
            |            y__1 = 0.0f64;
            |        } else {
            |            y__1 = y__0.unwrap();
            |        }
            |        x = x__1;
            |        y = y__1;
            |        let selfish = Vec2(std::sync::Arc::new(Vec2Struct {
            |                    x, y
            |        }));
            |        return selfish;
            |    }
            |    pub fn x(& self) -> f64 {
            |        return self.0.x;
            |    }
            |    pub fn y(& self) -> f64 {
            |        return self.0.y;
            |    }
            |}
            |temper_core::impl_any_value_trait!(Vec2, []);
            |pub mod builders {
            |    #[derive(Clone)]
            |    pub struct Vec2Builder {}
            |    #[derive(Clone)]
            |    pub struct Vec2Options {
            |        selfish: Vec2Builder, x: Option<f64>, y: Option<f64>
            |    }
            |    impl Vec2Options {
            |        pub fn new(selfish: Vec2Builder) -> Self {
            |            Self {
            |                selfish, x: None, y: None
            |            }
            |        }
            |        pub fn x(mut self, x: f64) -> Self {
            |            self.x = Some(x);
            |            self
            |        }
            |        pub fn y(mut self, y: f64) -> Self {
            |            self.y = Some(y);
            |            self
            |        }
            |        pub fn build(self) -> Vec2 {
            |            Vec2::new(self.x, self.y)
            |        }
            |    }
            |    impl Vec2Builder {
            |        pub fn build(self) -> Vec2 {
            |            self.options().build()
            |        }
            |        pub fn options(self) -> Vec2Options {
            |            Vec2Options::new(self)
            |        }
            |    }
            |    use super::*;
            |}
        """.trimMargin(),
    )

    @Test
    fun optional() = assertGenerateWanted(
        temper = $$"""
            |f();
            |let f(a: Int = 1, b: Int? = null): Void {
            |  let b = b as Int orelse 3;
            |  console.log("${a.toString()} ${b.toString()}");
            |}
        """.trimMargin(),
        rust = """
            |pub (crate) fn init() -> temper_core::Result<()> {
            |    static INIT_ONCE: std::sync::OnceLock<temper_core::Result<()>> = std::sync::OnceLock::new();
            |    INIT_ONCE.get_or_init(| |{
            |            f__0(None, None);
            |            Ok(())
            |    }).clone()
            |}
            |fn f__0(a__0: Option<i32>, b__0: Option<i32>) {
            |    let a__1: i32;
            |    if a__0.is_none() {
            |        a__1 = 1;
            |    } else {
            |        a__1 = a__0.unwrap();
            |    }
            |    let mut b__1: i32;
            |    'ok___0: {
            |        'orelse___0: {
            |            if b__0.is_none() {
            |                break 'orelse___0;
            |            } else {
            |                let result___0: temper_core::Result<i32> = Ok(b__0.unwrap());
            |                if ! result___0.is_ok() {
            |                    break 'orelse___0;
            |                }
            |                let t___0: i32 = result___0.unwrap();
            |                b__1 = t___0;
            |            }
            |            break 'ok___0;
            |        }
            |        b__1 = 3;
            |    }
            |    println!("{} {}", a__1, b__1);
            |}
        """.trimMargin(),
    )

    @Test
    fun nullConstructorInput() = assertGenerateWanted(
        temper = """
            |class Foo(public x: Foo?) {}
            |let emptyFoo = new Foo(null);
        """.trimMargin(),
        rust = """
            |pub (crate) fn init() -> temper_core::Result<()> {
            |    static INIT_ONCE: std::sync::OnceLock<temper_core::Result<()>> = std::sync::OnceLock::new();
            |    INIT_ONCE.get_or_init(| |{
            |            let emptyFoo__0: Foo = Foo::new(None);
            |            Ok(())
            |    }).clone()
            |}
            |struct FooStruct {
            |    x: Option<Foo>
            |}
            |#[derive(Clone)]
            |pub (crate) struct Foo(std::sync::Arc<FooStruct>);
            |impl Foo {
            |    pub fn new(x__0: Option<Foo>) -> Foo {
            |        let x;
            |        x = x__0.clone();
            |        let selfish = Foo(std::sync::Arc::new(FooStruct {
            |                    x
            |        }));
            |        return selfish;
            |    }
            |    pub fn x(& self) -> Option<Foo> {
            |        return self.0.x.clone();
            |    }
            |}
            |temper_core::impl_any_value_trait!(Foo, []);
        """.trimMargin(),
    )

    @Test
    fun pairKeyClone() = assertGenerateWanted(
        temper = """
            |let a(b: String, c: String): Pair<String, String> { new Pair(b, c) }
        """.trimMargin(),
        rust = """
            |pub (crate) fn init() -> temper_core::Result<()> {
            |    static INIT_ONCE: std::sync::OnceLock<temper_core::Result<()>> = std::sync::OnceLock::new();
            |    INIT_ONCE.get_or_init(| |{
            |            Ok(())
            |    }).clone()
            |}
            |fn a__0(b__0: impl temper_core::ToArcString, c__0: impl temper_core::ToArcString) ->(std::sync::Arc<String>, std::sync::Arc<String>) {
            |    let b__0 = b__0.to_arc_string();
            |    let c__0 = c__0.to_arc_string();
            |    return (b__0.clone(), c__0.clone());
            |}
        """.trimMargin(),
    )

    @Test
    fun restParam() = assertGenerateWanted(
        temper = """
            |hi(1, "a", "b");
            |export let hi(n: Int, ...things: List<String>): Int {
            |  n + things.length
            |}
        """.trimMargin(),
        rust = """
            |pub (crate) fn init() -> temper_core::Result<()> {
            |    static INIT_ONCE: std::sync::OnceLock<temper_core::Result<()>> = std::sync::OnceLock::new();
            |    INIT_ONCE.get_or_init(| |{
            |            hi(1, vec![std::sync::Arc::new("a".to_string()), std::sync::Arc::new("b".to_string())]);
            |            Ok(())
            |    }).clone()
            |}
            |pub fn hi(n__0: i32, things__0: impl temper_core::ToList<std::sync::Arc<String>>) -> i32 {
            |    let things__0 = things__0.to_list();
            |    return n__0.wrapping_add(temper_core::ListedTrait::len( & things__0));
            |}
        """.trimMargin(),
    )

    @Test
    fun staticProperty() = assertGenerateWanted(
        temper = """
            |console.log(Something.here);
            |export class Something {
            |  public static here = "there";
            |  public static hi = 5;
            |}
        """.trimMargin(),
        // TODO Optimize handling of Copy types here, like Int. No need for locks.
        rust = """
            |pub (crate) fn init() -> temper_core::Result<()> {
            |    static INIT_ONCE: std::sync::OnceLock<temper_core::Result<()>> = std::sync::OnceLock::new();
            |    INIT_ONCE.get_or_init(| |{
            |            SOMETHING__HERE.set(std::sync::Arc::new("there".to_string())).unwrap_or_else(| _ | panic!());
            |            SOMETHING__HI.set(5).unwrap_or_else(| _ | panic!());
            |            println!("{}", Something::here());
            |            Ok(())
            |    }).clone()
            |}
            |struct SomethingStruct {}
            |#[derive(Clone)]
            |pub struct Something(std::sync::Arc<SomethingStruct>);
            |static SOMETHING__HERE: std::sync::OnceLock<std::sync::Arc<String>> = std::sync::OnceLock::new();
            |static SOMETHING__HI: std::sync::OnceLock<i32> = std::sync::OnceLock::new();
            |impl Something {
            |    pub fn here() -> std::sync::Arc<String> {
            |        ( * SOMETHING__HERE.get().unwrap()).clone()
            |    }
            |    pub fn hi() -> i32 {
            |        * SOMETHING__HI.get().unwrap()
            |    }
            |    pub fn new() -> Something {
            |        let selfish = Something(std::sync::Arc::new(SomethingStruct {}));
            |        return selfish;
            |    }
            |}
            |temper_core::impl_any_value_trait!(Something, []);
        """.trimMargin(),
    )

    @Test
    fun stringIndexOption() = assertGenerateWanted(
        temper = """
            |export let something(maybe: StringIndexOption): Void {
            |  console.log((maybe is StringIndex).toString());
            |}
            |let nope = StringIndex.none;
            |something(nope);
        """.trimMargin(),
        rust = """
            |pub (crate) fn init() -> temper_core::Result<()> {
            |    static INIT_ONCE: std::sync::OnceLock<temper_core::Result<()>> = std::sync::OnceLock::new();
            |    INIT_ONCE.get_or_init(| |{
            |            let nope__0: () = ();
            |            something(None);
            |            Ok(())
            |    }).clone()
            |}
            |pub fn something(maybe__0: Option<usize>) {
            |    let t___0: bool = maybe__0.is_some();
            |    println!("{}", t___0);
            |}
        """.trimMargin(),
    )

    @Test
    fun superSetterless() = assertGenerateWanted(
        temper = """
            |export interface Hi {
            |  public get thing(): Int;
            |}
            |export class Lo(
            |  public var thing: Int,
            |) extends Hi {}
        """.trimMargin(),
        rust = """
            |pub (crate) fn init() -> temper_core::Result<()> {
            |    static INIT_ONCE: std::sync::OnceLock<temper_core::Result<()>> = std::sync::OnceLock::new();
            |    INIT_ONCE.get_or_init(| |{
            |            Ok(())
            |    }).clone()
            |}
            |pub trait HiTrait: temper_core::AsAnyValue + temper_core::AnyValueTrait + std::marker::Send + std::marker::Sync {
            |    fn clone_boxed(& self) -> Hi;
            |    fn thing(& self) -> i32;
            |}
            |#[derive(Clone)]
            |pub struct Hi(std::sync::Arc<dyn HiTrait>);
            |impl Hi {
            |    pub fn new(selfish: impl HiTrait + 'static) -> Hi {
            |        Hi(std::sync::Arc::new(selfish))
            |    }
            |}
            |impl HiTrait for Hi {
            |    fn clone_boxed(& self) -> Hi {
            |        HiTrait::clone_boxed( & ( * self.0))
            |    }
            |    fn thing(& self) -> i32 {
            |        HiTrait::thing( & ( * self.0))
            |    }
            |}
            |temper_core::impl_any_value_trait_for_interface!(Hi);
            |impl std::ops::Deref for Hi {
            |    type Target = dyn HiTrait;
            |    fn deref(& self) -> & Self::Target {
            |        & ( * self.0)
            |    }
            |}
            |struct LoStruct {
            |    thing: i32
            |}
            |#[derive(Clone)]
            |pub struct Lo(std::sync::Arc<std::sync::RwLock<LoStruct>>);
            |impl Lo {
            |    pub fn new(thing__0: i32) -> Lo {
            |        let thing;
            |        thing = thing__0;
            |        let selfish = Lo(std::sync::Arc::new(std::sync::RwLock::new(LoStruct {
            |                        thing
            |        })));
            |        return selfish;
            |    }
            |    pub fn thing(& self) -> i32 {
            |        return self.0.read().unwrap().thing;
            |    }
            |    pub fn set_thing(& self, newThing__0: i32) {
            |        self.0.write().unwrap().thing = newThing__0;
            |    }
            |}
            |impl HiTrait for Lo {
            |    fn clone_boxed(& self) -> Hi {
            |        Hi::new(self.clone())
            |    }
            |    fn thing(& self) -> i32 {
            |        self.thing()
            |    }
            |}
            |temper_core::impl_any_value_trait!(Lo, [Hi]);
        """.trimMargin(),
    )

    @Test
    fun topBubbly() = assertGenerateWanted(
        temper = """
            |export let incAwkwardly(i: Int): Int {
            |  i + j
            |}
            |let prepare(x: Float64): Int throws Bubble {
            |  console.log(x.toString());
            |  x.toInt32()
            |}
            |let j = prepare(1.5);
        """.trimMargin(),
        // TODO We currently fail when trying to bubble in a top-level.
        // TODO See: https://github.com/temperlang/temper/issues/191
        rust = """
            |pub (crate) fn init() -> temper_core::Result<()> {
            |    static INIT_ONCE: std::sync::OnceLock<temper_core::Result<()>> = std::sync::OnceLock::new();
            |    INIT_ONCE.get_or_init(| |{
            |            let j___0: temper_core::Result<i32> = Ok(prepare__0(1.5f64));
            |            if ! j___0.is_ok() {
            |                return Err(temper_core::Error::new());
            |            }
            |            J.set(j___0.unwrap()).unwrap_or_else(| _ | panic!());
            |            Ok(())
            |    }).clone()
            |}
            |static J: std::sync::OnceLock<i32> = std::sync::OnceLock::new();
            |fn j() -> i32 {
            |    * J.get().unwrap()
            |}
            |fn prepare__0(x__0: f64) -> temper_core::Result<i32> {
            |    let return__0: i32;
            |    println!("{}", temper_core::float64::to_string(x__0));
            |    let return___0: temper_core::Result<i32> = Ok(temper_core::float64::to_int(x__0));
            |    if ! return___0.is_ok() {
            |        return return___0;
            |    }
            |    return__0 = return___0.unwrap();
            |    return Ok(return__0);
            |}
            |pub fn inc_awkwardly(i__0: i32) -> i32 {
            |    return i__0.wrapping_add(j());
            |}
        """.trimMargin(),
    )

    @Test
    fun topVars() = assertGenerateWanted(
        temper = """
            |var a = 1;
            |hi();
            |export let hi(): Int {
            |  a += 1;
            |  a
            |}
        """.trimMargin(),
        rust = """
            |pub (crate) fn init() -> temper_core::Result<()> {
            |    static INIT_ONCE: std::sync::OnceLock<temper_core::Result<()>> = std::sync::OnceLock::new();
            |    INIT_ONCE.get_or_init(| |{
            |            {
            |                * A.write().unwrap() = Some(1);
            |            }
            |            hi();
            |            Ok(())
            |    }).clone()
            |}
            |static A: std::sync::RwLock<Option<i32>> = std::sync::RwLock::new(None);
            |fn a() -> i32 {
            |    A.read().unwrap().unwrap()
            |}
            |pub fn hi() -> i32 {
            |    {
            |        * A.write().unwrap() = Some(a().wrapping_add(1));
            |    }
            |    return a();
            |}
        """.trimMargin(),
    )

    @Test
    fun trait() {
        assertGenerateWanted(
            temper = $$"""
                |let a: A = new B();
                |let a2 = a;
                |// TODO Without contextual inference of `new B() as A`, these should be illegal variance.
                |let things: List<A> = [new B()];
                |let more = [new B()] as List<A>;
                |console.log(a2.adjust("hi"));
                |export interface A {
                |  public adjust(text: String): String;
                |}
                |export class B extends A {
                |  public adjust(text: String): String { "${text} there" }
                |  public greet(text: String): String {
                |    needy(this, text)
                |  }
                |}
                |export let needy(a: A, text: String): String {
                |  a.adjust(text)
                |}
            """.trimMargin(),
            // TODO Fix broken things below. Is that just the list things that might just be variance matters?
            rust = """
                |pub (crate) fn init() -> temper_core::Result<()> {
                |    static INIT_ONCE: std::sync::OnceLock<temper_core::Result<()>> = std::sync::OnceLock::new();
                |    INIT_ONCE.get_or_init(| |{
                |            let a__0: A = A::new(B::new());
                |            let a2__0: A = a__0.clone();
                |            let things__0: temper_core::List<A> = std::sync::Arc::new(vec![B::new()]);
                |            let more___0: temper_core::Result<temper_core::List<A>> = Ok(std::sync::Arc::new(vec![B::new()]).unwrap());
                |            if ! more___0.is_ok() {
                |                return Err(temper_core::Error::new());
                |            }
                |            let more__0: temper_core::List<A> = more___0.unwrap();
                |            println!("{}", a2__0.adjust(std::sync::Arc::new("hi".to_string())));
                |            Ok(())
                |    }).clone()
                |}
                |pub trait ATrait: temper_core::AsAnyValue + temper_core::AnyValueTrait + std::marker::Send + std::marker::Sync {
                |    fn clone_boxed(& self) -> A;
                |    fn adjust(& self, text__0: std::sync::Arc<String>) -> std::sync::Arc<String>;
                |}
                |#[derive(Clone)]
                |pub struct A(std::sync::Arc<dyn ATrait>);
                |impl A {
                |    pub fn new(selfish: impl ATrait + 'static) -> A {
                |        A(std::sync::Arc::new(selfish))
                |    }
                |}
                |impl ATrait for A {
                |    fn clone_boxed(& self) -> A {
                |        ATrait::clone_boxed( & ( * self.0))
                |    }
                |    fn adjust(& self, arg1: std::sync::Arc<String>) -> std::sync::Arc<String> {
                |        ATrait::adjust( & ( * self.0), arg1)
                |    }
                |}
                |temper_core::impl_any_value_trait_for_interface!(A);
                |impl std::ops::Deref for A {
                |    type Target = dyn ATrait;
                |    fn deref(& self) -> & Self::Target {
                |        & ( * self.0)
                |    }
                |}
                |struct BStruct {}
                |#[derive(Clone)]
                |pub struct B(std::sync::Arc<BStruct>);
                |impl B {
                |    pub fn adjust(& self, text__1: impl temper_core::ToArcString) -> std::sync::Arc<String> {
                |        let text__1 = text__1.to_arc_string();
                |        return std::sync::Arc::new(format!("{} there", text__1));
                |    }
                |    pub fn greet(& self, text__2: impl temper_core::ToArcString) -> std::sync::Arc<String> {
                |        let text__2 = text__2.to_arc_string();
                |        return needy(A::new(self.clone()), text__2.clone());
                |    }
                |    pub fn new() -> B {
                |        let selfish = B(std::sync::Arc::new(BStruct {}));
                |        return selfish;
                |    }
                |}
                |impl ATrait for B {
                |    fn clone_boxed(& self) -> A {
                |        A::new(self.clone())
                |    }
                |    fn adjust(& self, text__1: std::sync::Arc<String>) -> std::sync::Arc<String> {
                |        self.adjust(text__1)
                |    }
                |}
                |temper_core::impl_any_value_trait!(B, [A]);
                |pub fn needy(a__1: A, text__3: impl temper_core::ToArcString) -> std::sync::Arc<String> {
                |    let text__3 = text__3.to_arc_string();
                |    return a__1.adjust(text__3.clone());
                |}
            """.trimMargin(),
        )
    }

    @Test
    fun traitProps() {
        assertGenerateWanted(
            temper = """
                |export interface A {
                |  public get thing(): Int;
                |  public set thing(value: Int): Void;
                |  public get other(): Int;
                |}
                |export class B(
                |  public var thing: Int,
                |) extends A {
                |  public get other(): Int { 5 }
                |}
            """.trimMargin(),
            rust = """
                |pub (crate) fn init() -> temper_core::Result<()> {
                |    static INIT_ONCE: std::sync::OnceLock<temper_core::Result<()>> = std::sync::OnceLock::new();
                |    INIT_ONCE.get_or_init(| |{
                |            Ok(())
                |    }).clone()
                |}
                |pub trait ATrait: temper_core::AsAnyValue + temper_core::AnyValueTrait + std::marker::Send + std::marker::Sync {
                |    fn clone_boxed(& self) -> A;
                |    fn thing(& self) -> i32;
                |    fn set_thing(& self, value__0: i32);
                |    fn other(& self) -> i32;
                |}
                |#[derive(Clone)]
                |pub struct A(std::sync::Arc<dyn ATrait>);
                |impl A {
                |    pub fn new(selfish: impl ATrait + 'static) -> A {
                |        A(std::sync::Arc::new(selfish))
                |    }
                |}
                |impl ATrait for A {
                |    fn clone_boxed(& self) -> A {
                |        ATrait::clone_boxed( & ( * self.0))
                |    }
                |    fn thing(& self) -> i32 {
                |        ATrait::thing( & ( * self.0))
                |    }
                |    fn set_thing(& self, value: i32) {
                |        ATrait::set_thing( & ( * self.0), value)
                |    }
                |    fn other(& self) -> i32 {
                |        ATrait::other( & ( * self.0))
                |    }
                |}
                |temper_core::impl_any_value_trait_for_interface!(A);
                |impl std::ops::Deref for A {
                |    type Target = dyn ATrait;
                |    fn deref(& self) -> & Self::Target {
                |        & ( * self.0)
                |    }
                |}
                |struct BStruct {
                |    thing: i32
                |}
                |#[derive(Clone)]
                |pub struct B(std::sync::Arc<std::sync::RwLock<BStruct>>);
                |impl B {
                |    pub fn other(& self) -> i32 {
                |        return 5;
                |    }
                |    pub fn new(thing__0: i32) -> B {
                |        let thing;
                |        thing = thing__0;
                |        let selfish = B(std::sync::Arc::new(std::sync::RwLock::new(BStruct {
                |                        thing
                |        })));
                |        return selfish;
                |    }
                |    pub fn thing(& self) -> i32 {
                |        return self.0.read().unwrap().thing;
                |    }
                |    pub fn set_thing(& self, newThing__0: i32) {
                |        self.0.write().unwrap().thing = newThing__0;
                |    }
                |}
                |impl ATrait for B {
                |    fn clone_boxed(& self) -> A {
                |        A::new(self.clone())
                |    }
                |    fn thing(& self) -> i32 {
                |        self.thing()
                |    }
                |    fn set_thing(& self, newThing__0: i32) {
                |        self.set_thing(newThing__0)
                |    }
                |    fn other(& self) -> i32 {
                |        self.other()
                |    }
                |}
                |temper_core::impl_any_value_trait!(B, [A]);
            """.trimMargin(),
        )
    }

    @Test
    fun decodeHexTest() = assertGenerateWanted(
        temper = """
            |let decodeHexUnsigned(
            |  sourceText: String, start: StringIndex, limit: StringIndex
            |): Int {
            |  var n = 0;
            |  var i = start;
            |  while (i.compareTo(limit) < 0) {
            |    let cp = sourceText[i];
            |    let digit = if (char'0' <= cp && cp <= char'9') {
            |      cp - char'0'
            |    } else if (char'A' <= cp && cp <= char'F') {
            |      cp - char'A' + 10
            |    } else if (char'a' <= cp && cp <= char'f') {
            |      cp - char'a' + 10
            |    } else {
            |      return -1;
            |    }
            |    n = (n * 16) + digit;
            |    i = sourceText.next(i);
            |  }
            |  n
            |}
        """.trimMargin(),
        rust = """
            |pub (crate) fn init() -> temper_core::Result<()> {
            |    static INIT_ONCE: std::sync::OnceLock<temper_core::Result<()>> = std::sync::OnceLock::new();
            |    INIT_ONCE.get_or_init(| |{
            |            Ok(())
            |    }).clone()
            |}
            |fn decodeHexUnsigned__0(sourceText__0: impl temper_core::ToArcString, start__0: usize, limit__0: usize) -> i32 {
            |    let sourceText__0 = sourceText__0.to_arc_string();
            |    let return__0: i32;
            |    'fn__0: {
            |        let mut n__0: i32 = 0;
            |        let mut i__0: usize = start__0;
            |        'loop___0: while (Some(i__0).cmp( & Some(limit__0)) as i32) < 0 {
            |            let cp__0: i32 = temper_core::string::get( & sourceText__0, i__0);
            |            let digit__0: i32;
            |            let t___0: bool;
            |            if 48 <= cp__0 {
            |                t___0 = cp__0 <= 57;
            |            } else {
            |                t___0 = false;
            |            }
            |            if t___0 {
            |                digit__0 = cp__0.wrapping_sub(48);
            |            } else {
            |                let t___1: i32;
            |                let t___2: bool;
            |                if 65 <= cp__0 {
            |                    t___2 = cp__0 <= 70;
            |                } else {
            |                    t___2 = false;
            |                }
            |                if t___2 {
            |                    t___1 = cp__0.wrapping_sub(65).wrapping_add(10);
            |                } else {
            |                    let t___3: bool;
            |                    if 97 <= cp__0 {
            |                        t___3 = cp__0 <= 102;
            |                    } else {
            |                        t___3 = false;
            |                    }
            |                    if ! t___3 {
            |                        return__0 = -1;
            |                        break 'fn__0;
            |                    }
            |                    t___1 = cp__0.wrapping_sub(97).wrapping_add(10);
            |                }
            |                digit__0 = t___1;
            |            }
            |            n__0 = n__0.wrapping_mul(16).wrapping_add(digit__0);
            |            i__0 = temper_core::string::next( & sourceText__0, i__0);
            |        }
            |        return n__0;
            |    }
            |    return return__0;
            |}
        """.trimMargin(),
    )

    @Test
    fun stringIndexOptionCompareToStringIndexMinimal() = assertGenerateWanted(
        temper = """
            |export let notEmpty(start: StringIndex, limit: StringIndex): Boolean {
            |  start.compareTo(limit) < 0
            |}
        """.trimMargin(),
        rust = """
            |pub (crate) fn init() -> temper_core::Result<()> {
            |    static INIT_ONCE: std::sync::OnceLock<temper_core::Result<()>> = std::sync::OnceLock::new();
            |    INIT_ONCE.get_or_init(| |{
            |            Ok(())
            |    }).clone()
            |}
            |pub fn not_empty(start__0: usize, limit__0: usize) -> bool {
            |    return (Some(start__0).cmp( & Some(limit__0)) as i32) < 0;
            |}
        """.trimMargin(),
    )
}

private fun assertGenerateWanted(
    temper: String,
    rust: String,
    moduleName: String = "test",
) = assertGenerateWanted(listOf(ModuleInfo(name = moduleName, temper = temper, rust = rust)))

class ModuleInfo(
    val name: String,
    val temper: String,
    val rust: String,
)

private fun assertGenerateWanted(modules: List<ModuleInfo>) {
    val rusts = modules.joinToString("\n") { module ->
        """
            |                "${module.name}": {
            |                    "mod.rs": {
            |                        "content":
            |```
            |#![allow(warnings)]
            |#![allow(dependency_on_unit_never_type_fallback)]
            |use temper_core::AnyValueTrait;
            |use temper_core::AsAnyValue;
            |use temper_core::Pair;
            |${module.rust}
            |
            |```
            |                    },
            |                    "mod.rs.map": "__DO_NOT_CARE__",
            |                },
        """.trimMargin()
    }
    assertGeneratedFileTree(
        inputs = modules.map { filePath(it.name, "impl.temper") to it.temper },
        want = """
            |{
            |    "rust": {
            |        "my-test-library": {
            |            "Cargo.toml": "__DO_NOT_CARE__",
            |            "src": {
            |                "lib.rs": "__DO_NOT_CARE__",
            |                "lib.rs.map": "__DO_NOT_CARE__",
            |                "main.rs": "__DO_NOT_CARE__",
            |                $SUPPORT_FILES_DO_NOT_CARE
            |$rusts
            |            },
            |        },
            |    }
            |}
        """.trimMargin(),
    )
}

private fun assertGeneratedFileTree(
    inputs: List<Pair<FilePath, String>>,
    want: String,
) {
    assertGeneratedCode(
        inputs = inputs,
        want = want,
        factory = RustBackend.Factory,
        backendConfig = Backend.Config.production,
        moduleResultNeeded = false,
    )
}

private const val SUPPORT_FILES_DO_NOT_CARE = """
"support": {
    "mod.rs": "__DO_NOT_CARE__",
},
"""
