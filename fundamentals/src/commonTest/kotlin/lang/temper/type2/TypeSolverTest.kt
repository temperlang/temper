package lang.temper.type2

import lang.temper.builtin.BuiltinFuns
import lang.temper.common.console
import lang.temper.common.printErr
import lang.temper.common.soleElement
import lang.temper.format.TextOutputTokenSink
import lang.temper.log.unknownPos
import lang.temper.name.BuiltinName
import lang.temper.name.Symbol
import lang.temper.type.NominalType
import lang.temper.type.StaticType
import lang.temper.type.TypeFormal
import lang.temper.type.TypeShape
import lang.temper.type.Variance
import lang.temper.type.WellKnownTypes
import lang.temper.type.withTypeTestHarness
import lang.temper.value.MacroValue
import lang.temper.value.TBoolean
import lang.temper.value.TInt
import lang.temper.value.TList
import lang.temper.value.TNull
import lang.temper.value.TString
import lang.temper.value.TemperFormattingHints
import lang.temper.value.Value
import java.nio.file.Files
import java.nio.file.Path
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class TypeSolverTest {
    @Test
    fun assignmentOfValue() = runSolverTest {
        // let x;  // No declared type
        // x = 0
        // ^   ^
        // ʼa  ʼb
        val a = TypeVar("ʼa")
        val b = TypeVar("ʼb")

        a assignableFrom b
        b assignableFrom ValueBound(Value(0, TInt))

        solve()

        assertSolutions(
            mapOf(
                a to "Int32",
                b to "Int32",
            ),
        )
    }

    @Test
    fun assignmentOfComplexValue() = runSolverTest {
        // let x;  // No declared type
        // x = [0]
        // ^
        // ʼa
        val a0 = TypeVar("ʼa")

        a0 assignableFrom ValueBound(
            Value(listOf<Value<*>>(Value(0, TInt)), TList),
        )

        solve()

        assertSolutions(mapOf(a0 to "List<Int32>"))
    }

    @Test
    fun partialTypesJoined() = runSolverTest {
        // Test combining of
        //    Map<ʼb, Int>
        //    Map<String, ʼc>
        // to
        //    Map<String, Int>
        val a = TypeVar("ʼa")
        val b = TypeVar("ʼb")
        val c = TypeVar("ʼc")

        a assignableFrom PartialType.from(
            WellKnownTypes.mapTypeDefinition,
            listOf(
                TypeVarRef(b, Nullity.NonNull),
                MkType2(WellKnownTypes.intTypeDefinition).get(),
            ),
            Nullity.NonNull,
        )
        PartialType.from(
            WellKnownTypes.mapTypeDefinition,
            listOf(
                MkType2(WellKnownTypes.stringTypeDefinition).get(),
                TypeVarRef(c, Nullity.NonNull),
            ),
            Nullity.NonNull,
        ) assignableFrom a

        solve()

        assertSolutions(
            mapOf(
                a to "Map<String, Int32>",
                b to "String",
                c to "Int32",
            ),
        )
    }

    @Test
    fun inferenceOfParameter() = runSolverTest {
        // let x;  // No declared type
        // x = listify< >("");
        // ^   ^       ^  ^
        // ʼa  callee  |  ʼb
        //             callTypeActuals
        val a = TypeVar("ʼa")
        val b = TypeVar("ʼb")

        val callee = SimpleVar("ʼcallee")
        val callPass = TypeVar("ʼcallPass")
        val callFail = SimpleVar("ʼcallFail")
        val formal = TypeVar("ʼformal")
        val actualsVar = SimpleVar("ʼcallTypeActuals")

        a assignableFrom callPass
        formal assignableFrom b
        b assignableFrom ValueBound(Value("", TString))
        regularCall {
            chosenCallee(callee)

            callee(BuiltinFuns.listifyFn)
            typeActualsList(actualsVar)

            arg(b)

            result(
                pass = callPass,
                fail = callFail,
            )
        }

        solve()

        assertSolutions(
            mapOf(
                a to "List<String>",
                callPass to "List<String>",
                callFail to "[]",
                callee to "0",
                actualsVar to "[String]",
            ),
        )
    }

    @Test
    fun varsReconciled() = runSolverTest {
        val a = TypeVar("ʼa")
        val b = TypeVar("ʼb")
        val c = TypeVar("ʼc")

        a sameAs b
        b sameAs c
        c assignableFrom ValueBound(Value(0, TInt))

        solve()

        assertSolutions(
            mapOf(
                a to "Int32",
                b to "Int32",
                c to "Int32",
            ),
        )
    }

    @Test
    fun varsReconciledViaPartialEquivalences() = withTypeTestHarness {
        runSolverTest {
            val a = TypeVar("ʼa")
            val b = TypeVar("ʼb")
            val c = TypeVar("ʼc")

            partialType("List<ʼa>") sameAs partialType("List<ʼb>")
            partialType("List<ʼb>") sameAs partialType("List<ʼc>")
            partialType("List<ʼc>") assignableFrom ValueBound(
                Value(listOf<Value<*>>(Value(0, TInt)), TList),
            )

            solve()

            assertSolutions(
                mapOf(
                    a to "Int32",
                    b to "Int32",
                    c to "Int32",
                ),
            )
        }
    }

    @Test
    fun variableAssignedNullAndInt() = runSolverTest {
        // Can we get a full type for a null literal
        // without solving type unions?

        // let x; // <-- ʼa
        // if (...) {
        //   x = null; <-- ʼn
        // } else {
        //   x = 0;
        // }

        val a = TypeVar("ʼa")
        val nullLiteralType = TypeVar("ʼn")

        val nullLiteralBound = ValueBound(TNull.value)
        nullLiteralType sameAs nullLiteralBound

        a assignableFrom nullLiteralBound
        a assignableFrom ValueBound(Value(0, TInt))

        solve()

        assertSolutions(
            mapOf(
                a to "Int32?",
                nullLiteralType to "Int32?",
            ),
        )
    }

    @Test
    fun nullInLargerValueLiteralGetsTypeIndirectly() = withTypeTestHarness {
        runSolverTest {
            // let ls: List<Int?> = [null]
            // //  ^a                ^n

            val a = TypeVar("ʼa")
            a sameAs type2("List<Int?>")

            val listLiteralType = TypeVar("ʼn")

            val listLiteralBound = ValueBound(Value(listOf(TNull.value), TList))
            listLiteralType sameAs listLiteralBound

            a assignableFrom listLiteralBound

            solve()

            assertSolutions(
                mapOf(
                    a to "List<Int32?>",
                    listLiteralType to "List<Int32?>",
                ),
            )
        }
    }

    @Test
    fun nullByItselfIsUnsolvable() = withTypeTestHarness {
        runSolverTest {
            // let x = null;
            //     ^ʼa ^ʼb
            val a = TypeVar("ʼa")
            val b = TypeVar("ʼb")

            val nullValueBound = ValueBound(TNull.value)

            a.assignableFrom(nullValueBound)
            b.sameAs(nullValueBound)

            solve()

            assertSolutions(
                mapOf(
                    a to "Unsolvable",
                    b to "Unsolvable",
                ),
            )
        }
    }

    @Test
    fun nullAssignedToTypeDeclIsSolvable() = withTypeTestHarness {
        runSolverTest {
            // let x: String? = null;
            //     ^ʼa           ^ʼb
            val a = TypeVar("ʼa")
            val b = TypeVar("ʼb")

            val nullValueBound = ValueBound(TNull.value)

            a.sameAs(type2("String?"))
            a.assignableFrom(nullValueBound)
            b.sameAs(nullValueBound)

            solve()

            assertSolutions(
                mapOf(
                    a to "String?",
                    b to "String?",
                ),
            )
        }
    }

    @Test
    fun pickingACallee() = withTypeTestHarness {
        val tFloat64 = type2("Float64")
        val tInt = type2("Int")
        // Try int addition and float addition filtered by input type.
        val floatFloatToFloat = Signature2(
            returnType2 = tFloat64,
            hasThisFormal = false,
            requiredInputTypes = listOf(tFloat64, tFloat64),
        )
        val intIntToInt = Signature2(
            returnType2 = tInt,
            hasThisFormal = false,
            requiredInputTypes = listOf(tInt, tInt),
        )

        runSolverTest {
            // z  =  x  +  y
            // ^ʼa   ^ʼb   ^ʼc
            val a = TypeVar("ʼa")
            val b = TypeVar("ʼb")
            val c = TypeVar("ʼc")

            val callee = SimpleVar("ʼcallee")
            val pass = TypeVar("ʼpass")
            val fail = SimpleVar("ʼfail")
            val actuals = SimpleVar("ʼactuals")

            a assignableFrom tInt
            b assignableFrom tInt
            c assignableFrom pass

            regularCall {
                chosenCallee(callee)

                callee(floatFloatToFloat)
                callee(intIntToInt)
                typeActualsList(actuals)

                arg(a)
                arg(b)

                result(
                    pass = pass,
                    fail = fail,
                )
            }

            solve()

            assertSolutions(
                mapOf(
                    a to "Int32",
                    b to "Int32",
                    c to "Int32",
                    callee to "1",
                    pass to "Int32",
                    fail to "[]",
                    actuals to "[]",
                ),
            )
        }
    }

    @Test
    fun twoInitializersInfersLeastCommonSuperType() = withTypeTestHarness(
        """
            |interface I<T>;
            |class A extends I<String>;
            |class B extends I<String>;
            |class C extends I<Int>;
        """.trimMargin(),
    ) {
        // let x;
        //     ^ʼx
        // if (...) { x = new A(); } else { x = new B(); }
        //                ^ʼa                   ^ʼb
        val x = TypeVar("ʼx")
        val a = TypeVar("ʼa")
        val b = TypeVar("ʼb")

        runSolverTest {
            a sameAs type2("A")
            b sameAs type2("B")
            x assignableFrom a
            x assignableFrom b

            solve()

            // I<String> is the common superclass of A and B, so x has type I<String>
            assertSolutions(
                mapOf(
                    a to "A",
                    b to "B",
                    x to "I<String>",
                ),
            )
        }
    }

    @Test
    fun invalidFilteredOutFromInitializers() = runSolverTest {
        // let x;
        // if (condition) {
        //   x = <INVALID>;
        // } else {
        //   x = 0;
        // }

        // We can come to better conclusions if we localize type failures.

        // Try this both ways: with Invalid first and with it second.
        // if (condition) {
        //   y = "";
        // } else {
        //   y = <INVALID>;
        // }

        val x = TypeVar("ʼx")
        val y = TypeVar("ʼy")

        x assignableFrom WellKnownTypes.invalidType2
        x assignableFrom ValueBound(Value(0, TInt))

        y assignableFrom ValueBound(Value("", TString))
        y assignableFrom WellKnownTypes.invalidType2

        solve()

        assertSolutions(
            mapOf(
                x to "Int32",
                y to "String",
            ),
        )
    }

    @Test
    fun reconcilingTypeParameterWithActualType() = withTypeTestHarness {
        runSolverTest {
            // let f<T extends Listed<String>>(t: T) {
            //   let x =  if (...) { t } else { [] }
            //      ^ʼx              ^ʼT        ^ʼlistLiteral

            // Since T has an upperbound of Listed<String> it should be reconcilable with List<a>.
            val x = TypeVar("ʼx")
            val t = TypeVar("ʼt")
            val listLiteral = TypeVar("ʼlistLiteral")

            val (defT) = makeTypeFormalsForTest("T" to type("Listed<String>"))

            listLiteral sameAs ValueBound(Value(emptyList(), TList))

            t sameAs defT
            x assignableFrom t
            x assignableFrom listLiteral

            solve()

            assertSolutions(
                mapOf(
                    x to "Listed<String>",
                    listLiteral to "List<String>",
                    t to "T",
                ),
            )
        }
    }

    @Test
    fun nonNullablePreferredForTemporary() = withTypeTestHarness {
        runSolverTest {
            // Our NotNullFn has a signature like Fn<T extends AnyValue>(T?): T
            val (typeT) = makeTypeFormalsForTest("T" to WellKnownTypes.anyValueType)
            val notNullFnSig = Signature2(
                returnType2 = typeT,
                hasThisFormal = false,
                requiredInputTypes = listOf(typeT.withNullity(Nullity.OrNull)),
                typeFormals = listOf(typeT.definition),
            )

            // Given (a: String, b: Int?)
            // let c = notNull(a);
            // let d = notNull(b);
            val a = TypeVar("ʼa")
            val b = TypeVar("ʼb")
            val c = TypeVar("ʼc")
            val d = TypeVar("ʼd")

            a sameAs type2("String")
            b sameAs type2("Int?")

            // Type actual lists
            val aTs = SimpleVar("ʼaTs")
            val bTs = SimpleVar("ʼbTs")

            // Result vars
            val aPass = TypeVar("ʼaPass")
            val bPass = TypeVar("ʼbPass")
            val aFail = SimpleVar("ʼaFail")
            val bFail = SimpleVar("ʼbFail")

            c assignableFrom aPass
            d assignableFrom bPass

            regularCall {
                callee(notNullFnSig)
                typeActualsList(aTs)
                arg(a)
                result(aPass, aFail)
            }

            regularCall {
                callee(notNullFnSig)
                typeActualsList(bTs)
                arg(b)
                result(bPass, bFail)
            }

            solve()

            assertSolutions(
                mapOf(
                    a to "String",
                    c to "String",
                    aTs to "[String]",
                    aPass to "String",
                    aFail to "[]",

                    b to "Int32?",
                    d to "Int32",
                    bTs to "[Int32]",
                    bPass to "Int32",
                    bFail to "[]",
                ),
            )
        }
    }

    @Test
    fun returnTypeBoundedBeforeOverloadEliminated() = withTypeTestHarness {
        runSolverTest {
            // f( g([]), h([], [], "") )

            // This test sets up a situation where we have three intertwined and overloaded calls,
            // and we cannot pick the exact callee for any without some bounds from another.

            val gSigs = listOf(
                sig("fn<T>(List<T>, List<T>): String"), // Arity excludes
                sig("fn<T>(List<T>): List<T>"),
                sig("fn<T>(Float64): Float64"), // Arg type excludes
                sig("fn<T>(Listed<T>): List<T>"), // Listed less specific than List
            )

            // h does not resolve because it has ambiguous overloads.  The specificity rule
            // does not disambiguate.
            //
            // But its output is ambiguous and that informs `f`'s chosen type actual which
            // propagates to `g` to type its list.
            val hSigs = listOf(
                sig("fn<T>(List<Int>, List<AnyValue>, T): T"),
                sig("fn<T>(List<AnyValue>, List<Int>, T): T"),
            )

            // When ʼp0 is the type variable corresponding to the first type parameter:
            // The third input for both correspond to 'p0.
            // The solver can conclude, without getting down to one callee, that the
            // String (from the "" value bound) is assignable to 'p0.
            // Since 'p0 is also a bound on the call pass variable, we can bind the output
            // type to String which lets us prefer the overload of `f` which takes
            // a String as its second argument.

            val fSigs = listOf(
                sig("fn(List<Float64>, Boolean): Void"),
                sig("fn(List<Int>, String): Void"),
            )
            // When the call to `g` resolves to a passing output type of String, then
            // we can eliminate the second, and start comparing `List<Int>` to `f`'s
            // return type of `List<T>`.

            val fCallee = SimpleVar("ʼfCallee")
            val fActuals = SimpleVar("ʼfActuals")
            val fPass = TypeVar("ʼfPass")
            val fFail = SimpleVar("ʼfFail")
            val f0 = TypeVar("ʼf0")
            val f1 = TypeVar("ʼf1")

            val gCallee = SimpleVar("ʼgCallee")
            val gActuals = SimpleVar("ʼgActuals")
            val gPass = TypeVar("ʼgPass")
            val gFail = SimpleVar("ʼgFail")
            val g0 = TypeVar("ʼg0")

            val hCallee = SimpleVar("ʼhCallee")
            val hActuals = SimpleVar("ʼhActuals")
            val hPass = TypeVar("ʼhPass")
            val hFail = SimpleVar("ʼhFail")
            val h0 = TypeVar("ʼh0")
            val h1 = TypeVar("ʼh1")
            val h2 = TypeVar("ʼh2")

            regularCall {
                fSigs.forEach {
                    callee(it)
                }
                arg(f0)
                arg(f1)
                chosenCallee(fCallee)
                typeActualsList(fActuals)
                result(fPass, fFail)
            }

            regularCall {
                gSigs.forEach {
                    callee(it)
                }
                arg(g0)
                chosenCallee(gCallee)
                typeActualsList(gActuals)
                result(gPass, gFail)
            }

            regularCall {
                hSigs.forEach {
                    callee(it)
                }
                arg(h0)
                arg(h1)
                arg(h2)
                chosenCallee(hCallee)
                typeActualsList(hActuals)
                result(hPass, hFail)
            }

            // f( g([]), h([], [], "") )
            val listG0 = TypeVar("ʼlistG0")
            val listH0 = TypeVar("ʼlistH0")
            val listH1 = TypeVar("ʼlistH1")

            ValueBound(Value(emptyList(), TList)).let {
                g0 assignableFrom it
                listG0 sameAs it
            }
            ValueBound(Value(emptyList(), TList)).let {
                h0 assignableFrom it
                listH0 sameAs it
            }
            ValueBound(Value(emptyList(), TList)).let {
                h1 assignableFrom it
                listH1 sameAs it
            }
            h2 assignableFrom ValueBound(Value("", TString))

            f0 assignableFrom gPass
            f1 assignableFrom hPass

            solve()

            assertSolutions(
                mapOf(
                    fCallee to "1",
                    fActuals to "[]",
                    f0 to "List<Int32>",
                    f1 to "String",
                    fPass to "Void",
                    fFail to "[]",

                    gCallee to "1",
                    gActuals to "[Int32]",
                    g0 to "List<Int32>",
                    listG0 to "List<Int32>",
                    gPass to "List<Int32>",
                    gFail to "[]",

                    hCallee to "Unsolvable",
                    hActuals to "[String]",
                    h0 to "List<AnyValue>",
                    h1 to "List<AnyValue>",
                    h2 to "String",
                    listH0 to "List<AnyValue>",
                    listH1 to "List<AnyValue>",
                    hPass to "String",
                    hFail to "[]",
                ),
            )
        }
    }

    @Test
    fun moreSpecificOverloadChosen() = withTypeTestHarness(
        """
            |interface I;
            |interface J extends I;
        """.trimMargin(),
    ) {
        runSolverTest {
            val sigII = sig("fn(I, I): Void")
            val sigIJ = sig("fn(I, J): Void")
            val sigJI = sig("fn(J, I): Void")

            val a = TypeVar("ʼa")
            val b = TypeVar("ʼb")
            val c = TypeVar("ʼc")
            val d = TypeVar("ʼd")
            val e = TypeVar("ʼe")
            val f = TypeVar("ʼf")
            val g = TypeVar("ʼg")
            val h = TypeVar("ʼh")

            a assignableFrom type2("J")
            b assignableFrom type2("J")
            c assignableFrom type2("J")
            d assignableFrom type2("J")
            e assignableFrom type2("J")
            f assignableFrom type2("J")
            g assignableFrom type2("J")
            h assignableFrom type2("J")

            val calleeIIAndIJ = SimpleVar("ʼcIIAndIJ")
            val calleeIJAndII = SimpleVar("ʼcIJAndII")
            val calleeIIAndJI = SimpleVar("ʼcIIAndJI")
            val calleeIJAndJI = SimpleVar("ʼcIJAndJI")

            // IJ wins because the J is more specific
            regularCall {
                callee(sigII)
                callee(sigIJ)
                arg(a)
                arg(b)
                chosenCallee(calleeIIAndIJ)
            }

            // Same as before but swapping the order the sigs are presented in
            regularCall {
                callee(sigIJ)
                callee(sigII)
                arg(c)
                arg(d)
                chosenCallee(calleeIJAndII)
            }

            // Same as first, but it's the first argument.
            regularCall {
                callee(sigII)
                callee(sigJI)
                arg(e)
                arg(f)
                chosenCallee(calleeIIAndJI)
            }

            // Unsolvable, each has one more specific than the other.
            regularCall {
                callee(sigIJ)
                callee(sigJI)
                arg(g)
                arg(h)
                chosenCallee(calleeIJAndJI)
            }

            solve()

            assertSolutions(
                mapOf(
                    calleeIIAndIJ to "1",
                    calleeIJAndII to "0",
                    calleeIIAndJI to "1",
                    calleeIJAndJI to "Unsolvable",
                ),
            )
        }
    }

    @Test
    fun typeParameterFromReturnContext() = withTypeTestHarness {
        runSolverTest {
            // let f(s: String?): Void
            // let nullish<T>(): T?
            //
            // f(nullish())

            val fSig = sig("fn(String?): Void")
            val nullishSig = sig("fn<T extends AnyValue>(): T?")

            val nullishPass = TypeVar("ʼnullishPass")
            val nullishActuals = SimpleVar("ʼnullishActuals")
            regularCall {
                callee(nullishSig)
                result(pass = nullishPass, fail = SimpleVar("ʼnullishFail"))
                typeActualsList(nullishActuals)
            }

            val fArg = TypeVar("ʼfArg")
            regularCall {
                callee(fSig)
                arg(fArg)
            }

            fArg assignableFrom nullishPass

            solve()

            assertSolutions(
                mapOf(
                    fArg to "String?",
                    nullishActuals to "[String]",
                    nullishPass to "String?",
                ),
            )
        }
    }

    @Test
    fun callWithFallback() = withTypeTestHarness {
        runSolverTest {
            val a = TypeVar("ʼa")
            val b = TypeVar("ʼb")
            val iCallee = SimpleVar("ʼiCallee")

            regularCall {
                callee(sig("fn (Int?, Int?): Boolean"))
                callee(sig("fn (Float64?, Float64?): Boolean"))
                callee(sig("fn (String?, String?): Boolean"))
                callee(
                    sig("fn<cmpT extends AnyValue>(cmpT?, cmpT?): Boolean"),
                    CalleePriority.Fallback,
                )
                arg(a)
                arg(b)
                chosenCallee(iCallee)
            }

            a assignableFrom type2("Float64")
            b assignableFrom type2("Float64")

            solve()

            assertSolutions(
                mapOf(
                    a to "Float64",
                    b to "Float64",
                    iCallee to "1",
                ),
            )
        }
    }

    @Test
    fun zeroArgVariadicWithContext() = withTypeTestHarness {
        runSolverTest {
            val result = TypeVar("ʼr")
            val pass = TypeVar("ʼp")
            val fail = SimpleVar("ʼf")
            val actuals = SimpleVar("ʼa")
            val choice = SimpleVar("ʼc")

            val elementDeclaredType = "MapKey"

            result sameAs pass
            type2("Listed<$elementDeclaredType>") assignableFrom pass
            regularCall {
                callee(sig("fn<T extends AnyValue>(...T): List<T>"))
                result(pass, fail)
                typeActualsList(actuals)
                chosenCallee(choice)
            }

            solve()

            assertSolutions(
                mapOf(
                    actuals to "[$elementDeclaredType]",
                    pass to "List<$elementDeclaredType>",
                    choice to "0",
                    result to "List<$elementDeclaredType>",
                ),
            )
        }
    }

    @Test
    fun functionalInterfaceApplication() = withTypeTestHarness {
        runSolverTest {
            val a = unusedTypeVar("a")
            val actuals = unusedSimpleVar("T")
            val pass = unusedTypeVar("pass")
            val result = unusedTypeVar("result")
            val fail = unusedSimpleVar("fail")
            a assignableFrom type2("fn(Int): Int")
            result sameAs pass
            regularCall {
                callee(sig("fn<O extends AnyValue>(fn(Int): O): List<O>"))
                arg(a)
                typeActualsList(actuals)
                result(pass, fail)
            }

            solve()

            assertSolutions(
                mapOf(
                    a to "Fn<Int32, Int32>",
                    actuals to "[Int32]",
                    pass to "List<Int32>",
                    fail to "[]",
                    result to "List<Int32>",
                ),
            )
        }
    }

    // TODO: We can probably glean information about some type parameters even if we don't do all
    // by marking everything except type parameter list vars Unsolvable and then rerunning put
    // constraints.
    // This is a potential problem if we have variadic functions without any bounds on their type
    // parameter.
    //     Fn<T>(...T): Void
    // But such variadic parameters are not really useful without an extra input that uses T but
    // which is internally not bounds-agnostic.
    //     Fn<T>(Fn(T): Void, ...T)
    // Without variadic parameters we can probably punt on this.
    @Ignore
    @Test
    fun callWithUnsolvableTypeParameterGetsTypeActualsList() = withTypeTestHarness {
        runSolverTest {
            val a = unusedTypeVar("a")
            val tList = unusedSimpleVar("TList")
            a assignableFrom ValueBound(TBoolean.valueTrue)
            regularCall {
                callee(sig("fn<A extends AnyValue, B>(A): A"))
                arg(a)
                typeActualsList(tList)
            }

            solve()

            assertSolutions(
                mapOf(
                    a to "Boolean",
                    tList to "[Boolean, Unsolvable]",
                ),
            )
        }
    }

    @Test
    fun mappedAndMap() = withTypeTestHarness {
        runSolverTest {
            val a = unusedTypeVar("a")
            a assignableFrom type2("Map<Int, String>")
            val ts = unusedSimpleVar("Ts")
            val callee = unusedSimpleVar("callee")
            val pass = unusedTypeVar("pass")
            regularCall {
                callee(sig("fn<K extends MapKey, V extends AnyValue>(Mapped<K, V>): List<K>"))
                arg(a)
                result(pass, null)
                chosenCallee(callee)
                typeActualsList(ts)
            }

            solve()

            assertSolutions(
                mapOf(
                    a to "Mapped<Int32, String>",
                    ts to "[Int32, String]",
                    callee to "0",
                    pass to "List<Int32>",
                ),
            )
        }
    }

    @Test
    fun fnReorderedOverOptional1() = withTypeTestHarness {
        runSolverTest {
            val a = unusedTypeVar("a")
            val b = unusedTypeVar("b")
            val choice = unusedSimpleVar("choice")

            regularCall {
                callee(sig("fn(Int, (fn (): Void), _? : String): Void"))
                callee(sig("fn(String, (fn (Int): Int)): Void"))

                arg(a)
                arg(b)
                hasTrailingBlock()

                chosenCallee(choice)
            }

            a assignableFrom type2("Int")

            solve()

            assertSolutions(
                mapOf(
                    a to "Int32",
                    b to "Fn<Void>",
                    choice to "0",
                ),
            )
        }
    }

    @Test
    fun fnReorderedOverOptional2() = withTypeTestHarness {
        runSolverTest {
            val a = unusedTypeVar("a")
            val b = unusedTypeVar("b")
            val c = unusedTypeVar("c")
            val choice = unusedSimpleVar("choice")

            regularCall {
                callee(sig("fn(Int, (fn (): Void), _? : String): Void"))
                callee(sig("fn(String, (fn (Int): Int)): Void"))

                arg(a)
                arg(b)
                arg(c)
                // no trailing block

                chosenCallee(choice)
            }

            a assignableFrom type2("Int")
            b assignableFrom type2("fn (): Void")
            c assignableFrom type2("String")

            solve()

            assertSolutions(
                mapOf(
                    a to "Int32",
                    b to "Fn<Void>",
                    c to "String",
                    choice to "0",
                ),
            )
        }
    }

    @Test
    fun fnReorderedOverOptional3() = withTypeTestHarness {
        runSolverTest {
            val a = unusedTypeVar("a")
            val b = unusedTypeVar("b")
            val c = unusedTypeVar("c")
            val choice = unusedSimpleVar("choice")

            regularCall {
                callee(sig("fn(Int, (fn (): Void), _? : String): Void"))
                callee(sig("fn(String, (fn (Int): Int)): Void"))

                arg(a)
                arg(b)
                arg(c)
                hasTrailingBlock()

                chosenCallee(choice)
            }

            solve()

            assertSolutions(
                mapOf(
                    a to "Int32",
                    // Argument c is reordered over b despite none of them having type information.
                    c to "Fn<Void>",
                    // Argument b is ordered as the third formal, the optional String
                    b to "String",
                    // Since only callee 0 can support 3 actual arguments, it's chosen.
                    choice to "0",
                ),
            )
        }
    }

    @Test
    fun fnReorderedOverOptional4() = withTypeTestHarness {
        runSolverTest {
            val a = unusedTypeVar("a")
            val b = unusedTypeVar("b")
            val choice = unusedSimpleVar("choice")

            regularCall {
                callee(sig("fn(Int, (fn (): Void), _? : String): Void"))
                callee(sig("fn(String, (fn (Int): Int)): Void"))

                arg(a)
                arg(b)
                hasTrailingBlock()

                chosenCallee(choice)
            }

            b assignableFrom type2("fn (Int): Int")

            solve()

            assertSolutions(
                mapOf(
                    a to "String",
                    b to "Fn<Int32, Int32>",
                    choice to "1",
                ),
            )
        }
    }

    @Test
    fun oneInvalidArgDoesNotPreventCalleeResolution() = withTypeTestHarness {
        runSolverTest {
            val a = unusedTypeVar("a")
            val b = unusedTypeVar("b")
            val calleeIndex = unusedSimpleVar("callee")
            val pass = unusedTypeVar("pass")

            regularCall {
                callee(sig("fn (Int, Int): Int"))
                callee(sig("fn (Int): Int"))
                callee(sig("fn (Float64, Float64): Float64"))
                callee(sig("fn (Float64): Float64"))

                arg(a)
                arg(b)
                chosenCallee(calleeIndex)
                result(pass, null)
            }

            a assignableFrom WellKnownTypes.invalidType2
            b assignableFrom type2("Float64")

            solve()

            assertSolutions(
                mapOf(
                    a to "Float64", // Its valid upper bound from the callee trumps the Invalid lower bound.
                    b to "Float64",
                    calleeIndex to "2",
                    pass to "Float64",
                ),
            )
        }
    }

    @Test
    fun voidContextType() = withTypeTestHarness {
        runSolverTest {
            // Two callees.  One with a void return type and one without.
            // Based on the context type alone, we filter out one callee.
            val voidCallee = sig("fn(): Void")
            val genericCallee = sig("fn<T extends AnyValue>(): T")

            val aIndex = unusedSimpleVar("aI")
            val bIndex = unusedSimpleVar("bI")

            val aTs = unusedSimpleVar("aTs")
            val bTs = unusedSimpleVar("bTs")

            val aPass = unusedTypeVar("aPass")
            val bPass = unusedTypeVar("bPass")

            regularCall {
                callee(voidCallee)
                callee(genericCallee)
                chosenCallee(aIndex)
                typeActualsList(aTs)
                result(aPass, null)
            }

            regularCall {
                callee(voidCallee)
                callee(genericCallee)
                chosenCallee(bIndex)
                typeActualsList(bTs)
                result(bPass, null)
            }

            WellKnownTypes.voidType2 assignableFrom aPass
            WellKnownTypes.stringType2 assignableFrom bPass

            solve()

            assertSolutions(
                mapOf(
                    aIndex to "0",
                    aTs to "[]",
                    aPass to "Void",
                    bIndex to "1",
                    bTs to "[String]",
                    bPass to "String",
                ),
            )
        }
    }

    /** Like [voidContextType] but with explicit *Never* on the output types. */
    @Test
    fun voidishNeverContextType() = withTypeTestHarness {
        runSolverTest {
            // Two callees.  One with a void return type and one without.
            // Based on the context type alone, we filter out one callee.
            val voidCallee = sig("fn(): Never<Void>")
            val genericCallee = sig("fn<T extends AnyValue>(): Never<T>")

            val aIndex = unusedSimpleVar("aI")
            val bIndex = unusedSimpleVar("bI")

            val aTs = unusedSimpleVar("aTs")
            val bTs = unusedSimpleVar("bTs")

            val aPass = unusedTypeVar("aPass")
            val bPass = unusedTypeVar("bPass")

            regularCall {
                callee(voidCallee)
                callee(genericCallee)
                chosenCallee(aIndex)
                typeActualsList(aTs)
                result(aPass, null)
            }

            regularCall {
                callee(voidCallee)
                callee(genericCallee)
                chosenCallee(bIndex)
                typeActualsList(bTs)
                result(bPass, null)
            }

            type2("Void") assignableFrom aPass
            type2("String") assignableFrom bPass

            solve()

            assertSolutions(
                mapOf(
                    aIndex to "0",
                    aTs to "[]",
                    aPass to "Never<Void>",
                    bIndex to "1",
                    bTs to "[String]",
                    bPass to "Never<String>",
                ),
            )
        }
    }

    /** Like [voidContextType] but with explicit *Never* on the output types. */
    @Test
    fun voidishBubblyNeverContextType() = withTypeTestHarness {
        runSolverTest {
            // Two callees.  One with a void return type and one without.
            // Based on the context type alone, we filter out one callee.
            val voidCallee = sig("fn(): Never<Void> throws Bubble")
            val genericCallee = sig("fn<T extends AnyValue>(): Never<T> throws Bubble")

            val aIndex = unusedSimpleVar("aI")
            val bIndex = unusedSimpleVar("bI")

            val aTs = unusedSimpleVar("aTs")
            val bTs = unusedSimpleVar("bTs")

            val aPass = unusedTypeVar("aPass")
            val bPass = unusedTypeVar("bPass")

            val aFail = unusedSimpleVar("aFail")
            val bFail = unusedSimpleVar("bFail")

            regularCall {
                callee(voidCallee)
                callee(genericCallee)
                chosenCallee(aIndex)
                typeActualsList(aTs)
                result(aPass, aFail)
            }

            regularCall {
                callee(voidCallee)
                callee(genericCallee)
                chosenCallee(bIndex)
                typeActualsList(bTs)
                result(bPass, bFail)
            }

            type2("Never<Void>") assignableFrom aPass
            type2("Never<String>") assignableFrom bPass

            solve()

            assertSolutions(
                mapOf(
                    aIndex to "0",
                    aTs to "[]",
                    aPass to "Never<Void>",
                    aFail to "[Bubble]",
                    bIndex to "1",
                    bTs to "[Never<String>]",
                    bPass to "Never<String>",
                    bFail to "[Bubble]",
                ),
            )
        }
    }

    @Test
    fun nullaryFnIntertwined() = withTypeTestHarness {
        runSolverTest {
            // Two calls.   One a nullary never call nested in a variadic any function.
            val outerCallee = sig("fn(...AnyValue): Void")

            // Two callees.  One with a void return type and one without.
            // Based on the context type alone, we filter out one callee.
            val voidInnerCallee = sig("fn(): Never<Void>")
            val genericInnerCallee = sig("fn<T extends AnyValue>(): Never<T>")

            val aIndex = unusedSimpleVar("aI")
            val bIndex = unusedSimpleVar("bI")

            val aTs = unusedSimpleVar("aTs")
            val bTs = unusedSimpleVar("bTs")

            val aPass = unusedTypeVar("aPass")
            val bPass = unusedTypeVar("bPass")

            val bArg = unusedTypeVar("bArg")

            regularCall {
                callee(voidInnerCallee)
                callee(genericInnerCallee)
                chosenCallee(aIndex)
                typeActualsList(aTs)
                result(aPass, null)
            }

            regularCall {
                callee(outerCallee)
                chosenCallee(bIndex)
                typeActualsList(bTs)
                result(bPass, null)
                arg(bArg)
            }

            bArg assignableFrom aPass

            solve()

            assertSolutions(
                mapOf(
                    aIndex to "1", // non-void version because AnyValue is an upper bound.
                    aTs to "[AnyValue]",
                    aPass to "Never<AnyValue>",
                    bIndex to "0",
                    bTs to "[]",
                    bPass to "Void",
                ),
            )
        }
    }

    @Test
    fun wideningAndNarrowingCasts() = withTypeTestHarness(
        """
            |interface Animal<T>;
            |class Cat<T> extends Animal<T>;
        """.trimMargin(),
    ) {
        val sig = sig("fn<asT extends AnyValue>(AnyValue?, Type): asT throws Bubble")
        runSolverTest {
            // myCat as Animal
            val a = unusedTypeVar("a")
            val b = unusedTypeVar("b")
            val animalT = unusedTypeVar("animalT")
            val checkedAnimal = unusedTypeVar("checkedAnimal")
            val chosenWidening = unusedSimpleVar("chosenW")
            val typeActualsWidening = unusedSimpleVar("typeActualsW")
            val passWidening = unusedTypeVar("passW")
            val failWidening = unusedSimpleVar("failW")
            a assignableFrom type2("Cat<String>")
            PartialType.from(
                getDefinition("Animal") as TypeShape,
                listOf(TypeVarRef(animalT, Nullity.NonNull)),
                Nullity.NonNull,
            ) sameAs checkedAnimal
            type2("Cat<String>") relatesTo checkedAnimal
            b assignableFrom type2("Type")
            regularCall {
                callee(sig)
                args(a, b)
                typeActualVars(checkedAnimal)
                chosenCallee(chosenWidening)
                typeActualsList(typeActualsWidening)
                result(passWidening, failWidening)
            }

            // someAnimal as Cat
            val c = unusedTypeVar("c")
            val d = unusedTypeVar("d")
            val catT = unusedTypeVar("catT")
            val checkedCat = unusedTypeVar("checkedCat")
            val chosenNarrowing = unusedSimpleVar("chosenN")
            val typeActualsNarrowing = unusedSimpleVar("typeActualsN")
            val passNarrowing = unusedTypeVar("passN")
            val failNarrowing = unusedSimpleVar("failN")
            a assignableFrom type2("Animal<String>")
            PartialType.from(
                getDefinition("Cat") as TypeShape,
                listOf(TypeVarRef(catT, Nullity.NonNull)),
                Nullity.NonNull,
            ) sameAs checkedCat
            type2("Animal<String>") relatesTo checkedCat
            b assignableFrom type2("Type")
            regularCall {
                callee(sig)
                args(c, d)
                typeActualVars(checkedCat)
                chosenCallee(chosenNarrowing)
                typeActualsList(typeActualsNarrowing)
                result(passNarrowing, failNarrowing)
            }

            solve()

            assertSolutions(
                mapOf(
                    a to "AnyValue?", // This is the input type from the signature, not the type of the input.
                    b to "Type",
                    animalT to "String", // because cats
                    checkedAnimal to "Animal<String>", // Can be substituted for the incomplete `as` operand.
                    chosenWidening to "0",
                    typeActualsWidening to "[Animal<String>]",
                    passWidening to "Animal<String>",
                    failWidening to "[Bubble]",

                    c to "AnyValue?",
                    d to "Type",
                    catT to "String",
                    checkedCat to "Cat<String>",
                    chosenNarrowing to "0",
                    typeActualsNarrowing to "[Cat<String>]",
                    passNarrowing to "Cat<String>",
                    failNarrowing to "[Bubble]",
                ),
            )
        }
    }

    private fun runSolverTest(debugPath: String? = null, body: TypeSolverTestHarness.() -> Unit) {
        val debugHookOut = debugPath?.let {
            Files.newBufferedWriter(Path.of(debugPath))
        }
        try {
            debugHookOut.use {
                TypeSolverTestHarness(debugHookOut = debugHookOut).body()
            }
        } finally {
            if (debugPath != null) {
                printErr("Wrote Graphviz debugging info to $debugPath!")
            }
        }
    }
}

// A DSL for setting up a system of type solver constraints
internal class TypeSolverTestHarness(debugHookOut: Appendable?) : AbstractSolverVarNamer() {
    val typeSolver =
        TypeSolver(
            solverVarNamer = this,
            debugHook = debugHookOut?.let { D3TypeSolverDebugHook(it) },
        )

    infix fun TypeBoundary.assignableFrom(right: TypeBoundary) {
        typeSolver.assignable(left = this@assignableFrom, right = right)
    }

    infix fun TypeBoundary.sameAs(right: TypeBoundary) {
        typeSolver.sameAs(this@sameAs, right)
    }

    infix fun TypeBoundary.relatesTo(right: TypeBoundary) {
        typeSolver.relatesTo(this@relatesTo, right)
    }

    fun regularCall(
        body: CallBuilder.() -> Unit,
    ) {
        val callBuilder = CallBuilder()
        callBuilder.body()
        callBuilder.addCallConstraint(typeSolver)
    }

    fun solution(v: Solvable): Solution? = typeSolver[v]

    fun assertSolutions(
        /** Maps [Solvable]s to the expected stringification of their solution */
        m: Map<Solvable, String>,
    ) {
        val got = m.keys.associateWith { key ->
            solution(key)?.let { stripSuffixes("$it") }
        }
        if (m != got) {
            // assertEquals puts map output on one line which does not lend itself to diffing.
            fun diffableStr(m: Map<Solvable, String?>) = buildString {
                for ((k, v) in m) {
                    append("$k -> $v\n")
                }
            }
            assertEquals(diffableStr(m), diffableStr(got))
            fail("$m != $got")
        }
    }

    fun solve() {
        typeSolver.solve()
    }

    @Suppress("unused") // May add calls when interactively debugging
    fun debugConstraints() {
        console.group("Constraints") {
            typeSolver.allConstraintsForDebug.forEach { cons ->
                console.group("${cons::class.simpleName}") {
                    TemperFormattingHints.makeFormattingTokenSink(
                        TextOutputTokenSink(console.textOutput),
                        singleLine = false,
                    ).use {
                        cons.renderTo(it)
                    }
                }
            }
        }
    }

    @Suppress("unused") // May add calls when interactively debugging
    fun debugSolutions() {
        console.group("Solutions") {
            typeSolver.allSolutions.forEach { (k, v) ->
                console.log("- $k -> $v")
            }
        }
    }

    internal inner class CallBuilder {
        private val callees = mutableListOf<Callee>()
        private var choiceVar: SimpleVar? = null
        private val explicitTypeArgs = mutableListOf<Type2>()
        private var typeArgVars = mutableListOf<TypeVar>()
        private val args = mutableListOf<TypeVar>()
        private var hasTrailingBlock = false
        private var callPassVar: TypeVar? = null
        private var callFailVar: SimpleVar? = null
        private var typeActualsVar: SimpleVar? = null

        fun chosenCallee(v: SimpleVar) {
            check(choiceVar == null)
            choiceVar = v
        }

        fun callee(fn: MacroValue) =
            callee(fn.sigs!!.soleElement as Signature2)

        fun callee(sig: Signature2, priority: CalleePriority = CalleePriority.Default) =
            callee(Callee(sig, priority))

        fun callee(callee: Callee) {
            callees.add(callee)
        }

        fun arg(v: TypeVar) {
            args.add(v)
        }

        fun args(vararg vs: TypeVar) {
            for (v in vs) {
                arg(v)
            }
        }

        fun hasTrailingBlock(newValue: Boolean = true) {
            this.hasTrailingBlock = newValue
        }

        fun result(
            pass: TypeVar?,
            fail: SimpleVar?,
        ) {
            check(this.callPassVar == null)
            this.callPassVar = pass
            this.callFailVar = fail
        }

        fun typeActualsList(actualsVar: SimpleVar) {
            this.typeActualsVar = actualsVar
        }

        fun typeActualVars(vararg actualVars: TypeVar) {
            this.typeArgVars.addAll(actualVars)
        }

        fun addCallConstraint(typeSolver: TypeSolver) = typeSolver.called(
            callees = callees.toList(),
            calleeChoice = choiceVar ?: unusedSimpleVar("calleeChoice"),
            explicitTypeArgs = explicitTypeArgs.toList(),
            typeArgVars = if (typeArgVars.isNotEmpty()) { typeArgVars.toList() } else { null },
            args = args.toList(),
            hasTrailingBlock = hasTrailingBlock,
            typeActuals = typeActualsVar,
            callPass = callPassVar ?: unusedTypeVar("callPass"),
            callFail = callFailVar ?: unusedSimpleVar("callFail"),
        )
    }
}

internal fun makeTypeFormalsForTest(vararg nameTextAndBounds: Pair<String, StaticType>): List<TypeParamRef> =
    nameTextAndBounds.map { (nameText, upperBound) ->
        val tf = TypeFormal(
            unknownPos,
            BuiltinName(nameText),
            Symbol(nameText),
            Variance.Invariant,
            WellKnownTypes.anyValueTypeDefinition.mutationCount,
            listOf(upperBound as NominalType),
        )
        MkType2(tf).get()
    }

/** Fn__123 -> Fn.   The latter is introduced by hackOldStyleToNew for function types */
private fun stripSuffixes(s: String): String {
    return s.replace(suffixPattern, "")
}
private var suffixPattern = Regex("__\\d+\\b")
