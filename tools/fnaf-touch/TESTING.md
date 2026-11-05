# Touch adapter test notes

The APK uses the small `fnaf-touch-v1.pk3` built from `ZSCRIPT` and `MAPINFO`
in this directory. Rebuild it after changes with `python3 tools/fnaf-touch/build.py`
and update the exact adapter length in `FnafBundledGame.kt`.

The adapter runs only inside UI events. Character selection uses the original
`MouseDriverHandler` and ACS `NetMouse`/`NetClick` path. A click is delayed by
four UI ticks so the original selector can observe the position before the click.
Engine menus use their own mouse and back-button handlers.

Regression evidence from Linux UZDoom 4.14.3, not an Android phone:

* With an instrumented copy of the same touch handler in Freedoom, a tap on
  OPTIONS opened the real engine options menu, and a tap on its back arrow
  returned to the list menu.
* With the original IWAD, NIGHT1 WAD and selector scripts, tapping the Mike
  icon gave `IsMike=1`; tapping READY then gave `SelectedChar=1` and removed
  `MenuSelectionOpen`. The fixture omitted most game assets, suppressed model
  animation calls and retained the original graphic scale definitions for the
  selector; it is only a lightweight logic test, not a shipped mod or a test
  of graphics, sound, animations, or the Android engine.

The Android overlay has host tests in `scripts/tests/run_host_controls.sh`.
A real Xiaomi test is still needed for screen coordinates, controls and text.
