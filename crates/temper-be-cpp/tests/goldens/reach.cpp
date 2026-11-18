// init.hpp
#pragma once

#include <temper-core/core.hpp>
#ifdef TEMPER_TESTS
#include <std/testing.hpp>
#endif

namespace reach {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

int32_t shown(int32_t n);


#ifdef TEMPER_TESTS
struct TestShape;
struct TestSquare;

struct TestShape : public virtual temper::core::AnyValueBase {
  virtual int32_t area() const = 0;
};

struct TestSquare : public virtual TestShape {
  int32_t side;
  static std::shared_ptr<TestSquare> make(int32_t side);
  int32_t area() const override;
  int32_t get_side() const;
};

// Tests, which `test.cpp` runs.
void testParts(std::shared_ptr<temper_std::Test> const & test);
#endif

}  // namespace reach

// init.cpp
#include <reach/init.hpp>

#include <mutex>

namespace reach {

namespace core = temper::core;

namespace temper_globals {

static core::GlobalSlot<int32_t> shownCount;
static core::GlobalSlot<int32_t> base;
static core::GlobalSlot<int32_t> checked;

}  // namespace temper_globals

#ifdef TEMPER_TESTS
namespace temper_globals {

static core::GlobalSlot<int32_t> testFactor;
static core::GlobalSlot<int32_t> testCount;

}  // namespace temper_globals
#endif  // TEMPER_TESTS

static std::mutex moduleMutex;

#ifdef TEMPER_TESTS
// Reads a variable that functions write, under the module's lock.
template <class T>
static T load(T const &variable) {
  std::lock_guard<std::mutex> lock(moduleMutex);
  return variable;
}

// Writes a variable that functions write, under the module's lock.
template <class T, class V>
static void store(T &variable, V &&value) {
  std::lock_guard<std::mutex> lock(moduleMutex);
  variable = std::forward<V>(value);
}
#endif  // TEMPER_TESTS

int32_t shown(int32_t n) {
  global_init_init();
  std::lock_guard<std::mutex> lock(moduleMutex);
  temper_globals::shownCount.value = core::Int::add(temper_globals::shownCount.value, 1);
  return core::Int::add(n, temper_globals::base.value);
}

#ifdef TEMPER_TESTS
std::shared_ptr<TestSquare> TestSquare::make(int32_t side) {
  std::shared_ptr<TestSquare> self = std::make_shared<TestSquare>();
  self->side = side;
  return self;
}

int32_t TestSquare::area() const {
  return core::Int::mul(this->side, this->side);
}

int32_t TestSquare::get_side() const {
  return this->side;
}

static int32_t forTests(int32_t n) {
  return core::Int::mul(n, temper_globals::testFactor.value);
}

static void addArea(std::shared_ptr<TestShape> const & shape) {
  store(temper_globals::testCount.value, core::Int::add(load(temper_globals::testCount.value), shape->area()));
}
#endif  // TEMPER_TESTS

void global_init_init() {
  core::init_once([] {
    temper_globals::shownCount.construct();
    temper_globals::base.construct();
    temper_globals::checked.construct();
    std::shared_ptr<core::Console::Type> console = core::Console::get_console();
    temper_globals::shownCount.value = 0;
    temper_globals::base.value = 1;
    temper_globals::checked.value = shown(2);
    core::Console::log(console, "shown " + std::to_string(shown(1)));
#ifdef TEMPER_TESTS
    temper_globals::testFactor.construct();
    temper_globals::testCount.construct();
    temper_globals::testFactor.value = core::Int::mul(temper_globals::base.value, 2);
    temper_globals::testCount.value = 0;
#endif  // TEMPER_TESTS
  });
}

}  // namespace reach

#ifdef TEMPER_TESTS

namespace reach {

void testParts(std::shared_ptr<temper_std::Test> const & test) {
  std::shared_ptr<TestShape> shape = core::to_base<TestShape>(TestSquare::make(forTests(2)));
  reach::addArea(shape);
  store(temper_globals::testCount.value, core::Int::add(load(temper_globals::testCount.value), shape->area()));
  {
    int32_t actual = load(temper_globals::testCount.value);
    int32_t expected = 32;
    test->assert_(actual == expected, [=]() -> core::Str { return "expected testCount == (" + std::to_string(expected) + ") not (" + std::to_string(actual) + ")"; });
  }
  {
    int32_t actual = temper_globals::checked.value;
    int32_t expected = 3;
    test->assert_(actual == expected, [=]() -> core::Str { return "expected checked == (" + std::to_string(expected) + ") not (" + std::to_string(actual) + ")"; });
  }
  {
    int32_t actual = load(temper_globals::shownCount.value);
    int32_t expected = 2;
    test->assert_(actual == expected, [=]() -> core::Str { return "expected shownCount == (" + std::to_string(expected) + ") not (" + std::to_string(actual) + ")"; });
  }
  {
    std::lock_guard<std::mutex> lock(moduleMutex);
    temper_globals::shownCount.value = 0;
  }
  shown(0);
  {
    int32_t actual = load(temper_globals::shownCount.value);
    int32_t expected = 1;
    test->assert_(actual == expected, [=]() -> core::Str { return "expected shownCount == (" + std::to_string(expected) + ") not (" + std::to_string(actual) + ")"; });
  }
}

}  // namespace reach

#endif  // TEMPER_TESTS

// main.cpp
#include "reach/init.hpp"

int main() {
  reach::global_init_init();
}

// test.cpp
#include "reach/init.hpp"
#include "temper-core/test_main.hpp"

int main() {
  reach::global_init_init();
  temper_std::global_init_testing();
  return temper::core::run_tests({
      {"test parts", [] {
        auto test = temper_std::Test::make();
        reach::testParts(test);
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
project(reach LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 14)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)
find_package(Threads REQUIRED)

add_library(reach init.cpp)
target_include_directories(reach PUBLIC ${CMAKE_CURRENT_SOURCE_DIR}/..)
target_link_libraries(reach PUBLIC Threads::Threads)

add_executable(reach-main main.cpp)
target_link_libraries(reach-main PRIVATE reach)

if(EXISTS ${CMAKE_CURRENT_SOURCE_DIR}/../std/CMakeLists.txt)
  if(NOT TARGET std)
    add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/../std ${CMAKE_CURRENT_BINARY_DIR}/std)
  endif()
  add_executable(reach-test test.cpp init.cpp)
  target_compile_definitions(reach-test PRIVATE TEMPER_TESTS)
  target_include_directories(reach-test PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/..)
  target_link_libraries(reach-test PRIVATE Threads::Threads std)
endif()

