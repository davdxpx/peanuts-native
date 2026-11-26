"""The layout question's pictures (android/app/src/peanuts/res/drawable/pil_layout_*.xml, dev.peanuts.ui.LayoutChoice) are what peanuts/scripts/layouts.py
writes, byte for byte: they named a generator outside the repository (igawa6's note on #679), as the icons once did (test_icons.py)."""
import subprocess
import sys
import unittest
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
SCRIPT = REPO / 'peanuts/scripts/layouts.py'
DRAWABLE = REPO / 'android/app/src/peanuts/res/drawable'


class LayoutPictures(unittest.TestCase):
    def test_files_are_what_the_script_writes(self):
        r = subprocess.run([sys.executable, str(SCRIPT), '--check'], capture_output=True, text=True)
        self.assertEqual(r.returncode, 0, r.stdout + r.stderr)

    def test_each_file_names_its_source(self):
        files = sorted(DRAWABLE.glob('pil_layout_*.xml'))
        self.assertEqual([f.name for f in files], ['pil_layout_bottom.xml', 'pil_layout_right.xml', 'pil_layout_two.xml'])
        for f in files:
            self.assertIn('written by peanuts/scripts/layouts.py', f.read_text(encoding='utf-8').splitlines()[1], f.name)

    def test_the_question_shows_them(self):
        q = (REPO / 'android/app/src/main/java/dev/peanuts/ui/LayoutChoice.java').read_text(encoding='utf-8')
        self.assertIn('{"pil_layout_two", "pil_layout_right", "pil_layout_bottom"}', q)


if __name__ == '__main__':
    unittest.main()
