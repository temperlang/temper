#pragma once
#include <functional>
#include <iostream>
#include <memory>
#include <sstream>
#include <string>
#include "temper_bubble.hpp"
#include "any_value.hpp"
#include "nullable_param.hpp"

namespace temper {
    namespace core {

        template<class T>
        struct NonDeduced {
            typedef T type;
        };

        struct Never {
            template<class T>
            operator T() const {
                std::terminate();
            }
        };

        struct Invalid {};

        struct Empty {};

        inline std::shared_ptr<Empty> empty() {
            return std::make_shared<Empty>();
        }

        template<class Base, class... Fields>
        std::shared_ptr<Base> object(Fields... fields) {
            return std::make_shared<Base>(fields...);
        }

        template<class T>
        std::shared_ptr<T> borrow_this(T* ptr) {
            return std::shared_ptr<T>(std::shared_ptr<T>{}, ptr);
        }

        inline void cat_impl(std::stringstream& dst) {
            (void) dst;
        }

        template<class First, class... Rest>
        void cat_impl(std::stringstream& dst, First fst, Rest... rest) {
            dst << fst;
            cat_impl(dst, rest...);
        }

        template<class... Args>
        std::string cat(Args... args) {
            std::stringstream ss;
            cat_impl(ss, args...);
            return ss.str();
        }

        template<class T = void>
        [[noreturn]] T bubble(std::string message) {
            throw TemperBubble(std::move(message));
        }

        template<class T = void>
        [[noreturn]] T bubble() {
            throw TemperBubble();
        }

        template<class T = void>
        [[noreturn]] T panic(std::string message) {
            std::terminate();
        }

        template<class T = void>
        [[noreturn]] T panic() {
            std::terminate();
        }

        template<class T>
        bool is_null(T) {
            return false;
        }

        template<class T>
        bool is_null(std::shared_ptr<T> value) {
            return value == nullptr;
        }

        template<class T>
        bool is_null(std::function<T> value) {
            return !static_cast<bool>(value);
        }

        template<class T>
        bool is_null(const NullableParam<T>& value) {
            return !value.has_value;
        }

        template<class T>
        T not_null(T value) {
            return value;
        }

        template<class T>
        T not_null(const NullableParam<T>& value) {
            if (!value.has_value) {
                bubble<T>("not_null on null value");
            }
            return value.value;
        }

        inline void print(std::string s) {
            std::cout << s;
        }

        [[noreturn]] inline void pure_virtual() {
            throw TemperBubble("pure virtual call");
        }

        template<class T = void>
        void testBail(T = T()) {
            throw TemperBubble("test bail");
        }

    }
}
