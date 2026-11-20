"""Import pack… before the first build (GitHub issue #44; dev.peanuts.pack.FirstImport, PackImport and its Upstream, dev.peanuts.ui.PackImportUi).

On the JVM (FirstImportJvmTest): the rules and the words, and the whole import through upstream's own PackZip, Pack, BuildCheck and SaveStore: a pack folder
and a zip in, into the app's packs/<id> and into a build folder's <id>/pack (staged beside it), the old pack kept until the new one is in, a pack of another
version or another game refused with nothing written, the Storage tab's import, a running game, another list entry on the same pack folder and an unfinished
Prepare job refusing it, a picked pack folder let go, a dump folder the pack fits listed (ready to play) and one of another build refused. The upstream classes
that need Android are stand-ins written here; the source checks below say that upstream's still have the names and parameters Upstream.java reflects on, and
that Build still takes a pack it finds where the import puts it. Skipped without a JDK."""
import re
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
APP = REPO / 'android/app/src'
UP = APP / 'main/java/dev/nxnative/android'
OND = APP / 'ondevice/java/dev/nxnative/android'
OWN = APP / 'main/java/dev/peanuts'
SRC = [OWN / 'pack' / n for n in ('FirstImport.java', 'PackImport.java', 'Upstream.java')]
REAL = [UP / f'{n}.java' for n in ('PackZip', 'Pack', 'DumpLayout', 'BuildCheck', 'RomfsCheck', 'Nup', 'Nsp', 'Zstd', 'Profile', 'ShaderLibs', 'SaveStore')]
TEST = APP / 'jvmtest/java/dev/peanuts/pack/FirstImportJvmTest.java'

BUILD_CONFIG = '''package dev.nxnative.android;
public final class BuildConfig {
    public static final String NX_PROFILES = "040074A01BF12000\\tDonkey Kong Bananza\\t22852\\tabcdef012345\\t458752 main=aa\\n0400C3F00006E000\\tMario Kart World\\t0\\tunknown\\t";
    public static final String NX_DMSH_VK_CHAIN = "c0ffee", NX_DMSH_VK_ABI = "abi1", NX_DMSH_VK_ABI_COMPAT = "";
}
'''

# Stand-ins for upstream's classes that need Android: each method has the parameters of upstream's (checked by Reflection.test_names); their state is public
STUBS = {
    'android/content/Context.java': 'package android.content;\npublic abstract class Context {}\n',
    'android/util/Log.java': '''package android.util;
public final class Log {
    public static int i(String t, String m) { return 0; }
    public static int w(String t, String m) { return 0; }
    public static int w(String t, String m, Throwable e) { return 0; }
}
''',
    'dev/nxnative/android/Game.java': '''package dev.nxnative.android;
import android.content.Context;
import java.util.ArrayList;
import java.util.List;
public final class Game {
    public static final List<Game> LIST = new ArrayList<>();
    final String titleId, name, gameDir, packDir;
    Game(String titleId, String name, String gameDir, String packDir) { this.titleId = titleId; this.name = name; this.gameDir = gameDir; this.packDir = packDir; }
    static List<Game> load(Context c) { return new ArrayList<>(LIST); }
    static Game put(Context c, Game g) { LIST.removeIf(x -> x.gameDir.equals(g.gameDir)); LIST.add(g); return g; }
}
''',
    'dev/nxnative/android/GameList.java': '''package dev.nxnative.android;
import java.io.File;
import java.io.IOException;
final class GameList {
    static boolean samePath(String a, String b) {
        if (a == null || b == null) return false;
        try { return new File(a).getCanonicalPath().equals(new File(b).getCanonicalPath()); } catch (IOException e) { return a.equals(b); }
    }
}
''',
    'dev/nxnative/android/PackUi.java': '''package dev.nxnative.android;
public final class PackUi {
    public static boolean BUSY;
    static synchronized boolean busy(String titleId) { return BUSY; }
}
''',
    'dev/nxnative/android/SaveUi.java': '''package dev.nxnative.android;
import android.content.Context;
public final class SaveUi {
    public static boolean RUNNING;
    static boolean gameRunning(Context c, Game g) { return RUNNING; }
}
''',
    'dev/nxnative/android/Folders.java': '''package dev.nxnative.android;
import android.content.Context;
import java.util.HashMap;
import java.util.Map;
public final class Folders {
    public static final Map<String, String> PICKED = new HashMap<>();
    static String pickedPack(Context a, String id) { return PICKED.get(id); }
    static void pickedPack(Context a, String id, String path) { if (path == null) PICKED.remove(id); else PICKED.put(id, path); }
}
''',
    'dev/nxnative/android/Prefs.java': '''package dev.nxnative.android;
import android.content.Context;
import java.util.HashMap;
import java.util.Map;
public final class Prefs {
    public static final Map<String, String> FOLDERS = new HashMap<>();
    static String folder(Context c, String key) { return FOLDERS.get(key); }
    static void folder(Context c, String key, String path) { if (path == null) FOLDERS.remove(key); else FOLDERS.put(key, path); }
}
''',
    'dev/nxnative/android/DumpCheck.java': '''package dev.nxnative.android;
import java.io.File;
final class DumpCheck {
    static File workOf(File dump, String titleId) {
        File parent = dump.getAbsoluteFile().getParentFile();
        return new File(parent != null ? parent : dump, titleId + "-work");
    }
}
''',
    # job.json here is one line, "<state>\\t<pack folder>" (Job.read's own format is JSON; the import asks only state() and pack())
    'dev/nxnative/android/Job.java': '''package dev.nxnative.android;
import android.content.Context;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
public final class Job {
    public static File REMEMBERED;
    private final String state;
    private final File pack;
    private Job(String state, File pack) { this.state = state; this.pack = pack; }
    static File fileOf(File work) { return new File(new File(work, "ondevice"), "job.json"); }
    static File remembered(Context c) { return REMEMBERED; }
    static Job read(File f) throws IOException {
        String[] c = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).trim().split("\\t");
        if (c.length != 2) throw new IOException(f + " is not a job");
        return new Job(c[0], new File(c[1]));
    }
    synchronized String state() { return state; }
    synchronized File pack() { return pack; }
}
''',
}


def text(p):
    return p.read_text(encoding='utf-8')


class Reflection(unittest.TestCase):
    """Upstream.java reaches upstream's package-private classes by name: each name and its parameters, as upstream's sources have them"""

    def test_names(self):
        want = {
            UP / 'PackZip.java': [r'static String titleOf\(File source\) throws IOException', r'static Pack stage\(File source, File packs, String want\) throws IOException',
                                  r'static void swapIn\(File part, File target\) throws IOException'],
            UP / 'SaveStore.java': [r'static void deleteTree\(File f\)'],
            UP / 'Pack.java': [r'final File dir;', r'final String titleId, name, version;', r'static String libraryProblem\(File dir, String titleId\)',
                               r'static File playableLibrary\(File dir, String titleId\)', r'static File legacyLibrary\(File dir, String titleId\)'],
            UP / 'BuildCheck.java': [r'static Result build\(Pack p, File game\)', r'final Kind kind;', r'final String message, detail;',
                                     r'enum Kind \{ OK, OTHER_BUILD, MISSING, UNREADABLE, UNSUPPORTED, ROMFS \}'],
            UP / 'PackUi.java': [r'static synchronized boolean busy\(String titleId\)'],
            UP / 'SaveUi.java': [r'static boolean gameRunning\(Context c, Game g\)'],
            UP / 'Game.java': [r'final String titleId, name, gameDir, packDir;', r'Game\(String titleId, String name, String gameDir, String packDir\) \{',
                               r'static List<Game> load\(Context c\)', r'static Game put\(Context c, Game g\) \{'],
            UP / 'GameList.java': [r'static boolean samePath\(String a, String b\)'],
            UP / 'Folders.java': [r'static String pickedPack\(Context a, String id\)', r'static void pickedPack\(Context a, String id, String path\)'],
            UP / 'Prefs.java': [r'static String folder\(Context c, String key\)', r'static void folder\(Context c, String key, String path\)'],
            OND / 'DumpCheck.java': [r'static File workOf\(File dump, String titleId\)'],
            OND / 'Job.java': [r'static File fileOf\(File work\)', r'static File remembered\(Context c\)', r'static Job read\(File f\) throws IOException',
                               r'synchronized String state\(\)', r'synchronized File pack\(\)'],
        }
        for f, pats in want.items():
            src = text(f)
            for p in pats:
                self.assertRegex(src, p, f'{f.name}: {p}')

    def test_reflected_names_are_checked(self):
        """Every class Upstream.java names is one test_names reads: a new reflective call comes with its check"""
        up = text(OWN / 'pack/Upstream.java')
        named = set(re.findall(r'k\("(\w+)"\)', up))
        self.assertEqual(named, {'PackZip', 'SaveStore', 'Pack', 'BuildCheck', 'PackUi', 'SaveUi', 'Game', 'GameList', 'Folders', 'Prefs', 'DumpCheck', 'Job'})

    def test_stubs_match(self):
        """The stand-ins have upstream's parameters (the JVM test calls them through Upstream.java, as the app calls upstream's)"""
        for rel, src in STUBS.items():
            if not rel.startswith('dev/'):
                continue
            real = text((OND if rel.endswith(('DumpCheck.java', 'Job.java')) else UP) / Path(rel).name)
            for sig in re.findall(r'^\s*((?:static |synchronized )+[\w<>]+ \w+\([^)]*\))', src, re.M):
                self.assertIn(sig.strip(), real, f'{rel}: {sig.strip()}')


class Wiring(unittest.TestCase):
    def test_build_takes_the_pack_where_it_goes(self):
        """The import puts the pack where Build looks first: Prepare adopts a pack that Folders.packFor finds (AppStorage.pack, the build folders) or the Build
        destination's, and only makes what does not fit"""
        prep = text(OWN / 'build/Prepare.java')
        self.assertIn('try { packDir = Upstream.packFor(c, dumpR, id); }', prep)
        self.assertIn('job = Upstream.jobAdopt(r, packDir, code, sh);', prep)
        self.assertIn('File packDir = destination;', prep)
        svc = text(OWN / 'build/BuildService.java')
        self.assertIn('File pack = dir == null ? null : new File(dir, "pack");', svc)
        self.assertIn('return r.buildDestination == null ? PeanutsPrefs.buildGameDir(this, r.titleId) : PeanutsPrefs.buildGameDir(r.buildDestination, r.titleId);', svc)
        folders = text(UP / 'Folders.java')
        self.assertIn('PackFind.candidates(picked, AppStorage.pack(a, id), game, id, PackFind.volumeRoot(game))', folders)
        ui = text(OWN / 'ui/PackImportUi.java')
        self.assertIn('File dest = chosen == null ? null : PeanutsPrefs.buildGameDir(chosen, t.titleId);', ui)
        self.assertIn('File target = dest != null ? new File(dest, "pack") : dev.nxnative.android.AppStorage.pack(a, t.titleId);', ui)
        # the job Build would go on with: an NSP's .nup goes into the build folder's <title ID> when one applies, and its work folder beside it
        self.assertIn('File dir = again ? importDest : buildGameDir != null ? buildGameDir : new File(r.gameDir, r.fileName())', svc)
        self.assertIn('File buildDir = dest != null ? dest : PeanutsPrefs.buildGameDir(a, t.titleId);', ui)
        self.assertIn('if (chosenUpdate != null && chosenUpdate.needsImport()) { s.update = chosenUpdate.file;', text(OWN / 'ui/GameSheet.java'))
        self.assertIn('long version = chosenUpdate != null && chosenUpdate.needsImport() ? chosenUpdate.version : t.base.version;', ui)

    def test_entry_points(self):
        sheet = text(OWN / 'ui/GameSheet.java')
        self.assertIn('if (PackImportUi.offered(title)) {', sheet)
        self.assertIn('PUi.button(a, "Import pack…", PUi.Kind.QUIET', sheet)
        lib = text(OWN / 'ui/LibrarySettings.java')
        self.assertIn('if (PackImportUi.result(a, request, result, data)) return true;', lib)
        self.assertLess(lib.index('PackImportUi.result('), lib.index('if (request < REQ_KEYS || request > REQ_MOD_ZIP) return false;'))
        self.assertIn('if (dev.peanuts.pack.PackImport.busy(s.titleId))', text(OWN / 'ui/Library.java'))

    def test_request_codes_are_free(self):
        """72 and 73 are no other picker's: LibrarySettings' 60 to 70, BuildLogUi's 71, upstream's own (MainActivity, PackUi, SaveUi, Folders, GameCopy...)"""
        used = {}
        for f in list((APP / 'main/java').rglob('*.java')) + list((APP / 'ondevice/java').rglob('*.java')):
            if f.name == 'PackImportUi.java':
                continue
            for name, value in re.findall(r'\b((?:REQ|PICK|REQUEST)\w*)\s*=\s*(\d+)', text(f)):
                used.setdefault(int(value), []).append(f'{f.name}:{name}')
        for code in (72, 73):
            self.assertNotIn(code, used, used.get(code))

    def test_ctl(self):
        req = text(OWN / 'ctl/Request.java')
        self.assertIn('"import_pack"', req)
        self.assertIn('case "import_pack": titleId(true); need("path"); break;', req)
        self.assertIn('case "import_pack": importPack(a, r, res); break;', text(OWN / 'ctl/Ctl.java'))
        self.assertIn('`import_pack`', text(REPO / 'peanuts/docs/CONTROL.md'))

    def test_no_upstream_file_changed_for_it(self):
        """The feature reaches upstream only through reflection: no upstream source names it"""
        for f in list(UP.glob('*.java')) + list(OND.glob('*.java')):
            src = text(f)
            self.assertNotIn('PackImport', src, f.name)
            self.assertNotIn('FirstImport', src, f.name)


@unittest.skipUnless(shutil.which('javac') and shutil.which('java'), 'no JDK')
class FirstImportJvm(unittest.TestCase):
    def test_import(self):
        with tempfile.TemporaryDirectory() as d:
            d = Path(d)
            stub = d / 'stub'
            for rel, src in {**STUBS, 'dev/nxnative/android/BuildConfig.java': BUILD_CONFIG}.items():
                (stub / rel).parent.mkdir(parents=True, exist_ok=True)
                (stub / rel).write_text(src, encoding='utf-8')
            srcs = [str(p) for p in SRC + REAL + [TEST]] + [str(p) for p in stub.rglob('*.java')]
            c = subprocess.run(['javac', '-Xlint:all,-try', '-Werror', '-encoding', 'UTF-8', '-d', str(d / 'classes'), *srcs], capture_output=True, text=True)
            self.assertEqual(c.returncode, 0, c.stdout + c.stderr)
            (d / 'run').mkdir()
            r = subprocess.run(['java', '-cp', str(d / 'classes'), 'dev.peanuts.pack.FirstImportJvmTest', str(d / 'run')], capture_output=True, text=True, timeout=300)
            self.assertEqual(r.returncode, 0, r.stdout + r.stderr)
            self.assertTrue(r.stdout.startswith('ok '), r.stdout)


if __name__ == '__main__':
    unittest.main()
