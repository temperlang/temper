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
                |class BadImuProp extends Imu {
                |  public p: Neither;
                |}
                |-> Class BadImuProp extends Imu but property p has type Neither__5 which is not Imu!
                |
                |BadImuVar:
                |class BadImuVar extends Imu {
                |  public var p: ImuOneProperty;
                |}
                |-> Class BadImuVar extends Imu but has a `var` property, p!
                |
                |BadImuGeneric1:
                |class BadImuGeneric1<T extends PartialImu> extends Imu {
                |  public p: T;
                |}
                |-> Class BadImuGeneric1 extends Imu but property p has type T__9 which is not Imu!
                |
                |BadImuGeneric2:
                |class BadImuGeneric2<T> extends Imu {
                |  public p: T;
                |}
                |-> Class BadImuGeneric2 extends Imu but property p has type T__11 which is not Imu!
                |
                |BadImuContravariant:
                |class BadImuContravariant<@in T extends Imu> extends Imu {
                |  public p: List<T>;
                |}
                |-> Class BadImuContravariant extends Imu but property p: List<T__13> uses contravariant type T__13!
                |
                |BadPartialImuNoTypeArgs:
                |class BadPartialImuNoTypeArgs extends PartialImu {}
                |-> Type BadPartialImuNoTypeArgs extends PartialImu but has no type parameters!
                |
                |BadPartialImuClass:
                |class BadPartialImuClass<T> extends PartialImu {
                |  public p: T; // ok
                |  public q: ListBuilder<T>; // not imu under any parameterization
                |}
                |-> Class BadPartialImuClass extends PartialImu but property q has type ListBuilder<T__34> which is not Imu!
                |
                |BadPartialImuDeepInterface1:
                |interface BadPartialImuDeepInterface1<T> extends PartialImuInterfaceTwoArgs<T, Neither> {}
                |-> PartialImu interface BadPartialImuDeepInterface1 could have Imu parameters but it extends PartialImuInterfaceTwoArgs__21<T__36, Neither__5> which cannot be Imu because Neither__5 is not!
                |
                |BadPartialImuDeepInterface2:
                |interface BadPartialImuDeepInterface2<T>
                |extends PartialImuInterfaceOneArg<T> & PartialImuInterfaceTwoArgs<T, Neither> {}
                |-> PartialImu interface BadPartialImuDeepInterface2 could have Imu parameters but it extends PartialImuInterfaceTwoArgs__21<T__38, Neither__5> which cannot be Imu because Neither__5 is not!
                |
                |BadPartialImuViaUpcast:
                |interface BadPartialImuViaUpcast<T, U> extends PartialImuInterfaceOneArg<List<U>> {}
                |-> PartialImu interface BadPartialImuViaUpcast's type parameter <T> would not be Imu when cast to its effectively Imu super-type PartialImuInterfaceOneArg__19<List<U__41>> because T__40 is not Imu!
                |
                |BadImuClassUsingPartialImu:
                |class BadImuClassUsingPartialImu<T> extends Imu {
                |  public p: List<Neither>;
                |}
                |-> Expected Imu type but got Neither__5!
                |-> Class BadImuClassUsingPartialImu extends Imu but property p has type List<Neither__5> which is not Imu!
                |
                |BadPartialImuInterfaceHidesParam:
                |interface BadPartialImuInterfaceHidesParam<T> extends PartialImuInterfaceOneArg<ListBuilder<T>> {}
                |-> PartialImu interface BadPartialImuInterfaceHidesParam could have Imu parameters but it extends PartialImuInterfaceOneArg__19<ListBuilder<T__48>> which cannot be Imu because ListBuilder<T__48> is not!
            """.trimMargin().trimEnd(),
            got.trimEnd(),
        )
    }

    companion object {
        private val testSourceCode = """
            |class ImuNoProperties extends Imu {}
            |class ImuOneProperty extends Imu {
            |  public p: ImuNoProperties;
            |}
            |class ImuRecursive extends Imu {
            |  public p: ImuRecursive?;
            |}
            |class ImuGeneric<T extends Imu> {
            |  public p: T;
            |}
            |class Neither {}
            |class BadImuProp extends Imu {
            |  public p: Neither;
            |}
            |class BadImuVar extends Imu {
            |  public var p: ImuOneProperty;
            |}
            |class BadImuGeneric1<T extends PartialImu> extends Imu {
            |  public p: T;
            |}
            |class BadImuGeneric2<T> extends Imu {
            |  public p: T;
            |}
            |class BadImuContravariant<@in T extends Imu> extends Imu {
            |  public p: List<T>;
            |}
            |class ImuWithParameterizedPartialList<T extends Imu> extends Imu {
            |  public p: List<T>;
            |}
            |
            |class BadPartialImuNoTypeArgs extends PartialImu {}
            |class PartialImuDirectClass<T> extends PartialImu {
            |  public p: T;
            |}
            |interface PartialImuInterfaceOneArg<T> extends PartialImu {}
            |interface PartialImuInterfaceTwoArgs<T, U> extends PartialImu {}
            |interface PartialImuInterfaceTwoArgsOneImu<T, U extends Imu> extends PartialImu {}
            |interface PartialImuDeepInterface1<T> extends PartialImuInterfaceTwoArgs<T, T> {}
            |interface PartialImuDeepInterface2<T> extends PartialImuInterfaceOneArg<List<T>> {}
            |interface PartialImuDeepInterface3<T> extends PartialImuInterfaceTwoArgs<T, Null> {}
            |class BadPartialImuClass<T> extends PartialImu {
            |  public p: T; // ok
            |  public q: ListBuilder<T>; // not imu under any parameterization
            |}
            |interface BadPartialImuDeepInterface1<T> extends PartialImuInterfaceTwoArgs<T, Neither> {}
            |
            |interface BadPartialImuDeepInterface2<T>
            |extends PartialImuInterfaceOneArg<T> & PartialImuInterfaceTwoArgs<T, Neither> {}
            |
            |interface BadPartialImuViaUpcast<T, U> extends PartialImuInterfaceOneArg<List<U>> {}
            |
            |class ImuClassUsesPartialImu extends Imu {
            |  public p: PartialImuInterfaceOneArg<ImuOneProperty>;
            |}
            |class PartialImuClassUsingPartialImuContingently<T> extends PartialImu {
            |  public p: List<T>;
            |}
            |class BadImuClassUsingPartialImu<T> extends Imu {
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
