#pragma once
#include <cstdint>
#include <memory>
#include <vector>
#include "base_types.hpp"

namespace temper {
    namespace core {
        namespace DenseBitVector {

            inline std::shared_ptr<std::vector<bool>> make(int32_t size) {
                return std::make_shared<std::vector<bool>>(size, false);
            }

            inline bool get(std::shared_ptr<std::vector<bool>> bv, int32_t index) {
                int32_t sz = static_cast<int32_t>(bv->size());
                if (index < 0 || index >= sz) {
                    bubble<bool>("DenseBitVector index out of bounds");
                }
                return (*bv)[index];
            }

            inline void set(std::shared_ptr<std::vector<bool>> bv, int32_t index, bool value) {
                int32_t sz = static_cast<int32_t>(bv->size());
                if (index < 0 || index >= sz) {
                    bubble("DenseBitVector set index out of bounds");
                }
                (*bv)[index] = value;
            }

        }
    }
}
