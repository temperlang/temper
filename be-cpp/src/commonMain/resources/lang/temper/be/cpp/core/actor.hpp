#pragma once
#include <atomic>
#include <memory>
#include <mutex>
#include <vector>
#include "base_types.hpp"
#include "generator.hpp"
#include "promise.hpp"

namespace temper {
    namespace core {

        // Turns for `@actor` classes.
        //
        // Generated code runs on one thread, so Temper code alone never runs two turns
        // of one actor at once. What this adds is for a host program that calls an
        // actor's members from several threads: each call into an actor, its getters,
        // setters and constructor included, holds that actor's mutex for the call.
        //
        // Only the actor's own members are made safe this way. The async run queue,
        // promises and module-level variables are still meant for one thread, so an
        // async block can only be launched and drained from one thread at a time.

        struct ActorState;

        // What other threads need to know about a thread that is in some actor's turn:
        // which actor, if any, it is blocked waiting for. Records are pooled and never
        // freed, so a record read through a stale `owner` pointer is still valid memory.
        struct ActorThread {
            std::atomic<const ActorState*> waiting_for{nullptr};
        };

        // Held by every instance of an `@actor` class, through a shared_ptr so that
        // members emitted `const` can still lock it and async steps can keep it alive.
        struct ActorState {
            std::mutex mutex;
            // The thread whose turn holds `mutex`, or null.
            std::atomic<ActorThread*> owner{nullptr};
        };

        inline ActorThread& actor_thread() {
            struct Pool {
                std::mutex mutex;
                std::vector<ActorThread*> free;
            };
            static Pool* pool = new Pool();
            struct Holder {
                ActorThread* record;
                Holder() {
                    std::lock_guard<std::mutex> guard(pool->mutex);
                    if (pool->free.empty()) {
                        record = new ActorThread();
                    } else {
                        record = pool->free.back();
                        pool->free.pop_back();
                    }
                }
                ~Holder() {
                    std::lock_guard<std::mutex> guard(pool->mutex);
                    pool->free.push_back(record);
                }
            };
            static thread_local Holder holder;
            return *holder.record;
        }

        // True if `me`, about to wait for `wanted`, would wait forever: `wanted` is held
        // by a thread waiting for an actor held by a thread ... waiting for an actor that
        // `me` holds.
        //
        // The first pass follows owner and waiting_for links, which other threads change
        // as it reads them, so what it finds may be a cycle that never existed at one
        // instant. The second pass rechecks the links from the end of the path back to
        // the start. The last thread on the path waits for an actor `me` holds, so it
        // cannot stop waiting; once a link is confirmed after the one it leads to, it
        // cannot change either. A path that survives the recheck is a real deadlock.
        inline bool actor_wait_is_cycle(const ActorState& wanted, const ActorThread& me) {
            struct Hop {
                const ActorState* actor;
                const ActorThread* owner;
            };
            // Each thread waits for at most one actor, so a path longer than this
            // keeps visiting threads that are no longer where they were. Give up.
            const std::size_t max_hops = 64;
            std::vector<Hop> path;
            const ActorState* actor = &wanted;
            while (path.size() < max_hops) {
                const ActorThread* owner = actor->owner.load();
                if (owner == nullptr) {
                    return false;
                }
                path.push_back(Hop{actor, owner});
                if (owner == &me) {
                    break;
                }
                actor = owner->waiting_for.load();
                if (actor == nullptr) {
                    return false;
                }
            }
            if (path.empty() || path.back().owner != &me) {
                return false;
            }
            for (std::size_t i = path.size() - 1; i > 0; i--) {
                if (path[i].actor->owner.load() != path[i].owner) {
                    return false;
                }
                if (path[i - 1].owner->waiting_for.load() != path[i].actor) {
                    return false;
                }
            }
            return path[0].actor->owner.load() == path[0].owner;
        }

        // One turn, held for the duration of a member call. Constructed first thing in
        // every member of an `@actor` class; the destructor releases the actor on
        // return and on a bubble (a C++ exception) alike.
        //
        // A call into an actor this thread already holds is part of the same chain of
        // calls (`this.m()`, a callback the actor invoked calling back in, or A -> B -> A)
        // and runs inline without locking again. Only `owner` tells: it is set to this
        // thread's record only by this thread, while it holds the mutex.
        //
        // A call that finds the actor held by another thread records what it waits for
        // and checks whether that wait closes a loop of threads, each waiting for an
        // actor the next one holds. If it does, it panics with "actor call cycle"
        // instead of deadlocking. A panic terminates the process.
        class ActorTurn {
        public:
            explicit ActorTurn(ActorState& state) : state_(state), locked_(false) {
                ActorThread& me = actor_thread();
                if (state.owner.load(std::memory_order_relaxed) == &me) {
                    return;
                }
                if (!state.mutex.try_lock()) {
                    me.waiting_for.store(&state);
                    if (actor_wait_is_cycle(state, me)) {
                        panic<void>("actor call cycle");
                    }
                    state.mutex.lock();
                    me.waiting_for.store(nullptr);
                }
                state.owner.store(&me);
                locked_ = true;
            }

            ~ActorTurn() {
                if (locked_) {
                    state_.owner.store(nullptr);
                    state_.mutex.unlock();
                }
            }

            ActorTurn(const ActorTurn&) = delete;
            ActorTurn& operator=(const ActorTurn&) = delete;

        private:
            ActorState& state_;
            bool locked_;
        };

        // Wrap a generator so that each step, from its start or from one await to the
        // next, runs as its own turn on `state`. The wrapper passes itself as the
        // step's generator argument, so the `awake_upon` an await registers resumes
        // through the wrapper and takes the turn again. An await is a turn boundary:
        // other calls may run on the actor while the block is suspended.
        template<class G>
        std::shared_ptr<G> actor_steps(std::shared_ptr<ActorState> state, std::shared_ptr<G> inner) {
            return std::make_shared<G>([state, inner](std::shared_ptr<G> self) {
                ActorTurn turn(*state);
                return inner->step(self);
            });
        }

        // `async { }` written inside a member of an `@actor` class. Like `async_run`,
        // it only queues the block.
        template<class FactoryFn>
        void async_run_on(std::shared_ptr<ActorState> state, FactoryFn factory) {
            async_enqueue([state, factory]() {
                next(actor_steps(state, factory()));
            });
        }

    }
}
