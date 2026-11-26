// init.hpp
#pragma once

#include <temper-core/core.hpp>

namespace async {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

}  // namespace async

// init.cpp
#include <async/init.hpp>

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
      std::shared_ptr<std::shared_ptr<core::PromiseBuilder<core::Str>>> b5 = std::make_shared<std::shared_ptr<core::PromiseBuilder<core::Str>>>(nullptr);
      std::shared_ptr<std::shared_ptr<core::PromiseBuilder<core::Str>>> b6 = std::make_shared<std::shared_ptr<core::PromiseBuilder<core::Str>>>(nullptr);
      std::shared_ptr<std::shared_ptr<core::PromiseBuilder<std::shared_ptr<core::Empty>>>> a = std::make_shared<std::shared_ptr<core::PromiseBuilder<std::shared_ptr<core::Empty>>>>(nullptr);
      std::shared_ptr<std::shared_ptr<core::PromiseBuilder<std::shared_ptr<core::Empty>>>> b7 = std::make_shared<std::shared_ptr<core::PromiseBuilder<std::shared_ptr<core::Empty>>>>(nullptr);
      std::shared_ptr<std::shared_ptr<core::PromiseBuilder<core::Str>>> b8 = std::make_shared<std::shared_ptr<core::PromiseBuilder<core::Str>>>(nullptr);
      std::shared_ptr<core::Str> t = std::make_shared<core::Str>(core::Str());
      std::shared_ptr<std::shared_ptr<core::Promise<core::Str>>> awaited2 = std::make_shared<std::shared_ptr<core::Promise<core::Str>>>(nullptr);
      std::shared_ptr<core::Str> t2 = std::make_shared<core::Str>(core::Str());
      std::shared_ptr<std::shared_ptr<core::Promise<core::Str>>> awaited3 = std::make_shared<std::shared_ptr<core::Promise<core::Str>>>(nullptr);
      std::shared_ptr<core::Str> t3 = std::make_shared<core::Str>(core::Str());
      std::shared_ptr<std::shared_ptr<core::BrokenPromise>> e = std::make_shared<std::shared_ptr<core::BrokenPromise>>(nullptr);
      std::shared_ptr<std::shared_ptr<core::Promise<core::Str>>> awaited4 = std::make_shared<std::shared_ptr<core::Promise<core::Str>>>(nullptr);
      std::shared_ptr<core::Str> t4 = std::make_shared<core::Str>(core::Str());
      std::shared_ptr<std::shared_ptr<core::BrokenPromise>> e2 = std::make_shared<std::shared_ptr<core::BrokenPromise>>(nullptr);
      std::shared_ptr<std::shared_ptr<core::Promise<core::Str>>> awaited5 = std::make_shared<std::shared_ptr<core::Promise<core::Str>>>(nullptr);
      std::shared_ptr<core::Str> t5 = std::make_shared<core::Str>(core::Str());
      std::shared_ptr<std::shared_ptr<core::Promise<core::Str>>> awaited6 = std::make_shared<std::shared_ptr<core::Promise<core::Str>>>(nullptr);
      std::shared_ptr<core::Str> t6 = std::make_shared<core::Str>(core::Str());
      std::shared_ptr<std::shared_ptr<core::Promise<core::Str>>> awaited7 = std::make_shared<std::shared_ptr<core::Promise<core::Str>>>(nullptr);
      std::shared_ptr<core::Str> t7 = std::make_shared<core::Str>(core::Str());
      std::shared_ptr<std::shared_ptr<core::Promise<core::Str>>> awaited8 = std::make_shared<std::shared_ptr<core::Promise<core::Str>>>(nullptr);
      std::shared_ptr<std::shared_ptr<core::Promise<std::shared_ptr<core::Empty>>>> awaited9 = std::make_shared<std::shared_ptr<core::Promise<std::shared_ptr<core::Empty>>>>(nullptr);
      std::shared_ptr<core::Str> t8 = std::make_shared<core::Str>(core::Str());
      std::shared_ptr<std::shared_ptr<core::Promise<core::Str>>> awaited10 = std::make_shared<std::shared_ptr<core::Promise<core::Str>>>(nullptr);
      auto convertedCoroutine2 = [=](std::shared_ptr<core::SafeGenerator<std::shared_ptr<core::Empty>>> generator2) -> std::shared_ptr<core::GeneratorResult<std::shared_ptr<core::Empty>>> {
        while (true) {
          int32_t caseIndexLocal2 = (*caseIndex2);
          *caseIndex2 = -1;
          switch (caseIndexLocal2) {
            case 0:
              *b = core::PromiseBuilderNs::make<core::Str>();
              core::complete((*b), TEMPER_STR("Completed synchronously"));
              *awaited2 = core::getpromise((*b));
              *caseIndex2 = 3;
              core::awake_upon((*awaited2), generator2);
              return core::ValueResult<std::shared_ptr<core::Empty>>::make(core::empty());
            case 1:
              core::panic();
            case 2:
              *b2 = core::PromiseBuilderNs::make<core::Str>();
              core::breakpromise((*b2));
              *caseIndex2 = 6;
              break;
            case 3: {
              auto Result = core::get_promise_result_sync((*awaited2));
              if (Result.has_value()) {
                *t = *Result;
                core::Console::log(temper_globals::console.value, (*t));
                *caseIndex2 = 2;
              } else {
                *caseIndex2 = 1;
              }
              break;
            }
            case 4:
              *t2 = TEMPER_STR("Broken synchronously");
              *caseIndex2 = 5;
              break;
            case 5:
              core::Console::log(temper_globals::console.value, (*t2));
              *b3 = core::PromiseBuilderNs::make<core::Str>();
              core::breakpromise((*b3));
              *caseIndex2 = 10;
              break;
            case 6:
              *awaited3 = core::getpromise((*b2));
              *caseIndex2 = 7;
              core::awake_upon((*awaited3), generator2);
              return core::ValueResult<std::shared_ptr<core::Empty>>::make(core::empty());
            case 7: {
              auto Result2 = core::get_promise_result_sync((*awaited3));
              if (Result2.has_value()) {
                *t2 = *Result2;
                *caseIndex2 = 5;
              } else {
                *caseIndex2 = 4;
              }
              break;
            }
            case 8:
              *t3 = "Caught " + (*e)->get_message();
              *caseIndex2 = 9;
              break;
            case 9: {
              core::Console::log(temper_globals::console.value, (*t3));
              *b4 = core::PromiseBuilderNs::make<core::Str>();
              auto fn2 = [=]() -> std::shared_ptr<core::SafeGenerator<std::shared_ptr<core::Empty>>> {
                std::shared_ptr<int32_t> caseIndex = std::make_shared<int32_t>(0);
                auto convertedCoroutine = [=](std::shared_ptr<core::SafeGenerator<std::shared_ptr<core::Empty>>>) -> std::shared_ptr<core::GeneratorResult<std::shared_ptr<core::Empty>>> {
                  int32_t caseIndexLocal = (*caseIndex);
                  *caseIndex = -1;
                  switch (caseIndexLocal) {
                    case 0:
                      core::breakpromise((*b4));
                      return core::doneResult();
                    default:
                      return core::doneResult();
                  }
                };
                return core::safe_adapt_generator_fn<std::shared_ptr<core::Empty>>(convertedCoroutine);
              };
              core::async_run(fn2);
              *caseIndex2 = 14;
              break;
            }
            case 10:
              *awaited4 = core::getpromise((*b3));
              *caseIndex2 = 11;
              core::awake_upon((*awaited4), generator2);
              return core::ValueResult<std::shared_ptr<core::Empty>>::make(core::empty());
            case 11: {
              auto Result3 = core::get_promise_result_sync((*awaited4));
              if (Result3.has_value()) {
                *t3 = *Result3;
                *caseIndex2 = 9;
              } else {
                std::shared_ptr<core::BrokenPromise> failure = Result3.error();
                *e = failure;
                *caseIndex2 = 8;
              }
              break;
            }
            case 12:
              *t4 = "Caught " + (*e2)->get_message() + " later";
              *caseIndex2 = 13;
              break;
            case 13:
              core::Console::log(temper_globals::console.value, (*t4));
              *caseIndex2 = 18;
              break;
            case 14:
              *awaited5 = core::getpromise((*b4));
              *caseIndex2 = 15;
              core::awake_upon((*awaited5), generator2);
              return core::ValueResult<std::shared_ptr<core::Empty>>::make(core::empty());
            case 15: {
              auto Result4 = core::get_promise_result_sync((*awaited5));
              if (Result4.has_value()) {
                *t4 = *Result4;
                *caseIndex2 = 13;
              } else {
                std::shared_ptr<core::BrokenPromise> failure = Result4.error();
                *e2 = failure;
                *caseIndex2 = 12;
              }
              break;
            }
            case 16:
              core::panic();
            case 17:
              *b6 = core::PromiseBuilderNs::make<core::Str>();
              core::complete((*b6), TEMPER_STR("Worth doing twice"));
              *awaited7 = core::getpromise((*b6));
              *caseIndex2 = 22;
              core::awake_upon((*awaited7), generator2);
              return core::ValueResult<std::shared_ptr<core::Empty>>::make(core::empty());
            case 18: {
              *b5 = core::PromiseBuilderNs::make<core::Str>();
              auto fn2 = [=]() -> std::shared_ptr<core::SafeGenerator<std::shared_ptr<core::Empty>>> {
                std::shared_ptr<int32_t> caseIndex = std::make_shared<int32_t>(0);
                auto convertedCoroutine = [=](std::shared_ptr<core::SafeGenerator<std::shared_ptr<core::Empty>>>) -> std::shared_ptr<core::GeneratorResult<std::shared_ptr<core::Empty>>> {
                  int32_t caseIndexLocal = (*caseIndex);
                  *caseIndex = -1;
                  switch (caseIndexLocal) {
                    case 0:
                      core::complete((*b5), TEMPER_STR("Completed asynchronously"));
                      return core::doneResult();
                    default:
                      return core::doneResult();
                  }
                };
                return core::safe_adapt_generator_fn<std::shared_ptr<core::Empty>>(convertedCoroutine);
              };
              core::async_run(fn2);
              *awaited6 = core::getpromise((*b5));
              *caseIndex2 = 19;
              core::awake_upon((*awaited6), generator2);
              return core::ValueResult<std::shared_ptr<core::Empty>>::make(core::empty());
            }
            case 19: {
              auto Result5 = core::get_promise_result_sync((*awaited6));
              if (Result5.has_value()) {
                *t5 = *Result5;
                core::Console::log(temper_globals::console.value, (*t5));
                *caseIndex2 = 17;
              } else {
                *caseIndex2 = 16;
              }
              break;
            }
            case 20:
              core::panic();
            case 21: {
              *a = core::PromiseBuilderNs::make<std::shared_ptr<core::Empty>>();
              *b7 = core::PromiseBuilderNs::make<std::shared_ptr<core::Empty>>();
              auto fn2 = [=]() -> std::shared_ptr<core::SafeGenerator<std::shared_ptr<core::Empty>>> {
                std::shared_ptr<int32_t> caseIndex = std::make_shared<int32_t>(0);
                std::shared_ptr<std::shared_ptr<core::Promise<std::shared_ptr<core::Empty>>>> awaited = std::make_shared<std::shared_ptr<core::Promise<std::shared_ptr<core::Empty>>>>(nullptr);
                auto convertedCoroutine = [=](std::shared_ptr<core::SafeGenerator<std::shared_ptr<core::Empty>>> generator) -> std::shared_ptr<core::GeneratorResult<std::shared_ptr<core::Empty>>> {
                  while (true) {
                    int32_t caseIndexLocal = (*caseIndex);
                    *caseIndex = -1;
                    switch (caseIndexLocal) {
                      case 0:
                        *awaited = core::getpromise((*b7));
                        *caseIndex = 3;
                        core::awake_upon((*awaited), generator);
                        return core::ValueResult<std::shared_ptr<core::Empty>>::make(core::empty());
                      case 1:
                        core::panic();
                      case 2:
                        core::Console::log(temper_globals::console.value, TEMPER_STR("After"));
                        core::complete((*a), core::empty());
                        return core::doneResult();
                      case 3: {
                        auto Result6 = core::get_promise_result_sync((*awaited));
                        if (Result6.has_value()) {
                          *caseIndex = 2;
                        } else {
                          *caseIndex = 1;
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
              core::async_run(fn2);
              auto fn3 = [=]() -> std::shared_ptr<core::SafeGenerator<std::shared_ptr<core::Empty>>> {
                std::shared_ptr<int32_t> caseIndex = std::make_shared<int32_t>(0);
                auto convertedCoroutine = [=](std::shared_ptr<core::SafeGenerator<std::shared_ptr<core::Empty>>>) -> std::shared_ptr<core::GeneratorResult<std::shared_ptr<core::Empty>>> {
                  int32_t caseIndexLocal = (*caseIndex);
                  *caseIndex = -1;
                  switch (caseIndexLocal) {
                    case 0:
                      core::Console::log(temper_globals::console.value, TEMPER_STR("Before"));
                      core::complete((*b7), core::empty());
                      return core::doneResult();
                    default:
                      return core::doneResult();
                  }
                };
                return core::safe_adapt_generator_fn<std::shared_ptr<core::Empty>>(convertedCoroutine);
              };
              core::async_run(fn3);
              *awaited9 = core::getpromise((*a));
              *caseIndex2 = 26;
              core::awake_upon((*awaited9), generator2);
              return core::ValueResult<std::shared_ptr<core::Empty>>::make(core::empty());
            }
            case 22: {
              auto Result7 = core::get_promise_result_sync((*awaited7));
              if (Result7.has_value()) {
                *t6 = *Result7;
                core::Console::log(temper_globals::console.value, (*t6));
                *awaited8 = core::getpromise((*b6));
                *caseIndex2 = 23;
                core::awake_upon((*awaited8), generator2);
                return core::ValueResult<std::shared_ptr<core::Empty>>::make(core::empty());
              } else {
                *caseIndex2 = 20;
              }
              break;
            }
            case 23: {
              auto Result8 = core::get_promise_result_sync((*awaited8));
              if (Result8.has_value()) {
                *t7 = *Result8;
                core::Console::log(temper_globals::console.value, (*t7));
                *caseIndex2 = 21;
              } else {
                *caseIndex2 = 20;
              }
              break;
            }
            case 24:
              core::panic();
            case 25:
              core::Console::log(temper_globals::console.value, TEMPER_STR("What is your favourite colour?"));
              *b8 = core::PromiseBuilderNs::make<core::Str>();
              core::complete((*b8), TEMPER_STR("Blue"));
              core::complete((*b8), TEMPER_STR("No, yell..."));
              *awaited10 = core::getpromise((*b8));
              *caseIndex2 = 27;
              core::awake_upon((*awaited10), generator2);
              return core::ValueResult<std::shared_ptr<core::Empty>>::make(core::empty());
            case 26: {
              auto Result9 = core::get_promise_result_sync((*awaited9));
              if (Result9.has_value()) {
                *caseIndex2 = 25;
              } else {
                *caseIndex2 = 24;
              }
              break;
            }
            case 27: {
              auto Result10 = core::get_promise_result_sync((*awaited10));
              if (Result10.has_value()) {
                *t8 = *Result10;
                core::Console::log(temper_globals::console.value, (*t8));
                *caseIndex2 = -1;
              } else {
                *caseIndex2 = -1;
              }
              break;
            }
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
