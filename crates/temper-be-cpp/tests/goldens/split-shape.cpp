#include <split-shape/init.hpp>

namespace split_shape {

namespace core = temper::core;

void global_init_init() {
  core::init_once([] {
    std::shared_ptr<core::Console::Type> console = core::Console::get_console();
    std::shared_ptr<std::vector<core::Str>> items = core::List::make_parts<core::Str>([&]() -> std::shared_ptr<std::vector<core::Str>> { return core::List::make<core::Str>(TEMPER_STR("item 0 é✓𝄞"), TEMPER_STR("item 1 é✓𝄞"), TEMPER_STR("item 2 é✓𝄞"), TEMPER_STR("item 3 é✓𝄞")); }(), [&]() -> std::shared_ptr<std::vector<core::Str>> { return core::List::make<core::Str>(TEMPER_STR("item 4 é✓𝄞"), TEMPER_STR("item 5 é✓𝄞"), TEMPER_STR("item 6 é✓𝄞"), TEMPER_STR("item 7 é✓𝄞")); }(), [&]() -> std::shared_ptr<std::vector<core::Str>> { return core::List::make<core::Str>(TEMPER_STR("item 8 é✓𝄞"), TEMPER_STR("item 9 é✓𝄞")); }());
    std::shared_ptr<core::Mapped::Ordered<core::Str, core::Str>> table = core::Map::make<core::Str, core::Str>(core::List::make_parts<std::shared_ptr<core::Pair<core::Str, core::Str>>>([&]() -> std::shared_ptr<std::vector<std::shared_ptr<core::Pair<core::Str, core::Str>>>> { return core::List::make<std::shared_ptr<core::Pair<core::Str, core::Str>>>(core::PairFactory::make<core::Str, core::Str>(TEMPER_STR("k0"), TEMPER_STR("item 0 é✓𝄞")), core::PairFactory::make<core::Str, core::Str>(TEMPER_STR("k1"), TEMPER_STR("item 1 é✓𝄞")), core::PairFactory::make<core::Str, core::Str>(TEMPER_STR("k2"), TEMPER_STR("item 2 é✓𝄞")), core::PairFactory::make<core::Str, core::Str>(TEMPER_STR("k3"), TEMPER_STR("item 3 é✓𝄞"))); }(), [&]() -> std::shared_ptr<std::vector<std::shared_ptr<core::Pair<core::Str, core::Str>>>> { return core::List::make<std::shared_ptr<core::Pair<core::Str, core::Str>>>(core::PairFactory::make<core::Str, core::Str>(TEMPER_STR("k4"), TEMPER_STR("item 4 é✓𝄞")), core::PairFactory::make<core::Str, core::Str>(TEMPER_STR("k5"), TEMPER_STR("item 5 é✓𝄞"))); }()));
    core::Str long_ = TEMPER_STR("abcdefghij é✓𝄞 \000 abcdefghij é✓𝄞 \000 ", 46);
    std::shared_ptr<std::vector<core::Str>> built = core::ListBuilder::make<core::Str>();
    [&]() -> void {
      core::ListBuilder::add<core::Str>(built, TEMPER_STR("item 0 é✓𝄞"));
      core::ListBuilder::add<core::Str>(built, TEMPER_STR("item 1 é✓𝄞"));
      core::ListBuilder::add<core::Str>(built, TEMPER_STR("item 2 é✓𝄞"));
      core::ListBuilder::add<core::Str>(built, TEMPER_STR("item 3 é✓𝄞"));
    }();
    [&]() -> void {
      core::ListBuilder::add<core::Str>(built, TEMPER_STR("item 4 é✓𝄞"));
      core::ListBuilder::add<core::Str>(built, TEMPER_STR("item 5 é✓𝄞"));
      core::ListBuilder::add<core::Str>(built, TEMPER_STR("item 6 é✓𝄞"));
      core::ListBuilder::add<core::Str>(built, TEMPER_STR("item 7 é✓𝄞"));
    }();
    [&]() -> void {
      core::ListBuilder::add<core::Str>(built, TEMPER_STR("item 8 é✓𝄞"));
      core::ListBuilder::add<core::Str>(built, TEMPER_STR("item 9 é✓𝄞"));
    }();
    std::shared_ptr<std::vector<core::Str>> all = core::List::toList<core::Str>(built);
    core::Console::log(console, std::to_string(core::List::length<core::Str>(items)) + " " + core::List::get<core::Str>(items, 9));
    core::Str string = std::to_string(core::Mapped::length<core::Str, core::Str>(table)) + " ";
    core::Str value;
    try {
      value = core::Mapped::get<core::Str, core::Str>(table, TEMPER_STR("k5"));
    } catch (core::TemperBubble const &) {
      value = TEMPER_STR("none");
    }
    core::Console::log(console, string + value);
    core::Console::log(console, std::to_string(core::List::length<core::Str>(core::String::split(long_, TEMPER_STR("✓")))));
    core::Console::log(console, std::to_string(core::List::length<core::Str>(all)) + " " + core::List::get<core::Str>(all, 9));
  });
}

}  // namespace split_shape
