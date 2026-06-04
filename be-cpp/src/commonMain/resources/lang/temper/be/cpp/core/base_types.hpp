#pragma once
#include <functional>
#include <iostream>
#include <memory>
#include <sstream>
#include <string>
#include <type_traits>
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

        // Recover an owning shared_ptr for `this` so it can be passed where an `Object<T>` is
        // expected. Every Temper object is created via make_shared, so the control block is
        // recoverable and the result keeps the object alive even if the callee stores it.
        //
        // Two ownership-bearing shapes exist (see the struct emitter): inheritance/interface
        // types share a single virtual `AnyValueBase` root (which derives enable_shared_from_this
        // <AnyValueBase>), and plain rootless structs carry their own CRTP enable_shared_from_this
        // <T>. They are dispatched separately because the former needs a down-cast from the shared
        // root while the latter (non-polymorphic, so dynamic_cast is unavailable) does not.

        // Inheritance/interface types: shared_from_this() yields shared_ptr<AnyValueBase>; the
        // object's most-derived type is T, so down-cast (the type is polymorphic via AnyValueBase).
        template<class T>
        typename std::enable_if<std::is_base_of<AnyValueBase, T>::value, std::shared_ptr<T>>::type
        borrow_this(T* ptr) {
            return std::dynamic_pointer_cast<T>(ptr->shared_from_this());
        }

        // Plain rootless structs: shared_from_this() already yields shared_ptr<T> directly.
        template<class T>
        typename std::enable_if<
            !std::is_base_of<AnyValueBase, T>::value
            && std::is_base_of<std::enable_shared_from_this<T>, T>::value,
            std::shared_ptr<T>>::type
        borrow_this(T* ptr) {
            return ptr->shared_from_this();
        }

        // Anything else (e.g. core helper types not rooted at either base): non-owning alias.
        // Safe only as a transient borrow that does not outlive the object.
        template<class T>
        typename std::enable_if<
            !std::is_base_of<AnyValueBase, T>::value
            && !std::is_base_of<std::enable_shared_from_this<T>, T>::value,
            std::shared_ptr<T>>::type
        borrow_this(T* ptr) {
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
            // Surface the panic message before aborting; otherwise the reason for the
            // crash is lost.
            std::cerr << message << std::endl;
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
