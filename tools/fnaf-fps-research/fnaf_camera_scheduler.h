// Optional security-screen-only render throttling, never a simulation throttle.
#pragma once
#include <cstdint>
namespace FnafCameraScheduler {
struct State {
    std::uint64_t lastTime = 0;
    unsigned skipCount = 0;
    bool rendered = false;
    bool visible = false;
};
inline char Upper(char c) { return (c >= 'a' && c <= 'z') ? char(c - ('a' - 'A')) : c; }
inline bool IsSecurityScreen(const char* name) {
    if (!name) return false;
    const char prefix[] = "TCAMTEX";
    for (unsigned i = 0; i < 7; ++i) if (!name[i] || Upper(name[i]) != prefix[i]) return false;
    return name[7] >= '1' && name[7] <= '7' && name[8] == 0;
}
// At most 5 updates/s for selected screens, and never on two adjacent render
// frames. First display, a changed camera and reappearance update immediately.
// Skipped textures remain marked dirty for a later frame.
inline bool ShouldRender(State& state, bool saver, bool isSecurity, bool first,
                         bool currentlyVisible, std::uint64_t now) {
    if (!currentlyVisible) { state.visible = false; return false; }
    bool resume = !state.visible;
    state.visible = true;
    if (!saver || !isSecurity || first || resume || !state.rendered || now < state.lastTime) {
        state.lastTime = now; state.skipCount = 0; state.rendered = true; return true;
    }
    if (state.skipCount == 0) { state.skipCount = 1; return false; }
    if (now - state.lastTime < 200) return false;
    state.lastTime = now; state.skipCount = 0; return true;
}
}
