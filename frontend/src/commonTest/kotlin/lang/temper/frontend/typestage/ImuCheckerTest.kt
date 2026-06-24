@file:Suppress("MaxLineLength")

package lang.temper.frontend.typestage

import lang.temper.common.CustomValueFormatter
import lang.temper.common.ListBackedLogSink
import lang.temper.common.Log
import lang.temper.common.console
import lang.temper.common.testCodeLocation
import lang.temper.common.testModuleName
import lang.temper.format.ConsoleBackedContextualLogSink
import lang.temper.frontend.ModuleSource
import lang.temper.frontend.staging.ModuleAdvancer
import lang.temper.lexer.StandaloneLanguageConfig
import lang.temper.name.ResolvedParsedName
import lang.temper.stage.Stage
import lang.temper.type.TypeShape
import lang.temper.value.DeclTree
import lang.temper.value.typeDeclSymbol
import lang.temper.value.typeShapeAtLeafOrNull
import kotlin.test.Test
import kotlin.test.assertEquals

class ImuCheckerTest {
    // The bad ones do not pass the checker
    @Test
    fun checkImus() {
        val logSink = ConsoleBackedContextualLogSink(
            console,
            null,
            null,
            CustomValueFormatter.Nope,
        )
        val (wantPassing, wantFailing) = typesByName.keys.toList().sorted().partition {
            "Bad" !in it
        }
        val checker = ImuChecker(logSink)
        val (passing, failing) = checker.check(typesByName.values)
        val gotPassing = passing.map { it.simpleName.nameText }.sorted()
        val gotFailing = failing.map { it.simpleName.nameText }.sorted()
        assertEquals(
            wantPassing to wantFailing,
            gotPassing to gotFailing,
        )
        assertEquals(typesByName.size, passing.size + failing.size)
        assertEquals(true, typesByName.size > 6)
    }

    // The bad ones have log messages explaining why.
    @Test
    fun checkReasons() {
        val logSink = ListBackedLogSink()
        val got = buildString {
            for ((typeName, type) in typesByName) {
                val checker = ImuChecker(logSink)
                val (passing, failing) = checker.check(listOf(type))
                assertEquals(1, passing.size + failing.size)

                val logEntries = logSink.allEntries
                val passed = passing.isNotEmpty()
                if (logEntries.isEmpty() && passed) {
                    continue
                }

                if (this.isNotEmpty()) { append('\n') }
                append("$typeName:")
                if (logEntries.isEmpty() != passed) {
                    // We expect log entries for failing
                    if (passed) {
                        append(" Got unexpected messages")
                    } else {
                        append(" Expected error messages")
                    }
                }
                append('\n')

                if (logEntries.isNotEmpty()) {
                    val pos = type.pos
                    append(testSourceCode, pos.left, pos.right)
                    append('\n')
                    for (logEntry in logEntries) {
                        append("-> ")
                        if (logEntry.level != Log.Error) {
                            append("${logEntry.level}").append(": ")
                        }
                        append(logEntry.messageText)
                        append('\n')
                    }
                }

                logSink.clear()
            }
        }
        assertEquals(
            """
                |BadImuProp:
                |class BadImuProp {
                |  public p: Neither;
                |}
                |-> Class BadImuProp claims imu but property p has type Neither__5 which is not imu!
                |
                |BadImuVar:
                |class BadImuVar {
                |  public var p: ImuOneProperty;
                |}
                |-> Class BadImuVar claims imu but has a `var` property, p!
                |
                |BadImuGeneric1:
                |class BadImuGeneric1<@partialImu T> {
                |  public p: T;
                |}
                |-> Class BadImuGeneric1 claims imu but property p has type T__9 which is not imu!
                |
                |BadImuGeneric2:
                |class BadImuGeneric2<T> {
                |  public p: T;
                |}
                |-> Class BadImuGeneric2 claims imu but property p has type T__11 which is not imu!
                |
                |BadImuContravariant:
                |class BadImuContravariant<@imu @in T> {
                |  public p: List<T>;
                |}
                |-> Class BadImuContravariant claims imu but property p: List<T__13> uses contravariant type T__13!
                |
                |BadPartialImuNoTypeArgs:
                |class BadPartialImuNoTypeArgs {}
                |-> Type BadPartialImuNoTypeArgs claims partialImu but has no type parameters!
                |
                |BadPartialImuClass:
                |class BadPartialImuClass<T> {
                |  public p: T; // ok
                |  public q: ListBuilder<T>; // not imu under any parameterization
                |}
                |-> Class BadPartialImuClass claims partialImu but property q has type ListBuilder<T__35> which is not imu!
                |
                |BadPartialImuDeepInterface1:
                |interface BadPartialImuDeepInterface1<T> extends PartialImuInterfaceTwoArgs<T, Neither> {}
                |-> PartialImu interface BadPartialImuDeepInterface1 could have imu parameters but it extends PartialImuInterfaceTwoArgs__22<T__37, Neither__5> which cannot be imu because Neither__5 is not!
                |
                |BadPartialImuDeepInterface2:
                |interface BadPartialImuDeepInterface2<T>
                |extends PartialImuInterfaceOneArg<T> & PartialImuInterfaceTwoArgs<T, Neither> {}
                |-> PartialImu interface BadPartialImuDeepInterface2 could have imu parameters but it extends PartialImuInterfaceTwoArgs__22<T__39, Neither__5> which cannot be imu because Neither__5 is not!
                |
                |BadImuClassWithImuDeepInterface:
                |class BadImuClassWithImuDeepInterface extends ImuDeepInterface {
                |  public n: Neither;
                |}
                |-> Class BadImuClassWithImuDeepInterface claims imu but property n has type Neither__5 which is not imu!
                |
                |BadPartialImuClassWithPartialImuDeepInterface:
                |class BadPartialImuClassWithPartialImuDeepInterface<T> extends PartialImuDeepInterface1<T> {
                |  public n: Neither;
                |}
                |-> Class BadPartialImuClassWithPartialImuDeepInterface claims partialImu but property n has type Neither__5 which is not imu!
                |
                |BadPartialImuViaUpcast:
                |interface BadPartialImuViaUpcast<T, U> extends PartialImuInterfaceOneArg<List<U>> {}
                |-> PartialImu interface BadPartialImuViaUpcast's type parameter <T> would not be imu when cast to its effectively Imu super-type PartialImuInterfaceOneArg__20<List<U__46>> because T__45 is not imu!
                |
                |BadPartialImuViaUpcast2:
                |interface BadPartialImuViaUpcast2<T, U> extends PartialImuInterfaceOneArg<U> {}
                |-> PartialImu interface BadPartialImuViaUpcast2's type parameter <T> would not be imu when cast to its effectively Imu super-type PartialImuInterfaceOneArg__20<U__49> because T__48 is not imu!
                |
                |BadPartialImuViaUpcast3:
                |interface BadPartialImuViaUpcast3<T> extends PartialImuInterfaceNoTypeArgs {}
                |-> PartialImu interface BadPartialImuViaUpcast3's type parameter <T> would not be imu when cast to its effectively Imu super-type PartialImuInterfaceNoTypeArgs__16 because T__51 is not imu!
                |
                |BadPartialImuClassViaUpcast:
                |class BadPartialImuClassViaUpcast<T, U>(
                |  public prop: T,
                |  public prop2: U,
                |) extends PartialImuInterfaceOneArg<List<U>> {}
                |-> Class BadPartialImuClassViaUpcast claims partialImu but property prop has type T__56 which is not imu!
                |
                |BadPartialImuClassViaUpcast2:
                |class BadPartialImuClassViaUpcast2<T>(public prop: T) extends PartialImuInterfaceNoTypeArgs {}
                |-> Class BadPartialImuClassViaUpcast2 claims partialImu but property prop has type T__59 which is not imu!
                |
                |BadImuClassUsingPartialImu:
                |class BadImuClassUsingPartialImu<T> {
                |  public p: List<Neither>;
                |}
                |-> Expected imu type but got Neither__5!
                |-> Class BadImuClassUsingPartialImu claims imu but property p has type List<Neither__5> which is not imu!
                |
                |BadPartialImuInterfaceHidesParam:
                |interface BadPartialImuInterfaceHidesParam<T> extends PartialImuInterfaceOneArg<ListBuilder<T>> {}
                |-> PartialImu interface BadPartialImuInterfaceHidesParam could have imu parameters but it extends PartialImuInterfaceOneArg__20<ListBuilder<T__74>> which cannot be imu because ListBuilder<T__74> is not!
            """.trimMargin().trimEnd(),
            got.trimEnd(),
        )
    }

    companion object {
        private val testSourceCode = """
            |@imu class ImuNoProperties {}
            |@imu class ImuOneProperty {
            |  public p: ImuNoProperties;
            |}
            |@imu class ImuRecursive {
            |  public p: ImuRecursive?;
            |}
            |@imu class ImuGeneric<@imu T> {
            |  public p: T;
            |}
            |class Neither {}
            |@imu class BadImuProp {
            |  public p: Neither;
            |}
            |@imu class BadImuVar {
            |  public var p: ImuOneProperty;
            |}
            |@imu class BadImuGeneric1<@partialImu T> {
            |  public p: T;
            |}
            |@imu class BadImuGeneric2<T> {
            |  public p: T;
            |}
            |@imu class BadImuContravariant<@imu @in T> {
            |  public p: List<T>;
            |}
            |@imu class ImuWithParameterizedPartialList<@imu T> {
            |  public p: List<T>;
            |}
            |
            |@partialImu interface PartialImuInterfaceNoTypeArgs {}
            |@partialImu class BadPartialImuNoTypeArgs {}
            |@partialImu class PartialImuDirectClass<T> {
            |  public p: T;
            |}
            |@partialImu interface PartialImuInterfaceOneArg<T> {}
            |@partialImu interface PartialImuInterfaceTwoArgs<T, U> {}
            |@partialImu interface PartialImuInterfaceTwoArgsOneImu<T, @imu U> {}
            |interface PartialImuDeepInterface1<T> extends PartialImuInterfaceTwoArgs<T, T> {}
            |interface PartialImuDeepInterface2<T> extends PartialImuInterfaceOneArg<List<T>> {}
            |interface PartialImuDeepInterface3<T> extends PartialImuInterfaceTwoArgs<T, Null> {}
            |@partialImu class BadPartialImuClass<T> {
            |  public p: T; // ok
            |  public q: ListBuilder<T>; // not imu under any parameterization
            |}
            |interface BadPartialImuDeepInterface1<T> extends PartialImuInterfaceTwoArgs<T, Neither> {}
            |
            |interface BadPartialImuDeepInterface2<T>
            |extends PartialImuInterfaceOneArg<T> & PartialImuInterfaceTwoArgs<T, Neither> {}
            |
            |interface ImuDeepInterface extends ImuNoProperties {}
            |class BadImuClassWithImuDeepInterface extends ImuDeepInterface {
            |  public n: Neither;
            |}
            |class BadPartialImuClassWithPartialImuDeepInterface<T> extends PartialImuDeepInterface1<T> {
            |  public n: Neither;
            |}
            |
            |interface BadPartialImuViaUpcast<T, U> extends PartialImuInterfaceOneArg<List<U>> {}
            |interface BadPartialImuViaUpcast2<T, U> extends PartialImuInterfaceOneArg<U> {}
            |interface BadPartialImuViaUpcast3<T> extends PartialImuInterfaceNoTypeArgs {}
            |interface PartialImuViaUpcast4<@imu T> extends PartialImuInterfaceNoTypeArgs {}
            |@imu class ImuClassThatWouldBeOk(public prop: PartialImuInterfaceNoTypeArgs) {}
            |class BadPartialImuClassViaUpcast<T, U>(
            |  public prop: T,
            |  public prop2: U,
            |) extends PartialImuInterfaceOneArg<List<U>> {}
            |class BadPartialImuClassViaUpcast2<T>(public prop: T) extends PartialImuInterfaceNoTypeArgs {}
            |class PartialImuClassViaUpcast<@imu T>(public prop: T) extends PartialImuInterfaceNoTypeArgs {}
            |class PartialImuClassViaUpcast2<T> extends PartialImuInterfaceNoTypeArgs {
            |  public hi(thing: T): T { thing }
            |}
            |class PartialImuClassViaUpcast3<T> extends ImuDeepInterface {
            |  public hi(thing: T): T { thing }
            |}
            |
            |@imu class ImuClassUsesPartialImu {
            |  public p: PartialImuInterfaceOneArg<ImuOneProperty>;
            |}
            |@partialImu class PartialImuClassUsingPartialImuContingently<T> {
            |  public p: List<T>;
            |}
            |@imu class BadImuClassUsingPartialImu<T> {
            |  public p: List<Neither>;
            |}
            |interface BadPartialImuInterfaceHidesParam<T> extends PartialImuInterfaceOneArg<ListBuilder<T>> {}
        """.trimMargin()

        private val typesByName: Map<String, TypeShape>

        init {
            val logSink = ConsoleBackedContextualLogSink(
                console,
                null,
                null,
                CustomValueFormatter.Nope,
            )
            val moduleAdvancer = ModuleAdvancer(logSink)
            val module = moduleAdvancer.createModule(testModuleName, console)
            val moduleSource = ModuleSource(
                fetchedContent = testSourceCode,
                filePath = testCodeLocation,
                languageConfig = StandaloneLanguageConfig,
            )
            module.deliverContent(moduleSource)
            moduleAdvancer.advanceModules(stopBefore = Stage.Run)
            check(module.stageCompleted == Stage.GenerateCode) {
                "Test module advanced to ${module.stageCompleted}"
            }

            val typesByName = buildMap {
                val root = module.treeForDebug!!
                for (child in root.children) {
                    if (child is DeclTree) {
                        child.parts!!.metadataSymbolMap[typeDeclSymbol]?.target
                            ?.typeShapeAtLeafOrNull?.let {
                                this[it.simpleName.nameText] = it
                            }
                    }
                }
            }
            this.typesByName = typesByName
        }
    }
}

private val TypeShape.simpleName get() = (name as ResolvedParsedName).baseName
