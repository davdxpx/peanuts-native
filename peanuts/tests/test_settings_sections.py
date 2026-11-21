"""Settings in ten sections (dev.peanuts.ui.SettingsSections, laid out by SettingsPage): the table on the JVM (android/app/src/jvmtest's
SettingsSectionsJvmTest; skipped without a JDK), and against the sources: every row Settings builds (upstream's MainActivity.buildSettings, the two
default rows it adds, LibrarySettings.add) has its place in the table and the table names no row that is not built; every icon is in the Peanuts
resources; the one upstream hook; the control interface's section names; the new text keeps UI-RULES.md."""
import re
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
APP = REPO / 'android/app/src'
UI = APP / 'main/java/dev/peanuts/ui'
MAIN = APP / 'main/java/dev/nxnative/android/MainActivity.java'
TABLE = UI / 'SettingsSections.java'
ROW = re.compile(r'new P?Ui\.Setting\((?:this|a), "[^"]*", "([^"]+)"')


def between(text, start, end):
    i = text.index(start)
    return text[i:text.index(end, i)]


def built_rows():
    main = MAIN.read_text(encoding='utf-8')
    body = between(main, 'private void buildSettings(LinearLayout content)', '/** A settings group\'s card of rows')
    rows = ROW.findall(body)
    for owner in ('ScreenMode', 'ResScale'):   # perf.addView(ScreenMode.defaultRow(this).view): the row is built in its own file
        if f'{owner}.defaultRow(this)' in body:
            src = (MAIN.parent / f'{owner}.java').read_text(encoding='utf-8')
            rows += ROW.findall(between(src, 'static Ui.Setting defaultRow(', '\n    }\n'))
    lib = (UI / 'LibrarySettings.java').read_text(encoding='utf-8')
    rows += ROW.findall(between(lib, 'public static void add(Activity a, LinearLayout content)', 'private static CharSequence companionsValue'))
    return rows


def table():
    """(section key, label, icon) of each section, (title, icon) of each row"""
    src = TABLE.read_text(encoding='utf-8')
    sections = re.findall(r'new Section\("(\w+)", "([^"]+)", "([\w-]+)"', src)
    rows = re.findall(r'\br\("([^"]+)", "([\w-]+)"\)', src)
    return sections, rows


@unittest.skipUnless(shutil.which('javac') and shutil.which('java'), 'no JDK')
class Jvm(unittest.TestCase):
    def test_jvm(self):
        with tempfile.TemporaryDirectory(prefix='settingssections_') as t:
            subprocess.run(['javac', '-encoding', 'UTF-8', '-Xlint:all', '-Werror', '-d', t, str(TABLE),
                            str(APP / 'jvmtest/java/dev/peanuts/ui/SettingsSectionsJvmTest.java')], check=True)
            r = subprocess.run(['java', '-cp', t, 'dev.peanuts.ui.SettingsSectionsJvmTest'], capture_output=True, text=True)
            self.assertEqual(r.returncode, 0, r.stdout + r.stderr)
            self.assertRegex(r.stdout, r'^ok \d+')


class Mapping(unittest.TestCase):
    def test_every_built_row_has_a_place(self):
        built = built_rows()
        self.assertGreater(len(built), 40, 'the parser still finds the rows')
        _, rows = table()
        placed = [t for t, _ in rows]
        self.assertEqual(sorted(set(built) - set(placed)), [], 'rows Settings builds that the table does not place (they would land in "More")')
        self.assertEqual(sorted(set(placed) - set(built)), [], 'rows in the table that Settings no longer builds')
        self.assertEqual(len(placed), len(set(placed)), 'a row in two places')

    def test_icons_are_in_the_resources(self):
        sections, rows = table()
        self.assertEqual([s[1] for s in sections], ['Library', 'Console', 'Graphics', 'Controls', 'Screens', 'Users', 'App', 'Help', 'Developer', 'About'])
        icons = {s[2] for s in sections} | {i for _, i in rows} | {'menu', 'close'}
        res = APP / 'peanuts/res/drawable'
        for i in sorted(icons):
            f = res / f'pi_{i.replace("-", "_")}.xml'
            self.assertTrue(f.is_file(), f'{f.name} for the icon {i}')
            xml = f.read_text(encoding='utf-8')
            self.assertIn('?attr/pnIconInk', xml)
        styles = (APP / 'peanuts/res/values/peanuts_icons.xml').read_text(encoding='utf-8')
        page = (UI / 'Icons.java').read_text(encoding='utf-8')
        for style in re.findall(r'\("(PeanutsIcon\w*)"\)', page):
            self.assertIn(f'<style name="{style}"', styles)


class Wiring(unittest.TestCase):
    def test_one_hook_at_the_end_of_buildSettings(self):
        main = MAIN.read_text(encoding='utf-8')
        hooks = [l for l in main.splitlines() if 'dev.peanuts.ui.SettingsPage' in l]
        self.assertEqual(len(hooks), 1)
        self.assertIn('PEANUTS', hooks[0])
        body = between(main, 'private void buildSettings(LinearLayout content)', '/** A settings group\'s card of rows')
        self.assertRegex(body, r'refreshSettings\(\);\n\s*dev\.peanuts\.ui\.SettingsPage\.lay\(this, content\);[^\n]*\n\s*\}\s*$')

    def test_control_interface_names_sections(self):
        ctl = (REPO / 'android/app/src/main/java/dev/peanuts/ctl/Ctl.java').read_text(encoding='utf-8')
        self.assertIn('dev.peanuts.ui.SettingsPage.go(a, section)', ctl)
        self.assertNotIn('private static boolean scrollTo(', ctl)
        doc = (REPO / 'peanuts/docs/CONTROL.md').read_text(encoding='utf-8')
        sections, _ = table()
        for _, label, _ in sections:
            self.assertTrue(f'`{label}`' in doc, f'CONTROL.md names the section {label}')


class Text(unittest.TestCase):
    def test_ui_rules(self):
        for f in ('SettingsSections.java', 'SettingsPage.java'):
            src = (UI / f).read_text(encoding='utf-8')
            strings = ''.join(re.findall(r'"((?:[^"\\]|\\.)*)"', src))
            for bad in ('·', '•', ' - ', '–', '—', '!'):
                self.assertNotIn(bad, strings, f'{f}: {bad!r} in its text')
            self.assertIsNone(re.search('[\U0001F300-\U0001FAFF☀-➿]', strings), f'{f}: an emoji in its text')


if __name__ == '__main__':
    unittest.main()
