// init.hpp
#pragma once

#include <temper-core/base.hpp>
#include <temper-core/casting.hpp>
#include <temper-core/int.hpp>
#include <temper-core/list_builder.hpp>
#include <temper-core/deque.hpp>
#include <temper-core/dense_bit_vector.hpp>
#include <deque>

namespace myers_diff {

// Runs the module's top level, once. Exported functions, constructors,
// static functions, and accessors run it first, so calling it is
// optional.
void global_init_init();

template <class T>
struct Change;
template <class T>
struct Patch;
struct DiffPath;

template <class T>
struct Change : public std::enable_shared_from_this<Change<T>> {
  temper::core::Str type;
  int32_t leftIndex;
  int32_t rightIndex;
  std::shared_ptr<std::vector<T>> items;
  static std::shared_ptr<Change<T>> make(temper::core::Str const & type, int32_t leftIndex, int32_t rightIndex, std::shared_ptr<std::vector<T>> const & items);
  temper::core::Str get_type() const;
  int32_t get_leftIndex() const;
  int32_t get_rightIndex() const;
  std::shared_ptr<std::vector<T>> get_items() const;
};

template <class T>
struct Patch : public std::enable_shared_from_this<Patch<T>> {
  std::shared_ptr<std::vector<std::shared_ptr<Change<T>>>> changes;
  static std::shared_ptr<Patch<T>> make(std::shared_ptr<std::vector<std::shared_ptr<Change<T>>>> const & changes);
  std::shared_ptr<std::vector<std::shared_ptr<Change<T>>>> get_changes() const;
};

struct DiffPath : public std::enable_shared_from_this<DiffPath> {
  int32_t leftIndex;
  int32_t rightIndex;
  temper::core::Str changeType;
  std::shared_ptr<DiffPath> previous;
  static std::shared_ptr<DiffPath> make(int32_t leftIndex, int32_t rightIndex, temper::core::Str const & changeType, std::shared_ptr<DiffPath> const & previous);
  int32_t get_leftIndex() const;
  int32_t get_rightIndex() const;
  temper::core::Str get_changeType() const;
  std::shared_ptr<DiffPath> get_previous() const;
};

// Storage, which the library reads and its init sets; read values
// through the accessors below.
namespace temper_globals {
extern temper::core::GlobalSlot<temper::core::Str> ChangeTypeAddition;
extern temper::core::GlobalSlot<temper::core::Str> ChangeTypeDeletion;
extern temper::core::GlobalSlot<temper::core::Str> ChangeTypeUnchanged;
}  // namespace temper_globals

temper::core::Str const & ChangeTypeAddition();
temper::core::Str const & ChangeTypeDeletion();
temper::core::Str const & ChangeTypeUnchanged();

temper::core::Str formatPatch(std::shared_ptr<Patch<temper::core::Str>> const & patch);

template <class T>
std::shared_ptr<Change<T>> Change<T>::make(temper::core::Str const & type, int32_t leftIndex, int32_t rightIndex, std::shared_ptr<std::vector<T>> const & items) {
  global_init_init();
  std::shared_ptr<Change<T>> self = std::make_shared<Change<T>>();
  self->type = type;
  self->leftIndex = leftIndex;
  self->rightIndex = rightIndex;
  self->items = items;
  return self;
}

template <class T>
temper::core::Str Change<T>::get_type() const {
  return this->type;
}

template <class T>
int32_t Change<T>::get_leftIndex() const {
  return this->leftIndex;
}

template <class T>
int32_t Change<T>::get_rightIndex() const {
  return this->rightIndex;
}

template <class T>
std::shared_ptr<std::vector<T>> Change<T>::get_items() const {
  return this->items;
}

template <class T>
std::shared_ptr<Patch<T>> Patch<T>::make(std::shared_ptr<std::vector<std::shared_ptr<Change<T>>>> const & changes) {
  global_init_init();
  std::shared_ptr<Patch<T>> self = std::make_shared<Patch<T>>();
  self->changes = changes;
  return self;
}

template <class T>
std::shared_ptr<std::vector<std::shared_ptr<Change<T>>>> Patch<T>::get_changes() const {
  return this->changes;
}

template <class T>
std::shared_ptr<Patch<T>> diff(std::shared_ptr<std::vector<T>> const & left, std::shared_ptr<std::vector<T>> const & right, std::function<bool(T, T)> const & eq) {
  global_init_init();
  int32_t leftLength = temper::core::List::length<T>(left);
  int32_t rightLength = temper::core::List::length<T>(right);
  int32_t reachedRowLength = temper::core::Int::add(leftLength, 1);
  int32_t reachedColLength = temper::core::Int::add(rightLength, 1);
  std::shared_ptr<std::vector<bool>> reached = temper::core::DenseBitVector::make(temper::core::Int::mul(reachedRowLength, reachedColLength));
  std::shared_ptr<std::deque<std::shared_ptr<DiffPath>>> costZeroEdges = temper::core::Deque::make<std::shared_ptr<DiffPath>>();
  temper::core::Deque::add<std::shared_ptr<DiffPath>>(costZeroEdges, nullptr);
  std::shared_ptr<std::deque<std::shared_ptr<DiffPath>>> costOneEdges = temper::core::Deque::make<std::shared_ptr<DiffPath>>();
  while (true) {
    std::shared_ptr<DiffPath> diffPath = !temper::core::Deque::isEmpty<std::shared_ptr<DiffPath>>(costZeroEdges) ? temper::core::Deque::removeFirst<std::shared_ptr<DiffPath>>(costZeroEdges) : !temper::core::Deque::isEmpty<std::shared_ptr<DiffPath>>(costOneEdges) ? temper::core::Deque::removeFirst<std::shared_ptr<DiffPath>>(costOneEdges) : nullptr;
    temper::core::NullableParam<int32_t> subject = diffPath == nullptr ? nullptr : temper::core::NullableParam<int32_t>(diffPath->leftIndex);
    int32_t leftIndex = subject == nullptr ? 0 : subject.value;
    temper::core::NullableParam<int32_t> subject2 = diffPath == nullptr ? nullptr : temper::core::NullableParam<int32_t>(diffPath->rightIndex);
    int32_t rightIndex = subject2 == nullptr ? 0 : subject2.value;
    if (leftIndex == leftLength && rightIndex == rightLength) {
      std::shared_ptr<std::vector<std::shared_ptr<DiffPath>>> pathElements = temper::core::ListBuilder::make<std::shared_ptr<DiffPath>>();
      {
        std::shared_ptr<DiffPath> pathElement = diffPath;
        while (pathElement != nullptr) {
          std::shared_ptr<DiffPath> path = temper::core::checked_cast<DiffPath>(pathElement);
          temper::core::ListBuilder::add<std::shared_ptr<DiffPath>>(pathElements, path);
          pathElement = path->previous;
        }
      }
      temper::core::ListBuilder::reverse<std::shared_ptr<DiffPath>>(pathElements);
      int32_t leftPatchIndex = 0;
      int32_t rightPatchIndex = 0;
      std::shared_ptr<std::vector<std::shared_ptr<Change<T>>>> changes = temper::core::ListBuilder::make<std::shared_ptr<Change<T>>>();
      int32_t n = temper::core::List::length<std::shared_ptr<DiffPath>>(pathElements);
      {
        int32_t i = 0;
        int32_t end;
        while (i < n) {
          temper::core::Str changeType = temper::core::List::get<std::shared_ptr<DiffPath>>(pathElements, i)->changeType;
          end = temper::core::Int::add(i, 1);
          while (end < n && changeType == temper::core::List::get<std::shared_ptr<DiffPath>>(pathElements, end)->changeType) {
            end = temper::core::Int::add(end, 1);
          }
          int32_t nItems = temper::core::Int::sub(end, i);
          std::shared_ptr<std::vector<T>> items;
          int32_t nLeftItems = 0;
          int32_t nRightItems = 0;
          if (changeType == temper_globals::ChangeTypeAddition.value) {
            items = temper::core::List::slice<T>(right, rightPatchIndex, temper::core::Int::add(rightPatchIndex, nItems));
            nRightItems = nItems;
          } else {
            items = temper::core::List::slice<T>(left, leftPatchIndex, temper::core::Int::add(leftPatchIndex, nItems));
            if (changeType == temper_globals::ChangeTypeUnchanged.value) {
              nRightItems = nItems;
            }
            nLeftItems = nItems;
          }
          temper::core::ListBuilder::add<std::shared_ptr<Change<T>>>(changes, Change<T>::make(changeType, leftPatchIndex, rightPatchIndex, items));
          leftPatchIndex = temper::core::Int::add(leftPatchIndex, nLeftItems);
          rightPatchIndex = temper::core::Int::add(rightPatchIndex, nRightItems);
          i = end;
        }
      }
      return Patch<T>::make(temper::core::List::toList<std::shared_ptr<Change<T>>>(changes));
    }
    if (leftIndex < leftLength) {
      if (rightIndex < rightLength) {
        int32_t sameReachedIndex = temper::core::Int::add(temper::core::Int::add(leftIndex, 1), temper::core::Int::mul(temper::core::Int::add(rightIndex, 1), reachedRowLength));
        if (!temper::core::DenseBitVector::get(reached, sameReachedIndex) && eq(temper::core::List::get<T>(left, leftIndex), temper::core::List::get<T>(right, rightIndex))) {
          temper::core::DenseBitVector::set(reached, sameReachedIndex, true);
          temper::core::Deque::add<std::shared_ptr<DiffPath>>(costZeroEdges, DiffPath::make(temper::core::Int::add(leftIndex, 1), temper::core::Int::add(rightIndex, 1), temper_globals::ChangeTypeUnchanged.value, diffPath));
        }
      }
      int32_t delReachedIndex = temper::core::Int::add(temper::core::Int::add(leftIndex, 1), temper::core::Int::mul(rightIndex, reachedRowLength));
      if (!temper::core::DenseBitVector::get(reached, delReachedIndex)) {
        temper::core::DenseBitVector::set(reached, delReachedIndex, true);
        temper::core::Deque::add<std::shared_ptr<DiffPath>>(costOneEdges, DiffPath::make(temper::core::Int::add(leftIndex, 1), rightIndex, temper_globals::ChangeTypeDeletion.value, diffPath));
      }
    }
    if (rightIndex < rightLength) {
      int32_t addReachedIndex = temper::core::Int::add(leftIndex, temper::core::Int::mul(temper::core::Int::add(rightIndex, 1), reachedRowLength));
      if (!temper::core::DenseBitVector::get(reached, addReachedIndex)) {
        temper::core::DenseBitVector::set(reached, addReachedIndex, true);
        temper::core::Deque::add<std::shared_ptr<DiffPath>>(costOneEdges, DiffPath::make(leftIndex, temper::core::Int::add(rightIndex, 1), temper_globals::ChangeTypeAddition.value, diffPath));
      }
    }
  }
}

}  // namespace myers_diff

// init.cpp
#include <myers-diff/init.hpp>

#include <temper-core/console.hpp>
#include <temper-core/string.hpp>

namespace myers_diff {

namespace core = temper::core;

namespace temper_globals {

core::GlobalSlot<core::Str> ChangeTypeAddition;
core::GlobalSlot<core::Str> ChangeTypeDeletion;
core::GlobalSlot<core::Str> ChangeTypeUnchanged;

}  // namespace temper_globals

core::Str const & ChangeTypeAddition() {
  global_init_init();
  return temper_globals::ChangeTypeAddition.value;
}

core::Str const & ChangeTypeDeletion() {
  global_init_init();
  return temper_globals::ChangeTypeDeletion.value;
}

core::Str const & ChangeTypeUnchanged() {
  global_init_init();
  return temper_globals::ChangeTypeUnchanged.value;
}

core::Str formatPatch(std::shared_ptr<Patch<core::Str>> const & patch) {
  global_init_init();
  core::Str formatted = core::Str();
  std::shared_ptr<std::vector<std::shared_ptr<Change<core::Str>>>> changes = patch->changes;
  int32_t n = core::List::length<std::shared_ptr<Change<core::Str>>>(changes);
  {
    int32_t i = 0;
    while (i < n) {
      std::shared_ptr<Change<core::Str>> change = core::List::get<std::shared_ptr<Change<core::Str>>>(changes, i);
      core::Str changeType = change->type;
      std::shared_ptr<std::vector<core::Str>> items = change->items;
      core::Str prefix = changeType == temper_globals::ChangeTypeAddition.value ? TEMPER_STR("+") : changeType == temper_globals::ChangeTypeDeletion.value ? TEMPER_STR("-") : TEMPER_STR(" ");
      int32_t nItems = core::List::length<core::Str>(items);
      {
        int32_t j = 0;
        while (j < nItems) {
          core::Str item = core::List::get<core::Str>(items, j);
          formatted = formatted + prefix + item + "\n";
          j = core::Int::add(j, 1);
        }
      }
      i = core::Int::add(i, 1);
    }
  }
  return formatted;
}

std::shared_ptr<DiffPath> DiffPath::make(int32_t leftIndex, int32_t rightIndex, core::Str const & changeType, std::shared_ptr<DiffPath> const & previous) {
  std::shared_ptr<DiffPath> self = std::make_shared<DiffPath>();
  self->leftIndex = leftIndex;
  self->rightIndex = rightIndex;
  self->changeType = changeType;
  self->previous = previous;
  return self;
}

int32_t DiffPath::get_leftIndex() const {
  return this->leftIndex;
}

int32_t DiffPath::get_rightIndex() const {
  return this->rightIndex;
}

core::Str DiffPath::get_changeType() const {
  return this->changeType;
}

std::shared_ptr<DiffPath> DiffPath::get_previous() const {
  return this->previous;
}

void global_init_init() {
  core::init_once([] {
    temper_globals::ChangeTypeAddition.construct();
    temper_globals::ChangeTypeDeletion.construct();
    temper_globals::ChangeTypeUnchanged.construct();
    std::shared_ptr<core::Console::Type> console = core::Console::get_console();
    temper_globals::ChangeTypeAddition.value = TEMPER_STR("Addition");
    temper_globals::ChangeTypeDeletion.value = TEMPER_STR("Deletion");
    temper_globals::ChangeTypeUnchanged.value = TEMPER_STR("Unchanged");
    std::shared_ptr<std::vector<core::Str>> split = core::String::split(TEMPER_STR("Hello,\nWorld!"), TEMPER_STR("\n"));
    core::Console::log(console, myers_diff::formatPatch(myers_diff::diff<core::Str>(split, core::String::split(TEMPER_STR("Hello,\nCosmos!"), TEMPER_STR("\n")), [](core::Str a, core::Str b) -> bool { return a == b; })));
  });
}

}  // namespace myers_diff
