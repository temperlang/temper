#pragma once
#include <exception>
#include <functional>
#include <string>

namespace temper {
    namespace core {

        // Whether the process is ending by a panic, which has said what
        // it was, so the terminate handler (`stack_trace.hpp`) doesn't
        // report the failure it replaced as uncaught.
        inline bool& panicking() {
            static bool ending = false;
            return ending;
        }

        // What a panic replaces, if it panics while a failure is being
        // handled, as `orelse panic()` does: `caused by: WHAT`, else
        // empty. C++ exceptions have no cause, so the message says it.
        inline std::string panic_cause() {
            std::exception_ptr handling = std::current_exception();
            if (!handling) {
                return "";
            }
            try {
                std::rethrow_exception(handling);
            } catch (const std::exception& e) {
                return std::string("caused by: ") + e.what();
            } catch (...) {
                return "caused by: an exception";
            }
        }

        // A panic, where a test build's runner (`run_tests`) has it throw
        // rather than end the process (`panic_hook`), so a test that panics
        // fails alone. No handler catches it, as failures aren't thrown.
        // `trace` gives where it panicked (`stack_trace.hpp`), for the
        // report, described only then, if it's set.
        struct TemperPanic : std::exception {
            std::string message;
            std::function<std::string()> trace;

            explicit TemperPanic(std::string msg)
                : message(std::move(msg)) {}

            const char* what() const noexcept override {
                return message.c_str();
            }
        };

        // A test's hard failure, from std's `Test.bail()`, with the test's
        // messages: no handler catches it, and the test runner
        // (`run_tests`) reports the messages as they are.
        struct TemperTestFailure : std::exception {
            std::string message;

            explicit TemperTestFailure(std::string msg)
                : message(std::move(msg)) {}

            const char* what() const noexcept override {
                return message.c_str();
            }
        };

    }
}
