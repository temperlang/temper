#pragma once
#include <functional>
#include <memory>
#include <utility>
#include "base_types.hpp"
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
            bool is_done() const override { return false; }
            T value() const override { return held; }
            static std::shared_ptr<GeneratorResult<T>> make(T value) {
                return std::make_shared<ValueResult<T>>(std::move(value));
            }
        };

        template<class T>
        struct DoneResult : GeneratorResult<T> {
            bool is_done() const override { return true; }
            T value() const override {
                return bubble<T>("generator is exhausted");
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

        // A generator whose `next` may bubble (throw). Built from a step function that
        // runs until the next yield or completion, returning the corresponding result.
        template<class T>
        struct Generator {
            using Step = std::function<std::shared_ptr<GeneratorResult<T>>(std::shared_ptr<Generator<T>>)>;
            Step step;
            bool done = false;
            explicit Generator(Step step) : step(std::move(step)) {}
            void close() { done = true; }
        };

        // A generator whose `next` does not bubble.
        template<class T>
        struct SafeGenerator {
            using Step = std::function<std::shared_ptr<GeneratorResult<T>>(std::shared_ptr<SafeGenerator<T>>)>;
            Step step;
            bool done = false;
            explicit SafeGenerator(Step step) : step(std::move(step)) {}
            void close() { done = true; }
        };

        // The parameter is spelled out as std::function (rather than the Step typedef)
        // so that T is deducible from the argument at the call site.
        template<class T>
        std::shared_ptr<Generator<T>> adapt_generator_fn(
            std::function<std::shared_ptr<GeneratorResult<T>>(std::shared_ptr<Generator<T>>)> step
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
        std::shared_ptr<GeneratorResult<T>> next(std::shared_ptr<Generator<T>> generator) {
            if (generator->done) {
                return std::make_shared<DoneResult<T>>();
            }
            std::shared_ptr<GeneratorResult<T>> result = generator->step(generator);
            if (result->is_done()) {
                generator->done = true;
            }
            return result;
        }

        template<class T>
        std::shared_ptr<GeneratorResult<T>> next(std::shared_ptr<SafeGenerator<T>> generator) {
            if (generator->done) {
                return std::make_shared<DoneResult<T>>();
            }
            std::shared_ptr<GeneratorResult<T>> result = generator->step(generator);
            if (result->is_done()) {
                generator->done = true;
            }
            return result;
        }

    }
}
