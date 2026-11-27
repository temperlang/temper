// init.hpp
#pragma once

#include <temper-core/core.hpp>
#ifdef TEMPER_TESTS
#include <std/regex.hpp>
#include <std/testing.hpp>
#endif

namespace host_errors {

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
void listRead(std::shared_ptr<temper_std::Test> const & test);
void listReadAtTheEnd(std::shared_ptr<temper_std::Test> const & test);
void negativeIndex(std::shared_ptr<temper_std::Test> const & test);
void set(std::shared_ptr<temper_std::Test> const & test);
void setAtTheEnd(std::shared_ptr<temper_std::Test> const & test);
void insert2(std::shared_ptr<temper_std::Test> const & test);
void insertPastTheEnd(std::shared_ptr<temper_std::Test> const & test);
void insertAtTheEndAppends(std::shared_ptr<temper_std::Test> const & test);
void removeLast2(std::shared_ptr<temper_std::Test> const & test);
void removeFirst2(std::shared_ptr<temper_std::Test> const & test);
void reduce(std::shared_ptr<temper_std::Test> const & test);
void stringRead(std::shared_ptr<temper_std::Test> const & test);
void replaceCallback(std::shared_ptr<temper_std::Test> const & test);
#endif

}  // namespace host_errors

// init.cpp
#include <host-errors/init.hpp>

namespace host_errors {

namespace core = temper::core;

#ifdef TEMPER_TESTS
std::shared_ptr<Oops> Oops::make() {
  std::shared_ptr<Oops> self = std::make_shared<Oops>();
  return self;
}

core::Str Oops::get_message() const {
  return TEMPER_STR("oops");
}

static core::Expected<int32_t, std::shared_ptr<Oops>> at(int32_t i) {
  if (i == 99) {
    return core::unexpected(Oops::make());
  }
  return i;
}

static std::shared_ptr<std::vector<int32_t>> built() {
  std::shared_ptr<std::vector<int32_t>> lb = core::ListBuilder::make<int32_t>();
  core::ListBuilder::add<int32_t>(lb, 1);
  return lb;
}

static core::Expected<int32_t, std::shared_ptr<Oops>> read(std::shared_ptr<std::vector<int32_t>> const & ls, int32_t i) {
  auto atResult = at(i);
  if (!atResult.has_value()) {
    return core::unexpected(atResult.error());
  }
  return core::List::get<int32_t>(ls, *atResult);
}

static core::Expected<int32_t, std::shared_ptr<Oops>> write(std::shared_ptr<std::vector<int32_t>> const & lb, int32_t i) {
  auto atResult = at(i);
  if (!atResult.has_value()) {
    return core::unexpected(atResult.error());
  }
  core::ListBuilder::set<int32_t>(lb, *atResult, 3);
  return core::List::length<int32_t>(lb);
}

static core::Expected<int32_t, std::shared_ptr<Oops>> insert(std::shared_ptr<std::vector<int32_t>> const & lb, int32_t i) {
  auto atResult = at(i);
  if (!atResult.has_value()) {
    return core::unexpected(atResult.error());
  }
  core::ListBuilder::add<int32_t>(lb, 2, *atResult);
  return core::List::length<int32_t>(lb);
}

static core::Expected<int32_t, std::shared_ptr<Oops>> removeLast(std::shared_ptr<std::vector<int32_t>> const & lb, int32_t i) {
  auto atResult = at(i);
  if (!atResult.has_value()) {
    return core::unexpected(atResult.error());
  }
  return core::ListBuilder::removeLast<int32_t>(lb);
}

static core::Expected<int32_t, std::shared_ptr<Oops>> removeFirst(std::shared_ptr<std::deque<int32_t>> const & d, int32_t i) {
  auto atResult = at(i);
  if (!atResult.has_value()) {
    return core::unexpected(atResult.error());
  }
  return core::Deque::removeFirst<int32_t>(d);
}

static core::Expected<int32_t, std::shared_ptr<Oops>> sum(std::shared_ptr<std::vector<int32_t>> const & ls, int32_t i) {
  auto atResult = at(i);
  if (!atResult.has_value()) {
    return core::unexpected(atResult.error());
  }
  return core::List::reduce(ls, [](int32_t a, int32_t b) -> int32_t { return core::Int::add(a, b); });
}

static core::Expected<int32_t, std::shared_ptr<Oops>> codeAt(core::Str const & s, int32_t i) {
  auto atResult = at(i);
  if (!atResult.has_value()) {
    return core::unexpected(atResult.error());
  }
  return core::String::get(s, core::String::end(s));
}

static core::Expected<core::Str, std::shared_ptr<Oops>> replaced(int32_t i) {
  auto atResult = at(i);
  if (!atResult.has_value()) {
    return core::unexpected(atResult.error());
  }
  return *temper_std::CodePoints::make(TEMPER_STR("b"))->compiled()->replace<core::Never>(TEMPER_STR("abc"), [=](std::shared_ptr<temper_std::Match>) -> core::Str { return core::List::get<core::Str>(core::List::make<core::Str>(TEMPER_STR("x")), i); });
}
#endif  // TEMPER_TESTS

void global_init_init() {}

}  // namespace host_errors

#ifdef TEMPER_TESTS

namespace host_errors {

void listRead(std::shared_ptr<temper_std::Test> const & test) {
  int32_t r;
  auto readResult = host_errors::read(core::List::make<int32_t>(1, 2), 5);
  if (readResult.has_value()) {
    r = *readResult;
  } else {
    r = -1;
  }
  test->assert_(r != -1, []() -> core::Str { return TEMPER_STR("caught"); });
}

void listReadAtTheEnd(std::shared_ptr<temper_std::Test> const & test) {
  std::shared_ptr<std::vector<int32_t>> ls = core::List::make<int32_t>(1, 2);
  int32_t r;
  auto readResult = host_errors::read(ls, core::List::length<int32_t>(ls));
  if (readResult.has_value()) {
    r = *readResult;
  } else {
    r = -1;
  }
  test->assert_(r != -1, []() -> core::Str { return TEMPER_STR("caught"); });
}

void negativeIndex(std::shared_ptr<temper_std::Test> const & test) {
  int32_t r;
  auto readResult = host_errors::read(built(), -1);
  if (readResult.has_value()) {
    r = *readResult;
  } else {
    r = -1;
  }
  test->assert_(r != -1, []() -> core::Str { return TEMPER_STR("caught"); });
}

void set(std::shared_ptr<temper_std::Test> const & test) {
  int32_t r;
  auto writeResult = host_errors::write(built(), 5);
  if (writeResult.has_value()) {
    r = *writeResult;
  } else {
    r = -1;
  }
  test->assert_(r != -1, []() -> core::Str { return TEMPER_STR("caught"); });
}

void setAtTheEnd(std::shared_ptr<temper_std::Test> const & test) {
  std::shared_ptr<std::vector<int32_t>> lb = built();
  int32_t r;
  auto writeResult = host_errors::write(lb, core::List::length<int32_t>(lb));
  if (writeResult.has_value()) {
    r = *writeResult;
  } else {
    r = -1;
  }
  test->assert_(r != -1, []() -> core::Str { return TEMPER_STR("caught"); });
}

void insert2(std::shared_ptr<temper_std::Test> const & test) {
  int32_t r;
  auto insertResult = host_errors::insert(built(), 5);
  if (insertResult.has_value()) {
    r = *insertResult;
  } else {
    r = -1;
  }
  test->assert_(r != -1, []() -> core::Str { return TEMPER_STR("caught"); });
}

void insertPastTheEnd(std::shared_ptr<temper_std::Test> const & test) {
  std::shared_ptr<std::vector<int32_t>> lb = built();
  int32_t r;
  auto insertResult = host_errors::insert(lb, core::Int::add(core::List::length<int32_t>(lb), 1));
  if (insertResult.has_value()) {
    r = *insertResult;
  } else {
    r = -1;
  }
  test->assert_(r != -1, []() -> core::Str { return TEMPER_STR("caught"); });
}

void insertAtTheEndAppends(std::shared_ptr<temper_std::Test> const & test) {
  std::shared_ptr<std::vector<int32_t>> lb = built();
  int32_t n;
  auto insertResult = host_errors::insert(lb, core::List::length<int32_t>(lb));
  if (insertResult.has_value()) {
    n = *insertResult;
  } else {
    n = -1;
  }
  test->assert_(n == 2 && core::List::get<int32_t>(lb, 1) == 2, []() -> core::Str { return TEMPER_STR("didn't append"); });
}

void removeLast2(std::shared_ptr<temper_std::Test> const & test) {
  int32_t r;
  auto removeLastResult = host_errors::removeLast(core::ListBuilder::make<int32_t>(), 0);
  if (removeLastResult.has_value()) {
    r = *removeLastResult;
  } else {
    r = -1;
  }
  test->assert_(r != -1, []() -> core::Str { return TEMPER_STR("caught"); });
}

void removeFirst2(std::shared_ptr<temper_std::Test> const & test) {
  int32_t r;
  auto removeFirstResult = host_errors::removeFirst(core::Deque::make<int32_t>(), 0);
  if (removeFirstResult.has_value()) {
    r = *removeFirstResult;
  } else {
    r = -1;
  }
  test->assert_(r != -1, []() -> core::Str { return TEMPER_STR("caught"); });
}

void reduce(std::shared_ptr<temper_std::Test> const & test) {
  int32_t r;
  auto sumResult = host_errors::sum(core::List::make<int32_t>(), 0);
  if (sumResult.has_value()) {
    r = *sumResult;
  } else {
    r = -1;
  }
  test->assert_(r != -1, []() -> core::Str { return TEMPER_STR("caught"); });
}

void stringRead(std::shared_ptr<temper_std::Test> const & test) {
  int32_t r;
  auto codeAtResult = host_errors::codeAt(TEMPER_STR("ab"), 0);
  if (codeAtResult.has_value()) {
    r = *codeAtResult;
  } else {
    r = -1;
  }
  test->assert_(r != -1, []() -> core::Str { return TEMPER_STR("caught"); });
}

void replaceCallback(std::shared_ptr<temper_std::Test> const & test) {
  core::Str r;
  auto replacedResult = replaced(3);
  if (replacedResult.has_value()) {
    r = *replacedResult;
  } else {
    r = core::Str();
  }
  test->assert_(r != core::Str(), []() -> core::Str { return TEMPER_STR("caught"); });
}

}  // namespace host_errors

#endif  // TEMPER_TESTS

// main.cpp
#include "host-errors/init.hpp"
#include "temper-core/stack_trace.hpp"

int main() {
  temper::core::install_stack_traces();
  host_errors::global_init_init();
}

// test.cpp
#include "host-errors/init.hpp"
#include "temper-core/test_main.hpp"

int main() {
  temper::core::install_stack_traces();
  host_errors::global_init_init();
  temper_std::global_init_regex();
  temper_std::global_init_testing();
  return temper::core::run_tests({
      {"list read", [] {
        auto test = temper_std::Test::make();
        host_errors::listRead(test);
        auto messages = test->messagesCombined();
        return temper::core::TestOutcome{
            test->get_passing(),
            temper::core::is_null(messages) ? std::string() : temper::core::Str(messages).str()};
      }},
      {"list read at the end", [] {
        auto test = temper_std::Test::make();
        host_errors::listReadAtTheEnd(test);
        auto messages = test->messagesCombined();
        return temper::core::TestOutcome{
            test->get_passing(),
            temper::core::is_null(messages) ? std::string() : temper::core::Str(messages).str()};
      }},
      {"negative index", [] {
        auto test = temper_std::Test::make();
        host_errors::negativeIndex(test);
        auto messages = test->messagesCombined();
        return temper::core::TestOutcome{
            test->get_passing(),
            temper::core::is_null(messages) ? std::string() : temper::core::Str(messages).str()};
      }},
      {"set", [] {
        auto test = temper_std::Test::make();
        host_errors::set(test);
        auto messages = test->messagesCombined();
        return temper::core::TestOutcome{
            test->get_passing(),
            temper::core::is_null(messages) ? std::string() : temper::core::Str(messages).str()};
      }},
      {"set at the end", [] {
        auto test = temper_std::Test::make();
        host_errors::setAtTheEnd(test);
        auto messages = test->messagesCombined();
        return temper::core::TestOutcome{
            test->get_passing(),
            temper::core::is_null(messages) ? std::string() : temper::core::Str(messages).str()};
      }},
      {"insert", [] {
        auto test = temper_std::Test::make();
        host_errors::insert2(test);
        auto messages = test->messagesCombined();
        return temper::core::TestOutcome{
            test->get_passing(),
            temper::core::is_null(messages) ? std::string() : temper::core::Str(messages).str()};
      }},
      {"insert past the end", [] {
        auto test = temper_std::Test::make();
        host_errors::insertPastTheEnd(test);
        auto messages = test->messagesCombined();
        return temper::core::TestOutcome{
            test->get_passing(),
            temper::core::is_null(messages) ? std::string() : temper::core::Str(messages).str()};
      }},
      {"insert at the end appends", [] {
        auto test = temper_std::Test::make();
        host_errors::insertAtTheEndAppends(test);
        auto messages = test->messagesCombined();
        return temper::core::TestOutcome{
            test->get_passing(),
            temper::core::is_null(messages) ? std::string() : temper::core::Str(messages).str()};
      }},
      {"remove last", [] {
        auto test = temper_std::Test::make();
        host_errors::removeLast2(test);
        auto messages = test->messagesCombined();
        return temper::core::TestOutcome{
            test->get_passing(),
            temper::core::is_null(messages) ? std::string() : temper::core::Str(messages).str()};
      }},
      {"remove first", [] {
        auto test = temper_std::Test::make();
        host_errors::removeFirst2(test);
        auto messages = test->messagesCombined();
        return temper::core::TestOutcome{
            test->get_passing(),
            temper::core::is_null(messages) ? std::string() : temper::core::Str(messages).str()};
      }},
      {"reduce", [] {
        auto test = temper_std::Test::make();
        host_errors::reduce(test);
        auto messages = test->messagesCombined();
        return temper::core::TestOutcome{
            test->get_passing(),
            temper::core::is_null(messages) ? std::string() : temper::core::Str(messages).str()};
      }},
      {"string read", [] {
        auto test = temper_std::Test::make();
        host_errors::stringRead(test);
        auto messages = test->messagesCombined();
        return temper::core::TestOutcome{
            test->get_passing(),
            temper::core::is_null(messages) ? std::string() : temper::core::Str(messages).str()};
      }},
      {"replace callback", [] {
        auto test = temper_std::Test::make();
        host_errors::replaceCallback(test);
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
project(host-errors LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 14)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)
find_package(Threads REQUIRED)

add_library(host-errors init.cpp)
target_include_directories(host-errors PUBLIC ${CMAKE_CURRENT_SOURCE_DIR}/..)
target_link_libraries(host-errors PUBLIC Threads::Threads)

# Stack traces for the programs, not the library (temper-core/stack_trace.hpp).
include(${CMAKE_CURRENT_SOURCE_DIR}/../temper-core/stack_trace.cmake)
add_executable(host-errors-main main.cpp)
target_link_libraries(host-errors-main PRIVATE host-errors)
temper_stack_traces(host-errors-main)

if(EXISTS ${CMAKE_CURRENT_SOURCE_DIR}/../std/CMakeLists.txt)
  if(NOT TARGET std)
    add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/../std ${CMAKE_CURRENT_BINARY_DIR}/std)
  endif()
  add_executable(host-errors-test test.cpp init.cpp)
  target_compile_definitions(host-errors-test PRIVATE TEMPER_TESTS)
  target_include_directories(host-errors-test PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/..)
  target_link_libraries(host-errors-test PRIVATE Threads::Threads std)
  temper_stack_traces(host-errors-test)
endif()

