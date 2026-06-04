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

            inline void log(std::shared_ptr<Type> console, std::string msg) {
                msg.append("\n");
                console->logger(std::move(msg));
            }

            inline std::shared_ptr<Type> get_console() {
                return object<Type>(std::function<void(std::string)>([](std::string s) {
                    std::cout << s;
                }));
            }

        }

    }
}
