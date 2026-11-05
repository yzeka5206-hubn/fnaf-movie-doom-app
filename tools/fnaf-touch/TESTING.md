# Touch adapter test notes

The APK uses the small `fnaf-touch-v1.pk3` built from `ZSCRIPT` and `MAPINFO`
in this directory. Rebuild it after changes with `python3 tools/fnaf-touch/build.py`
and update the exact adapter length in `FnafBundledGame.kt`.

The adapter runs only inside UI events. Character selection uses the original
`MouseDriverHandler` and ACS `NetMouse`/`NetClick` path. A click is delayed by
four UI ticks so the original selector can observe the position before the click.
Settings and other engine menus receive actual SDL mouse/wheel events, not
`Menu.MouseEvent` calls from `InterfaceProcess`. The original pause list uses a
session-and-serial-acknowledged coordinate bridge. It only invokes the original
item inside its real `OnUIEvent` responder; this preserves the engine's menu
security context and original actions. A stale, cancelled or expired request is
not activated. Android sends the drawn back arrow as SDL X1/GUI Back because its
left-click path may otherwise return to a null menu. A session-specific mode ACK
guards menu clicks from leaking into gameplay. Explicit on-screen `GERI`, arrow
and `ENTER` buttons provide a fallback for navigation.

Regression evidence from Linux UZDoom 4.14.3, not an Android phone:

* With the real pause list, a coordinate request followed by the GUI key opened
  GRAPHICS; another request at RETURN closed the menu and resumed gameplay.
  The packaged frame-model addon loaded alongside the original game. Host tests
  also reject stale acknowledgements and verify cancel, timeout and letterbox.
* The SDL input path changed gamma from 1.0 to 1.3, a graphics preset from
  2 to 3, and the menu scroll position from 13 to 7 without a VM abort. SDL X1
  returned safely to gameplay; the guard blocked mouse events until the right
  session/mode ACK, then passed them through again.
* With the original IWAD, NIGHT1 WAD and selector scripts, plus the *shipped*
  touch adapter and static-model addon, the Abby icon produced `IsAbby=1`,
  `currentSkinID=2` and her original view height 55; READY set `SelectedChar=1`
  and removed `MenuSelectionOpen`. The fixture omitted many other game assets
  and suppressed model animation calls. It is not an Android phone test.

The Android overlay has host tests in `scripts/tests/run_host_controls.sh`.
A real Xiaomi test is still needed for screen coordinates, controls and text.
