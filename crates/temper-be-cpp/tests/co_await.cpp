// C++20 interop: a C++ coroutine `co_await`s Temper promises, as the
// runtime allows behind `__cpp_impl_coroutine`, and a range-for steps a
// Temper generator, which a failed step leaves done; `tests/functional.rs`
// builds it with `-std=c++20`.
#include <temper-core/core.hpp>
#include <coroutine>
#include <cstdio>
#include <exception>
#include <stdexcept>
#include <string>

namespace core = temper::core;

// A fire-and-forget coroutine, as a C++ user might write.
namespace {

struct Task {
    struct promise_type {
        Task get_return_object() { return {}; }
        std::suspend_never initial_suspend() noexcept { return {}; }
        std::suspend_never final_suspend() noexcept { return {}; }
        void return_void() {}
        void unhandled_exception() { std::terminate(); }
    };
};

Task use(std::shared_ptr<core::PromiseBuilder<std::string>> a,
         std::shared_ptr<core::PromiseBuilder<int32_t>> b) {
    std::string s = co_await core::getpromise(a);
    std::printf("got %s\n", s.c_str());
    try {
        int32_t n = co_await core::getpromise(b);
        std::printf("got %d\n", n);
    } catch (const core::UncaughtFailure& e) {
        std::printf("%s\n", e.what());
    }
}

}  // namespace

int main() {
    auto a = core::PromiseBuilderNs::make<std::string>();
    auto b = core::PromiseBuilderNs::make<int32_t>();
    core::complete(a, "ready");  // settled before the await
    use(a, b);
    std::printf("waiting\n");
    core::breakpromise(b);       // resumes it
    std::printf("done\n");

    int count = 0;
    auto counting = core::safe_adapt_generator_fn<int32_t>(
        [count](std::shared_ptr<core::SafeGenerator<int32_t>>) mutable
            -> std::shared_ptr<core::GeneratorResult<int32_t>> {
            if (count >= 3) {
                return core::doneResult();
            }
            return core::ValueResult<int32_t>::make(++count);
        });
    for (int32_t n : counting) {
        std::printf("for %d\n", n);
    }
    auto failing = core::safe_adapt_generator_fn<int32_t>(
        [](std::shared_ptr<core::SafeGenerator<int32_t>>)
            -> std::shared_ptr<core::GeneratorResult<int32_t>> {
            throw std::runtime_error("boom");
        });
    try {
        core::next(failing);
    } catch (const std::runtime_error&) {
        std::printf("failed step\n");
    }
    std::printf("then done %d\n", core::next(failing)->is_done() ? 1 : 0);
}
