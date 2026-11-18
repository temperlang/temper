#pragma once
#include <condition_variable>
#include <cstddef>
#include <deque>
#include <functional>
#include <memory>
#include <mutex>
#include <utility>
#include <vector>
#include "temper_bubble.hpp"
#include "generator.hpp"
#if defined(__cpp_impl_coroutine)
#include <coroutine>
#endif

namespace temper {
    namespace core {

        // Async here is cooperative and single-threaded: instead of blocking a thread,
        // a generator suspends at each `await` and registers a continuation that is run
        // when the awaited promise settles. Continuations are dispatched through a FIFO
        // queue so that independently launched async blocks interleave in a predictable
        // order rather than recursing into one another.
        // Generated code calls only async_run, awake_upon, and the promise builders,
        // never the queue, so the queue stays swappable (one per isolate, say). It's
        // single-threaded: settle promises on the thread that runs Temper code.
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

        // Work outside any turn, as std/net's requests on another thread:
        // async_hold() when it starts, then one async_post(task) from any
        // thread when it's done, with what to run on the thread that runs
        // Temper code (settling its promise, say). When no task is ready,
        // the outermost drain waits for posted tasks while any hold is out,
        // as nothing else drives them.
        struct AsyncInbox {
            std::mutex mutex;
            std::condition_variable posted;
            std::deque<std::function<void()>> tasks;
            std::size_t holds = 0;
        };

        inline AsyncInbox& async_inbox() {
            // Never destroyed, since another thread may post as the
            // program exits.
            static AsyncInbox* inbox = new AsyncInbox();
            return *inbox;
        }

        inline void async_hold() {
            AsyncInbox& inbox = async_inbox();
            std::lock_guard<std::mutex> lock(inbox.mutex);
            ++inbox.holds;
        }

        inline void async_post(std::function<void()> task) {
            AsyncInbox& inbox = async_inbox();
            {
                std::lock_guard<std::mutex> lock(inbox.mutex);
                inbox.tasks.push_back(std::move(task));
                if (inbox.holds > 0) {
                    --inbox.holds;
                }
            }
            inbox.posted.notify_all();
        }

        // Moves posted tasks to the queue, first waiting for one while any
        // hold is out; false if there were none to move.
        inline bool async_take_posted() {
            AsyncInbox& inbox = async_inbox();
            std::unique_lock<std::mutex> lock(inbox.mutex);
            inbox.posted.wait(lock, [&inbox] { return !inbox.tasks.empty() || inbox.holds == 0; });
            if (inbox.tasks.empty()) {
                return false;
            }
            for (std::function<void()>& task : inbox.tasks) {
                async_enqueue(std::move(task));
            }
            inbox.tasks.clear();
            return true;
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
            do {
                while (!async_task_queue().empty()) {
                    std::function<void()> task = async_task_queue().front();
                    async_task_queue().pop_front();
                    task();
                }
            } while (async_take_posted());
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

            // Queues the continuations, and runs them unless a task that's
            // running settled it: code outside any async block, such as a
            // library's init completing a promise that a block awaits, has
            // no loop of its own to drain the queue.
            void settle() {
                std::vector<std::function<void()>> pending;
                pending.swap(continuations);
                for (std::function<void()>& task : pending) {
                    async_enqueue(std::move(task));
                }
                async_drain();
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
        T get_promise_result_sync(std::shared_ptr<Promise<T>> promise) {
            if (promise->is_broken()) {
                throw TemperBubble();
            }
            return promise->get();
        }

#if defined(__cpp_impl_coroutine)
        // C++20 interop: `co_await promise` in a C++ coroutine suspends it
        // until the Temper promise settles, then gives its value, or throws
        // `TemperBubble` if it's broken. The coroutine resumes from the async
        // queue, as a Temper async block does.
        template<class R>
        struct PromiseAwaiter {
            std::shared_ptr<Promise<R>> promise;
            bool await_ready() const {
                return promise->state->ready || promise->state->broken;
            }
            void await_suspend(std::coroutine_handle<> handle) {
                promise->on_ready([handle]() { handle.resume(); });
            }
            R await_resume() {
                return promise->get();
            }
        };

        template<class R>
        PromiseAwaiter<R> operator co_await(std::shared_ptr<Promise<R>> promise) {
            return PromiseAwaiter<R>{std::move(promise)};
        }
#endif

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
