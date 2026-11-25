// Host code using the generated `failures-interop` library's declared
// failures the standard way, for `functional.rs`'s `failures_interop`: it
// branches on `has_value()`, reads `error()` by its own type, a sealed
// interface's, and the library's root, its fields, `what()` (as any
// `std::exception`'s), `get_message()`, and its causes (`get_cause()`),
// passes a failure on into a result of its own, and sees a panic stay a
// panic, never a failure. It prints a line per check, which the test
// compares with what it expects. Built as C++14, where `Expected` is the
// runtime's shim, and as C++23, where it's `std::expected`.

#include <failures-interop/init.hpp>

#include <iostream>
#include <memory>
#include <stdexcept>
#include <string>

namespace core = temper::core;
namespace lib = failures_interop;

namespace {

    // A type of host code's own under a sealed one, which Temper's
    // exhaustive `when` over it doesn't know.
    struct Rogue : public virtual lib::SignError {
        core::Str get_message() const override { return "rogue"; }
    };

    // A failure's message and its causes', as `get_cause()` gives them.
    std::string chain(const std::shared_ptr<core::Failure>& failure) {
        std::string out = failure->what();
        for (std::shared_ptr<core::Failure> cause = failure->get_cause(); cause;
             cause = cause->get_cause()) {
            out += ", caused by ";
            out += cause->what();
        }
        return out;
    }

    // Any of the library's failures, passed on as its root, as host code
    // that propagates them does.
    core::Expected<int32_t, std::shared_ptr<lib::FailuresInteropError>> both(
        const std::string& key, const std::string& text) {
        auto found = lib::lookup(key);
        if (!found.has_value()) {
            return core::unexpected(found.error());
        }
        auto sign = lib::sign(text);
        if (!sign) {
            return core::unexpected(sign.error());
        }
        return *found + *sign;
    }

    // What a panic does in this program: throws, so it's seen here.
    struct Panicked : std::runtime_error {
        using std::runtime_error::runtime_error;
    };

    [[noreturn]] void throw_panic(const std::string& message) {
        throw Panicked(message);
    }

}

int main() {
    // Its top-level code logs.
    lib::global_init_init();

    auto missing = lib::lookup("z");
    if (!missing.has_value()) {
        std::shared_ptr<lib::NotFound> e = missing.error();
        std::cout << "NotFound " << e->get_key().str() << ": " << e->what() << "\n";
    }
    std::cout << "found " << *lib::lookup("a") << " " << lib::lookup("q").value_or(-1) << "\n";

    // Each failure type is its base, a sealed interface's below it.
    for (auto pair : {std::make_pair("y", "+"), std::make_pair("a", "?")}) {
        auto result = both(pair.first, pair.second);
        if (result.has_value()) {
            std::cout << "both " << *result << "\n";
        } else if (auto e = std::dynamic_pointer_cast<lib::NotFound>(result.error())) {
            std::cout << "base NotFound " << e->get_key().str() << "\n";
        } else if (auto e2 = std::dynamic_pointer_cast<lib::BadSign>(result.error())) {
            std::cout << "base BadSign " << e2->get_text().str() << "\n";
        }
    }
    std::cout << "both " << *both("a", "-") << "\n";

    for (const char* text : {"x", ""}) {
        auto result = lib::sign(text);
        std::shared_ptr<lib::SignError> e = result.error();
        if (auto bad = std::dynamic_pointer_cast<lib::BadSign>(e)) {
            std::cout << "sign BadSign " << bad->get_text().str() << ": " << e->what() << "\n";
        } else if (std::dynamic_pointer_cast<lib::EmptyText>(e)) {
            std::cout << "sign EmptyText: " << e->get_message().str() << "\n";
        }
    }

    // As a `std::exception`, and as core's `Error`, with causes.
    std::shared_ptr<lib::LookupFailed> failed = lib::setting("port").error();
    const std::exception& as_std = *failed;
    std::shared_ptr<core::Failure> cause = failed->get_cause();
    std::cout << "LookupFailed " << chain(failed) << ", a NotFound cause "
              << (std::dynamic_pointer_cast<lib::NotFound>(cause) != nullptr) << ", what "
              << as_std.what() << "\n";
    std::cout << "made before " << chain(lib::madeBefore("early").error()) << "\n";
    std::cout << "thrown again " << chain(lib::thrownAgain("first").error()) << "\n";

    // A panic isn't a failure: it stays a panic.
    core::panic_hook() = throw_panic;
    try {
        auto result = lib::risky(2);
        std::cout << "panic gave a result " << result.has_value() << "\n";
    } catch (const Panicked& e) {
        std::cout << "panic passes " << e.what() << "\n";
    }

    // An exhaustive `when` panics on it.
    std::cout << "known subtype " << lib::signName(lib::BadSign::make("?")) << "\n";
    try {
        core::Str name = lib::signName(std::make_shared<Rogue>());
        std::cout << "unknown subtype passes " << name << "\n";
    } catch (const Panicked& e) {
        std::cout << "unknown subtype panics " << e.what() << "\n";
    }
}
