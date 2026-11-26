// init.hpp
#pragma once

#include <temper-core/core.hpp>
#include <std/regex.hpp>

namespace match {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

}  // namespace match

// init.cpp
#include <match/init.hpp>

namespace match {

namespace core = temper::core;

namespace temper_globals {

static core::GlobalSlot<std::shared_ptr<core::Console::Type>> console;
static core::GlobalSlot<core::Str> text;
static core::GlobalSlot<core::Str> moreText;

}  // namespace temper_globals

static void checkGroup(std::shared_ptr<temper_std::Group> const & group) {
  core::Str name = group->name;
  core::Str value = group->value;
  int32_t begin = group->begin;
  int32_t end = group->end;
  core::Str size = std::to_string(core::String::countBetween(temper_globals::text.value, begin, end));
  core::Str begin2 = std::to_string(core::String::countBetween(temper_globals::text.value, core::String::begin(), begin));
  core::Console::log(temper_globals::console.value, "match group '" + name + "': '" + value + "' at " + begin2 + " size " + size);
}

void global_init_init() {
  core::init_once([] {
    temper_globals::console.construct();
    temper_globals::text.construct();
    temper_globals::moreText.construct();
    temper_std::global_init_regex();
    temper_globals::console.value = core::Console::get_console();
    std::shared_ptr<temper_std::RegexNode> regexNode = core::to_base<temper_std::RegexNode>(temper_std::oneOrMore(core::to_base<temper_std::RegexNode>(temper_std::CodeSet::make(core::List::make<std::shared_ptr<temper_std::CodePart>>(core::to_base<temper_std::CodePart>(temper_std::CodePoints::make(TEMPER_STR("ab"))))))));
    std::shared_ptr<temper_std::Regex> regex = temper_std::Sequence::make(core::List::make<std::shared_ptr<temper_std::RegexNode>>(regexNode, core::to_base<temper_std::RegexNode>(temper_std::CodePoints::make(TEMPER_STR("c")))))->compiled();
    std::shared_ptr<temper_std::Regex> full = temper_std::entire(regex->data)->compiled();
    std::shared_ptr<std::vector<core::Str>> strings = core::List::make<core::Str>(TEMPER_STR("abc"), TEMPER_STR("ac"), TEMPER_STR("bbabac"), TEMPER_STR("ab"), TEMPER_STR("dc"), TEMPER_STR("acc"), TEMPER_STR("cbc"));
    {
      int32_t length = core::List::length<core::Str>(strings);
      int32_t i = 0;
      while (i < length) {
        core::Str string = core::List::get<core::Str>(strings, i);
        i = core::Int::add(i, 1);
        core::Console::log(temper_globals::console.value, "found " + string + ": " + core::Boolean::toString(regex->found(string)));
        core::Console::log(temper_globals::console.value, "matches " + string + ": " + core::Boolean::toString(full->found(string)));
      }
    }
    std::shared_ptr<temper_std::RegexNode> value2 = core::to_base<temper_std::RegexNode>(temper_std::Repeat::make(core::to_base<temper_std::RegexNode>(temper_std::CodePoints::make(TEMPER_STR("a"))), 1, nullptr, false));
    std::shared_ptr<temper_std::RegexNode> value3 = core::to_base<temper_std::RegexNode>(temper_std::Capture::make(TEMPER_STR("intro"), core::to_base<temper_std::RegexNode>(temper_std::Repeat::make(core::to_base<temper_std::RegexNode>(temper_std::CodePoints::make(TEMPER_STR("b"))), 1, nullptr, false))));
    std::shared_ptr<temper_std::RegexNode> value4 = core::to_base<temper_std::RegexNode>(temper_std::CodePoints::make(TEMPER_STR("c")));
    std::shared_ptr<temper_std::RegexNode> value5 = core::to_base<temper_std::RegexNode>(temper_std::Capture::make(TEMPER_STR("option1"), core::to_base<temper_std::RegexNode>(temper_std::CodePoints::make(TEMPER_STR("d")))));
    std::shared_ptr<temper_std::Regex> catcher = temper_std::Sequence::make(core::List::make<std::shared_ptr<temper_std::RegexNode>>(value2, value3, value4, core::to_base<temper_std::RegexNode>(temper_std::Repeat::make(core::to_base<temper_std::RegexNode>(temper_std::Or::make(core::List::make<std::shared_ptr<temper_std::RegexNode>>(value5, core::to_base<temper_std::RegexNode>(temper_std::Capture::make(TEMPER_STR("alphaOption2"), core::to_base<temper_std::RegexNode>(temper_std::CodePoints::make(TEMPER_STR("e")))))))), 1, nullptr, false))))->compiled();
    temper_globals::text.value = TEMPER_STR("🌍aaabbceef");
    std::shared_ptr<temper_std::Match> subject = catcher->find(temper_globals::text.value);
    std::shared_ptr<temper_std::Match> match = subject == nullptr ? core::panic<std::shared_ptr<temper_std::Match>>() : subject;
    match::checkGroup(match->full);
    auto Result = core::Mapped::get<core::Str, std::shared_ptr<temper_std::Group>>(match->groups, TEMPER_STR("intro"));
    if (!Result.has_value()) {
      core::uncaught_failure(Result.error());
    }
    match::checkGroup(*Result);
    auto Result2 = core::Mapped::get<core::Str, std::shared_ptr<temper_std::Group>>(match->groups, TEMPER_STR("alphaOption2"));
    if (!Result2.has_value()) {
      core::uncaught_failure(Result2.error());
    }
    match::checkGroup(*Result2);
    std::shared_ptr<temper_std::Regex> thing = temper_std::Repeat::make(core::to_base<temper_std::RegexNode>(temper_std::CodeSet::make(core::List::make<std::shared_ptr<temper_std::CodePart>>(core::to_base<temper_std::CodePart>(temper_std::CodeRange::make(97, 122))), false)), 1, nullptr, false)->compiled();
    core::Str multiText = TEMPER_STR("---abc---xyz---");
    std::shared_ptr<temper_std::Match> subject2 = thing->find(multiText);
    std::shared_ptr<temper_std::Match> first = subject2 == nullptr ? core::panic<std::shared_ptr<temper_std::Match>>() : subject2;
    std::shared_ptr<temper_std::Match> subject3 = thing->find(multiText, first->full->end);
    std::shared_ptr<temper_std::Match> second = subject3 == nullptr ? core::panic<std::shared_ptr<temper_std::Match>>() : subject3;
    core::Console::log(temper_globals::console.value, "Found both " + first->full->value + " and " + second->full->value + ".");
    temper_globals::moreText.value = TEMPER_STR("🌍aaabbcee🌎abcd🌏");
    auto fn = [](std::shared_ptr<temper_std::Match> match2) -> core::Str {
      std::shared_ptr<core::Mapped::Ordered<core::Str, std::shared_ptr<temper_std::Group>>> groups = match2->groups;
      core::Str option1_2;
      auto result = [&]() -> core::Expected<core::Str, std::shared_ptr<core::MissingKey>> {
        auto Result3 = core::Mapped::get<core::Str, std::shared_ptr<temper_std::Group>>(groups, TEMPER_STR("option1"));
        if (!Result3.has_value()) {
          return core::unexpected(Result3.error());
        }
        return (*Result3)->value;
      }();
      if (result.has_value()) {
        option1_2 = *result;
      } else {
        option1_2 = core::Str();
      }
      core::Str alphaOption2;
      auto result2 = [&]() -> core::Expected<core::Str, std::shared_ptr<core::MissingKey>> {
        auto Result4 = core::Mapped::get<core::Str, std::shared_ptr<temper_std::Group>>(groups, TEMPER_STR("alphaOption2"));
        if (!Result4.has_value()) {
          return core::unexpected(Result4.error());
        }
        return (*Result4)->value;
      }();
      if (result2.has_value()) {
        alphaOption2 = *result2;
      } else {
        alphaOption2 = core::Str();
      }
      int32_t begin2 = core::String::countBetween(temper_globals::moreText.value, core::String::begin(), match2->full->begin);
      return "-" + option1_2 + alphaOption2 + std::to_string(begin2) + "-";
    };
    core::Str replaced = catcher->replace(temper_globals::moreText.value, fn);
    core::Console::log(temper_globals::console.value, "replaced: " + replaced);
    core::Str notReplaced = catcher->replace(TEMPER_STR("nope"), [](std::shared_ptr<temper_std::Match>) -> core::Str { return TEMPER_STR("hi"); });
    core::Console::log(temper_globals::console.value, "not replaced: " + notReplaced);
    std::shared_ptr<temper_std::Match> subject6 = catcher->find(TEMPER_STR("not here"));
    std::shared_ptr<temper_std::Group> subject5 = subject6 == nullptr ? nullptr : subject6->full;
    core::NullableParam<core::Str> subject4 = subject5 == nullptr ? nullptr : core::NullableParam<core::Str>(subject5->value);
    core::Str noMatch = subject4 == nullptr ? TEMPER_STR("sorry") : subject4.value;
    core::Console::log(temper_globals::console.value, "No match says " + noMatch);
    std::shared_ptr<temper_std::RegexNode> value6 = core::to_base<temper_std::RegexNode>(temper_std::CodePoints::make(TEMPER_STR("ab")));
    std::shared_ptr<temper_std::RegexNode> or_ = temper_std::Or::make(core::List::make<std::shared_ptr<temper_std::RegexNode>>(value6, core::to_base<temper_std::RegexNode>(temper_std::CodePoints::make(TEMPER_STR("bc")))))->compiled()->data;
    core::Str option1 = TEMPER_STR("ab");
    core::Str option2 = TEMPER_STR("bc");
    core::Str optionNone = TEMPER_STR("cb");
    core::Console::log(temper_globals::console.value, "or option 1: " + core::Boolean::toString(or_->found(option1)));
    core::Console::log(temper_globals::console.value, "or option 2: " + core::Boolean::toString(or_->found(option2)));
    core::Console::log(temper_globals::console.value, "or option none: " + core::Boolean::toString(or_->found(optionNone)));
    std::shared_ptr<temper_std::Regex> begin = temper_std::Sequence::make(core::List::make<std::shared_ptr<temper_std::RegexNode>>(core::to_base<temper_std::RegexNode>(temper_std::temper_globals::Begin.value), core::to_base<temper_std::RegexNode>(temper_std::CodePoints::make(TEMPER_STR("a")))))->compiled();
    core::Str beginsWithATrue = TEMPER_STR("ab");
    core::Str beginsWithAFalse = TEMPER_STR("bab");
    core::Console::log(temper_globals::console.value, "begin true: " + core::Boolean::toString(begin->found(beginsWithATrue)));
    core::Console::log(temper_globals::console.value, "begin false: " + core::Boolean::toString(begin->found(beginsWithAFalse)));
    std::shared_ptr<temper_std::RegexNode> value7 = core::to_base<temper_std::RegexNode>(temper_std::CodePoints::make(TEMPER_STR("a")));
    std::shared_ptr<temper_std::Regex> negatedBasic = temper_std::entire(temper_std::Sequence::make(core::List::make<std::shared_ptr<temper_std::RegexNode>>(value7, core::to_base<temper_std::RegexNode>(temper_std::CodeSet::make(core::List::make<std::shared_ptr<temper_std::CodePart>>(core::to_base<temper_std::CodePart>(temper_std::CodeRange::make(97, 99))), true))))->compiled()->data)->compiled();
    core::Str nbTrue = TEMPER_STR("a🌍");
    core::Str nbFalse = TEMPER_STR("ac");
    core::Console::log(temper_globals::console.value, "negated basic true: " + core::Boolean::toString(negatedBasic->found(nbTrue)));
    core::Console::log(temper_globals::console.value, "negated basic false: " + core::Boolean::toString(negatedBasic->found(nbFalse)));
    core::Str simplerLines = TEMPER_STR("It was the best of times.\nIt was the worst of times.");
    std::shared_ptr<temper_std::Match> subject9 = temper_std::Repeat::make(core::to_base<temper_std::RegexNode>(temper_std::CodeSet::make(core::List::make<std::shared_ptr<temper_std::CodePart>>(), true)), 1, nullptr, false)->compiled()->find(simplerLines);
    std::shared_ptr<temper_std::Group> subject8 = subject9 == nullptr ? nullptr : subject9->full;
    core::NullableParam<core::Str> subject7 = subject8 == nullptr ? nullptr : core::NullableParam<core::Str>(subject8->value);
    core::Console::log(temper_globals::console.value, subject7 == nullptr ? TEMPER_STR("-") : subject7.value);
    std::shared_ptr<temper_std::Match> subject12 = temper_std::Repeat::make(core::to_base<temper_std::RegexNode>(temper_std::CodeSet::make(core::List::make<std::shared_ptr<temper_std::CodePart>>(), false)), 1, nullptr, false)->compiled()->find(simplerLines);
    std::shared_ptr<temper_std::Group> subject11 = subject12 == nullptr ? nullptr : subject12->full;
    core::NullableParam<core::Str> subject10 = subject11 == nullptr ? nullptr : core::NullableParam<core::Str>(subject11->value);
    core::Console::log(temper_globals::console.value, subject10 == nullptr ? TEMPER_STR("-") : subject10.value);
    std::shared_ptr<temper_std::Regex> notLow = temper_std::oneOrMore(core::to_base<temper_std::RegexNode>(temper_std::CodeSet::make(core::List::make<std::shared_ptr<temper_std::CodePart>>(core::to_base<temper_std::CodePart>(temper_std::CodeRange::make(0, 47))), true)))->compiled();
    std::shared_ptr<temper_std::Match> subject15 = notLow->find(TEMPER_STR("-/a🌍-"));
    std::shared_ptr<temper_std::Group> subject14 = subject15 == nullptr ? nullptr : subject15->full;
    core::NullableParam<core::Str> subject13 = subject14 == nullptr ? nullptr : core::NullableParam<core::Str>(subject14->value);
    core::Console::log(temper_globals::console.value, "not low: " + (subject13 == nullptr ? TEMPER_STR("-") : subject13.value));
    std::shared_ptr<temper_std::CodePart> codePart = core::to_base<temper_std::CodePart>(temper_std::CodePoints::make(TEMPER_STR("\t")));
    std::shared_ptr<temper_std::CodePart> value8 = core::to_base<temper_std::CodePart>(temper_std::CodePoints::make(TEMPER_STR("\n")));
    std::shared_ptr<temper_std::CodePart> value9 = core::to_base<temper_std::CodePart>(temper_std::CodePoints::make(TEMPER_STR(" ")));
    std::shared_ptr<temper_std::Match> subject18 = temper_std::Repeat::make(core::to_base<temper_std::RegexNode>(temper_std::CodeSet::make(core::List::make<std::shared_ptr<temper_std::CodePart>>(codePart, value8, value9, core::to_base<temper_std::CodePart>(temper_std::CodePoints::make(TEMPER_STR(">")))), true)), 1, nullptr, false)->compiled()->find(TEMPER_STR("ab>c"));
    std::shared_ptr<temper_std::Group> subject17 = subject18 == nullptr ? nullptr : subject18->full;
    core::NullableParam<core::Str> subject16 = subject17 == nullptr ? nullptr : core::NullableParam<core::Str>(subject17->value);
    core::Console::log(temper_globals::console.value, "not space or >: " + (subject16 == nullptr ? TEMPER_STR("-") : subject16.value));
    std::shared_ptr<temper_std::CodePart> value10 = core::to_base<temper_std::CodePart>(temper_std::CodeRange::make(97, 122));
    std::shared_ptr<temper_std::CodePart> value11 = core::to_base<temper_std::CodePart>(temper_std::CodeRange::make(65, 90));
    std::shared_ptr<temper_std::RegexNode> idStart = temper_std::CodeSet::make(core::List::make<std::shared_ptr<temper_std::CodePart>>(value10, value11, core::to_base<temper_std::CodePart>(temper_std::CodePoints::make(TEMPER_STR("_")))), false)->compiled()->data;
    std::shared_ptr<temper_std::Or> idContinue = temper_std::Or::make(core::List::make<std::shared_ptr<temper_std::RegexNode>>(idStart, temper_std::CodeSet::make(core::List::make<std::shared_ptr<temper_std::CodePart>>(core::to_base<temper_std::CodePart>(temper_std::CodeRange::make(48, 57))), false)->compiled()->data));
    std::shared_ptr<temper_std::Regex> word = temper_std::Sequence::make(core::List::make<std::shared_ptr<temper_std::RegexNode>>(core::to_base<temper_std::RegexNode>(temper_std::temper_globals::WordBoundary.value), idStart, core::to_base<temper_std::RegexNode>(temper_std::Repeat::make(core::to_base<temper_std::RegexNode>(idContinue), 0, nullptr)), core::to_base<temper_std::RegexNode>(temper_std::temper_globals::WordBoundary.value)))->compiled();
    core::Str expected = TEMPER_STR("def_456");
    std::shared_ptr<temper_std::Match> subject21 = word->find("123_abc " + expected + " ghi_789");
    std::shared_ptr<temper_std::Group> subject20 = subject21 == nullptr ? nullptr : subject21->full;
    core::NullableParam<core::Str> subject19 = subject20 == nullptr ? nullptr : core::NullableParam<core::Str>(subject20->value);
    core::Str found = subject19 == nullptr ? TEMPER_STR("-") : subject19.value;
    core::Console::log(temper_globals::console.value, "Found id: " + found);
    core::Str stringToEscape = TEMPER_STR("$(*^");
    std::shared_ptr<temper_std::Regex> regexWithInterpolatedString = temper_std::Sequence::make(core::List::make<std::shared_ptr<temper_std::RegexNode>>(core::to_base<temper_std::RegexNode>(temper_std::temper_globals::Dot.value), core::to_base<temper_std::RegexNode>(temper_std::Repeat::make(core::to_base<temper_std::RegexNode>(temper_std::CodePoints::make(stringToEscape)), 1, nullptr, false)), core::to_base<temper_std::RegexNode>(temper_std::temper_globals::Dot.value)))->compiled();
    core::Str stringToEscapeAndMore = "a" + stringToEscape + stringToEscape + "b";
    std::shared_ptr<temper_std::Match> subject22 = regexWithInterpolatedString->find(stringToEscapeAndMore);
    std::shared_ptr<temper_std::Match> escapedMatch = subject22 == nullptr ? core::panic<std::shared_ptr<temper_std::Match>>() : subject22;
    core::Console::log(temper_globals::console.value, escapedMatch->full->value);
    std::shared_ptr<temper_std::Match> subject23 = temper_std::Sequence::make(core::List::make<std::shared_ptr<temper_std::RegexNode>>(core::to_base<temper_std::RegexNode>(temper_std::temper_globals::Dot.value), core::to_base<temper_std::RegexNode>(temper_std::Capture::make(TEMPER_STR("full"), core::to_base<temper_std::RegexNode>(temper_std::temper_globals::Dot.value)))))->compiled()->find(TEMPER_STR("ab"));
    std::shared_ptr<temper_std::Match> ownFullMatch = subject23 == nullptr ? core::panic<std::shared_ptr<temper_std::Match>>() : subject23;
    auto Result5 = core::Mapped::get<core::Str, std::shared_ptr<temper_std::Group>>(ownFullMatch->groups, TEMPER_STR("full"));
    if (!Result5.has_value()) {
      core::uncaught_failure(Result5.error());
    }
    core::Str ownFull = (*Result5)->value;
    core::Console::log(temper_globals::console.value, "Own full: " + ownFull + " vs " + ownFullMatch->full->value);
    core::Str comboLines = TEMPER_STR("It was the best\nof times.\r\n\rIt was the worst of times.");
    {
      std::shared_ptr<temper_std::RegexNode> value12 = core::to_base<temper_std::RegexNode>(temper_std::CodePoints::make(TEMPER_STR("\n")));
      std::shared_ptr<temper_std::RegexNode> value13 = core::to_base<temper_std::RegexNode>(temper_std::CodePoints::make(TEMPER_STR("\r")));
      std::shared_ptr<std::vector<core::Str>> list = temper_std::Or::make(core::List::make<std::shared_ptr<temper_std::RegexNode>>(value12, core::to_base<temper_std::RegexNode>(temper_std::Sequence::make(core::List::make<std::shared_ptr<temper_std::RegexNode>>(value13, core::to_base<temper_std::RegexNode>(temper_std::Repeat::make(core::to_base<temper_std::RegexNode>(temper_std::CodePoints::make(TEMPER_STR("\n"))), 0, 1, false)))))))->compiled()->split(comboLines);
      int32_t length2 = core::List::length<core::Str>(list);
      int32_t i2 = 0;
      while (i2 < length2) {
        core::Str line = core::List::get<core::Str>(list, i2);
        i2 = core::Int::add(i2, 1);
        core::Console::log(temper_globals::console.value, line + ";");
      }
    }
  });
}

}  // namespace match
