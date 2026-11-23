// init.hpp
#pragma once

#include <temper-core/core.hpp>

namespace panic_causes_panic_in_an_async_block {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

}  // namespace panic_causes_panic_in_an_async_block

// init.cpp
#include <panic-causes-panic-in-an-async-block/init.hpp>

namespace panic_causes_panic_in_an_async_block {

namespace core = temper::core;

namespace temper_globals {

static core::GlobalSlot<std::shared_ptr<core::Console::Type>> console;

}  // namespace temper_globals

static int32_t third(std::shared_ptr<std::vector<int32_t>> const & ls) {
  return core::List::get<int32_t>(ls, 2);
}

void global_init_init() {
  core::init_once([] {
    temper_globals::console.construct();
    temper_globals::console.value = core::Console::get_console();
    core::Console::log(temper_globals::console.value, TEMPER_STR("before"));
    auto fn = []() -> std::shared_ptr<core::SafeGenerator<std::shared_ptr<core::Empty>>> {
      std::shared_ptr<int32_t> caseIndex = std::make_shared<int32_t>(0);
      auto convertedCoroutine = [=](std::shared_ptr<core::SafeGenerator<std::shared_ptr<core::Empty>>>) -> std::shared_ptr<core::GeneratorResult<std::shared_ptr<core::Empty>>> {
        int32_t caseIndexLocal = (*caseIndex);
        *caseIndex = -1;
        switch (caseIndexLocal) {
          case 0:
            core::Console::log(temper_globals::console.value, TEMPER_STR("in async"));
            panic_causes_panic_in_an_async_block::third(core::List::make<int32_t>(1, 2));
            return core::doneResult();
          default:
            return core::doneResult();
        }
      };
      return core::safe_adapt_generator_fn<std::shared_ptr<core::Empty>>(convertedCoroutine);
    };
    core::async_run(fn);
  });
}

}  // namespace panic_causes_panic_in_an_async_block
