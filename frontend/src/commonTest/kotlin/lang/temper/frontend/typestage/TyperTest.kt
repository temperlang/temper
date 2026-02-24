@file:Suppress("MaxLineLength")

package lang.temper.frontend.typestage

import lang.temper.ast.TreeVisit
import lang.temper.builtin.BuiltinFuns
import lang.temper.common.Console
import lang.temper.common.ListBackedLogSink
import lang.temper.common.Log
import lang.temper.common.NonsenseGradient
import lang.temper.common.SnapshotKey
import lang.temper.common.Snapshotter
import lang.temper.common.assertStringsEqual
import lang.temper.common.console
import lang.temper.common.putMultiSet
import lang.temper.common.structure.Structured
import lang.temper.common.testCodeLocation
import lang.temper.common.testModuleName
import lang.temper.common.toStringViaBuilder
import lang.temper.common.toStringViaTextOutput
import lang.temper.format.ValueSimplifyingLogSink
import lang.temper.format.toStringViaTokenSink
import lang.temper.frontend.AstSnapshotKey
import lang.temper.frontend.DebugTreeRepresentation
import lang.temper.frontend.Module
import lang.temper.frontend.ModuleSource
import lang.temper.frontend.RandomBool
import lang.temper.frontend.RandomInt
import lang.temper.frontend.StagingFlags
import lang.temper.frontend.syntax.isAssignment
import lang.temper.lexer.StandaloneLanguageConfig
import lang.temper.log.CodeLocation
import lang.temper.log.Debug
import lang.temper.log.FilePositions
import lang.temper.log.Position
import lang.temper.log.Positioned
import lang.temper.log.excerpt
import lang.temper.log.toReadablePosition
import lang.temper.name.BuiltinName
import lang.temper.name.ModuleName
import lang.temper.name.SourceName
import lang.temper.name.TemperName
import lang.temper.name.Temporary
import lang.temper.stage.Stage
import lang.temper.type.DotHelper
import lang.temper.type.InvalidType
import lang.temper.type.StaticType
import lang.temper.type.mentionsInvalid
import lang.temper.value.BasicTypeInferences
import lang.temper.value.BasicTypeInferencesTree
import lang.temper.value.BlockTree
import lang.temper.value.CallTypeInferences
import lang.temper.value.CallTypeInferencesTree
import lang.temper.value.DeclTree
import lang.temper.value.FunTree
import lang.temper.value.LeftNameLeaf
import lang.temper.value.NameLeaf
import lang.temper.value.NoTypeInferencesTree
import lang.temper.value.PanicFn
import lang.temper.value.PseudoCodeDetail
import lang.temper.value.TBoolean
import lang.temper.value.TEdge
import lang.temper.value.TFunction
import lang.temper.value.TType
import lang.temper.value.Tree
import lang.temper.value.TypeInferences
import lang.temper.value.Value
import lang.temper.value.blockPartialEvaluationOrder
import lang.temper.value.symbolContained
import lang.temper.value.thisParsedName
import lang.temper.value.toLispy
import lang.temper.value.toPseudoCode
import lang.temper.value.typeSymbol
import lang.temper.value.valueContained
import lang.temper.value.void
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.fail

class TyperTest {

    @Test
    fun intPlusIntIsInt() = assertTypes(
        // By the time the type stage happens, the addition has been collapsed to a constant, so
        // the Typer never sees the individual operands.
        """
        |/// ┏━┓   ┏━┓ : ???
        |    123 + 456;
        |/// ┗━━━━━━━┛ : Int32
        |
        |// The interpreter simplifies some things, so we can't be sure the Interpreter is actually
        |// using the definition of `+`.
        |
        |let int: Int = panic();
        |/// ┏━┓   ┏━┓ : Int32
        |    int + int;
        |/// ┗━━━━━━━┛ : Int32
        """.trimMargin(),
    )

    @Test
    fun declaredTypes() = assertTypes(
        """
        |///     ╻        ╻        : Int32
        |    let i: Int = 0;
        |///        ┗━┛            : Type
        |
        |///     ╻                 : Int32
        |///     ┃        ┏━━━━━┓  : Never
        |    let j: Int = panic();
        |///        ┗━┛            : Type
        |
        |/// ╻  ╻                  : Int32
        |    i; j;
        """.trimMargin(),
    )

    @Test
    fun varInference() = assertTypes(
        """
        |    // See makePeanoProud test from sub-project be:
        |    var one = 1;
        |///     ┗━┛      : Int32
        |    one = one;
        |/// ┗━┛   ┗━┛    : Int32
        |/// ┏━┓   ┏━┓    : Int32
        |    one + one
        |/// ┗━━━━━━━┛    : Int32
        """.trimMargin(),
    )

    @Test
    fun condition() = assertTypes(
        """
        |///     ╻                     : Int32
        |    var i = 0;
        |    while (i < 3) { i += 1; }
        |///                 ┗━━━━┛    : Int32
        """.trimMargin(),
    )

    @Test
    fun formalTypes() = assertTypes(
        """
        |///     ╻                   :  fn (Int32): Top
        |    let f(arg: Int) { arg }
        |///       ┗━┛         ┗━┛   :  Int32
        """.trimMargin(),
        wantErrors = listOf(
            "2+20: Explicit return type required!",
        ),
    )

    @Test
    fun typeFromInitializer() = assertTypes(
        """
        |let initialized = ".";
        |/// ┗━━━━━━━━━┛   ┗━┛   ┏━━━━━━━━━┓ :  String
        |                        initialized
        """.trimMargin(),
    )

    @Test
    fun functionType() = assertTypes(
        """
        |    let addTwoIntegers(a: Int, b: Int): Int {
        |///     ┏━━━┓ : Int32
        |        a + b
        |///       ╹    : fn (Int32, Int32): Int32
        |    }
        |
        |    let addTwoFloats(a: Float64, b: Float64): Float64 {
        |///     ┏━━━┓ : Float64
        |        a + b
        |///       ╹    : fn (Float64, Float64): Float64
        |    }
        |
        |/// ┏━━━━━━━━━━━━┓ : fn (Int32, Int32): Int32
        |    addTwoIntegers;
        |/// ┏━━━━━━━━━━┓ : fn (Float64, Float64): Float64
        |    addTwoFloats;
        """.trimMargin(),
    )

    @Test
    fun genericFn() = assertTypes(
        """
        |    let identity<T>(x: T): T { x }
        |    let x = identity("");
        |///     ╹ : String
        |    let y = identity(42);
        |///     ╹ : Int32
        |    void
        """.trimMargin(),
    )

    @Test
    fun genericFnWithUpperBound() = assertTypes(
        """
        |    let identity<T extends AnyValue>(x: T): T { x }
        |    let x = identity("");
        |///     ╹ : String
        |    let y = identity(42);
        |///     ╹ : Int32
        |    void
        """.trimMargin(),
    )

    @Test
    fun genericFnNoConstantFolding() = assertTypes(
        """
        |    let str: String = panic();
        |    let int: Int = panic();
        |    let identity<T extends AnyValue>(x: T): T { x }
        |    let x = identity(str);
        |///     ╹ : String
        |    let y = identity(int);
        |///     ╹ : Int32
        |    void
        """.trimMargin(),
    )

    @Test
    fun typeInfoForImportedNames() = assertTypes(
        """
        |///                  ┏━━━━━━┓ : Type
        |    let declaration: AnyValue = 42;
        """.trimMargin(),
    )

    @Test
    fun twoInputsFitOneTypeArg() = assertTypes(
        """
        |    let str: String = panic();
        |    let int: Int = panic();
        |
        |    // This function forces T to bind to the least-upper-bound of a's and b's types.
        |    let first<T>(a: T, b: T): T { a }
        |    // It returns the type of the first, but the return type is intentionally vague
        |    // which may be required for backwards compatibility.
        |
        |    first(str, int);
        |/// ┗━━━━━━━━━━━━━┛ : MapKey
        |
        """.trimMargin(),
    )

    @Test
    fun substitutionIntoUnion() = assertTypes(
        """
        |    let identityOrNull<T extends AnyValue>(x: T): (T?) { if (panic()) { x } else { null } }
        |
        |/// ┏━━━━━━━━━━━━┓ : fn<T extends AnyValue>(T): (T?)
        |    identityOrNull("");
        |/// ┗━━━━━━━━━━━━━━━━┛ : String?
        |
        |    identityOrNull(null);
        |/// ┗━━━━━━━━━━━━━━━━━━┛ : AnyValue?
        |
        |    identityOrNull<String>(null);
        |/// ┗━━━━━━━━━━━━━━━━━━━━━━━━━━┛ : String?
        """.trimMargin(),
        wantErrors = listOf(
            // Explicitly specified `<String>` but input is `null`.
            "10+27-31: Actual arguments do not match signature: <T extends AnyValue>(T) -> T? expected [String], but got [String?]!",
            "10+4-32: Invalid variant: Invalid mentions Invalid",
        ),
    )

    @Test
    fun newExpression() = assertTypes(
        """
        |    class C {}
        |
        |    new C()
        |/// ┗━━━━━┛ : C
        """.trimMargin(),
    )

    @Test
    fun bubblyNewExpression() = assertTypes(
        """
        |    class C { public constructor(): Void throws Bubble {} }
        |
        |    new C()
        |/// ┗━━━━━┛ : C | Bubble
        """.trimMargin(),
    )

    @Test
    fun newExpressionAndRegularCall() = assertTypes(
        """
        |    interface I {}
        |    let limitedIdentity<T extends I>(x: T): T { x }
        |
        |    class C extends I {}
        |
        |/// ┏━━━━━━━━━━━━━━━━━━━━━━┓ : C
        |    limitedIdentity(new C())
        |///                 ┗━━━━━┛ : C
        """.trimMargin(),
    )

    @Test
    fun outOfBoundsBinding() = assertTypes(
        """
        |    interface I {}
        |    let limitedIdentity<T extends I>(x: T): T { x }
        |
        |    class C {}  // does not extend I
        |
        |/// ┏━━━━━━━━━━━━━━━━━━━━━━┓ : C
        |    limitedIdentity(new C());
        |///                 ┗━━━━━┛ : C
        |    void
        """.trimMargin(),
        wantErrors = listOf(
            "7+4-28: Type formal <T extends I> cannot bind to C which does not fit upper bounds [I]!",
            "7+4-28: Invalid variant: Invalid mentions Invalid",
        ),
    )

    @Test
    fun conflictingBounds() = assertTypes(
        """
        |    let identity<T>(x: T): T { x }
        |    let int: Int = panic();
        |    // Here we have conflicting irreconcilable upper and lower bounds.
        |    // But the input types are sufficient to establish the bounds, so we don't ever
        |    // actually involve the return type.
        |    let str: String = identity(int);
        |///                   ┗━━━━━━━━━━━┛ : Int32
        """.trimMargin(),
        wantErrors = listOf(
            "6+22-35: Cannot assign to String from Int32!",
        ),
    )

    @Test
    fun zeroArgumentGenericFunctions() = assertTypes(
        """
        |    interface I<E> {}
        |    class C<F> extends I<F> {}
        |    let nothing<T>(): C<T> { panic() }
        |
        |    nothing;
        |/// ┗━━━━━┛ : fn<T extends AnyValue>: C<T>
        |
        |    nothing();
        |/// ┗━━━━━━━┛ : C<AnyValue>
        |
        |    nothing<Boolean>();
        |/// ┗━━━━━━━━━━━━━━━━┛ : C<Boolean>
        |
        |    let x: C<Int>    = nothing();
        |///                    ┗━━━━━━━┛ : C<Int32>
        |
        |    // The context type may be projected to.
        |    let y: I<String> = nothing();
        |///                    ┗━━━━━━━┛ : C<String>
        |
        |    // Branches create a lot of temporaries.  Make sure we propagate context there.
        |    let z: C<Boolean> = if (foo()) {
        |        nothing()
        |///     ┗━━━━━━━┛ : C<Boolean>
        |      } else {
        |        nothing()
        |///     ┗━━━━━━━┛ : C<Boolean>
        |      };
        """.trimMargin(),
        wantErrors = listOf(
            // In `foo()`, `foo` is undefined, so types as Invalid.
            "22+28-31: No declaration for foo!",
            "22+28-31: Type Invalid mentions Invalid",
            "22+28-33: Type Invalid mentions Invalid",
        ),
    )

    @Test
    fun isCombo() = assertTypes(
        """
        |    interface A {}
        |    class B extends A {}
        |    class C extends A {}
        |    let a1 = new B() as A;
        |///     ┗┛ : A
        |    let a2 = new C() as A;
        |    let check = a1 is B && a2 is C;
        |///     ┗━━━┛ : Boolean
        """.trimMargin(),
    )

    /** Somewhat like [zeroArgumentGenericFunctions] but other variations. */
    @Test
    fun typeContextWinsOverInsidesButNotYet() = assertTypes(
        """
            |    let typify<T>(it: T): T { it } // useless because inlined early
            |    class Gizmo<T>(public hi: T) {}
            |    class Thing<T> {}
            |    let fly(thing: Thing<Int>): Void { panic(); }
            |    fly(new Thing());
            |///     ┗━━━━━━━━━┛ : Thing<Int32>
            |    new Gizmo(1);
            |/// ┗━━━━━━━━━━┛ : Gizmo<Int32>
            |    let thing: Thing<Int> = new Thing();
            |///                         ┗━━━━━━━━━┛ : Thing<Int32>
            |    let things: List<Int> = [];
            |///                         ┗┛ : List<Int32>
            |    List.of<AnyValue>(1, 2, 3);
            |/// ┗━━━━━━━━━━━━━━━━━━━━━━━━┛ : List<AnyValue>
            |    List.of(1, 2, 3);
            |/// ┗━━━━━━━━━━━━━━┛ : List<Int32>
            |    List.of();
            |/// ┗━━━━━━━┛ : List<AnyValue>
            |    let things: List<Int> = List.of();
            |///                         ┗━━━━━━━┛ : List<Int32>
            |    let gizmo: Gizmo<AnyValue> = new Gizmo(1);
            |///                              ┗━━━━━━━━━━┛ : Gizmo<AnyValue>
            |    new Thing() as Thing<Int>;
            |/// ┗━━━━━━━━━┛ : Thing<AnyValue>
            |    let things: List<AnyValue> = [42];
            |///                              ┗━━┛ : List<Int32>
            |    let things: List<AnyValue> = List.of(42);
            |///                              ┗━━━━━━━━━┛ : List<Int32>
            |    [42] as List<AnyValue>;
            |/// ┗━━┛ : List<Int32>
        """.trimMargin(),
    )

    @Test
    fun deepTypedLists() = assertTypes(
        """
            |    interface Fruit {}
            |    class Apple extends Fruit {}
            |    class Banana extends Fruit {}
            |    List.of(1);
            |/// ┗━━━━━━━━┛ : List<Int32>
            |    List.of<AnyValue>(1);
            |/// ┗━━━━━━━━━━━━━━━━━━┛ : List<AnyValue>
            |    List.of(new Apple());
            |/// ┗━━━━━━━━━━━━━━━━━━┛ : List<Apple>
            |    List.of<Fruit>(new Apple());
            |/// ┗━━━━━━━━━━━━━━━━━━━━━━━━━┛ : List<Fruit>
            |    let pairs =
            |///     ┗━━━┛ : List<Pair<String, Fruit>>
            |      List.of<Pair<String, Fruit>>(
            |        new Pair("apple", new Apple()),
            |        new Pair("banana", new Banana()),
            |      );
            |    let things = new Map(pairs);
            |///     ┗━━━━┛ : Map<String, Fruit>
            |    let things2 = new Map(pairs2);
            |///     ┗━━━━━┛ : Map<String, List<Fruit>>
            |    let pairs2 =
            |///     ┗━━━━┛ : List<Pair<String, List<Fruit>>>
            |      List.of<Pair<String, List<Fruit>>>(
            |        new Pair("apple", List.of<Fruit>(new Apple())),
            |///                       ┗━━━━━━━━━━━━━━━━━━━━━━━━━┛ : List<Fruit>
            |        new Pair("banana", List.of<Fruit>(new Banana())),
            |///                        ┗━━━━━━━━━━━━━━━━━━━━━━━━━━┛ : List<Fruit>
            |      );
            |    let unbound = List.of;
            |///     ┗━━━━━┛               : fn<listT extends AnyValue>(...listT): List<listT>
            |    let bound = List.of<Int>;
            |///     ┗━━━┛                 : fn (...Int32): List<Int32>
        """.trimMargin(),
    )

    @Test
    fun conditionsAreBoolean(
        // thx, Cpt. Obvious
    ) = assertTypes(
        """
        |    let nothing<T>(): T { panic() }
        |
        |///     ┏━━━━━━━┓ : Boolean
        |    if (nothing()) {
        |        nothing()
        |///     ┗━━━━━━━┛ : AnyValue
        |    }
        """.trimMargin(),
    )

    @Test
    fun intertwinedGenericCalls() = assertTypes(
        """
        |    // By nesting calls to generic functions we can test how operations that benefit from
        |    // intertwining their type inference with other functions' work.
        |    let identity<T>(x: T): T { panic() }
        |    let nothing<T>(): T { panic() }
        |
        |    // Here's an input.
        |    let int: Int = panic();
        |
        |    // Can we propagate across calls inside-out.
        |///     ╻            ┏━━━━━━━━━━━┓                    : Int32
        |    let x = identity(identity(int));
        |///         ┗━━━━━━━━━━━━━━━━━━━━━┛                   : Int32
        |
        |    // Can we propagate context across calls outside-in.
        |    // The innermost call is eligible because its <T> is not mentioned in any input type
        |    // and it has no context and it's used in the middle call.
        |    // The middle call is eligible because its <T> is not mentioned in any input other than
        |    // x and x's type depends on the delayed call to nothing, and it is used in a call.
        |    // The outermost call is not eligible because its got type context, so it types
        |    // in a group with the other two.
        |///     ┏━┓                                           : String
        |    let str: String = identity(identity(nothing()));
        |///                   ┃        ┃        ┗━━━━━━━┛┃┃   : String
        |///                   ┃        ┗━━━━━━━━━━━━━━━━━┛┃   : String
        |///                   ┗━━━━━━━━━━━━━━━━━━━━━━━━━━━┛   : String
        """.trimMargin(),
    )

    @Test
    fun multipleDifferentInstantiationsOfSameTypeVar() = assertTypes(
        """
        |    let nothing<T>(): T { panic() }
        |
        |    let f(s: String, i: Int): Void {}
        |
        |    // The call to the non-generic function needs to intertwine with the two calls to
        |    // nothing so that their <T> can relate to the types of f's two parameters.
        |    // Each separate use of <T> needs to not conflict with the other.
        |    f(nothing(), nothing());
        |/// ┃ ┃       ┃  ┗━━━━━━━┛┃   : Int32
        |/// ┃ ┗━━━━━━━┛           ┃   : String
        |/// ┗━━━━━━━━━━━━━━━━━━━━━┛   : Void
        """.trimMargin(),
    )

    @Test
    fun intertwinedCallsNotSplit() = assertTypes(
        """
        |    // These tests are similar to intertwinedCallsGeneric, but they test the case where
        |    // the calls are not pulled out.
        |    let identityPure<T>(x: T): T { panic() }
        |    let nothingPure<T>(): T { panic() }
        |    // nothingPure may sound like a 90s band that was a tad too emo to make it as a
        |    // The Cure cover band, but by using the pure function, panic(), we produce a
        |    // function that is also pure so is recognized early as not able to throw.
        |    // Since the MagicSecurityDust pass never splits the calls out to insert hs(...) calls
        |    // and failure branches, we can further test our grouping of co-dependent calls.
        |
        |    let boo: Boolean = panic();
        |
        |///     ╻                ┏━━━━━━━━━━━━━━━┓                     : Boolean
        |    let x = identityPure(identityPure(boo));
        |///         ┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛                    : Boolean
        |
        |///     ┏━┓                                                    : Int32
        |    let int: Int = identityPure(identityPure(nothingPure()));
        |///                ┃            ┃            ┗━━━━━━━━━━━┛┃┃   : Int32
        |///                ┃            ┗━━━━━━━━━━━━━━━━━━━━━━━━━┛┃   : Int32
        |///                ┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛   : Int32
        """.trimMargin(),
    )

    @Test
    fun multipleAssigningBranchesNull() = assertTypes(
        """
        |    var x;
        |///     ╹ : String?
        |    if (b) {
        |        x = "42";
        |///     ╹   ┗━━┛ : String?, String
        |    } else {
        |        x = null;
        |///     ╹   ┗━━┛ : String?
        |    }
        |    x;
        |/// ╹ : String?
        """.trimMargin(),
        wantErrors = listOf(
            "3+8-9: No declaration for b!",
            "3+8-9: Type Invalid mentions Invalid",
        ),
    )

    // TODO: When we generalize entangling to handle lambda signatures,
    // make sure we can also entangle assignments that lead to Unsolvable
    // solutions with other initializers.
    @Ignore
    @Test
    fun multipleAssigningBranchesListOfNullable() = assertTypes(
        """
        |    var x;
        |///     ╹ : List<String?>
        |    if () {
        |        x = ["42"];
        |///     ╹   ┗━━━━┛ : List<String?>
        |    } else {
        |        x = [null];
        |///     ╹   ┗━━━━┛ : List<String?>
        |    }
        |    x;
        |/// ╹ : List<String?>
        """.trimMargin(),
    )

    @Test
    fun invalidAssignment() = assertTypes(
        """
        |    let x: Int;
        |    // The assignment is illegal.  Typing the assignment thus prevents optimizing out the
        |    // failure branch.  The failure would be recognized as problematic during type checking
        |    // but optimizing out the failure branch during the stages between Stage.Type and
        |    // Stage.GenerateCode.
        |/// ┏━━━━━━┓    : String
        |    x = "42";
        |/// ┃   ┗━━┛    : String
        |/// ╹           : Int32
        |    x
        |/// ╹           : Int32
        """.trimMargin(),
        wantErrors = listOf(
            "7+4-12: Cannot assign to Int32 from String!",
        ),
    )

    @Test
    fun dotProperty() = assertTypes(
        """
        |    class C(public prop: String) {}
        |
        |/// ┏━━━━━━━┓       : C
        |    new C("").prop
        |/// ┗━━━━━━━━━━━━┛  : String
        """.trimMargin(),
    )

    @Test
    fun dotMethod() = assertTypes(
        """
        |    class C { public method(): String { "" }; }
        |
        |/// ┏━━━━━━━━━━━━━━┓  : String
        |/// ┣━━━━━┓        ┃  : C
        |    new C().method()
        """.trimMargin(),
    )

    @Test
    fun privateMethod() = assertTypes(
        """
        |    class C {
        |        private helper(): String { "" };
        |        public method(): String { helper() };
        |///                               ┗━━━━━━┛    : String
        |    }
        """.trimMargin(),
    )

    @Test
    fun booleansAllTheWayDown() = assertTypes(
        """
        |    let  boo : Boolean = panic();
        |    ! ! !boo
        |/// ┃ ┃ ┃┗━┫ : Boolean
        |/// ┃ ┃ ┗━━┫ : Boolean
        |/// ┃ ┗━━━━┫ : Boolean
        |/// ┗━━━━━━┛ : Boolean
        """.trimMargin(),
    )

    @Test
    fun equalityFunction() = assertTypes(
        """
        |    let x: String = panic();
        |    "foo" == x;
        |/// ┗━━━━━━━━┛ : Boolean
        """.trimMargin(),
    )

    @Test
    fun varargs() = assertTypes(
        """
        |    cat();
        |/// ┗━━━┛ : String
        |
        |    cat("");
        |/// ┗━━━━━┛ : String
        |
        |    cat("", "");
        |/// ┗━━━━━━━━━┛ : String
        |
        |    cat("", "", "");
        |/// ┗━━━━━━━━━━━━━┛ : String
        """.trimMargin(),
    )

    // TODO: Testing whether two function types are sub-types needs to ignore the exact name
    // part of formals.
    // `fn <T>(T): T` should be a sub-type of `fn <S>(S): S`
    @Test
    fun functionAssignedToFunctionType() = assertTypes(
        """
        |///                             ┏━━━━━━━━━━━━━━━━━━┓  : fn<T extends AnyValue>(T): T
        |    let identity: fn<T>(T): T = fn<T>(x: T): T { x }
        |///     ┗━━━━━━┛ : fn<T extends AnyValue>(T): T
        |    identity
        |/// ┗━━━━━━┛ : fn<T extends AnyValue>(T): T
        """.trimMargin(),
    )

    @Test
    fun publicMemberVisibility() = assertTypes(
        """
        |    class Public(public b: Int) {}
        |    let aPublic  = new Public(9);
        |///     ┗━━━━━┛    ┗━━━━━━━━━━━┛   : Public
        |/// ┏━━━━━┓                        : Public
        |    aPublic.b;
        |/// ┗━━━━━━━┛                      : Int32
        """.trimMargin(),
    )

    @Test
    fun privateMemberVisibility() = assertTypes(
        """
        |    class Private(private b: Int) {}
        |    let aPrivate = new Private(9);
        |///     ┗━━━━━━┛   ┗━━━━━━━━━━━━┛  : Private
        |/// ┏━━━━━━┓                       : Private
        |    aPrivate.b;
        |/// ┗━━━━━━━━┛                     : Invalid
        |    void
        """.trimMargin(),
        wantErrors = listOf(
            "5+4-14: Member b defined in Private not publicly accessible!",
            "5+4: Type Invalid mentions Invalid",
            "5+4-14: Type Invalid mentions Invalid",
            "5+13-14: Type Invalid mentions Invalid",
        ),
    )

    /**
     * Effectively the same test as [privateMemberVisibility], except the usage is also wrong.
     * This tests semi-arbitrary priorities in our error messaging.
     */
    @Test
    fun noMatchingMemberUsage() = assertTypes(
        """
        |    class Something(private b: Int) {}
        |    let something = new Something(9);
        |    something.b();
        |/// ┗━━━━━━━━━━━┛                     : Invalid
        |    void
        """.trimMargin(),
        wantErrors = listOf(
            "3+4-15: Member b defined in Something incompatible with usage!",
            "3+4: Type Invalid mentions Invalid",
            "3+4-15: Type Invalid mentions Invalid",
            "3+4-17: Type Invalid mentions Invalid",
            "3+14-15: Type Invalid mentions Invalid",
        ),
    )

    @Test
    fun noSuchMember() = assertTypes(
        """
        |    class Something(public b: Int) {}
        |    let something = new Something(9);
        |    something.a;
        |/// ┗━━━━━━━━━┛                     : Invalid
        |    void
        """.trimMargin(),
        wantErrors = listOf(
            "3+4-15: No member a in Something!",
            "3+4: Type Invalid mentions Invalid",
            "3+4-15: Type Invalid mentions Invalid",
            "3+14-15: Type Invalid mentions Invalid",
        ),
    )

    @Test
    fun noMemberInNotMissingType() = assertTypes(
        """
        |    [].hi;
        |    interface A {}
        |    class B extends A {}
        |    (new B() as A).hi;
        """.trimMargin(),
        wantErrors = listOf(
            "1+4-9: No member hi in MissingType!",
            "4+4-21: No member hi in A | AnyValue!",
            "1+4-9: Type Invalid mentions Invalid",
            "1+7-9: Type Invalid mentions Invalid",
            "4+4: Type Invalid mentions Invalid",
            "4+4-21: Type Invalid mentions Invalid",
            "4+19-21: Type Invalid mentions Invalid",
        ),
    )

    @Test
    fun parameterizedTypeForNew() = assertTypes(
        """
        |    interface Foo {}
        |    class Bar<T> extends Foo {}
        |
        |    let baz(f: Foo): Void {}
        |
        |    const item = new Bar<String>();
        |///       ┗━━┛     : Bar<String>
        |    baz(item);
        |///     ┗━━┛       : Bar<String>
        """.trimMargin(),
    )

    @Test
    fun blockLambdas() = assertTypes(
        """
        |    let f(g: fn (Int): String): String { g(0) }
        |    f { (i: Int): String => i.toString() }
        |///   ┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛ : fn (Int32): String
        """.trimMargin(),
    )

    @Test
    fun blockLambdasWithInferredTypes() = assertTypes(
        """
        |    let f(g: fn (Int): String): String { g(0) }
        |    f { i => i.toString() }
        |///   ┗━━━━━━━━━━━━━━━━━━━┛ : fn (Int32): String
        """.trimMargin(),
    )

    @Test
    fun intertwinedPairTypes() = assertTypes(
        """
        |class Pair<T, U>(public first: T, public second: U) {}
        |let f<T, U>(a: Pair<T, U>, b: Pair<U, T>, c: Boolean): Pair<T, U> {
        |  if (c) {
        |    new Pair(a.first, b.first)
        |  } else {
        |    new Pair(b.second, a.second)
        |  }
        |}
        |f(new Pair(3, "hi"), new Pair("bye", 4), true);
        |///                  ┗━━━━━━━━━━━━━━━━┛ : Pair<String, Int32>
        |    new Pair("bye", 4);
        |/// ┗━━━━━━━━━━━━━━━━┛ : Pair<String, Int32>
        """.trimMargin(),
    )

    @Test
    fun intertwinedBlockTypes() = assertTypes(
        """
        |   let f<T, U>(aFirst: Boolean, a: fn (T?): U, b: fn (U?): T): Void {
        |     if (aFirst) {
        |       b(a(null));
        |///        ┗━━┛ : T?
        |     } else {
        |       a(b(null));
        |///        ┗━━┛ : U?
        |     }
        |   }
        |   f(
        |       true,
        |///    ┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓ : fn (Int32?): String
        |       fn(t: Int?): String { if (t == null) { "aha!" } else { t.toString() } },
        |       fn(u: String?): Int { if (u == null) {  -1 } else { u[String.begin] } },
        |   )
        """.trimMargin(),
    )

    /**
     * Unfortunately, something is still causing errors in later stages.
     * See `GenerateCodeStageTest.assignedFnWithInferredSigTypes`. But this gets us part of the way.
     */
    @Test
    fun assignedFnWithInferredSigTypes() = assertTypes(
        """
        |    let f: fn (Int): String = fn (i) { i.toString() };
        |///                           ┗━━━━━━━━━━━━━━━━━━━━━┛ : fn (Int32): String
        """.trimMargin(),
    )

    @Ignore
    @Test
    fun intertwinedBlockTypesInferred() = assertTypes(
        """
        |let f<T, U>(aFirst: Boolean, a: fn (T?): U, b: fn (U?): T): Void {
        |  if (aFirst) {
        |    b(a(null))
        |  } else {
        |    a(b(null))
        |  }
        |}
        |f( // or even f<Int, String>(
        |    true,
        |/// ┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓ : fn (Int32?): String
        |    fn (t) { if (t == null) { "aha!" } else { t.toString() } },
        |    fn (u: String?): Int { if (u == null) { -1 } else { u.codeAt(0) } },
        |)
        """.trimMargin(),
    )

    @Test
    fun methodWithBlock() = assertTypes(
        """
        |    let ls: List<Int> = [0];
        |
        |///             ┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓ :           String
        |    console.log(ls.join(", ") { (x: Int): String => x.toString()});
        |///                           ┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛ : fn (Int32): String
        """.trimMargin(),
    )

    @Test
    fun genericMethodWithBlock() = assertTypes(
        """
        |    let ls: List<Int> = [4];
        |
        |    ls.map { (x: Int): Int => -x }
        |/// ┃      ┗━━━━━━━━━━━━━━━━━━━━━┫ : fn (Int32): Int32
        |/// ┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛ : List<Int32>
        """.trimMargin(),
    )

    @Test
    fun genericMethodBrief() = assertTypes(
        """
        |    [0].map { (x: Int): Int => -x }
        |///         ┗━━━━━━━━━━━━━━━━━━━━━┛ : fn (Int32): Int32
        """.trimMargin(),
    )

    @Test
    fun genericMethodBriefBlockInferredTypes() = assertTypes(
        """
        |    [0].map { (x): Int => -x }
        |///         ┗━━━━━━━━━━━━━━━━┛ : fn (Int32): Int32
        """.trimMargin(),
    )

    @Ignore
    @Test
    fun genericMethodBriefBlockGenericArgType() = assertTypes(
        """
        |    [0].map(fn<T> (x: T): Int { x })
        |///         ┗━━━━━━━━━━━━━━━━━━━━━┛ : fn (Int32): Int32
        """.trimMargin(),
    )

    @Test
    fun genericMethodBriefBlockOptionalInferredTypes() = assertTypes(
        // Not even sure what this ought to mean, but it documents current results.
        """
        |    [0].map { (x): Int => -x }
        |///         ┗━━━━━━━━━━━━━━━━┛ : fn (Int32): Int32
        """.trimMargin(),
    )

    @Test
    fun fnWithExtensionMethod() = assertTypes(
        """
        |    fn (s: String): Boolean { s.isEmpty }
        |/// ┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛ : fn (String): Boolean
        """.trimMargin(),
    )

    @Test
    fun fnWithExtensionMethodAndComplexSubject() = assertTypes(
        """
        |    fn (s: String): Boolean { "foo#{s}bar".isEmpty }
        |/// ┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛ : fn (String): Boolean
        """.trimMargin().replace('#', '$'),
    )

    @Test
    fun inferredStaticProperty() = assertTypes(
        """
        |    class C { public static num = 123; }
        |/// ┏━━━┓                   ┗━┛ : Int32
        |    C.num;
        """.trimMargin(),
    )

    @Test
    fun noSuchStaticProperty() = assertTypes(
        """
        |    class C { public static b = 123; }
        |    C.a;
        |/// ┗━┛ : Invalid
        |    void
        """.trimMargin(),
        wantErrors = listOf(
            "2+4-7: No member a in C!",
            "2+4-7: Type Invalid mentions Invalid",
            "2+6-7: Type Invalid mentions Invalid",
        ),
    )

    @Test
    fun noStaticPropertyVisible() = assertTypes(
        """
        |    class C { private static b = 123; }
        |    C.b;
        |/// ┗━┛ : Invalid
        |    void
        """.trimMargin(),
        wantErrors = listOf(
            "2+4-7: Member b defined in C not publicly accessible!",
            "2+4-7: Type Invalid mentions Invalid",
            "2+6-7: Type Invalid mentions Invalid",
        ),
    )

    @Test
    fun enumMemberReference() = assertTypes(
        """
        |    enum E { A, B }
        |/// ┏━┓      ╹  ╹ : E
        |    E.A;
        """.trimMargin(),
    )

    @Test
    fun castViaAs() = assertTypes(
        // TODO reminder to put the two halves back together later
        """
        |//                                        The innermost expressions have type String.
        |//                                        They're wrapped in a hs(...) call.
        |    let f(x: AnyValue): String throws Bubble { x as String }
        |/// ┏━━━━━━━━━━━━━━━━━━┓                       ┗━━━━━━━━━┛   : String | Bubble
        |    f(panic<AnyValue>());
        """.trimMargin(),
    )

    @Test
    fun intVDouble() = assertTypes(
        """
        |    let i: Int = panic();
        |    (i + 1) + (i * i);
        |/// ┗━━━━━━━━━━━━━━━┛ : Int32
        """.trimMargin(),
    )

    @Test
    fun buildADeque() = assertTypes(
        """
        |    class C {}
        |    let deq = new Deque<C>()
        |///     ┗━┛   ┗━━━━━━━━━━━━┛ : Deque<C>
        """.trimMargin(),
    )

    @Test
    fun orelse() = assertTypes(
        """
        |    let b: Boolean = randomBool();
        |    let s(): String { panic() }
        |    let x: String? =
        |        (if (b) { s() } else { s() }) orelse null;
        |///     ┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛ : String?
        |    x
        """.trimMargin(),
    )

    @Test
    fun orelse2() = assertTypes(
        """
        |    (new Deque<String?>()).removeFirst() orelse null
        |/// ┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛ : String?
        """.trimMargin(),
    )

    @Test
    fun orelseObjects() = assertTypes(
        """
        |    class A {}
        |/// ┏━━━━━┓        ┏━━━━━┓ : A
        |    new A() orelse new A()
        |/// ┗━━━━━━━━━━━━━━━━━━━━┛ : A
        """.trimMargin(),
    )

    @Test
    fun similarMethodCallsInHook() = assertTypes(
        // This had problems because the variant wasn't getting set for one branch through the
        // hook.
        """
        |    let d: Deque<String?> = new Deque<String?>();
        |
        |    if (d.isEmpty) {
        |      d.removeFirst()
        |    } else {
        |      d.removeFirst()
        |    }
        """.trimMargin(),
    )

    @Test
    fun neverIsANeverNotAFunction() = assertTypes(
        // Never is a sub-type of function types
        """
        |    let f: fn<T>(List<T>): List<T> = panic();
        |    f<String>([])
        """.trimMargin(),
    )

    @Test
    fun listOfObjectOfList() = assertTypes(
        """
        |    class C(public ls: List<String>) {}
        |    let cs: List<C> = [new C([""]), new C([""])];
        """.trimMargin(),
    )

    @Test
    fun untypedProperty() = assertTypes(
        """
        |    class C(public p) {}
        |    (new C()).p
        |/// ┗━━━━━━━━━┛ : AnyValue?
        """.trimMargin(),
        wantErrors = listOf(
            // TODO Message and also location could be improved.
            "1+19: Explicit return type required!",
        ),
    )

    @Test
    fun typedGetterNoExplicitCorrespondingPropertyDeclaration() = assertTypes(
        """
        |    class C {
        |      public get p(): String { "foo" }
        |    }
        |    (new C()).p
        |/// ┗━━━━━━━━━┛ : String
        """.trimMargin(),
    )

    @Test
    fun optionalParams() = assertTypes(
        """
        |    let hi(a: Int = 1, b: Int? = 2, c: Int? = null): Int {
        |      let d = "11".toInt32(c);
        |///   ╻                                        : Int32
        |///        ╻                                   : Int32?
        |      a + (b as Int) + (c as Int orelse 3) + d
        |///                     ╹                    ┃ : Int32?
        |///                                          ╹ : Int32
        |    }
        """.trimMargin(),
    )

    @Test
    fun genericRestParametersWithContext() = assertTypes(
        """
        |    // With context type
        |    let zero: List<String> = [];
        |///                          ┗┛             : List<String>
        |    let one: List<String>  = ["foo"];
        |///                          ┗━━━━━┛        : List<String>
        |    let two: List<String>  = ["foo", "bar"];
        |///                          ┗━━━━━━━━━━━━┛ : List<String>
        |    let zeroInOne: List<List<String>> = [[]];
        |///                                      ┗┛ : List<String>
        |    void;
        """.trimMargin(),
    )

    @Test
    fun initializeClassPropertyToNull() = assertTypes(
        """
        |   class C(
        |     public p: String? = null
        |   ) {
        |     public q: String? = null;
        |///                      ┗━━┛ : String?
        |   }
        """.trimMargin(),
    )

    @Test
    fun initializeClassPropertyToNull2() = assertTypes(
        """
        |   class C {
        |     private p: String;
        |     private var q: Int?;
        |     public constructor() {
        |       p = "Hello";
        |       console.log(p);
        |       q = null;
        |///        ┗━━┛ : Int32?
        |     }
        |   }
        """.trimMargin(),
    )

    @Test
    fun genericRestParametersWithoutContext() = assertTypes(
        """
        |    // Without context type
        |    [];
        |/// ┗┛             : List<AnyValue>
        |    ["foo"];
        |/// ┗━━━━━┛        : List<String>
        |    ["foo", "bar"];
        |/// ┗━━━━━━━━━━━━┛ : List<String>
        |    void;
        """.trimMargin(),
    )

    @Test
    fun nestedGenericRestParameterCallsWithoutContext() = assertTypes(
        """
        |/// ┏━━━━━━━━━━━┓  : List<List<String>>
        |    [[], ["foo"]];
        |///  ┗┛  ┗━━━━━┛   : List<String>
        |    void;
        """.trimMargin(),
    )

    @Test
    fun noVariants() = assertTypes(
        """
        |    let s: String = panic();
        |    s + s;
        |/// ┗━━━┛ : Int32
        |    void
        """.trimMargin(),
        wantErrors = listOf(
            "2+4-9: Actual arguments do not match signature: (Int32, Int32) -> Int32 expected [Int32, Int32], but got [String, String]!",
            "2+4-9: Invalid variant: Invalid mentions Invalid",
        ),
    )

    @Test
    fun declarationThatIsNeitherInitializedNorRead() = assertTypes(
        """
        |    let unused;
        |///     ┗━━━━┛ : Top
        """.trimMargin(),
    )

    @Test
    fun multipleInitializersButEachUsedBeforeBothTyped() = assertTypes(
        """
        |    let f<T>(): T { panic() }
        |    let x; // String? from initializer
        |    let y;
        |    let z;
        |    if (panic()) {
        |        x = f<String>();
        |        y = x;        // Either (String) or (String?) are OK types for y
        |        z = panic();
        |    } else {
        |        x = null;
        |        z = x;        // Either (Never<String>?) or (String?) are OK types for z
        |        y = panic();
        |    }
        |    x;
        |/// ╹ : String?
        |    y;
        |/// ╹ : String
        |    z;
        |/// ╹ : String?
        |    void;
        """.trimMargin(),
    )

    @Test
    fun intertwinedGenericMethodDefinitions() = assertTypes(
        """
        |    class Apple<T>(private let t: T) {
        |      public banana(): T { carrot() }
        |///                        ┗━━━━━━┛ : T
        |      public carrot(): T { t }
        |    }
        """.trimMargin(),
    )

    @Test
    fun getsOnInterface() = assertTypes(
        """
        |    interface I { x: Boolean }
        |    let f(i: I): Boolean { i.x }
        |///                        ┗━┛ : Boolean
        """.trimMargin(),
    )

    @Test
    fun internalGetWithinInterface() = assertTypes(
        """
        |    interface I {
        |      x: Boolean;
        |      f(): Boolean {
        |        x
        |///     ╹ : Boolean
        |      }
        |    }
        """.trimMargin(),
    )

    @Test
    fun inheritedMethodFromGenericInterface() = assertTypes(
        """
        |    interface Apple<T> {
        |      hi(): T                     // Overridden
        |    }
        |    class Banana<T>(
        |      private let t: T,
        |    ) extends Apple<T> {
        |      public hi(): T { t }        // Override
        |    }
        |    let banana = new Banana<String>("");
        |    banana.hi()
        |/// ┗━━━━━━━━━┛ : String
        """.trimMargin(),
    )

    @Test
    fun inheritedGenericMethodFromGenericInterface() = assertTypes(
        """
        |    interface Apple<T> {
        |      public map<U>(t: T, transform: fn (T): U): U;                 // Overridden
        |    }
        |    class Banana<T> extends Apple<T> {
        |      public map<U>(t: T, transform: fn (T): U): U { transform(t) } // Override
        |    }
        |    let banana = new Banana<Int>();
        |    banana.map(5) { (t: Int): String => t.toString() }
        |/// ┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛ : String
        """.trimMargin(),
        wantErrors = listOf(
            // We decided to make the above illegal, but we ought to infer reasonable types anyway.
            // So keep the test but expect an error.
            "2+17: Illegal type parameter U. Overridable methods don't allow generics!",
        ),
    )

    @Test // See issue#1305
    fun deeperInheritance() = assertTypes(
        """
        |    interface A {
        |      hi(): String { "hello" }
        |    }
        |    interface B extends A {}
        |    class C extends B {}
        |    new C().hi()
        |/// ┗━━━━━━━━━━┛ : String
        """.trimMargin(),
    )

    @Test
    fun mutualRecursion() = assertTypes(
        """
        |    let sign(x: Int): Int { if (x == 0) { 0 } else if (x < 0) { -1 } else { 1 } }
        |///                                         ┏━━━━━━━━━━━━━━━━┓   : Boolean
        |///                                         ┣━━━┓            ┃   : fn (Int32): Boolean
        |    let isEven(x: Int): Boolean { x == 0 || isOdd(x - sign(x)) }
        |    let isOdd(x: Int): Boolean { !isEven(x) }
        |///                               ┣━━━━┛  ┃                      : fn (Int32): Boolean
        |///                               ┗━━━━━━━┛                      : Boolean
        """.trimMargin(),
    )

    @Test
    fun destructuringInitWithGoodTypes() = assertTypes(
        """
        |    class Person(public name: String, public age: Int) {}
        |    let { name is String, age is Int }: Person = { name: "Bob", age: 40 };
        |///       ┗━━┛ : String
        """.trimMargin(),
    )

    @Test
    fun destructuringInitWithBadTypes() = assertTypes(
        """
        |    class Person(public name: String, public age: Int) {}
        |    let { name, age is String } = { name: "Bob", age: 40 };
        |///       ┗━━┛ : String
        |///             ┗━┛ : String
        """.trimMargin(),
        wantErrors = listOf(
            "2+16-19: Cannot assign to String from Int32!",
        ),
    )

    @Test
    fun covariantLists() = assertTypes(
        """
        |    interface Fruit { }
        |    class Apple extends Fruit { }
        |    let fruits: List<Fruit> = [new Apple()];
        |    fruits[0]
        |/// ┗━━━━━━━┛ : Fruit
        """.trimMargin(),
    )

    @Test
    fun commas() = assertTypes(
        // Commas a variadic, but it's only the last argument that matters,
        // so it's tough to come up with a signature for the comma operation as a whole.
        """
        |    let b: Boolean = panic();
        |    let i: Int = panic();
        |    let s: String = panic();
        |
        |    do { b, i }, s
        |/// ┃    ┗━━┛    ┃ : Int32
        |/// ┗━━━━━━━━━━━━┛ : String
        """.trimMargin(),
    )

    @Test
    fun orNullCast() = assertTypes(
        """
        |    let something(x: Int?): String? {
        |///    ╻                                : Int32?
        |      (x as Int).toString() orelse null
        |///   ┃┗━━━━━━┛           ┃           ┃ : Int32 | Bubble
        |///   ┣━━━━━━━━━━━━━━━━━━━┛           ┃ : String
        |///   ┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛ : String?
        |    }
        """.trimMargin(),
    )

    @Test
    fun nullClauseOnly() = assertTypes(
        """
        |    let something(x: Int?): Void {
        |      if (x == null) { console.log("null"); }
        |    }
        """.trimMargin(),
    )

    @Test
    fun orNullThis() = assertTypes(
        """
        |    let something(x: Int?): String? {
        |      if (x != null) { x.toString() } else { null }
        |    }
        """.trimMargin(),
    )

    /** Cowardly refuse autocast for vars. Could improve some of this someday. */
    @Test
    fun orNullThisVar() = assertTypes(
        """
        |    let something(var x: Int?): String? {
        |      if (x != null) { x.toString() } else { null }
        |    }
        """.trimMargin(),
        wantErrors = listOf(
            "2+23-33: No member toString in Equatable | AnyValue!",
            "2+23-33: Type Invalid mentions Invalid",
            "2+23-35: Type Invalid mentions Invalid",
            "2+25-33: Type Invalid mentions Invalid",
        ),
    )

    /** After null check, plain `x` works in all later branches. */
    @Test
    fun nullCheckChain() = assertTypes(
        """
        |    let hi(x: Int?, y: Int): Int {
        |      if (x == null) {
        |        0
        |        // Note that bare `x` even works in the following condition.
        |      } else if (x == 0 && y == 0) {
        |        1
        |      } else if (y == 0) {
        |        // Math here is arbitrary nonsense.
        |        2 * x
        |      } else {
        |        x + y
        |      }
        |    }
        """.trimMargin(),
    )

    @Test
    fun explicitActualsDoNotRequireInference() = assertTypes(
        """
        |    class C<T> {
        |      public let f(): Void {
        |        let c0       = new C();       // No basis for inference here
        |///                    ┗━━━━━┛        : C<AnyValue>
        |        let c1       = new C<T>();    // No inference needed here
        |///                    ┗━━━━━━━━┛     : C<T>
        |        let c2: C<T> = new C();       // Type on `let` provides basis for inference.
        |///                    ┗━━━━━┛        : C<T>
        |        ignore(c0);
        |        ignore(c1);
        |        ignore(c2);
        |      }
        |    }
        """.trimMargin(),
        wantErrors = listOf(
            "3+23-30: Cannot assign to Top from C<AnyValue>!",
            "3+23-30: Type Invalid mentions Invalid",
        ),
    )

    @Test
    fun constructorAndSetterDefaultToVoid() = assertTypes(
        """
        |    class Something {
        |///          ┏━━━━━━━━━┓         : fn (Something): Void
        |      public constructor() {}
        |      public set blah(x: Int) {}
        |///              ┗━━┛            : fn (Something, Int32): Void
        |    }
        """.trimMargin(),
    )

    @Test
    fun reorder() = assertTypes(
        """
        |    let f(): Int { let i = 4; let g(): Int { i + j }; g() }
        |///                                              ╹          : Int32
        |    let j = i + 1;
        |///     ╹                                                   : Int32
        |    let i = do { console.log("hi"); 1 };
        |///     ╹                                                   : Int32
        """.trimMargin(),
    )

    @Test
    fun divAndModSpecialization() = assertTypes(
        """
        |    let f(a: Int): Int               { a / 2 }
        |///                                      ╹     : fn (Int32, Int32): Int32
        |    let g(a: Int): Int throws Bubble { a / a }
        |///                                      ╹     : fn (Int32, Int32): (Int32 | Bubble)
        """.trimMargin(),
    )

    @Test
    fun explicitTypeArg() = assertTypes(
        """
        |    let maybe: List<Int>? = [123];
        |    let sure = maybe as List<Int> orelse [456];
        |///     ┗━━┛ : List<Int32>
        """.trimMargin(),
    )

    @Test
    fun impliedTypeArg() = assertTypes(
        """
        |    sealed interface Apple<T> {}
        |    class Fuji<T>(public t: T) extends Apple<T> {}
        |    let maybe: Apple<Int>? = new Fuji(123);
        |    // Downcast to `Fuji` while inferring type args `<Int>`.
        |    let sure = maybe as Fuji orelse new Fuji(456);
        |///     ┗━━┛ : Fuji<Int32>
        """.trimMargin(),
    )

    @Test
    fun impliedTypeArgInit() = assertTypes(
        """
        |    sealed interface Apple<T> {}
        |    class Fuji<T>(public t: T) extends Apple<T> {}
        |    // This simple case was already working, so it's good for reference.
        |    let simple = new Fuji(123);
        |///     ┗━━━━┛ : Fuji<Int32>
        |    // When assigning from an implied-type var to an explicit-type var, we get confused.
        |    // So be explicit. See also https://github.com/temperlang/temper/issues/105
        |    let explicit: Fuji<Int> = new Fuji(456);
        |///     ┗━━━━━━┛ : Fuji<Int32>
        |    // New case is upcast to `Apple?` while inferring type args `<Int>`.
        |    let maybe: Apple? = explicit;
        |///     ┗━━━┛ : Apple<Int32>?
        """.trimMargin(),
    )

    @Test
    fun impliedTypeArgWeaving() = assertTypes(
        """
        |    // Swap number and order of type args in the casting here.
        |    sealed interface Thing<T, U, V> {}
        |    sealed interface Apple<T, U> extends Thing<U, T, T> {}
        |    class Fuji<T, U> extends Apple<T, U> {}
        |    let fuji: Fuji = new Fuji<Int, String>();
        |///     ┗━━┛ : Fuji<Int32, String>
        |    let thing: Thing = fuji;
        |///     ┗━━━┛ : Thing<String, Int32, Int32>
        |    let apple = thing as Apple;
        |///     ┗━━━┛ : Apple<Int32, String>
        """.trimMargin(),
    )

    @Test
    fun simpleInitChainAndUsage() = assertTypes(
        """
        |    let apple = [0];
        |    let banana = apple;
        |    let cherry = banana.length;
        |    cherry
        |/// ┗━━━━┛ : Int32
        """.trimMargin(),
    )

    @Test
    fun undeclared() = assertTypes(
        """
        |///     ┏━━━┓           : Top
        |    let banjo = avocado as Thing;
        |///             ┗━━━━━┛ : Invalid
        |    let cobra: Commander;
        |///     ┗━━━┛ : Top
        """.trimMargin(),
        wantErrors = listOf(
            "2+27-32: Expected value of type Type not ❎!",
            "2+16-23: No declaration for avocado!",
            "2+27-32: No declaration for Thing!",
            "4+15-24: No declaration for Commander!",
            "2+16-23: Type Invalid mentions Invalid",
            "2+27-32: Type Invalid mentions Invalid",
            "4+15-24: Type Invalid mentions Invalid",
        ),
    )

    @Test
    fun genericMembers() = assertTypes(
        """
        |    class Avocado(public pits: List<Int>) {}
        |    new Avocado([1]).pits[0] + 1
        |/// ┗━━━━━━━━━━━━━━━━━━━━━━━━━━┛ : Int32
        """.trimMargin(),
    )

    @Test
    fun assignmentToIntMayBeInHs() = assertTypes(
        """
            |    randomInt()
            |/// ┗━━━━━━━━━┛ : Int32
        """.trimMargin(),
    )

    @Test
    fun nestedLoops() = assertTypes(
        $$"""
            |var n = 0;
            |outer: for (var i = 0; i < 4; i++) {
            |  var str = "row ${i.toString()} =";
            |  var j = 0;
            |  while (true) {
            |    str = "${str} ${(n++).toString()}";
            |    if (i <= j) {
            |      console.log(str);
            |      continue outer;
            |    }
            |    j += 1;
            |  }
            |  console.log(str);
            |}
            |
            |var prime = 1;
            |loop1: for (var i = 0; i < 25; i++) {
            |  loop2: for (var j = 0; j < 25; j++) {
            |    loop3: for (var k = 0; k < 25; k++) {
            |      prime += 1;
            |      if (prime != 2 && prime % 2 == 0) {
            |        continue loop1;
            |      }
            |      if (prime != 3 && prime % 3 == 0) {
            |        continue loop2;
            |      }
            |      if (prime != 5 && prime % 5 == 0) {
            |        continue loop3;
            |      }
            |      if (prime != 7 && prime % 7 == 0) {
            |        continue;
            |      }
            |      console.log(prime.toString());
            |    }
            |  }
            |}
        """.trimMargin(),
    )

    @Test
    fun extensionMethodIsAlternative() = assertTypes(
        """
            |    class C {
            |      public f(i: Int): Int { i }
            |    }
            |
            |    @extension("f")
            |    let cf(c: C, s: String): String { s }
            |
            |    let c = new C();
            |
            |    c.f(1234);
            |/// ┗━━━━━━━┛ : Int32
            |    c.f("hi");
            |/// ┗━━━━━━━┛ : String
        """.trimMargin(),
    )

    @Test
    fun instanceExtensionVsStaticExtension() = assertTypes(
        """
            |    interface I {}
            |
            |    @extension("f")
            |    let iF(i: I?): String { "Hello" }
            |
            |    @staticExtension(I, "f")
            |    let IF(): Int { 42 }
            |
            |    var inst: I? = null;
            |    inst = inst;
            |
            |    inst.f();
            |/// ┗━━━━━━┛ : String
            |    I.f()
            |/// ┗━━━┛ : Int32
        """.trimMargin(),
    )

    @Test
    fun unionOfSealed() = assertTypes(
        """
            |    sealed interface I {}
            |    class A extends I {}
            |    class B extends I {}
            |
            |    // Using a union finds the least common super-type instead of A | B
            |    let randomI =
            |///     ┗━━━━━┛ : I
            |      if (randomBool()) {
            |        new A()
            |      } else {
            |        new B()
            |      };
            |
            |    // Mixing null in doesn't pull the super-type to AnyValue
            |    let randomIOrNull =
            |///     ┗━━━━━━━━━━━┛ : I?
            |      if (randomBool()) {
            |        new B()
            |      } else if (randomBool()) {
            |        new A()
            |      } else {
            |        null
            |      };
        """.trimMargin(),
    )

    @Test
    fun emptyListTyped() = assertTypes(
        """
            |    let f(ls: Listed<String>): Void { panic() }
            |
            |    f([])
            |///   ┗┛ : List<String>
        """.trimMargin(),
    )

    @Test
    fun defaultArgEmptyList() = assertTypes(
        """
            |    let f<T>(ls: Listed<T> = []): List<T> { ls.toList() }
            |///                          ┗┛ : List<T>
        """.trimMargin(),
    )

    @Test
    fun bubbleOrElsePanic() = assertTypes(
        // Nullary never-ish functions like bubble and panic are a tad tricky.
        """
            |    let f(): String throws Bubble { bubble() }
            |///                                 ┗━━━━━━┛ : Bubble
            |
            |    let x = f() orelse panic();
            |///     ┃              ┗━━━━━┛ : Never
            |///     ╹                      : String
        """.trimMargin(), // Eventually `Never` becomes `Never<String>`
    )

    @Test
    fun bubbles() = assertTypes(
        """
            |    let something(x: String?): String throws Bubble {
            |      when (x) {
            |        is String -> x;
            |        else -> bubble();
            |///             ┗━━━━━━┛ : Bubble
            |      }
            |    }
            |
            |    let bail(): Never<Void> throws Bubble {
            |      bubble()
            |///   ┗━━━━━━┛ : Bubble
            |    }
        """.trimMargin(),
        // Eventually the first `Bubble` becomes `Never<String> throws Bubble`
        // and the second becomes Never<Void>.
    )

    @Test
    fun assignedNull() = assertTypes(
        """
            |    let a;
            |    if (panic()) {
            |///   ┏━━━━━━┓ : String?
            |      a = null;
            |///   ╹   ┗━━┛ : String?
            |    } else {
            |      a = "Hi";
            |    }
            |
            |    var b: Float64?;
            |/// ┏━━━━━━┓ : Float64?
            |    b = null;
            |/// ╹   ┗━━┛ : Float64?
        """.trimMargin(),
    )

    @Test
    fun listWithNull() = assertTypes(
        """
            |    let x = ["foo", null];
            |///     ╹   ┗━━━━━━━━━━━┛ : List<String?>
            |
        """.trimMargin(),
    )

    @Test
    fun applicationOfFunctionalInterface() = assertTypes(
        """
            |    @fun interface F(i: Int): Int;
            |    let            f(i: Int): Int { i + 1 }
            |
            |    var g: F = f;
            |    g = f;
            |
            |/// ┏━━┓ : Int32
            |    g(0);
            |/// ╹     : F
            |
        """.trimMargin(),
    )

    @Test
    fun errorsInConcatenation() = assertTypes(
        """
            |///             ┏━━━━━━━━━━━┓ : String
            |///             ┃      ┏━━┓ ┃ : String
            |    console.log("foo\u{dddd}")
        """.trimMargin(),
    )

    /** Text at a position in a source code snippet in a unit test */
    data class Chunk(
        val text: String,
        override val pos: Position,
    ) : Positioned

    data class TypeRequirement(
        /** For example, `"123"` above. */
        val annotatedCode: Chunk,
        /**
         * The line number on which the `┏━┓` or `┗━┛` appears on.
         * That line, which must start with `///` specifies the type of the chunk at the
         * corresponding characters in [describedLineNumber].
         */
        val lineNumberOfDescription: Int,
        /** The non-comment line to which the type description refers. */
        val describedLineNumber: Int,
        /** The type text from [lineNumberOfDescription] and its position in the input. */
        val desiredType: Chunk,
    )

    private fun assertTypes(
        /**
         * This is a combined Temper input expression and annotated pseudocode with comments
         * showing the types.
         */
        content: String,
        wantErrors: List<String> = emptyList(),
        verbose: Boolean = false,
        nameSimplifying: Boolean = true,
        skipImplicits: Boolean = false,
    ) {
        val filePath = testCodeLocation
        val source = ModuleSource(
            filePath = filePath,
            fetchedContent = content,
            languageConfig = StandaloneLanguageConfig,
        )
        val typeRequirements = parseTypeRequirementsFromAsciiArtComments(filePath, content)

        val (root, errors) = treeAtTypeStage(
            loc = testModuleName,
            content = source,
            verbose = verbose,
            nameSimplifying = nameSimplifying,
            skipImplicits = skipImplicits,
        )

        val requirementsToInferences = matchRequirementsWithInferences(root, typeRequirements)

        // Rewrite content, replacing type chunks with rendered TypeInferences from the Typer
        val typeColonChunkToRequirements = typeRequirements.groupBy { it.desiredType }
        val replacements = typeColonChunkToRequirements.map { (chunk, reqs) ->
            var replacementTexts = reqs.map { req ->
                val typeGotten = requirementsToInferences[req]?.type
                if (typeGotten != null) {
                    fixupTypeNames(
                        toStringViaTokenSink { tokenSink -> typeGotten.renderTo(tokenSink) },
                    )
                } else {
                    "???"
                }
            }
            // Collapse when we have multiple TypeInferences that are all the same on the same line
            // so that we end up showing one type phrase for all requirements that share that line.
            val first = replacementTexts.first()
            if (replacementTexts.all { it == first }) {
                replacementTexts = listOf(first)
            }
            // Otherwise, the diff will show the multiple different types which may help debug.
            (chunk.pos.left until chunk.pos.right) to replacementTexts.joinToString()
        }

        // By processing replacements in reverse order, we can do one without affecting the
        // indices of replacements yet to be done.
        val replacementBuffer = StringBuilder(content)
        for ((range, text) in replacements.sortedByDescending { it.first.first }) {
            replacementBuffer.setRange(range.first, range.last + 1, text)
        }
        val got = "$replacementBuffer"

        val filePositions = FilePositions.fromSource(filePath, content)
        val gotErrors = errors.map { (pos, message) ->
            "${filePositions.spanning(pos)?.toReadablePosition() ?: "$pos"}: $message"
        }
        val (extraWant, extraGot) = if (gotErrors != wantErrors) {
            fun formatErrors(errorList: List<String>) = toStringViaBuilder {
                it.append("\n\n\n")
                errorList.joinTo(it, "\n")
            }
            formatErrors(wantErrors) to formatErrors(gotErrors)
        } else {
            "" to ""
        }

        var ok = false
        try {
            assertStringsEqual("\n$content$extraWant", "\n$got$extraGot")
            // Newline at start gets ASCII art to line up nicely in JUnit's
            //     expected:<...> but was:<...>
            // output.

            requireInferencesPresent(content, filePath, root)
            ok = true
        } finally {
            if (!ok) {
                console.group("Post typing pseudocode") {
                    root.toPseudoCode(
                        console.textOutput,
                        detail = PseudoCodeDetail(showInferredTypes = true),
                    )
                }
            }
        }
    }

    private fun parseTypeRequirementsFromAsciiArtComments(
        loc: CodeLocation,
        content: String,
    ): List<TypeRequirement> {
        val typeRequirements = mutableListOf<TypeRequirement>()
        val lines = content.splitAfter("\n")
        val characterIndexAtStartOfLine = run {
            var charsSeen = 0
            lines.map { lineText ->
                val charIndex = charsSeen
                charsSeen += lineText.length
                charIndex
            }
        }

        fun position(lineNumber: Int, leftColumn: Int, rightColumn: Int): Position {
            val lineStart = characterIndexAtStartOfLine[lineNumber]
            return Position(loc, lineStart + leftColumn, lineStart + rightColumn)
        }

        var lastNonCommentLineNumber = -1
        for (lineNumber in lines.indices) {
            val lineText = lines[lineNumber]
            if (tripleSlashCommentStart.find(lineText) != null) {
                check(wholeSpecialComment.matchEntire(lineText) != null) {
                    "$lineNumber: $lineText"
                }
                val typeMatch = colonTypeStuff.find(lineText)
                if (typeMatch != null) {
                    val typeChunkText = typeMatch.groups[1]!!.value
                    // group.range not available in Kotlin common.
                    val typeChunkTextOffset = typeMatch.value.indexOf(typeChunkText)
                    val typeChunkLeft = typeMatch.range.first + typeChunkTextOffset
                    val typeChunkRight = typeChunkLeft + typeChunkText.length
                    val typeChunk = Chunk(
                        typeChunkText,
                        position(lineNumber, typeChunkLeft, typeChunkRight),
                    )

                    fun addTypeRequirement(match: MatchResult, describedLineNumber: Int) {
                        val range = match.range
                        val leftColumn = range.first
                        val rightColumn = range.last + 1
                        typeRequirements.add(
                            TypeRequirement(
                                annotatedCode = Chunk(
                                    lines[describedLineNumber].substring(leftColumn, rightColumn),
                                    position(describedLineNumber, leftColumn, rightColumn),
                                ),
                                lineNumberOfDescription = lineNumber,
                                describedLineNumber = describedLineNumber,
                                desiredType = typeChunk,
                            ),
                        )
                    }

                    val forFollowingLines = downwardsAsciiArtRegion.findAll(lineText).toList()
                    val forPrecedingLines = upwardsAsciiArtRegion.findAll(lineText).toList()
                    if (forFollowingLines.isNotEmpty()) {
                        // Look forward
                        val describedLineNumber = ((lineNumber + 1) until lines.size).first {
                            tripleSlashCommentStart.find(lines[it]) == null
                        }
                        forFollowingLines.forEach { matchResult ->
                            addTypeRequirement(matchResult, describedLineNumber)
                        }
                    }
                    if (forPrecedingLines.isNotEmpty()) {
                        val describedLineNumber = lastNonCommentLineNumber
                        check(describedLineNumber != -1) {
                            "Nothing preceding $lineText"
                        }
                        forPrecedingLines.forEach { matchResult ->
                            addTypeRequirement(matchResult, describedLineNumber)
                        }
                    }
                    check(forFollowingLines.isNotEmpty() || forPrecedingLines.isNotEmpty()) {
                        "$lineNumber: $lineText"
                    }
                }
            } else {
                lastNonCommentLineNumber = lineNumber
            }
        }
        return typeRequirements.sortedBy { it.annotatedCode.pos.left }
    }

    /**
     * Stages a module whose content is [content] through the type stage and returns a
     * snapshot of the AST post Typer and any error messages encountered.
     */
    private fun treeAtTypeStage(
        loc: ModuleName,
        content: ModuleSource,
        verbose: Boolean,
        nameSimplifying: Boolean,
        skipImplicits: Boolean,
    ): Pair<Tree, List<Pair<Position, String>>> {
        val logSink = ListBackedLogSink()
        val projectLogSink = ValueSimplifyingLogSink(logSink, nameSimplifying = nameSimplifying)
        var kTicks = 10
        val continueCondition = {
            kTicks -= 1
            kTicks > 0
        }

        val module = Module(
            projectLogSink,
            loc,
            console,
            continueCondition,
        )
        module.deliverContent(content)
        module.addEnvironmentBindings(extraBindings)
        if (skipImplicits) {
            module.addEnvironmentBindings(mapOf(StagingFlags.skipImportImplicits to TBoolean.valueTrue))
        }

        var treeAfterTyper: BlockTree? = null
        // Get a snapshot of the tree post typing, and before we remove any
        // unused temporaries that have type information which is not used
        // but which might inform us of how the Typer is doing.
        Debug.configure(
            module,
            Console(
                console.textOutput,
                console.level,
                object : Snapshotter {
                    override fun <IR : Structured> snapshot(key: SnapshotKey<IR>, stepId: String, state: IR) {
                        if (key == AstSnapshotKey && stepId == Debug.Frontend.TypeStage.AfterTyper.loggerName) {
                            AstSnapshotKey.useIfSame(key, state) { root ->
                                val snapshot = root.copy() as BlockTree
                                fun copyTypeInferences(from: Tree, to: Tree) {
                                    when {
                                        to is BasicTypeInferencesTree && from is BasicTypeInferencesTree ->
                                            to.typeInferences = from.typeInferences
                                        from is NoTypeInferencesTree -> {}
                                        to is NoTypeInferencesTree -> {}
                                        to is CallTypeInferencesTree && from is CallTypeInferencesTree ->
                                            to.typeInferences = from.typeInferences
                                        to is BasicTypeInferencesTree ->
                                            to.typeInferences =
                                                from.typeInferences?.let {
                                                    BasicTypeInferences(it.type, it.explanations)
                                                }
                                        else -> error("$to / $from")
                                    }
                                    from.children.zip(to.children).forEach { (f, t) ->
                                        copyTypeInferences(f, t)
                                    }
                                }
                                copyTypeInferences(from = root, to = snapshot)
                                treeAfterTyper = snapshot
                            }
                        }
                    }
                },
            ),
        )
        while (module.canAdvance() && (module.nextStage ?: Stage.Run) <= Stage.Type) {
            console.groupIf(verbose, "Stage ${module.nextStage}") {
                if (verbose) {
                    val debugTreeRepresentation = DebugTreeRepresentation.PseudoCode
                    module.treeForDebug?.let { tree ->
                        console.group("Before advancing") {
                            debugTreeRepresentation.dump(tree, console)
                        }
                    }
                }
                module.advance()
            }
        }
        if (Stage.Type != module.stageCompleted) {
            logSink.allEntries.forEach {
                console.error(it.messageText)
            }
            fail("Module failed to reach end of type stage")
        }
        val root = treeAfterTyper ?: module.treeForDebug!!

        // If any part of the tree types to Invalid, call that out via the error message mechanism
        // since we don't proceed all the way to GenerateCode and its checks.
        val invalidPositions = mutableMapOf<Position, MutableSet<String>>()
        fun findContradictionsAndInvalidType(t: Tree) {
            val childOrder = if (t is BlockTree) {
                blockPartialEvaluationOrder(t)
            } else {
                0 until t.size
            }
            childOrder.forEach { i -> findContradictionsAndInvalidType(t.child(i)) }

            val typeInferences = t.typeInferences
            if (typeInferences != null) {
                if (typeInferences.type.mentionsInvalid) {
                    invalidPositions.putMultiSet(t.pos, "Type ${typeInferences.type}")
                } else if (typeInferences is CallTypeInferences) {
                    typeInferences.bindings2.forEach { (typeFormal, binding) ->
                        val name = typeFormal.name
                        if (binding is StaticType && binding.mentionsInvalid) {
                            invalidPositions.putMultiSet(t.pos, "Binding from $name to $binding")
                        }
                    }
                    val variant = typeInferences.variant
                    if (variant.mentionsInvalid) {
                        invalidPositions.putMultiSet(t.pos, "Invalid variant: $variant")
                    }
                }
                typeInferences.explanations.forEach {
                    it.logTo(projectLogSink)
                }
            }
        }
        findContradictionsAndInvalidType(root)

        val errors = logSink.allEntries.mapNotNullTo(mutableListOf()) {
            if (it.level >= Log.Error) {
                it.pos to it.messageText
            } else {
                null
            }
        }

        invalidPositions.keys.sortedWith { (_, aLeft, aRight), (_, bLeft, bRight) ->
            var delta = aLeft.compareTo(bLeft)
            if (delta == 0) {
                delta = aRight.compareTo(bRight)
            }
            delta
        }.forEach { pos ->
            invalidPositions[pos]?.mapTo(errors) { pos to "$it mentions Invalid" }
        }

        return Pair(root, errors.toList())
    }

    private fun matchRequirementsWithInferences(
        root: Tree,
        typeRequirements: List<TypeRequirement>,
    ): Map<TypeRequirement, TypeInferences> {
        // For each requirement, collect candidates and judge them based on
        val requirementsToInferences =
            mutableMapOf<TypeRequirement, Pair<NonsenseGradient, TypeInferences>>()
        val requirementsByPosition = typeRequirements.associateBy { it.annotatedCode.pos }
        // Walk over the tree with TypeInferences attached and check requirements.
        TreeVisit.startingAt(root)
            .forEachContinuing { t ->
                val sensibleness = when {
                    !hasInterestingType(t) -> NonsenseGradient.TotalNonsense
                    isProbablyMadeUp(t) -> NonsenseGradient.ProbableNonsense
                    isPossiblyMadeUp(t) -> NonsenseGradient.PossibleNonsense
                    else -> NonsenseGradient.NotSuss
                }
                val typeInferences = t.typeInferences
                if (typeInferences != null) {
                    val req = requirementsByPosition[t.pos]
                    if (req != null) {
                        val previousSensibleness = requirementsToInferences[req]?.first
                            ?: NonsenseGradient.TotalNonsense
                        if (sensibleness > previousSensibleness) {
                            requirementsToInferences[req] = sensibleness to typeInferences
                        }
                    }
                }
            }
            // Deeper expressions often have more specific type metadata than wrapping expressions.
            .visitPostOrder()
        return requirementsToInferences.mapValues { it.value.second }
    }

    /**
     * The tests allow us to find the narrowest node that has a type inferred.
     * Do a final coherence check that all nodes that should, do have types.
     */
    private fun requireInferencesPresent(
        sourceCode: String,
        loc: CodeLocation,
        ast: Tree,
    ) {
        val missingTypeInformation = mutableListOf<Tree>()
        TreeVisit.startingAt(ast)
            .forEachContinuing {
                val needsTypeInfo = it.needsTypeInfo
                if (needsTypeInfo && it.typeInferences?.type == null) {
                    missingTypeInformation.add(it)
                }
            }
            .visitPreOrder()
        if (missingTypeInformation.isNotEmpty()) {
            val positionsMissingTypeInformation = missingTypeInformation.map { it.pos }.toSet()
            val maximalPositions = positionsMissingTypeInformation.filter { pos ->
                positionsMissingTypeInformation.all { other ->
                    pos == other || pos !in other
                }
            }
            val treesGroupedByLocation = missingTypeInformation.groupBy { t ->
                val pos = t.pos
                maximalPositions.first { pos in it }
            }

            val filePositions = FilePositions.fromSource(loc, sourceCode)
            for ((pos, trees) in treesGroupedByLocation) {
                if (pos.loc == loc) {
                    excerpt(pos, sourceCode, console.textOutput)
                }
                val locStr = Pair(
                    filePositions.filePositionAtOffset(pos.left),
                    filePositions.filePositionAtOffset(pos.right),
                ).toReadablePosition()
                console.group(locStr) {
                    trees.forEach {
                        console.log(it.toLispy())
                    }
                }
            }
            fail(
                toStringViaTextOutput {
                    Console(it).run {
                        group("${missingTypeInformation.size} tree(s) missing type information") {
                            for ((i, missingTree) in missingTypeInformation.withIndex()) {
                                group("#$i @ ${missingTree.document.context.formatPosition(missingTree.pos)}") {
                                    missingTree.toPseudoCode(textOutput)
                                }
                                missingTree.incoming?.source?.let { parent ->
                                    group("within ${parent::class.simpleName}") {
                                        parent.toPseudoCode(textOutput)
                                    }
                                }
                            }
                        }
                    }
                },
            )
        }
    }
}

private val downwardsAsciiArtRegion = Regex("""[┣┏][━]*[┓┫]|[╻]""")
private val upwardsAsciiArtRegion = Regex("""[┣┗][━]*[┛┫]|[╹]""")
private val tripleSlashCommentStart = Regex("""^[ \t]*///""")
private val colonTypeStuff = Regex(""":[ \t]*([\S][^\n]*)\n?$""")
private val wholeSpecialComment = Regex(
    """${tripleSlashCommentStart.pattern}([┃ ]|${
        downwardsAsciiArtRegion.pattern}|${upwardsAsciiArtRegion
    })+(?:${colonTypeStuff.pattern})?""",
)

private fun String.splitAfter(separator: String): List<String> {
    val substrings = mutableListOf<String>()
    var i = 0
    while (true) {
        val endOrZero = this.indexOf(separator, i) + 1
        val end = if (endOrZero != 0) { endOrZero } else { this.length }
        substrings.add(this.substring(i, end))
        if (endOrZero == 0) {
            return substrings.toList()
        }
        i = end
    }
}

private fun hasInterestingType(t: Tree) =
    t !is DeclTree // Declarations are all Void

private fun isProbablyMadeUp(t: Tree) =
    // Variables like fail#123 have position metadata similar to \type metadata trees but very
    // different type metadata
    (t is NameLeaf && t.content is Temporary) ||
        // Void nodes are often replacements for unreachable or garbage nodes.
        (t.valueContained == void) ||
        // Assignments to temporaries that fail are captured in the errors list,
        // so look at the right hand-side to help diagnose partial success.
        (
            t.typeInferences?.type == InvalidType &&
                isAssignment(t) && (t.child(1) as? LeftNameLeaf)?.content is Temporary
            ) ||
        // Metadata on a declaration or function is probably not what the test author
        // is thinking of.
        isMadeUpMetadata(t.incoming)

private fun isMadeUpMetadata(e: TEdge?): Boolean {
    val parent = e?.source ?: return false
    when (parent) {
        is DeclTree -> {
            // Apart from \type and \init metadata, the rest are made up.
            // \init metadata shouldn't exist at the time of typing.
            val index = e.edgeIndex
            if ((index and 1) == 1) { return true } // metadata key
            if (index != 0) {
                // A metadata value.
                val key = parent.child(index - 1).symbolContained
                return key != typeSymbol
            }
        }
        is FunTree -> {} // TODO
        else -> {}
    }
    return false
}

private fun isPossiblyMadeUp(t: Tree) = when {
    // `this` references are inserted as in `propertyName` meaning `this.propertyName`.
    t is NameLeaf && t.content.let {
        it is SourceName && it.baseName == thisParsedName
    } -> true
    // dot helpers are inserted
    t.valueContained(TFunction) is DotHelper -> true
    // Types are inserted into in member expressions
    t.valueContained(TType) != null -> true
    else -> false
}

private fun fixupTypeNames(typeString: String) =
    typeString.replace(trailingNumbersFixupPattern) {
        it.groups[1]!!.value
    }

private val trailingNumbersFixupPattern = Regex("""\b(\w+)__\d+\b""")

private val extraBindings = mapOf<TemperName, Value<*>>(
    // panic() can be used anywhere but does not inline to a value that is trivially
    // typed, so may be used to initialize a declaration whose type is used to fill
    // other type slots
    BuiltinName(PanicFn.name) to BuiltinFuns.vPanic,
    BuiltinName(RandomBool.name) to Value(RandomBool),
    BuiltinName(RandomInt.name) to Value(RandomInt),
    StagingFlags.moduleResultNeeded to TBoolean.valueTrue,
)
