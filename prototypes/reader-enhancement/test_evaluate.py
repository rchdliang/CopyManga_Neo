"""Lab invariants; no external binary, network, or third-party artwork."""
import unittest
from pathlib import Path
import numpy as np
from PIL import Image
from evaluate import sharpen, fixtures


class EvaluationTest(unittest.TestCase):
    def test_zero_preserves_bytes(self):
        rng = np.random.default_rng(7)
        data = rng.integers(0, 256, (48, 32, 3), dtype=np.uint8)
        np.testing.assert_array_equal(np.asarray(sharpen(Image.fromarray(data), 0)), data)

    def test_constant_and_black_white_edges_do_not_halo(self):
        data = np.full((32, 32, 3), 128, dtype=np.uint8)
        np.testing.assert_array_equal(np.asarray(sharpen(Image.fromarray(data), .35)), data)
        data[:, :16] = 0; data[:, 16:] = 255
        np.testing.assert_array_equal(np.asarray(sharpen(Image.fromarray(data), .35)), data)

    def test_mild_correction_is_bounded(self):
        rng = np.random.default_rng(11)
        data = rng.integers(50, 180, (64, 64, 3), dtype=np.uint8)
        out = np.asarray(sharpen(Image.fromarray(data), .35))
        delta = abs(out.astype(int) - data.astype(int))
        self.assertLessEqual(delta.max(), 8)
        self.assertGreater(delta.max(), 0)

    def test_fixture_set_has_four_categories(self):
        font = Path('/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc')
        if not font.is_file(): self.skipTest('Use evaluate.py --font with an installed CJK font')
        samples = list(fixtures(font))
        self.assertEqual([n for n, _ in samples], ['small-text', 'screentone', 'thin-lines', 'color-page'])
        self.assertTrue(all(img.size == (384, 256) for _, img in samples))


if __name__ == '__main__': unittest.main()
