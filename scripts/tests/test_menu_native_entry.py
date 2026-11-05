#!/usr/bin/env python3
"""Guard the distinct OpenTouch and converted-menu SDL thread entry paths."""
from pathlib import Path
import unittest
ROOT=Path(__file__).resolve().parents[2]

class NativeMenuEntryTests(unittest.TestCase):
    def test_entry_runs_after_sdl_tls_and_only_on_non_opentouch_path(self):
        s=(ROOT/'doom/src/main/java/org/libsdl/app2012/SDLActivity.java').read_text()
        body=s[s.index('class SDLMain implements Runnable'):s.index('class SDLSurface extends SurfaceView')]
        init=body.index('SDLActivity.nativeRunMain(library, function, arguments);')
        guard=body.index('if (!SDLActivity.OPENTOUCH_SDL_EXTRA && SDLActivity.mSingleton != null)')
        invoke=body.index('SDLActivity.mSingleton.runLauncherAfterSdlSetup(arguments);')
        game=body.index('SDLOpenTouch.RunApplication(')
        self.assertLess(init,guard); self.assertLess(guard,invoke); self.assertLess(invoke,game)
    def test_launcher_is_loaded_and_dispatches_original_entry(self):
        k=(ROOT/'doom/src/main/java/com/msa/freedoom/OriginalMenuActivity.kt').read_text()
        c=(ROOT/'tools/original-menu/menu-runner/menu_runner.c').read_text()
        self.assertIn('"Chowdren", "FnafMenuRunner"',k)
        self.assertIn('nativeRunConvertedLauncher(arguments)',k)
        self.assertIn('SDL_main((int)count + 1, argv)',c)
        self.assertIn('strdup("app_process")',c)

if __name__=='__main__': unittest.main()
