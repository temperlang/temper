# Stack traces for the programs Temper generates (`stack_trace.hpp`), which
# each library's CMakeLists.txt includes for its `-main` and `-test`
# programs, never the library itself. Where the compiler has C++23's
# `std::stacktrace`, found once per build directory, `stack_trace.cpp`,
# built as C++23, gives names, files, and lines; otherwise the programs
# export their symbols (`-rdynamic`), which `backtrace()` names. With
# TEMPER_STACK_TRACES off, neither: no check, and traces only as
# `backtrace()` finds them.

# This directory, for the functions below, wherever they're called.
set(TEMPER_CORE_DIR "${CMAKE_CURRENT_LIST_DIR}" CACHE INTERNAL "")
include_guard(GLOBAL)

option(TEMPER_STACK_TRACES "Stack traces from Temper's generated programs" ON)

# The flag and library, if any, that `std::stacktrace` needs, or NONE.
function(temper_find_std_stacktrace)
  if(DEFINED TEMPER_STACKTRACE_FLAG)
    return()
  endif()
  include(CheckCXXSourceCompiles)
  # Each try alone, with no C++ standard but its own flag.
  unset(CMAKE_CXX_STANDARD)
  set(CMAKE_REQUIRED_QUIET ON)
  if(MSVC)
    set(flags "/std:c++latest")
  else()
    set(flags "-std=c++23" "-std=c++2b")
  endif()
  set(found_flag NONE)
  set(found_library "")
  foreach(flag IN LISTS flags)
    # GCC 12 and 13 have it in stdc++_libbacktrace, 13.3 and later in
    # stdc++exp; MSVC needs neither.
    foreach(library IN ITEMS none stdc++exp stdc++_libbacktrace)
      if(found_flag STREQUAL "NONE")
        set(CMAKE_REQUIRED_FLAGS "${flag}")
        set(CMAKE_REQUIRED_LIBRARIES "")
        if(NOT library STREQUAL "none")
          set(CMAKE_REQUIRED_LIBRARIES "${library}")
        endif()
        string(MAKE_C_IDENTIFIER "TEMPER_STACKTRACE_${flag}_${library}" result)
        check_cxx_source_compiles("
#include <stacktrace>
#include <string>
int main() {
  return static_cast<int>(std::to_string(std::stacktrace::current()).size() == 0);
}" ${result})
        if(${result})
          set(found_flag "${flag}")
          set(found_library "${CMAKE_REQUIRED_LIBRARIES}")
        endif()
      endif()
    endforeach()
  endforeach()
  set(TEMPER_STACKTRACE_FLAG "${found_flag}" CACHE INTERNAL "")
  set(TEMPER_STACKTRACE_LIBRARY "${found_library}" CACHE INTERNAL "")
endfunction()

# Gives a program Temper generates stack traces.
function(temper_stack_traces target)
  if(NOT TEMPER_STACK_TRACES)
    return()
  endif()
  temper_find_std_stacktrace()
  if(TEMPER_STACKTRACE_FLAG STREQUAL "NONE")
    set_target_properties(${target} PROPERTIES ENABLE_EXPORTS ON)
    target_link_libraries(${target} PRIVATE ${CMAKE_DL_LIBS})
    return()
  endif()
  if(NOT TARGET temper-stack-trace)
    # Its own standard, not the libraries' C++14.
    unset(CMAKE_CXX_STANDARD)
    add_library(temper-stack-trace STATIC ${TEMPER_CORE_DIR}/stack_trace.cpp)
    target_compile_options(temper-stack-trace PRIVATE ${TEMPER_STACKTRACE_FLAG})
    target_compile_definitions(temper-stack-trace PUBLIC TEMPER_STD_STACKTRACE)
    target_link_libraries(temper-stack-trace PUBLIC ${TEMPER_STACKTRACE_LIBRARY})
  endif()
  target_link_libraries(${target} PRIVATE temper-stack-trace)
endfunction()
