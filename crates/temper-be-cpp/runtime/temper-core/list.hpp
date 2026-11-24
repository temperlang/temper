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
                    index_out_of_bounds<Elem>(index, list->size());
                }
                return (*list)[index];
            }

            template<class Elem>
            Elem getOr(const std::shared_ptr<std::vector<Elem>>& list, int32_t index, Elem defaultValue) {
                int32_t sz = static_cast<int32_t>(list->size());
                return (index >= 0 && index < sz) ? (*list)[index] : defaultValue;
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

            // Those that call back (`map`, `filter`, `join` with a function,
            // `reduce`, and `reduceFrom`) walk a copy of the items, as the
            // interpreter walks a snapshot: the callback may change a
            // builder it walks, which would free or shrink the storage a
            // walk of the vector itself reads.

            template<class Elem, class F>
            auto map(
                const std::shared_ptr<std::vector<Elem>>& list,
                F fn
            ) -> std::shared_ptr<std::vector<decltype(fn(std::declval<const Elem&>()))>> {
                using R = decltype(fn(std::declval<const Elem&>()));
                const std::vector<Elem> items(*list);
                std::shared_ptr<std::vector<R>> result = std::make_shared<std::vector<R>>();
                result->reserve(items.size());
                for (const Elem& elem : items) {
                    result->push_back(fn(elem));
                }
                return result;
            }

            template<class Elem, class F>
            std::shared_ptr<std::vector<Elem>> filter(const std::shared_ptr<std::vector<Elem>>& list, F fn) {
                const std::vector<Elem> items(*list);
                std::shared_ptr<std::vector<Elem>> result = std::make_shared<std::vector<Elem>>();
                for (const Elem& elem : items) {
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
                const std::vector<Elem> items(*list);
                std::ostringstream oss;
                bool first = true;
                for (const Elem& elem : items) {
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
                const std::vector<Elem> items(*list);
                if (items.empty()) {
                    // As reading its first value does.
                    index_out_of_bounds<Elem>(0, 0);
                }
                Elem acc = items[0];
                for (std::size_t i = 1; i < items.size(); ++i) {
                    acc = fn(acc, items[i]);
                }
                return acc;
            }

            template<class Elem, class Acc, class F>
            Acc reduceFrom(const std::shared_ptr<std::vector<Elem>>& list, Acc init, F fn) {
                const std::vector<Elem> items(*list);
                Acc acc = init;
                for (const Elem& elem : items) {
                    acc = fn(acc, elem);
                }
                return acc;
            }

            // From `index`, none past the end; a negative index fails as
            // `get` does.
            template<class Elem, class Acc, class F>
            Acc reduceFromIndex(
                const std::shared_ptr<std::vector<Elem>>& list,
                Acc init,
                int32_t index,
                F fn
            ) {
                if (index < 0) {
                    get(list, index);
                }
                const std::vector<Elem> items(*list);
                Acc acc = init;
                for (size_t i = static_cast<size_t>(index); i < items.size(); ++i) {
                    acc = fn(acc, items[i]);
                }
                return acc;
            }

        }
    }
}
