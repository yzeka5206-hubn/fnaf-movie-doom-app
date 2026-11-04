"""Regression checks for the native OpenTouch -> Android keyboard bridge."""
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
BRIDGE = ROOT / "doom/src/main/java/org/libsdl/app/SDLOpenTouch.java"
GAME = ROOT / "doom/src/main/java/com/msa/freedoom/Game.kt"


class KeyboardBridgeTest(unittest.TestCase):
    def test_system_keyboard_option_avoids_the_native_overlay_keyboard(self):
        source = BRIDGE.read_text()
        self.assertRegex(source, r"options\s*\|=\s*0x4\s*;\s*//\s*GAME_OPTION_USE_SYSTEM_KEYBOARD")

    def test_native_keyboard_message_opens_sdl_editor(self):
        source = BRIDGE.read_text()
        self.assertRegex(source, r"COMMAND_SHOW_KEYBOARD\s*=\s*0x8003")
        handler = source.split("if (msg.arg1 == COMMAND_SHOW_KEYBOARD)", 1)[1].split("if (msg.arg1 == COMMAND_EXIT_APP)", 1)[0]
        self.assertIn("SDLActivity.showTextInput(0, 0, 1, 1)", handler)
        self.assertIn("return true;", handler)

    def test_overlay_activates_native_sdl_text_input(self):
        source = GAME.read_text()
        self.assertRegex(source, r"ACT_SHOW_KEYBOARD\s*=\s*26")
        self.assertIn("SDLOpenTouch.fnafAction(1, ACT_SHOW_KEYBOARD)", source)
        self.assertIn("SDLActivity.showTextInput(0, 0, 1, 1)", source)


if __name__ == "__main__":
    unittest.main()
