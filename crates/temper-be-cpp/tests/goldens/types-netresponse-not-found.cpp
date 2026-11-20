// init.hpp
#pragma once

#include <temper-core/base.hpp>
#include <std/net.hpp>

namespace netresponse_not_found {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

}  // namespace netresponse_not_found

// init.cpp
#include <netresponse-not-found/init.hpp>

#include <temper-core/console.hpp>
#include <temper-core/generator.hpp>
#include <temper-core/promise.hpp>

namespace netresponse_not_found {

namespace core = temper::core;

namespace temper_globals {

static core::GlobalSlot<std::shared_ptr<core::Console::Type>> console;
static core::GlobalSlot<core::Str> url;

}  // namespace temper_globals

void global_init_init() {
  core::init_once([] {
    temper_globals::console.construct();
    temper_globals::url.construct();
    temper_std::global_init_net();
    temper_globals::console.value = core::Console::get_console();
    temper_globals::url.value = "http://127.0.0.1:" + std::to_string(28461) + "/missing";
    auto fn = []() -> std::shared_ptr<core::SafeGenerator<std::shared_ptr<core::Empty>>> {
      std::shared_ptr<int32_t> caseIndex = std::make_shared<int32_t>(0);
      std::shared_ptr<std::shared_ptr<temper_std::NetResponse>> resp = std::make_shared<std::shared_ptr<temper_std::NetResponse>>(nullptr);
      std::shared_ptr<core::Str> body = std::make_shared<core::Str>(core::Str());
      std::shared_ptr<core::NullableParam<core::Str>> subject = std::make_shared<core::NullableParam<core::Str>>(nullptr);
      std::shared_ptr<std::shared_ptr<core::Promise<std::shared_ptr<temper_std::NetResponse>>>> awaited = std::make_shared<std::shared_ptr<core::Promise<std::shared_ptr<temper_std::NetResponse>>>>(nullptr);
      std::shared_ptr<std::shared_ptr<core::Promise<core::NullableParam<core::Str>>>> awaited2 = std::make_shared<std::shared_ptr<core::Promise<core::NullableParam<core::Str>>>>(nullptr);
      std::shared_ptr<std::shared_ptr<core::Promise<std::shared_ptr<temper_std::NetResponse>>>> awaited3 = std::make_shared<std::shared_ptr<core::Promise<std::shared_ptr<temper_std::NetResponse>>>>(nullptr);
      auto convertedCoroutine = [=](std::shared_ptr<core::SafeGenerator<std::shared_ptr<core::Empty>>> generator) -> std::shared_ptr<core::GeneratorResult<std::shared_ptr<core::Empty>>> {
        while (true) {
          int32_t caseIndexLocal = (*caseIndex);
          *caseIndex = -1;
          switch (caseIndexLocal) {
            case 0:
              try {
                *awaited = temper_std::NetRequest::make(temper_globals::url.value)->send();
                *caseIndex = 3;
                core::awake_upon((*awaited), generator);
                return core::ValueResult<std::shared_ptr<core::Empty>>::make(core::empty());
              } catch (core::TemperBubble const &) {
                *caseIndex = 1;
              }
              break;
            case 1:
              core::Console::log(temper_globals::console.value, TEMPER_STR("broken promise"));
              *caseIndex = 2;
              break;
            case 2:
              try {
                *awaited3 = temper_std::NetRequest::make(TEMPER_STR("http://127.0.0.1:1/"))->send();
                *caseIndex = 6;
                core::awake_upon((*awaited3), generator);
                return core::ValueResult<std::shared_ptr<core::Empty>>::make(core::empty());
              } catch (core::TemperBubble const &) {
                *caseIndex = 5;
              }
              break;
            case 3:
              try {
                *resp = core::get_promise_result_sync((*awaited));
                core::Console::log(temper_globals::console.value, "status " + std::to_string((*resp)->get_status()));
                *awaited2 = (*resp)->get_bodyContent();
                *caseIndex = 4;
                core::awake_upon((*awaited2), generator);
                return core::ValueResult<std::shared_ptr<core::Empty>>::make(core::empty());
              } catch (core::TemperBubble const &) {
                *caseIndex = 1;
              }
              break;
            case 4:
              try {
                *subject = core::get_promise_result_sync((*awaited2));
                *body = (*subject) == nullptr ? TEMPER_STR("missing") : (*subject).value;
                core::Console::log(temper_globals::console.value, "body " + (*body));
                *caseIndex = 2;
              } catch (core::TemperBubble const &) {
                *caseIndex = 1;
              }
              break;
            case 5:
              core::Console::log(temper_globals::console.value, TEMPER_STR("no response from port 1"));
              *caseIndex = -1;
              break;
            case 6:
              try {
                core::get_promise_result_sync((*awaited3));
                core::Console::log(temper_globals::console.value, TEMPER_STR("port 1 answered"));
                *caseIndex = -1;
              } catch (core::TemperBubble const &) {
                *caseIndex = 5;
              }
              break;
            default:
              return core::doneResult();
          }
        }
      };
      return core::safe_adapt_generator_fn<std::shared_ptr<core::Empty>>(convertedCoroutine);
    };
    core::async_run(fn);
  });
}

}  // namespace netresponse_not_found
