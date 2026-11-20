// init.hpp
#pragma once

#include <temper-core/base.hpp>

namespace async_waiters {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

}  // namespace async_waiters

// init.cpp
#include <async-waiters/init.hpp>

#include <temper-core/console.hpp>
#include <temper-core/generator.hpp>
#include <temper-core/promise.hpp>

namespace async_waiters {

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
      std::shared_ptr<std::shared_ptr<core::PromiseBuilder<int32_t>>> b = std::make_shared<std::shared_ptr<core::PromiseBuilder<int32_t>>>(nullptr);
      std::shared_ptr<std::shared_ptr<core::PromiseBuilder<int32_t>>> firstDone = std::make_shared<std::shared_ptr<core::PromiseBuilder<int32_t>>>(nullptr);
      std::shared_ptr<std::shared_ptr<core::PromiseBuilder<int32_t>>> secondDone = std::make_shared<std::shared_ptr<core::PromiseBuilder<int32_t>>>(nullptr);
      std::shared_ptr<int32_t> first = std::make_shared<int32_t>(0);
      std::shared_ptr<int32_t> second = std::make_shared<int32_t>(0);
      std::shared_ptr<std::shared_ptr<core::PromiseBuilder<int32_t>>> b2 = std::make_shared<std::shared_ptr<core::PromiseBuilder<int32_t>>>(nullptr);
      std::shared_ptr<std::shared_ptr<core::PromiseBuilder<int32_t>>> firstDone2 = std::make_shared<std::shared_ptr<core::PromiseBuilder<int32_t>>>(nullptr);
      std::shared_ptr<std::shared_ptr<core::PromiseBuilder<int32_t>>> secondDone2 = std::make_shared<std::shared_ptr<core::PromiseBuilder<int32_t>>>(nullptr);
      std::shared_ptr<int32_t> first2 = std::make_shared<int32_t>(0);
      std::shared_ptr<int32_t> second2 = std::make_shared<int32_t>(0);
      std::shared_ptr<std::shared_ptr<core::Promise<int32_t>>> awaited2 = std::make_shared<std::shared_ptr<core::Promise<int32_t>>>(nullptr);
      std::shared_ptr<std::shared_ptr<core::Promise<int32_t>>> awaited3 = std::make_shared<std::shared_ptr<core::Promise<int32_t>>>(nullptr);
      std::shared_ptr<std::shared_ptr<core::Promise<int32_t>>> awaited4 = std::make_shared<std::shared_ptr<core::Promise<int32_t>>>(nullptr);
      std::shared_ptr<std::shared_ptr<core::Promise<int32_t>>> awaited5 = std::make_shared<std::shared_ptr<core::Promise<int32_t>>>(nullptr);
      auto convertedCoroutine2 = [=](std::shared_ptr<core::SafeGenerator<std::shared_ptr<core::Empty>>> generator2) -> std::shared_ptr<core::GeneratorResult<std::shared_ptr<core::Empty>>> {
        while (true) {
          int32_t caseIndexLocal2 = (*caseIndex2);
          *caseIndex2 = -1;
          switch (caseIndexLocal2) {
            case 0:
              try {
                *b = core::PromiseBuilderNs::make<int32_t>();
                *firstDone = core::PromiseBuilderNs::make<int32_t>();
                *secondDone = core::PromiseBuilderNs::make<int32_t>();
                auto fn2 = [=]() -> std::shared_ptr<core::SafeGenerator<std::shared_ptr<core::Empty>>> {
                  std::shared_ptr<int32_t> caseIndex = std::make_shared<int32_t>(0);
                  std::shared_ptr<int32_t> v = std::make_shared<int32_t>(0);
                  std::shared_ptr<std::shared_ptr<core::Promise<int32_t>>> awaited = std::make_shared<std::shared_ptr<core::Promise<int32_t>>>(nullptr);
                  auto convertedCoroutine = [=](std::shared_ptr<core::SafeGenerator<std::shared_ptr<core::Empty>>> generator) -> std::shared_ptr<core::GeneratorResult<std::shared_ptr<core::Empty>>> {
                    while (true) {
                      int32_t caseIndexLocal = (*caseIndex);
                      *caseIndex = -1;
                      switch (caseIndexLocal) {
                        case 0:
                          try {
                            *awaited = core::getpromise((*b));
                            *caseIndex = 3;
                            core::awake_upon((*awaited), generator);
                            return core::ValueResult<std::shared_ptr<core::Empty>>::make(core::empty());
                          } catch (core::TemperBubble const &) {
                            *caseIndex = 1;
                          }
                          break;
                        case 1:
                          *v = -1;
                          *caseIndex = 2;
                          break;
                        case 2:
                          core::complete((*firstDone), (*v));
                          return core::doneResult();
                        case 3:
                          try {
                            *v = core::get_promise_result_sync((*awaited));
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
                  std::shared_ptr<int32_t> v = std::make_shared<int32_t>(0);
                  std::shared_ptr<std::shared_ptr<core::Promise<int32_t>>> awaited = std::make_shared<std::shared_ptr<core::Promise<int32_t>>>(nullptr);
                  auto convertedCoroutine = [=](std::shared_ptr<core::SafeGenerator<std::shared_ptr<core::Empty>>> generator) -> std::shared_ptr<core::GeneratorResult<std::shared_ptr<core::Empty>>> {
                    while (true) {
                      int32_t caseIndexLocal = (*caseIndex);
                      *caseIndex = -1;
                      switch (caseIndexLocal) {
                        case 0:
                          try {
                            *awaited = core::getpromise((*b));
                            *caseIndex = 3;
                            core::awake_upon((*awaited), generator);
                            return core::ValueResult<std::shared_ptr<core::Empty>>::make(core::empty());
                          } catch (core::TemperBubble const &) {
                            *caseIndex = 1;
                          }
                          break;
                        case 1:
                          *v = -1;
                          *caseIndex = 2;
                          break;
                        case 2:
                          core::complete((*secondDone), (*v));
                          return core::doneResult();
                        case 3:
                          try {
                            *v = core::get_promise_result_sync((*awaited));
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
                core::async_run(fn3);
                auto fn4 = [=]() -> std::shared_ptr<core::SafeGenerator<std::shared_ptr<core::Empty>>> {
                  std::shared_ptr<int32_t> caseIndex = std::make_shared<int32_t>(0);
                  auto convertedCoroutine = [=](std::shared_ptr<core::SafeGenerator<std::shared_ptr<core::Empty>>>) -> std::shared_ptr<core::GeneratorResult<std::shared_ptr<core::Empty>>> {
                    int32_t caseIndexLocal = (*caseIndex);
                    *caseIndex = -1;
                    switch (caseIndexLocal) {
                      case 0:
                        core::complete((*b), 7);
                        return core::doneResult();
                      default:
                        return core::doneResult();
                    }
                  };
                  return core::safe_adapt_generator_fn<std::shared_ptr<core::Empty>>(convertedCoroutine);
                };
                core::async_run(fn4);
                *awaited2 = core::getpromise((*firstDone));
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
              try {
                *b2 = core::PromiseBuilderNs::make<int32_t>();
                *firstDone2 = core::PromiseBuilderNs::make<int32_t>();
                *secondDone2 = core::PromiseBuilderNs::make<int32_t>();
                auto fn2 = [=]() -> std::shared_ptr<core::SafeGenerator<std::shared_ptr<core::Empty>>> {
                  std::shared_ptr<int32_t> caseIndex = std::make_shared<int32_t>(0);
                  std::shared_ptr<int32_t> v = std::make_shared<int32_t>(0);
                  std::shared_ptr<std::shared_ptr<core::Promise<int32_t>>> awaited = std::make_shared<std::shared_ptr<core::Promise<int32_t>>>(nullptr);
                  auto convertedCoroutine = [=](std::shared_ptr<core::SafeGenerator<std::shared_ptr<core::Empty>>> generator) -> std::shared_ptr<core::GeneratorResult<std::shared_ptr<core::Empty>>> {
                    while (true) {
                      int32_t caseIndexLocal = (*caseIndex);
                      *caseIndex = -1;
                      switch (caseIndexLocal) {
                        case 0:
                          try {
                            *awaited = core::getpromise((*b2));
                            *caseIndex = 3;
                            core::awake_upon((*awaited), generator);
                            return core::ValueResult<std::shared_ptr<core::Empty>>::make(core::empty());
                          } catch (core::TemperBubble const &) {
                            *caseIndex = 1;
                          }
                          break;
                        case 1:
                          *v = -1;
                          *caseIndex = 2;
                          break;
                        case 2:
                          core::complete((*firstDone2), (*v));
                          return core::doneResult();
                        case 3:
                          try {
                            *v = core::get_promise_result_sync((*awaited));
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
                  std::shared_ptr<int32_t> v = std::make_shared<int32_t>(0);
                  std::shared_ptr<std::shared_ptr<core::Promise<int32_t>>> awaited = std::make_shared<std::shared_ptr<core::Promise<int32_t>>>(nullptr);
                  auto convertedCoroutine = [=](std::shared_ptr<core::SafeGenerator<std::shared_ptr<core::Empty>>> generator) -> std::shared_ptr<core::GeneratorResult<std::shared_ptr<core::Empty>>> {
                    while (true) {
                      int32_t caseIndexLocal = (*caseIndex);
                      *caseIndex = -1;
                      switch (caseIndexLocal) {
                        case 0:
                          try {
                            *awaited = core::getpromise((*b2));
                            *caseIndex = 3;
                            core::awake_upon((*awaited), generator);
                            return core::ValueResult<std::shared_ptr<core::Empty>>::make(core::empty());
                          } catch (core::TemperBubble const &) {
                            *caseIndex = 1;
                          }
                          break;
                        case 1:
                          *v = -1;
                          *caseIndex = 2;
                          break;
                        case 2:
                          core::complete((*secondDone2), (*v));
                          return core::doneResult();
                        case 3:
                          try {
                            *v = core::get_promise_result_sync((*awaited));
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
                core::async_run(fn3);
                auto fn4 = [=]() -> std::shared_ptr<core::SafeGenerator<std::shared_ptr<core::Empty>>> {
                  std::shared_ptr<int32_t> caseIndex = std::make_shared<int32_t>(0);
                  auto convertedCoroutine = [=](std::shared_ptr<core::SafeGenerator<std::shared_ptr<core::Empty>>>) -> std::shared_ptr<core::GeneratorResult<std::shared_ptr<core::Empty>>> {
                    int32_t caseIndexLocal = (*caseIndex);
                    *caseIndex = -1;
                    switch (caseIndexLocal) {
                      case 0:
                        core::breakpromise((*b2));
                        return core::doneResult();
                      default:
                        return core::doneResult();
                    }
                  };
                  return core::safe_adapt_generator_fn<std::shared_ptr<core::Empty>>(convertedCoroutine);
                };
                core::async_run(fn4);
                *awaited4 = core::getpromise((*firstDone2));
                *caseIndex2 = 6;
                core::awake_upon((*awaited4), generator2);
                return core::ValueResult<std::shared_ptr<core::Empty>>::make(core::empty());
              } catch (core::TemperBubble const &) {
                *caseIndex2 = 5;
              }
              break;
            case 3:
              try {
                *first = core::get_promise_result_sync((*awaited2));
                *awaited3 = core::getpromise((*secondDone));
                *caseIndex2 = 4;
                core::awake_upon((*awaited3), generator2);
                return core::ValueResult<std::shared_ptr<core::Empty>>::make(core::empty());
              } catch (core::TemperBubble const &) {
                *caseIndex2 = 1;
              }
              break;
            case 4:
              try {
                *second = core::get_promise_result_sync((*awaited3));
                core::Console::log(temper_globals::console.value, "after complete: first got " + std::to_string((*first)) + ", second got " + std::to_string((*second)));
                *caseIndex2 = 2;
              } catch (core::TemperBubble const &) {
                *caseIndex2 = 1;
              }
              break;
            case 5:
              core::panic();
            case 6:
              try {
                *first2 = core::get_promise_result_sync((*awaited4));
                *awaited5 = core::getpromise((*secondDone2));
                *caseIndex2 = 7;
                core::awake_upon((*awaited5), generator2);
                return core::ValueResult<std::shared_ptr<core::Empty>>::make(core::empty());
              } catch (core::TemperBubble const &) {
                *caseIndex2 = 5;
              }
              break;
            case 7:
              try {
                *second2 = core::get_promise_result_sync((*awaited5));
                core::Console::log(temper_globals::console.value, "after breakPromise: first got " + std::to_string((*first2)) + ", second got " + std::to_string((*second2)));
                *caseIndex2 = -1;
              } catch (core::TemperBubble const &) {
                *caseIndex2 = 5;
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

}  // namespace async_waiters
