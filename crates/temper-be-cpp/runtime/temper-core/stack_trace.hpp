#pragma once
// Stack traces for the programs Temper generates: `temper run`'s `main.cpp`
// and `temper test`'s `test.cpp` call `install_stack_traces()`, so a panic
// or an uncaught bubble or failure prints where it happened, and a test that panics
// has the trace in its failure. Libraries never call it: a terminate
// handler is the whole process's, so it's the application's to set. An
// application may call it too.
//
// The frames come from C++23's `std::stacktrace` where the harness's CMake
// finds it (`stack_trace.cmake`, which builds `stack_trace.cpp` and defines
// `TEMPER_STD_STACKTRACE`): names and, in debug builds, files and lines.
// Otherwise `backtrace()` (glibc, macOS, the BSDs), naming exported
// functions (CMake links the harnesses with `ENABLE_EXPORTS`, `-rdynamic`)
// and giving the others' offsets, for `addr2line`. Elsewhere (MSVC without
// `std::stacktrace`, musl), no trace, but everything else the same.
#include <cstdint>
#include <cstdlib>
#include <exception>
#include <functional>
#include <iostream>
#include <string>
#include <vector>
#include "failure.hpp"
#include "temper_bubble.hpp"

#if !defined(TEMPER_STD_STACKTRACE) && defined(__has_include)
#if __has_include(<execinfo.h>) && __has_include(<dlfcn.h>) && __has_include(<cxxabi.h>)
#define TEMPER_STACK_TRACE_EXECINFO 1
#include <cxxabi.h>
#include <dlfcn.h>
#include <execinfo.h>
#endif
#endif

namespace temper {
    namespace core {

        // One frame, innermost first: the function's name, if known, and
        // where it is, as `FILE:LINE`, or `BINARY+0xOFFSET` where only that
        // is known; `own` where it's the program's own code rather than a
        // system library's.
        struct StackFrame {
            std::string name;
            std::string where;
            bool own;
        };

        // A stack, captured where it was, its frames found (named, and
        // placed in their files) only when called: finding them costs,
        // and a test's panic that `assertPanics` catches is never shown.
        using StackCapture = std::function<std::vector<StackFrame>()>;

#if defined(TEMPER_STD_STACKTRACE)
        // The calling thread's stack, from `std::stacktrace`, in
        // `stack_trace.cpp`, which C++23 builds.
        StackCapture std_stack_capture();
#endif

        // The calling thread's stack, its frames innermost first, those
        // that can be found; none where there's no way to.
        inline StackCapture capture_stack() {
#if defined(TEMPER_STD_STACKTRACE)
            return std_stack_capture();
#elif defined(TEMPER_STACK_TRACE_EXECINFO)
            std::vector<void*> addresses(128);
            addresses.resize(static_cast<std::size_t>(backtrace(addresses.data(), 128)));
            return [addresses]() {
                std::vector<StackFrame> out;
                // Frame 0 is the capture, in the program, which tells its
                // own frames from a system library's.
                const void* program = nullptr;
                for (std::size_t i = 0; i < addresses.size(); i++) {
                    // A caller's address is where the call returns to, past
                    // the call, which may be past the function's end when
                    // the callee never returns (a panic): the byte before is
                    // the call's.
                    std::uintptr_t address = reinterpret_cast<std::uintptr_t>(addresses[i]);
                    if (i > 0) {
                        address--;
                    }
                    Dl_info info;
                    StackFrame frame{std::string(), std::string(), false};
                    if (dladdr(reinterpret_cast<void*>(address), &info) != 0) {
                        if (i == 0) {
                            program = info.dli_fbase;
                        }
                        frame.own = info.dli_fbase == program;
                        if (info.dli_sname != nullptr) {
                            int status = 0;
                            char* demangled = abi::__cxa_demangle(info.dli_sname, nullptr, nullptr, &status);
                            frame.name = demangled != nullptr ? demangled : info.dli_sname;
                            std::free(demangled);
                        }
                        if (info.dli_fname != nullptr) {
                            std::string binary = info.dli_fname;
                            std::string::size_type slash = binary.find_last_of('/');
                            if (slash != std::string::npos) {
                                binary = binary.substr(slash + 1);
                            }
                            std::uintptr_t offset = address - reinterpret_cast<std::uintptr_t>(info.dli_fbase);
                            static const char digits[] = "0123456789abcdef";
                            std::string hex;
                            do {
                                hex.insert(hex.begin(), digits[offset % 16]);
                                offset /= 16;
                            } while (offset != 0);
                            frame.where = binary + "+0x" + hex;
                        }
                    }
                    out.push_back(frame);
                }
                return out;
            };
#else
            return []() { return std::vector<StackFrame>(); };
#endif
        }

        // A function's qualified name, from a frame's name: no return
        // type, parameters, or qualifiers (`void temper::core::panic<void>()`
        // is `temper::core::panic<void>`), nor MSVC's module and offset
        // (`a-main!a::boom+0x2B`).
        inline std::string stack_frame_function(const std::string& name) {
            int depth = 0;
            std::string::size_type start = 0;
            for (std::string::size_type i = 0; i < name.size(); i++) {
                char c = name[i];
                if (c == '<' || c == '{' || c == '[') {
                    depth++;
                } else if ((c == '>' || c == '}' || c == ']') && depth > 0) {
                    depth--;
                } else if (depth == 0 && (c == ' ' || c == '!')) {
                    start = i + 1;
                } else if (depth == 0 && (c == '(' || c == '+')) {
                    return name.substr(start, i - start);
                }
            }
            return name.substr(start);
        }

        // Whether a qualified name has a part that C++ reserves for its
        // implementation, which generated code never names: one with
        // `__`, or `_` and a capital, or a name outside any namespace
        // starting with `_` (`std::__invoke_r`, `_M_invoke`, `_start`).
        inline bool stack_name_reserved(const std::string& function) {
            int depth = 0;
            bool top = true;
            std::string::size_type part = 0;
            for (std::string::size_type i = 0; i <= function.size(); i++) {
                char c = i < function.size() ? function[i] : ':';
                if (c == '<' || c == '{' || c == '(') {
                    depth++;
                } else if ((c == '>' || c == '}' || c == ')') && depth > 0) {
                    depth--;
                } else if (depth == 0 && c == ':') {
                    std::string name = function.substr(part, i - part);
                    bool last = i + 1 >= function.size();
                    if (name.find("__") != std::string::npos
                        || (name.size() > 1 && name[0] == '_' && name[1] >= 'A' && name[1] <= 'Z')
                        || (top && last && !name.empty() && name[0] == '_')) {
                        return true;
                    }
                    if (!name.empty()) {
                        top = false;
                    }
                    part = i + 1;
                }
            }
            return false;
        }

        // Whether a frame is the runtime's, C++'s, or the system's, which
        // a trace leaves out: no name outside the program, a name in
        // `temper::core` or `std` or reserved for C++'s implementation,
        // or a file of the runtime's or of C++'s headers (where
        // `std::stacktrace` names templates without their namespaces).
        inline bool stack_frame_hidden(const StackFrame& frame) {
            static const char* const files[] = {
                "temper-core/", "temper-core\\", "/include/c++/", "\\include\\c++\\",
            };
            for (const char* file : files) {
                if (frame.where.find(file) != std::string::npos) {
                    return true;
                }
            }
            if (frame.name.empty()) {
                return !frame.own;
            }
            std::string function = stack_frame_function(frame.name);
            static const char* const prefixes[] = {
                "temper::core::", "std::", "invoke_main", "BaseThreadInitThunk",
                "RtlUserThreadStart",
            };
            for (const char* prefix : prefixes) {
                if (function.compare(0, std::string(prefix).size(), prefix) == 0) {
                    return true;
                }
            }
            return stack_name_reserved(function);
        }

        // Whether a frame is the harness's, below the code it runs:
        // `main`, or one of its lambdas, by name or none.
        inline bool stack_frame_harness(const StackFrame& frame) {
            std::string function = stack_frame_function(frame.name);
            return function.empty() || function == "main" || function.compare(0, 6, "main::") == 0
                || function == "operator" || function == "_FUN";
        }

        // A stack's frames, one `  at NAME (WHERE)` line a frame,
        // innermost first, without the runtime's frames, C++'s, or the
        // harness's below the program's code; empty where there's no way
        // to find them.
        inline std::string stack_trace(const StackCapture& capture) {
            std::vector<StackFrame> frames;
            for (const StackFrame& frame : capture()) {
                if (!stack_frame_hidden(frame)) {
                    frames.push_back(frame);
                }
            }
            while (!frames.empty() && stack_frame_harness(frames.back())) {
                frames.pop_back();
            }
            std::string out;
            for (const StackFrame& frame : frames) {
                out += "  at ";
                out += frame.name.empty() ? "??" : frame.name;
                if (!frame.where.empty()) {
                    out += " (" + frame.where + ")";
                }
                out += "\n";
            }
            return out;
        }

        // The calling thread's stack, as above.
        inline std::string stack_trace() {
            return stack_trace(capture_stack());
        }

        // The handler `install_stack_traces` sets: what was uncaught, if
        // anything (a panic has said what it was already, and what it
        // replaced, which is still being handled), then the
        // stack, which is still where it went wrong, since nothing caught
        // it, then abort, as the default handler does.
        [[noreturn]] inline void print_stack_trace_and_abort() {
            std::string trace = stack_trace();
            std::cout.flush();
            std::exception_ptr uncaught = std::current_exception();
            if (uncaught && !panicking()) {
                try {
                    std::rethrow_exception(uncaught);
                } catch (const TemperBubble& bubble) {
                    std::cerr << "uncaught bubble: " << bubble.what() << "\n";
                } catch (const UncaughtFailure& failure) {
                    std::cerr << "uncaught failure: " << failure.what() << "\n";
                } catch (const std::exception& error) {
                    std::cerr << "uncaught exception: " << error.what() << "\n";
                } catch (...) {
                    std::cerr << "uncaught exception\n";
                }
            }
            std::cerr << trace << std::flush;
            std::abort();
        }

        // Prints a stack trace when the process ends by a panic or an
        // uncaught exception: a terminate handler, so the program's to
        // call, never a library's.
        inline void install_stack_traces() {
            std::set_terminate(print_stack_trace_and_abort);
        }

    }
}
