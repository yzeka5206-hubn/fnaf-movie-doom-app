package com.msa.freedoom;

/** Typed single-player launch contract. Never accepts a shell command. */
public final class FnafLaunchProfile {
    private FnafLaunchProfile() {}

    public static boolean isValid(String map, int difficulty, int streamer, int graphics,
                                  int shaders, int halloween, int christmas, int height) {
        boolean validMap = "OUTSIDE".equals(map) || "NIGHT1".equals(map) ||
                "NIGHT2".equals(map) || "NIGHT3".equals(map) ||
                "NIGHT4".equals(map) || "NIGHT5".equals(map);
        return validMap && difficulty >= 0 && difficulty <= 2 &&
                streamer >= 0 && streamer <= 1 && graphics >= 0 && graphics <= 4 &&
                shaders == 0 && halloween >= 0 && halloween <= 1 &&
                christmas >= 0 && christmas <= 1 && (height == 480 || height == 720);
    }

    public static int[] renderSize(int displayWidth, int displayHeight, int targetHeight) {
        if (displayWidth <= 0 || displayHeight <= 0 || (targetHeight != 480 && targetHeight != 720))
            throw new IllegalArgumentException("Invalid display or resolution");
        int height = Math.min(displayHeight, targetHeight);
        // Even dimensions avoid odd framebuffer sizes on mobile GPUs.
        height &= ~1;
        int width = (int)(((long) displayWidth * height) / displayHeight) & ~1;
        return new int[]{Math.max(2, width), Math.max(2, height)};
    }

    /**
     * The SDL surface is normally the physical display size on Android.
     * vid_defwidth/height alone do not control UZDoom's current render buffer;
     * explicitly select its custom scaling mode and its logical dimensions.
     */
    public static String renderScaleArgs(int width, int height) {
        if (width < 2 || height < 2 || (width & 1) != 0 || (height & 1) != 0)
            throw new IllegalArgumentException("Invalid render size");
        return " +set vid_scalemode 5" +
                " +set vid_scale_customwidth " + width +
                " +set vid_scale_customheight " + height +
                " +set vid_scale_custompixelaspect 1";
    }

    public static String buildArgs(String map, int difficulty, int streamer, int graphics,
                                   int shaders, int halloween, int christmas, int height) {
        if (!isValid(map, difficulty, streamer, graphics, shaders, halloween, christmas, height))
            throw new IllegalArgumentException("Invalid single-player selection");

        // Original launcher presets, plus matching in-game menu CVARs. Resolution
        // is an Android-specific 480p/720p choice, so vid_scalefactor is 1.
        final int[] tex = {0, 2, 3, 4, 4};
        final int[] shadow = {0, 1, 2, 3, 3};
        final int[] ao = {0, 0, 1, 2, 3};
        final int[] aa = {0, 0, 0, 2, 16};
        final int[] bloom = {0, 0, 1, 1, 1};
        final int[] lens = {0, 0, 0, 1, 1};
        final int[] shadowMap = {128, 256, 512, 1024, 1024};
        final int[] multisample = {0, 0, 0, 2, 8};
        return " +set CL_GraphicPreset " + graphics +
                " +set CL_TextureQuality " + tex[graphics] +
                " +set CL_ShadowQuality " + shadow[graphics] +
                " +set CL_SSAO " + ao[graphics] +
                " +set CL_Antialiasing " + aa[graphics] +
                " +set CL_Bloom " + bloom[graphics] +
                " +set CL_Lens " + lens[graphics] +
                " +set vid_scalefactor 1" +
                // Decode and upload visual assets when needed; leave sound
                // precaching and all gameplay simulation untouched.
                " +set gl_precache 0" +
                " +set fnaf_iqm_cull 1" +
                // Only the lowest profile caps ordinary world/character images;
                // camera canvases, fonts and original menus are excluded.
                " +set fnaf_max_image_size " + (graphics == 0 ? 1024 : 0) +
                " +set gl_texture_filter " + tex[graphics] +
                " +set gl_light_shadowmap " + (graphics == 0 ? 0 : 1) +
                " +set gl_shadowmap_quality " + shadowMap[graphics] +
                " +set gl_bloom " + bloom[graphics] +
                " +set gl_lens " + lens[graphics] +
                " +set gl_ssao " + (graphics < 2 ? 0 : 1) +
                " +set gl_multisample " + multisample[graphics] +
                (graphics == 0 ? " +set gl_fxaa 0" : "") +
                " +set sv_cheats 0" +
                " +set sv_difficulty " + difficulty +
                " +set sv_streamermode " + streamer +
                " +set ev_IsHalloween " + halloween +
                " +set ev_IsChristmas " + christmas +
                " -skill 1 +map " + map;
    }
}
