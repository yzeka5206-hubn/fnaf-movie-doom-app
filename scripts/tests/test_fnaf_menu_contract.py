import re
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / 'doom/src/main/java/com/msa/freedoom'
ANDROID = '{http://schemas.android.com/apk/res/android}'


def jpeg_size(path):
    data = path.read_bytes()
    if data[:2] != b'\xff\xd8':
        raise ValueError('not JPEG')
    i = 2
    while i < len(data):
        if data[i] != 0xff:
            raise ValueError('invalid JPEG marker')
        while data[i] == 0xff:
            i += 1
        marker = data[i]
        i += 1
        if marker in (0xd8, 0xd9):
            continue
        length = int.from_bytes(data[i:i + 2], 'big')
        if marker in (0xc0, 0xc1, 0xc2, 0xc3):
            height = int.from_bytes(data[i + 3:i + 5], 'big')
            width = int.from_bytes(data[i + 5:i + 7], 'big')
            return width, height
        i += length
    raise ValueError('JPEG size marker missing')


class MenuContractTest(unittest.TestCase):
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
        self.assertEqual(byname['.FnafOptionsActivity'].get(ANDROID + 'exported'), 'false')
        self.assertEqual(byname['.Game'].get(ANDROID + 'process'), ':Game')
        self.assertEqual(byname['.Game'].get(ANDROID + 'screenOrientation'), 'sensorLandscape')
        self.assertIsNotNone(byname['.EntryActivity'].find('./intent-filter/data'))

    def test_reference_art_is_original_aspect_ratio_and_not_a_pointer(self):
        art = ROOT / 'doom/src/main/res/drawable-nodpi/fnaf_menu_art.jpg'
        self.assertEqual(jpeg_size(art), (1920, 1080))
        self.assertGreater(art.stat().st_size, 50_000)

    def test_uses_existing_v7_launch_and_backup_without_file_picker(self):
        menu = (JAVA / 'FnafMenuActivity.kt').read_text()
        options = (JAVA / 'FnafOptionsActivity.kt').read_text()
        self.assertIn('LaunchState(this)', menu)
        self.assertIn('launch.initialize()', menu)
        self.assertIn('launch.selectGame(game)', menu)
        self.assertIn('FnafBundledGame.modPaths.forEach', menu)
        self.assertIn('launch.launchGame()', menu)
        self.assertNotIn('ACTION_OPEN_DOCUMENT', menu)
        self.assertIn('OptionsScreen(Modifier.weight(1f))', options)
        underlying = (JAVA / 'ui/options/OptionsScreen.kt').read_text()
        self.assertIn('EngineOptionsCard()', underlying)
        self.assertIn('BackupCard()', underlying)
        bridge = (ROOT / 'doom/src/main/java/org/libsdl/app/SDLOpenTouch.java').read_text()
        self.assertIn('+set sv_difficulty 1 +map OUTSIDE', bridge)

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
