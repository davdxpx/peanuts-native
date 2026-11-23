package dev.peanuts.mods;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import dev.nxnative.android.CheatsBridge;
import dev.nxnative.android.R;
import dev.peanuts.ui.Feel;
import dev.peanuts.ui.Icons;
import dev.peanuts.ui.PUi;

/**
 * Mods &amp; Cheats › Add (SPEC-mod-menu.md, Package B 2): a file (.zip or .txt) or a folder from this device, read off the UI thread
 * (CheatImport), then one dialog in the app's AlertDialog look: the cheats for the game's version ("Add cheats"), or why not (another version, another game, no
 * build named, nothing found). A pick with romfs or exefs folders is a mod: it goes to the mod import (ModsSection) on the page's Mods tab. Nothing is downloaded.
 * An .mncf (or an .mncp pack of them) is added as it is; any other cheat file becomes an .mncf as it is added (CheatSources.add: MncfConvert), off the UI thread,
 * and is made ready for the game's build before the page lists it. Export ({@link #export}) writes the game's sources as .mncf or .mncp (MncfExport).
 * The pickers' results come back through MainActivity.onActivityResult → dev.peanuts.ui.LibrarySettings.result → {@link #result}.
 */
public final class CheatImportUi {
    private CheatImportUi() {}

    public static final int REQ_FILE = CheatsBridge.REQ_FILE, REQ_FOLDER = CheatsBridge.REQ_FOLDER, REQ_EXPORT = CheatsBridge.REQ_EXPORT;   // the cheat rows' codes: this page replaces those rows
    private static boolean waiting, exporting;   // a pick or an export of this page is open (the rows' sheet uses the same codes)

    /** Add: a file or a folder */
    static void add(Activity a, String titleId) {
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        AlertDialog[] d = new AlertDialog[1];
        PUi.add(box, PUi.body(a, "Cheats or a mod for this game, from this device. Peanuts never downloads any."), 0);
        PUi.Setting file = new PUi.Setting(a, "", "A file", Feel.row(v -> { d[0].dismiss(); pick(a, false); })).value("A .zip, a .txt, an .mncf or an .mncp").chevron(a);
        Icons.tile((TextView) file.view.getChildAt(0), "list");
        PUi.add(box, file.view, 12);
        PUi.Setting folder = new PUi.Setting(a, "", "A folder", Feel.row(v -> { d[0].dismiss(); pick(a, true); })).value("With Atmosphère, Eden or Ryujinx folders, or a mod's").chevron(a);
        Icons.tile((TextView) folder.view.getChildAt(0), "folder");
        PUi.add(box, folder.view, 4);
        d[0] = PUi.sheet(a, "Add cheats or mods", box).setNegativeButton("Cancel", null).create();
        Feel.dialog(d[0]);
        d[0].show();
    }

    private static void pick(Activity a, boolean folder) {
        Intent i = folder ? new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE) : new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
                .putExtra(Intent.EXTRA_MIME_TYPES, new String[] {"application/zip", "application/x-zip-compressed", "text/plain", "application/octet-stream", "application/toml"});
        i.putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI, Uri.parse("content://com.android.externalstorage.documents/document/primary%3A"));
        try { waiting = true; a.startActivityForResult(i, folder ? REQ_FOLDER : REQ_FILE); }
        catch (android.content.ActivityNotFoundException e) { Toast.makeText(a, "This device has no file picker.", Toast.LENGTH_LONG).show(); }
    }

    /** Export: the game's sources as one .mncf, or an .mncp when there are several, to a place the player picks */
    static void export(Activity a, CheatsPage.Game g, int sources) {
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/octet-stream")
                .putExtra(Intent.EXTRA_TITLE, MncfExport.fileName(g.name, sources > 1));
        try { exporting = true; a.startActivityForResult(i, REQ_EXPORT); }
        catch (android.content.ActivityNotFoundException e) { exporting = false; Toast.makeText(a, "This device has no file picker.", Toast.LENGTH_LONG).show(); }
    }

    /** The export written to the document picked, off the UI thread */
    private static void exported(Activity a, CheatsPage.Game g, Uri u) {
        CheatPick.work(a, "Export cheats", () -> {
            File folder = CheatsBridge.folder(a, g.titleId);
            String build = CheatsBridge.builds(g.titleId, g.packDir).stream().findFirst().orElse(null);
            CheatSources.Merged m = build == null ? null : CheatsBridge.merged(a, g.titleId, g.gameDir, g.packDir, build, CheatsBridge.textEnd(g.packDir), true);
            MncfExport.Out out = MncfExport.make(folder, g.name, m, CheatsBridge.on(a, g.titleId), CheatValues.read(folder));
            if (out == null) throw new IOException("There are no added cheats to export.");
            try (java.io.OutputStream os = a.getContentResolver().openOutputStream(u)) {
                if (os == null) throw new IOException("The place picked cannot be written. Pick another one.");
                os.write(out.bytes);
            }
            return out;
        }, out -> {
            Feel.accepted(a);
            Toast.makeText(a, "Exported " + CheatsPage.count(out.cheats, "cheat") + (out.pack ? " of " + out.sources + " sources as a pack." : "."), Toast.LENGTH_LONG).show();
        }, e -> {
            Feel.refused(a);
            android.util.Log.w("peanuts", "cheats: export: " + e);
            message(a, "Not exported", e instanceof IOException && e.getMessage() != null && e.getMessage().endsWith(".") ? e.getMessage() : "The cheats could not be written there. Nothing was changed here.");
        });
    }

    /** LibrarySettings.result (PEANUTS: Mods & Cheats' pickers, asked before CheatsBridge's): true when the request was Add's or Export's, the ones this page started */
    public static boolean result(Activity a, int request, int result, Intent data) {
        if (request == REQ_EXPORT && exporting) {
            exporting = false;
            CheatsPage.Game g = CheatsPage.shown();
            if (result == Activity.RESULT_OK && data != null && data.getData() != null && g != null) exported(a, g, data.getData());
            return true;
        }
        if (request != REQ_FILE && request != REQ_FOLDER || !waiting) return false;
        waiting = false;
        if (result != Activity.RESULT_OK || data == null || data.getData() == null) return true;
        CheatsPage.Game g = CheatsPage.shown();
        if (g == null) { Toast.makeText(a, "Open the game's Mods & Cheats again to add them.", Toast.LENGTH_LONG).show(); return true; }
        Uri u = data.getData();
        boolean folder = request == REQ_FOLDER;
        CheatPick.read(a, u, folder, g.titleId, CheatsBridge.builds(g.titleId, g.packDir), "Add cheats or mods", r -> {
            if (r.error != null) { Feel.refused(a); message(a, "Not added", r.error); return; }
            decide(a, g, r.found, r.name == null ? "it" : r.name, r.from, u, r.display, folder);
        });
        return true;
    }

    /** One dialog for what the pick holds */
    static void decide(Activity a, CheatsPage.Game g, CheatImport.Found f, String what, String from, Uri u, String display, boolean folder) {
        String build = CheatsBridge.builds(g.titleId, g.packDir).stream().findFirst().orElse(null);
        String version = build == null ? null : CheatsBridge.version(g.titleId, build);
        String yours = version != null ? version : "your version";
        String kind = folder ? "folder" : CheatSources.FROM_ZIP.equals(from) ? "zip" : "file";
        if (f.mod) {   // romfs or exefs: the mod import, which takes a mod's cheats along
            if (!CheatsPage.showMods() || !dev.peanuts.settings.ModsSection.installPicked(a, u, display, folder)) Toast.makeText(a, "Open the game's Mods & Cheats again to install the mod.", Toast.LENGTH_LONG).show();
            return;
        }
        if (build == null) { message(a, "Build the game first", "Cheats are made for one build of the game, and this game's build is not known yet. Prepare the game, then add them."); return; }
        File folderOf = CheatsBridge.folder(a, g.titleId);
        if (!f.mncf.isEmpty()) { decideMncf(a, g, f, what, from, build, yours); return; }
        if (!f.forGame.isEmpty()) {
            for (CheatImport.Hit h : f.forGame) {
                CheatSources.Source same = CheatSources.sameAs(folderOf, h.text);
                if (same != null && f.forGame.size() == 1) { message(a, "Added already", same.name + " has the same cheats. Nothing was added."); return; }
            }
            int n = f.cheats();
            List<String[]> lines = new ArrayList<>();
            lines.add(new String[] {"check", CheatsPage.count(n, "cheat") + " for your version",
                    "Build " + CheatsPage.shortBuild(build) + ", the one your game was built from." + (f.groups() > 0 ? " " + CheatsPage.count(f.groups(), "group") + "." : "")});
            if (f.unsupported() > 0) lines.add(new String[] {"info", f.unsupported() + " more " + (f.unsupported() == 1 ? "is" : "are") + " not supported",
                    "They use a part of the cheat format Peanuts does not run. They are listed and stay off."});
            if (f.restores()) lines.add(new String[] {"info", "Restore Code is left out", "Peanuts puts the original code back by itself when you turn a cheat off."});
            if (!f.otherVersion.isEmpty()) lines.add(new String[] {"info", CheatsPage.count(f.otherVersion.size(), "file") + " for another version",
                    "Kept with them: they work when the game is that version."});
            if (!folder && CheatSources.FROM_ZIP.equals(from)) lines.add(new String[] {"info", "No mods in this zip", "Mods go in the same way: a zip with romfs or exefs folders."});
            dialog(a, "Add cheats", "From " + what + " for " + g.name + (version != null ? " " + version : "") + ".", lines,
                    "Cancel", null, "Add " + CheatsPage.count(n, "cheat"), () -> take(a, f, from, n));
            return;
        }
        if (!f.otherVersion.isEmpty()) {
            List<String> theirs = new ArrayList<>();
            int n = 0;
            for (CheatImport.Hit h : f.otherVersion) { if (!theirs.contains(CheatsPage.shortBuild(h.bid))) theirs.add(CheatsPage.shortBuild(h.bid)); n += h.cheats(); }
            List<String[]> lines = new ArrayList<>();
            lines.add(new String[] {"warning", "In the " + kind + ": build " + String.join(", ", theirs),
                    "Your game: " + yours + ", build " + CheatsPage.shortBuild(build) + ". None of the " + CheatsPage.count(n, "cheat") + " would hit the right places."});
            lines.add(new String[] {"info", "Look for cheats for " + yours, "The file has to be named " + build + ".txt or start with that build."});
            dialog(a, "These cheats are for another version", "Cheats only work on the exact build they were made for.", lines,
                    "Keep them anyway", () -> take(a, f, from, 0), "OK", null);
            return;
        }
        if (!f.otherGame.isEmpty() || !f.mncfOtherGame.isEmpty()) {
            String other = !f.otherGame.isEmpty() ? f.otherGame.get(0).tid : f.mncfOtherGame.get(0).file.gameId();
            message(a, "These cheats are for another game", "The " + kind + " has them under the title ID " + other + ", not this game's. Nothing was added.");
            return;
        }
        if (!f.mncfBroken.isEmpty()) {
            android.util.Log.w("peanuts", "cheats: .mncf files that cannot be read: " + f.mncfBroken);
            message(a, "These cheats cannot be read", "The " + kind + " has a Mistel cheat file Peanuts cannot read: it is damaged or of a newer kind. Nothing was added.");
            return;
        }
        if (!f.noBuild.isEmpty()) {
            List<String[]> lines = new ArrayList<>();
            lines.add(new String[] {"info", "Name it " + build + ".txt", "Or start it with the line build " + build + ", if it was made for " + yours + "."});
            dialog(a, "These cheats do not name their version", "A cheat file has to say which build of the game it was made for. Nothing was added.", lines, null, null, "OK", null);
            return;
        }
        message(a, "No cheats in it", "There is no cheat file in the " + kind + ". Cheats are text files in the Atmosphère, Eden or Ryujinx folders; a mod is a zip or a folder with romfs or exefs folders.");
    }

    /** An .mncf pick (one or more files, or an .mncp): one dialog, the files added as they are */
    private static void decideMncf(Activity a, CheatsPage.Game g, CheatImport.Found f, String what, String from, String build, String yours) {
        File folderOf = CheatsBridge.folder(a, g.titleId);
        List<CheatImport.MncfHit> fresh = new ArrayList<>();
        List<String> had = new ArrayList<>();
        for (CheatImport.MncfHit h : f.mncf) { CheatSources.Source same = CheatSources.sameMncf(folderOf, h.data); if (same != null) had.add(same.name); else fresh.add(h); }
        if (fresh.isEmpty()) { message(a, "Added already", String.join(", ", had) + (had.size() == 1 ? " has" : " have") + " the same cheats. Nothing was added."); return; }
        int n = 0;
        boolean ours = false;
        List<String> versions = new ArrayList<>();
        for (CheatImport.MncfHit h : fresh) {
            n += h.cheats();
            if (MncfBuild.builds(h.file).contains(build)) ours = true;
            for (io.github.davdxpx.mistel.mncf.Build b : h.file.builds().values()) if (!versions.contains(b.version())) versions.add(b.version());
        }
        List<String[]> lines = new ArrayList<>();
        lines.add(new String[] {"check", CheatsPage.count(n, "cheat") + (fresh.size() > 1 ? " in " + fresh.size() + " files" : ""),
                ours ? "Made for " + yours + " among others." : versions.isEmpty() ? "It names no version: the cheats that find their place in the game's code work on any." : "Made for " + String.join(", ", versions) + ". The cheats that find their place in the game's code can work on " + yours + " too."});
        for (CheatImport.MncfHit h : fresh) {
            io.github.davdxpx.mistel.mncf.SignatureResult sig = h.file.signature();   // only a verified file gets a line; an unsigned one is as any other
            if (io.github.davdxpx.mistel.mncf.SignatureResult.VERIFIED.equals(sig.result()) && !sig.signers().isEmpty())
                lines.add(new String[] {"check", "Signed by " + sig.signers().get(0), "Its file is as " + sig.signers().get(0) + " made it."});
        }
        lines.add(new String[] {"info", "Checked for your version next", "Peanuts looks up each cheat's place in your game and lists the ones that cannot work with the reason."});
        if (!had.isEmpty()) lines.add(new String[] {"info", "Added already: " + String.join(", ", had), "Those are not added again."});
        int cheats = n;
        dialog(a, "Add cheats", "From " + what + " for " + g.name + ".", lines, "Cancel", null, "Add " + CheatsPage.count(n, "cheat"), () -> takeMncf(a, fresh, from, cheats));
    }

    /** The pick's sources added (one per Eden or Ryujinx set, else one), each made one .mncf, on; n: the game's cheats, for the line after */
    private static void take(Activity a, CheatImport.Found f, String from, int n) {
        CheatsPage.Game g = CheatsPage.shown();
        if (g == null) return;
        File folder = CheatsBridge.folder(a, g.titleId);
        String origin = f.name;
        added(a, g, n, () -> {
            List<CheatSources.Source> out = new ArrayList<>();
            long now = System.currentTimeMillis();
            MncfConvert.Known k = CheatsBridge.known(a, g.titleId, g.name, g.packDir);
            for (Map.Entry<String, Map<String, String>> e : f.sources().entrySet()) out.add(CheatSources.add(folder, e.getKey(), from, e.getValue(), now++, k, origin));
            return out;
        });
    }

    /** .mncf files added as they are, on */
    private static void takeMncf(Activity a, List<CheatImport.MncfHit> files, String from, int n) {
        CheatsPage.Game g = CheatsPage.shown();
        if (g == null) return;
        File folder = CheatsBridge.folder(a, g.titleId);
        added(a, g, n, () -> {
            List<CheatSources.Source> out = new ArrayList<>();
            long now = System.currentTimeMillis();
            for (CheatImport.MncfHit h : files) out.add(CheatSources.addMncf(folder, h.name(), from, h.data, now++));
            return out;
        });
    }

    /**
     * Sources added off the UI thread (add: makes them; a conversion reads the game's main), then made ready for the game's build, and their cheats that are on
     * by default (an export's: the ones that were on) switched on; then the line after and the page again. A failure leaves what was added before it
     */
    private static void added(Activity a, CheatsPage.Game g, int n, java.util.concurrent.Callable<List<CheatSources.Source>> add) {
        CheatPick.work(a, "Add cheats", () -> {
            List<CheatSources.Source> made = add.call();
            String build = CheatsBridge.builds(g.titleId, g.packDir).stream().findFirst().orElse(null);
            java.util.Set<String> on = new java.util.LinkedHashSet<>();
            if (build != null) {
                CheatSources.Merged m = CheatsBridge.merged(a, g.titleId, g.gameDir, g.packDir, build, CheatsBridge.textEnd(g.packDir), true);
                java.util.Set<String> ids = new java.util.HashSet<>();
                for (CheatSources.Source s : made) ids.add(s.id);
                for (CheatSources.Row r : m.rows) if (ids.contains(r.block.id) && r.cheat.mncf != null && r.cheat.mncf.isDefault && r.cheat.switchable()) on.add(r.key);
            }
            return on;
        }, on -> {
            if (!on.isEmpty()) { java.util.Set<String> all = CheatsBridge.on(a, g.titleId); all.addAll(on); CheatsBridge.on(a, g.titleId, all); }
            Feel.accepted(a);
            Toast.makeText(a, n > 0 ? "Added " + CheatsPage.count(n, "cheat") + "." + (on.isEmpty() ? " Turn on the ones you want." : " " + CheatsPage.count(on.size(), "cheat") + " on, as the file has them.") : "Kept for another version.", Toast.LENGTH_LONG).show();
            CheatsPage.rebuild();
        }, e -> {
            Feel.refused(a);
            android.util.Log.w("peanuts", "cheats: adding a source: " + e);
            message(a, "Not added", "The cheats could not be kept in the app's storage. Free some space and add them again.");
            CheatsPage.rebuild();
        });
    }

    // ---- The dialogs, in the app's AlertDialog look ----

    private static void message(Activity a, String title, String text) {
        AlertDialog d = new AlertDialog.Builder(a).setTitle(title).setMessage(text).setPositiveButton("OK", null).create();
        Feel.dialog(d);
        d.show();
    }

    /** A dialog with a lead and lines of an icon, a heading and a line under it (icon: check, info, warning) */
    private static void dialog(Activity a, String title, String lead, List<String[]> lines, String negative, Runnable onNegative, String positive, Runnable onPositive) {
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        TextView l = PUi.body(a, lead);
        l.setLineSpacing(0, 1.15f);
        box.addView(l);
        for (String[] line : lines) {
            LinearLayout r = PUi.row(a);
            r.setGravity(android.view.Gravity.TOP);
            TextView tile = PUi.text(a, "", 12, R.color.bn_text, 400);
            tile.setBackground(PUi.round(PUi.col(a, R.color.bn_tint), PUi.dp(a, 10), 0, 0));
            r.addView(tile, new LinearLayout.LayoutParams(PUi.dp(a, 32), PUi.dp(a, 32)));
            Icons.tile(tile, line[0]);
            LinearLayout col = new LinearLayout(a);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setPadding(PUi.dp(a, 12), PUi.dp(a, 1), 0, 0);
            col.addView(PUi.text(a, line[1], 15, R.color.bn_text, 600));
            TextView sub = PUi.small(a, line[2]);
            sub.setLineSpacing(0, 1.1f);
            col.addView(sub);
            r.addView(col, PUi.weight());
            PUi.add(box, r, 14);
        }
        AlertDialog.Builder b = PUi.sheet(a, title, box);
        if (positive != null) b.setPositiveButton(positive, onPositive == null ? null : Feel.confirm((x, w) -> onPositive.run()));
        if (negative != null) b.setNegativeButton(negative, onNegative == null ? null : (x, w) -> onNegative.run());
        AlertDialog d = b.create();
        Feel.dialog(d);
        d.show();
    }

}
