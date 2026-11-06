#pragma once

// Render-only experiment for the exact five FNAF Movie Doom night maps.
// Deliberately fail open for unknown map names, portals and camera positions.
// These tests do not alter the map's linedefs, collision, sounds or actors.
namespace FnafOutsideBlack
{
inline bool NightMap(const char* name)
{
    if (!name) return false;
    const char expected[] = "NIGHT";
    for (int i = 0; i < 5; ++i)
    {
        char c = name[i];
        if (!c) return false;
        if (c >= 'a' && c <= 'z') c -= 'a' - 'A';
        if (c != expected[i]) return false;
    }
    return name[5] >= '1' && name[5] <= '5' && name[6] == '\0';
}

// These areas are the separate outdoor world and the eight isolated outdoor
// display/camera cells in the original NIGHT1..5 maps. A player/camera view
// here can be cleared without traversing its BSP. Other positions fail open.
inline bool ExteriorView(const char* map, double x, double y)
{
    if (!NightMap(map)) return false;
    const bool externalWorld = x >= -4900 && x <= 10000 && y >= -13400 && y <= -2530;
    const bool outdoorCells = x >= 3640 && x <= 5730 && y >= 2540 && y <= 3670;
    return externalWorld || outdoorCells;
}

// Exact interior-to-outside portals shared by NIGHT1..5, plus the portals in
// outdoor display cells. Both source/destination tags AND approximate
// coordinates must match. Reverse portals back toward the interior never match.
inline bool ExteriorPortal(const char* map, int sourceId, int destinationId,
                           double sx, double sy, double dx, double dy)
{
    if (!NightMap(map)) return false;
    const bool entrancePair =
        (sourceId == 101 && destinationId == 100) ||
        (sourceId == 103 && destinationId == 102) ||
        (sourceId == 105 && destinationId == 104) ||
        (sourceId == 106 && destinationId == 108) ||
        (sourceId == 107 && destinationId == 109);
    if (entrancePair && sx >= -700 && sx <= 100 && sy >= -970 && sy <= -930 &&
        dx >= 2600 && dx <= 3450 && dy >= -8100 && dy <= -8000) return true;
    const bool cellPair = sourceId == 0 && (destinationId == 20 || destinationId == 21);
    return cellPair && sx >= 3600 && sx <= 5750 && sy >= 2500 && sy <= 3700 &&
           dx >= 3100 && dx <= 3600 && dy >= -8600 && dy <= -8100;
}
} // namespace FnafOutsideBlack
