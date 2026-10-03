package lang.temper.be.java

import lang.temper.be.Backend
import lang.temper.be.gatherFilesSync
import lang.temper.common.ListBackedLogSink
import lang.temper.fs.MemoryFileSystem
import lang.temper.lexer.Genre
import lang.temper.log.filePath
import java.io.File
import java.net.URLClassLoader
import java.nio.file.Files
import javax.tools.ToolProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Generates an `@actor` class, compiles it with the temper-core runtime and
 * calls it from several host Java threads at once, which is what the actor
 * guards against on Java: Temper code alone runs one async step at a time.
 */
class JavaActorHostThreadTest {
    @Test
    fun hostThreadsTakeTurns() {
        assertEquals(
            listOf(
                "deposits: total=1016000 expected 1016000",
                "self call: twice(5) -> 10",
                "callback: b.ping(c) -> 11",
                "closure: viaClosure(3) -> 14",
                "cycle: actor call cycle | ok 10",
                "bubble: bubbled, then deposit -> 7",
                "inherited: bumpTwice x 8000 -> 16000, odd totals seen 0",
            ).joinToString("\n"),
            runHost(),
        )
    }

    private fun runHost(): String {
        val logSink = ListBackedLogSink()
        val outputRoot = lang.temper.be.generateCode(
            inputs = listOf(filePath("test", "test.temper") to TEMPER_SOURCE),
            factory = JavaBackend.Java17,
            backendConfig = Backend.Config.production,
            genre = Genre.Library,
            moduleResultNeeded = false,
            logSink = logSink,
        )
        val generated = (outputRoot.fs as MemoryFileSystem).gatherFilesSync { path ->
            path.lastOrNull()?.fullName?.endsWith(".java") == true
        }
        assertTrue(generated.isNotEmpty(), "${logSink.allEntries}")

        val dir = Files.createTempDirectory("actor-host").toFile()
        try {
            val sources = mutableListOf<File>()
            fun put(relPath: String, content: String) {
                val file = File(dir, "src/$relPath")
                file.parentFile.mkdirs()
                file.writeText(content)
                sources.add(file)
            }
            for ((path, content) in generated) {
                put(path.segments.joinToString("/") { it.fullName }, content)
            }
            for (resource in JavaBackend.Java17.immediateLibraryResources) {
                put("temper-core/" + resource.rsrcPath.segments.joinToString("/") { it.fullName }, resource.load())
            }
            put("host/Host.java", HOST_SOURCE)

            val classes = File(dir, "classes").also { it.mkdirs() }
            val javac = assertNotNull(ToolProvider.getSystemJavaCompiler(), "tests need a JDK, not a JRE")
            val args = listOf("-nowarn", "-d", classes.path) + sources.map { it.path }
            val status = javac.run(null, null, System.err, *args.toTypedArray())
            assertEquals(0, status, "javac failed")

            URLClassLoader(arrayOf(classes.toURI().toURL()), ClassLoader.getPlatformClassLoader()).use { loader ->
                return loader.loadClass("host.Host").getMethod("run").invoke(null) as String
            }
        } finally {
            dir.deleteRecursively()
        }
    }
}

private val TEMPER_SOURCE = """
    |@imu export interface Gate { pass(): Void; }
    |
    |export interface Bumpable {
    |  bump(): Void;
    |  bumpTwice(): Void { bump(); bump(); }
    |}
    |
    |@actor export class Account extends Bumpable {
    |  private var balance: Int = 0;
    |  public get total(): Int { balance }
    |  // Reads, works a little, then writes: a lost update if turns overlap.
    |  public deposit(n: Int): Int {
    |    let before = balance;
    |    var i = 0;
    |    var spin = 0;
    |    while (i < 200) { spin += i; i += 1; }
    |    balance = before + n + (spin - spin);
    |    balance
    |  }
    |  public bump(): Void {
    |    let before = balance;
    |    var i = 0;
    |    var spin = 0;
    |    while (i < 200) { spin += i; i += 1; }
    |    balance = before + 1 + (spin - spin);
    |  }
    |  public withdraw(n: Int): Int throws Bubble {
    |    if (n > balance) { bubble() }
    |    balance -= n;
    |    balance
    |  }
    |  public twice(n: Int): Int { deposit(n); deposit(n) }
    |  public ping(other: Account): Int { other.pong(this) }
    |  public pong(back: Account): Int { back.deposit(1) }
    |  private apply(f: fn (Int): Int, x: Int): Int { f(x) }
    |  public viaClosure(n: Int): Int { apply(fn (x: Int): Int { deposit(x) }, n) }
    |  public transfer(to: Account, n: Int, gate: Gate): Int throws Bubble {
    |    withdraw(n);
    |    gate.pass();
    |    to.deposit(n)
    |  }
    |  public burst(k: Int): Void {
    |    async { (): GeneratorResult<Empty> extends GeneratorFn =>
    |      var j = 0;
    |      while (j < k) {
    |        let before = balance;
    |        balance = before + 1;
    |        j += 1;
    |      }
    |    }
    |  }
    |}
""".trimMargin()

private val HOST_SOURCE = """
    |package host;
    |
    |import java.util.ArrayList;
    |import java.util.Arrays;
    |import java.util.List;
    |import java.util.concurrent.CyclicBarrier;
    |import java.util.concurrent.TimeUnit;
    |import java.util.concurrent.atomic.AtomicReference;
    |import my_test_library.test.Account;
    |import my_test_library.test.Gate;
    |import temper.core.Core;
    |
    |public final class Host {
    |    static final int THREADS = 8;
    |    static final int CALLS = 2000;
    |    static final int BURST = 1000000;
    |
    |    static Thread start(Runnable body) {
    |        Thread thread = new Thread(body);
    |        thread.setDaemon(true);
    |        thread.start();
    |        return thread;
    |    }
    |
    |    static void joinAll(List<Thread> threads) throws InterruptedException {
    |        for (Thread thread : threads) {
    |            thread.join(TimeUnit.SECONDS.toMillis(20));
    |        }
    |    }
    |
    |    public static String run() throws Exception {
    |        List<String> out = new ArrayList<>();
    |
    |        // N threads x M calls, while an async block started in a turn
    |        // makes BURST read-modify-writes in one step on temper-async.
    |        Account a = new Account();
    |        List<Thread> threads = new ArrayList<>();
    |        for (int i = 0; i < THREADS; ++i) {
    |            threads.add(start(() -> { for (int j = 0; j < CALLS; ++j) { a.deposit(1); } }));
    |        }
    |        a.burst(BURST);
    |        joinAll(threads);
    |        Core.waitUntilTasksComplete();
    |        out.add("deposits: total=" + a.getTotal() + " expected " + (THREADS * CALLS + BURST));
    |
    |        Account b = new Account();
    |        out.add("self call: twice(5) -> " + b.twice(5));
    |        Account c = new Account();
    |        out.add("callback: b.ping(c) -> " + b.ping(c));
    |        out.add("closure: viaClosure(3) -> " + b.viaClosure(3));
    |
    |        // x -> y and y -> x at once, each holding its own actor first.
    |        Account x = new Account();
    |        Account y = new Account();
    |        x.deposit(10);
    |        y.deposit(10);
    |        CyclicBarrier bothHolding = new CyclicBarrier(2);
    |        Gate gate = () -> {
    |            try {
    |                bothHolding.await(10, TimeUnit.SECONDS);
    |            } catch (Exception e) {
    |                throw new RuntimeException(e);
    |            }
    |        };
    |        AtomicReference<String> r1 = new AtomicReference<>("hung");
    |        AtomicReference<String> r2 = new AtomicReference<>("hung");
    |        Thread t1 = start(() -> {
    |            try { r1.set("ok " + x.transfer(y, 1, gate)); } catch (RuntimeException e) { r1.set(e.getMessage()); }
    |        });
    |        Thread t2 = start(() -> {
    |            try { r2.set("ok " + y.transfer(x, 1, gate)); } catch (RuntimeException e) { r2.set(e.getMessage()); }
    |        });
    |        joinAll(Arrays.asList(t1, t2));
    |        String[] results = { r1.get(), r2.get() };
    |        Arrays.sort(results);
    |        out.add("cycle: " + results[0] + " | " + results[1]);
    |
    |        // A bubble leaves the turn; another thread gets in afterwards.
    |        Account z = new Account();
    |        String bubbled;
    |        try {
    |            z.withdraw(1);
    |            bubbled = "no bubble";
    |        } catch (RuntimeException e) {
    |            bubbled = "bubbled";
    |        }
    |        AtomicReference<String> after = new AtomicReference<>("hung");
    |        joinAll(Arrays.asList(start(() -> after.set("deposit -> " + z.deposit(7)))));
    |        out.add("bubble: " + bubbled + ", then " + after.get());
    |
    |        // An inherited interface default method is one turn too, so no
    |        // reader sees the total between its two bumps.
    |        Account w = new Account();
    |        threads.clear();
    |        for (int i = 0; i < THREADS; ++i) {
    |            threads.add(start(() -> { for (int j = 0; j < 1000; ++j) { w.bumpTwice(); } }));
    |        }
    |        int odd = 0;
    |        while (threads.get(THREADS - 1).isAlive()) {
    |            odd += w.getTotal() % 2;
    |        }
    |        joinAll(threads);
    |        out.add("inherited: bumpTwice x " + (THREADS * 1000) + " -> " + w.getTotal() + ", odd totals seen " + odd);
    |
    |        return String.join("\n", out);
    |    }
    |}
""".trimMargin()
