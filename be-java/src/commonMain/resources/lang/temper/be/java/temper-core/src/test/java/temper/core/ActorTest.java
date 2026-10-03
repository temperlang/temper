package temper.core;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ActorTest {
    /** What generated code does around a body. */
    private static void turn(Actor actor, Runnable body) {
        actor.enter();
        try {
            body.run();
        } finally {
            actor.exit();
        }
    }

    private static Thread start(Runnable body) {
        Thread thread = new Thread(body);
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    /** Joins, failing instead of hanging if the thread does not finish. */
    private static void joinOrFail(Thread thread) throws InterruptedException {
        thread.join(TimeUnit.SECONDS.toMillis(10));
        assertFalse(thread.isAlive(), "thread hung: " + thread.getName());
    }

    @Test
    void turnsDoNotOverlap() throws InterruptedException {
        Actor actor = new Actor();
        int[] counter = {0};
        AtomicInteger inside = new AtomicInteger();
        AtomicInteger overlaps = new AtomicInteger();
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 8; ++i) {
            threads.add(start(() -> {
                for (int j = 0; j < 10000; ++j) {
                    turn(actor, () -> {
                        if (inside.incrementAndGet() != 1) {
                            overlaps.incrementAndGet();
                        }
                        int before = counter[0];
                        Thread.yield();
                        counter[0] = before + 1;
                        inside.decrementAndGet();
                    });
                }
            }));
        }
        for (Thread thread : threads) {
            joinOrFail(thread);
        }
        assertEquals(80000, counter[0]);
        assertEquals(0, overlaps.get());
    }

    @Test
    void sameThreadReentersInline() throws InterruptedException {
        Actor actor = new Actor();
        List<String> log = new ArrayList<>();
        turn(actor, () -> {
            log.add("outer");
            turn(actor, () -> {
                log.add("inner");
                turn(actor, () -> log.add("innermost"));
            });
        });
        assertEquals("[outer, inner, innermost]", log.toString());
        // Released: another thread gets in at once.
        AtomicReference<String> other = new AtomicReference<>("hung");
        Thread thread = start(() -> turn(actor, () -> other.set("in")));
        joinOrFail(thread);
        assertEquals("in", other.get());
    }

    @Test
    void exceptionReleasesTheActor() throws InterruptedException {
        Actor actor = new Actor();
        assertThrows(RuntimeException.class, () -> turn(actor, () -> {
            throw new RuntimeException("bubble");
        }));
        AtomicReference<String> other = new AtomicReference<>("hung");
        Thread thread = start(() -> turn(actor, () -> other.set("in")));
        joinOrFail(thread);
        assertEquals("in", other.get());
    }

    @Test
    void twoActorCycleThrowsInsteadOfHanging() throws InterruptedException {
        Actor a = new Actor();
        Actor b = new Actor();
        CyclicBarrier bothHolding = new CyclicBarrier(2);
        List<String> results = new ArrayList<>();
        Runnable waitForOther = () -> {
            try {
                bothHolding.await(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
        Thread t1 = start(() -> record(results, () -> turn(a, () -> {
            waitForOther.run();
            turn(b, () -> { });
        })));
        Thread t2 = start(() -> record(results, () -> turn(b, () -> {
            waitForOther.run();
            turn(a, () -> { });
        })));
        joinOrFail(t1);
        joinOrFail(t2);
        results.sort(null);
        assertEquals("[actor call cycle, ok]", results.toString());
    }

    @Test
    void threeActorCycleThrowsInsteadOfHanging() throws InterruptedException {
        Actor[] actors = {new Actor(), new Actor(), new Actor()};
        CyclicBarrier allHolding = new CyclicBarrier(3);
        List<String> results = new ArrayList<>();
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 3; ++i) {
            Actor mine = actors[i];
            Actor next = actors[(i + 1) % 3];
            threads.add(start(() -> record(results, () -> turn(mine, () -> {
                try {
                    allHolding.await(10, TimeUnit.SECONDS);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                turn(next, () -> { });
            }))));
        }
        for (Thread thread : threads) {
            joinOrFail(thread);
        }
        results.sort(null);
        assertEquals("[actor call cycle, ok, ok]", results.toString());
    }

    @Test
    void waitingWithoutACycleJustWaits() throws InterruptedException {
        Actor a = new Actor();
        Actor b = new Actor();
        CountDownLatch holdingB = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        List<String> results = new ArrayList<>();
        // t1 holds b for a while; t2 holds a and waits for b.  No cycle.
        Thread t1 = start(() -> record(results, () -> turn(b, () -> {
            holdingB.countDown();
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        })));
        holdingB.await(10, TimeUnit.SECONDS);
        Thread t2 = start(() -> record(results, () -> turn(a, () -> turn(b, () -> { }))));
        Thread.sleep(50);
        release.countDown();
        joinOrFail(t1);
        joinOrFail(t2);
        assertEquals("[ok, ok]", results.toString());
    }

    private static void record(List<String> results, Runnable body) {
        String result;
        try {
            body.run();
            result = "ok";
        } catch (Actor.CycleException e) {
            result = e.getMessage();
        }
        synchronized (results) {
            results.add(result);
        }
    }
}
