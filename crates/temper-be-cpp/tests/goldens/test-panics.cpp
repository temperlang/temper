// init.hpp
#pragma once

#include <temper-core/core.hpp>
#ifdef TEMPER_TESTS
#include <std/testing.hpp>
#endif

namespace test_panics {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();


#ifdef TEMPER_TESTS
struct Problem;
struct Oops;

struct Problem : public virtual temper::core::Failure {};

struct Oops : public virtual Problem {
  static std::shared_ptr<Oops> make();
  temper::core::Str get_message() const override;
};

// Tests, which `test.cpp` runs.
void passesBefore(std::shared_ptr<temper_std::Test> const & test);
void panicsReadingPastTheEnd(std::shared_ptr<temper_std::Test> const & test);
void diesOutright(std::shared_ptr<temper_std::Test> const &);
void failsAnAssertion(std::shared_ptr<temper_std::Test> const & test);
void passesAfter(std::shared_ptr<temper_std::Test> const & test);
void diesAfterFailing(std::shared_ptr<temper_std::Test> const & test);
#endif

}  // namespace test_panics

// init.cpp
#include <test-panics/init.hpp>

namespace test_panics {

namespace core = temper::core;

#ifdef TEMPER_TESTS
std::shared_ptr<Oops> Oops::make() {
  std::shared_ptr<Oops> self = std::make_shared<Oops>();
  return self;
}

core::Str Oops::get_message() const {
  return TEMPER_STR("oops");
}

static core::Expected<int32_t, std::shared_ptr<Oops>> at(std::shared_ptr<std::vector<int32_t>> const & ls, int32_t i) {
  if (i == 99) {
    return core::unexpected(Oops::make());
  }
  return core::List::get<int32_t>(ls, i);
}
#endif  // TEMPER_TESTS

void global_init_init() {}

}  // namespace test_panics

#ifdef TEMPER_TESTS

namespace test_panics {

void passesBefore(std::shared_ptr<temper_std::Test> const & test) {
  {
    int32_t actual = core::Int::add(1, 1);
    int32_t expected = 2;
    test->assert_(actual == expected, [=]() -> core::Str { return "expected 1 + 1 == (" + std::to_string(expected) + ") not (" + std::to_string(actual) + ")"; });
  }
}

void panicsReadingPastTheEnd(std::shared_ptr<temper_std::Test> const & test) {
  int32_t n;
  auto atResult = test_panics::at(core::List::make<int32_t>(1, 2), 5);
  if (atResult.has_value()) {
    n = *atResult;
  } else {
    n = -1;
  }
  {
    int32_t actual = n;
    int32_t expected = -1;
    test->assert_(actual == expected, [=]() -> core::Str { return "expected n == (" + std::to_string(expected) + ") not (" + std::to_string(actual) + ")"; });
  }
}

void diesOutright(std::shared_ptr<temper_std::Test> const &) {
  core::panic();
}

void failsAnAssertion(std::shared_ptr<temper_std::Test> const & test) {
  test->assert_(core::Int::add(1, 1) == 3, []() -> core::Str { return TEMPER_STR("wrong on purpose"); });
}

void passesAfter(std::shared_ptr<temper_std::Test> const & test) {
  {
    auto atResult = test_panics::at(core::List::make<int32_t>(1, 2), 1);
    if (!atResult.has_value()) {
      core::uncaught_failure(atResult.error());
    }
    int32_t actual = *atResult;
    int32_t expected = 2;
    test->assert_(actual == expected, [=]() -> core::Str { return "expected at([1, 2], 1) == (" + std::to_string(expected) + ") not (" + std::to_string(actual) + ")"; });
  }
}

void diesAfterFailing(std::shared_ptr<temper_std::Test> const & test) {
  test->assert_(core::Int::add(1, 1) == 3, []() -> core::Str { return TEMPER_STR("failed before"); });
  core::panic();
}

}  // namespace test_panics

#endif  // TEMPER_TESTS

// main.cpp
#include "test-panics/init.hpp"
#include "temper-core/stack_trace.hpp"

int main() {
  temper::core::install_stack_traces();
  test_panics::global_init_init();
}

// test.cpp
#include "test-panics/init.hpp"
#include "temper-core/test_main.hpp"

int main() {
  temper::core::install_stack_traces();
  test_panics::global_init_init();
  temper_std::global_init_testing();
  return temper::core::run_tests({
      {"passes before", [] {
        auto test = temper_std::Test::make();
        test_panics::passesBefore(test);
        auto messages = test->messagesCombined();
        return temper::core::TestOutcome{
            test->get_passing(),
            temper::core::is_null(messages) ? std::string() : temper::core::Str(messages).str()};
      }},
      {"panics reading past the end", [] {
        auto test = temper_std::Test::make();
        test_panics::panicsReadingPastTheEnd(test);
        auto messages = test->messagesCombined();
        return temper::core::TestOutcome{
            test->get_passing(),
            temper::core::is_null(messages) ? std::string() : temper::core::Str(messages).str()};
      }},
      {"dies outright", [] {
        auto test = temper_std::Test::make();
        test_panics::diesOutright(test);
        auto messages = test->messagesCombined();
        return temper::core::TestOutcome{
            test->get_passing(),
            temper::core::is_null(messages) ? std::string() : temper::core::Str(messages).str()};
      }},
      {"fails an assertion", [] {
        auto test = temper_std::Test::make();
        test_panics::failsAnAssertion(test);
        auto messages = test->messagesCombined();
        return temper::core::TestOutcome{
            test->get_passing(),
            temper::core::is_null(messages) ? std::string() : temper::core::Str(messages).str()};
      }},
      {"passes after", [] {
        auto test = temper_std::Test::make();
        test_panics::passesAfter(test);
        auto messages = test->messagesCombined();
        return temper::core::TestOutcome{
            test->get_passing(),
            temper::core::is_null(messages) ? std::string() : temper::core::Str(messages).str()};
      }},
      {"dies after failing", [] {
        auto test = temper_std::Test::make();
        test_panics::diesAfterFailing(test);
        auto messages = test->messagesCombined();
        return temper::core::TestOutcome{
            test->get_passing(),
            temper::core::is_null(messages) ? std::string() : temper::core::Str(messages).str()};
      }},
  });
}

// CMakeLists.txt
# Generated by Temper.
cmake_minimum_required(VERSION 3.10)
project(test-panics LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 14)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)
find_package(Threads REQUIRED)

add_library(test-panics init.cpp)
target_include_directories(test-panics PUBLIC ${CMAKE_CURRENT_SOURCE_DIR}/..)
target_link_libraries(test-panics PUBLIC Threads::Threads)

# Stack traces for the programs, not the library (temper-core/stack_trace.hpp).
include(${CMAKE_CURRENT_SOURCE_DIR}/../temper-core/stack_trace.cmake)
add_executable(test-panics-main main.cpp)
target_link_libraries(test-panics-main PRIVATE test-panics)
temper_stack_traces(test-panics-main)

if(EXISTS ${CMAKE_CURRENT_SOURCE_DIR}/../std/CMakeLists.txt)
  if(NOT TARGET std)
    add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/../std ${CMAKE_CURRENT_BINARY_DIR}/std)
  endif()
  add_executable(test-panics-test test.cpp init.cpp)
  target_compile_definitions(test-panics-test PRIVATE TEMPER_TESTS)
  target_include_directories(test-panics-test PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/..)
  target_link_libraries(test-panics-test PRIVATE Threads::Threads std)
  temper_stack_traces(test-panics-test)
endif()

