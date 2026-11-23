// init.hpp
#pragma once

#include <temper-core/core.hpp>
#ifdef TEMPER_TESTS
#include <std/testing.hpp>
#endif

namespace test_async {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();


#ifdef TEMPER_TESTS
// Tests, which `test.cpp` runs.
void failsInAnAsyncBlock(std::shared_ptr<temper_std::Test> const & test);
void passesInAnAsyncBlock(std::shared_ptr<temper_std::Test> const & test);
void failsInALaterTurn(std::shared_ptr<temper_std::Test> const & test);
void passesAfterALaterTurn(std::shared_ptr<temper_std::Test> const & test);
void failsHardInAnAsyncBlock(std::shared_ptr<temper_std::Test> const & test);
void diesInAnAsyncBlock(std::shared_ptr<temper_std::Test> const &);
void passesAfter(std::shared_ptr<temper_std::Test> const & test);
void waitsOnNothing(std::shared_ptr<temper_std::Test> const & test);
void ownFailureWins(std::shared_ptr<temper_std::Test> const & test);
#endif

}  // namespace test_async

// init.cpp
#include <test-async/init.hpp>

namespace test_async {

namespace core = temper::core;

void global_init_init() {}

}  // namespace test_async

#ifdef TEMPER_TESTS

namespace test_async {

void failsInAnAsyncBlock(std::shared_ptr<temper_std::Test> const & test) {
  auto fn = [=]() -> std::shared_ptr<core::SafeGenerator<std::shared_ptr<core::Empty>>> {
    std::shared_ptr<int32_t> caseIndex = std::make_shared<int32_t>(0);
    auto convertedCoroutine = [=](std::shared_ptr<core::SafeGenerator<std::shared_ptr<core::Empty>>>) -> std::shared_ptr<core::GeneratorResult<std::shared_ptr<core::Empty>>> {
      int32_t caseIndexLocal = (*caseIndex);
      *caseIndex = -1;
      switch (caseIndexLocal) {
        case 0:
          test->assert_(core::Int::add(1, 1) == 3, []() -> core::Str { return TEMPER_STR("failed in async"); });
          return core::doneResult();
        default:
          return core::doneResult();
      }
    };
    return core::safe_adapt_generator_fn<std::shared_ptr<core::Empty>>(convertedCoroutine);
  };
  core::async_run(fn);
}

void passesInAnAsyncBlock(std::shared_ptr<temper_std::Test> const & test) {
  auto fn = [=]() -> std::shared_ptr<core::SafeGenerator<std::shared_ptr<core::Empty>>> {
    std::shared_ptr<int32_t> caseIndex = std::make_shared<int32_t>(0);
    auto convertedCoroutine = [=](std::shared_ptr<core::SafeGenerator<std::shared_ptr<core::Empty>>>) -> std::shared_ptr<core::GeneratorResult<std::shared_ptr<core::Empty>>> {
      int32_t caseIndexLocal = (*caseIndex);
      *caseIndex = -1;
      switch (caseIndexLocal) {
        case 0:
          test->assert_(core::Int::add(1, 1) == 2, []() -> core::Str { return TEMPER_STR("not shown"); });
          return core::doneResult();
        default:
          return core::doneResult();
      }
    };
    return core::safe_adapt_generator_fn<std::shared_ptr<core::Empty>>(convertedCoroutine);
  };
  core::async_run(fn);
}

void failsInALaterTurn(std::shared_ptr<temper_std::Test> const & test) {
  std::shared_ptr<core::PromiseBuilder<int32_t>> pb = core::PromiseBuilderNs::make<int32_t>();
  auto fn = [=]() -> std::shared_ptr<core::SafeGenerator<std::shared_ptr<core::Empty>>> {
    std::shared_ptr<int32_t> caseIndex = std::make_shared<int32_t>(0);
    std::shared_ptr<int32_t> n = std::make_shared<int32_t>(0);
    std::shared_ptr<std::shared_ptr<core::Promise<int32_t>>> awaited = std::make_shared<std::shared_ptr<core::Promise<int32_t>>>(nullptr);
    auto convertedCoroutine = [=](std::shared_ptr<core::SafeGenerator<std::shared_ptr<core::Empty>>> generator) -> std::shared_ptr<core::GeneratorResult<std::shared_ptr<core::Empty>>> {
      while (true) {
        int32_t caseIndexLocal = (*caseIndex);
        *caseIndex = -1;
        switch (caseIndexLocal) {
          case 0:
            try {
              *awaited = core::getpromise(pb);
              *caseIndex = 3;
              core::awake_upon((*awaited), generator);
              return core::ValueResult<std::shared_ptr<core::Empty>>::make(core::empty());
            } catch (core::TemperBubble const &) {
              *caseIndex = 1;
            }
            break;
          case 1:
            *n = -1;
            *caseIndex = 2;
            break;
          case 2:
            test->assert_((*n) == 2, [=]() -> core::Str { return "later turn got " + std::to_string((*n)); });
            return core::doneResult();
          case 3:
            try {
              *n = core::get_promise_result_sync((*awaited));
              *caseIndex = 2;
            } catch (core::TemperBubble const &) {
              *caseIndex = 1;
            }
            break;
          default:
            return core::doneResult();
        }
      }
    };
    return core::safe_adapt_generator_fn<std::shared_ptr<core::Empty>>(convertedCoroutine);
  };
  core::async_run(fn);
  auto fn3 = [=]() -> std::shared_ptr<core::SafeGenerator<std::shared_ptr<core::Empty>>> {
    std::shared_ptr<int32_t> caseIndex = std::make_shared<int32_t>(0);
    auto convertedCoroutine = [=](std::shared_ptr<core::SafeGenerator<std::shared_ptr<core::Empty>>>) -> std::shared_ptr<core::GeneratorResult<std::shared_ptr<core::Empty>>> {
      int32_t caseIndexLocal = (*caseIndex);
      *caseIndex = -1;
      switch (caseIndexLocal) {
        case 0:
          core::complete(pb, 1);
          return core::doneResult();
        default:
          return core::doneResult();
      }
    };
    return core::safe_adapt_generator_fn<std::shared_ptr<core::Empty>>(convertedCoroutine);
  };
  core::async_run(fn3);
}

void passesAfterALaterTurn(std::shared_ptr<temper_std::Test> const & test) {
  {
    int32_t actual = core::Int::add(1, 1);
    int32_t expected = 2;
    test->assert_(actual == expected, [=]() -> core::Str { return "expected 1 + 1 == (" + std::to_string(expected) + ") not (" + std::to_string(actual) + ")"; });
  }
}

void failsHardInAnAsyncBlock(std::shared_ptr<temper_std::Test> const & test) {
  auto fn = [=]() -> std::shared_ptr<core::SafeGenerator<std::shared_ptr<core::Empty>>> {
    std::shared_ptr<int32_t> caseIndex = std::make_shared<int32_t>(0);
    auto convertedCoroutine = [=](std::shared_ptr<core::SafeGenerator<std::shared_ptr<core::Empty>>>) -> std::shared_ptr<core::GeneratorResult<std::shared_ptr<core::Empty>>> {
      int32_t caseIndexLocal = (*caseIndex);
      *caseIndex = -1;
      switch (caseIndexLocal) {
        case 0:
          test->fail(TEMPER_STR("failed hard in async"));
          core::panic();
        default:
          return core::doneResult();
      }
    };
    return core::safe_adapt_generator_fn<std::shared_ptr<core::Empty>>(convertedCoroutine);
  };
  core::async_run(fn);
}

void diesInAnAsyncBlock(std::shared_ptr<temper_std::Test> const &) {
  auto fn = []() -> std::shared_ptr<core::SafeGenerator<std::shared_ptr<core::Empty>>> {
    std::shared_ptr<int32_t> caseIndex = std::make_shared<int32_t>(0);
    auto convertedCoroutine = [=](std::shared_ptr<core::SafeGenerator<std::shared_ptr<core::Empty>>>) -> std::shared_ptr<core::GeneratorResult<std::shared_ptr<core::Empty>>> {
      int32_t caseIndexLocal = (*caseIndex);
      *caseIndex = -1;
      switch (caseIndexLocal) {
        case 0:
          core::panic();
        default:
          return core::doneResult();
      }
    };
    return core::safe_adapt_generator_fn<std::shared_ptr<core::Empty>>(convertedCoroutine);
  };
  core::async_run(fn);
}

void passesAfter(std::shared_ptr<temper_std::Test> const & test) {
  {
    int32_t actual = core::Int::add(1, 1);
    int32_t expected = 2;
    test->assert_(actual == expected, [=]() -> core::Str { return "expected 1 + 1 == (" + std::to_string(expected) + ") not (" + std::to_string(actual) + ")"; });
  }
}

void waitsOnNothing(std::shared_ptr<temper_std::Test> const & test) {
  std::shared_ptr<core::PromiseBuilder<int32_t>> never = core::PromiseBuilderNs::make<int32_t>();
  auto fn = [=]() -> std::shared_ptr<core::SafeGenerator<std::shared_ptr<core::Empty>>> {
    std::shared_ptr<int32_t> caseIndex = std::make_shared<int32_t>(0);
    std::shared_ptr<int32_t> n = std::make_shared<int32_t>(0);
    std::shared_ptr<std::shared_ptr<core::Promise<int32_t>>> awaited = std::make_shared<std::shared_ptr<core::Promise<int32_t>>>(nullptr);
    auto convertedCoroutine = [=](std::shared_ptr<core::SafeGenerator<std::shared_ptr<core::Empty>>> generator) -> std::shared_ptr<core::GeneratorResult<std::shared_ptr<core::Empty>>> {
      while (true) {
        int32_t caseIndexLocal = (*caseIndex);
        *caseIndex = -1;
        switch (caseIndexLocal) {
          case 0:
            try {
              *awaited = core::getpromise(never);
              *caseIndex = 3;
              core::awake_upon((*awaited), generator);
              return core::ValueResult<std::shared_ptr<core::Empty>>::make(core::empty());
            } catch (core::TemperBubble const &) {
              *caseIndex = 1;
            }
            break;
          case 1:
            *n = -1;
            *caseIndex = 2;
            break;
          case 2:
            test->assert_(false, [=]() -> core::Str { return "ran " + std::to_string((*n)); });
            return core::doneResult();
          case 3:
            try {
              *n = core::get_promise_result_sync((*awaited));
              *caseIndex = 2;
            } catch (core::TemperBubble const &) {
              *caseIndex = 1;
            }
            break;
          default:
            return core::doneResult();
        }
      }
    };
    return core::safe_adapt_generator_fn<std::shared_ptr<core::Empty>>(convertedCoroutine);
  };
  core::async_run(fn);
}

void ownFailureWins(std::shared_ptr<temper_std::Test> const & test) {
  test->assert_(false, []() -> core::Str { return TEMPER_STR("soft failure"); });
  auto fn2 = [=]() -> std::shared_ptr<core::SafeGenerator<std::shared_ptr<core::Empty>>> {
    std::shared_ptr<int32_t> caseIndex = std::make_shared<int32_t>(0);
    auto convertedCoroutine = [=](std::shared_ptr<core::SafeGenerator<std::shared_ptr<core::Empty>>>) -> std::shared_ptr<core::GeneratorResult<std::shared_ptr<core::Empty>>> {
      int32_t caseIndexLocal = (*caseIndex);
      *caseIndex = -1;
      switch (caseIndexLocal) {
        case 0:
          test->fail(TEMPER_STR("async failure"));
          core::panic();
        default:
          return core::doneResult();
      }
    };
    return core::safe_adapt_generator_fn<std::shared_ptr<core::Empty>>(convertedCoroutine);
  };
  core::async_run(fn2);
  std::shared_ptr<std::vector<int32_t>> ls = core::List::make<int32_t>(1);
  {
    int32_t actual = core::List::get<int32_t>(ls, 5);
    int32_t expected = 1;
    test->assert_(actual == expected, [=]() -> core::Str { return "expected ls[5] == (" + std::to_string(expected) + ") not (" + std::to_string(actual) + ")"; });
  }
}

}  // namespace test_async

#endif  // TEMPER_TESTS

// main.cpp
#include "test-async/init.hpp"
#include "temper-core/stack_trace.hpp"

int main() {
  temper::core::install_stack_traces();
  test_async::global_init_init();
}

// test.cpp
#include "test-async/init.hpp"
#include "temper-core/test_main.hpp"

int main() {
  temper::core::install_stack_traces();
  test_async::global_init_init();
  temper_std::global_init_testing();
  return temper::core::run_tests({
      {"fails in an async block", [] {
        auto test = temper_std::Test::make();
        test_async::failsInAnAsyncBlock(test);
        auto messages = test->messagesCombined();
        return temper::core::TestOutcome{
            test->get_passing(),
            temper::core::is_null(messages) ? std::string() : temper::core::Str(messages).str()};
      }},
      {"passes in an async block", [] {
        auto test = temper_std::Test::make();
        test_async::passesInAnAsyncBlock(test);
        auto messages = test->messagesCombined();
        return temper::core::TestOutcome{
            test->get_passing(),
            temper::core::is_null(messages) ? std::string() : temper::core::Str(messages).str()};
      }},
      {"fails in a later turn", [] {
        auto test = temper_std::Test::make();
        test_async::failsInALaterTurn(test);
        auto messages = test->messagesCombined();
        return temper::core::TestOutcome{
            test->get_passing(),
            temper::core::is_null(messages) ? std::string() : temper::core::Str(messages).str()};
      }},
      {"passes after a later turn", [] {
        auto test = temper_std::Test::make();
        test_async::passesAfterALaterTurn(test);
        auto messages = test->messagesCombined();
        return temper::core::TestOutcome{
            test->get_passing(),
            temper::core::is_null(messages) ? std::string() : temper::core::Str(messages).str()};
      }},
      {"fails hard in an async block", [] {
        auto test = temper_std::Test::make();
        test_async::failsHardInAnAsyncBlock(test);
        auto messages = test->messagesCombined();
        return temper::core::TestOutcome{
            test->get_passing(),
            temper::core::is_null(messages) ? std::string() : temper::core::Str(messages).str()};
      }},
      {"dies in an async block", [] {
        auto test = temper_std::Test::make();
        test_async::diesInAnAsyncBlock(test);
        auto messages = test->messagesCombined();
        return temper::core::TestOutcome{
            test->get_passing(),
            temper::core::is_null(messages) ? std::string() : temper::core::Str(messages).str()};
      }},
      {"passes after", [] {
        auto test = temper_std::Test::make();
        test_async::passesAfter(test);
        auto messages = test->messagesCombined();
        return temper::core::TestOutcome{
            test->get_passing(),
            temper::core::is_null(messages) ? std::string() : temper::core::Str(messages).str()};
      }},
      {"waits on nothing", [] {
        auto test = temper_std::Test::make();
        test_async::waitsOnNothing(test);
        auto messages = test->messagesCombined();
        return temper::core::TestOutcome{
            test->get_passing(),
            temper::core::is_null(messages) ? std::string() : temper::core::Str(messages).str()};
      }},
      {"own failure wins", [] {
        auto test = temper_std::Test::make();
        test_async::ownFailureWins(test);
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
project(test-async LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 14)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)
find_package(Threads REQUIRED)

add_library(test-async init.cpp)
target_include_directories(test-async PUBLIC ${CMAKE_CURRENT_SOURCE_DIR}/..)
target_link_libraries(test-async PUBLIC Threads::Threads)

# Stack traces for the programs, not the library (temper-core/stack_trace.hpp).
include(${CMAKE_CURRENT_SOURCE_DIR}/../temper-core/stack_trace.cmake)
add_executable(test-async-main main.cpp)
target_link_libraries(test-async-main PRIVATE test-async)
temper_stack_traces(test-async-main)

if(EXISTS ${CMAKE_CURRENT_SOURCE_DIR}/../std/CMakeLists.txt)
  if(NOT TARGET std)
    add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/../std ${CMAKE_CURRENT_BINARY_DIR}/std)
  endif()
  add_executable(test-async-test test.cpp init.cpp)
  target_compile_definitions(test-async-test PRIVATE TEMPER_TESTS)
  target_include_directories(test-async-test PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/..)
  target_link_libraries(test-async-test PRIVATE Threads::Threads std)
  temper_stack_traces(test-async-test)
endif()

