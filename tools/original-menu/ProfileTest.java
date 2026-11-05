import com.msa.freedoom.FnafLaunchProfile;
public class ProfileTest {
    public static void main(String[] ignored) {
        for (String map : new String[]{"OUTSIDE","NIGHT1","NIGHT2","NIGHT3","NIGHT4","NIGHT5"})
            for (int d=0; d<3; d++) for (int s=0; s<2; s++) for (int g=0; g<5; g++) for (int h : new int[]{480,720}) {
                if (!FnafLaunchProfile.isValid(map,d,s,g,0,0,0,h)) throw new AssertionError("valid rejected");
                String result=FnafLaunchProfile.buildArgs(map,d,s,g,0,0,0,h);
                if (!result.contains("+map " + map) || !result.contains("+set sv_difficulty " + d) ||
                    !result.contains("+set sv_streamermode " + s) || !result.contains("+set CL_GraphicPreset " + g))
                    throw new AssertionError(result);
                if (result.contains("-host") || result.contains("-join") || result.contains("+map OUTSIDE") && !map.equals("OUTSIDE"))
                    throw new AssertionError("network or override: " + result);
            }
        for (String m : new String[]{null,"","NIGHT6","NIGHT1 +map OUTSIDE","night1","-join evil"})
            if (FnafLaunchProfile.isValid(m,0,0,0,0,0,0,480)) throw new AssertionError("invalid map accepted: "+m);
        int[][] bad = {{-1,0,0,0,0,0,480},{3,0,0,0,0,0,480},{0,2,0,0,0,0,480},
                       {0,0,5,0,0,0,480},{0,0,0,1,0,0,480},{0,0,0,0,2,0,480},
                       {0,0,0,0,0,-1,480},{0,0,0,0,0,0,1080}};
        for (int[] x : bad) if (FnafLaunchProfile.isValid("OUTSIDE",x[0],x[1],x[2],x[3],x[4],x[5],x[6]))
            throw new AssertionError("invalid field accepted");
        int[] lo=FnafLaunchProfile.renderSize(2400,1080,480), hi=FnafLaunchProfile.renderSize(2400,1080,720);
        if (lo[0]!=1066 || lo[1]!=480 || hi[0]!=1600 || hi[1]!=720) throw new AssertionError("bad dimensions");
        System.out.println("PASS: 360 accepted selection combinations; malformed/network inputs rejected; 11T resolution 1066x480 / 1600x720");
    }
}
