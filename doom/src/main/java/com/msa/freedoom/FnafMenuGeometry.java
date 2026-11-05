package com.msa.freedoom;

/** Pure geometry shared by the Android view and the host-side hitbox tests.
 * Coordinates are in the original 1920 x 1080 menu artwork, not device pixels.
 */
public final class FnafMenuGeometry {
    public static final int ART_WIDTH = 1920;
    public static final int ART_HEIGHT = 1080;

    public enum Item { SINGLEPLAYER, MULTIPLAYER, OPTIONS, YOUTUBE, DISCORD, VOLUME }

    public static final class Box {
        public final float left, top, right, bottom;
        public Box(float left, float top, float right, float bottom) {
            this.left = left; this.top = top; this.right = right; this.bottom = bottom;
        }
    }

    public static final class Fit {
        public final float scale, left, top;
        private Fit(float scale, float left, float top) {
            this.scale = scale; this.left = left; this.top = top;
        }
        public Box place(Box source) {
            return new Box(left + source.left * scale, top + source.top * scale,
                    left + source.right * scale, top + source.bottom * scale);
        }
    }

    private FnafMenuGeometry() {}

    public static Fit fit(int width, int height) {
        if (width <= 0 || height <= 0) return new Fit(0f, 0f, 0f);
        float scale = Math.min(width / (float) ART_WIDTH, height / (float) ART_HEIGHT);
        return new Fit(scale, (width - ART_WIDTH * scale) / 2f,
                (height - ART_HEIGHT * scale) / 2f);
    }

    public static Box bounds(Item item) {
        switch (item) {
            case SINGLEPLAYER: return new Box(92, 590, 735, 684);
            case MULTIPLAYER:  return new Box(92, 686, 735, 779);
            case OPTIONS:      return new Box(92, 781, 735, 878);
            case YOUTUBE:      return new Box(1486, 45, 1610, 175);
            case DISCORD:      return new Box(1611, 45, 1740, 175);
            case VOLUME:       return new Box(1741, 45, 1875, 175);
            default: throw new IllegalArgumentException("Unknown menu item: " + item);
        }
    }
}
