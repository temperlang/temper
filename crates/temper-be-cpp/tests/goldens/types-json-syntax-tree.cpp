// init.hpp
#pragma once

#include <temper-core/core.hpp>
#include <std/json.hpp>

namespace json_syntax_tree {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

}  // namespace json_syntax_tree

// init.cpp
#include <json-syntax-tree/init.hpp>

namespace json_syntax_tree {

namespace core = temper::core;

void global_init_init() {
  core::init_once([] {
    temper_std::global_init_json();
    std::shared_ptr<core::Console::Type> console = core::Console::get_console();
    {
      std::shared_ptr<std::vector<core::Str>> list = core::List::make<core::Str>(TEMPER_STR("[]"), TEMPER_STR(" [] "), TEMPER_STR(" [ ] "), TEMPER_STR("[1]"), TEMPER_STR("null"));
      int32_t length = core::List::length<core::Str>(list);
      int32_t i = 0;
      while (i < length) {
        core::Str s = core::List::get<core::Str>(list, i);
        i = core::Int::add(i, 1);
        std::shared_ptr<temper_std::JsonSyntaxTree> jsonValue;
        try {
          jsonValue = temper_std::parseJson(s);
        } catch (core::TemperBubble const &) {
          jsonValue = nullptr;
        }
        core::Console::log(console, "`" + s + "` is empty array: " + core::Boolean::toString(jsonValue != nullptr && dynamic_cast<temper_std::JsonArray const *>(jsonValue.get()) != nullptr && core::List::isEmpty<std::shared_ptr<temper_std::JsonSyntaxTree>>(core::checked_cast<temper_std::JsonArray>(jsonValue)->elements)));
      }
    }
    {
      std::shared_ptr<std::vector<core::Str>> list2 = core::List::make<core::Str>(TEMPER_STR("["), TEMPER_STR("]"), TEMPER_STR("[,]"), TEMPER_STR("[]]"), TEMPER_STR("[1,]"), TEMPER_STR("[1,,2]"));
      int32_t length2 = core::List::length<core::Str>(list2);
      int32_t i2 = 0;
      while (i2 < length2) {
        core::Str s2 = core::List::get<core::Str>(list2, i2);
        i2 = core::Int::add(i2, 1);
        bool bubbled = false;
        std::shared_ptr<temper_std::JsonSyntaxTreeProducer> p = temper_std::JsonSyntaxTreeProducer::make();
        temper_std::parseJsonToProducer(s2, core::to_base<temper_std::JsonProducer>(p));
        try {
          p->toJsonSyntaxTree();
        } catch (core::TemperBubble const &) {
          bubbled = true;
        }
        core::NullableParam<core::Str> error = p->get_jsonError();
        core::Console::log(console, "`" + s2 + "` has" + (bubbled ? core::Str() : TEMPER_STR(" no")) + " error: " + (error == nullptr ? TEMPER_STR("MISSING ERROR") : error.value));
      }
    }
    core::Str selectCodePoints = TEMPER_STR("\000\u0001\u0002\u0003\u0004\u0005\u0006\u0007\u0008\t\n\u000B\u000C\r\u000E\u000F\u0010\u0011\u0012\u0013\u0014\u0015\u0016\u0017\u0018\u0019\u001A\u001B\u001C\u001D\u001E\u001F -𐀀-\uE000-퟿\u2028\u2029\u0085", 54);
    {
      std::shared_ptr<temper_std::JsonTextProducer> p2 = temper_std::JsonTextProducer::make(core::to_base<temper_std::InterchangeContext>(temper_std::NullInterchangeContext::instance()));
      p2->stringValue(selectCodePoints);
      core::Str value;
      try {
        value = p2->toJsonString();
      } catch (core::TemperBubble const &) {
        value = TEMPER_STR("?!");
      }
      core::Console::log(console, "string with select code points: " + value);
    }
    {
      std::shared_ptr<temper_std::JsonTextProducer> p3 = temper_std::JsonTextProducer::make(core::to_base<temper_std::InterchangeContext>(temper_std::NullInterchangeContext::instance()));
      p3->startObject();
      std::shared_ptr<temper_std::OrNullJsonAdapter<int32_t>> intOrNullAdapter = temper_std::OrNullJsonAdapter<int32_t>::make(temper_std::int32JsonAdapter());
      core::NullableParam<int32_t> x = nullptr;
      core::NullableParam<int32_t> y = 123;
      p3->objectKey(TEMPER_STR("x"));
      intOrNullAdapter->encodeToJson(x, core::to_base<temper_std::JsonProducer>(p3));
      p3->objectKey(TEMPER_STR("y"));
      intOrNullAdapter->encodeToJson(y, core::to_base<temper_std::JsonProducer>(p3));
      p3->endObject();
      core::Console::log(console, p3->toJsonString());
    }
    {
      core::Str s3 = TEMPER_STR("{\"x\":null,\"y\":123,\"z\":123456789012}");
      std::shared_ptr<temper_std::JsonSyntaxTree> ast = temper_std::parseJson(s3);
      std::shared_ptr<temper_std::JsonObject> obj = core::checked_cast<temper_std::JsonObject>(ast);
      std::shared_ptr<temper_std::JsonSyntaxTree> xAst = obj->propertyValueOrBubble(TEMPER_STR("x"));
      std::shared_ptr<temper_std::JsonSyntaxTree> yAst = obj->propertyValueOrBubble(TEMPER_STR("y"));
      core::Console::log(console, "for x, got JSON null: " + core::Boolean::toString(dynamic_cast<temper_std::JsonNull const *>(xAst.get()) != nullptr));
      core::Console::log(console, "for y, got JSON numeric: " + core::Boolean::toString(dynamic_cast<temper_std::JsonNumeric const *>(yAst.get()) != nullptr));
      std::shared_ptr<temper_std::OrNullJsonAdapter<int32_t>> intOrNullAdapter2 = temper_std::OrNullJsonAdapter<int32_t>::make(temper_std::int32JsonAdapter());
      core::NullableParam<int32_t> x2 = intOrNullAdapter2->decodeFromJson(xAst, core::to_base<temper_std::InterchangeContext>(temper_std::NullInterchangeContext::instance()));
      core::NullableParam<int32_t> y2 = intOrNullAdapter2->decodeFromJson(yAst, core::to_base<temper_std::InterchangeContext>(temper_std::NullInterchangeContext::instance()));
      core::Console::log(console, "x ?\? -1 -> " + std::to_string(x2 == nullptr ? -1 : x2.value));
      core::Console::log(console, "y ?\? -1 -> " + std::to_string(y2 == nullptr ? -1 : y2.value));
      std::shared_ptr<temper_std::JsonSyntaxTree> zAst = obj->propertyValueOrBubble(TEMPER_STR("z"));
      std::shared_ptr<temper_std::OrNullJsonAdapter<int64_t>> int64OrNullAdapter = temper_std::OrNullJsonAdapter<int64_t>::make(temper_std::int64JsonAdapter());
      core::NullableParam<int64_t> z = int64OrNullAdapter->decodeFromJson(zAst, core::to_base<temper_std::InterchangeContext>(temper_std::NullInterchangeContext::instance()));
      core::Console::log(console, "z ?\? -1 -> " + std::to_string(z == nullptr ? int64_t{-1} : z.value));
    }
    std::shared_ptr<temper_std::JsonTextProducer> normalJson = temper_std::JsonTextProducer::make();
    temper_std::parseJsonToProducer(TEMPER_STR("{\n  \"foo\": \"bar\",\n  \"baz\": [\n    \"boo\",\n    { \"f\": 1, \"a\": null, \"r\": 123.0 }\n  ]\n}\n"), core::to_base<temper_std::JsonProducer>(normalJson));
    core::Console::log(console, TEMPER_STR("Normalized:"));
    core::Console::log(console, normalJson->toJsonString());
  });
}

}  // namespace json_syntax_tree
