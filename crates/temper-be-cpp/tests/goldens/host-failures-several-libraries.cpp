// init.hpp
#pragma once

#include <temper-core/core.hpp>
#include <count-failures/init.hpp>

namespace host_failures_several_libraries {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

struct HostFailuresError;
struct NotFound;
struct Unreadable;

struct HostFailuresError : public virtual temper::core::Failure {};

struct NotFound : public virtual HostFailuresError {
  temper::core::Str key;
  static std::shared_ptr<NotFound> make(temper::core::Str const & key);
  temper::core::Str get_message() const override;
  temper::core::Str get_key() const;
};

struct Unreadable : public virtual HostFailuresError {
  temper::core::Str key;
  static std::shared_ptr<Unreadable> make(temper::core::Str const & key);
  temper::core::Str get_message() const override;
  temper::core::Str get_key() const;
};

temper::core::Expected<int32_t, std::shared_ptr<NotFound>> hostLookup(temper::core::Str const & key);

}  // namespace host_failures_several_libraries

// init.cpp
#include <host-failures-several-libraries/init.hpp>

#include "_connected.hpp"

namespace host_failures_several_libraries {

namespace core = temper::core;

namespace temper_globals {

static core::GlobalSlot<std::shared_ptr<core::Console::Type>> console;

}  // namespace temper_globals

std::shared_ptr<NotFound> NotFound::make(core::Str const & key) {
  global_init_init();
  std::shared_ptr<NotFound> self = std::make_shared<NotFound>();
  self->key = key;
  return self;
}

core::Str NotFound::get_message() const {
  return "not found (key: " + this->key + ")";
}

core::Str NotFound::get_key() const {
  return this->key;
}

core::Expected<int32_t, std::shared_ptr<NotFound>> hostLookup(core::Str const & key) {
  global_init_init();
  return _connected::hostLookup(key);
}

static int32_t total(core::Str const & key, core::Str const & count) {
  auto result = [&]() -> core::Expected<int32_t, std::shared_ptr<core::Failure>> {
    auto hostLookupResult = host_failures_several_libraries::hostLookup(key);
    if (!hostLookupResult.has_value()) {
      return core::unexpected(hostLookupResult.error());
    }
    int32_t int32 = *hostLookupResult;
    auto parseCountResult = count_failures::parseCount(count);
    if (!parseCountResult.has_value()) {
      return core::unexpected(parseCountResult.error());
    }
    return core::Int::add(int32, *parseCountResult);
  }();
  if (result.has_value()) {
    return *result;
  } else {
    std::shared_ptr<core::Failure> e = result.error();
    core::Console::log(temper_globals::console.value, "caught " + e->get_message());
    return dynamic_cast<NotFound const *>(e.get()) != nullptr ? -1 : -2;
  }
}

std::shared_ptr<Unreadable> Unreadable::make(core::Str const & key) {
  std::shared_ptr<Unreadable> self = std::make_shared<Unreadable>();
  self->key = key;
  return self;
}

core::Str Unreadable::get_message() const {
  return "unreadable (key: " + this->key + ")";
}

core::Str Unreadable::get_key() const {
  return this->key;
}

static core::Expected<int32_t, std::shared_ptr<Unreadable>> reading(core::Str const & key, core::Str const & count) {
  auto result = [&]() -> core::Expected<int32_t, std::shared_ptr<core::Failure>> {
    auto hostLookupResult = host_failures_several_libraries::hostLookup(key);
    if (!hostLookupResult.has_value()) {
      return core::unexpected(hostLookupResult.error());
    }
    int32_t int32 = *hostLookupResult;
    auto parseCountResult = count_failures::parseCount(count);
    if (!parseCountResult.has_value()) {
      return core::unexpected(parseCountResult.error());
    }
    return core::Int::add(int32, *parseCountResult);
  }();
  if (result.has_value()) {
    return *result;
  } else {
    std::shared_ptr<core::Failure> failure = result.error();
    return core::unexpected(core::with_cause(Unreadable::make(key), failure));
  }
}

static int32_t ratio(core::Str const & key, core::Str const & count) {
  try {
    auto result = [&]() -> core::Expected<int32_t, std::shared_ptr<core::Failure>> {
      auto hostLookupResult = host_failures_several_libraries::hostLookup(key);
      if (!hostLookupResult.has_value()) {
        return core::unexpected(hostLookupResult.error());
      }
      int32_t int32 = *hostLookupResult;
      auto parseCountResult = count_failures::parseCount(count);
      if (!parseCountResult.has_value()) {
        return core::unexpected(parseCountResult.error());
      }
      return core::Int::div_wrap(int32, *parseCountResult);
    }();
    if (result.has_value()) {
      return *result;
    } else {
      core::bubble();
    }
  } catch (core::TemperBubble const &) {
    return -9;
  }
}

void global_init_init() {
  core::init_once([] {
    temper_globals::console.construct();
    count_failures::global_init_init();
    temper_globals::console.value = core::Console::get_console();
    core::Console::log(temper_globals::console.value, TEMPER_STR("before"));
    int32_t int32 = host_failures_several_libraries::total(TEMPER_STR("a"), TEMPER_STR("two"));
    int32_t value = host_failures_several_libraries::total(TEMPER_STR("z"), TEMPER_STR("two"));
    core::Console::log(temper_globals::console.value, std::to_string(int32) + " " + std::to_string(value) + " " + std::to_string(host_failures_several_libraries::total(TEMPER_STR("a"), TEMPER_STR("x"))));
    int32_t r;
    auto readingResult = host_failures_several_libraries::reading(TEMPER_STR("a"), TEMPER_STR("x"));
    if (readingResult.has_value()) {
      r = *readingResult;
    } else {
      std::shared_ptr<Unreadable> e = readingResult.error();
      core::Str string = e->get_message() + ", caused by ";
      core::Str value2 = string;
      std::shared_ptr<core::Failure> subject2 = e->get_cause();
      core::NullableParam<core::Str> subject = subject2 == nullptr ? nullptr : core::NullableParam<core::Str>(subject2->get_message());
      core::Console::log(temper_globals::console.value, value2 + (subject == nullptr ? TEMPER_STR("nothing") : subject.value));
      r = 0;
    }
    int32_t value3 = host_failures_several_libraries::ratio(TEMPER_STR("a"), TEMPER_STR("two"));
    int32_t value4 = host_failures_several_libraries::ratio(TEMPER_STR("a"), TEMPER_STR("zero"));
    int32_t value5 = host_failures_several_libraries::ratio(TEMPER_STR("a"), TEMPER_STR("x"));
    core::Console::log(temper_globals::console.value, std::to_string(r) + " " + std::to_string(value3) + " " + std::to_string(value4) + " " + std::to_string(value5) + " " + std::to_string(host_failures_several_libraries::ratio(TEMPER_STR("z"), TEMPER_STR("two"))));
    auto parseCountResult = count_failures::parseCount(TEMPER_STR("x"));
    if (!parseCountResult.has_value()) {
      core::uncaught_failure(parseCountResult.error());
    }
    core::Console::log(temper_globals::console.value, TEMPER_STR("after"));
  });
}

}  // namespace host_failures_several_libraries

// _connected.hpp: host code, as it is
// main.cpp
#include "host-failures-several-libraries/init.hpp"
#include "temper-core/stack_trace.hpp"

int main() {
  temper::core::install_stack_traces();
  host_failures_several_libraries::global_init_init();
}

// CMakeLists.txt
# Generated by Temper.
cmake_minimum_required(VERSION 3.10)
project(host-failures-several-libraries LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 14)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)
find_package(Threads REQUIRED)
if(NOT TARGET count-failures)
  add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/../count-failures ${CMAKE_CURRENT_BINARY_DIR}/count-failures)
endif()

add_library(host-failures-several-libraries init.cpp)
target_include_directories(host-failures-several-libraries PUBLIC ${CMAKE_CURRENT_SOURCE_DIR}/..)
target_link_libraries(host-failures-several-libraries PUBLIC Threads::Threads count-failures)

# Stack traces for the programs, not the library (temper-core/stack_trace.hpp).
include(${CMAKE_CURRENT_SOURCE_DIR}/../temper-core/stack_trace.cmake)
add_executable(host-failures-several-libraries-main main.cpp)
target_link_libraries(host-failures-several-libraries-main PRIVATE host-failures-several-libraries)
temper_stack_traces(host-failures-several-libraries-main)

