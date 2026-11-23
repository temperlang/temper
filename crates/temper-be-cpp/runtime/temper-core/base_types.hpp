#pragma once
#include <functional>
#include <iostream>
#include <memory>
#include <sstream>
#include <string>
#include <type_traits>
#include "temper_bubble.hpp"
#include "str.hpp"
#include "any_value.hpp"
#include "nullable_param.hpp"

namespace temper {
    namespace core {

        template<class T>
        struct NonDeduced {
            typedef T type;
        };

        // Whether a type is passed by `const &` (shared_ptr, std::function, and Str), matching
        // the translator's choice for concrete types.
        template<class T>
        struct IsIndirect : std::false_type {};

        template<class T>
        struct IsIndirect<std::shared_ptr<T>> : std::true_type {};

        template<class T>
        struct IsIndirect<std::function<T>> : std::true_type {};

        template<>
        struct IsIndirect<Str> : std::true_type {};

        // How a virtual member takes a type parameter's value, so a template's method
        // and its overrides for concrete types have the same signature.
        template<class T>
        using Param = typename std::conditional<IsIndirect<T>::value, T const &, T>::type;

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
        void cat_impl(std::stringstream& dst, const First& fst, const Rest&... rest) {
            dst << fst;
            cat_impl(dst, rest...);
        }

        template<class... Args>
        Str cat(const Args&... args) {
            std::stringstream ss;
            cat_impl(ss, args...);
            return Str::written_to(ss.rdbuf());
        }

        template<class T = void>
        [[noreturn]] T bubble(std::string message) {
            throw TemperBubble(std::move(message));
        }

        template<class T = void>
        [[noreturn]] T bubble() {
            throw TemperBubble();
        }

        // What a panic does before the process ends, if anything: none
        // but in a test build, whose runner (`run_tests`) sets one that
        // throws a `TemperPanic`, which it catches test by test. One for
        // the whole program, however many libraries it links.
        using PanicHook = void (*)(const std::string& message);

        inline PanicHook& panic_hook() {
            static PanicHook hook = nullptr;
            return hook;
        }

        template<class T = void>
        [[noreturn]] T panic(std::string message) {
            // Keep what was logged before, and surface the panic message,
            // with what it replaces, before aborting; otherwise all are
            // lost.
            std::cout.flush();
            std::string cause = panic_cause();
            if (!cause.empty()) {
                message += "\n" + cause;
            }
            if (PanicHook hook = panic_hook()) {
                hook(message);
            }
            std::cerr << message << std::endl;
            panicking() = true;
            std::terminate();
        }

        template<class T = void>
        [[noreturn]] T panic() {
            panic<T>("panic");
        }

        // A panic for an index out of a list's bounds, as core's docs say.
        template<class T = void>
        [[noreturn]] T panic_out_of_bounds(int64_t index, int64_t length) {
            panic<T>("index " + std::to_string(index) + " out of bounds for length "
                + std::to_string(length));
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

        // std's `Test.bail()`: ends the test with its messages, by a
        // `TemperTestFailure`, which no `orelse` catches, and `run_tests`
        // reports as the test's failure.
        template<class T>
        [[noreturn]] void testBail(const T& test) {
            auto messages = test->messagesCombined();
            throw TemperTestFailure(is_null(messages) ? std::string() : Str(messages).str());
        }

        // std's `Test.panics(block)`, for `Test.assertPanics`: whether the
        // block threw anything but a bubble or a test's failure, as on
        // every backend: a panic, which in a test build throws a
        // `TemperPanic` (`run_tests` sets the hook), or a host's
        // exception. What isn't passes through.
        template<class T, class F>
        bool testPanics(const T&, const F& block) {
            try {
                block();
            } catch (const TemperBubble&) {
                throw;
            } catch (const TemperTestFailure&) {
                throw;
            } catch (...) {
                return true;
            }
            return false;
        }

    }
}
