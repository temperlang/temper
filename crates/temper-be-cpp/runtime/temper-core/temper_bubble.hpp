#pragma once
#include <exception>
#include <string>

namespace temper {
    namespace core {

        struct TemperBubble : std::exception {
            std::string message;

            TemperBubble()
                : message("bubble") {}

            explicit TemperBubble(std::string msg)
                : message(std::move(msg)) {}

            const char* what() const noexcept override {
                return message.c_str();
            }
        };

        // A panic, where a test build's runner (`run_tests`) has it throw
        // rather than end the process (`panic_hook`), so a test that panics
        // fails alone. It's no `TemperBubble`, so no `orelse` catches it.
        struct TemperPanic : std::exception {
            std::string message;

            explicit TemperPanic(std::string msg)
                : message(std::move(msg)) {}

            const char* what() const noexcept override {
                return message.c_str();
            }
        };

        // A test's hard failure, from std's `Test.bail()`, with the test's
        // messages: no `TemperBubble`, so no `orelse` catches it, and the
        // test runner (`run_tests`) reports the messages as they are.
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
