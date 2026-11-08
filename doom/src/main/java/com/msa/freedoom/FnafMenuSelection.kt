package com.msa.freedoom

import android.content.Intent

/** Only typed, validated data crosses the two isolated SDL processes. */
data class FnafMenuSelection(
    val map: String,
    val difficulty: Int,
    val streamer: Int,
    val graphics: Int,
    val shaders: Int,
    val halloween: Int,
    val christmas: Int,
    val resolution: Int,
    val outsideBlack: Boolean,
    val lightTextures: Boolean,
    val cameraSaver: Boolean,
    val vulkan: Boolean = false,
) {
    fun isValid(): Boolean = FnafLaunchProfile.isValid(
        map, difficulty, streamer, graphics, shaders, halloween, christmas, resolution,
    )

    fun writeTo(intent: Intent) {
        require(isValid())
        intent.putExtra("fnaf_menu_selection", true)
        intent.putExtra("fnaf_map", map)
        intent.putExtra("fnaf_difficulty", difficulty)
        intent.putExtra("fnaf_streamer", streamer)
        intent.putExtra("fnaf_graphics", graphics)
        intent.putExtra("fnaf_shaders", shaders)
        intent.putExtra("fnaf_halloween", halloween)
        intent.putExtra("fnaf_christmas", christmas)
        intent.putExtra("fnaf_resolution", resolution)
        intent.putExtra("fnaf_outside_black", outsideBlack)
        intent.putExtra("fnaf_light_textures", lightTextures)
        intent.putExtra("fnaf_camera_saver", cameraSaver)
        intent.putExtra("fnaf_vulkan", vulkan)
    }

    companion object {
        fun fromIntent(intent: Intent?): FnafMenuSelection? {
            if (intent == null || !intent.getBooleanExtra("fnaf_menu_selection", false)) return null
            val map = intent.getStringExtra("fnaf_map") ?: return null
            val selected = FnafMenuSelection(
                map,
                intent.getIntExtra("fnaf_difficulty", -1),
                intent.getIntExtra("fnaf_streamer", -1),
                intent.getIntExtra("fnaf_graphics", -1),
                intent.getIntExtra("fnaf_shaders", -1),
                intent.getIntExtra("fnaf_halloween", -1),
                intent.getIntExtra("fnaf_christmas", -1),
                intent.getIntExtra("fnaf_resolution", -1),
                intent.getBooleanExtra("fnaf_outside_black", false),
                intent.getBooleanExtra("fnaf_light_textures", true),
                intent.getBooleanExtra("fnaf_camera_saver", false),
                intent.getBooleanExtra("fnaf_vulkan", false),
            )
            return selected.takeIf { it.isValid() }
        }
    }
}
