package android.view;
public class Surface {
    public static final int FRAME_RATE_COMPATIBILITY_DEFAULT = 0;
    public boolean valid = true, fail = false;
    public int calls = 0, compatibility = -1;
    public float frameRate = -1f;
    public boolean isValid() { return valid; }
    public void setFrameRate(float rate, int compat) {
        calls++; frameRate = rate; compatibility = compat;
        if (fail) throw new IllegalStateException("destroyed");
    }
}
