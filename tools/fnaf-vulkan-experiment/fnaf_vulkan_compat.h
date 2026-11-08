// FNAF mobile Vulkan compatibility; no GL or API emulation.
#pragma once
#include <cstdint>
namespace FnafVulkanCompat {
inline bool DeviceMeetsRequirements(bool mobile, bool anisotropy, bool fragmentAtomics,
                                    bool multiDrawIndirect, bool independentBlend) {
    return anisotropy && fragmentAtomics && independentBlend && (mobile || multiDrawIndirect);
}
template<class Issue>
inline void IndirectDraws(std::uint64_t offset, std::uint32_t count, std::uint32_t stride,
                          bool multiDrawEnabled, Issue issue) {
    if (multiDrawEnabled || count <= 1) { issue(offset, count, stride); return; }
    for (std::uint32_t i = 0; i < count; ++i)
        issue(offset + std::uint64_t(i) * stride, 1, stride);
}
}
