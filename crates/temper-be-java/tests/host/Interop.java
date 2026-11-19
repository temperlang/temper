package interop;

import async_turns.AsyncTurnsGlobal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import temper.core.Core;
import temper.core.Generator;

/**
 * Host code using the generated {@code async_turns} package the standard
 * way, for {@code functional.rs}'s {@code runs}: it prints a line per
 * check, which the test compares with what it expects. javac compiles it
 * with generated code's lints.
 */
public final class Interop {
    private Interop() {}

    /**
     * Runs the checks.
     *
     * @param args unused
     * @throws ClassNotFoundException never, as the class is generated
     */
    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Core.beginTopLevel();
        try {
            // Its top-level code logs, and starts its async blocks.
            Class.forName("async_turns.AsyncTurnsGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.waitUntilTasksComplete();

        // A generated promise is a CompletableFuture.
        System.out.println("join " + AsyncTurnsGlobal.later(1).join());
        System.out.println("thenApply " + AsyncTurnsGlobal.later(2).thenApply(x -> x + 1).join());

        // Blocking inside a turn, before the promise settles, throws.
        Core.beginTopLevel();
        try {
            CompletableFuture<Integer> unsettled = AsyncTurnsGlobal.later(4);
            Core.getPromiseResult(unsettled);
            System.out.println("in a turn: returned");
        } catch (IllegalStateException e) {
            System.out.println("in a turn: " + e.getMessage().contains("would deadlock"));
        } finally {
            Core.endTopLevel();
        }

        // A for-each loop steps a generator.
        List<Integer> seen = new ArrayList<>();
        for (Optional<? super Object> ignored : AsyncTurnsGlobal.ticks(3, seen::add)) {
            // Each step reports through the callback.
        }
        System.out.println("for-each " + seen);

        // A generator stepped again after a failed step is done.
        Generator<Optional<? super Object>> g = AsyncTurnsGlobal.ticks(3, i -> {
            if (i == 1) {
                throw new IllegalArgumentException("boom");
            }
        });
        g.get();
        try {
            g.get();
        } catch (IllegalArgumentException e) {
            System.out.println("failed step");
        }
        g.get();
        System.out.println("then done " + g.getDone());

        // A for-each loop takes a lazy generator's values, and stops early.
        List<String> fib = new ArrayList<>();
        for (int n : AsyncTurnsGlobal.fibonacci()) {
            if (fib.size() == 8) {
                break;
            }
            fib.add(Integer.toString(n));
        }
        System.out.println("fibonacci " + String.join(",", fib));
        List<String> steps = new ArrayList<>();
        for (int n : AsyncTurnsGlobal.collatz(7)) {
            steps.add(Integer.toString(n));
        }
        System.out.println("collatz " + String.join(",", steps));

        // A Temper failure is a RuntimeException, as notes/api-compat.md
        // promises.
        try {
            System.out.println("halve returned " + AsyncTurnsGlobal.halve(3));
        } catch (RuntimeException e) {
            System.out.println("halve " + AsyncTurnsGlobal.halve(8) + " caught");
        }
        Core.waitUntilTasksComplete();
    }
}
