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

    def test_pictures_only_when_turned_on(self):
        """igawa6's review of #711: a new outside host is the player's choice. Only AmiiboPictures reaches out, and only with the pictures turned on
        (off by default); off, it answers at once with none (Peanuts' own card) and reads no cache"""
        for p in sorted(PKG.glob('*.java')) + sorted((APP / 'main/java/dev/peanuts/ui').glob('Amiibo*.java')):
            if p.name == 'AmiiboPictures.java':
                continue
            text = p.read_text(encoding='utf-8')
            for word in ('http://', 'https://', 'HttpURLConnection', 'java.net.URL', 'openConnection'):
                self.assertNotIn(word, text, f'{p.name} reaches out ({word})')
        pictures = (PKG / 'AmiiboPictures.java').read_text(encoding='utf-8')
        load = pictures[pictures.index('public static void load('):]
        self.assertTrue(load.split('\n')[1].strip().startswith('if (!AmiiboSwitch.pictures(c)) { done.accept(null); return; }'), 'the first thing load does')
        self.assertIn('getBoolean(PICTURES, false)', (PKG / 'AmiiboSwitch.java').read_text(encoding='utf-8'), 'off by default')
        notice = (REPO / 'android/NOTICE.md').read_text(encoding='utf-8')
        line = next(l for l in notice.splitlines() if 'AmiiboAPI' in l)
        self.assertIn('once the player has turned the pictures on', line)

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
        self.assertIn('dev.peanuts.amiibo.AmiiboPage.back()', page, 'B from an amiibo\'s page goes back to the cards')
        self.assertIn('dev.peanuts.amiibo.AmiiboPage.leave()', page)
        self.assertIn('dev.peanuts.amiibo.AmiiboPage.result(a, request, result, data)', (ui / 'LibrarySettings.java').read_text(encoding='utf-8'))
        self.assertTrue((APP / 'peanuts/res/drawable/pi_amiibo.xml').is_file(), 'the tile\'s icon')

    def test_words(self):
        """UI-RULES.md: no separator glyphs, no dashes, no exclamation marks, no Loading text in the player's words"""
        for f in ('AmiiboPage.java', 'AmiiboImport.java'):
            for s in literals(PKG / f):
                for bad in ('·', '•', ' - ', '—', '–', '!', 'Loading', 'Oops'):
                    self.assertNotIn(bad, s, f'{f}: "{s}"')

    def test_switch_first(self):
        """The area starts with the game's amiibo switch, the same setting as the one in its Game options; the tile says when it is off"""
        page = (PKG / 'AmiiboPage.java').read_text(encoding='utf-8')
        self.assertIn('PUi.add(body, switchRow(a), 14);', page)
        self.assertIn('AmiiboSwitch.game(a, titleId, now)', page)
        self.assertIn('if (!AmiiboSwitch.on(c, titleId)) return "Off for this game";', page)

    def test_pictures_row(self):
        """Settings › Users › amiibo pictures: off by default (AmiiboSwitch.pictures), the row names the host"""
        ui = APP / 'main/java/dev/peanuts/ui'
        lib = (ui / 'LibrarySettings.java').read_text(encoding='utf-8')
        row = lib[lib.index('new PUi.Setting(a, "", "amiibo pictures", null)'):]
        row = row[:row.index('card.addView(pics.view);')]
        self.assertIn('raw.githubusercontent.com', row, 'the host is named')
        self.assertIn('dev.peanuts.amiibo.AmiiboSwitch.pictures(a, on)', row)
        self.assertIn('new Group("amiibo", r("amiibo", "amiibo"), r("amiibo pictures", "amiibo"))', (ui / 'SettingsSections.java').read_text(encoding='utf-8'))
        self.assertIn('put(m, "amiibo pictures",', (ui / 'SettingsHelp.java').read_text(encoding='utf-8'))

    def test_pictures_only_on_demand(self):
        """Pictures are fetched when a page shows an amiibo (AmiiboPictures.load from the page), never at start or in the background"""
        uses = [p.name for p in APP.rglob('*.java') if 'AmiiboPictures.load(' in p.read_text(encoding='utf-8') and p.name != 'AmiiboPictures.java']
        self.assertTrue(set(uses) <= {'AmiiboPage.java', 'AmiiboTab.java', 'AmiiboCard.java', 'AmiiboWatch.java'}, uses)


if __name__ == '__main__':
    unittest.main()
