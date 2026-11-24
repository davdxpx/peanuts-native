"""amiibo in the app (dev.peanuts.amiibo): the JVM part (android/app/src/jvmtest's AmiiboJvmTest, skipped without a JDK) reads the app's table, dumps made in
the test (made-up IDs), imports, the player's amiibo with the games' data and the host's description. Then the table itself: made by
peanuts/scripts/amiibo-db.py from AmiiboAPI (MIT) and noted as such, and the rule that no amiibo keys or dumps come near the repository."""
import re
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
APP = REPO / 'android/app/src'
PKG = APP / 'main/java/dev/peanuts/amiibo'
TABLE = APP / 'peanuts/res/raw/amiibo_db.tsv'
CORE = [PKG / 'AmiiboDb.java', PKG / 'AmiiboBin.java', PKG / 'AmiiboImport.java', PKG / 'AmiiboStore.java', PKG / 'AmiiboTag.java']
TEST = APP / 'jvmtest/java/dev/peanuts/amiibo/AmiiboJvmTest.java'


@unittest.skipUnless(shutil.which('javac') and shutil.which('java'), 'needs a JDK')
class Jvm(unittest.TestCase):
    def test_store(self):
        with tempfile.TemporaryDirectory() as t:
            d = Path(t)
            r = subprocess.run(['javac', '-Xlint:all', '-Werror', '-encoding', 'UTF-8', '-d', str(d / 'classes'), *map(str, CORE), str(TEST)],
                               capture_output=True, text=True)
            self.assertEqual(r.returncode, 0, r.stderr)
            (d / 'work').mkdir()
            r = subprocess.run(['java', '-cp', str(d / 'classes'), 'dev.peanuts.amiibo.AmiiboJvmTest', str(TABLE), str(d / 'work')],
                               capture_output=True, text=True, encoding='utf-8')
            self.assertEqual(r.returncode, 0, r.stderr)
            self.assertEqual(r.stdout.strip(), 'ok')


class Table(unittest.TestCase):
    def test_source_and_shape(self):
        lines = TABLE.read_text(encoding='utf-8').splitlines()
        self.assertTrue(lines[0].startswith('# The amiibo of AmiiboAPI (https://github.com/N3evin/AmiiboAPI, MIT, Copyright (c) 2017 Nevin Vu), commit '))
        kinds = {l.split('\t')[0] for l in lines if not l.startswith('#')}
        self.assertEqual(kinds, {'A', 'S', 'G', 'T', 'N', 'W', 'U'})
        for l in lines:
            if l.startswith('A\t'):
                self.assertRegex(l, r'^A\t[0-9a-f]{14}0[23]\t\S')
            if l.startswith('U\t'):
                self.assertRegex(l, r'^U\t[0-9A-F]{16}\t\d+\t[01]\t[0-9a-f]{16}(,[0-9a-f]{16})*$')

    def test_made_by_the_script(self):
        script = (REPO / 'peanuts/scripts/amiibo-db.py').read_text(encoding='utf-8')
        self.assertIn('N3evin/AmiiboAPI', script)
        self.assertIn('no amiibo data of any kind', script)

    def test_no_keys_and_no_dumps(self):
        """amiibo keys (key_retail.bin and the like) and dumps are never part of the repository; the app never asks for them"""
        for p in list(PKG.glob('*.java')) + [REPO / 'dm/peanuts/peanuts_amiibo.c']:
            text = p.read_text(encoding='utf-8').lower()
            for word in ('key_retail', 'locked-secret', 'unfixed-info', 'retail.bin', 'prod.keys'):
                self.assertNotIn(word, text, f'{p.name} mentions {word}')
        tracked = subprocess.run(['git', 'ls-files'], cwd=REPO, capture_output=True, text=True).stdout.splitlines()
        bins = [f for f in tracked if f.endswith('.bin') and 'amiibo' in f.lower()]
        self.assertEqual(bins, [], 'no amiibo dump in the repository')

    def test_names_keep_the_ui_rules(self):
        db = PKG / 'AmiiboDb.java'
        self.assertRegex(db.read_text(encoding='utf-8'), r'name\.substring\(0, i\) \+ " \(" \+ name\.substring\(i \+ 3\) \+ "\)"')


def literals(path):
    return re.findall(r'"((?:[^"\\]|\\.)*)"', path.read_text(encoding='utf-8'))


class Page(unittest.TestCase):
    """The game page's amiibo area (AmiiboPage), Part B"""

    def test_wiring(self):
        ui = APP / 'main/java/dev/peanuts/ui'
        areas = (ui / 'GameAreas.java').read_text(encoding='utf-8')
        self.assertIn('new Area(AMIIBO, "amiibo", "amiibo",', areas)
        page = (ui / 'GamePage.java').read_text(encoding='utf-8')
        self.assertIn('case GameAreas.AMIIBO: return dev.peanuts.amiibo.AmiiboStand.usesAmiibo(a, f.titleId);', page, 'only for games that use amiibo')
        self.assertIn('dev.peanuts.amiibo.AmiiboPage.area(a, body, f.titleId, f.name, wide);', page)
        self.assertIn('dev.peanuts.amiibo.AmiiboPage.back()', page, 'B from an amiibo\'s page goes back to the pictures')
        self.assertIn('dev.peanuts.amiibo.AmiiboPage.leave()', page)
        self.assertIn('dev.peanuts.amiibo.AmiiboPage.result(a, request, result, data)', (ui / 'LibrarySettings.java').read_text(encoding='utf-8'))
        self.assertTrue((APP / 'peanuts/res/drawable/pi_amiibo.xml').is_file(), 'the tile\'s icon')

    def test_words(self):
        """UI-RULES.md: no separator glyphs, no dashes, no exclamation marks, no Loading text in the player's words"""
        for f in ('AmiiboPage.java', 'AmiiboImport.java'):
            for s in literals(PKG / f):
                for bad in ('·', '•', ' - ', '—', '–', '!', 'Loading', 'Oops'):
                    self.assertNotIn(bad, s, f'{f}: "{s}"')

    def test_pictures_only_on_demand(self):
        """Pictures are fetched when a page shows an amiibo (AmiiboPictures.load from the page), never at start or in the background"""
        uses = [p.name for p in APP.rglob('*.java') if 'AmiiboPictures.load(' in p.read_text(encoding='utf-8') and p.name != 'AmiiboPictures.java']
        self.assertTrue(set(uses) <= {'AmiiboPage.java', 'AmiiboTab.java', 'AmiiboCard.java', 'AmiiboWatch.java'}, uses)


class InGame(unittest.TestCase):
    """The quick menu's amiibo tab, the card over the game and the show (Part C)"""
    LAUNCHER = APP / 'main/java/dev/nxnative/android'

    def test_tab_wiring(self):
        mods = (self.LAUNCHER / 'ModsPanel.java').read_text(encoding='utf-8')
        for line in ('names.add(dev.peanuts.amiibo.AmiiboTab.NAME);', 'dev.peanuts.amiibo.AmiiboTab.openOn(', 'dev.peanuts.amiibo.AmiiboTab.fill(a, box, host(p, a))',
                     'if (dev.peanuts.amiibo.AmiiboTab.padKey(k, confirm)) return true;'):
            hit = [l for l in mods.splitlines() if line in l]
            self.assertEqual(len(hit), 1, line)
            self.assertIn('PEANUTS', hit[0], 'a hunk in an upstream file carries its marker')
        tab = (PKG / 'AmiiboTab.java').read_text(encoding='utf-8')
        self.assertIn('takeOff = run;', tab, "the controller's X reaches Take off")
        self.assertIn('if (!has || v.isInTouchMode() || passed[0]) return;', tab, "a controller's first focus goes past the search, so the keyboard stays shut")
        rail = (APP / 'main/java/dev/peanuts/ingame/Rail.java').read_text(encoding='utf-8')
        self.assertIn('case "amiibo": return "amiibo";', rail)
        self.assertIn('public static void lit(String tab)', rail)
        self.assertTrue((APP / 'peanuts/res/drawable/pis_amiibo.xml').is_file(), 'the rail\'s solid icon')

    def test_watch_wiring(self):
        panel = (self.LAUNCHER / 'InGamePanel.java').read_text(encoding='utf-8')
        self.assertIn('dev.peanuts.amiibo.AmiiboWatch.attach(a, root,', panel)
        self.assertEqual(panel.count('dev.peanuts.amiibo.AmiiboWatch.key(e)'), 2, 'the card and the show have the controller (backKey, menuKey)')

    def test_never_opens_unasked(self):
        """The quick menu opens only from the card's Choose"""
        watch = (PKG / 'AmiiboWatch.java').read_text(encoding='utf-8')
        self.assertEqual(watch.count('host.openTab()'), 1)
        self.assertIn('@Override public void choose() { hideCard(); if (host != null) host.openTab(); }', watch)
        self.assertIn('CARD_AFTER_MS = 400', watch, 'the card after the game has looked for 0.4 s')
        self.assertIn('s.search == handled', watch, 'once per search')

    def test_setting(self):
        prefs = (APP / 'main/java/dev/peanuts/PeanutsPrefs.java').read_text(encoding='utf-8')
        self.assertIn('panel(c).getInt("amiibo_when", AMIIBO_SUGGEST)', prefs, 'Suggest by default')

    def test_show(self):
        show = (APP / 'main/java/dev/peanuts/ui/AmiiboShow.java').read_text(encoding='utf-8')
        self.assertIn('END = 2300', show, 'about 2.3 s: the amiibo stands with its name for a good second')
        self.assertIn('float left = cx - baseX * pw, top = bottom - baseY * ph;', show, 'its base on the ring\'s centre')
        self.assertIn('PeanutArt', show, 'the About peanut')
        self.assertIn('Haptics.Word.HEAVY', show, 'one firm tick')
        self.assertIn('"PEANUTS NATIVE \\u00d7 AMIIBO"', show)
        self.assertIn('public void skip()', show, 'any button skips it')

    def test_words(self):
        for f in (PKG / 'AmiiboCard.java', PKG / 'AmiiboTab.java', PKG / 'AmiiboWatch.java'):
            for s in literals(f):
                for bad in ('·', '•', ' - ', '—', '–', '!', 'Loading', 'Oops'):
                    self.assertNotIn(bad, s, f'{f.name}: "{s}"')


if __name__ == '__main__':
    unittest.main()
