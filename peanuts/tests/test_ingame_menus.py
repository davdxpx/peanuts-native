"""The in-game menus: the game menu at the left (dev.peanuts.ingame.GameMenu) and the quick
menu at the right (upstream's SidePanel with its tabs on a rail, dev.peanuts.ingame.Rail), joined by dev.nxnative.android.InGamePanel. Checks the marked
hooks in upstream's files, the rail's order and icons, the game menu's rows, the solid icons against design's icons.py output, and the text the player
reads against UI-RULES.md. On the JVM (InGameBackupJvmTest with SaveStore): Back up the save beside the host's Commit. Skipped without a JDK."""
import re
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
APP = REPO / 'android/app/src'
LAUNCHER = APP / 'main/java/dev/nxnative/android'
INGAME = APP / 'main/java/dev/peanuts/ingame'
RES = APP / 'peanuts/res'
UI_FILES = [LAUNCHER / 'InGamePanel.java', INGAME / 'GameMenu.java', INGAME / 'Rail.java', INGAME / 'Look.java', APP / 'main/java/dev/peanuts/ui/MenuFeel.java']


def marked(src, call):
    """The line with call carries a PEANUTS marker"""
    return any(call in line and 'PEANUTS' in line for line in src.splitlines())


class Hooks(unittest.TestCase):
    def test_side_panel(self):
        p = (LAUNCHER / 'SidePanel.java').read_text(encoding='utf-8')
        for call in ('InGamePanel.frame(this, sh, tabs, scroll)', 'InGamePanel.rail(this, tab, this::showTab)', 'InGamePanel.railFocused()',
                     'InGamePanel.slideIn(this, sheet)', 'InGamePanel.slideOut(this, sheet,', 'InGamePanel.panelBack(this)', 'InGamePanel.laid(this, box, tab)'):
            self.assertTrue(marked(p, call), f'SidePanel: the marked hook {call}')
        self.assertRegex(p, r'static int TAB_CONTROLS = 2, TAB_GENERAL = 3;\s+// PEANUTS', 'Controls moves behind Cheats and Mods')
        self.assertNotIn('setDuration(180)', p, "upstream's open animation is the quick menu's (InGamePanel.slideIn)")

    def test_game_activity(self):
        g = (LAUNCHER / 'GameActivity.java').read_text(encoding='utf-8')
        for call in ('InGamePanel.attach(this, root, panel)', 'InGamePanel.key(this, combo, k, down,', 'InGamePanel.toggle(this, true)',
                     'InGamePanel.backKey(this, e)',
                     'InGamePanel.menuKey(this, e, gamepad(e.getSource()))', 'InGamePanel.menuMotion(this, e)', 'InGamePanel.back(this)',
                     'static File runSave()'):
            self.assertTrue(marked(g, call), f'GameActivity: the marked hook {call}')
        self.assertNotIn('panel.padToggle()', g, 'the controller opens the game menu, not the quick menu')
        self.assertLess(g.index('InGamePanel.menuKey('), g.index('if (panel.isOpen() && gamepad(e.getSource()))'), 'the game menu takes the controller first')
        self.assertLess(g.index('InGamePanel.backKey('), g.index('InGamePanel.key(this, combo'), "the controller's Back before the combination")
        p = (LAUNCHER / 'InGamePanel.java').read_text(encoding='utf-8')
        back = p[p.index('static boolean backKey('):]
        back = back[:back.index('\n    }\n') + 6]
        self.assertIn('controllerBack(e)', back, "only the controller's own Back")
        self.assertIn('supportsSource(InputDevice.SOURCE_GAMEPAD)', p, "the device tells a controller's Back: Android gives it the keyboard's source alone")
        self.assertIn('FLAG_FALLBACK', p, "a Back Android made from an unused B is not the controller's Back")
        self.assertIn('PadPanel.HOLD_MS', back, 'held as long as Home for the quick menu')
        self.assertIn('toggle(a, true)', back, 'pressed: the game menu with the selection shown')
        plain = p[p.index('static void back(Activity a)'):]
        plain = plain[:plain.index('\n    }\n')]
        self.assertIn('padAttached()', plain, "Android's own Back (a key tool handed it on) with the selection while a controller is attached")
        self.assertIn('menu.at(quickRow);', p, 'B from the quick menu: back on the Quick menu row')

    def test_save_store(self):
        s = (LAUNCHER / 'SaveStore.java').read_text(encoding='utf-8')
        self.assertIn('    private static Backup backupLocked(', s, "backupLocked stays upstream's, private")
        span = s[s.index('PEANUTS begin: the recovery apart from the copy'):]
        span = span[:span.index('PEANUTS end')]
        self.assertIn('static Backup backupRunning(', span, 'backupRunning, in its marked span')
        running = span[span.index('static Backup backupRunning('):]
        running = running[:running.index('\n    }\n')]
        self.assertNotIn('recover', running, "no recovery on the running game's save: a marker there is the host's own Commit, still being written")
        self.assertIn('if (running && pending(save)) throw', span, 'a marker before the copy fails it')
        self.assertTrue(marked(s, 'if (running && pending(save)) throw new IOException("the save changed while it was copied (the game started saving it)")'),
                        'and one after its second scan, before the backup gets its name')
        b = (LAUNCHER / 'InGamePanel.java').read_text(encoding='utf-8')
        self.assertIn('SaveStore.backupRunning(save, to, SaveUi.stamp())', b, "the game menu's backup: SaveStore's own copy, without the recovery")
        self.assertNotIn('backupLocked', b)
        self.assertIn('GameActivity.runSave()', b, 'the save the game runs on (the player picked at the start)')
        self.assertIn('if (err.contains("no save folder")) break;', b, 'a Commit during the copy is tried again')


@unittest.skipUnless(shutil.which('javac') and shutil.which('java'), 'no JDK')
class BackupBesideACommit(unittest.TestCase):
    def test_jvm(self):
        """InGameBackupJvmTest: each state of the host's Commit as the copy starts, then a host committing beside backups (not on Windows)"""
        with tempfile.TemporaryDirectory() as d:
            srcs = [LAUNCHER / 'SaveStore.java', APP / 'jvmtest/java/dev/nxnative/android/InGameBackupJvmTest.java']
            r = subprocess.run(['javac', '-Xlint:all,-try', '-Werror', '-encoding', 'UTF-8', '-d', d, *map(str, srcs)], capture_output=True, text=True)
            self.assertEqual(r.returncode, 0, r.stdout + r.stderr)
            r = subprocess.run(['java', '-cp', d, 'dev.nxnative.android.InGameBackupJvmTest'], capture_output=True, text=True, timeout=120)
            self.assertEqual(r.returncode, 0, r.stdout[-3000:] + r.stderr[-3000:])
            self.assertIn('PASS', r.stdout)


class Menus(unittest.TestCase):
    def test_the_rail(self):
        mp = (LAUNCHER / 'ModsPanel.java').read_text(encoding='utf-8')
        order = re.findall(r'names\.add\((\w+|"\w+")\)', mp)
        self.assertEqual(order, ['CHEATS', 'MODS', '"Controls"', '"General"'], 'Performance, Display, then Cheats, Mods, Controls, the settings at the bottom')
        rail = (INGAME / 'Rail.java').read_text(encoding='utf-8')
        for tab, ic in (('Performance', 'bolt'), ('Display', 'display'), ('Cheats', 'cheats'), ('Mods', 'mods'), ('Controls', 'controller')):
            self.assertIn(f'case "{tab}": return "{ic}";', rail, f'{tab} has the solid {ic}')
        self.assertIn('"General".equals(tab) ? "Menu settings"', rail, "General is the menu's own settings")

    def test_the_game_menu(self):
        b = (LAUNCHER / 'InGamePanel.java').read_text(encoding='utf-8')
        rows = re.findall(r'new GameMenu\.Item\("([\w-]+)", "([^"]+)"', b)
        self.assertEqual([t for _, t in rows], ['Back to the game', 'Pause the game', 'Quick menu', 'Back up the save', 'Button mapping', 'Share log', 'Stop the game'])
        for icon, _ in rows:
            self.assertTrue((RES / 'drawable' / f'pis_{icon.replace("-", "_")}.xml').is_file(), f'the solid icon {icon}')
        gm = (INGAME / 'GameMenu.java').read_text(encoding='utf-8')
        self.assertIn('"Keep playing"', gm, 'Stop asks once, the safe choice first')
        self.assertIn('c.getDrawable(dev.nxnative.android.R.drawable.ic_chevron)', gm, "a row that opens a page shows the launcher's chevron")
        self.assertNotIn('"chevron-right"', gm, "there is no pi_chevron_right: Icons.get gave null, an empty space at the row's end")
        tab = (APP / 'main/java/dev/peanuts/mods/ModsTab.java').read_text(encoding='utf-8')
        self.assertIn('public static void fill(Activity a, LinearLayout box, Host host, int part)', tab, 'Cheats and Mods are two tabs')

    def test_solid_icons(self):
        styles = (RES / 'values/peanuts_ingame.xml').read_text(encoding='utf-8')
        for s in ('PeanutsSolidRest', 'PeanutsSolidSelected', 'PeanutsSolidMenu'):
            self.assertIn(f'<style name="{s}">', styles)
        for f in (RES / 'drawable').glob('pis_*.xml'):
            x = f.read_text(encoding='utf-8')
            self.assertIn('generated from icons.py', x, f'{f.name}: made by design/peanuts-native/icons/icons.py')
            self.assertNotRegex(x, r'fillColor="#(?!00000000)', f'{f.name}: colours come from the theme (?attr/pnSolid*)')

    def test_ui_text_rules(self):
        for f in UI_FILES:
            src = f.read_text(encoding='utf-8')
            for s in re.findall(r'"((?:[^"\\]|\\.)*)"', src):
                for bad, what in (('—', 'an em dash'), ('–', 'an en dash'), ('·', 'a middle dot'), (' • ', 'a bullet'), ('...', 'three dots'), ('…', 'an ellipsis')):
                    self.assertNotIn(bad, s, f'{f.name}: {what} in "{s}"')
                self.assertFalse(re.search('[\U0001F300-\U0001FAFF☀-➿]', s), f'{f.name}: an emoji in "{s}"')
                if ' ' in s and not s.startswith('(?') and not s.startswith('[RED]'):
                    self.assertNotIn('!', s, f'{f.name}: an exclamation mark in "{s}"')


class Feel(unittest.TestCase):
    def test_the_menus_feel(self):
        f = (APP / 'main/java/dev/peanuts/ui/MenuFeel.java').read_text(encoding='utf-8')
        self.assertIn('getBoolean("menu_haptics", true)', f, 'the tick is on by default')
        self.assertIn('getBoolean("menu_sounds", false)', f, 'the sounds are off by default')
        self.assertIn('Haptics.play(v, Haptics.Word.TICK)', f, "the launcher's haptics: its gaps and the system's setting")
        b = (LAUNCHER / 'InGamePanel.java').read_text(encoding='utf-8')
        for line in ('"Vibrate on each step"', '"Menu sounds"', 'MenuFeel.move(now)', 'MenuFeel.open(sheet)', 'MenuFeel.tab(box)'):
            self.assertIn(line, b)
        gm = (INGAME / 'GameMenu.java').read_text(encoding='utf-8')
        for line in ('MenuFeel.move(this)', 'MenuFeel.select(this)', 'MenuFeel.open(this)', 'MenuFeel.back(this)'):
            self.assertIn(line, gm)

    def test_console_details(self):
        stop = (RES / 'drawable/pis_stop.xml').read_text(encoding='utf-8')
        self.assertIn('M7.1 6.9a7.6 7.6 0 1 0 9.8 0', stop, 'Stop the game has the power sign')
        tab = (APP / 'main/java/dev/peanuts/mods/ModsTab.java').read_text(encoding='utf-8')
        self.assertIn('host.action("X", "All off"', tab, 'All off in the header with its button')
        pp = (APP / 'main/java/dev/peanuts/ui/PanelPolish.java').read_text(encoding='utf-8')
        self.assertIn('private static void inset(View v, float dp)', pp, 'the focus frame keeps clear of the first letter')
        self.assertIn('b.setMaxLines(1);', pp, 'a short choice: one line without the scroll that hides the frame')
        self.assertIn('public static View chosen(View old, View now)', pp, 'into a track of choices: onto the chosen one')
        self.assertIn('PanelPolish.landing(v)', (INGAME / 'Rail.java').read_text(encoding='utf-8'), 'a first row that is a track: its chosen choice')
        self.assertIn('b.setButtonDrawable(radioMark(dp, room ? in : 0));', pp, "a list of choices: a still mark of the panel's, clear of the frame")
        self.assertIn('ring(dp, ACCENT, true)', pp, 'the chosen one: a yellow ring with its dot')
        self.assertIn('if (b.getBackground() instanceof android.graphics.drawable.RippleDrawable) b.setBackground(null);', pp, "a list choice: no grey circle of the theme's")
        rail = (INGAME / 'Rail.java').read_text(encoding='utf-8')
        self.assertIn('if (direction == View.FOCUS_RIGHT) { View r = firstRow(); if (r != null) return r; }', rail, 'right from the rail: into the rows')
        self.assertIn('LinearLayout.HORIZONTAL) return v;', pp, 'a list one under the other keeps its step to the next row')
        panel = (LAUNCHER / 'InGamePanel.java').read_text(encoding='utf-8')
        self.assertIn('PanelPolish.chosen(old, now)', panel, 'the quick menu asks it at each step of the focus')
        self.assertIn('chosen.post(() -> { if (p.isOpen() && now.isFocused()) chosen.requestFocus(); });', panel, "after the change: the panel's frame follows the focus")


if __name__ == '__main__':
    unittest.main()
