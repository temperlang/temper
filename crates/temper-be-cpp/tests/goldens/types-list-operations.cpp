// init.hpp
#pragma once

#include <temper-core/core.hpp>

namespace operations {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

}  // namespace operations

// init.cpp
#include <operations/init.hpp>

namespace operations {

namespace core = temper::core;

namespace temper_globals {

static core::GlobalSlot<std::shared_ptr<core::Console::Type>> console;

}  // namespace temper_globals

template <class T>
static void reportLength(std::shared_ptr<std::vector<T>> const & list) {
  core::Console::log(temper_globals::console.value, "Length: " + std::to_string(core::List::length<T>(list)));
}

static void reportSplit(core::Str const & string, core::Str const & sep) {
  std::shared_ptr<std::vector<core::Str>> strings = core::String::split(string, sep);
  core::Str len = std::to_string(core::List::length<core::Str>(strings));
  core::Console::log(temper_globals::console.value, "Split " + core::List::join(strings, TEMPER_STR(", "), [](core::Str x) -> core::Str { return x; }) + " - " + len);
}

static void something(std::function<void(std::shared_ptr<std::vector<core::Str>>)> const & callback) {
  callback(core::List::make<core::Str>(TEMPER_STR("Called"), TEMPER_STR("with"), TEMPER_STR("listed.")));
}

static void runSomething() {
  operations::something([](std::shared_ptr<std::vector<core::Str>> words) -> void { core::Console::log(temper_globals::console.value, core::List::join(words, TEMPER_STR(" "), [](core::Str word) -> core::Str { return word; })); });
}

void global_init_init() {
  core::init_once([] {
    temper_globals::console.construct();
    temper_globals::console.value = core::Console::get_console();
    operations::reportLength<std::shared_ptr<core::AnyValueBase>>(core::List::make<std::shared_ptr<core::AnyValueBase>>(core::any_box(4), core::any_box(5), core::any_box(6)));
    std::shared_ptr<std::vector<int32_t>> ls = core::List::sorted(core::List::make<int32_t>(5, 6, 7, 8, 9, 0, 1, 2, 3, 4), [](int32_t i, int32_t j) -> int32_t { return core::Int::sub(i, j); });
    core::Console::log(temper_globals::console.value, "All  " + core::List::join(ls, TEMPER_STR(", "), [](int32_t x) -> core::Str { return std::to_string(x); }));
    core::Console::log(temper_globals::console.value, "Even " + core::List::join(core::List::filter(ls, [](int32_t x) -> bool { return (x & 1) == 0; }), TEMPER_STR(", "), [](int32_t x) -> core::Str { return std::to_string(x); }));
    core::Console::log(temper_globals::console.value, "Odd  " + core::List::join(core::List::filter(ls, [](int32_t x) -> bool { return (x & 1) != 0; }), TEMPER_STR(", "), [](int32_t x) -> core::Str { return std::to_string(x); }));
    core::Console::log(temper_globals::console.value, "Neg  " + core::List::join(core::List::map(ls, [](int32_t x) -> int32_t { return core::Int::neg(x); }), TEMPER_STR(", "), [](int32_t x) -> core::Str { return std::to_string(x); }));
    std::shared_ptr<std::vector<int32_t>> listed = ls;
    core::Console::log(temper_globals::console.value, "Neg  " + core::List::join(core::List::map(listed, [](int32_t x) -> int32_t { return core::Int::neg(x); }), TEMPER_STR(", "), [](int32_t x) -> core::Str { return std::to_string(x); }));
    std::shared_ptr<std::vector<int32_t>> listedList = core::List::toList<int32_t>(listed);
    core::Console::log(temper_globals::console.value, "Listed list length is " + std::to_string(core::List::length<int32_t>(listedList)));
    core::Console::log(temper_globals::console.value, TEMPER_STR("Looping"));
    int32_t n = core::List::length<int32_t>(ls);
    {
      int32_t i = 0;
      while (i < n) {
        core::Console::log(temper_globals::console.value, "  " + std::to_string(core::List::get<int32_t>(ls, i)));
        i = core::Int::add(i, 1);
      }
    }
    operations::reportSplit(TEMPER_STR("a ; b ; c"), TEMPER_STR(" ; "));
    operations::reportSplit(TEMPER_STR("def"), core::Str());
    operations::reportSplit(TEMPER_STR(" g  h i"), TEMPER_STR(" "));
    operations::reportSplit(TEMPER_STR(" "), TEMPER_STR(" "));
    operations::reportSplit(core::Str(), TEMPER_STR(" "));
    operations::reportSplit(core::Str(), core::Str());
    operations::reportSplit(TEMPER_STR("🌍"), core::Str());
    auto fn11 = [](std::shared_ptr<std::vector<core::Str>> list) -> std::shared_ptr<std::vector<core::Str>> {
      std::shared_ptr<std::vector<core::Str>> builder = core::List::toListBuilder<core::Str>(list);
      core::ListBuilder::sort(builder, [](core::Str a, core::Str b) -> int32_t { return core::Compare::cmp(a, b); });
      return builder;
    };
    std::shared_ptr<std::vector<std::shared_ptr<std::vector<core::Str>>>> triangleOfFooBar = core::List::toListBuilder<std::shared_ptr<std::vector<core::Str>>>(core::List::map(core::List::make<std::shared_ptr<std::vector<core::Str>>>(core::List::make<core::Str>(), core::List::make<core::Str>(TEMPER_STR("bar")), core::List::make<core::Str>(TEMPER_STR("bar"), TEMPER_STR("baz")), core::List::make<core::Str>(TEMPER_STR("bar"), TEMPER_STR("baz"), TEMPER_STR("boo")), core::List::make<core::Str>(TEMPER_STR("bar"), TEMPER_STR("baz"), TEMPER_STR("boo"), TEMPER_STR("far")), core::List::make<core::Str>(TEMPER_STR("bar"), TEMPER_STR("baz"), TEMPER_STR("boo"), TEMPER_STR("far"), TEMPER_STR("faz")), core::List::make<core::Str>(TEMPER_STR("far"), TEMPER_STR("faz"), TEMPER_STR("foo"), TEMPER_STR("bar"), TEMPER_STR("baz"), TEMPER_STR("boo"))), fn11));
    {
      int32_t j = 0;
      while (j < core::List::length<std::shared_ptr<std::vector<core::Str>>>(triangleOfFooBar)) {
        std::shared_ptr<std::vector<core::Str>> row = core::List::get<std::shared_ptr<std::vector<core::Str>>>(triangleOfFooBar, j);
        core::Console::log(temper_globals::console.value, "Fwd  [" + core::List::join(row, TEMPER_STR(" "), [](core::Str x) -> core::Str { return x; }) + "]");
        core::ListBuilder::reverse<core::Str>(row);
        j = core::Int::add(j, 1);
      }
    }
    core::ListBuilder::reverse<std::shared_ptr<std::vector<core::Str>>>(triangleOfFooBar);
    {
      int32_t j2 = 0;
      while (j2 < core::List::length<std::shared_ptr<std::vector<core::Str>>>(triangleOfFooBar)) {
        std::shared_ptr<std::vector<core::Str>> row2 = core::List::get<std::shared_ptr<std::vector<core::Str>>>(triangleOfFooBar, j2);
        core::Console::log(temper_globals::console.value, "Back [" + core::List::join(row2, TEMPER_STR(" "), [](core::Str x) -> core::Str { return x; }) + "]");
        j2 = core::Int::add(j2, 1);
      }
    }
    std::shared_ptr<std::vector<core::Str>> messageBuilder = core::ListBuilder::make<core::Str>();
    core::ListBuilder::add<core::Str>(messageBuilder, TEMPER_STR("hello"));
    core::ListBuilder::set<core::Str>(messageBuilder, 0, TEMPER_STR("hi"));
    core::ListBuilder::addAll<core::Str>(messageBuilder, core::List::make<core::Str>(TEMPER_STR("there"), TEMPER_STR("y'all")));
    core::ListBuilder::add<core::Str>(messageBuilder, TEMPER_STR("say"), 0);
    std::shared_ptr<std::vector<core::Str>> copyThat = core::List::toListBuilder<core::Str>(messageBuilder);
    core::ListBuilder::addAll<core::Str>(messageBuilder, core::List::make<core::Str>(TEMPER_STR("more"), TEMPER_STR("than")), 1);
    core::ListBuilder::addAll<core::Str>(copyThat, messageBuilder);
    core::Console::log(temper_globals::console.value, core::List::join(messageBuilder, TEMPER_STR(" "), [](core::Str x) -> core::Str { return x; }));
    core::Console::log(temper_globals::console.value, core::List::join(core::List::sorted(copyThat, [](core::Str a, core::Str b) -> int32_t { return core::Compare::cmp(b, a); }), TEMPER_STR(" "), [](core::Str x) -> core::Str { return x; }));
    core::Console::log(temper_globals::console.value, core::List::join(copyThat, TEMPER_STR(" "), [](core::Str x) -> core::Str { return x; }));
    core::Str oldLast = core::ListBuilder::removeLast<core::Str>(copyThat);
    core::Console::log(temper_globals::console.value, "Removed " + oldLast + " so " + core::List::join(copyThat, TEMPER_STR(" "), [](core::Str x) -> core::Str { return x; }));
    std::shared_ptr<std::vector<core::Str>> nextLast = core::ListBuilder::splice<core::Str>(copyThat, core::Int::sub(core::List::length<core::Str>(copyThat), 1), 1);
    std::shared_ptr<std::vector<core::Str>> oldWords = core::ListBuilder::splice<core::Str>(copyThat, 4, 3, nextLast);
    core::ListBuilder::splice<core::Str>(copyThat, nullptr, 0, oldWords);
    core::ListBuilder::splice<core::Str>(copyThat, core::List::length<core::Str>(copyThat), nullptr, core::List::make<core::Str>(TEMPER_STR("and"), TEMPER_STR("the"), TEMPER_STR("end")));
    core::Console::log(temper_globals::console.value, core::List::join(copyThat, TEMPER_STR(" "), [](core::Str x) -> core::Str { return x; }));
    core::ListBuilder::splice<core::Str>(copyThat, nullptr, nullptr, core::List::make<core::Str>(TEMPER_STR("gone")));
    core::Console::log(temper_globals::console.value, core::List::join(copyThat, TEMPER_STR(" "), [](core::Str x) -> core::Str { return x; }));
    std::shared_ptr<std::vector<core::Str>> pastEnd = core::ListBuilder::splice<core::Str>(copyThat, core::Int::add(core::List::length<core::Str>(copyThat), 5), 1);
    core::ListBuilder::splice<core::Str>(copyThat, -3, 0, core::List::make<core::Str>(TEMPER_STR("first")));
    int32_t length = core::List::length<core::Str>(pastEnd);
    core::Console::log(temper_globals::console.value, std::to_string(length) + " " + core::List::join(copyThat, TEMPER_STR(" "), [](core::Str x) -> core::Str { return x; }));
    core::ListBuilder::clear<core::Str>(messageBuilder);
    core::ListBuilder::add<core::Str>(messageBuilder, TEMPER_STR("Bye"));
    core::Console::log(temper_globals::console.value, core::List::join(messageBuilder, TEMPER_STR(" "), [](core::Str x) -> core::Str { return x; }));
    {
      int32_t i2 = -2;
      while (i2 <= 10) {
        core::Console::log(temper_globals::console.value, "Slice(" + std::to_string(i2) + ", " + std::to_string(core::Int::add(i2, 2)) + "): [" + core::List::join(core::List::slice<int32_t>(ls, i2, core::Int::add(i2, 2)), TEMPER_STR(", "), [](int32_t x) -> core::Str { return std::to_string(x); }) + "]");
        i2 = core::Int::add(i2, 1);
      }
    }
    core::Console::log(temper_globals::console.value, "Outside fallback: " + std::to_string(core::List::getOr<int32_t>(ls, core::List::length<int32_t>(ls), 123)));
    {
      std::function<core::Str(bool)> booleanToString = [](bool b) -> core::Str { return core::Boolean::toString(b); };
      std::shared_ptr<std::vector<bool>> ls2 = core::List::make<bool>(true, false);
      core::Str joined = core::List::join(ls2, TEMPER_STR(", "), booleanToString);
      std::shared_ptr<std::vector<bool>> inverted = core::List::map(ls2, [](bool b) -> bool { return !b; });
      core::Str joinedAndInverted = core::List::join(inverted, TEMPER_STR(", "), [](bool b) -> core::Str { if (b) { return TEMPER_STR("not false"); } else { return TEMPER_STR("not true"); } });
      core::Console::log(temper_globals::console.value, "list = [" + joined + "]");
      core::Console::log(temper_globals::console.value, "list (each inverted) = [" + joinedAndInverted + "]");
      std::shared_ptr<std::vector<bool>> onlyTrue = core::List::filter(ls2, [](bool b) -> bool { return b; });
      std::shared_ptr<std::vector<bool>> onlyFalse = core::List::filter(ls2, [](bool b) -> bool { return !b; });
      core::Console::log(temper_globals::console.value, "list (== true only) = [" + core::List::join(onlyTrue, TEMPER_STR(", "), booleanToString) + "]");
      core::Console::log(temper_globals::console.value, "list (== false only) = [" + core::List::join(onlyFalse, TEMPER_STR(", "), booleanToString) + "]");
    }
    {
      std::shared_ptr<std::vector<double>> sizes = core::List::make<double>(0.25, 3.0);
      std::shared_ptr<std::vector<core::Str>> labels = core::List::map(sizes, [](double f) -> core::Str { if (core::Float64::lt(f, 1.0)) { return TEMPER_STR("small"); } else { return TEMPER_STR("big"); } });
      core::Console::log(temper_globals::console.value, "sizes = [" + core::List::join(labels, TEMPER_STR(", "), [](core::Str s) -> core::Str { return s; }) + "]");
    }
    runSomething();
    {
      std::shared_ptr<std::vector<core::Str>> aListBuilder = core::ListBuilder::make<core::Str>();
      core::ListBuilder::add<core::Str>(aListBuilder, TEMPER_STR("first"));
      std::shared_ptr<std::vector<core::Str>> builtFirst = core::List::toList<core::Str>(aListBuilder);
      core::ListBuilder::add<core::Str>(aListBuilder, TEMPER_STR("second"));
      std::shared_ptr<std::vector<core::Str>> builtSecond = core::List::toList<core::Str>(aListBuilder);
      core::ListBuilder::add<core::Str>(aListBuilder, TEMPER_STR("third"));
      core::Console::log(temper_globals::console.value, "builtFirst has " + core::List::join(builtFirst, TEMPER_STR(", "), [](core::Str s) -> core::Str { return s; }));
      core::Console::log(temper_globals::console.value, "builtSecond has " + core::List::join(builtSecond, TEMPER_STR(", "), [](core::Str s) -> core::Str { return s; }));
    }
  });
}

}  // namespace operations
