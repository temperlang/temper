#pragma once
#include <deque>
#include <functional>
#include <memory>
#include <utility>
#include <vector>
#include "temper_bubble.hpp"
#include "generator.hpp"

namespace temper {
    namespace core {

        // Async here is cooperative and single-threaded: instead of blocking a thread,
        // a generator suspends at each `await` and registers a continuation that is run
        // when the awaited promise settles. Continuations are dispatched through a FIFO
        // queue so that independently launched async blocks interleave in a predictable
        // order rather than recursing into one another.
        inline std::deque<std::function<void()>>& async_task_queue() {
            static std::deque<std::function<void()>> queue;
            return queue;
        }

        inline bool& async_loop_running() {
            static bool running = false;
            return running;
        }

        inline void async_enqueue(std::function<void()> task) {
            async_task_queue().push_back(std::move(task));
        }

        // Run queued tasks to completion. Re-entrant calls are no-ops so that nested
        // async blocks merely enqueue work for the outermost loop to drain.
        inline void async_drain() {
            if (async_loop_running()) {
                return;
            }
            async_loop_running() = true;
            // Reset the running flag even if a task throws (Temper bubbles are C++
            // exceptions); otherwise a bubble escaping the loop would permanently
            // wedge the async subsystem and strand any remaining queued tasks.
            struct ResetGuard {
                ~ResetGuard() { async_loop_running() = false; }
            } resetGuard;
            while (!async_task_queue().empty()) {
                std::function<void()> task = async_task_queue().front();
                async_task_queue().pop_front();
                task();
            }
        }

        template<class R>
        struct PromiseState {
            // Larger-alignment members first to minimize padding; all members are
            // accessed by name only, never positionally, so the order is unobservable.
            // A promise may be awaited from several places before it settles, so keep
            // every registered continuation rather than only the most recent one.
            std::vector<std::function<void()>> continuations;
            R value{};
            bool ready = false;
            bool broken = false;

            void settle() {
                std::vector<std::function<void()>> pending;
                pending.swap(continuations);
                for (std::function<void()>& task : pending) {
                    async_enqueue(std::move(task));
                }
            }
        };

        template<class R>
        struct Promise {
            std::shared_ptr<PromiseState<R>> state;
            explicit Promise(std::shared_ptr<PromiseState<R>> state) : state(std::move(state)) {}

            // Schedule `task` to run once this promise has settled (immediately queued
            // if it already has).
            void on_ready(std::function<void()> task) {
                if (state->ready || state->broken) {
                    async_enqueue(std::move(task));
                } else {
                    state->continuations.push_back(std::move(task));
                }
            }

            bool is_broken() const {
                return state->broken;
            }

            R get() {
                if (state->broken) {
                    return bubble<R>("awaited a broken promise");
                }
                return state->value;
            }
        };

        template<class R>
        struct PromiseBuilder {
            std::shared_ptr<PromiseState<R>> state = std::make_shared<PromiseState<R>>();
        };

        namespace PromiseBuilderNs {
            template<class R>
            std::shared_ptr<PromiseBuilder<R>> make() {
                return std::make_shared<PromiseBuilder<R>>();
            }
        }

        // The first completion or break wins; later ones are ignored. The value is
        // accepted as a separate type so that e.g. string literals convert to R.
        template<class R, class V>
        void complete(std::shared_ptr<PromiseBuilder<R>> builder, V&& value) {
            PromiseState<R>& state = *builder->state;
            if (!state.ready && !state.broken) {
                state.value = std::forward<V>(value);
                state.ready = true;
                state.settle();
            }
        }

        template<class R>
        void breakpromise(std::shared_ptr<PromiseBuilder<R>> builder) {
            PromiseState<R>& state = *builder->state;
            if (!state.ready && !state.broken) {
                state.broken = true;
                state.settle();
            }
        }

        template<class R>
        std::shared_ptr<Promise<R>> getpromise(std::shared_ptr<PromiseBuilder<R>> builder) {
            return std::make_shared<Promise<R>>(builder->state);
        }

        // Resume `generator` when `promise` settles. Used to implement `await`.
        template<class T, class GenT>
        void awake_upon(std::shared_ptr<Promise<T>> promise, std::shared_ptr<GenT> generator) {
            promise->on_ready([generator]() {
                next(generator);
            });
        }

        // Read the value an awaited promise settled with. A handler scope cannot span a
        // suspension point, so rather than bubbling, a broken promise is reported by
        // setting the caller's `fail` flag; the state machine then branches to its
        // failure case (e.g. an `orelse`).
        template<class T>
        T get_promise_result_sync(bool& fail, std::shared_ptr<Promise<T>> promise) {
            if (promise->is_broken()) {
                fail = true;
                return T();
            }
            return promise->get();
        }

        // Launch an async block: build its generator and drive it, then drain any
        // continuations its awaits scheduled. Accepts any factory producing a Generator
        // or SafeGenerator (next() is overloaded for both).
        template<class FactoryFn>
        void async_run(FactoryFn factory) {
            async_enqueue([factory]() {
                next(factory());
            });
            async_drain();
        }

    }
}
