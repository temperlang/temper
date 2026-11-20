// init.hpp
#pragma once

#include <memory>
#include <sstream>

namespace build {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

struct StringBuilderOwner;

struct StringBuilderOwner : public std::enable_shared_from_this<StringBuilderOwner> {
  std::shared_ptr<std::ostringstream> builder;
  static std::shared_ptr<StringBuilderOwner> make(std::shared_ptr<std::ostringstream> const & builder);
  std::shared_ptr<std::ostringstream> get_builder() const;
};

}  // namespace build

// init.cpp
#include <build/init.hpp>

#include <temper-core/temper_bubble.hpp>
#include <temper-core/str.hpp>
#include <temper-core/module_init.hpp>
#include <temper-core/boolean.hpp>
#include <temper-core/console.hpp>
#include <temper-core/string.hpp>
#include <temper-core/string_builder.hpp>
#include <cstdint>

namespace build {

namespace core = temper::core;

std::shared_ptr<StringBuilderOwner> StringBuilderOwner::make(std::shared_ptr<std::ostringstream> const & builder) {
  std::shared_ptr<StringBuilderOwner> self = std::make_shared<StringBuilderOwner>();
  self->builder = builder;
  return self;
}

std::shared_ptr<std::ostringstream> StringBuilderOwner::get_builder() const {
  return this->builder;
}

void global_init_init() {
  core::init_once([] {
    std::shared_ptr<core::Console::Type> console = core::Console::get_console();
    std::shared_ptr<std::ostringstream> builder = core::StringBuilder::make();
    core::StringBuilder::append(builder, TEMPER_STR("Hi "));
    core::StringBuilder::appendCodepoint(builder, 127759);
    core::Str source = TEMPER_STR("Wow!! Neato!");
    int32_t bangIndex = core::String::step(source, core::String::begin(), 3);
    int32_t spaceIndex = core::String::step(source, bangIndex, 2);
    core::StringBuilder::appendBetween(builder, source, bangIndex, spaceIndex);
    core::Str built = core::StringBuilder::toString(builder);
    core::StringBuilder::append(builder, TEMPER_STR("\n"));
    core::StringBuilder::append(builder, built);
    core::Console::log(console, core::StringBuilder::toString(builder));
    try {
      core::StringBuilder::appendCodepoint(builder, 1114112);
    } catch (core::TemperBubble const &) {
      core::Console::log(console, TEMPER_STR("High code blocked."));
    }
    try {
      core::StringBuilder::appendCodepoint(builder, 55296);
    } catch (core::TemperBubble const &) {
      core::Console::log(console, TEMPER_STR("Surrogate blocked."));
    }
    core::Console::log(console, core::StringBuilder::toString(builder));
    core::StringBuilder::clear(builder);
    core::StringBuilder::append(builder, TEMPER_STR("Back to square zero."));
    core::Console::log(console, core::StringBuilder::toString(builder));
    core::StringBuilder::clear(builder);
    core::StringBuilder::append(builder, TEMPER_STR("a?\?=b?\?/c?\?>d?\?\?e"));
    core::Str marks = core::StringBuilder::toString(builder);
    core::Console::log(console, marks);
    core::Console::log(console, "Same: " + core::Boolean::toString(marks == TEMPER_STR("a?\?=b?\?/c?\?>d?\?\?e")));
    core::StringBuilder::clear(builder);
    core::StringBuilder::append(builder, TEMPER_STR("?\?"));
    try {
      core::StringBuilder::appendCodepoint(builder, 1);
    } catch (core::TemperBubble const &) {
      core::Console::log(console, TEMPER_STR("No U+1."));
    }
    core::StringBuilder::append(builder, TEMPER_STR("0"));
    try {
      core::StringBuilder::appendCodepoint(builder, 8364);
    } catch (core::TemperBubble const &) {
      core::Console::log(console, TEMPER_STR("No euro."));
    }
    core::StringBuilder::append(builder, TEMPER_STR("1"));
    core::Console::log(console, "Digits after escapes: " + core::Boolean::toString(core::StringBuilder::toString(builder) == TEMPER_STR("?\?\u00010€1")));
    std::shared_ptr<StringBuilderOwner> owner = StringBuilderOwner::make(core::StringBuilder::make());
    core::StringBuilder::append(owner->builder, TEMPER_STR("Hi from inside."));
    core::Console::log(console, core::StringBuilder::toString(owner->builder));
  });
}

}  // namespace build
