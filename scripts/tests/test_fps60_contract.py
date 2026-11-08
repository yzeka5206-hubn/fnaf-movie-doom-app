from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[2]
class FPS60Contract(unittest.TestCase):
    def test_both_launch_paths_use_same_fps_args(self):
        p=(ROOT/'doom/src/main/java/org/libsdl/app/SDLOpenTouch.java').read_text()
        self.assertEqual(p.count('FnafLaunchProfile.frameRateArgs()'),2)
        self.assertIn('FnafLaunchProfile.buildArgs(',p)
    def test_surface_hint_runs_only_on_create_before_native_callback(self):
        p=(ROOT/'doom/src/main/java/org/libsdl/app2012/SDLActivity.java').read_text()
        self.assertEqual(p.count('FnafFrameRateHint.request(holder)'),1)
        start=p.index('public void surfaceCreated(SurfaceHolder holder)')
        end=p.index('public void surfaceDestroyed(SurfaceHolder holder)',start)
        block=p[start:end]
        self.assertLess(block.index('FnafFrameRateHint.request(holder)'),block.index('SDLActivity.onNativeSurfaceCreated()'))
        self.assertIn('SDLActivity.OPENTOUCH_SDL_EXTRA',block)
    def test_original_menu_and_touch_unchanged(self):
        p=(ROOT/'doom/src/main/java/com/msa/freedoom/FnafLaunchProfile.java').read_text()
        self.assertIn('+set fnaf_outside_black ',p)
        self.assertIn('(height == 480 ? 768 : 1024)',p)
        self.assertIn('+set vid_fps 1',p)
        self.assertIn('+set cl_capfps 0',p)

if __name__=='__main__': unittest.main()
