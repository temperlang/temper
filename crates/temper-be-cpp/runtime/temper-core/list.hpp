#pragma once
#include <algorithm>
#include <cstdint>
#include <initializer_list>
#include <memory>
#include <sstream>
#include <string>
#include <type_traits>
#include <utility>
#include <vector>
#include "temper_bubble.hpp"
#include "base_types.hpp"
#include "any_value.hpp"
#include "expected.hpp"

namespace temper {
    namespace core {
        namespace List {

            template<class Elem, class Arg>
            typename std::enable_if<std::is_convertible<Arg, Elem>::value, Elem>::type
            convert_elem(Arg&& arg) {
                return static_cast<Elem>(std::forward<Arg>(arg));
            }

            template<class Elem, class Arg>
            typename std::enable_if<
                !std::is_convertible<Arg, Elem>::value
                && std::is_same<Elem, std::shared_ptr<AnyValueBase>>::value,
                Elem
            >::type
            convert_elem(Arg&& arg) {
                return any_box(std::forward<Arg>(arg));
            }

            // Room for a list literal's items, but none for Booleans: a vector<bool>
            // packs 64 to a word, so its first push_back makes room for most literals,
            // and g++ 12 to 14 at -O3 report a false -Warray-bounds in libstdc++'s
            // vector<bool> reserve, failing -Wall -Werror release builds.
            template<class Elem>
            void reserve_items(std::vector<Elem>& list, std::size_t n) {
                list.reserve(n);
            }

            inline void reserve_items(std::vector<bool>&, std::size_t) {}

            template<class Elem, class... Args>
            std::shared_ptr<std::vector<Elem>> make(Args... args) {
                std::shared_ptr<std::vector<Elem>> list = std::make_shared<std::vector<Elem>>();
                reserve_items(*list, sizeof...(Args));
                // Expand the pack in a braced list, which runs in order, rather than
                // recursing, which copied the rest of the pack at each step and ran past
                // the compiler's template depth for long list literals.
                int expand[] = {0, (list->push_back(convert_elem<Elem>(std::move(args))), 0)...};
                (void) expand;
                return list;
            }

            template<
                class Base,
                class Derived,
                class = typename std::enable_if<
                    !std::is_same<Base, Derived>::value
                    && std::is_convertible<Derived, Base>::value
                >::type
            >
            std::shared_ptr<std::vector<Base>> upcast(const std::shared_ptr<std::vector<Derived>>& src) {
                std::shared_ptr<std::vector<Base>> result = std::make_shared<std::vector<Base>>();
                result->reserve(src->size());
                for (const Derived& elem : *src) {
                    result->push_back(elem);
                }
                return result;
            }

            template<class T>
            std::shared_ptr<std::vector<T>> upcast(const std::shared_ptr<std::vector<T>>& src) {
                return src;
            }

            template<class Elem>
            bool isEmpty(const std::shared_ptr<std::vector<Elem>>& list) {
                return list->empty();
            }

            template<class Elem>
            int32_t length(const std::shared_ptr<std::vector<Elem>>& list) {
                return static_cast<int32_t>(list->size());
            }

            template<class Elem>
            Elem get(const std::shared_ptr<std::vector<Elem>>& list, int32_t index) {
                int32_t sz = static_cast<int32_t>(list->size());
                if (index < 0 || index >= sz) {
                    panic_out_of_bounds<Elem>(index, sz);
                }
                return (*list)[index];
            }

            template<class Elem>
            Elem getOr(const std::shared_ptr<std::vector<Elem>>& list, int32_t index, Elem defaultValue) {
                int32_t sz = static_cast<int32_t>(list->size());
                return (index >= 0 && index < sz) ? (*list)[index] : defaultValue;
            }

            // The item, or `fallback`, which may be null where `Elem`
            // isn't nullable: `getOr` given a null.
            template<class Elem>
            NullableParam<Elem> getOrNull(
                const std::shared_ptr<std::vector<Elem>>& list,
                int32_t index,
                NullableParam<Elem> fallback
            ) {
                int32_t sz = static_cast<int32_t>(list->size());
                if (index >= 0 && index < sz) {
                    return (*list)[index];
                }
                return fallback;
            }

            template<class Elem, class F>
            void forEach(const std::shared_ptr<std::vector<Elem>>& list, F fn) {
                for (const Elem& elem : *list) {
                    fn(elem);
                }
            }

            template<class Elem>
            std::shared_ptr<std::vector<Elem>> toList(const std::shared_ptr<std::vector<Elem>>& list) {
                return std::make_shared<std::vector<Elem>>(*list);
            }

            template<class Elem>
            std::shared_ptr<std::vector<Elem>> toListBuilder(const std::shared_ptr<std::vector<Elem>>& list) {
                return std::make_shared<std::vector<Elem>>(*list);
            }

            template<class Elem, class F>
            auto map(
                const std::shared_ptr<std::vector<Elem>>& list,
                F fn
            ) -> std::shared_ptr<std::vector<decltype(fn(std::declval<const Elem&>()))>> {
                using R = decltype(fn(std::declval<const Elem&>()));
                std::shared_ptr<std::vector<R>> result = std::make_shared<std::vector<R>>();
                for (const Elem& elem : *list) {
                    result->push_back(fn(elem));
                }
                return result;
            }

            template<class Elem, class F>
            std::shared_ptr<std::vector<Elem>> filter(const std::shared_ptr<std::vector<Elem>>& list, F fn) {
                std::shared_ptr<std::vector<Elem>> result = std::make_shared<std::vector<Elem>>();
                for (const Elem& elem : *list) {
                    if (fn(elem)) {
                        result->push_back(elem);
                    }
                }
                return result;
            }

            template<class Elem>
            Str join(const std::shared_ptr<std::vector<Elem>>& list, const Str& separator) {
                std::ostringstream oss;
                bool first = true;
                for (const Elem& elem : *list) {
                    if (!first) {
                        oss << separator;
                    }
                    oss << elem;
                    first = false;
                }
                return Str::written_to(oss.rdbuf());
            }

            template<class Elem, class F>
            Str join(const std::shared_ptr<std::vector<Elem>>& list, const Str& separator, F fn) {
                std::ostringstream oss;
                bool first = true;
                for (const Elem& elem : *list) {
                    if (!first) {
                        oss << separator;
                    }
                    oss << fn(elem);
                    first = false;
                }
                return Str::written_to(oss.rdbuf());
            }

            template<class Elem, class F>
            std::shared_ptr<std::vector<Elem>> sorted(const std::shared_ptr<std::vector<Elem>>& list, F comparator) {
                std::shared_ptr<std::vector<Elem>> result = std::make_shared<std::vector<Elem>>(*list);
                std::stable_sort(
                    result->begin(),
                    result->end(),
                    [&comparator](const Elem& a, const Elem& b) {
                        return comparator(a, b) < 0;
                    }
                );
                return result;
            }

            template<class Elem>
            int32_t indexOf(const std::shared_ptr<std::vector<Elem>>& list, Elem value) {
                int32_t sz = static_cast<int32_t>(list->size());
                for (int32_t i = 0; i < sz; ++i) {
                    if ((*list)[i] == value) {
                        return i;
                    }
                }
                return -1;
            }

            template<class Elem>
            std::shared_ptr<std::vector<Elem>> slice(
                const std::shared_ptr<std::vector<Elem>>& list,
                int32_t start,
                int32_t end_pos
            ) {
                int32_t sz = static_cast<int32_t>(list->size());
                if (start < 0) {
                    start = 0;
                }
                if (end_pos > sz) {
                    end_pos = sz;
                }
                if (start >= end_pos) {
                    return std::make_shared<std::vector<Elem>>();
                }
                return std::make_shared<std::vector<Elem>>(
                    list->begin() + start,
                    list->begin() + end_pos
                );
            }

            template<class Elem, class F>
            Elem reduce(const std::shared_ptr<std::vector<Elem>>& list, F fn) {
                if (list->empty()) {
                    // Core's reduce starts from `this[0]`.
                    panic_out_of_bounds<Elem>(0, 0);
                }
                Elem acc = (*list)[0];
                int32_t sz = static_cast<int32_t>(list->size());
                for (int32_t i = 1; i < sz; ++i) {
                    acc = fn(acc, (*list)[i]);
                }
                return acc;
            }

            template<class Elem, class Acc, class F>
            Acc reduceFrom(const std::shared_ptr<std::vector<Elem>>& list, Acc init, F fn) {
                Acc acc = init;
                for (const Elem& elem : *list) {
                    acc = fn(acc, elem);
                }
                return acc;
            }

            // Each `try` form takes a callback that returns an `Expected`
            // (RFC 164's failure parameters), stops at its first failure,
            // and returns it, else what its plain form gives, in an
            // `Expected` of the callback's failure type.

            // The failure type of what `F` returns given `Args`.
            template<class F, class... Args>
            using FailureOf = typename decltype(std::declval<F&>()(std::declval<Args>()...))::error_type;

            // The value type of what `F` returns given `Args`.
            template<class F, class... Args>
            using ValueOf = typename decltype(std::declval<F&>()(std::declval<Args>()...))::value_type;

            template<class Elem, class F>
            auto tryMap(const std::shared_ptr<std::vector<Elem>>& list, F fn)
                -> Expected<std::shared_ptr<std::vector<ValueOf<F, const Elem&>>>, FailureOf<F, const Elem&>> {
                auto result = std::make_shared<std::vector<ValueOf<F, const Elem&>>>();
                for (const Elem& elem : *list) {
                    auto r = fn(elem);
                    if (!r.has_value()) {
                        return unexpected(r.error());
                    }
                    result->push_back(std::move(*r));
                }
                return result;
            }

            template<class Elem, class F>
            auto tryFilter(const std::shared_ptr<std::vector<Elem>>& list, F fn)
                -> Expected<std::shared_ptr<std::vector<Elem>>, FailureOf<F, const Elem&>> {
                auto result = std::make_shared<std::vector<Elem>>();
                for (const Elem& elem : *list) {
                    auto r = fn(elem);
                    if (!r.has_value()) {
                        return unexpected(r.error());
                    }
                    if (*r) {
                        result->push_back(elem);
                    }
                }
                return result;
            }

            template<class Elem, class F>
            auto tryJoin(const std::shared_ptr<std::vector<Elem>>& list, const Str& separator, F fn)
                -> Expected<Str, FailureOf<F, const Elem&>> {
                std::ostringstream oss;
                bool first = true;
                for (const Elem& elem : *list) {
                    auto r = fn(elem);
                    if (!r.has_value()) {
                        return unexpected(r.error());
                    }
                    if (!first) {
                        oss << separator;
                    }
                    oss << *r;
                    first = false;
                }
                return Str::written_to(oss.rdbuf());
            }

            template<class Elem, class Acc, class F>
            auto tryReduceFrom(const std::shared_ptr<std::vector<Elem>>& list, Acc init, F fn)
                -> Expected<Acc, FailureOf<F, const Acc&, const Elem&>> {
                Acc acc = init;
                for (const Elem& elem : *list) {
                    auto r = fn(acc, elem);
                    if (!r.has_value()) {
                        return unexpected(r.error());
                    }
                    acc = *r;
                }
                return acc;
            }

            template<class Elem, class F>
            auto tryReduce(const std::shared_ptr<std::vector<Elem>>& list, F fn)
                -> Expected<Elem, FailureOf<F, const Elem&, const Elem&>> {
                if (list->empty()) {
                    // Core's reduce starts from `this[0]`.
                    panic_out_of_bounds<Elem>(0, 0);
                }
                Elem acc = (*list)[0];
                int32_t sz = static_cast<int32_t>(list->size());
                for (int32_t i = 1; i < sz; ++i) {
                    auto r = fn(acc, (*list)[i]);
                    if (!r.has_value()) {
                        return unexpected(r.error());
                    }
                    acc = *r;
                }
                return acc;
            }

            // A stable merge sort by a comparison that may fail, which
            // stops at its first failure: `std::stable_sort`'s behavior is
            // undefined where comparisons stop being consistent, as one
            // that gives up partway would be.
            template<class Elem, class F>
            auto try_merge_sort(std::vector<Elem>& items, F& compare)
                -> Expected<void, FailureOf<F, const Elem&, const Elem&>> {
                if (items.size() < 2) {
                    return {};
                }
                std::vector<Elem> right(items.begin() + items.size() / 2, items.end());
                items.erase(items.begin() + items.size() / 2, items.end());
                for (std::vector<Elem>* half : {&items, &right}) {
                    auto sorted = try_merge_sort(*half, compare);
                    if (!sorted.has_value()) {
                        return unexpected(sorted.error());
                    }
                }
                std::vector<Elem> merged;
                merged.reserve(items.size() + right.size());
                std::size_t i = 0;
                std::size_t j = 0;
                while (i < items.size() && j < right.size()) {
                    auto c = compare(items[i], right[j]);
                    if (!c.has_value()) {
                        return unexpected(c.error());
                    }
                    // The left's first on ties, so it's stable.
                    merged.push_back(*c > 0 ? right[j++] : items[i++]);
                }
                merged.insert(merged.end(), items.begin() + i, items.end());
                merged.insert(merged.end(), right.begin() + j, right.end());
                items = std::move(merged);
                return {};
            }

            template<class Elem, class F>
            auto trySorted(const std::shared_ptr<std::vector<Elem>>& list, F comparator)
                -> Expected<std::shared_ptr<std::vector<Elem>>, FailureOf<F, const Elem&, const Elem&>> {
                auto result = std::make_shared<std::vector<Elem>>(*list);
                auto sorted = try_merge_sort(*result, comparator);
                if (!sorted.has_value()) {
                    return unexpected(sorted.error());
                }
                return result;
            }

        }
    }
}
