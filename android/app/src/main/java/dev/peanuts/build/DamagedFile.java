package dev.peanuts.build;

/**
 * A game file that is damaged or incomplete, in the player's words (GitHub issue #561). Two places find one:
 * <ul>
 * <li>the import (dm/peanuts/import: nximport.c, nx_verify.c): the NCA's bytes do not match the checksums its own hash layers hold, or the file ends early.
 * Its message is "&lt;file&gt;: the base game|the update v&lt;n&gt;|the DLC &lt;id&gt; is damaged or incomplete: &lt;why&gt;", or "&lt;file&gt; is damaged or
 * incomplete: &lt;why&gt;" for a file cut short. {@link #failure} makes the card's text of it; the build log keeps the importer's own words;</li>
 * <li>the shader split (dm/gfx/tools/shader_split.py damaged()): a game file of a .nup imported before that check, whose index points outside it. Its job
 * fails with {@link #CAUSE} (dev.nxnative.android.PrepareText.toolStopped), which {@link Prepare} turns into {@link BuildListener#FILE_DAMAGED}.</li>
 * </ul>
 * Either way the card offers the game's sheet and no Retry: the same file fails the same way again. Compressed files have words of their own
 * ({@link Compressed#failure}, asked first). No Android class here: the JVM tests drive it (peanuts/tests/test_damaged_game_files.py).
 */
public final class DamagedFile {
    private DamagedFile() {}

    /** job.json's "cause" of a tool that found a damaged game file (PrepareText.CAUSE_DAMAGED) */
    public static final String CAUSE = "game_file_damaged";
    /** What the importer's messages about a damaged or incomplete file say after the file (and its role) */
    static final String MARK = " is damaged or incomplete";

    /**
     * The card's text for an importer message about a damaged or incomplete file, or null when the message is about something else. name: the game's name;
     * unchanged: nothing was replaced or deleted (a first import, or Import again with the old file kept)
     */
    public static String failure(String message, String name, boolean unchanged) {
        if (message == null) return null;
        String first = message.split("\n", 2)[0];
        int at = first.indexOf(MARK);
        if (at <= 0) return null;
        String head = first.substring(0, at).trim(), file = head, kind = "game file";
        int c = head.indexOf(": ");
        if (c > 0) {
            file = head.substring(0, c).trim();
            String role = head.substring(c + 2);
            if (role.startsWith("the update")) kind = "update file";
            else if (role.startsWith("the DLC")) kind = "DLC file";
        }
        if (file.isEmpty()) return null;
        String game = name == null || name.isEmpty() ? "the game" : name;
        return "The " + kind + " " + file + " is damaged or incomplete, so " + game + " was not imported." + (unchanged ? " Nothing was changed." : "")
                + " Copy it again, or dump it again from your console.";
    }
}
