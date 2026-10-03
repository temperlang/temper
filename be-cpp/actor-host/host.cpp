// Drives the generated bank::Account (an @actor class) from host threads.
//
//   threads    8 threads x 100000 deposits, transfers and property writes
//   async      7 threads deposit while an eighth launches and drains async steps
//   bubble     a bubble leaves a turn and another thread can take the next one
//   crossing   a -> b on one thread while b -> a on another: "actor call cycle"
//   reentry    a -> b -> a on one thread runs inline and returns
#include "bank/init.hpp"
#include <cstdio>
#include <cstring>
#include <thread>
#include <vector>

namespace {
    constexpr int kThreads = 8;
    constexpr int kCalls = 100000;

    template<class F>
    void on_threads(int n, F f) {
        std::vector<std::thread> ts;
        for (int t = 0; t < n; t++) ts.emplace_back([&f, t] { f(t); });
        for (auto& t : ts) t.join();
    }

    int threads() {
        auto acct = bank::Account::make("ann");
        on_threads(kThreads, [&](int) {
            for (int i = 0; i < kCalls; i++) acct->deposit(1);
        });
        std::printf("deposits: balance %d entries %d (expected %d %d)\n",
            acct->get_balance(), acct->get_entries(), kThreads * kCalls, kThreads * kCalls);

        // Half the threads move money a -> b and half c -> b, each transfer a
        // withdraw on one actor and a deposit on another, made inside the first
        // one's turn. All of them call into b at once.
        auto a = bank::Account::make("a");
        auto b = bank::Account::make("b");
        auto c = bank::Account::make("c");
        a->deposit(kThreads / 2 * kCalls);
        c->deposit(kThreads / 2 * kCalls);
        on_threads(kThreads, [&](int t) {
            for (int i = 0; i < kCalls; i++) {
                if (t % 2 == 0) a->transferTo(b, 1); else c->transferTo(b, 1);
            }
        });
        std::printf("transfers: a %d b %d c %d (expected 0 %d 0)\n",
            a->get_balance(), b->get_balance(), c->get_balance(), kThreads * kCalls);

        // drain() reads other.balance and writes other.balance = 0. Each is its
        // own turn on src, through src's getter and setter, so there is no data
        // race. The pair is not one turn on src: a deposit that lands between
        // them is wiped out. That is the rule, not a bug; it is shown here so
        // the count is not mistaken for one.
        auto sink = bank::Account::make("sink");
        auto src = bank::Account::make("src");
        on_threads(kThreads, [&](int t) {
            for (int i = 0; i < kCalls; i++) {
                if (t % 2 == 0) src->deposit(1); else sink->drain(src);
            }
        });
        sink->drain(src);
        std::printf("drain: sink %d src %d of %d deposited (lost between turns: %d)\n",
            sink->get_balance(), src->get_balance(), kThreads / 2 * kCalls,
            kThreads / 2 * kCalls - sink->get_balance() - src->get_balance());
        return 0;
    }

    int async_steps() {
        auto acct = bank::Account::make("ann");
        on_threads(kThreads, [&](int t) {
            for (int i = 0; i < kCalls; i++) {
                if (t == 0) {
                    // The run queue is not thread-safe: only this thread launches
                    // and drains. The steps it runs still take acct's turn.
                    acct->later(1);
                    temper::core::async_drain();
                } else {
                    acct->deposit(1);
                }
            }
        });
        std::printf("async: balance %d (expected %d)\n", acct->get_balance(), kThreads * kCalls);
        return 0;
    }

    int bubble() {
        auto acct = bank::Account::make("ann");
        acct->deposit(10);
        try {
            acct->withdraw(50);
            std::puts("withdraw 50: no bubble");
        } catch (const temper::core::TemperBubble&) {
            std::puts("withdraw 50: bubbled to the caller");
        }
        int after = 0;
        std::thread other([&] { after = acct->deposit(1); });
        other.join();
        std::printf("another thread's deposit after the bubble: %d\n", after);
        return 0;
    }

    // a.transferTo(b) on one thread holds a and waits for b; b.transferTo(a) on
    // another holds b and waits for a. Whichever thread closes the loop panics.
    int crossing() {
        auto a = bank::Account::make("a");
        auto b = bank::Account::make("b");
        a->deposit(kCalls);
        b->deposit(kCalls);
        on_threads(2, [&](int t) {
            for (int i = 0; i < kCalls; i++) {
                if (t == 0) a->transferTo(b, 1); else b->transferTo(a, 1);
            }
        });
        std::fflush(stdout);
        std::printf("crossing: no cycle in %d tries each way: a %d b %d\n",
            kCalls, a->get_balance(), b->get_balance());
        return 0;
    }

    int reentry() {
        auto a = bank::Account::make("a");
        auto b = bank::Account::make("b");
        std::puts("a.relay(b): a calls b.poke(a), which calls a.deposit(1)");
        std::printf("returned %d\n", a->relay(b));
        return 0;
    }
}

int main(int argc, char** argv) {
    bank::global_init_init();
    const char* mode = argc > 1 ? argv[1] : "threads";
    if (std::strcmp(mode, "threads") == 0) return threads();
    if (std::strcmp(mode, "async") == 0) return async_steps();
    if (std::strcmp(mode, "bubble") == 0) return bubble();
    if (std::strcmp(mode, "crossing") == 0) return crossing();
    if (std::strcmp(mode, "reentry") == 0) return reentry();
    std::fprintf(stderr, "unknown mode %s\n", mode);
    return 2;
}
