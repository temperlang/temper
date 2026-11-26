// init.hpp
#pragma once

#include <temper-core/core.hpp>

namespace await_in_loop {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

}  // namespace await_in_loop

// init.cpp
#include <await-in-loop/init.hpp>

namespace await_in_loop {

namespace core = temper::core;

namespace temper_globals {

static core::GlobalSlot<std::shared_ptr<core::Console::Type>> console;
static core::GlobalSlot<std::shared_ptr<core::PromiseBuilder<core::Str>>> once;
static core::GlobalSlot<std::shared_ptr<core::PromiseBuilder<core::Str>>> later;

}  // namespace temper_globals

void global_init_init() {
  core::init_once([] {
    temper_globals::console.construct();
    temper_globals::once.construct();
    temper_globals::later.construct();
    temper_globals::console.value = core::Console::get_console();
    temper_globals::once.value = core::PromiseBuilderNs::make<core::Str>();
    core::complete(temper_globals::once.value, TEMPER_STR("same"));
    temper_globals::later.value = core::PromiseBuilderNs::make<core::Str>();
    auto fn = []() -> std::shared_ptr<core::SafeGenerator<std::shared_ptr<core::Empty>>> {
      std::shared_ptr<int32_t> caseIndex = std::make_shared<int32_t>(0);
      std::shared_ptr<int32_t> i = std::make_shared<int32_t>(0);
      std::shared_ptr<core::Str> s = std::make_shared<core::Str>(core::Str());
      std::shared_ptr<int32_t> j = std::make_shared<int32_t>(0);
      std::shared_ptr<core::Str> s2 = std::make_shared<core::Str>(core::Str());
      std::shared_ptr<core::Str> t = std::make_shared<core::Str>(core::Str());
      std::shared_ptr<std::shared_ptr<core::Promise<core::Str>>> awaited = std::make_shared<std::shared_ptr<core::Promise<core::Str>>>(nullptr);
      std::shared_ptr<std::shared_ptr<core::Promise<core::Str>>> awaited2 = std::make_shared<std::shared_ptr<core::Promise<core::Str>>>(nullptr);
      std::shared_ptr<std::shared_ptr<core::Promise<core::Str>>> awaited3 = std::make_shared<std::shared_ptr<core::Promise<core::Str>>>(nullptr);
      auto convertedCoroutine = [=](std::shared_ptr<core::SafeGenerator<std::shared_ptr<core::Empty>>> generator) -> std::shared_ptr<core::GeneratorResult<std::shared_ptr<core::Empty>>> {
        while (true) {
          int32_t caseIndexLocal = (*caseIndex);
          *caseIndex = -1;
          switch (caseIndexLocal) {
            case 0:
              *i = 0;
              *caseIndex = 1;
              break;
            case 1:
              if ((*i) < 2) {
                *caseIndex = 2;
              } else {
                *caseIndex = 3;
              }
              break;
            case 2:
              *awaited = core::getpromise(temper_globals::once.value);
              *caseIndex = 6;
              core::awake_upon((*awaited), generator);
              return core::ValueResult<std::shared_ptr<core::Empty>>::make(core::empty());
            case 3:
              *j = 0;
              *caseIndex = 7;
              break;
            case 4:
              *s = TEMPER_STR("?");
              *caseIndex = 5;
              break;
            case 5:
              core::Console::log(temper_globals::console.value, (*s) + " " + std::to_string((*i)));
              *i = core::Int::add((*i), 1);
              *caseIndex = 1;
              break;
            case 6: {
              auto Result = core::get_promise_result_sync((*awaited));
              if (Result.has_value()) {
                *s = *Result;
                *caseIndex = 5;
              } else {
                *caseIndex = 4;
              }
              break;
            }
            case 7:
              if ((*j) < 2) {
                *caseIndex = 8;
              } else {
                *caseIndex = 9;
              }
              break;
            case 8:
              *awaited2 = core::getpromise(temper_globals::later.value);
              *caseIndex = 12;
              core::awake_upon((*awaited2), generator);
              return core::ValueResult<std::shared_ptr<core::Empty>>::make(core::empty());
            case 9:
              *awaited3 = core::getpromise(temper_globals::once.value);
              *caseIndex = 15;
              core::awake_upon((*awaited3), generator);
              return core::ValueResult<std::shared_ptr<core::Empty>>::make(core::empty());
            case 10:
              *s2 = TEMPER_STR("?");
              *caseIndex = 11;
              break;
            case 11:
              core::Console::log(temper_globals::console.value, (*s2) + " " + std::to_string((*j)));
              *j = core::Int::add((*j), 1);
              *caseIndex = 7;
              break;
            case 12: {
              auto Result2 = core::get_promise_result_sync((*awaited2));
              if (Result2.has_value()) {
                *s2 = *Result2;
                *caseIndex = 11;
              } else {
                *caseIndex = 10;
              }
              break;
            }
            case 13:
              *t = TEMPER_STR("?");
              *caseIndex = 14;
              break;
            case 14:
              core::Console::log(temper_globals::console.value, (*t) + " after the loops");
              return core::doneResult();
            case 15: {
              auto Result3 = core::get_promise_result_sync((*awaited3));
              if (Result3.has_value()) {
                *t = *Result3;
                *caseIndex = 14;
              } else {
                *caseIndex = 13;
              }
              break;
            }
            default:
              return core::doneResult();
          }
        }
      };
      return core::safe_adapt_generator_fn<std::shared_ptr<core::Empty>>(convertedCoroutine);
    };
    core::async_run(fn);
    core::complete(temper_globals::later.value, TEMPER_STR("later"));
  });
}

}  // namespace await_in_loop
