#pragma once
// Declared failures' results (RFC 164): a function that declares a failure
// type returns `Expected<T, E>`, its value or its failure, never throwing
// it. That's C++23's `std::expected` where the standard library has it
// (`__cpp_lib_expected`), and else this file's shim, in C++14, with the
// same names and behavior for what it has:
//
// - `Expected<T, E>` (and `Expected<void, E>`), made from a value that
//   converts to `T`, or from an `Unexpected<G>` whose error converts to
//   `E`; copied, moved, and assigned; `has_value()` and `explicit
//   operator bool`; `value()`, which throws `BadExpectedAccess<E>` (C++23's
//   `std::bad_expected_access<E>`) where there's none; `*` and `->`, which
//   don't check, as std's don't; `error()`, only where there's no value;
//   and `value_or(default)`.
// - `Unexpected<E>`, a failure on its way into an `Expected`, and
//   `unexpected(e)`, which makes one, as C++17's deduction makes
//   `std::unexpected(e)`.
//
// A value is held in place, so the success path allocates nothing. Define
// `TEMPER_EXPECTED_SHIM` to use the shim even where `std::expected` is
// there (its tests do, to compare the two).

#include <exception>
#include <new>
#include <type_traits>
#include <utility>

#if defined(__has_include)
#if __has_include(<version>)
#include <version>
#endif
#endif

#if defined(__cpp_lib_expected) && !defined(TEMPER_EXPECTED_SHIM)
#if __cpp_lib_expected >= 202202L
#define TEMPER_STD_EXPECTED 1
#endif
#endif

#if defined(TEMPER_STD_EXPECTED)
#include <expected>
#else
#include <cstdlib>
#endif

namespace temper {
    namespace core {

#if defined(TEMPER_STD_EXPECTED)

        template<class T, class E>
        using Expected = std::expected<T, E>;

        template<class E>
        using Unexpected = std::unexpected<E>;

        template<class E>
        using BadExpectedAccess = std::bad_expected_access<E>;

#else

        // A failure on its way into an `Expected`.
        template<class E>
        class Unexpected {
        public:
            explicit Unexpected(const E& error) : error_(error) {}

            explicit Unexpected(E&& error) : error_(std::move(error)) {}

            const E& error() const & noexcept {
                return error_;
            }

            E& error() & noexcept {
                return error_;
            }

            E&& error() && noexcept {
                return std::move(error_);
            }

        private:
            E error_;
        };

        // What `value()` throws where there's no value, with the failure.
        template<class E>
        class BadExpectedAccess : public std::exception {
        public:
            explicit BadExpectedAccess(E error) : error_(std::move(error)) {}

            const char* what() const noexcept override {
                return "bad access to Expected without expected value";
            }

            const E& error() const & noexcept {
                return error_;
            }

        private:
            E error_;
        };

        namespace expected_detail {

            template<class X>
            struct is_unexpected : std::false_type {};

            template<class E>
            struct is_unexpected<Unexpected<E>> : std::true_type {};

            template<class E>
            [[noreturn]] void bad_access(const E& error) {
#if defined(__cpp_exceptions) || defined(__EXCEPTIONS) || defined(_CPPUNWIND)
                throw BadExpectedAccess<E>(error);
#else
                static_cast<void>(error);
                std::abort();
#endif
            }

        }

        template<class T, class E>
        class Expected {
            template<class U>
            using from_value = typename std::enable_if<
                std::is_convertible<U&&, T>::value
                    && !std::is_same<typename std::decay<U>::type, Expected>::value
                    && !expected_detail::is_unexpected<typename std::decay<U>::type>::value,
                int>::type;

        public:
            using value_type = T;
            using error_type = E;
            using unexpected_type = Unexpected<E>;

            Expected() : has_(true) {
                new (&value_) T();
            }

            template<class U = T, from_value<U> = 0>
            Expected(U&& value) : has_(true) {
                new (&value_) T(std::forward<U>(value));
            }

            template<
                class G,
                typename std::enable_if<std::is_convertible<const G&, E>::value, int>::type = 0>
            Expected(const Unexpected<G>& failure) : has_(false) {
                new (&error_) E(failure.error());
            }

            template<
                class G,
                typename std::enable_if<std::is_convertible<G&&, E>::value, int>::type = 0>
            Expected(Unexpected<G>&& failure) : has_(false) {
                new (&error_) E(std::move(failure).error());
            }

            Expected(const Expected& other) : has_(other.has_) {
                if (has_) {
                    new (&value_) T(other.value_);
                } else {
                    new (&error_) E(other.error_);
                }
            }

            Expected(Expected&& other) noexcept(
                std::is_nothrow_move_constructible<T>::value
                && std::is_nothrow_move_constructible<E>::value)
                : has_(other.has_) {
                if (has_) {
                    new (&value_) T(std::move(other.value_));
                } else {
                    new (&error_) E(std::move(other.error_));
                }
            }

            ~Expected() {
                destroy();
            }

            // Copies first, so a copy that throws leaves this as it was.
            Expected& operator=(const Expected& other) {
                if (this != &other) {
                    Expected copy(other);
                    *this = std::move(copy);
                }
                return *this;
            }

            Expected& operator=(Expected&& other) noexcept(
                std::is_nothrow_move_constructible<T>::value
                && std::is_nothrow_move_constructible<E>::value) {
                if (this != &other) {
                    destroy();
                    has_ = other.has_;
                    if (has_) {
                        new (&value_) T(std::move(other.value_));
                    } else {
                        new (&error_) E(std::move(other.error_));
                    }
                }
                return *this;
            }

            bool has_value() const noexcept {
                return has_;
            }

            explicit operator bool() const noexcept {
                return has_;
            }

            T& value() & {
                if (!has_) {
                    expected_detail::bad_access(error_);
                }
                return value_;
            }

            const T& value() const & {
                if (!has_) {
                    expected_detail::bad_access(error_);
                }
                return value_;
            }

            T&& value() && {
                if (!has_) {
                    expected_detail::bad_access(error_);
                }
                return std::move(value_);
            }

            T& operator*() & noexcept {
                return value_;
            }

            const T& operator*() const & noexcept {
                return value_;
            }

            T&& operator*() && noexcept {
                return std::move(value_);
            }

            T* operator->() noexcept {
                return &value_;
            }

            const T* operator->() const noexcept {
                return &value_;
            }

            E& error() & noexcept {
                return error_;
            }

            const E& error() const & noexcept {
                return error_;
            }

            E&& error() && noexcept {
                return std::move(error_);
            }

            template<class U>
            T value_or(U&& fallback) const & {
                return has_ ? value_ : static_cast<T>(std::forward<U>(fallback));
            }

            template<class U>
            T value_or(U&& fallback) && {
                return has_ ? std::move(value_) : static_cast<T>(std::forward<U>(fallback));
            }

        private:
            void destroy() noexcept {
                if (has_) {
                    value_.~T();
                } else {
                    error_.~E();
                }
            }

            union {
                T value_;
                E error_;
            };
            bool has_;
        };

        // A function's result with no value: done, or its failure.
        template<class E>
        class Expected<void, E> {
        public:
            using value_type = void;
            using error_type = E;
            using unexpected_type = Unexpected<E>;

            Expected() noexcept : has_(true) {}

            template<
                class G,
                typename std::enable_if<std::is_convertible<const G&, E>::value, int>::type = 0>
            Expected(const Unexpected<G>& failure) : has_(false) {
                new (&error_) E(failure.error());
            }

            template<
                class G,
                typename std::enable_if<std::is_convertible<G&&, E>::value, int>::type = 0>
            Expected(Unexpected<G>&& failure) : has_(false) {
                new (&error_) E(std::move(failure).error());
            }

            Expected(const Expected& other) : has_(other.has_) {
                if (!has_) {
                    new (&error_) E(other.error_);
                }
            }

            Expected(Expected&& other) noexcept(std::is_nothrow_move_constructible<E>::value)
                : has_(other.has_) {
                if (!has_) {
                    new (&error_) E(std::move(other.error_));
                }
            }

            ~Expected() {
                destroy();
            }

            Expected& operator=(const Expected& other) {
                if (this != &other) {
                    Expected copy(other);
                    *this = std::move(copy);
                }
                return *this;
            }

            Expected& operator=(Expected&& other) noexcept(
                std::is_nothrow_move_constructible<E>::value) {
                if (this != &other) {
                    destroy();
                    has_ = other.has_;
                    if (!has_) {
                        new (&error_) E(std::move(other.error_));
                    }
                }
                return *this;
            }

            bool has_value() const noexcept {
                return has_;
            }

            explicit operator bool() const noexcept {
                return has_;
            }

            void value() const & {
                if (!has_) {
                    expected_detail::bad_access(error_);
                }
            }

            void value() && {
                if (!has_) {
                    expected_detail::bad_access(error_);
                }
            }

            void operator*() const noexcept {}

            E& error() & noexcept {
                return error_;
            }

            const E& error() const & noexcept {
                return error_;
            }

            E&& error() && noexcept {
                return std::move(error_);
            }

        private:
            void destroy() noexcept {
                if (!has_) {
                    error_.~E();
                }
            }

            union {
                E error_;
            };
            bool has_;
        };

#endif

        // `Unexpected<E>` of a failure, its type deduced, as C++17 deduces
        // `std::unexpected(e)`'s: `return core::unexpected(NotFound::make(key));`.
        template<class E>
        Unexpected<typename std::decay<E>::type> unexpected(E&& error) {
            return Unexpected<typename std::decay<E>::type>(std::forward<E>(error));
        }

    }
}
