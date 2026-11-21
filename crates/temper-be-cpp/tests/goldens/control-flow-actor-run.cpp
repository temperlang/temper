// init.hpp
#pragma once

namespace actor_run {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

}  // namespace actor_run

// init.cpp
#include <actor-run/init.hpp>

#include <temper-core/str.hpp>
#include <temper-core/base_types.hpp>
#include <temper-core/module_init.hpp>
#include <temper-core/int.hpp>
#include <temper-core/console.hpp>
#include <temper-core/generator.hpp>
#include <cstdint>
#include <functional>
#include <memory>
#include <string>

namespace actor_run {

namespace core = temper::core;

namespace temper_globals {

static core::GlobalSlot<std::shared_ptr<core::Console::Type>> console;

}  // namespace temper_globals

static void f(std::function<std::shared_ptr<core::SafeGenerator<std::shared_ptr<core::Empty>>>()> const & body) {
  std::shared_ptr<core::SafeGenerator<std::shared_ptr<core::Empty>>> generator = body();
  core::Console::log(temper_globals::console.value, TEMPER_STR("f0"));
  core::next(generator);
  core::Console::log(temper_globals::console.value, TEMPER_STR("f1"));
  core::next(generator);
  core::Console::log(temper_globals::console.value, TEMPER_STR("f2"));
  core::next(generator);
  core::Console::log(temper_globals::console.value, TEMPER_STR("f3"));
}

void global_init_init() {
  core::init_once([] {
    temper_globals::console.construct();
    temper_globals::console.value = core::Console::get_console();
    auto fn = []() -> std::shared_ptr<core::SafeGenerator<std::shared_ptr<core::Empty>>> {
      std::shared_ptr<int32_t> caseIndex = std::make_shared<int32_t>(0);
      std::shared_ptr<int32_t> i = std::make_shared<int32_t>(0);
      auto convertedCoroutine = [=](std::shared_ptr<core::SafeGenerator<std::shared_ptr<core::Empty>>>) -> std::shared_ptr<core::GeneratorResult<std::shared_ptr<core::Empty>>> {
        while (true) {
          int32_t caseIndexLocal = (*caseIndex);
          *caseIndex = -1;
          switch (caseIndexLocal) {
            case 0:
              *i = 0;
              *caseIndex = 1;
              break;
            case 1:
              core::Console::log(temper_globals::console.value, "generated " + std::to_string((*i)));
              *i = core::Int::add((*i), 1);
              *caseIndex = 1;
              return core::ValueResult<std::shared_ptr<core::Empty>>::make(core::empty());
            default:
              return core::doneResult();
          }
        }
      };
      return core::safe_adapt_generator_fn<std::shared_ptr<core::Empty>>(convertedCoroutine);
    };
    actor_run::f(fn);
  });
}

}  // namespace actor_run
