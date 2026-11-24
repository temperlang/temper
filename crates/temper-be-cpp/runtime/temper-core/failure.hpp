#pragma once
// Declared failures (RFC 164): what every library's failure types share.
//
// A library's failure class is a struct, a `shared_ptr` as any class's,
// below its library's base (`struct FailuresError : virtual Failure`),
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

    }
}
