#pragma once
#include <algorithm>
#include <cstdint>
#include <memory>
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

            template<class Elem>
            void addAll(const std::shared_ptr<std::vector<Elem>>& list, const std::shared_ptr<std::vector<Elem>>& other) {
                list->insert(list->end(), other->begin(), other->end());
            }

            template<class Elem>
            void addAll(const std::shared_ptr<std::vector<Elem>>& list, const std::shared_ptr<std::vector<Elem>>& other, int32_t index) {
                if (index < 0 || index > static_cast<int32_t>(list->size())) {
                    bubble("list addall index out of bounds");
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
                typename std::vector<Elem>::iterator it = list->erase(
                    list->begin() + start,
                    list->begin() + start + deleteCount
                );
                list->insert(it, items->begin(), items->end());
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
                // exhibits if the user comparator is not a strict weak ordering.
                std::stable_sort(
                    list->begin(),
                    list->end(),
                    [&comparator](const Elem& a, const Elem& b) {
                        return comparator(a, b) < 0;
                    }
                );
            }

            template<class Elem>
            void clear(const std::shared_ptr<std::vector<Elem>>& list) {
                list->clear();
            }

        }
    }
}
