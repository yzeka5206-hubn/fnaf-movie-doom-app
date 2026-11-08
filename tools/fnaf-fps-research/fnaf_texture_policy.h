// Low-profile world-texture mipmaps: preserve nearest magnification and all
// unselected categories, honor platform filter restrictions, and fail safe on
// GLES2 devices without full NPOT mipmap support.
#pragma once
namespace FnafTexturePolicy {
inline bool UseLowMip(bool requested, bool visual, int sourceFilter,
                      bool filteringAllowed, bool supportsNpotMipmaps) {
    return requested && visual && sourceFilter == 0 && filteringAllowed && supportsNpotMipmaps;
}
inline int Filter(bool requested, bool visual, int sourceFilter,
                  bool filteringAllowed, bool supportsNpotMipmaps) {
    if (!filteringAllowed) return 0;
    return UseLowMip(requested, visual, sourceFilter, filteringAllowed, supportsNpotMipmaps)
        ? 1 : sourceFilter;
}
}
