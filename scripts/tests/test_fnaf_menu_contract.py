import re
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / 'doom/src/main/java/com/msa/freedoom'
ANDROID = '{http://schemas.android.com/apk/res/android}'


class MenuContractTest(unittest.TestCase):
    def test_debug_package_is_separate_from_v7(self):
        gradle = (ROOT / 'doom/build.gradle.kts').read_text()
        self.assertIn('applicationId = "com.fnaf.moviedoom"', gradle)
        self.assertIn('applicationIdSuffix = ".menutest"', gradle)
        self.assertIn('FNAF Menü Testi', gradle)

    def test_one_launcher_is_the_native_landscape_menu(self):
        manifest = ET.parse(ROOT / 'doom/src/main/AndroidManifest.xml').getroot()
        activities = manifest.findall('./application/activity')
        launchers = [a for a in activities if any(
            c.get(ANDROID + 'name') == 'android.intent.category.LAUNCHER'
            for c in a.findall('./intent-filter/category')
        )]
        self.assertEqual(len(launchers), 1)
        self.assertEqual(launchers[0].get(ANDROID + 'name'), '.FnafMenuActivity')
        self.assertEqual(launchers[0].get(ANDROID + 'exported'), 'true')
        self.assertEqual(launchers[0].get(ANDROID + 'screenOrientation'), 'sensorLandscape')
        byname = {a.get(ANDROID + 'name'): a for a in activities}
        self.assertEqual(byname['.OriginalMenuActivity'].get(ANDROID + 'process'), ':OriginalMenu')
        self.assertEqual(byname['.OriginalMenuActivity'].get(ANDROID + 'screenOrientation'), 'sensorLandscape')
        self.assertEqual(byname['.FnafOptionsActivity'].get(ANDROID + 'exported'), 'false')
        self.assertEqual(byname['.Game'].get(ANDROID + 'process'), ':Game')
        self.assertEqual(byname['.Game'].get(ANDROID + 'screenOrientation'), 'sensorLandscape')
        self.assertIsNotNone(byname['.EntryActivity'].find('./intent-filter/data'))

    def test_real_launcher_asset_and_overlay(self):
        asset = ROOT / 'doom/src/main/assets/original-menu/Assets.dat'
        controller = (JAVA / 'FnafMenuActivity.kt').read_text()
        original = (JAVA / 'OriginalMenuActivity.kt').read_text()
        self.assertGreater(asset.stat().st_size, 30_000_000)
        self.assertIn('ASSET_SHA256', controller)
        self.assertIn('override fun useOpenTouch(): Boolean = false', original)
        self.assertIn('libChowdren.so', original)
        self.assertIn('FnafOptionsActivity::class.java', original)
        self.assertIn('480', original)
        self.assertIn('720', original)

    def test_uses_existing_v7_launch_and_backup_without_file_picker(self):
        menu = (JAVA / 'FnafMenuActivity.kt').read_text()
        options = (JAVA / 'FnafOptionsActivity.kt').read_text()
        self.assertIn('LaunchState(this)', menu)
        self.assertIn('launch.initialize()', menu)
        self.assertIn('launch.selectGame(game)', menu)
        self.assertIn('FnafBundledGame.modPaths.forEach', menu)
        self.assertIn('launch.launchGame(selection)', menu)
        self.assertNotIn('ACTION_OPEN_DOCUMENT', menu)
        self.assertIn('OptionsScreen(Modifier.weight(1f))', options)
        underlying = (JAVA / 'ui/options/OptionsScreen.kt').read_text()
        self.assertIn('EngineOptionsCard()', underlying)
        self.assertIn('BackupCard()', underlying)
        bridge = (ROOT / 'doom/src/main/java/org/libsdl/app/SDLOpenTouch.java').read_text()
        self.assertIn('FnafLaunchProfile.buildArgs', bridge)
        self.assertIn('else {\n            // Legacy paths', bridge)

    def test_fixed_game_sizes_match_lfs_metadata_or_bundled_bytes(self):
        assets = ROOT / 'doom/src/main/assets/game'
        for name, expected in [('FNAFMovie.ipk3', 66904),
                               ('FiveNightsAtFreddysDoomMOVIE.wad', 37464359),
                               ('FiveNightsAtFreddysDoomMOVIE.pk3', 207026277)]:
            p = assets / name
            with p.open('rb') as source:
                header = source.read(256)
            if header.startswith(b'version https://git-lfs.github.com/spec/v1'):
                size = int(re.search(rb'size (\d+)', header).group(1))
            else:
                size = p.stat().st_size
            self.assertEqual(size, expected, name)


if __name__ == '__main__':
    unittest.main()
