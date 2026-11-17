// init.hpp
#pragma once

#include <temper-core/core.hpp>
#include <std/testing.hpp>

namespace asserts {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

struct Vec2;
struct TestHelper;
struct KeptForHost;

struct Vec2 : public std::enable_shared_from_this<Vec2> {
  double x;
  double y;
  static std::shared_ptr<Vec2> make(double x, double y);
  temper::core::Str toString();
  double get_x() const;
  double get_y() const;
};

struct TestHelper : public std::enable_shared_from_this<TestHelper> {
  int32_t i;
  int32_t j;
  static std::shared_ptr<TestHelper> make(int32_t i, int32_t j);
  int32_t get_i() const;
  int32_t get_j() const;
};

struct KeptForHost : public std::enable_shared_from_this<KeptForHost> {
  int32_t n;
  static std::shared_ptr<KeptForHost> make(int32_t n);
  int32_t get_n() const;
};


#ifdef TEMPER_TESTS
// For tests' host code (`@keepTest`).
int32_t keptForTests(std::shared_ptr<TestHelper> const & helper);
// Tests, which `test.cpp` runs.
void aTestCase(std::shared_ptr<temper_std::Test> const & test);
void aFailingTest(std::shared_ptr<temper_std::Test> const & test);
void exported(std::shared_ptr<temper_std::Test> const & test);
void moduleValues(std::shared_ptr<temper_std::Test> const & test);
#endif

}  // namespace asserts

// init.cpp
#include <asserts/init.hpp>

namespace asserts {

namespace core = temper::core;

namespace temper_globals {

static core::GlobalSlot<std::shared_ptr<std::vector<std::shared_ptr<Vec2>>>> unitVecs;

}  // namespace temper_globals

static double normSquared(std::shared_ptr<Vec2> const & vec);

std::shared_ptr<Vec2> Vec2::make(double x, double y) {
  global_init_init();
  std::shared_ptr<Vec2> self = std::make_shared<Vec2>();
  self->x = x;
  self->y = y;
  return self;
}

core::Str Vec2::toString() {
  std::shared_ptr<Vec2> self = shared_from_this();
  return "(" + core::Float64::toString(self->x) + ", " + core::Float64::toString(self->y) + ") size squared " + core::Float64::toString(asserts::normSquared(self));
}

double Vec2::get_x() const {
  return this->x;
}

double Vec2::get_y() const {
  return this->y;
}

static double normSquared(std::shared_ptr<Vec2> const & vec) {
  return vec->x * vec->x + vec->y * vec->y;
}

std::shared_ptr<TestHelper> TestHelper::make(int32_t i, int32_t j) {
  std::shared_ptr<TestHelper> self = std::make_shared<TestHelper>();
  self->i = i;
  self->j = j;
  return self;
}

int32_t TestHelper::get_i() const {
  return this->i;
}

int32_t TestHelper::get_j() const {
  return this->j;
}

#ifdef TEMPER_TESTS
static void assertMoreThings(std::shared_ptr<temper_std::Test> const & t, std::shared_ptr<TestHelper> const & helper, std::shared_ptr<std::vector<std::shared_ptr<std::vector<core::Str>>>> const & strings) {
  {
    int32_t i = 0;
    while (i < core::List::length<std::shared_ptr<std::vector<core::Str>>>(strings)) {
      {
        int32_t j = 0;
        while (j < core::List::length<core::Str>(core::List::get<std::shared_ptr<std::vector<core::Str>>>(strings, i))) {
          {
            int32_t actual = helper->i;
            int32_t expected = helper->j;
            t->assert_(actual == expected, [=]() -> core::Str { return "expected helper.i == (" + std::to_string(expected) + ") not (" + std::to_string(actual) + ")"; });
          }
          core::Str s = core::List::get<core::Str>(core::List::get<std::shared_ptr<std::vector<core::Str>>>(strings, i), j);
          {
            core::Str actual = s;
            core::Str expected = TEMPER_STR("hi");
            t->assert_(actual == expected, [=]() -> core::Str { return "expected s == (" + expected + ") not (" + actual + ")"; });
          }
          j = core::Int::add(j, 1);
        }
      }
      i = core::Int::add(i, 1);
    }
  }
}
#endif  // TEMPER_TESTS

std::shared_ptr<KeptForHost> KeptForHost::make(int32_t n) {
  std::shared_ptr<KeptForHost> self = std::make_shared<KeptForHost>();
  self->n = n;
  return self;
}

int32_t KeptForHost::get_n() const {
  return this->n;
}

#ifdef TEMPER_TESTS
static int32_t sumOf(std::shared_ptr<TestHelper> const & helper) {
  return core::Int::add(helper->i, helper->j);
}

int32_t keptForTests(std::shared_ptr<TestHelper> const & helper) {
  return asserts::sumOf(helper);
}
#endif  // TEMPER_TESTS

void global_init_init() {
  core::init_once([] {
    temper_globals::unitVecs.construct();
    temper_std::global_init_testing();
    core::Str name = TEMPER_STR("case");
    temper_globals::unitVecs.value = core::List::make<std::shared_ptr<Vec2>>(Vec2::make(1.0, 0.0), Vec2::make(0.0, 1.0));
  });
}

}  // namespace asserts

#ifdef TEMPER_TESTS

namespace asserts {

void aTestCase(std::shared_ptr<temper_std::Test> const & test) {
  test->assert_(core::Float64::eq(asserts::normSquared(Vec2::make(0.0, 0.0)), 0.0), []() -> core::Str { return TEMPER_STR("Oh noes"); });
}

void aFailingTest(std::shared_ptr<temper_std::Test> const & test) {
  test->assert_(false, []() -> core::Str { return TEMPER_STR("because it's wrong"); });
  std::shared_ptr<TestHelper> helper = TestHelper::make(3, 2);
  asserts::assertMoreThings(test, helper, core::List::make<std::shared_ptr<std::vector<core::Str>>>(core::List::make<core::Str>(TEMPER_STR("bye"))));
}

void exported(std::shared_ptr<temper_std::Test> const & test) {
  std::shared_ptr<Vec2> vec = Vec2::make(1.5, 2.5);
  {
    core::Str actual = vec->toString();
    core::Str expected = TEMPER_STR("(1.5, 2.5) size squared 8.5");
    test->assert_(actual == expected, [=]() -> core::Str { return "expected vec.toString() == (" + expected + ") not (" + actual + ")"; });
  }
}

void moduleValues(std::shared_ptr<temper_std::Test> const & test) {
  {
    int32_t length = core::List::length<std::shared_ptr<Vec2>>(temper_globals::unitVecs.value);
    int32_t i = 0;
    while (i < length) {
      std::shared_ptr<Vec2> vec = core::List::get<std::shared_ptr<Vec2>>(temper_globals::unitVecs.value, i);
      i = core::Int::add(i, 1);
      {
        double actual = asserts::normSquared(vec);
        double expected = 1.0;
        test->assert_(core::Float64::eq(actual, expected), [=]() -> core::Str { return "expected normSquared(vec) == (" + core::Float64::toString(expected) + ") not (" + core::Float64::toString(actual) + ")"; });
      }
    }
  }
}

}  // namespace asserts

#endif  // TEMPER_TESTS

// main.cpp
#include "asserts/init.hpp"

int main() {
  asserts::global_init_init();
}

// test.cpp
#include "asserts/init.hpp"
#include "temper-core/test_main.hpp"

int main() {
  asserts::global_init_init();
  temper_std::global_init_testing();
  return temper::core::run_tests({
      {"a test case", [] {
        auto test = temper_std::Test::make();
        asserts::aTestCase(test);
        auto messages = test->messagesCombined();
        return temper::core::TestOutcome{
            test->get_passing(),
            temper::core::is_null(messages) ? std::string() : temper::core::Str(messages).str()};
      }},
      {"a failing test", [] {
        auto test = temper_std::Test::make();
        asserts::aFailingTest(test);
        auto messages = test->messagesCombined();
        return temper::core::TestOutcome{
            test->get_passing(),
            temper::core::is_null(messages) ? std::string() : temper::core::Str(messages).str()};
      }},
      {"exported", [] {
        auto test = temper_std::Test::make();
        asserts::exported(test);
        auto messages = test->messagesCombined();
        return temper::core::TestOutcome{
            test->get_passing(),
            temper::core::is_null(messages) ? std::string() : temper::core::Str(messages).str()};
      }},
      {"module values", [] {
        auto test = temper_std::Test::make();
        asserts::moduleValues(test);
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
project(asserts LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 14)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)
find_package(Threads REQUIRED)
if(NOT TARGET std)
  add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/../std ${CMAKE_CURRENT_BINARY_DIR}/std)
endif()

add_library(asserts init.cpp)
target_include_directories(asserts PUBLIC ${CMAKE_CURRENT_SOURCE_DIR}/..)
target_link_libraries(asserts PUBLIC Threads::Threads std)

add_executable(asserts-main main.cpp)
target_link_libraries(asserts-main PRIVATE asserts)

if(TRUE)
  add_executable(asserts-test test.cpp init.cpp)
  target_compile_definitions(asserts-test PRIVATE TEMPER_TESTS)
  target_include_directories(asserts-test PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/..)
  target_link_libraries(asserts-test PRIVATE Threads::Threads std)
endif()

