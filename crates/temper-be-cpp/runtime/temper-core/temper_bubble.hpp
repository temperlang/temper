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

    }
}
