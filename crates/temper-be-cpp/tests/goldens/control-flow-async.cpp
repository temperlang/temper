// init.hpp
#pragma once

namespace async {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

}  // namespace async

// init.cpp
#include <async/init.hpp>

#include <temper-core/temper_bubble.hpp>
#include <temper-core/str.hpp>
#include <temper-core/base_types.hpp>
#include <temper-core/module_init.hpp>
#include <temper-core/console.hpp>
#include <temper-core/generator.hpp>
#include <temper-core/promise.hpp>
#include <cstdint>
#include <memory>

namespace async {

namespace core = temper::core;

namespace temper_globals {

static core::GlobalSlot<std::shared_ptr<core::Console::Type>> console;

}  // namespace temper_globals

void global_init_init() {
  core::init_once([] {
    temper_globals::console.construct();
    temper_globals::console.value = core::Console::get_console();
    auto fn = []() -> std::shared_ptr<core::SafeGenerator<std::shared_ptr<core::Empty>>> {
      std::shared_ptr<int32_t> caseIndex2 = std::make_shared<int32_t>(0);
      std::shared_ptr<std::shared_ptr<core::PromiseBuilder<core::Str>>> b = std::make_shared<std::shared_ptr<core::PromiseBuilder<core::Str>>>(nullptr);
      std::shared_ptr<std::shared_ptr<core::PromiseBuilder<core::Str>>> b2 = std::make_shared<std::shared_ptr<core::PromiseBuilder<core::Str>>>(nullptr);
      std::shared_ptr<std::shared_ptr<core::PromiseBuilder<core::Str>>> b3 = std::make_shared<std::shared_ptr<core::PromiseBuilder<core::Str>>>(nullptr);
      std::shared_ptr<std::shared_ptr<core::PromiseBuilder<core::Str>>> b4 = std::make_shared<std::shared_ptr<core::PromiseBuilder<core::Str>>>(nullptr);
      std::shared_ptr<std::shared_ptr<core::PromiseBuilder<std::shared_ptr<core::Empty>>>> a = std::make_shared<std::shared_ptr<core::PromiseBuilder<std::shared_ptr<core::Empty>>>>(nullptr);
      std::shared_ptr<std::shared_ptr<core::PromiseBuilder<std::shared_ptr<core::Empty>>>> b5 = std::make_shared<std::shared_ptr<core::PromiseBuilder<std::shared_ptr<core::Empty>>>>(nullptr);
      std::shared_ptr<std::shared_ptr<core::PromiseBuilder<core::Str>>> b6 = std::make_shared<std::shared_ptr<core::PromiseBuilder<core::Str>>>(nullptr);
      std::shared_ptr<core::Str> t = std::make_shared<core::Str>(core::Str());
      std::shared_ptr<std::shared_ptr<core::Promise<core::Str>>> awaited2 = std::make_shared<std::shared_ptr<core::Promise<core::Str>>>(nullptr);
      std::shared_ptr<core::Str> t2 = std::make_shared<core::Str>(core::Str());
      std::shared_ptr<std::shared_ptr<core::Promise<core::Str>>> awaited3 = std::make_shared<std::shared_ptr<core::Promise<core::Str>>>(nullptr);
      std::shared_ptr<core::Str> t3 = std::make_shared<core::Str>(core::Str());
      std::shared_ptr<std::shared_ptr<core::Promise<core::Str>>> awaited4 = std::make_shared<std::shared_ptr<core::Promise<core::Str>>>(nullptr);
      std::shared_ptr<core::Str> t4 = std::make_shared<core::Str>(core::Str());
      std::shared_ptr<std::shared_ptr<core::Promise<core::Str>>> awaited5 = std::make_shared<std::shared_ptr<core::Promise<core::Str>>>(nullptr);
      std::shared_ptr<core::Str> t5 = std::make_shared<core::Str>(core::Str());
      std::shared_ptr<std::shared_ptr<core::Promise<core::Str>>> awaited6 = std::make_shared<std::shared_ptr<core::Promise<core::Str>>>(nullptr);
      std::shared_ptr<std::shared_ptr<core::Promise<std::shared_ptr<core::Empty>>>> awaited7 = std::make_shared<std::shared_ptr<core::Promise<std::shared_ptr<core::Empty>>>>(nullptr);
      std::shared_ptr<core::Str> t6 = std::make_shared<core::Str>(core::Str());
      std::shared_ptr<std::shared_ptr<core::Promise<core::Str>>> awaited8 = std::make_shared<std::shared_ptr<core::Promise<core::Str>>>(nullptr);
      auto convertedCoroutine2 = [=](std::shared_ptr<core::SafeGenerator<std::shared_ptr<core::Empty>>> generator2) -> std::shared_ptr<core::GeneratorResult<std::shared_ptr<core::Empty>>> {
        while (true) {
          int32_t caseIndexLocal2 = (*caseIndex2);
          *caseIndex2 = -1;
          switch (caseIndexLocal2) {
            case 0:
              try {
                *b = core::PromiseBuilderNs::make<core::Str>();
                core::complete((*b), TEMPER_STR("Completed synchronously"));
                *awaited2 = core::getpromise((*b));
                *caseIndex2 = 3;
                core::awake_upon((*awaited2), generator2);
                return core::ValueResult<std::shared_ptr<core::Empty>>::make(core::empty());
              } catch (core::TemperBubble const &) {
                *caseIndex2 = 1;
              }
              break;
            case 1:
              core::panic();
            case 2:
              *b2 = core::PromiseBuilderNs::make<core::Str>();
              core::breakpromise((*b2));
              *caseIndex2 = 6;
              break;
            case 3:
              try {
                *t = core::get_promise_result_sync((*awaited2));
                core::Console::log(temper_globals::console.value, (*t));
                *caseIndex2 = 2;
              } catch (core::TemperBubble const &) {
                *caseIndex2 = 1;
              }
              break;
            case 4:
              *t2 = TEMPER_STR("Broken synchronously");
              *caseIndex2 = 5;
              break;
            case 5:
              core::Console::log(temper_globals::console.value, (*t2));
              *caseIndex2 = 10;
              break;
            case 6:
              try {
                *awaited3 = core::getpromise((*b2));
                *caseIndex2 = 7;
                core::awake_upon((*awaited3), generator2);
                return core::ValueResult<std::shared_ptr<core::Empty>>::make(core::empty());
              } catch (core::TemperBubble const &) {
                *caseIndex2 = 4;
              }
              break;
            case 7:
              try {
                *t2 = core::get_promise_result_sync((*awaited3));
                *caseIndex2 = 5;
              } catch (core::TemperBubble const &) {
                *caseIndex2 = 4;
              }
              break;
            case 8:
              core::panic();
            case 9:
              try {
                *b4 = core::PromiseBuilderNs::make<core::Str>();
                core::complete((*b4), TEMPER_STR("Worth doing twice"));
                *awaited5 = core::getpromise((*b4));
                *caseIndex2 = 14;
                core::awake_upon((*awaited5), generator2);
                return core::ValueResult<std::shared_ptr<core::Empty>>::make(core::empty());
              } catch (core::TemperBubble const &) {
                *caseIndex2 = 12;
              }
              break;
            case 10:
              try {
                *b3 = core::PromiseBuilderNs::make<core::Str>();
                auto fn2 = [=]() -> std::shared_ptr<core::SafeGenerator<std::shared_ptr<core::Empty>>> {
                  std::shared_ptr<int32_t> caseIndex = std::make_shared<int32_t>(0);
                  auto convertedCoroutine = [=](std::shared_ptr<core::SafeGenerator<std::shared_ptr<core::Empty>>>) -> std::shared_ptr<core::GeneratorResult<std::shared_ptr<core::Empty>>> {
                    int32_t caseIndexLocal = (*caseIndex);
                    *caseIndex = -1;
                    switch (caseIndexLocal) {
                      case 0:
                        core::complete((*b3), TEMPER_STR("Completed asynchronously"));
                        return core::doneResult();
                      default:
                        return core::doneResult();
                    }
                  };
                  return core::safe_adapt_generator_fn<std::shared_ptr<core::Empty>>(convertedCoroutine);
                };
                core::async_run(fn2);
                *awaited4 = core::getpromise((*b3));
                *caseIndex2 = 11;
                core::awake_upon((*awaited4), generator2);
                return core::ValueResult<std::shared_ptr<core::Empty>>::make(core::empty());
              } catch (core::TemperBubble const &) {
                *caseIndex2 = 8;
              }
              break;
            case 11:
              try {
                *t3 = core::get_promise_result_sync((*awaited4));
                core::Console::log(temper_globals::console.value, (*t3));
                *caseIndex2 = 9;
              } catch (core::TemperBubble const &) {
                *caseIndex2 = 8;
              }
              break;
            case 12:
              core::panic();
            case 13:
              try {
                *a = core::PromiseBuilderNs::make<std::shared_ptr<core::Empty>>();
                *b5 = core::PromiseBuilderNs::make<std::shared_ptr<core::Empty>>();
                auto fn2 = [=]() -> std::shared_ptr<core::SafeGenerator<std::shared_ptr<core::Empty>>> {
                  std::shared_ptr<int32_t> caseIndex = std::make_shared<int32_t>(0);
                  std::shared_ptr<std::shared_ptr<core::Promise<std::shared_ptr<core::Empty>>>> awaited = std::make_shared<std::shared_ptr<core::Promise<std::shared_ptr<core::Empty>>>>(nullptr);
                  auto convertedCoroutine = [=](std::shared_ptr<core::SafeGenerator<std::shared_ptr<core::Empty>>> generator) -> std::shared_ptr<core::GeneratorResult<std::shared_ptr<core::Empty>>> {
                    while (true) {
                      int32_t caseIndexLocal = (*caseIndex);
                      *caseIndex = -1;
                      switch (caseIndexLocal) {
                        case 0:
                          try {
                            *awaited = core::getpromise((*b5));
                            *caseIndex = 3;
                            core::awake_upon((*awaited), generator);
                            return core::ValueResult<std::shared_ptr<core::Empty>>::make(core::empty());
                          } catch (core::TemperBubble const &) {
                            *caseIndex = 1;
                          }
                          break;
                        case 1:
                          core::panic();
                        case 2:
                          core::Console::log(temper_globals::console.value, TEMPER_STR("After"));
                          core::complete((*a), core::empty());
                          return core::doneResult();
                        case 3:
                          try {
                            core::get_promise_result_sync((*awaited));
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
                core::async_run(fn2);
                auto fn3 = [=]() -> std::shared_ptr<core::SafeGenerator<std::shared_ptr<core::Empty>>> {
                  std::shared_ptr<int32_t> caseIndex = std::make_shared<int32_t>(0);
                  auto convertedCoroutine = [=](std::shared_ptr<core::SafeGenerator<std::shared_ptr<core::Empty>>>) -> std::shared_ptr<core::GeneratorResult<std::shared_ptr<core::Empty>>> {
                    int32_t caseIndexLocal = (*caseIndex);
                    *caseIndex = -1;
                    switch (caseIndexLocal) {
                      case 0:
                        core::Console::log(temper_globals::console.value, TEMPER_STR("Before"));
                        core::complete((*b5), core::empty());
                        return core::doneResult();
                      default:
                        return core::doneResult();
                    }
                  };
                  return core::safe_adapt_generator_fn<std::shared_ptr<core::Empty>>(convertedCoroutine);
                };
                core::async_run(fn3);
                *awaited7 = core::getpromise((*a));
                *caseIndex2 = 18;
                core::awake_upon((*awaited7), generator2);
                return core::ValueResult<std::shared_ptr<core::Empty>>::make(core::empty());
              } catch (core::TemperBubble const &) {
                *caseIndex2 = 16;
              }
              break;
            case 14:
              try {
                *t4 = core::get_promise_result_sync((*awaited5));
                core::Console::log(temper_globals::console.value, (*t4));
                *awaited6 = core::getpromise((*b4));
                *caseIndex2 = 15;
                core::awake_upon((*awaited6), generator2);
                return core::ValueResult<std::shared_ptr<core::Empty>>::make(core::empty());
              } catch (core::TemperBubble const &) {
                *caseIndex2 = 12;
              }
              break;
            case 15:
              try {
                *t5 = core::get_promise_result_sync((*awaited6));
                core::Console::log(temper_globals::console.value, (*t5));
                *caseIndex2 = 13;
              } catch (core::TemperBubble const &) {
                *caseIndex2 = 12;
              }
              break;
            case 16:
              core::panic();
            case 17:
              try {
                core::Console::log(temper_globals::console.value, TEMPER_STR("What is your favourite colour?"));
                *b6 = core::PromiseBuilderNs::make<core::Str>();
                core::complete((*b6), TEMPER_STR("Blue"));
                core::complete((*b6), TEMPER_STR("No, yell..."));
                *awaited8 = core::getpromise((*b6));
                *caseIndex2 = 19;
                core::awake_upon((*awaited8), generator2);
                return core::ValueResult<std::shared_ptr<core::Empty>>::make(core::empty());
              } catch (core::TemperBubble const &) {
                *caseIndex2 = -1;
              }
              break;
            case 18:
              try {
                core::get_promise_result_sync((*awaited7));
                *caseIndex2 = 17;
              } catch (core::TemperBubble const &) {
                *caseIndex2 = 16;
              }
              break;
            case 19:
              try {
                *t6 = core::get_promise_result_sync((*awaited8));
                core::Console::log(temper_globals::console.value, (*t6));
                *caseIndex2 = -1;
              } catch (core::TemperBubble const &) {
                *caseIndex2 = -1;
              }
              break;
            default:
              return core::doneResult();
          }
        }
      };
      return core::safe_adapt_generator_fn<std::shared_ptr<core::Empty>>(convertedCoroutine2);
    };
    core::async_run(fn);
  });
}

}  // namespace async
