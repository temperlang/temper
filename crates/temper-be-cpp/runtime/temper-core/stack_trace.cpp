// `stack_trace.hpp`'s frames from C++23's `std::stacktrace`, which
// `stack_trace.cmake` builds, as C++23, where the compiler has it, for the
// programs Temper generates; nothing else includes it.
#ifndef TEMPER_STD_STACKTRACE
#define TEMPER_STD_STACKTRACE 1
#endif
#include "stack_trace.hpp"
#include <stacktrace>

namespace temper {
    namespace core {

        void std_stack_frames(std::vector<StackFrame>& out) {
            for (const std::stacktrace_entry& entry : std::stacktrace::current()) {
                StackFrame frame{entry.description(), entry.source_file(), true};
                if (!frame.where.empty()) {
                    frame.where += ":" + std::to_string(entry.source_line());
                } else if (frame.name.empty()) {
                    // No name, no file: a system library's, as far as
                    // can be told.
                    frame.own = false;
                }
                out.push_back(frame);
            }
        }

    }
}
