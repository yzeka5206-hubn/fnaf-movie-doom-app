#include "fnaf_outside_black.h"
#include <cassert>
#include <iostream>
using namespace FnafOutsideBlack;
int main()
{
    assert(!NightMap(nullptr));
    for (const char* n : {"", "N", "NIGHT", "NIGHT0", "NIGHT6", "NIGHT10", "OUTSIDE", "MIDNIGHT", "NIGHT1X"}) assert(!NightMap(n));
    for (const char* n : {"NIGHT1", "NIGHT2", "NIGHT3", "NIGHT4", "NIGHT5", "night1"}) assert(NightMap(n));
    for (const char* n : {"NIGHT1", "NIGHT2", "NIGHT3", "NIGHT4", "NIGHT5"}) {
        for (auto pair : {std::pair<int,int>(101,100),{103,102},{105,104},{106,108},{107,109}}) {
            assert(ExteriorPortal(n,pair.first,pair.second,-100,-950,3200,-8059));
            assert(!ExteriorPortal(n,pair.second,pair.first,3200,-8059,-100,-950));
            assert(!ExteriorPortal(n,pair.first,pair.second,-100,-950,0,0));
            assert(!ExteriorPortal("OUTSIDE",pair.first,pair.second,-100,-950,3200,-8059));
        }
        assert(ExteriorPortal(n,0,20,4000,2700,3200,-8300));
        assert(ExteriorPortal(n,0,21,4000,2700,3200,-8300));
        assert(!ExteriorPortal(n,6,7,-461,1100,-2115,-113));
        assert(!ExteriorPortal(n,101,100,-100,-950,3200,-7999));
        assert(ExteriorView(n,3200,-8100));
        assert(ExteriorView(n,4000,2700));
        assert(!ExteriorView(n,-100,-850));
        assert(!ExteriorView(n,0,0));
        assert(!ExteriorView(n,-2904,2364));
        assert(!ExteriorView(n,3200,2700));
        assert(!ExteriorView(n,3200,-2529));
    }
    assert(!ExteriorView("OUTSIDE",3200,-8100));
    assert(!ExteriorView("MIDNIGHT",3200,-8100));
    std::cout << "PASS night-only, forward outdoor portals, outdoor camera/view and fail-open boundaries\n";
}
