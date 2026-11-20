#pragma once
#include <algorithm>
#include <cstdint>
#include <memory>
#include <utility>
#include <vector>
#include "temper_bubble.hpp"
#include "base_types.hpp"
#include "nullable_param.hpp"

namespace temper {
    namespace core {
        namespace ListBuilder {

            template<class Elem>
            std::shared_ptr<std::vector<Elem>> make() {
                return std::make_shared<std::vector<Elem>>();
            }

            template<class Elem>
            void add(const std::shared_ptr<std::vector<Elem>>& list, typename NonDeduced<Elem>::type elem) {
                list->push_back(elem);
            }

            template<class Elem>
            void add(const std::shared_ptr<std::vector<Elem>>& list, typename NonDeduced<Elem>::type elem, int32_t index) {
                if (index < 0 || index > static_cast<int32_t>(list->size())) {
                    bubble("list add index out of bounds");
                }
                list->insert(list->begin() + index, elem);
            }

            // `addAll` and `splice` given the builder itself insert a copy
            // of its items as they were, since inserting a vector's own
            // range into it is undefined.

            template<class Elem>
            void addAll(const std::shared_ptr<std::vector<Elem>>& list, const std::shared_ptr<std::vector<Elem>>& other) {
                if (list == other) {
                    const std::vector<Elem> items(*other);
                    list->insert(list->end(), items.begin(), items.end());
                    return;
                }
                list->insert(list->end(), other->begin(), other->end());
            }

            template<class Elem>
            void addAll(const std::shared_ptr<std::vector<Elem>>& list, const std::shared_ptr<std::vector<Elem>>& other, int32_t index) {
                if (index < 0 || index > static_cast<int32_t>(list->size())) {
                    bubble("list addall index out of bounds");
                }
                if (list == other) {
                    const std::vector<Elem> items(*other);
                    list->insert(list->begin() + index, items.begin(), items.end());
                    return;
                }
                list->insert(list->begin() + index, other->begin(), other->end());
            }

            template<class Elem>
            Elem removeLast(const std::shared_ptr<std::vector<Elem>>& list) {
                if (list->empty()) {
                    bubble<Elem>("removeLast on empty list");
                }
                Elem last = list->back();
                list->pop_back();
                return last;
            }

            template<class Elem>
            void reverse(const std::shared_ptr<std::vector<Elem>>& list) {
                std::reverse(list->begin(), list->end());
            }

            template<class Elem>
            std::shared_ptr<std::vector<Elem>> splice(
                const std::shared_ptr<std::vector<Elem>>& list,
                NullableParam<int32_t> start_opt = NullableParam<int32_t>(),
                NullableParam<int32_t> deleteCount_opt = NullableParam<int32_t>(),
                const std::shared_ptr<std::vector<Elem>>& items = std::make_shared<std::vector<Elem>>()
            ) {
                int32_t start = start_opt.has_value ? start_opt.value : 0;
                int32_t sz = static_cast<int32_t>(list->size());
                int32_t deleteCount = deleteCount_opt.has_value ? deleteCount_opt.value : sz;
                if (start < 0) {
                    start = 0;
                }
                if (start > sz) {
                    start = sz;
                }
                if (deleteCount < 0) {
                    deleteCount = 0;
                }
                int32_t remaining = sz - start;
                if (deleteCount > remaining) {
                    deleteCount = remaining;
                }
                std::shared_ptr<std::vector<Elem>> removed = std::make_shared<std::vector<Elem>>(
                    list->begin() + start,
                    list->begin() + start + deleteCount
                );
                std::vector<Elem> copied;
                if (list == items) {
                    copied = *items;
                }
                const std::vector<Elem>& values = list == items ? copied : *items;
                typename std::vector<Elem>::iterator it = list->erase(
                    list->begin() + start,
                    list->begin() + start + deleteCount
                );
                list->insert(it, values.begin(), values.end());
                return removed;
            }

            template<class Elem>
            void set(const std::shared_ptr<std::vector<Elem>>& list, int32_t index, typename NonDeduced<Elem>::type value) {
                if (index < 0 || index >= static_cast<int32_t>(list->size())) {
                    bubble("list set index out of bounds");
                }
                (*list)[index] = value;
            }

            template<class Elem, class F>
            void sort(const std::shared_ptr<std::vector<Elem>>& list, F comparator) {
                // stable_sort (matching List::sorted) keeps sorting stable and cross-backend
                // consistent with Java/JS/Python, and avoids the undefined behaviour std::sort
                // exhibits if the user comparator is not a strict weak ordering. It sorts a
                // copy, since the comparison may change the builder, which would free the
                // storage being sorted; one that changes its length panics, as Java's and
                // Python's sorts fail.
                std::vector<Elem> items(*list);
                std::stable_sort(
                    items.begin(),
                    items.end(),
                    [&comparator](const Elem& a, const Elem& b) {
                        return comparator(a, b) < 0;
                    }
                );
                if (items.size() != list->size()) {
                    panic("ListBuilder changed length during sort");
                }
                *list = std::move(items);
            }

            template<class Elem>
            void clear(const std::shared_ptr<std::vector<Elem>>& list) {
                list->clear();
            }

        }
    }
}
