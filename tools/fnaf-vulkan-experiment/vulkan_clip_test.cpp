// Exercise the exact shipped clip-distance shader branch on software Vulkan.
// Feature-disabled path approximates the Mali report, not actual Mali testing.
#define main fnaf_indirect_test_main
#include "vulkan_indirect_test.cpp"
#undef main
int main(int argc, char** argv) {
    if (argc != 5) { std::fprintf(stderr,"usage: clip-test native-vert native-frag fallback-vert fallback-frag\n"); return 2; }
    try {
        auto native = render(false, false, argv[1], argv[2], true);
        auto fallback = render(false, false, argv[3], argv[4], false);
        if (native.size() != fallback.size()) throw std::runtime_error("different image sizes");
        std::size_t changed = 0, compared = native.size()/4;
        for (std::size_t i=0; i<native.size(); i+=4)
            if (std::memcmp(native.data()+i, fallback.data()+i, 4) != 0) ++changed;
        // Hardware clip and fragment discard may differ at primitive edges.
        if (changed > compared / 100) throw std::runtime_error("clipping mismatch exceeds 1% of image pixels");
        if (errors) throw std::runtime_error("Vulkan validation errors occurred");
        std::printf("PASS shipped clip shader, hardware feature on versus fallback off: %zu/%zu pixels differ; validation errors=0\n", changed, compared);
    } catch (const std::exception& e) { std::fprintf(stderr,"FAIL: %s\n",e.what()); return 1; }
}
