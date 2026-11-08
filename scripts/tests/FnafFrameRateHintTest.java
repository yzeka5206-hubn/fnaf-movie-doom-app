import android.os.Build;
import android.view.Surface;
import com.msa.freedoom.FnafFrameRateHint;

/** Host test via stubs. Android integration still needs a real device. */
public final class FnafFrameRateHintTest {
    static void check(boolean v, String m) { if (!v) throw new AssertionError(m); }
    public static void main(String[] ignored) {
        Surface s=new Surface();
        Build.VERSION.SDK_INT=29;
        FnafFrameRateHint.request(() -> s); check(s.calls==0,"pre-30 called API");
        Build.VERSION.SDK_INT=30;
        FnafFrameRateHint.request(null);
        FnafFrameRateHint.request(() -> null); check(s.calls==0,"null should be safe");
        s.valid=false; FnafFrameRateHint.request(() -> s); check(s.calls==0,"invalid surface");
        s.valid=true; FnafFrameRateHint.request(() -> s);
        check(s.calls==1 && s.frameRate==60f && s.compatibility==Surface.FRAME_RATE_COMPATIBILITY_DEFAULT, "incorrect hint");
        s.fail=true; FnafFrameRateHint.request(() -> s); check(s.calls==2,"fallback should not crash");
        FnafFrameRateHint.request(() -> { throw new RuntimeException("gone"); });
        FnafFrameRateHint.request(() -> { throw new NoSuchMethodError("old ROM"); });
        System.out.println("PASS API gating, surface lifecycle, 60 Hz scheduler hint, safe fallback");
    }
}
