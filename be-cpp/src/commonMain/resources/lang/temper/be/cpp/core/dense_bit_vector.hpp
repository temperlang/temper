#pragma once
#include <cstdint>
#include <memory>
#include <vector>
#include "temper_bubble.hpp"
#include "base_types.hpp"

namespace temper {
    namespace core {
        namespace DenseBitVector {

            inline std::shared_ptr<std::vector<bool>> make(int32_t size) {
                return std::make_shared<std::vector<bool>>(size, false);
            }

            // Bits outside storage are false, as core.temper defines.
            inline bool get(std::shared_ptr<std::vector<bool>> bv, int32_t index) {
                int32_t sz = static_cast<int32_t>(bv->size());
                if (index < 0 || index >= sz) {
                    return false;
                }
                return (*bv)[index];
            }

            inline void set(std::shared_ptr<std::vector<bool>> bv, int32_t index, bool value) {
                if (index < 0) {
                    bubble("DenseBitVector set index out of bounds");
                }
                if (index >= static_cast<int32_t>(bv->size())) {
                    if (!value) {
                        return;
                    }
                    bv->resize(static_cast<size_t>(index) + 1, false);
                }
                (*bv)[index] = value;
            }

        }
    }
}
