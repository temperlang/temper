// init.hpp
#pragma once

#include <temper-core/core.hpp>
#ifdef TEMPER_TESTS
#include <std/testing.hpp>
#endif

namespace test_assert_panics {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();


#ifdef TEMPER_TESTS
// Tests, which `test.cpp` runs.
void passesBefore(std::shared_ptr<temper_std::Test> const & test);
void passesWhenTheBlockPanics(std::shared_ptr<temper_std::Test> const & test);
void passesPastAnOrelse(std::shared_ptr<temper_std::Test> const & test);
void failsWhenTheBlockReturns(std::shared_ptr<temper_std::Test> const & test);
void failsWithItsMessage(std::shared_ptr<temper_std::Test> const & test);
void failsWhenTheBlockBubbles(std::shared_ptr<temper_std::Test> const & test);
void failsWithItsMessageWhenTheBlockBubbles(std::shared_ptr<temper_std::Test> const & test);
void goesOnAfterFailing(std::shared_ptr<temper_std::Test> const & test);
void nests(std::shared_ptr<temper_std::Test> const & test);
void keepsWhatTheBlockChanged(std::shared_ptr<temper_std::Test> const & test);
void failsOnAPanicAfterTheBlock(std::shared_ptr<temper_std::Test> const & test);
void endsOnAFailInTheBlock(std::shared_ptr<temper_std::Test> const & test);
void passesAfter(std::shared_ptr<temper_std::Test> const & test);
#endif

}  // namespace test_assert_panics

// init.cpp
#include <test-assert-panics/init.hpp>

namespace test_assert_panics {

namespace core = temper::core;

#ifdef TEMPER_TESTS
static int32_t at(std::shared_ptr<std::vector<int32_t>> const & ls, int32_t i) {
  if (i == 99) {
    core::bubble();
  }
  return core::List::get<int32_t>(ls, i);
}
#endif  // TEMPER_TESTS

void global_init_init() {}

}  // namespace test_assert_panics

#ifdef TEMPER_TESTS

namespace test_assert_panics {

void passesBefore(std::shared_ptr<temper_std::Test> const & test) {
  {
    int32_t actual = test_assert_panics::at(core::List::make<int32_t>(1, 2), 1);
    int32_t expected = 2;
    test->assert_(actual == expected, [=]() -> core::Str { return "expected at([1, 2], 1) == (" + std::to_string(expected) + ") not (" + std::to_string(actual) + ")"; });
  }
}

void passesWhenTheBlockPanics(std::shared_ptr<temper_std::Test> const & test) {
  test->assertPanics([]() -> void { test_assert_panics::at(core::List::make<int32_t>(1, 2), 5); });
  test->assertPanics([]() -> void { core::panic(); });
}

void passesPastAnOrelse(std::shared_ptr<temper_std::Test> const & test) {
  auto fn = [=]() -> void {
    int32_t n;
    try {
      n = test_assert_panics::at(core::List::make<int32_t>(1, 2), 5);
    } catch (core::TemperBubble const &) {
      n = -1;
    }
    test->assert_(n == -1, []() -> core::Str { return TEMPER_STR("caught by an orelse"); });
  };
  test->assertPanics(fn);
}

void failsWhenTheBlockReturns(std::shared_ptr<temper_std::Test> const & test) {
  test->assertPanics([]() -> void { test_assert_panics::at(core::List::make<int32_t>(1, 2), 0); });
}

void failsWithItsMessage(std::shared_ptr<temper_std::Test> const & test) {
  test->assertPanics([]() -> void { test_assert_panics::at(core::List::make<int32_t>(1, 2), 1); }, TEMPER_STR("reading index 1 panics"));
}

void failsWhenTheBlockBubbles(std::shared_ptr<temper_std::Test> const & test) {
  test->assertPanics([]() -> void { test_assert_panics::at(core::List::make<int32_t>(1, 2), 99); });
}

void failsWithItsMessageWhenTheBlockBubbles(std::shared_ptr<temper_std::Test> const & test) {
  test->assertPanics([]() -> void { test_assert_panics::at(core::List::make<int32_t>(1, 2), 99); }, TEMPER_STR("reading index 99 panics"));
}

void goesOnAfterFailing(std::shared_ptr<temper_std::Test> const & test) {
  test->assertPanics([]() -> void { test_assert_panics::at(core::List::make<int32_t>(1, 2), 0); });
  test->assert_(false, []() -> core::Str { return TEMPER_STR("went on"); });
}

void nests(std::shared_ptr<temper_std::Test> const & test) {
  auto fn = [=]() -> void {
    test->assertPanics([]() -> void { core::panic(); });
    test->assertPanics([]() -> void { test_assert_panics::at(core::List::make<int32_t>(1, 2), 0); }, TEMPER_STR("inner returns"));
    core::panic();
  };
  test->assertPanics(fn);
}

void keepsWhatTheBlockChanged(std::shared_ptr<temper_std::Test> const & test) {
  std::shared_ptr<int32_t> n = std::make_shared<int32_t>(0);
  auto fn = [=]() -> void {
    *n = core::Int::add((*n), 1);
    core::panic();
  };
  test->assertPanics(fn);
  test->assert_((*n) == 1, []() -> core::Str { return TEMPER_STR("lost the change"); });
}

void failsOnAPanicAfterTheBlock(std::shared_ptr<temper_std::Test> const & test) {
  test->assertPanics([]() -> void { core::panic(); });
  test_assert_panics::at(core::List::make<int32_t>(1, 2), 7);
}

void endsOnAFailInTheBlock(std::shared_ptr<temper_std::Test> const & test) {
  auto fn = [=]() -> void {
    test->fail(TEMPER_STR("failed inside"));
    core::panic();
  };
  test->assertPanics(fn);
  test->fail(TEMPER_STR("swallowed"));
  core::panic();
}

void passesAfter(std::shared_ptr<temper_std::Test> const & test) {
  {
    int32_t actual = test_assert_panics::at(core::List::make<int32_t>(1, 2), 0);
    int32_t expected = 1;
    test->assert_(actual == expected, [=]() -> core::Str { return "expected at([1, 2], 0) == (" + std::to_string(expected) + ") not (" + std::to_string(actual) + ")"; });
  }
}

}  // namespace test_assert_panics

#endif  // TEMPER_TESTS

// main.cpp
#include "test-assert-panics/init.hpp"

int main() {
  test_assert_panics::global_init_init();
}

// test.cpp
#include "test-assert-panics/init.hpp"
#include "temper-core/test_main.hpp"

int main() {
  test_assert_panics::global_init_init();
  temper_std::global_init_testing();
  return temper::core::run_tests({
      {"passes before", [] {
        auto test = temper_std::Test::make();
        test_assert_panics::passesBefore(test);
        auto messages = test->messagesCombined();
        return temper::core::TestOutcome{
            test->get_passing(),
            temper::core::is_null(messages) ? std::string() : temper::core::Str(messages).str()};
      }},
      {"passes when the block panics", [] {
        auto test = temper_std::Test::make();
        test_assert_panics::passesWhenTheBlockPanics(test);
        auto messages = test->messagesCombined();
        return temper::core::TestOutcome{
            test->get_passing(),
            temper::core::is_null(messages) ? std::string() : temper::core::Str(messages).str()};
      }},
      {"passes past an orelse", [] {
        auto test = temper_std::Test::make();
        test_assert_panics::passesPastAnOrelse(test);
        auto messages = test->messagesCombined();
        return temper::core::TestOutcome{
            test->get_passing(),
            temper::core::is_null(messages) ? std::string() : temper::core::Str(messages).str()};
      }},
      {"fails when the block returns", [] {
        auto test = temper_std::Test::make();
        test_assert_panics::failsWhenTheBlockReturns(test);
        auto messages = test->messagesCombined();
        return temper::core::TestOutcome{
            test->get_passing(),
            temper::core::is_null(messages) ? std::string() : temper::core::Str(messages).str()};
      }},
      {"fails with its message", [] {
        auto test = temper_std::Test::make();
        test_assert_panics::failsWithItsMessage(test);
        auto messages = test->messagesCombined();
        return temper::core::TestOutcome{
            test->get_passing(),
            temper::core::is_null(messages) ? std::string() : temper::core::Str(messages).str()};
      }},
      {"fails when the block bubbles", [] {
        auto test = temper_std::Test::make();
        test_assert_panics::failsWhenTheBlockBubbles(test);
        auto messages = test->messagesCombined();
        return temper::core::TestOutcome{
            test->get_passing(),
            temper::core::is_null(messages) ? std::string() : temper::core::Str(messages).str()};
      }},
      {"fails with its message when the block bubbles", [] {
        auto test = temper_std::Test::make();
        test_assert_panics::failsWithItsMessageWhenTheBlockBubbles(test);
        auto messages = test->messagesCombined();
        return temper::core::TestOutcome{
            test->get_passing(),
            temper::core::is_null(messages) ? std::string() : temper::core::Str(messages).str()};
      }},
      {"goes on after failing", [] {
        auto test = temper_std::Test::make();
        test_assert_panics::goesOnAfterFailing(test);
        auto messages = test->messagesCombined();
        return temper::core::TestOutcome{
            test->get_passing(),
            temper::core::is_null(messages) ? std::string() : temper::core::Str(messages).str()};
      }},
      {"nests", [] {
        auto test = temper_std::Test::make();
        test_assert_panics::nests(test);
        auto messages = test->messagesCombined();
        return temper::core::TestOutcome{
            test->get_passing(),
            temper::core::is_null(messages) ? std::string() : temper::core::Str(messages).str()};
      }},
      {"keeps what the block changed", [] {
        auto test = temper_std::Test::make();
        test_assert_panics::keepsWhatTheBlockChanged(test);
        auto messages = test->messagesCombined();
        return temper::core::TestOutcome{
            test->get_passing(),
            temper::core::is_null(messages) ? std::string() : temper::core::Str(messages).str()};
      }},
      {"fails on a panic after the block", [] {
        auto test = temper_std::Test::make();
        test_assert_panics::failsOnAPanicAfterTheBlock(test);
        auto messages = test->messagesCombined();
        return temper::core::TestOutcome{
            test->get_passing(),
            temper::core::is_null(messages) ? std::string() : temper::core::Str(messages).str()};
      }},
      {"ends on a fail in the block", [] {
        auto test = temper_std::Test::make();
        test_assert_panics::endsOnAFailInTheBlock(test);
        auto messages = test->messagesCombined();
        return temper::core::TestOutcome{
            test->get_passing(),
            temper::core::is_null(messages) ? std::string() : temper::core::Str(messages).str()};
      }},
      {"passes after", [] {
        auto test = temper_std::Test::make();
        test_assert_panics::passesAfter(test);
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
project(test-assert-panics LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 14)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)
find_package(Threads REQUIRED)

add_library(test-assert-panics init.cpp)
target_include_directories(test-assert-panics PUBLIC ${CMAKE_CURRENT_SOURCE_DIR}/..)
target_link_libraries(test-assert-panics PUBLIC Threads::Threads)

add_executable(test-assert-panics-main main.cpp)
target_link_libraries(test-assert-panics-main PRIVATE test-assert-panics)

if(EXISTS ${CMAKE_CURRENT_SOURCE_DIR}/../std/CMakeLists.txt)
  if(NOT TARGET std)
    add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/../std ${CMAKE_CURRENT_BINARY_DIR}/std)
  endif()
  add_executable(test-assert-panics-test test.cpp init.cpp)
  target_compile_definitions(test-assert-panics-test PRIVATE TEMPER_TESTS)
  target_include_directories(test-assert-panics-test PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/..)
  target_link_libraries(test-assert-panics-test PRIVATE Threads::Threads std)
endif()

