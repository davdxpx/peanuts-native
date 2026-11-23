package dev.peanuts.mods;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
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
            lines.add(new String[] {"lock", "Checked before every write", "Each cheat that changes the game's code names the code it expects there, taken from your game. Peanuts compares it before it writes."});
            lines.add(new String[] {"update", "Ready for game updates", "Its places are found again by the code around them, so the cheats keep working after an update."});
            if (f.unsupported() > 0) lines.add(new String[] {"info", f.unsupported() + " more " + (f.unsupported() == 1 ? "is" : "are") + " not supported",
                    "They use a part of the cheat format Peanuts does not run. They are listed and stay off."});
            if (f.restores()) lines.add(new String[] {"info", "Restore Code is left out", "Peanuts puts the original code back by itself when you turn a cheat off."});
            if (!f.otherVersion.isEmpty()) lines.add(new String[] {"info", CheatsPage.count(f.otherVersion.size(), "file") + " for another version",
                    "Kept with them: they work when the game is that version."});
            if (!folder && CheatSources.FROM_ZIP.equals(from)) lines.add(new String[] {"info", "No mods in this zip", "Mods go in the same way: a zip with romfs or exefs folders."});
            View card = formatCard(a, "update", "Becomes a Mistel cheat file", "Atmosphère cheats from " + what + ". Peanuts keeps them as one .mncf for every version of the game.", null);
            dialog(a, "Add cheats", card, null, lines,
                    "Cancel", null, "Add " + CheatsPage.count(n, "cheat"), () -> take(a, f, from, n, display != null ? display : what));
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
                    "Keep them anyway", () -> take(a, f, from, 0, display != null ? display : what), "OK", null);
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
        List<io.github.davdxpx.mistel.mncf.MncfFile> files = new ArrayList<>();
        boolean ours = false;
        List<String> authors = new ArrayList<>();
        for (CheatImport.MncfHit h : fresh) {
            files.add(h.file);
            if (MncfBuild.builds(h.file).contains(build)) ours = true;
            for (String x : h.file.authors()) if (!authors.contains(x)) authors.add(x);
        }
        MncfFacts x = MncfFacts.of(files);
        int n = x.cheats;
        String by = authors.isEmpty() ? "" : " by " + String.join(", ", authors);
        View card = formatCard(a, "check", "Mistel cheat file", (fresh.size() == 1 ? fresh.get(0).name() + ".mncf" : fresh.size() + " files of a pack") + by, x.signer);
        List<String[]> lines = facts(x, yours, ours);
        lines.add(new String[] {"search", "Looked up in your game next", "Peanuts finds each cheat's place in " + yours + " and lists the ones that cannot work, with the reason."});
        if (!had.isEmpty()) lines.add(new String[] {"info", "Added already: " + String.join(", ", had), "Those are not added again."});
        dialog(a, "Add cheats", card, null, lines, "Cancel", null, "Add " + CheatsPage.count(n, "cheat"), () -> takeMncf(a, fresh, from, n));
    }

    /** What an .mncf brings, as the dialog's lines (only what it has): its cheats and versions, the checks, the updates, the numbers, the words */
    static List<String[]> facts(MncfFacts x, String yours, boolean ours) {
        List<String[]> lines = new ArrayList<>();
        String v = x.versionWords();
        lines.add(new String[] {"check", CheatsPage.count(x.cheats, "cheat"), ours ? (x.versions.size() > 1 ? "Made for " + v + ", " + yours + " among them." : "Made for " + yours + ", your version.")
                : v == null ? "It names no version: the cheats that find their place in the game's code work on any." : "Made for " + v + ". The cheats that find their place in the game's code can work on " + yours + " too."});
        if (x.checked > 0) lines.add(checked(x.checked, x.cheats));
        if (x.patterns > 0) lines.add(updates(x.patterns));
        if (x.values > 0) lines.add(new String[] {"scale", CheatsPage.count(x.values, "number") + " you can set", "In the game's Mods tab, within the range the file gives."});
        if (x.described > 0) lines.add(new String[] {"info", x.described == x.cheats ? (x.cheats == 1 ? "It says what it does" : "Each says what it does") : x.described == 1 ? "1 says what it does" : x.described + " say what they do",
                "Shown on the cheat and on the second screen."});
        return lines;
    }

    static String[] checked(int checked, int cheats) {
        String who = checked == cheats ? (cheats == 1 ? "It changes" : "Each changes") : checked == 1 ? "1 of them changes" : checked + " of them change";
        return new String[] {"lock", "Checked before every write", who + " the game's code only where the code it expects is, so nothing is written into the wrong place."};
    }

    static String[] updates(int patterns) {
        return new String[] {"update", "Ready for game updates", (patterns == 1 ? "1 cheat finds its place by the code around it, so it keeps" : patterns + " cheats find their place by the code around it, so they keep")
                + " working after an update."};
    }

    /**
     * The card at the top of the Add dialog and the sheet after it: the Mistel cheat file, what it is called and who made it, and who signed it when a key
     * Peanuts trusts did (an unsigned file gets no mark: it is as any other)
     */
    static View formatCard(Activity a, String icon, String title, String sub, String signer) {
        LinearLayout card = PUi.row(a);
        card.setGravity(android.view.Gravity.CENTER_VERTICAL);
        card.setPadding(PUi.dp(a, 14), PUi.dp(a, 12), PUi.dp(a, 14), PUi.dp(a, 12));
        card.setBackground(PUi.round(PUi.col(a, R.color.bn_tint), PUi.dp(a, 16), 0, 0));
        TextView tile = PUi.text(a, "", 12, R.color.bn_on_banana, 400);
        tile.setBackground(PUi.round(PUi.col(a, R.color.bn_banana), PUi.dp(a, 13), 0, 0));
        card.addView(tile, new LinearLayout.LayoutParams(PUi.dp(a, 44), PUi.dp(a, 44)));
        android.graphics.drawable.Drawable mark = Icons.get(a, icon, Icons.Ink.ON_BANANA);   // on the banana tile: Icons.tile draws in the row tile's colours
        if (mark != null) tile.setForeground(new android.graphics.drawable.InsetDrawable(mark, PUi.dp(a, 10)));
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(PUi.dp(a, 14), 0, PUi.dp(a, 8), 0);
        TextView t = PUi.text(a, title, 17, R.color.bn_text, 800);
        col.addView(t);
        TextView s = PUi.small(a, sub);
        s.setMaxLines(3);
        s.setEllipsize(android.text.TextUtils.TruncateAt.END);
        col.addView(s);
        card.addView(col, PUi.weight());
        if (signer != null) card.addView(CheatsPage.chip(a, "Signed by " + signer, true), PUi.wrap());
        return card;
    }

    /** The pick's sources added (one per Eden or Ryujinx set, else one), each made one .mncf, on; n: the game's cheats, for the line after */
    private static void take(Activity a, CheatImport.Found f, String from, int n, String origin) {
        CheatsPage.Game g = CheatsPage.shown();
        if (g == null) return;
        File folder = CheatsBridge.folder(a, g.titleId);
        added(a, g, n, true, () -> {
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
        added(a, g, n, false, () -> {
            List<CheatSources.Source> out = new ArrayList<>();
            long now = System.currentTimeMillis();
            for (CheatImport.MncfHit h : files) out.add(CheatSources.addMncf(folder, h.name(), from, h.data, now++));
            return out;
        });
    }

    /** What adding sources gave: the cheats it switched on (the file's defaults) and what the new files bring */
    private static final class Added {
        final java.util.Set<String> on = new java.util.LinkedHashSet<>();
        MncfFacts facts;
        String version;
        int work, refused;   // the new sources' cheats that work on this version, and the ones that cannot (listed with the reason)
        String named;        // the new files as the card names them: "Example cheats.mncf by Example Team"
    }

    /**
     * Sources added off the UI thread (add: makes them; a conversion reads the game's main, so the wait says it looks up the cheats there), then made ready for
     * the game's build, and their cheats that are on by default (an export's: the ones that were on) switched on; then a sheet with what the new Mistel cheat
     * files bring (converted: what the conversion made of them), and the page again. A failure leaves what was added before it
     */
    private static void added(Activity a, CheatsPage.Game g, int n, boolean converted, java.util.concurrent.Callable<List<CheatSources.Source>> add) {
        CheatPick.work(a, "Add cheats", converted ? "Finding each cheat's place in your game's code." : null, () -> {
            List<CheatSources.Source> made = add.call();
            Added out = new Added();
            String build = CheatsBridge.builds(g.titleId, g.packDir).stream().findFirst().orElse(null);
            out.version = build == null ? null : CheatsBridge.version(g.titleId, build);
            if (build != null) {
                CheatSources.Merged m = CheatsBridge.merged(a, g.titleId, g.gameDir, g.packDir, build, CheatsBridge.textEnd(g.packDir), true);
                java.util.Set<String> ids = new java.util.HashSet<>();
                for (CheatSources.Source s : made) ids.add(s.id);
                for (CheatSources.Row r : m.rows) if (ids.contains(r.block.id) && r.cheat.mncf != null && r.cheat.mncf.isDefault && r.cheat.switchable()) out.on.add(r.key);
                for (CheatSources.Block b : m.blocks) if (ids.contains(b.id)) for (CheatFile.Cheat c : b.parsed.cheats) { if (c.switchable()) out.work++; else out.refused++; }
            }
            List<io.github.davdxpx.mistel.mncf.MncfFile> files = new ArrayList<>();
            List<String> authors = new ArrayList<>();
            for (CheatSources.Source s : made) {
                try { files.add(io.github.davdxpx.mistel.mncf.Mncf.load(java.nio.file.Files.readAllBytes(s.file().toPath()))); } catch (IOException | io.github.davdxpx.mistel.mncf.MncfException ignored) { }
            }
            for (io.github.davdxpx.mistel.mncf.MncfFile f : files) for (String x : f.authors()) if (!authors.contains(x)) authors.add(x);
            out.facts = MncfFacts.of(files);
            out.named = (made.size() == 1 ? made.get(0).name + ".mncf" : made.size() + " files") + (authors.isEmpty() ? "" : " by " + String.join(", ", authors));
            return out;
        }, out -> {
            if (!out.on.isEmpty()) { java.util.Set<String> all = CheatsBridge.on(a, g.titleId); all.addAll(out.on); CheatsBridge.on(a, g.titleId, all); }
            Feel.accepted(a);
            CheatsPage.rebuild();
            if (n == 0) { Toast.makeText(a, "Kept for another version.", Toast.LENGTH_LONG).show(); return; }
            String yours = out.version != null ? out.version : "your version";
            MncfFacts x = out.facts;
            List<String[]> lines = new ArrayList<>();
            int all = out.work + out.refused;
            lines.add(new String[] {"check", out.refused == 0 ? (all == 1 ? "It works on " + yours : "All " + all + " work on " + yours) : out.work + " of " + all + " work on " + yours,
                    "Each was looked up in your game's code."});
            if (out.refused > 0) lines.add(new String[] {"info", out.refused == 1 ? "1 cannot work here" : out.refused + " cannot work here", "Listed with the reason, and off."});
            if (x.checked > 0) lines.add(checked(x.checked, x.cheats));
            if (x.patterns > 0) lines.add(updates(x.patterns));
            if (x.values > 0) lines.add(new String[] {"scale", CheatsPage.count(x.values, "number") + " you can set", "In the game's Mods tab, within the range the file gives."});
            if (converted && x.rawOnly > 0) lines.add(new String[] {"layers", x.rawOnly == 1 ? "1 kept as its own code" : x.rawOnly + " kept as their own code",
                    "Code that does more than change places Peanuts can name runs as it came, on " + yours + "."});
            lines.add(new String[] {"controller", out.on.isEmpty() ? "Turn on the ones you want" : (out.on.size() == 1 ? "1 is on" : out.on.size() + " are on") + ", as the file has them",
                    "Here or in the game's Mods tab. A cheat switches at once in a running game."});
            View card = formatCard(a, converted ? "update" : "check", converted ? "Now a Mistel cheat file" : "Mistel cheat file",
                    converted ? "One .mncf for every version of " + g.name + ": " + out.named : out.named, x.signer);
            dialog(a, "Cheats added", card, null, lines, null, null, "Done", null);
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
        dialog(a, title, null, lead, lines, negative, onNegative, positive, onPositive);
    }

    /** The same with a card above the lead (formatCard); lead null: none */
    private static void dialog(Activity a, String title, View card, String lead, List<String[]> lines, String negative, Runnable onNegative, String positive, Runnable onPositive) {
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        if (card != null) box.addView(card);
        if (lead != null) {
            TextView l = PUi.body(a, lead);
            l.setLineSpacing(0, 1.15f);
            PUi.add(box, l, card != null ? 14 : 0);
        }
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
