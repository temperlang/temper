#pragma once
#include <functional>
#include <memory>
#include <utility>
#include "base_types.hpp"
#include "expected.hpp"
#include "failure.hpp"
#include "temper_bubble.hpp"

namespace temper {
    namespace core {

        // The result of advancing a generator one step: either a yielded value
        // (ValueResult) or a signal that the generator has finished (DoneResult).
        template<class T>
        struct GeneratorResult {
            virtual ~GeneratorResult() = default;
            virtual bool is_done() const = 0;
            virtual T value() const = 0;
        };

        template<class T>
        struct ValueResult : GeneratorResult<T> {
            T held;
            explicit ValueResult(T value) : held(std::move(value)) {}
            bool is_done() const override {
                return false;
            }
            T value() const override {
                return held;
            }
            static std::shared_ptr<GeneratorResult<T>> make(T value) {
                return std::make_shared<ValueResult<T>>(std::move(value));
            }
        };

        template<class T>
        struct DoneResult : GeneratorResult<T> {
            bool is_done() const override {
                return true;
            }
            T value() const override {
                return panic<T>("generator is exhausted");
            }
        };

        // The coroutine lowering writes `doneResult<Whatever>()` without knowing the
        // surrounding generator's element type, so the sentinel adapts to any
        // GeneratorResult<T> via a templated conversion.
        struct DoneResultSentinel {
            template<class T>
            operator std::shared_ptr<GeneratorResult<T>>() const {
                return std::make_shared<DoneResult<T>>();
            }
        };

        inline DoneResultSentinel doneResult() {
            return DoneResultSentinel{};
        }

        // A generator whose body may throw any library's failure: a step gives
        // its result, or the failure, which leaves it done, as an Expected;
        // `next` gives a `BrokenGenerator` with that failure as its cause.
        template<class T>
        struct Generator {
            using Result = Expected<std::shared_ptr<GeneratorResult<T>>, std::shared_ptr<Failure>>;
            using Next = Expected<std::shared_ptr<GeneratorResult<T>>, std::shared_ptr<BrokenGenerator>>;
            using Step = std::function<Result(std::shared_ptr<Generator<T>>)>;
            Step step;
            bool done = false;
            explicit Generator(Step step) : step(std::move(step)) {}
            void close() {
                done = true;
            }
        };

        // A generator whose `next` can't fail. This is deliberately a separate struct from
        // [Generator] rather than a shared template specialized on a flag: the two are distinct C++
        // types so the `next` overloads below resolve statically to the failing or non-failing
        // variant at each call site (the `Step` self-type also differs accordingly).
        template<class T>
        struct SafeGenerator {
            using Step = std::function<std::shared_ptr<GeneratorResult<T>>(std::shared_ptr<SafeGenerator<T>>)>;
            Step step;
            bool done = false;
            explicit SafeGenerator(Step step) : step(std::move(step)) {}
            void close() {
                done = true;
            }
        };

        // The parameter is spelled out as std::function (rather than the Step typedef)
        // so that T is deducible from the argument at the call site.
        template<class T>
        std::shared_ptr<Generator<T>> adapt_generator_fn(
            std::function<typename Generator<T>::Result(std::shared_ptr<Generator<T>>)> step
        ) {
            return std::make_shared<Generator<T>>(std::move(step));
        }

        template<class T>
        std::shared_ptr<SafeGenerator<T>> safe_adapt_generator_fn(
            std::function<std::shared_ptr<GeneratorResult<T>>(std::shared_ptr<SafeGenerator<T>>)> step
        ) {
            return std::make_shared<SafeGenerator<T>>(std::move(step));
        }

        template<class T>
        typename Generator<T>::Next next(const std::shared_ptr<Generator<T>>& generator) {
            if (generator->done) {
                return std::shared_ptr<GeneratorResult<T>>(std::make_shared<DoneResult<T>>());
            }
            typename Generator<T>::Result result = [&]() {
                try {
                    return generator->step(generator);
                } catch (...) {
                    // A step that panics leaves it done.
                    generator->done = true;
                    throw;
                }
            }();
            // A step that fails, or ends, leaves it done.
            if (!result) {
                generator->done = true;
                return unexpected(with_cause(BrokenGenerator::make(), result.error()));
            }
            if ((*result)->is_done()) {
                generator->done = true;
            }
            return *result;
        }

        template<class T>
        std::shared_ptr<GeneratorResult<T>> next(const std::shared_ptr<SafeGenerator<T>>& generator) {
            if (generator->done) {
                return std::make_shared<DoneResult<T>>();
            }
            std::shared_ptr<GeneratorResult<T>> result;
            try {
                result = generator->step(generator);
            } catch (...) {
                // A step that fails leaves it done.
                generator->done = true;
                throw;
            }
            if (result->is_done()) {
                generator->done = true;
            }
            return result;
        }

        // A generator that can't fail as one that may.
        template<class T>
        std::shared_ptr<Generator<T>> generator_of(std::shared_ptr<SafeGenerator<T>> safe) {
            return std::make_shared<Generator<T>>(
                [safe](std::shared_ptr<Generator<T>>) -> typename Generator<T>::Result {
                    return next(safe);
                }
            );
        }

        // A range-for over a generator steps it: `for (auto v : generator)`,
        // where `generator` is a `shared_ptr` to a SafeGenerator,
        // finds these by argument-dependent lookup. The begin and end
        // iterators are of one type, as C++14 needs. Every loop shares the
        // one generator, so looping again continues where the last left off.
        template<class GenT, class T>
        struct GeneratorIterator {
            std::shared_ptr<GenT> generator;
            std::shared_ptr<GeneratorResult<T>> current;

            bool at_end() const {
                return !current || current->is_done();
            }
            T operator*() const {
                return current->value();
            }
            GeneratorIterator& operator++() {
                current = next(generator);
                return *this;
            }
            bool operator==(const GeneratorIterator& other) const {
                return at_end() == other.at_end();
            }
            bool operator!=(const GeneratorIterator& other) const {
                return !(*this == other);
            }
        };

        template<class T>
        GeneratorIterator<SafeGenerator<T>, T> begin(const std::shared_ptr<SafeGenerator<T>>& generator) {
            return GeneratorIterator<SafeGenerator<T>, T>{generator, next(generator)};
        }

        template<class T>
        GeneratorIterator<SafeGenerator<T>, T> end(const std::shared_ptr<SafeGenerator<T>>& generator) {
            return GeneratorIterator<SafeGenerator<T>, T>{generator, nullptr};
        }

    }
}
