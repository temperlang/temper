#pragma once
#include <functional>
#include <iostream>
#include <string>
#include "base_types.hpp"

namespace temper {
    namespace core {

        namespace Console {

            struct Type {
                std::function<void(std::string)> logger;
                Type() = default;
                explicit Type(std::function<void(std::string)> logger) : logger(std::move(logger)) {}
            };

            inline void log(const std::shared_ptr<Type>& console, const Str& msg) {
                std::string line;
                line.reserve(msg.size() + 1);
                line.append(msg.data(), msg.size()).append("\n");
                console->logger(std::move(line));
            }

            inline std::shared_ptr<Type> get_console() {
                return object<Type>(std::function<void(std::string)>([](std::string s) {
                    std::cout << s;
                }));
            }

        }

    }
}
