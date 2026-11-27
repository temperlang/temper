// Host code using the generated `async-turns` library the standard way,
// for `functional.rs`'s `co_await_interop`: it prints a line per check,
// which the test compares with what it expects. A C++20 coroutine
// `co_await`s a generated promise, behind the runtime's guard, and a
// range-for steps a generated generator (C++14). C++'s runtime is one
// thread with nothing that blocks, so there's no blocking check.
#include "async-turns/init.hpp"
#include <cstdio>
#include <exception>
#include <memory>
#include <stdexcept>
#include <string>
#include <vector>

#if !defined(__cpp_impl_coroutine)
#error "co_await of a Temper promise needs C++20 coroutines"
#endif

namespace core = temper::core;

namespace {

// A fire-and-forget coroutine, as a C++ user might write.
struct Task {
    struct promise_type {
        Task get_return_object() { return {}; }
        std::suspend_never initial_suspend() noexcept { return {}; }
        std::suspend_never final_suspend() noexcept { return {}; }
        void return_void() {}
        void unhandled_exception() { std::terminate(); }
    };
};

Task awaited() {
    int32_t value = co_await async_turns::later(1);
    std::printf("co_await %d\n", value);
}

}  // namespace

int main() {
    // Its top-level code logs, and runs its async blocks.
    async_turns::global_init_init();

    awaited();

    // A range-for steps a generator.
    std::vector<int32_t> seen;
    for (auto const& step : async_turns::ticks(3, [&seen](int32_t i) { seen.push_back(i); })) {
        static_cast<void>(step);
    }
    std::string list;
    for (int32_t i : seen) {
        list += (list.empty() ? "" : ",") + std::to_string(i);
    }
    std::printf("range-for %s\n", list.c_str());

    // A generator stepped again after a failed step is done.
    auto g = async_turns::ticks(3, [](int32_t i) {
        if (i == 1) {
            throw std::runtime_error("boom");
        }
    });
    core::next(g);
    try {
        core::next(g);
    } catch (const std::runtime_error&) {
        std::printf("failed step\n");
    }
    std::printf("then done %d\n", core::next(g)->is_done() ? 1 : 0);

    // A range-for takes a lazy generator's values, and stops early.
    std::string fib;
    int taken = 0;
    for (int32_t n : async_turns::fibonacci()) {
        if (taken == 8) {
            break;
        }
        fib += (taken++ == 0 ? "" : ",") + std::to_string(n);
    }
    std::printf("fibonacci %s\n", fib.c_str());
    std::string steps;
    for (int32_t n : async_turns::collatz(7)) {
        steps += (steps.empty() ? "" : ",") + std::to_string(n);
    }
    std::printf("collatz %s\n", steps.c_str());

    // A declared failure is an `Expected`'s error, of its own type.
    auto odd = async_turns::halve(3);
    if (odd.has_value()) {
        std::printf("halve returned %d\n", *odd);
    } else if (std::shared_ptr<async_turns::Odd> failure = odd.error()) {
        std::printf("halve %d caught\n", *async_turns::halve(8));
    }
}
