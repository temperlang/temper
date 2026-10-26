#pragma once
// Support for module-level state and init on first use.
//
// Each generated module has a `global_init_<module>()` that runs its top-level
// initialization once. Users needn't call it: accessors for exported values and
// public static fields, exported functions, constructors, and static methods all
// call it first. See `init_once` and `GlobalSlot` below.
#include <atomic>
#include <mutex>
#include <new>

namespace temper {
    namespace core {

        /// Once-only state for one module's init. Constant-initialized, so it's
        /// ready before any dynamic initialization runs.
        struct ModuleInitState {
            std::atomic<bool> done;
            std::mutex mutex;
            constexpr ModuleInitState() : done(false), mutex() {}
        };

        /// Runs `init` once, thread-safely. Fast path is one acquire load.
        ///
        /// Re-entrant on the initializing thread: init code may call the module's
        /// own entry points, which call back here, and those calls return at once
        /// (seeing whatever state is set so far). Other threads wait for the first
        /// run to finish. If `init` throws, the next call tries again.
        ///
        /// Each module passes its own lambda, and each lambda has a distinct type,
        /// so each module gets its own statics here.
        template <typename Init>
        void init_once(Init init) {
            static ModuleInitState state;
            static thread_local bool running = false;
            if (state.done.load(std::memory_order_acquire) || running) {
                return;
            }
            std::lock_guard<std::mutex> lock(state.mutex);
            if (state.done.load(std::memory_order_relaxed)) {
                return;
            }
            running = true;
            try {
                init();
            } catch (...) {
                running = false;
                throw;
            }
            running = false;
            state.done.store(true, std::memory_order_release);
        }

        /// Storage for a module-level value or static field.
        ///
        /// Constant-initialized with no live `T`, so it can't be clobbered by a
        /// dynamic initializer running after another translation unit already
        /// initialized the module (the static initialization order fiasco). The
        /// module init calls `construct()` before assigning `value`, and generated
        /// code reads `value` directly. Never destroyed, so it stays readable
        /// during other objects' destruction at exit.
        template <typename T>
        struct GlobalSlot {
            union {
                char unset;
                T value;
            };
            bool constructed;
            constexpr GlobalSlot() : unset(0), constructed(false) {}
            ~GlobalSlot() {}
            GlobalSlot(GlobalSlot const &) = delete;
            GlobalSlot &operator=(GlobalSlot const &) = delete;
            void construct() {
                if (!constructed) {
                    new (&value) T();
                    constructed = true;
                }
            }
        };

    }
}
