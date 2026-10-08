@file:Suppress("MaxLineLength")

package lang.temper.be.elixir

import lang.temper.common.ListBackedLogSink
import lang.temper.common.console
import lang.temper.common.testCodeLocation
import lang.temper.common.testModuleName
import lang.temper.frontend.ModuleSource
import lang.temper.frontend.staging.ModuleAdvancer
import lang.temper.lexer.StandaloneLanguageConfig
import lang.temper.stage.Stage
import kotlin.test.Test
import kotlin.test.assertEquals

class ElixirActorCheckerTest {
    @Test
    fun sendableBoundariesPass() = assertActorDiagnostics(
        """
            |@imu class Point(public x: Int, public y: Int) {}
            |class Box(public var n: Int) {}
            |@actor class Counter(private var n: Int) {
            |  public bump(): Int { n += 1; n }
            |}
            |@actor class Account(
            |  public name: String,
            |  private var balance: Int,
            |) {
            |  private history: ListBuilder<Int> = new ListBuilder<Int>();
            |  public var limit: Int = 100;
            |  public deposit(amount: Int, note: String?): Int { balance += amount; balance }
            |  public withdraw(amount: Int): Int throws Bubble {
            |    if (amount > balance) { bubble() }
            |    balance -= amount;
            |    balance
            |  }
            |  public reset(): Void { balance = 0 }
            |  public flags(): Map<String, Boolean> { new Map([new Pair("open", true)]) }
            |  public rate(): Float64 { 1.5 }
            |  public at(p: Point): Point { p }
            |  public log(): List<Int> { history.toList() }
            |  public counter(c: Counter): Counter { c }
            |  public counters(cs: List<Counter>): List<Counter> { cs }
            |  public later(p: Promise<Int>): Promise<Counter>? { null }
            |  public get total(): Int { balance }
            |  public set total(value: Int): Void { balance = value }
            |  private box(b: Box): Box { b }
            |  private each(f: fn (Int): Int): Void {}
            |}
            |@actor class Holder<@imu T>(private var item: T) {
            |  public get(): T { item }
            |  public put(newItem: T): Void { item = newItem }
            |}
        """,
        "",
    )

    // `Exposes` gets one message, not also one for its constructor's `box`
    // parameter: both are at the same position.
    @Test
    fun nonSendableMembersFail() = assertActorDiagnostics(
        """
            |class Box(public var n: Int) {}
            |@actor class Takes(private var n: Int) {
            |  public put(b: Box): Void { n = b.n }
            |}
            |@actor class Gives(private var n: Int) {
            |  public box(): Box { new Box(n) }
            |}
            |@actor class Exposes(public box: Box) {}
            |@actor class Nested(private var n: Int) {
            |  public boxes(bs: List<Box>): Int { n }
            |  public later(p: Promise<Box>): Void {}
            |  public unbox(): List<Box> { [] }
            |}
            |@actor class Calls(private var n: Int) {
            |  public each(f: fn (Int): Int): Void {}
            |}
            |@actor class Builds(private var n: Int) {
            |  public fill(b: ListBuilder<Int>): Void {}
            |  public view(l: Listed<Int>): Void {}
            |  public any(x: AnyValue): Void {}
            |}
            |@actor class Generic<T>(private var item: T) {
            |  public get(): T { item }
            |}
            |@actor class Accessors(private var n: Int) {
            |  public get box(): Box { new Box(n) }
            |  public set box(b: Box): Void { n = b.n }
            |}
        """,
        """
            |`b: Box` 3:14: Actor class Takes: parameter b of method put has type Box__0, which is not sendable!
            |`Box` 6:17: Actor class Gives: the result of method box has type Box__0, which is not sendable!
            |`box: Box` 8:29: Actor class Exposes: public property box has type Box__0, which is not sendable!
            |`bs: List<Box>` 10:16: Actor class Nested: parameter bs of method boxes has type List<Box__0>, which is not sendable because Box__0 is not!
            |`p: Promise<Box>` 11:16: Actor class Nested: parameter p of method later has type Promise<Box__0>, which is not sendable because Box__0 is not!
            |`List<Box>` 12:19: Actor class Nested: the result of method unbox has type List<Box__0>, which is not sendable because Box__0 is not!
            |`f: fn (Int): Int` 15:15: Actor class Calls: parameter f of method each has type fn (Int32): Int32, which is not sendable!
            |`b: ListBuilder<Int>` 18:15: Actor class Builds: parameter b of method fill has type ListBuilder<Int32>, which is not sendable!
            |`l: Listed<Int>` 19:15: Actor class Builds: parameter l of method view has type Listed<Int32>, which is not sendable!
            |`x: AnyValue` 20:14: Actor class Builds: parameter x of method any has type AnyValue, which is not sendable!
            |`T` 23:17: Actor class Generic: the result of method get has type T__17, which is not sendable!
            |` item: T` 22:36: Actor class Generic: parameter item of constructor has type T__17, which is not sendable!
            |`Box` 26:21: Actor class Accessors: the result of getter box has type Box__0, which is not sendable!
            |`b: Box` 27:18: Actor class Accessors: parameter b of setter box has type Box__0, which is not sendable!
        """,
    )

    @Test
    fun misplacedActorFails() = assertActorDiagnostics(
        """
            |@actor @imu class ActorImu(public n: Int) {}
            |@actor @partialImu class ActorPartialImu<T>(public item: T) {}
            |@actor interface ActorInterface {}
            |@actor let x = 1;
            |@actor let f(): Int { 1 }
            |class Plain {
            |  @actor public m(): Int { 1 }
            |}
        """,
        """
            |`actor` 1:2: Class ActorImu cannot be both @actor and @imu!
            |`actor` 2:2: Class ActorPartialImu cannot be both @actor and @partialImu!
            |`item: T` 2:52: Actor class ActorPartialImu: public property item has type T__2, which is not sendable!
            |`actor` 3:2: Interface ActorInterface cannot be @actor; only a class can!
            |`actor` 4:2: @actor applies only to classes!
            |`actor` 5:2: @actor applies only to classes!
            |`actor` 7:4: @actor applies only to classes!
        """,
    )

    // A type the typer could not resolve has already been reported, but the
    // actor still fails, so be-elixir does not translate it.
    @Test
    fun invalidTypesAreNotReportedAgain() = assertActorDiagnostics(
        """
            |@actor class Undeclared(private var n: Int) {
            |  public put(x: Nope): Void {}
            |}
        """,
        "",
        wantPass = false,
    )

    private fun assertActorDiagnostics(source: String, want: String, wantPass: Boolean = want.isBlank()) {
        val code = source.trimMargin()
        val logSink = ListBackedLogSink()
        val moduleAdvancer = ModuleAdvancer(logSink)
        val module = moduleAdvancer.createModule(testModuleName, console)
        module.deliverContent(
            ModuleSource(
                fetchedContent = code,
                filePath = testCodeLocation,
                languageConfig = StandaloneLanguageConfig,
            ),
        )
        moduleAdvancer.advanceModules(stopBefore = Stage.Run)
        assertEquals(Stage.GenerateCode, module.stageCompleted)
        val passes = ElixirActorChecker(logSink).check(module.generatedCode!!)

        val got = logSink.allEntries
            .filter { it.template is ElixirActorMessage }
            .joinToString("\n") { entry ->
                val pos = entry.pos
                val excerpt = code.substring(pos.left, pos.right).lineSequence().first()
                val line = code.substring(0, pos.left).count { it == '\n' } + 1
                val column = pos.left - (code.lastIndexOf('\n', pos.left - 1) + 1) + 1
                "`$excerpt` $line:$column: ${entry.messageText}"
            }
        assertEquals(want.trimMargin().trim(), got)
        assertEquals(wantPass, passes)
    }
}
