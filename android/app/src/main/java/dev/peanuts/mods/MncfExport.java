package dev.peanuts.mods;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import io.github.davdxpx.mistel.mncf.Mncf;
import io.github.davdxpx.mistel.mncf.MncfException;
import io.github.davdxpx.mistel.mncf.MncfFile;
import io.github.davdxpx.mistel.mncf.Writer;

/**
 * Export: the player's cheat sources of a game as Mistel Native Cheat Format files, so another device (or another app that reads .mncf) has the same cheats,
 * switched as here. One source becomes one .mncf, several an .mncp pack with one .mncf a source (Mistel's spec, section 13; its manifest names each file's
 * SHA-256). In each file the cheats switched on here are default = true and the others false, and a number set in the in-game tab is its value's default, so the
 * import of the export on another device starts as this one is. A file nothing of that changes is written byte for byte as it was added (a signature stays
 * good); a changed one is the library's writing of it (Writer: without signature lines). No Android classes: CheatLoaderJvmTest runs it.
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
            CheatValues.Kept k = kept.get(r.key.toLowerCase(Locale.ROOT));
            if (k != null && r.cheat.number != null && r.cheat.mncf.value != null && CheatValues.apply(r.cheat, k) != null && c.get("values") instanceof Map) {
                Object v = ((Map<String, Object>) c.get("values")).get(r.cheat.mncf.value.id());
                if (v instanceof Map) { ((Map<String, Object>) v).put("default", Math.max(r.cheat.number.min(), Math.min(k.number, r.cheat.number.max()))); changed = true; }
            }
        }
        return changed ? Writer.dumps(doc).getBytes(StandardCharsets.UTF_8) : own;
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
