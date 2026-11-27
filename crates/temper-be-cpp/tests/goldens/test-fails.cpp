// init.hpp
#pragma once

#include <temper-core/core.hpp>
#ifdef TEMPER_TESTS
#include <std/testing.hpp>
#endif

namespace test_fails {

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
void failsPastAnOrelse(std::shared_ptr<temper_std::Test> const & test);
void narrowsAfterFail(std::shared_ptr<temper_std::Test> const & test);
void failsBeforeNarrowing(std::shared_ptr<temper_std::Test> const & test);
void collectsSoftFailures(std::shared_ptr<temper_std::Test> const & test);
void failsHardAfterSoftFailures(std::shared_ptr<temper_std::Test> const & test);
void failsAHardAssertionPastAnOrelse(std::shared_ptr<temper_std::Test> const & test);
void hardensSoftFailuresPastAnOrelse(std::shared_ptr<temper_std::Test> const & test);
void passesAfter(std::shared_ptr<temper_std::Test> const & test);
#endif

}  // namespace test_fails

// init.cpp
#include <test-fails/init.hpp>

namespace test_fails {

namespace core = temper::core;

#ifdef TEMPER_TESTS
std::shared_ptr<Oops> Oops::make() {
  std::shared_ptr<Oops> self = std::make_shared<Oops>();
  return self;
}

core::Str Oops::get_message() const {
  return TEMPER_STR("oops");
}

static bool tries(std::function<core::Expected<void, std::shared_ptr<Oops>>()> const & f) {
  bool finished = true;
  auto result = f();
  if (!result.has_value()) {
    finished = false;
  }
  return finished;
}

static core::NullableParam<int32_t> find(core::Str const & key) {
  if (key == TEMPER_STR("one")) {
    return 1;
  } else {
    return nullptr;
  }
}
#endif  // TEMPER_TESTS

void global_init_init() {}

}  // namespace test_fails

#ifdef TEMPER_TESTS

namespace test_fails {

void passesBefore(std::shared_ptr<temper_std::Test> const & test) {
  auto fn = [=]() -> core::Expected<void, std::shared_ptr<Oops>> {
    {
      core::NullableParam<int32_t> actual = test_fails::find(TEMPER_STR("one"));
      int32_t expected = 1;
      test->assert_(actual == expected, [=]() -> core::Str { return "expected find(\"one\") == (" + std::to_string(expected) + ") not (" + (actual == nullptr ? TEMPER_STR("null") : core::Str(std::to_string(actual.value))) + ")"; });
    }
    return {};
  };
  test->assert_(test_fails::tries(fn), []() -> core::Str { return TEMPER_STR("expected tries { assert(find(\"one\") == 1); }"); });
}

void failsPastAnOrelse(std::shared_ptr<temper_std::Test> const & test) {
  auto fn = [=]() -> core::Expected<void, std::shared_ptr<Oops>> {
    test->fail(TEMPER_STR("failed on purpose"));
    core::panic();
  };
  test_fails::tries(fn);
  test->fail(TEMPER_STR("swallowed"));
  core::panic();
}

void narrowsAfterFail(std::shared_ptr<temper_std::Test> const & test) {
  core::NullableParam<int32_t> n = test_fails::find(TEMPER_STR("one"));
  if (n == nullptr) {
    test->fail(TEMPER_STR("one not found"));
    core::panic();
  }
  {
    int32_t actual = core::Int::add(n.value, 1);
    int32_t expected = 2;
    test->assert_(actual == expected, [=]() -> core::Str { return "expected n + 1 == (" + std::to_string(expected) + ") not (" + std::to_string(actual) + ")"; });
  }
}

void failsBeforeNarrowing(std::shared_ptr<temper_std::Test> const & test) {
  core::NullableParam<int32_t> n = test_fails::find(TEMPER_STR("two"));
  if (n == nullptr) {
    test->fail(TEMPER_STR("two not found"));
    core::panic();
  }
  test->assert_(core::Int::add(n.value, 1) == 3, []() -> core::Str { return TEMPER_STR("went on"); });
}

void collectsSoftFailures(std::shared_ptr<temper_std::Test> const & test) {
  test->assert_(core::Int::add(1, 1) == 3, []() -> core::Str { return TEMPER_STR("first"); });
  test->assert_(true, []() -> core::Str { return TEMPER_STR("not shown"); });
  test->assert_(core::Int::add(2, 2) == 5, []() -> core::Str { return TEMPER_STR("second"); });
}

void failsHardAfterSoftFailures(std::shared_ptr<temper_std::Test> const & test) {
  test->assert_(false, []() -> core::Str { return TEMPER_STR("soft before"); });
  test->fail(TEMPER_STR("then hard"));
  core::panic();
}

void failsAHardAssertionPastAnOrelse(std::shared_ptr<temper_std::Test> const & test) {
  auto fn = [=]() -> core::Expected<void, std::shared_ptr<Oops>> {
    test->assertHard(core::Int::add(1, 1) == 3, []() -> core::Str { return TEMPER_STR("hard assertion"); });
    return {};
  };
  test_fails::tries(fn);
  test->fail(TEMPER_STR("swallowed"));
  core::panic();
}

void hardensSoftFailuresPastAnOrelse(std::shared_ptr<temper_std::Test> const & test) {
  test->assert_(false, []() -> core::Str { return TEMPER_STR("soft only"); });
  auto fn2 = [=]() -> core::Expected<void, std::shared_ptr<Oops>> {
    test->softFailToHard();
    return {};
  };
  test_fails::tries(fn2);
  test->fail(TEMPER_STR("swallowed"));
  core::panic();
}

void passesAfter(std::shared_ptr<temper_std::Test> const & test) {
  {
    core::NullableParam<int32_t> actual = test_fails::find(TEMPER_STR("one"));
    int32_t expected = 1;
    test->assert_(actual == expected, [=]() -> core::Str { return "expected find(\"one\") == (" + std::to_string(expected) + ") not (" + (actual == nullptr ? TEMPER_STR("null") : core::Str(std::to_string(actual.value))) + ")"; });
  }
}

}  // namespace test_fails

#endif  // TEMPER_TESTS

// main.cpp
#include "test-fails/init.hpp"
#include "temper-core/stack_trace.hpp"

int main() {
  temper::core::install_stack_traces();
  test_fails::global_init_init();
}

// test.cpp
#include "test-fails/init.hpp"
#include "temper-core/test_main.hpp"

int main() {
  temper::core::install_stack_traces();
  test_fails::global_init_init();
  temper_std::global_init_testing();
  return temper::core::run_tests({
      {"passes before", [] {
        auto test = temper_std::Test::make();
        test_fails::passesBefore(test);
        auto messages = test->messagesCombined();
        return temper::core::TestOutcome{
            test->get_passing(),
            temper::core::is_null(messages) ? std::string() : temper::core::Str(messages).str()};
      }},
      {"fails past an orelse", [] {
        auto test = temper_std::Test::make();
        test_fails::failsPastAnOrelse(test);
        auto messages = test->messagesCombined();
        return temper::core::TestOutcome{
            test->get_passing(),
            temper::core::is_null(messages) ? std::string() : temper::core::Str(messages).str()};
      }},
      {"narrows after fail", [] {
        auto test = temper_std::Test::make();
        test_fails::narrowsAfterFail(test);
        auto messages = test->messagesCombined();
        return temper::core::TestOutcome{
            test->get_passing(),
            temper::core::is_null(messages) ? std::string() : temper::core::Str(messages).str()};
      }},
      {"fails before narrowing", [] {
        auto test = temper_std::Test::make();
        test_fails::failsBeforeNarrowing(test);
        auto messages = test->messagesCombined();
        return temper::core::TestOutcome{
            test->get_passing(),
            temper::core::is_null(messages) ? std::string() : temper::core::Str(messages).str()};
      }},
      {"collects soft failures", [] {
        auto test = temper_std::Test::make();
        test_fails::collectsSoftFailures(test);
        auto messages = test->messagesCombined();
        return temper::core::TestOutcome{
            test->get_passing(),
            temper::core::is_null(messages) ? std::string() : temper::core::Str(messages).str()};
      }},
      {"fails hard after soft failures", [] {
        auto test = temper_std::Test::make();
        test_fails::failsHardAfterSoftFailures(test);
        auto messages = test->messagesCombined();
        return temper::core::TestOutcome{
            test->get_passing(),
            temper::core::is_null(messages) ? std::string() : temper::core::Str(messages).str()};
      }},
      {"fails a hard assertion past an orelse", [] {
        auto test = temper_std::Test::make();
        test_fails::failsAHardAssertionPastAnOrelse(test);
        auto messages = test->messagesCombined();
        return temper::core::TestOutcome{
            test->get_passing(),
            temper::core::is_null(messages) ? std::string() : temper::core::Str(messages).str()};
      }},
      {"hardens soft failures past an orelse", [] {
        auto test = temper_std::Test::make();
        test_fails::hardensSoftFailuresPastAnOrelse(test);
        auto messages = test->messagesCombined();
        return temper::core::TestOutcome{
            test->get_passing(),
            temper::core::is_null(messages) ? std::string() : temper::core::Str(messages).str()};
      }},
      {"passes after", [] {
        auto test = temper_std::Test::make();
        test_fails::passesAfter(test);
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
project(test-fails LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 14)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)
find_package(Threads REQUIRED)

add_library(test-fails init.cpp)
target_include_directories(test-fails PUBLIC ${CMAKE_CURRENT_SOURCE_DIR}/..)
target_link_libraries(test-fails PUBLIC Threads::Threads)

# Stack traces for the programs, not the library (temper-core/stack_trace.hpp).
include(${CMAKE_CURRENT_SOURCE_DIR}/../temper-core/stack_trace.cmake)
add_executable(test-fails-main main.cpp)
target_link_libraries(test-fails-main PRIVATE test-fails)
temper_stack_traces(test-fails-main)

if(EXISTS ${CMAKE_CURRENT_SOURCE_DIR}/../std/CMakeLists.txt)
  if(NOT TARGET std)
    add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/../std ${CMAKE_CURRENT_BINARY_DIR}/std)
  endif()
  add_executable(test-fails-test test.cpp init.cpp)
  target_compile_definitions(test-fails-test PRIVATE TEMPER_TESTS)
  target_include_directories(test-fails-test PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/..)
  target_link_libraries(test-fails-test PRIVATE Threads::Threads std)
  temper_stack_traces(test-fails-test)
endif()

