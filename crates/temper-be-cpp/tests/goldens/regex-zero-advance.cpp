// init.hpp
#pragma once

#include <temper-core/str.hpp>
#include <std/regex.hpp>

namespace zero_advance {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

temper::core::Str bulletizeList(temper::core::Str const & text);

}  // namespace zero_advance

// init.cpp
#include <zero-advance/init.hpp>

#include <temper-core/list.hpp>
#include <temper-core/module_init.hpp>
#include <temper-core/casting.hpp>
#include <temper-core/console.hpp>
#include <temper-core/string.hpp>
#include <memory>

namespace zero_advance {

namespace core = temper::core;

namespace temper_globals {

static core::GlobalSlot<std::shared_ptr<temper_std::Regex>> commaItemStartRegex;

}  // namespace temper_globals

core::Str bulletizeList(core::Str const & text) {
  global_init_init();
  core::Str replaced = temper_globals::commaItemStartRegex.value->replace(text, [](std::shared_ptr<temper_std::Match>) -> core::Str { return TEMPER_STR("\n- "); });
  return core::String::slice(replaced, core::String::next(replaced, core::String::begin()), core::String::end(replaced));
}

void global_init_init() {
  core::init_once([] {
    temper_globals::commaItemStartRegex.construct();
    temper_std::global_init_regex();
    std::shared_ptr<core::Console::Type> console = core::Console::get_console();
    std::shared_ptr<temper_std::RegexNode> regexNode = core::to_base<temper_std::RegexNode>(temper_std::Or::make(core::List::make<std::shared_ptr<temper_std::RegexNode>>(core::to_base<temper_std::RegexNode>(temper_std::temper_globals::Begin.value), core::to_base<temper_std::RegexNode>(temper_std::CodePoints::make(TEMPER_STR(","))))));
    temper_globals::commaItemStartRegex.value = temper_std::Sequence::make(core::List::make<std::shared_ptr<temper_std::RegexNode>>(regexNode, core::to_base<temper_std::RegexNode>(temper_std::Repeat::make(core::to_base<temper_std::RegexNode>(temper_std::temper_globals::Space.value), 0, nullptr, false))))->compiled();
    core::Console::log(console, zero_advance::bulletizeList(TEMPER_STR("apples, bananas, cherries")));
  });
}

}  // namespace zero_advance
