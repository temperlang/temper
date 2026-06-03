#pragma once
#include <algorithm>
#include <cstdint>
#include <initializer_list>
#include <memory>
#include <sstream>
#include <string>
#include <type_traits>
#include <vector>
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

            template<class Elem>
            void make_push(std::shared_ptr<std::vector<Elem>>) {}

            template<class Elem, class First, class... Rest>
            void make_push(std::shared_ptr<std::vector<Elem>> list, First first, Rest... rest) {
                list->push_back(convert_elem<Elem>(first));
                make_push(list, rest...);
            }

            template<class Elem, class... Args>
            std::shared_ptr<std::vector<Elem>> make(Args... args) {
                std::shared_ptr<std::vector<Elem>> list = std::make_shared<std::vector<Elem>>();
                make_push<Elem>(list, args...);
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
            std::shared_ptr<std::vector<Base>> upcast(std::shared_ptr<std::vector<Derived>> src) {
                std::shared_ptr<std::vector<Base>> result = std::make_shared<std::vector<Base>>();
                result->reserve(src->size());
                for (const Derived& elem : *src) {
                    result->push_back(elem);
                }
                return result;
            }

            template<class T>
            std::shared_ptr<std::vector<T>> upcast(std::shared_ptr<std::vector<T>> src) {
                return src;
            }

            template<class Elem>
            bool isEmpty(std::shared_ptr<std::vector<Elem>> list) {
                return list->empty();
            }

            template<class Elem>
            int32_t length(std::shared_ptr<std::vector<Elem>> list) {
                return static_cast<int32_t>(list->size());
            }

            template<class Elem>
            Elem get(std::shared_ptr<std::vector<Elem>> list, int32_t index) {
                int32_t sz = static_cast<int32_t>(list->size());
                if (index < 0 || index >= sz) {
                    bubble<Elem>("list index out of bounds");
                }
                return (*list)[index];
            }

            template<class Elem>
            Elem getOr(std::shared_ptr<std::vector<Elem>> list, int32_t index, Elem defaultValue) {
                int32_t sz = static_cast<int32_t>(list->size());
                return (index >= 0 && index < sz) ? (*list)[index] : defaultValue;
            }

            template<class Elem, class F>
            void forEach(std::shared_ptr<std::vector<Elem>> list, F fn) {
                for (const Elem& elem : *list) {
                    fn(elem);
                }
            }

            template<class Elem>
            std::shared_ptr<std::vector<Elem>> toList(std::shared_ptr<std::vector<Elem>> list) {
                return std::make_shared<std::vector<Elem>>(*list);
            }

            template<class Elem>
            std::shared_ptr<std::vector<Elem>> toListBuilder(std::shared_ptr<std::vector<Elem>> list) {
                return std::make_shared<std::vector<Elem>>(*list);
            }

            template<class Elem, class F>
            std::shared_ptr<std::vector<typename std::result_of<F(Elem)>::type>> map(
                std::shared_ptr<std::vector<Elem>> list,
                F fn
            ) {
                typedef typename std::result_of<F(Elem)>::type R;
                std::shared_ptr<std::vector<R>> result = std::make_shared<std::vector<R>>();
                for (const Elem& elem : *list) {
                    result->push_back(fn(elem));
                }
                return result;
            }

            template<class Elem, class F>
            std::shared_ptr<std::vector<Elem>> filter(std::shared_ptr<std::vector<Elem>> list, F fn) {
                std::shared_ptr<std::vector<Elem>> result = std::make_shared<std::vector<Elem>>();
                for (const Elem& elem : *list) {
                    if (fn(elem)) {
                        result->push_back(elem);
                    }
                }
                return result;
            }

            template<class Elem, class F>
            std::shared_ptr<std::vector<typename std::result_of<F(Elem)>::type>> mapDropping(
                std::shared_ptr<std::vector<Elem>> list,
                F fn
            ) {
                typedef typename std::result_of<F(Elem)>::type R;
                std::shared_ptr<std::vector<R>> result = std::make_shared<std::vector<R>>();
                for (const Elem& elem : *list) {
                    try {
                        result->push_back(fn(elem));
                    } catch (const TemperBubble&) {}
                }
                return result;
            }

            template<class Elem>
            std::string join(std::shared_ptr<std::vector<Elem>> list, std::string separator) {
                std::ostringstream oss;
                bool first = true;
                for (const Elem& elem : *list) {
                    if (!first) {
                        oss << separator;
                    }
                    oss << elem;
                    first = false;
                }
                return oss.str();
            }

            template<class Elem, class F>
            std::string join(std::shared_ptr<std::vector<Elem>> list, std::string separator, F fn) {
                std::ostringstream oss;
                bool first = true;
                for (const Elem& elem : *list) {
                    if (!first) {
                        oss << separator;
                    }
                    oss << fn(elem);
                    first = false;
                }
                return oss.str();
            }

            template<class Elem, class F>
            std::shared_ptr<std::vector<Elem>> sorted(std::shared_ptr<std::vector<Elem>> list, F comparator) {
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
            int32_t indexOf(std::shared_ptr<std::vector<Elem>> list, Elem value) {
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
                std::shared_ptr<std::vector<Elem>> list,
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
            Elem reduce(std::shared_ptr<std::vector<Elem>> list, F fn) {
                if (list->empty()) {
                    bubble<Elem>("reduce on empty list");
                }
                Elem acc = (*list)[0];
                int32_t sz = static_cast<int32_t>(list->size());
                for (int32_t i = 1; i < sz; ++i) {
                    acc = fn(acc, (*list)[i]);
                }
                return acc;
            }

            template<class Elem, class Acc, class F>
            Acc reduceFrom(std::shared_ptr<std::vector<Elem>> list, Acc init, F fn) {
                Acc acc = init;
                for (const Elem& elem : *list) {
                    acc = fn(acc, elem);
                }
                return acc;
            }

        }
    }
}
