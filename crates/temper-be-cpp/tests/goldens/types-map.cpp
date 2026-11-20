// init.hpp
#pragma once

namespace map {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

}  // namespace map

// init.cpp
#include <map/init.hpp>

#include <temper-core/temper_bubble.hpp>
#include <temper-core/pair.hpp>
#include <temper-core/str.hpp>
#include <temper-core/list.hpp>
#include <temper-core/module_init.hpp>
#include <temper-core/boolean.hpp>
#include <temper-core/console.hpp>
#include <temper-core/mapped.hpp>
#include <temper-core/map_builder.hpp>
#include <temper-core/map.hpp>
#include <cstdint>
#include <memory>
#include <string>
#include <vector>

namespace map {

namespace core = temper::core;

namespace temper_globals {

static core::GlobalSlot<std::shared_ptr<core::Console::Type>> console;
static core::GlobalSlot<std::shared_ptr<core::Mapped::Ordered<int32_t, core::Str>>> messages;
static core::GlobalSlot<std::shared_ptr<core::Mapped::Ordered<core::Str, std::shared_ptr<std::vector<bool>>>>> eightColors;

}  // namespace temper_globals

template <class K, class V>
static std::shared_ptr<std::vector<K>> listKeys(std::shared_ptr<core::Mapped::Ordered<K, V>> const & mapped) {
  return core::List::map(core::Mapped::toList<K, V>(mapped), [](std::shared_ptr<core::Pair<K, V>> entry) -> K { return entry->key; });
}

static core::Str messageDefined(int32_t code) {
  return core::Boolean::toString(core::Mapped::has<int32_t, core::Str>(temper_globals::messages.value, code));
}

static core::Str colorToHex(std::shared_ptr<std::vector<bool>> const & color) {
  core::Str c = core::List::join(color, core::Str(), [](bool b) -> core::Str { if (b) { return TEMPER_STR("FF"); } else { return TEMPER_STR("00"); } });
  return "#" + c;
}

static void printMap(core::Str const & name, std::shared_ptr<core::Mapped::Ordered<core::Str, core::Str>> const & map) {
  core::Console::log(temper_globals::console.value, "--- " + name + " ---");
  core::Mapped::forEach(map, [](core::Str key, core::Str value) -> void { core::Console::log(temper_globals::console.value, key + " = " + value); });
}

static void printMapBuilder(core::Str const & name, std::shared_ptr<core::Mapped::Ordered<core::Str, core::Str>> const & map) {
  core::Console::log(temper_globals::console.value, "--- " + name + " ---");
  core::Mapped::forEach(map, [](core::Str key, core::Str value) -> void { core::Console::log(temper_globals::console.value, key + " = " + value); });
}

void global_init_init() {
  core::init_once([] {
    temper_globals::console.construct();
    temper_globals::messages.construct();
    temper_globals::eightColors.construct();
    temper_globals::console.value = core::Console::get_console();
    temper_globals::messages.value = core::Map::make<int32_t, core::Str>(core::List::make<std::shared_ptr<core::Pair<int32_t, core::Str>>>(core::PairFactory::make<int32_t, core::Str>(200, TEMPER_STR("OK")), core::PairFactory::make<int32_t, core::Str>(404, TEMPER_STR("Not Found")), core::PairFactory::make<int32_t, core::Str>(500, TEMPER_STR("Internal Server Error"))));
    core::Str message = core::Mapped::get<int32_t, core::Str>(temper_globals::messages.value, 200);
    core::Console::log(temper_globals::console.value, "Message: " + message);
    core::Console::log(temper_globals::console.value, "Has 200: " + messageDefined(200) + ", 302: " + messageDefined(302));
    core::Str value2;
    try {
      value2 = core::Mapped::get<int32_t, core::Str>(temper_globals::messages.value, 302);
    } catch (core::TemperBubble const &) {
      value2 = TEMPER_STR("Found");
    }
    core::Console::log(temper_globals::console.value, "Fallback: " + value2);
    core::Console::log(temper_globals::console.value, "Codes: " + core::List::join(map::listKeys<int32_t, core::Str>(temper_globals::messages.value), TEMPER_STR(" "), [](int32_t it) -> core::Str { return std::to_string(it); }));
    core::Console::log(temper_globals::console.value, "Again: " + core::List::join(core::Mapped::keys<int32_t, core::Str>(temper_globals::messages.value), TEMPER_STR(" "), [](int32_t it) -> core::Str { return std::to_string(it); }));
    core::Console::log(temper_globals::console.value, "Messages: " + core::List::join(core::Mapped::values<int32_t, core::Str>(temper_globals::messages.value), TEMPER_STR(", "), [](core::Str it) -> core::Str { return it; }));
    std::shared_ptr<std::vector<int32_t>> groups = core::Mapped::toListWith(temper_globals::messages.value, [](int32_t key, core::Str) -> int32_t { return key / 100; });
    core::Console::log(temper_globals::console.value, "Groups: " + core::List::join(groups, TEMPER_STR(" "), [](int32_t it) -> core::Str { return std::to_string(it); }));
    std::shared_ptr<core::Mapped::Ordered<core::Str, std::shared_ptr<std::vector<core::Str>>>> builder2 = core::Map::make<core::Str, std::shared_ptr<std::vector<core::Str>>>();
    core::MapBuilder::set<core::Str, std::shared_ptr<std::vector<core::Str>>>(builder2, TEMPER_STR("Honduras"), core::List::make<core::Str>(TEMPER_STR("El Salvador"), TEMPER_STR("Guatemala"), TEMPER_STR("Nicaragua")));
    core::Console::log(temper_globals::console.value, "From builder: " + core::List::join(core::Mapped::get<core::Str, std::shared_ptr<std::vector<core::Str>>>(builder2, TEMPER_STR("Honduras")), TEMPER_STR(", "), [](core::Str it) -> core::Str { return it; }));
    core::Console::log(temper_globals::console.value, "Key so far: " + core::List::get<core::Str>(map::listKeys<core::Str, std::shared_ptr<std::vector<core::Str>>>(builder2), 0));
    core::MapBuilder::set<core::Str, std::shared_ptr<std::vector<core::Str>>>(builder2, TEMPER_STR("El Salvador"), core::List::make<core::Str>(TEMPER_STR("Guatemala"), TEMPER_STR("Honduras")));
    core::MapBuilder::set<core::Str, std::shared_ptr<std::vector<core::Str>>>(builder2, TEMPER_STR("Nicaragua"), core::List::make<core::Str>(TEMPER_STR("Costa Rica"), TEMPER_STR("Honduras")));
    core::MapBuilder::set<core::Str, std::shared_ptr<std::vector<core::Str>>>(builder2, TEMPER_STR("Panama"), core::List::make<core::Str>(TEMPER_STR("Colombia"), TEMPER_STR("Costa Rica")));
    try {
      core::MapBuilder::remove<core::Str, std::shared_ptr<std::vector<core::Str>>>(builder2, TEMPER_STR("Honduras"));
    } catch (core::TemperBubble const &) {
    }
    std::shared_ptr<std::vector<core::Str>> was = core::MapBuilder::remove<core::Str, std::shared_ptr<std::vector<core::Str>>>(builder2, TEMPER_STR("El Salvador"));
    core::Console::log(temper_globals::console.value, "Was there: " + core::List::join(was, TEMPER_STR(", "), [](core::Str it) -> core::Str { return it; }));
    core::Str value3;
    try {
      value3 = core::List::get<core::Str>(core::MapBuilder::remove<core::Str, std::shared_ptr<std::vector<core::Str>>>(builder2, TEMPER_STR("El Salvador")), 0);
    } catch (core::TemperBubble const &) {
      value3 = TEMPER_STR("Gone");
    }
    core::Console::log(temper_globals::console.value, value3);
    std::shared_ptr<core::Mapped::Ordered<core::Str, std::shared_ptr<std::vector<core::Str>>>> neighbors = core::Mapped::toMap<core::Str, std::shared_ptr<std::vector<core::Str>>>(builder2);
    core::Console::log(temper_globals::console.value, "From built: " + core::List::join(core::Mapped::get<core::Str, std::shared_ptr<std::vector<core::Str>>>(neighbors, TEMPER_STR("Nicaragua")), TEMPER_STR(", "), [](core::Str it) -> core::Str { return it; }));
    std::shared_ptr<core::Mapped::Ordered<int32_t, int32_t>> mapOfNone = core::Map::make<int32_t, int32_t>(core::List::make<std::shared_ptr<core::Pair<int32_t, int32_t>>>());
    std::shared_ptr<core::Mapped::Ordered<core::Str, core::Str>> mapOfOne = core::Map::make<core::Str, core::Str>(core::List::make<std::shared_ptr<core::Pair<core::Str, core::Str>>>(core::PairFactory::make<core::Str, core::Str>(TEMPER_STR("jack"), TEMPER_STR("all trades"))));
    std::shared_ptr<core::Mapped::Ordered<int32_t, core::Str>> mapOfTwo = core::Map::make<int32_t, core::Str>(core::List::make<std::shared_ptr<core::Pair<int32_t, core::Str>>>(core::PairFactory::make<int32_t, core::Str>(1, TEMPER_STR("one")), core::PairFactory::make<int32_t, core::Str>(2, TEMPER_STR("electric boogaloo"))));
    core::Console::log(temper_globals::console.value, "mapOfNone: " + std::to_string(core::Mapped::length<int32_t, int32_t>(mapOfNone)));
    core::Console::log(temper_globals::console.value, "mapOfOne: " + std::to_string(core::Mapped::length<core::Str, core::Str>(mapOfOne)));
    core::Console::log(temper_globals::console.value, "mapOfTwo: " + std::to_string(core::Mapped::length<int32_t, core::Str>(mapOfTwo)));
    temper_globals::eightColors.value = core::Map::make<core::Str, std::shared_ptr<std::vector<bool>>>();
    core::MapBuilder::set<core::Str, std::shared_ptr<std::vector<bool>>>(temper_globals::eightColors.value, TEMPER_STR("black"), core::List::make<bool>(false, false, false));
    core::MapBuilder::set<core::Str, std::shared_ptr<std::vector<bool>>>(temper_globals::eightColors.value, TEMPER_STR("red"), core::List::make<bool>(true, false, false));
    core::MapBuilder::set<core::Str, std::shared_ptr<std::vector<bool>>>(temper_globals::eightColors.value, TEMPER_STR("green"), core::List::make<bool>(false, true, false));
    core::MapBuilder::set<core::Str, std::shared_ptr<std::vector<bool>>>(temper_globals::eightColors.value, TEMPER_STR("blue"), core::List::make<bool>(false, false, true));
    core::MapBuilder::set<core::Str, std::shared_ptr<std::vector<bool>>>(temper_globals::eightColors.value, TEMPER_STR("yellow"), core::List::make<bool>(true, true, false));
    core::MapBuilder::set<core::Str, std::shared_ptr<std::vector<bool>>>(temper_globals::eightColors.value, TEMPER_STR("cyan"), core::List::make<bool>(false, true, true));
    core::MapBuilder::set<core::Str, std::shared_ptr<std::vector<bool>>>(temper_globals::eightColors.value, TEMPER_STR("purple"), core::List::make<bool>(true, false, true));
    core::MapBuilder::set<core::Str, std::shared_ptr<std::vector<bool>>>(temper_globals::eightColors.value, TEMPER_STR("white"), core::List::make<bool>(true, true, true));
    core::Console::log(temper_globals::console.value, "with 3 bits you can draw " + std::to_string(core::Mapped::length<core::Str, std::shared_ptr<std::vector<bool>>>(temper_globals::eightColors.value)) + " colors.");
    auto fn9 = [](core::Str color, std::shared_ptr<std::vector<bool>> bits) -> void {
      std::shared_ptr<std::vector<bool>> foundByGetOr = core::Mapped::getOr<core::Str, std::shared_ptr<std::vector<bool>>>(temper_globals::eightColors.value, color, core::List::make<bool>());
      if (core::List::length<bool>(foundByGetOr) != 3) {
        core::Console::log(temper_globals::console.value, "getOr(\"" + color + "\") failed to find key");
      }
      core::Str want = map::colorToHex(bits);
      core::Str got = map::colorToHex(foundByGetOr);
      if (want != got) {
        core::Console::log(temper_globals::console.value, "getOr(\"" + color + "\") found " + got + " but wanted " + want);
      }
    };
    core::Mapped::forEach(temper_globals::eightColors.value, fn9);
    std::shared_ptr<core::Mapped::Ordered<core::Str, core::Str>> map = core::Map::make<core::Str, core::Str>(core::List::make<std::shared_ptr<core::Pair<core::Str, core::Str>>>(core::PairFactory::make<core::Str, core::Str>(TEMPER_STR("key"), TEMPER_STR("value")), core::PairFactory::make<core::Str, core::Str>(TEMPER_STR("other key"), TEMPER_STR("other value"))));
    map::printMap(TEMPER_STR("map"), map);
    std::shared_ptr<core::Mapped::Ordered<core::Str, core::Str>> builder = core::Mapped::toMapBuilder<core::Str, core::Str>(map);
    core::MapBuilder::set<core::Str, core::Str>(builder, TEMPER_STR("added key"), TEMPER_STR("added value"));
    core::MapBuilder::set<core::Str, core::Str>(builder, TEMPER_STR("key"), TEMPER_STR("replaced value"));
    map::printMapBuilder(TEMPER_STR("builder"), builder);
    map::printMap(TEMPER_STR("map (same as above)"), map);
    std::shared_ptr<core::Mapped::Ordered<core::Str, core::Str>> newMap = core::Mapped::toMap<core::Str, core::Str>(builder);
    map::printMap(TEMPER_STR("builder to map"), newMap);
    std::shared_ptr<core::Mapped::Ordered<core::Str, core::Str>> builderAsMapped = builder;
    std::shared_ptr<core::Mapped::Ordered<core::Str, core::Str>> newMapFromMapped = core::Mapped::toMap<core::Str, core::Str>(builderAsMapped);
    core::MapBuilder::remove<core::Str, core::Str>(builder, TEMPER_STR("other key"));
    core::MapBuilder::set<core::Str, core::Str>(builder, TEMPER_STR("key"), TEMPER_STR("double replaced value"));
    map::printMap(TEMPER_STR("builder to map (same as above)"), newMap);
    map::printMap(TEMPER_STR("builder to map via mapped (same as above)"), newMapFromMapped);
    core::MapBuilder::clear<core::Str, core::Str>(builder);
    core::MapBuilder::set<core::Str, core::Str>(builder, TEMPER_STR("something"), TEMPER_STR("alone"));
    map::printMapBuilder(TEMPER_STR("recently cleared"), builder);
    std::shared_ptr<core::Mapped::Ordered<core::Str, core::Str>> mayOrMayNotCopyMap = core::Mapped::toMap<core::Str, core::Str>(map);
    std::shared_ptr<core::Mapped::Ordered<core::Str, core::Str>> mapped = map;
    std::shared_ptr<core::Mapped::Ordered<core::Str, core::Str>> mayOrMayNotCopyMapped = core::Mapped::toMap<core::Str, core::Str>(mapped);
    std::shared_ptr<core::Mapped::Ordered<core::Str, core::Str>> madeBy = core::Map::make<core::Str, core::Str>();
    core::MapBuilder::set<core::Str, core::Str>(madeBy, TEMPER_STR("temper"), TEMPER_STR("temper systems"));
    map::printMapBuilder(TEMPER_STR("made by"), madeBy);
    std::shared_ptr<core::Mapped::Ordered<core::Str, core::Str>> madeByAndJokes = core::Mapped::toMapBuilder<core::Str, core::Str>(madeBy);
    core::MapBuilder::set<core::Str, core::Str>(madeByAndJokes, TEMPER_STR("milk"), TEMPER_STR("milking a cow"));
    map::printMapBuilder(TEMPER_STR("made by (and a joke about milk)"), madeByAndJokes);
    map::printMapBuilder(TEMPER_STR("made by (not changed from a change to a copy)"), madeBy);
  });
}

}  // namespace map
