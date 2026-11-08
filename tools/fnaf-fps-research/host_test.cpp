#include "fnaf_camera_scheduler.h"
#include "fnaf_light_cache.h"
#include "fnaf_texture_policy.h"
#include "fnaf_spot_cache.h"
#include <cmath>
#include <cassert>
#include <cstdint>
#include <cstring>
#include <iostream>
#include <random>
#include <vector>

int main() {
    using namespace FnafCameraScheduler;
    assert(IsSecurityScreen("TCAMTEX1")); assert(IsSecurityScreen("tcAmTeX7"));
    for (const char* s : {"TCAMTEX0", "TCAMTEX8", "TCAMTEX1X", "TCAMTEX", "DIGITAL1", "CAMTEX", "", "T"}) assert(!IsSecurityScreen(s));
    assert(!IsSecurityScreen(nullptr));
    State s;
    assert(ShouldRender(s,true,true,true,true,1000));  // first always draws
    assert(!ShouldRender(s,true,true,false,true,1016)); // no adjacent frame
    assert(!ShouldRender(s,true,true,false,true,1199)); // <= 5 fps
    assert(ShouldRender(s,true,true,false,true,1200));
    assert(!ShouldRender(s,true,true,false,true,1534)); // skips even at 3 fps
    assert(ShouldRender(s,true,true,false,true,1867));
    assert(ShouldRender(s,true,true,true,true,1868)); // camera/FOV assignment changed
    assert(!ShouldRender(s,true,true,false,false,1869)); // no screen visible
    assert(ShouldRender(s,true,true,false,true,1870));  // reappearing => immediate
    assert(ShouldRender(s,true,true,false,true,2));     // clock reset fails open
    assert(ShouldRender(s,false,true,false,true,3));    // option off
    assert(ShouldRender(s,true,false,false,true,4));   // menus / other cameras unaffected
    State sixty; int updates=0;
    for (unsigned i=0;i<600;++i) updates+=ShouldRender(sixty,true,true,false,true,10000+(i*1000/60));
    assert(updates>=48 && updates<=51); // ~50 in ten seconds, versus 600 input frames
    State slow; updates=0;
    for (unsigned i=0;i<30;++i) updates+=ShouldRender(slow,true,true,false,true,10000+(i*1000/3));
    assert(updates==15); // still updates every other frame if main render is very slow
    using FnafTexturePolicy::Filter; using FnafTexturePolicy::UseLowMip;
    assert(Filter(true,true,0,true,true)==1);
    assert(!UseLowMip(false,true,0,true,true));
    assert(!UseLowMip(true,false,0,true,true)); // UI/canvas/indexed excluded by caller
    assert(!UseLowMip(true,true,0,false,true)); // callback disallows filtering
    assert(!UseLowMip(true,true,0,true,false)); // GLES2 without NPOT capability
    assert(Filter(true,true,2,true,true)==2); // higher preset unchanged
    assert(Filter(false,true,0,true,true)==0); // normal=previous
    std::mt19937 rng(0xFAF0123); FnafLightCache::UploadCache cache;
    std::vector<float> a(32*16), b;
    for(int round=0;round<10000;++round) {
        for(auto &v:a) { uint32_t bits=rng(); std::memcpy(&v,&bits,sizeof(v)); }
        auto n=std::size_t(rng()%a.size()+1);
        cache.Remember(a.data(),n); assert(cache.Matches(a.data(),n));
        b=a; uint32_t bits; std::memcpy(&bits,&b[n-1],sizeof(bits)); bits^=1; std::memcpy(&b[n-1],&bits,sizeof(bits));
        assert(!cache.Matches(b.data(),n)); assert(!cache.Matches(a.data(),n-1));
        cache.Invalidate(); assert(!cache.Matches(a.data(),n));
    }
    FnafSpotCache::Cache spot; bool reused = false; unsigned computations=0;
    auto fn=[&](){ ++computations; return FnafSpotCache::Result{1,2,3,4,5}; };
    auto first=spot.Get(10,25,90,-5,true,fn,reused); assert(!reused && computations==1);
    auto again=spot.Get(10,25,90,-5,true,fn,reused); assert(reused && computations==1);
    assert(std::memcmp(&first,&again,sizeof(first))==0);
    (void)spot.Get(10,25,91,-5,true,fn,reused); assert(!reused && computations==2);
    (void)spot.Get(10,25,91,-5,false,fn,reused); assert(!reused && computations==3);
    // Signed zero angles remain distinct to preserve exact floating behavior.
    (void)spot.Get(0,25,0,0,true,fn,reused); assert(!reused);
    (void)spot.Get(-0.0,25,0,0,true,fn,reused); assert(!reused);
    for (int i=0;i<10000;++i) {
        const double a=(int(rng()%3600)-1800)*0.1, b=(int(rng()%3600)-1800)*0.1;
        auto compute=[&](){ return FnafSpotCache::Result{float(std::cos(a)),float(std::sin(b)),float(a*b),float(a),float(b)}; };
        auto direct=compute(); auto cached=spot.Get(a,b,0.0,-0.0,true,compute,reused);
        assert(std::memcmp(&direct,&cached,sizeof(direct))==0);
        cached=spot.Get(a,b,0.0,-0.0,true,compute,reused);
        assert(reused && std::memcmp(&direct,&cached,sizeof(direct))==0);
    }
    std::cout << "PASS spotlight cache: 10,000 randomized exact results, angle changes, bypass and +/-0\n";
    std::cout << "PASS exact light cache: 10,000 randomized bit patterns, size/content mutations and invalidation\n";
    std::cout << "PASS selective mipmap policy and CCTV scheduler: 60fps/3fps, initial/reassigned/reappearing/other cameras\n";
}
