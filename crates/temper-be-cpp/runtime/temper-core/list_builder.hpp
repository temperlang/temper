#pragma once
#include <algorithm>
#include <cstdint>
#include <memory>
#include <vector>
#include "temper_panic.hpp"
#include "base_types.hpp"
#include "nullable_param.hpp"
#include "expected.hpp"
#include "list.hpp"

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
                    panic_out_of_bounds(index, static_cast<int64_t>(list->size()));
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
                    panic_out_of_bounds(index, static_cast<int64_t>(list->size()));
                }
                list->insert(list->begin() + index, other->begin(), other->end());
            }

            template<class Elem>
            Elem removeLast(const std::shared_ptr<std::vector<Elem>>& list) {
                if (list->empty()) {
                    panic<Elem>("removeLast on an empty ListBuilder");
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
                    panic_out_of_bounds(index, static_cast<int64_t>(list->size()));
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

            // `sort` with a comparison that may fail (RFC 164's failure
            // parameters): on a failure, the list is as it was.
            template<class Elem, class F>
            auto trySort(const std::shared_ptr<std::vector<Elem>>& list, F comparator)
                -> Expected<void, List::FailureOf<F, const Elem&, const Elem&>> {
                std::vector<Elem> items = *list;
                auto sorted = List::try_merge_sort(items, comparator);
                if (!sorted.has_value()) {
                    return unexpected(sorted.error());
                }
                *list = std::move(items);
                return {};
            }

            template<class Elem>
            void clear(const std::shared_ptr<std::vector<Elem>>& list) {
                list->clear();
            }

        }
    }
}
