#pragma once
// Declared failures (RFC 164): what every library's failure types share.
//
// A library's failure class is a struct, a `shared_ptr` as any class's,
// below one of its library's roots (`struct FailuresError : virtual Failure`),
// and `Failure` is core's `Error`: a `std::exception` whose `what()` is
// its message, with the failure it was thrown while handling, if any, as
// its cause. A failure is never thrown: a function that declares one
// returns it in an `Expected` (`expected.hpp`), and callers branch on
// `has_value()`. Only a failure nothing handles at a module's top level
// is thrown, as an `UncaughtFailure`, as a bubble there would be.

#include <exception>
#include <memory>
#include <mutex>
#include <string>
#include "any_value.hpp"
#include "expected.hpp"
#include "str.hpp"

namespace temper {
    namespace core {

        // Core's `Error`, which every failure type is below.
        struct Failure : public virtual AnyValueBase, public std::exception {
            // Its message: its type's own `message` getter, or the one the
            // compiler gives it, `not found (key: y)`.
            virtual Str get_message() const = 0;

            // The failure that was being handled where it was first thrown
            // in a handler, if it was.
            std::shared_ptr<Failure> get_cause() const {
                std::lock_guard<std::mutex> hold(lock_);
                return cause_;
            }

            // Sets its cause, unless it has one, or `cause` is itself or has
            // it as a cause, so causes never loop: a failure thrown again
            // keeps its first.
            void set_cause(std::shared_ptr<Failure> cause) {
                for (std::shared_ptr<Failure> at = cause; at; at = at->get_cause()) {
                    if (at.get() == this) {
                        return;
                    }
                }
                std::lock_guard<std::mutex> hold(lock_);
                if (!cause_) {
                    cause_ = std::move(cause);
                }
            }

            // Its message, found when first asked, as a `std::exception`'s.
            const char* what() const noexcept override {
                std::call_once(what_once_, [this]() { what_ = get_message(); });
                return what_.c_str();
            }

        private:
            mutable std::mutex lock_;
            std::shared_ptr<Failure> cause_;
            mutable std::once_flag what_once_;
            mutable Str what_;
        };

        // `failure`, with `cause`, the failure being handled, as its cause
        // unless it has one (`Failure::set_cause`): a `throw` in a handler.
        template<class F, class C>
        F with_cause(F failure, const C& cause) {
            failure->set_cause(cause);
            return failure;
        }

        // A failure's message, then a `caused by: …` line for each cause.
        inline std::string failure_report(const Failure& failure) {
            std::string out = failure.get_message().str();
            for (std::shared_ptr<Failure> at = failure.get_cause(); at; at = at->get_cause()) {
                out += "\ncaused by: " + at->get_message().str();
            }
            return out;
        }

        // A failure nothing handled at a module's top level, thrown out of
        // its init function as a bubble there is: so a program ends saying
        // it (`stack_trace.hpp`), and host code that runs the init can
        // catch it. `what()` is its report (`failure_report`).
        struct UncaughtFailure : std::exception {
            std::shared_ptr<Failure> failure;
            std::string report;

            explicit UncaughtFailure(std::shared_ptr<Failure> f)
                : failure(std::move(f)), report(failure_report(*failure)) {}

            const char* what() const noexcept override {
                return report.c_str();
            }
        };

        // Throws an `UncaughtFailure` of a failure.
        template<class F>
        [[noreturn]] void uncaught_failure(const F& failure) {
            throw UncaughtFailure(failure);
        }

        // Core's own failures' root, `CoreError`, as a library's roots are.
        struct CoreError : public virtual Failure {};

        // What a map's `get` gives for a key it doesn't have, with that key.
        struct MissingKey : public virtual CoreError {
            std::shared_ptr<AnyValueBase> key;
            Str message;

            // For `key`, a map key boxed, as Temper's `new MissingKey(key)`.
            static std::shared_ptr<MissingKey> make(std::shared_ptr<AnyValueBase> key) {
                std::shared_ptr<MissingKey> made = std::make_shared<MissingKey>();
                made->message = Str("missing key (key: " + shown(key) + ")");
                made->key = std::move(key);
                return made;
            }

            // For a map's key, of a map key type: `int32_t` or `Str`.
            template<class Key>
            static std::shared_ptr<MissingKey> of(const Key& key) {
                return make(any_box(key));
            }

            Str get_message() const override {
                return message;
            }

            std::shared_ptr<AnyValueBase> get_key() const {
                return key;
            }

        private:
            // A map key's text, as a default message shows a field.
            static std::string shown(const std::shared_ptr<AnyValueBase>& key) {
                if (auto i = std::dynamic_pointer_cast<AnyValueBox<int32_t>>(key)) {
                    return std::to_string(i->value);
                }
                if (auto s = std::dynamic_pointer_cast<AnyValueBox<Str>>(key)) {
                    return s->value.str();
                }
                return "?";
            }
        };

        // What a conversion gives for a value it can't convert: its input,
        // as text, and why, as `not an Int32`.
        struct ParseError : public virtual CoreError {
            Str input;
            Str reason;

            static std::shared_ptr<ParseError> make(Str input, Str reason) {
                std::shared_ptr<ParseError> made = std::make_shared<ParseError>();
                made->input = std::move(input);
                made->reason = std::move(reason);
                return made;
            }

            Str get_message() const override {
                return Str("parse error (input: " + input.str() + ", reason: " + reason.str() + ")");
            }

            Str get_input() const {
                return input;
            }

            Str get_reason() const {
                return reason;
            }
        };

        // What making a string from a code point gives for one that isn't a
        // Unicode scalar value, with that number.
        struct InvalidCodePoint : public virtual CoreError {
            int32_t codePoint = 0;

            static std::shared_ptr<InvalidCodePoint> make(int32_t codePoint) {
                std::shared_ptr<InvalidCodePoint> made = std::make_shared<InvalidCodePoint>();
                made->codePoint = codePoint;
                return made;
            }

            Str get_message() const override {
                return Str("invalid code point (codePoint: " + std::to_string(codePoint) + ")");
            }

            int32_t get_codePoint() const {
                return codePoint;
            }
        };

        // What awaiting a broken promise gives. It has no fields: a broken
        // promise doesn't say why.
        struct BrokenPromise : public virtual CoreError {
            static std::shared_ptr<BrokenPromise> make() {
                return std::make_shared<BrokenPromise>();
            }

            Str get_message() const override {
                return Str("broken promise");
            }
        };

        // A promise's result once settled: its value, or a `BrokenPromise`.
        template<class T>
        using Awaited = Expected<T, std::shared_ptr<BrokenPromise>>;

        // A code point's result: its value, or an `InvalidCodePoint`.
        template<class T>
        using CodePointed = Expected<T, std::shared_ptr<InvalidCodePoint>>;

        // A conversion's result: its value, or a `ParseError`.
        template<class T>
        using Parsed = Expected<T, std::shared_ptr<ParseError>>;

        // A failed conversion of `input`, as text, and why.
        inline Unexpected<std::shared_ptr<ParseError>> parse_error(Str input, Str reason) {
            return unexpected(ParseError::make(std::move(input), std::move(reason)));
        }

    }
}
