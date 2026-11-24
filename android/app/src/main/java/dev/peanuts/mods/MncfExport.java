package dev.peanuts.mods;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import io.github.davdxpx.mistel.mncf.Mncf;
import io.github.davdxpx.mistel.mncf.MncfException;
import io.github.davdxpx.mistel.mncf.MncfFile;
import io.github.davdxpx.mistel.mncf.SignatureResult;
import io.github.davdxpx.mistel.mncf.Writer;

/**
 * Export: the player's cheat sources of a game as Mistel Native Cheat Format files, so another device (or another app that reads .mncf) has the same cheats,
 * switched as here. One source becomes one .mncf, several an .mncp pack with one .mncf a source (Mistel's spec, section 13; its manifest names each file's
 * SHA-256). In each file the cheats switched on here are default = true and the others false, and a number set in the in-game tab is its value's default, so the
 * import of the export on another device starts as this one is. A file nothing of that changes is written byte for byte as it was added (a signature stays
 * good); a changed one is the library's writing of it (Writer: without signature lines). For other emulators, {@link #zip} writes the same sources as
 * Atmosphère cheat files in Atmosphère's folders, one a build, and names what Atmosphère code cannot hold. No Android classes: CheatLoaderJvmTest runs it.
 */
public final class MncfExport {
    private MncfExport() {}

    /** What an export is: its file name, the bytes, and how many sources and cheats went in */
    public static final class Out {
        public final String fileName;
        public final byte[] bytes;
        public final int sources, cheats;
        public final boolean pack;
        Out(String fileName, byte[] bytes, int sources, int cheats, boolean pack) { this.fileName = fileName; this.bytes = bytes; this.sources = sources; this.cheats = cheats; this.pack = pack; }
    }

    /** The name an export is offered under: "&lt;game&gt; cheats.mncf" or ".mncp", without characters a file name cannot have */
    public static String fileName(String gameName, boolean pack) {
        String n = (gameName == null || gameName.trim().isEmpty() ? "Cheats" : gameName.trim() + " cheats").replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", " ").replaceAll("\\s+", " ").trim();
        return n + (pack ? ".mncp" : ".mncf");
    }

    /** Whether the game has sources to export (an .mncf each) */
    public static boolean any(File folder) {
        for (CheatSources.Source s : CheatSources.list(folder)) if (s.file().isFile()) return true;
        return false;
    }

    /**
     * The export of the game's sources: merged gives each cheat's key (the toggles' and the kept numbers' key), on the keys switched on, kept the numbers set
     * (CheatValues.read). Null when the game has no source. IOException when a source's file cannot be read
     */
    public static Out make(File folder, String gameName, CheatSources.Merged merged, Set<String> on, Map<String, CheatValues.Kept> kept) throws IOException {
        List<CheatSources.Source> sources = new ArrayList<>();
        for (CheatSources.Source s : CheatSources.list(folder)) if (s.file().isFile()) sources.add(s);
        if (sources.isEmpty()) return null;
        List<String> names = new ArrayList<>();
        List<byte[]> files = new ArrayList<>();
        int cheats = 0;
        for (CheatSources.Source s : sources) {
            byte[] b = Files.readAllBytes(s.file().toPath());
            MncfFile f;
            try {
                f = Mncf.load(b);
            } catch (MncfException e) {
                files.add(b);   // a file this app cannot use goes as it is: the other side may read it
                names.add(s.id + ".mncf");
                continue;
            }
            for (io.github.davdxpx.mistel.mncf.Cheat c : f.cheats()) if (!c.isMaster()) cheats++;
            files.add(withState(b, f, s.id, merged, on, kept));
            names.add(s.id + ".mncf");
        }
        if (sources.size() == 1) return new Out(fileName(gameName, false), files.get(0), 1, cheats, false);
        return new Out(fileName(gameName, true), pack(gameName, names, files), sources.size(), cheats, true);
    }

    /** A source's file with the player's switches as defaults and the set numbers as its values' defaults; its own bytes when that changes nothing */
    @SuppressWarnings("unchecked")
    static byte[] withState(byte[] own, MncfFile f, String sourceId, CheatSources.Merged merged, Set<String> on, Map<String, CheatValues.Kept> kept) {
        Map<String, CheatSources.Row> byId = new HashMap<>();
        if (merged != null) for (CheatSources.Row r : merged.rows) if (r.block.id.equals(sourceId) && r.cheat.mncf != null) byId.put(r.cheat.mncf.id, r);
        Map<String, Long> numbers = numbers(sourceId, merged, kept);
        Map<String, Object> doc = MncfBuild.copy(f.doc());
        boolean changed = false;
        for (Object o : (List<Object>) doc.get("cheat")) {
            Map<String, Object> c = (Map<String, Object>) o;
            CheatSources.Row r = byId.get(String.valueOf(c.get("id")));
            if (r == null || r.cheat.master) continue;   // refused here, a master, or not in this build's merge: as the file has it
            boolean want = on.contains(r.key), had = Boolean.TRUE.equals(c.get("default"));
            if (want != had) {
                if (want) c.put("default", true); else c.remove("default");
                changed = true;
            }
            Long n = r.cheat.mncf.valueKey == null ? null : numbers.get(r.cheat.mncf.valueKey);
            if (n != null && c.get("values") instanceof Map) {
                Object v = ((Map<String, Object>) c.get("values")).get(r.cheat.mncf.value.id());
                if (v instanceof Map) { ((Map<String, Object>) v).put("default", n); changed = true; }
            }
        }
        return changed ? Writer.dumps(doc).getBytes(StandardCharsets.UTF_8) : own;
    }

    /** The numbers set in the in-game tab of a source's cheats (CheatValues.read), inside their ranges: "&lt;cheat id&gt;.&lt;value id&gt;" → the number */
    static Map<String, Long> numbers(String sourceId, CheatSources.Merged merged, Map<String, CheatValues.Kept> kept) {
        Map<String, Long> out = new HashMap<>();
        if (merged == null || kept == null) return out;
        for (CheatSources.Row r : merged.rows) {
            if (!r.block.id.equals(sourceId) || r.cheat.mncf == null || r.cheat.number == null || r.cheat.mncf.valueKey == null) continue;
            CheatValues.Kept k = kept.get(r.key.toLowerCase(Locale.ROOT));
            if (k != null && CheatValues.apply(r.cheat, k) != null) out.put(r.cheat.mncf.valueKey, Math.max(r.cheat.number.min(), Math.min(k.number, r.cheat.number.max())));
        }
        return out;
    }

    // ---- A ZIP for other emulators ----

    /** At most this many cheats in one Atmosphère file (Atmosphère's dmnt keeps 128 entries, the first for the master code) */
    static final int ATMOSPHERE_MAX = 127;

    /** A ZIP in Atmosphère's layout, and what went in and what could not */
    public static final class Zip {
        public final String fileName;
        public final byte[] bytes;
        public final Map<String, Integer> builds;      // build ID (16 hex, upper case) → the cheats its file has; the running build first
        public final Map<String, String> versions;     // build ID → its version name, where a file names one
        public final int cheats;                       // the cheats in the ZIP, in one build's file at least
        public final List<String[]> left;              // the cheats in no file: {name, why in the player's words}, in the sources' order
        Zip(String fileName, byte[] bytes, Map<String, Integer> builds, Map<String, String> versions, int cheats, List<String[]> left) {
            this.fileName = fileName; this.bytes = bytes; this.builds = builds; this.versions = versions; this.cheats = cheats; this.left = left;
        }
    }

    /** The name a ZIP export is offered under: "&lt;game&gt; cheats.zip" */
    public static String zipName(String gameName) { return fileName(gameName, false).replaceFirst("\\.mncf$", ".zip"); }

    /** Where Atmosphère reads a build's cheats in the ZIP (Eden, Yuzu and Ryujinx read the same files) */
    public static String zipPath(String titleId, String build16) {
        return "atmosphere/contents/" + titleId.toUpperCase(Locale.ROOT) + "/cheats/" + build16.toUpperCase(Locale.ROOT) + ".txt";
    }

    /** One cheat lowered for one build; key: "&lt;source's place in the list&gt;/&lt;cheat id&gt;" */
    private static final class Lowered {
        final int source;
        final String key, name, group;
        final boolean master;
        final List<String> lines;
        Lowered(int source, MncfBuild.Cheat c, List<String> lines) {
            this.source = source; key = source + "/" + c.id; name = c.name; group = c.group; master = c.master; this.lines = lines;
        }
    }

    /**
     * Whether a cheat Peanuts refuses here may still go into the ZIP: one refused for what Peanuts' own engine lacks (runtime:unsupported:, runtime:rewritten,
     * a capability) can run in another emulator where Atmosphère code can say it; one whose place or code does not fit the game (pattern-not-found,
     * expect-mismatch, outside, ...) or whose relations stop it never goes
     */
    static boolean fitsElsewhere(String reason) {
        return reason == null || reason.startsWith("runtime:") && !reason.equals("runtime:no-main") || reason.startsWith("capability-missing:");
    }

    /**
     * The game's sources as a ZIP for other emulators: one Atmosphère cheat file a build, at atmosphere/contents/&lt;title ID&gt;/cheats/&lt;build ID&gt;.txt, with
     * each cheat lowered by Mistel's converter (Converter.toAtmosphere) for every build its file names, and for the build that runs from the file made ready for
     * it (MncfBuild: its places found in the game's own main). Each cheat is the code Peanuts runs where it runs it, else its own code for the build, else code
     * made from its operations, with the number set in the in-game tab where it has one. Left out and named: what Atmosphère code cannot say (a monitor, toggle
     * buttons, a run once or for a time, several conditions), what finds its place by a pattern in a build whose main Peanuts does not have, and what does not
     * fit the game that runs. Masters become each file's one master code; groups are headings; every name is unique. Null when the game has no source
     */
    public static Zip zip(File folder, String gameName, MncfBuild.Game g, CheatSources.Merged merged, Map<String, CheatValues.Kept> kept) throws IOException {
        List<CheatSources.Source> sources = new ArrayList<>();
        for (CheatSources.Source s : CheatSources.list(folder)) if (s.file().isFile()) sources.add(s);
        if (sources.isEmpty()) return null;
        Map<String, List<Lowered>> byBuild = new LinkedHashMap<>();
        if (g.build != null) byBuild.put(g.build, new ArrayList<>());
        Map<String, String> versions = new LinkedHashMap<>();
        List<String[]> left = new ArrayList<>();
        Map<String, String> names = new LinkedHashMap<>();   // every cheat (not a master) by its key, in the sources' order
        Map<String, String> why = new HashMap<>();           // a cheat's key → why it is in no build yet
        for (int si = 0; si < sources.size(); si++) {
            CheatSources.Source s = sources.get(si);
            byte[] bytes = Files.readAllBytes(s.file().toPath());
            MncfFile f;
            try {
                f = Mncf.load(bytes, MncfBuild.TRUST);
            } catch (MncfException e) {
                left.add(new String[] {s.name, "Its file cannot be read."});
                continue;
            }
            String sig = f.signature().result();
            boolean bad = SignatureResult.INVALID.equals(sig) || SignatureResult.REVOKED.equals(sig);
            List<MncfBuild.Cheat> all = new ArrayList<>();
            for (io.github.davdxpx.mistel.mncf.Cheat c : f.cheats()) {
                all.add(new MncfBuild.Cheat(c.index(), c, null, Collections.<String>emptyList(), MncfBuild.number(c)));
                if (!c.isMaster()) names.put(si + "/" + c.id(), c.name());
                if (bad) why.put(si + "/" + c.id(), MncfWords.reason("signature", null));
            }
            if (bad) continue;
            Map<String, Object> values = new HashMap<>(numbers(s.id, merged, kept));
            // the build that runs: from the file made ready for it
            MncfBuild.Ready r = g.build == null ? null : MncfBuild.kept(s.file(), g);
            if (r == null && g.build != null) r = MncfBuild.of(s.file(), g);
            if (r != null && r.error == null && r.key != null) {
                Map<String, Boolean> want = new LinkedHashMap<>();
                for (MncfBuild.Cheat c : r.cheats) {
                    if (!fitsElsewhere(c.reason)) { why.put(si + "/" + c.id, MncfWords.reason(c.reason, c)); continue; }
                    want.put(c.id, c.valueKey != null && values.containsKey(c.valueKey) ? Boolean.FALSE : c.reason == null ? r.usesRaw(c) : Boolean.TRUE);
                }
                add(byBuild.get(g.build), si, r.cheats, lowered(r.pinned, r.key, values, want, si, why));
                String v = MncfBuild.version(f, g.build);
                if (v != null) versions.put(g.build, v);
            }
            // every other build the file names: its own code for it, or code made from its operations where the places are fixed for it
            String own = new String(bytes, StandardCharsets.UTF_8);
            for (io.github.davdxpx.mistel.mncf.Build b : f.builds().values()) {
                String main = b.modules().get("main");
                if (main == null || main.length() < 16) continue;
                String bid = main.substring(0, 16).toUpperCase(Locale.ROOT);
                if (bid.equals(g.build)) continue;
                Map<String, Boolean> want = new LinkedHashMap<>();
                for (MncfBuild.Cheat c : all) want.put(c.id, c.valueKey != null && values.containsKey(c.valueKey) ? Boolean.FALSE : Boolean.TRUE);
                add(byBuild.computeIfAbsent(bid, k -> new ArrayList<>()), si, all, lowered(own, b.key(), values, want, si, why));
                String v = MncfBuild.version(f, bid);
                if (v != null) versions.putIfAbsent(bid, v);
            }
        }
        Map<String, Integer> counts = new LinkedHashMap<>();
        Set<String> in = new HashSet<>();
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(o)) {
            for (Map.Entry<String, List<Lowered>> e : byBuild.entrySet()) {
                Set<String> here = new HashSet<>();
                String text = atmosphere(e.getValue(), sources, here);
                if (here.isEmpty()) continue;
                counts.put(e.getKey(), here.size());
                in.addAll(here);
                z.putNextEntry(new ZipEntry(zipPath(g.titleId, e.getKey())));
                z.write(text.getBytes(StandardCharsets.UTF_8));
                z.closeEntry();
            }
        }
        Set<String> lowered = new HashSet<>();
        for (List<Lowered> l : byBuild.values()) for (Lowered x : l) lowered.add(x.key);
        for (Map.Entry<String, String> e : names.entrySet()) {
            if (in.contains(e.getKey())) continue;
            left.add(new String[] {e.getValue(), lowered.contains(e.getKey()) ? "An Atmosphère file holds at most " + ATMOSPHERE_MAX + " cheats."
                    : why.getOrDefault(e.getKey(), "An Atmosphère file cannot say what it does.")});
        }
        versions.keySet().retainAll(counts.keySet());
        return new Zip(zipName(gameName), o.toByteArray(), counts, versions, in.size(), left);
    }

    /** MncfBuild.lower, why getting the converter's reason (in the player's words) for each cheat it left out; a failure of the whole file leaves each out */
    private static Map<Integer, List<String>> lowered(String text, String key, Map<String, Object> values, Map<String, Boolean> want, int source, Map<String, String> why) {
        Map<String, String> skipped = new HashMap<>();
        try {
            Map<Integer, List<String>> out = MncfBuild.lower(text, key, values, want, skipped);
            for (Map.Entry<String, String> e : skipped.entrySet()) why.putIfAbsent(source + "/" + e.getKey(), zipWords(e.getValue()));
            return out;
        } catch (MncfException | RuntimeException e) {   // the library's own writing of a file it read: not expected
            for (String id : want.keySet()) why.putIfAbsent(source + "/" + id, "An Atmosphère file cannot say what it does.");
            return new HashMap<>();
        }
    }

    /** Why the converter left a cheat out of an Atmosphère file (Converter.toAtmosphere's words), in the player's words */
    static String zipWords(String converterWhy) {
        String w = converterWhy == null ? "" : converterWhy.toLowerCase(Locale.ROOT);
        if (w.contains("pattern") || w.contains("without an offset")) return "Its place is found by the code around it, which an Atmosphère file cannot do.";
        if (w.contains("value")) return "Its number does not fit the place it is written to.";
        switch (MncfBuild.runtimeReason(converterWhy)) {
            case "runtime:unsupported:run.once": return "It runs once when you turn it on, which an Atmosphère file cannot say.";
            case "runtime:unsupported:run.timed": return "It runs for a set time, which an Atmosphère file cannot say.";
            case "runtime:unsupported:toggle": return "It is switched with a button combination, which an Atmosphère file cannot say.";
            case "capability-missing:display": return "It shows values from the game, which an Atmosphère file cannot do.";
            case "runtime:unsupported:when.many": return "It waits for several things in the game at once, which an Atmosphère file cannot say.";
            case "runtime:unsupported:when": return "It waits for the game in a way an Atmosphère file cannot say.";
            case "runtime:unsupported:run.action": return "It runs when you press its buttons in a way an Atmosphère file cannot say.";
            case "runtime:unsupported:override": return "It changes a function in a way an Atmosphère file cannot say.";
            case "runtime:module": return "It changes a part of the game other than its main code, which an Atmosphère file cannot name.";
            default: return "An Atmosphère file cannot say what it does.";
        }
    }

    private static void add(List<Lowered> to, int source, List<MncfBuild.Cheat> cheats, Map<Integer, List<String>> lines) {
        for (MncfBuild.Cheat c : cheats) {
            List<String> code = lines.get(c.index);
            if (code != null && !code.isEmpty()) to.add(new Lowered(source, c, code));
        }
    }

    /**
     * One build's Atmosphère file: the masters as its one master code (Atmosphère takes one), then each source's cheats in its order, its groups between
     * EdiZon's section markers (as the merged file has them; Mistel's converter reads them back as groups). A name an earlier source has already gets the
     * source's name after it, as in the merged file; within one source the names stay as its file has them. in gets the key of each cheat it has. Past ATMOSPHERE_MAX entries the markers go first, then the last cheats
     */
    static String atmosphere(List<Lowered> l, List<CheatSources.Source> sources, Set<String> in) {
        List<Lowered> masters = new ArrayList<>(), cheats = new ArrayList<>();
        for (Lowered x : l) (x.master ? masters : cheats).add(x);
        Set<String> groups = new HashSet<>();
        for (Lowered x : cheats) if (!x.group.isEmpty()) groups.add(x.source + "\n" + x.group);
        boolean sections = cheats.size() + 2 * groups.size() <= ATMOSPHERE_MAX;
        StringBuilder sb = new StringBuilder();
        Map<String, Integer> taken = new HashMap<>();   // a name in lower case → the source that has it
        if (!masters.isEmpty()) {
            String name = masters.size() == 1 ? CheatSources.safe(masters.get(0).name) : "Master codes";
            if (name.isEmpty()) name = "Master code";
            sb.append('{').append(name).append("}\n");
            taken.put(name.toLowerCase(Locale.ROOT), -1);
            for (Lowered x : masters) for (String line : x.lines) sb.append(line).append('\n');
        }
        int entries = 0, lastSource = -1;
        String open = null;
        for (Lowered x : cheats) {
            if (entries >= ATMOSPHERE_MAX) break;
            String sec = sections && !x.group.isEmpty() ? CheatSources.section(null, x.group) : null;
            if (x.source != lastSource || !java.util.Objects.equals(sec, open)) {
                if (open != null) { sb.append("\n[--SectionEnd:").append(open).append("--]\n"); entries++; }
                if (sec != null) { sb.append(sb.length() > 0 ? "\n" : "").append("[--SectionStart:").append(sec).append("--]\n"); entries++; }
                open = sec;
                lastSource = x.source;
            }
            String safe = CheatSources.safe(x.name);
            sb.append(sb.length() > 0 ? "\n" : "").append('[').append(name(safe.isEmpty() ? "Cheat" : safe, x.source, sources.get(x.source).name, taken)).append("]\n");
            for (String line : x.lines) sb.append(line).append('\n');
            entries++;
            in.add(x.key);
        }
        if (open != null) sb.append("\n[--SectionEnd:").append(open).append("--]\n");
        return sb.toString();
    }

    /** A cheat's name in the file: its own, or with its source's name when another source has it (taken: lower case → source) */
    private static String name(String base, int source, String sourceName, Map<String, Integer> taken) {
        Integer had = taken.get(base.toLowerCase(Locale.ROOT));
        if (had == null || had == source) { taken.put(base.toLowerCase(Locale.ROOT), source); return base; }
        String n = CheatSources.fit(base, " (" + CheatSources.safe(sourceName) + ")");
        for (int k = 2; taken.containsKey(n.toLowerCase(Locale.ROOT)) && taken.get(n.toLowerCase(Locale.ROOT)) != source; k++) n = CheatSources.fit(base, " (" + CheatSources.safe(sourceName) + " " + k + ")");
        taken.put(n.toLowerCase(Locale.ROOT), source);
        return n;
    }

    /** An .mncp: pack.toml (format, name, each file with its SHA-256) and the files, stored in a ZIP */
    static byte[] pack(String gameName, List<String> names, List<byte[]> files) throws IOException {
        StringBuilder m = new StringBuilder("format = \"mncf-pack/1\"\nname = ").append(toml(gameName == null || gameName.trim().isEmpty() ? "Cheats" : gameName.trim() + " cheats")).append('\n');
        for (int i = 0; i < names.size(); i++) m.append("\n[[file]]\npath = ").append(toml(names.get(i))).append("\nsha256 = \"").append(MncfBuild.sha256(files.get(i))).append("\"\n");
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(o)) {
            z.putNextEntry(new ZipEntry("pack.toml"));
            z.write(m.toString().getBytes(StandardCharsets.UTF_8));
            z.closeEntry();
            for (int i = 0; i < names.size(); i++) {
                z.putNextEntry(new ZipEntry(names.get(i)));
                z.write(files.get(i));
                z.closeEntry();
            }
        }
        return o.toByteArray();
    }

    /** A TOML basic string */
    static String toml(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            if (c == '"' || c == '\\') sb.append('\\').append(c);
            else if (c < 0x20 || c == 0x7F) sb.append(String.format(Locale.ROOT, "\\u%04X", (int) c));
            else sb.append(c);
        }
        return sb.append('"').toString();
    }
}
