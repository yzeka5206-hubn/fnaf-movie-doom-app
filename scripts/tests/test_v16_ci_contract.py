#!/usr/bin/env python3
"""Guard the single-dispatch, same-signature, small-updater CI chain.
These checks do not replace the real Android emulator or a phone test.
"""
from pathlib import Path
import unittest

R = Path(__file__).resolve().parents[2]
WORKFLOW = (R / '.github/workflows/build-apk.yml').read_text(encoding='utf-8')

class V16CiContract(unittest.TestCase):
    def test_exact_template_and_prior_release_are_pinned(self):
        self.assertEqual(WORKFLOW.count('ref: 9521173a7bf57cff0f26320fc3c16da4a654e61a'), 2)
        self.assertIn('run-id: 42473457321', WORKFLOW)
        self.assertIn('FNAF_EXPECTED_SOURCE_SHA: 3ae61eee3afd2fd1b55c73cefff22d5c540a000d4961d04e35ee0edbb48c1fe5', WORKFLOW)

    def test_only_verified_build_can_feed_helper(self):
        self.assertIn('build_small_helper:\n    needs: build', WORKFLOW)
        self.assertIn('test_small_helper:\n    needs: build_small_helper', WORKFLOW)
        self.assertIn('publish_small_helper:\n    needs: [build_small_helper, test_small_helper]', WORKFLOW)
        self.assertIn('bash scripts/prepare-v16-ci.sh "$OLD" "$NEW"', WORKFLOW)

    def test_emulator_uses_generated_target_sha_and_real_v15(self):
        self.assertIn('TARGET_SHA: ${{ needs.build_small_helper.outputs.target_sha }}', WORKFLOW)
        self.assertIn('scripts/android14-animation-updater-ci.sh "$PWD/helper/FNAF-FPS-Deneme-Guncelleyici.apk" 15', WORKFLOW)
        self.assertIn('name: FNAF-FPS-Deneme-Guncelleyici', WORKFLOW)

    def test_existing_signature_and_offline_helper_are_required(self):
        self.assertIn('7ffca05129f461b68852130392b01a10a041c2b785f5b4413bb944ac933569e6', WORKFLOW)
        self.assertIn("'android.permission.INTERNET'", WORKFLOW)
        self.assertIn('secrets.FNAF_ANDROID_KEYSTORE_B64', WORKFLOW)
        self.assertIn('secrets.FNAF_ANDROID_KEYSTORE_PASSWORD', WORKFLOW)

if __name__ == '__main__':
    unittest.main()
