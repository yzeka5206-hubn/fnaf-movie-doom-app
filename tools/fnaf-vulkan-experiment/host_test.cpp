#include "fnaf_vulkan_compat.h"
#include <cassert>
#include <cstdint>
#include <cstdio>
#include <tuple>
#include <vector>

int main() {
    using FnafVulkanCompat::DeviceMeetsRequirements;
    // Published Xiaomi-11T report: the three remaining required bits are true,
    // multiDrawIndirect is false. Do not require a bit that is not used.
    assert(DeviceMeetsRequirements(true, true, true, false, true));
    assert(!DeviceMeetsRequirements(false, true, true, false, true));
    for (int i=0; i<3; ++i) {
        bool anisotropy = i != 0, atomics = i != 1, independent = i != 2;
        assert(!DeviceMeetsRequirements(true, anisotropy, atomics, false, independent));
    }
    using Call = std::tuple<std::uint64_t, std::uint32_t, std::uint32_t>;
    for (auto enabled : {false, true}) for (std::uint32_t count=0; count<300; ++count)
      for (std::uint32_t stride : {16u, 20u, 64u, 512u}) {
        constexpr std::uint64_t offset = (std::uint64_t(1)<<33) + 256;
        std::vector<Call> calls;
        FnafVulkanCompat::IndirectDraws(offset, count, stride, enabled,
          [&](auto off, auto n, auto step){ calls.emplace_back(off,n,step); });
        if (enabled || count<=1) {
            assert(calls.size()==1 && calls[0]==Call(offset,count,stride));
        } else {
            assert(calls.size()==count);
            for (std::uint32_t i=0; i<count; ++i)
              assert(calls[i]==Call(offset+std::uint64_t(i)*stride,1,stride));
        }
      }
    std::puts("PASS Mali-style feature selection, required feature rejection and 2,400 indirect draw decompositions");
}
