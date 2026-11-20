// init.hpp
#pragma once

#include <temper-core/core.hpp>

namespace content_tags {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

struct Mark;
struct Buffer;
struct Effect;
struct AppendParseEffect;
struct EventParseEffect;
struct After;
struct Propagator;
struct Escaper;
struct TextEscaper;
struct QuoteEscaper;
struct Both;
struct Picker;
struct ContextualAutoescapingAccumulator;
struct Quoting;

struct Mark : public std::enable_shared_from_this<Mark> {
  temper::core::Str label;
  static std::shared_ptr<Mark> make(temper::core::Str const & label);
  temper::core::Str get_label() const;
};

struct Buffer : public std::enable_shared_from_this<Buffer> {
  std::shared_ptr<std::ostringstream> sb;
  static std::shared_ptr<Buffer> make();
  void append(temper::core::Str const & x) const;
  void mark(std::shared_ptr<Mark> const & m) const;
  temper::core::Str toString() const;
};

struct Effect : public virtual temper::core::AnyValueBase {
  virtual void enactOn(std::shared_ptr<Buffer> const & c) const = 0;
};

struct AppendParseEffect : public virtual Effect {
  temper::core::Str text;
  static std::shared_ptr<AppendParseEffect> make(temper::core::Str const & text);
  void enactOn(std::shared_ptr<Buffer> const & c) const override;
  temper::core::Str get_text() const;
};

struct EventParseEffect : public virtual Effect {
  std::shared_ptr<Mark> event;
  static std::shared_ptr<EventParseEffect> make(std::shared_ptr<Mark> const & event);
  void enactOn(std::shared_ptr<Buffer> const & c) const override;
  std::shared_ptr<Mark> get_event() const;
};

struct After : public std::enable_shared_from_this<After> {
  std::shared_ptr<std::vector<std::shared_ptr<Effect>>> effects;
  bool stateAfter;
  static std::shared_ptr<After> make(std::shared_ptr<std::vector<std::shared_ptr<Effect>>> const & effects, bool stateAfter);
  std::shared_ptr<std::vector<std::shared_ptr<Effect>>> get_effects() const;
  bool get_stateAfter() const;
};

struct Propagator : public std::enable_shared_from_this<Propagator> {
  temper::core::Str quote;
  static std::shared_ptr<Propagator> make(temper::core::Str const & quote);
  temper::core::Str get_quote() const;
};

struct Escaper : public virtual temper::core::AnyValueBase {
  virtual temper::core::Str applyString(temper::core::Str const & x) const = 0;
};

struct TextEscaper : public virtual Escaper {
 private:
  struct temper_statics {
    static temper::core::GlobalSlot<std::shared_ptr<TextEscaper>> instance;
  };
  friend void global_init_init();

 public:
  static std::shared_ptr<TextEscaper> const & instance();
  static std::shared_ptr<TextEscaper> make();
  temper::core::Str applyString(temper::core::Str const & x) const override;
};

struct QuoteEscaper : public virtual Escaper {
 private:
  struct temper_statics {
    static temper::core::GlobalSlot<std::shared_ptr<QuoteEscaper>> instance;
  };
  friend void global_init_init();

 public:
  static std::shared_ptr<QuoteEscaper> const & instance();
  static std::shared_ptr<QuoteEscaper> make();
  temper::core::Str applyString(temper::core::Str const & x) const override;
};

struct Both : public virtual Escaper {
  std::shared_ptr<Escaper> first;
  std::shared_ptr<Escaper> second;
  static std::shared_ptr<Both> make(std::shared_ptr<Escaper> const & first, std::shared_ptr<Escaper> const & second);
  temper::core::Str applyString(temper::core::Str const & x) const override;
  std::shared_ptr<Escaper> get_first() const;
  std::shared_ptr<Escaper> get_second() const;
};

struct Picker : public std::enable_shared_from_this<Picker> {
  bool strict;
  static std::shared_ptr<Picker> make(bool strict);
  std::shared_ptr<Escaper> escaperFor(bool inQuote, std::function<void(temper::core::Str, bool)> const & callout) const;
  bool get_strict() const;
};

struct ContextualAutoescapingAccumulator : public virtual temper::core::AnyValueBase {
  virtual temper::core::Str get_accumulated() const = 0;
};

struct Quoting : public virtual ContextualAutoescapingAccumulator {
  bool inQuote;
  std::shared_ptr<Buffer> collector;
  static std::shared_ptr<Buffer> newCollector();
  static bool initialState();
  static std::shared_ptr<Propagator> propagator();
  static std::shared_ptr<Picker> picker();
  static std::function<bool(bool, bool)> sameStatePredicate();
  static bool mergeStates(bool a, bool b, std::function<void(temper::core::Str, bool)> const & callout);
  static void checkEndState(bool inQuote, std::function<void(temper::core::Str, bool)> const & callout);
  static temper::core::Str fromCollector(std::shared_ptr<Buffer> const & c);
  static void enact(std::shared_ptr<Mark> const & m, std::shared_ptr<Buffer> const & c);
  static std::shared_ptr<Quoting> make();
  void appendSafe(temper::core::Str const & x);
  void append(temper::core::Str const & x) const;
  temper::core::Str get_accumulated() const override;
};

std::shared_ptr<After> propagateOver(std::shared_ptr<Propagator> const & p, bool before, temper::core::NullableParam<temper::core::Str> known, std::function<bool(bool, bool)> const & same, std::function<void(temper::core::Str, bool)> const & callout);

}  // namespace content_tags

// init.cpp
#include <content-tags/init.hpp>

namespace content_tags {

namespace core = temper::core;

namespace temper_globals {

static core::GlobalSlot<std::shared_ptr<core::Console::Type>> console;

}  // namespace temper_globals

std::shared_ptr<Mark> Mark::make(core::Str const & label) {
  global_init_init();
  std::shared_ptr<Mark> self = std::make_shared<Mark>();
  self->label = label;
  return self;
}

core::Str Mark::get_label() const {
  return this->label;
}

std::shared_ptr<Buffer> Buffer::make() {
  global_init_init();
  std::shared_ptr<Buffer> self = std::make_shared<Buffer>();
  self->sb = core::StringBuilder::make();
  return self;
}

void Buffer::append(core::Str const & x) const {
  core::StringBuilder::append(this->sb, x);
}

void Buffer::mark(std::shared_ptr<Mark> const & m) const {
  core::StringBuilder::append(this->sb, "{" + m->label + "}");
}

core::Str Buffer::toString() const {
  return core::StringBuilder::toString(this->sb);
}

std::shared_ptr<AppendParseEffect> AppendParseEffect::make(core::Str const & text) {
  global_init_init();
  std::shared_ptr<AppendParseEffect> self = std::make_shared<AppendParseEffect>();
  self->text = text;
  return self;
}

void AppendParseEffect::enactOn(std::shared_ptr<Buffer> const & c) const {
  std::shared_ptr<AppendParseEffect const> self = std::shared_ptr<AppendParseEffect const>(shared_from_this(), this);
  c->append(self->text);
}

core::Str AppendParseEffect::get_text() const {
  return this->text;
}

std::shared_ptr<EventParseEffect> EventParseEffect::make(std::shared_ptr<Mark> const & event) {
  global_init_init();
  std::shared_ptr<EventParseEffect> self = std::make_shared<EventParseEffect>();
  self->event = event;
  return self;
}

void EventParseEffect::enactOn(std::shared_ptr<Buffer> const & c) const {
  std::shared_ptr<EventParseEffect const> self = std::shared_ptr<EventParseEffect const>(shared_from_this(), this);
  c->mark(self->event);
}

std::shared_ptr<Mark> EventParseEffect::get_event() const {
  return this->event;
}

std::shared_ptr<After> After::make(std::shared_ptr<std::vector<std::shared_ptr<Effect>>> const & effects, bool stateAfter) {
  global_init_init();
  std::shared_ptr<After> self = std::make_shared<After>();
  self->effects = effects;
  self->stateAfter = stateAfter;
  return self;
}

std::shared_ptr<std::vector<std::shared_ptr<Effect>>> After::get_effects() const {
  return this->effects;
}

bool After::get_stateAfter() const {
  return this->stateAfter;
}

std::shared_ptr<Propagator> Propagator::make(core::Str const & quote) {
  global_init_init();
  std::shared_ptr<Propagator> self = std::make_shared<Propagator>();
  self->quote = quote;
  return self;
}

core::Str Propagator::get_quote() const {
  return this->quote;
}

static void ignore(core::Str const & problem, bool isError) {
  if (isError) {
    core::Console::log(temper_globals::console.value, problem);
  }
}

std::shared_ptr<After> propagateOver(std::shared_ptr<Propagator> const & p, bool before, core::NullableParam<core::Str> known, std::function<bool(bool, bool)> const & same, std::function<void(core::Str, bool)> const & callout) {
  global_init_init();
  if (known == nullptr) {
    return After::make(core::List::make<std::shared_ptr<Effect>>(), before);
  }
  if (known.value == TEMPER_STR("!")) {
    return After::make(core::List::make<std::shared_ptr<Effect>>(core::to_base<Effect>(EventParseEffect::make(Mark::make(TEMPER_STR("bang"))))), before);
  }
  int32_t angles = core::List::length<core::Str>(core::String::split(known.value, TEMPER_STR("<")));
  if (angles > 1) {
    callout(TEMPER_STR("angle bracket in fixed text"), false);
  }
  if (!same(before, before)) {
    callout(TEMPER_STR("states differ from themselves"), true);
  }
  int32_t quotes = core::Int::sub(core::List::length<core::Str>(core::String::split(known.value, p->quote)), 1);
  return After::make(core::List::make<std::shared_ptr<Effect>>(core::to_base<Effect>(AppendParseEffect::make(known.value))), quotes % 2 == 1 ? !before : before);
}

core::GlobalSlot<std::shared_ptr<TextEscaper>> TextEscaper::temper_statics::instance;

std::shared_ptr<TextEscaper> const & TextEscaper::instance() {
  global_init_init();
  return TextEscaper::temper_statics::instance.value;
}

std::shared_ptr<TextEscaper> TextEscaper::make() {
  global_init_init();
  std::shared_ptr<TextEscaper> self = std::make_shared<TextEscaper>();
  return self;
}

core::Str TextEscaper::applyString(core::Str const & x) const {
  return "[" + x + "]";
}

core::GlobalSlot<std::shared_ptr<QuoteEscaper>> QuoteEscaper::temper_statics::instance;

std::shared_ptr<QuoteEscaper> const & QuoteEscaper::instance() {
  global_init_init();
  return QuoteEscaper::temper_statics::instance.value;
}

std::shared_ptr<QuoteEscaper> QuoteEscaper::make() {
  global_init_init();
  std::shared_ptr<QuoteEscaper> self = std::make_shared<QuoteEscaper>();
  return self;
}

core::Str QuoteEscaper::applyString(core::Str const & x) const {
  return "(" + x + ")";
}

std::shared_ptr<Both> Both::make(std::shared_ptr<Escaper> const & first, std::shared_ptr<Escaper> const & second) {
  global_init_init();
  std::shared_ptr<Both> self = std::make_shared<Both>();
  self->first = first;
  self->second = second;
  return self;
}

core::Str Both::applyString(core::Str const & x) const {
  std::shared_ptr<Both const> self = std::shared_ptr<Both const>(shared_from_this(), this);
  return self->second->applyString(self->first->applyString(x));
}

std::shared_ptr<Escaper> Both::get_first() const {
  return this->first;
}

std::shared_ptr<Escaper> Both::get_second() const {
  return this->second;
}

std::shared_ptr<Picker> Picker::make(bool strict) {
  global_init_init();
  std::shared_ptr<Picker> self = std::make_shared<Picker>();
  self->strict = strict;
  return self;
}

std::shared_ptr<Escaper> Picker::escaperFor(bool inQuote, std::function<void(core::Str, bool)> const & callout) const {
  std::shared_ptr<Picker const> self = shared_from_this();
  if (self->strict && inQuote) {
    callout(TEMPER_STR("a value in quotes"), false);
  }
  if (inQuote) {
    return core::to_base<Escaper>(Both::make(core::to_base<Escaper>(TextEscaper::instance()), core::to_base<Escaper>(QuoteEscaper::instance())));
  } else {
    return core::to_base<Escaper>(TextEscaper::instance());
  }
}

bool Picker::get_strict() const {
  return this->strict;
}

std::shared_ptr<Buffer> Quoting::newCollector() {
  global_init_init();
  return Buffer::make();
}

bool Quoting::initialState() {
  global_init_init();
  return false;
}

std::shared_ptr<Propagator> Quoting::propagator() {
  global_init_init();
  return Propagator::make(TEMPER_STR("'"));
}

std::shared_ptr<Picker> Quoting::picker() {
  global_init_init();
  return Picker::make(false);
}

std::function<bool(bool, bool)> Quoting::sameStatePredicate() {
  global_init_init();
  return [](bool a, bool b) -> bool { return a == b; };
}

bool Quoting::mergeStates(bool a, bool b, std::function<void(core::Str, bool)> const & callout) {
  global_init_init();
  if (a != b) {
    callout(TEMPER_STR("branches end in different contexts"), true);
  }
  return a;
}

void Quoting::checkEndState(bool inQuote, std::function<void(core::Str, bool)> const & callout) {
  global_init_init();
  if (inQuote) {
    callout(TEMPER_STR("ends inside quotes"), true);
  }
}

core::Str Quoting::fromCollector(std::shared_ptr<Buffer> const & c) {
  global_init_init();
  return c->toString();
}

void Quoting::enact(std::shared_ptr<Mark> const & m, std::shared_ptr<Buffer> const & c) {
  global_init_init();
  c->mark(m);
}

std::shared_ptr<Quoting> Quoting::make() {
  global_init_init();
  std::shared_ptr<Quoting> self = std::make_shared<Quoting>();
  self->inQuote = false;
  self->collector = Buffer::make();
  return self;
}

void Quoting::appendSafe(core::Str const & x) {
  std::shared_ptr<Quoting> self = std::shared_ptr<Quoting>(shared_from_this(), this);
  std::shared_ptr<After> after = content_tags::propagateOver(Quoting::propagator(), self->inQuote, x, Quoting::sameStatePredicate(), ignore);
  self->inQuote = after->stateAfter;
  {
    std::shared_ptr<std::vector<std::shared_ptr<Effect>>> list = after->effects;
    int32_t length = core::List::length<std::shared_ptr<Effect>>(list);
    int32_t i = 0;
    while (i < length) {
      std::shared_ptr<Effect> e = core::List::get<std::shared_ptr<Effect>>(list, i);
      i = core::Int::add(i, 1);
      e->enactOn(self->collector);
    }
  }
}

void Quoting::append(core::Str const & x) const {
  std::shared_ptr<Quoting const> self = std::shared_ptr<Quoting const>(shared_from_this(), this);
  core::Str escaped = Quoting::picker()->escaperFor(self->inQuote, ignore)->applyString(x);
  self->collector->append(escaped);
}

core::Str Quoting::get_accumulated() const {
  std::shared_ptr<Quoting const> self = std::shared_ptr<Quoting const>(shared_from_this(), this);
  return self->collector->toString();
}

static core::Str greet(core::Str const & name, core::Str const & who) {
  {
    std::shared_ptr<Buffer> collector = Quoting::newCollector();
    {
      collector->append(TEMPER_STR("say 'hi "));
      collector->append(QuoteEscaper::instance()->applyString(TextEscaper::instance()->applyString(name)));
      collector->append(TEMPER_STR("' to "));
      collector->append(TextEscaper::instance()->applyString(who));
      Quoting::enact(Mark::make(TEMPER_STR("bang")), collector);
    }
    return Quoting::fromCollector(collector);
  }
}

static core::Str branch(bool c, core::Str const & x) {
  {
    std::shared_ptr<Buffer> collector = Quoting::newCollector();
    {
      collector->append(TextEscaper::instance()->applyString(x));
      if (c) {
        collector->append(TEMPER_STR("''"));
      }
      collector->append(TEMPER_STR(" '"));
      collector->append(QuoteEscaper::instance()->applyString(TextEscaper::instance()->applyString(x)));
      collector->append(TEMPER_STR("'"));
    }
    return Quoting::fromCollector(collector);
  }
}

static core::Str each(std::shared_ptr<std::vector<core::Str>> const & xs) {
  {
    std::shared_ptr<Buffer> collector = Quoting::newCollector();
    {
      {
        int32_t length = core::List::length<core::Str>(xs);
        int32_t i = 0;
        while (i < length) {
          core::Str x = core::List::get<core::Str>(xs, i);
          i = core::Int::add(i, 1);
          collector->append(TEMPER_STR("'"));
          collector->append(QuoteEscaper::instance()->applyString(TextEscaper::instance()->applyString(x)));
          collector->append(TEMPER_STR("' "));
          collector->append(TextEscaper::instance()->applyString(x));
          collector->append(TEMPER_STR(";"));
        }
      }
    }
    return Quoting::fromCollector(collector);
  }
}

static core::Str counted(int32_t n, core::Str const & x) {
  {
    std::shared_ptr<Buffer> collector = Quoting::newCollector();
    {
      int32_t i = 0;
      while (i < n) {
        i = core::Int::add(i, 1);
        if (i == 2) {
          continue;
        }
        collector->append(TEMPER_STR("'"));
        collector->append(QuoteEscaper::instance()->applyString(TextEscaper::instance()->applyString(x)));
        collector->append(TEMPER_STR("'"));
        if (i == 4) {
          break;
        }
        collector->append(TextEscaper::instance()->applyString(x));
        collector->append(TEMPER_STR(","));
      }
    }
    return Quoting::fromCollector(collector);
  }
}

static core::Str attribute(bool c, core::Str const & x) {
  {
    std::shared_ptr<Buffer> collector = Quoting::newCollector();
    {
      collector->append(TEMPER_STR("a='"));
      if (c) {
        return TEMPER_STR("none");
      }
      collector->append(QuoteEscaper::instance()->applyString(TextEscaper::instance()->applyString(x)));
      collector->append(TEMPER_STR("'"));
    }
    return Quoting::fromCollector(collector);
  }
}

static core::Str opened(std::shared_ptr<std::vector<core::Str>> const & xs) {
  {
    std::shared_ptr<Quoting> accumulator = Quoting::make();
    {
      {
        int32_t length = core::List::length<core::Str>(xs);
        int32_t i = 0;
        while (i < length) {
          core::Str x = core::List::get<core::Str>(xs, i);
          i = core::Int::add(i, 1);
          accumulator->appendSafe(TEMPER_STR("'"));
          accumulator->append(x);
        }
      }
    }
    return accumulator->get_accumulated();
  }
}

void global_init_init() {
  core::init_once([] {
    temper_globals::console.construct();
    TextEscaper::temper_statics::instance.construct();
    QuoteEscaper::temper_statics::instance.construct();
    temper_globals::console.value = core::Console::get_console();
    TextEscaper::temper_statics::instance.value = TextEscaper::make();
    QuoteEscaper::temper_statics::instance.value = QuoteEscaper::make();
    core::Console::log(temper_globals::console.value, content_tags::greet(TEMPER_STR("Ann"), TEMPER_STR("Bob")));
    core::Console::log(temper_globals::console.value, content_tags::branch(true, TEMPER_STR("a")));
    core::Console::log(temper_globals::console.value, content_tags::branch(false, TEMPER_STR("b")));
    core::Console::log(temper_globals::console.value, content_tags::each(core::List::make<core::Str>(TEMPER_STR("c"), TEMPER_STR("d"))));
    core::Console::log(temper_globals::console.value, content_tags::counted(5, TEMPER_STR("e")));
    core::Str string = content_tags::attribute(false, TEMPER_STR("f"));
    core::Console::log(temper_globals::console.value, string + " " + content_tags::attribute(true, TEMPER_STR("f")));
    core::Console::log(temper_globals::console.value, content_tags::opened(core::List::make<core::Str>(TEMPER_STR("g"), TEMPER_STR("h"), TEMPER_STR("i"))));
  });
}

}  // namespace content_tags
