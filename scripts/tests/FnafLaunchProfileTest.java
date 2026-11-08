import com.msa.freedoom.FnafLaunchProfile;

/** Pure-Java launch contract and real-UZDoom custom scaling smoke test. */
public final class FnafLaunchProfileTest {
    private static void check(boolean b, String message) {
        if (!b) throw new AssertionError(message);
    }
    private static void dimensions(int width, int height, int target, int expectedW, int expectedH) {
        int[] r = FnafLaunchProfile.renderSize(width, height, target);
        check(r[0] == expectedW && r[1] == expectedH, "size: " + r[0] + "x" + r[1]);
        String args = FnafLaunchProfile.renderScaleArgs(r[0], r[1]);
        check(args.contains("+set vid_scalemode 5"), args);
        check(args.contains("+set vid_scale_customwidth " + expectedW + " "), args);
        check(args.contains("+set vid_scale_customheight " + expectedH + " "), args);
        check(args.endsWith("+set vid_scale_custompixelaspect 1"), args);
    }
    public static void main(String[] ignored) {
        dimensions(2316, 1080, 480, 1028, 480);
        dimensions(2316, 1080, 720, 1544, 720);
        dimensions(2400, 1080, 480, 1066, 480);
        dimensions(2400, 1080, 720, 1600, 720);
        dimensions(1280, 720, 480, 852, 480);
        dimensions(1280, 720, 720, 1280, 720);
        check(FnafLaunchProfile.frameRateArgs().equals(
            " +set vid_maxfps 60 +set cl_capfps 0 +set vid_fps 1"), "FPS launch settings");
        int n = 0;
        for (String map : new String[]{"OUTSIDE", "NIGHT1", "NIGHT2", "NIGHT3", "NIGHT4", "NIGHT5"})
            for (int difficulty=0; difficulty<3; difficulty++) for (int streamer=0; streamer<2; streamer++)
                for (int graphics=0; graphics<5; graphics++) for (int height : new int[]{480,720})
                    for (boolean outsideBlack : new boolean[]{false, true}) {
                    check(FnafLaunchProfile.isValid(map,difficulty,streamer,graphics,0,0,0,height), "valid rejected");
                    String args = FnafLaunchProfile.buildArgs(map,difficulty,streamer,graphics,0,0,0,height,outsideBlack);
                    check(args.contains("+set vid_scalefactor 1"), args);
                    check(args.contains("+set gl_precache 0"), args);
                    check(args.contains("+set fnaf_iqm_cull 1"), args);
                    check(args.contains("+set fnaf_static_cull 1"), args);
                    check(args.contains("+set fnaf_outside_black " + (outsideBlack ? 1 : 0) + " "), args);
                    check(args.indexOf("+set fnaf_outside_black") < args.indexOf("+map "), args);
                    check(args.contains("+set fnaf_max_image_size " +
                        (graphics == 0 ? (height == 480 ? 768 : 1024) : 0) + " "), args);
                    check(args.contains("+map " + map), args);
                    check(!args.contains("-host") && !args.contains("-join"), args);
                    n++;
                }
        for (String map : new String[]{null,"","NIGHT6","NIGHT1 +map OUTSIDE","night1","-join evil"})
            check(!FnafLaunchProfile.isValid(map,0,0,0,0,0,0,480), "bad map accepted");
        for (int[] size : new int[][]{{0,480},{1028,0},{1027,480},{1028,479},{-2,720}}) {
            try { FnafLaunchProfile.renderScaleArgs(size[0],size[1]); throw new AssertionError("bad size accepted"); }
            catch (IllegalArgumentException expected) { }
        }
        System.out.println("PASS " + n + " profiles; 480p/720p custom logical scaling; invalid inputs rejected");
    }
}
