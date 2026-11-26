package dev.peanuts.smooth;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Developer view's player words (DevView, Settings › Developer › Developer view off): the player's sentence for a text that carries build and tool details.
 * Each function hands back the text as it came when it does not know its shape, so an upstream change of a text shows the old words, never a broken
 * sentence. Plain Java, no Android (SmoothJvmTest).
 */
public final class PlayerWords {
    private PlayerWords() {}

    // ---- Building a game (Developer view off): the step in words, the percent and the time left; no counts, file names, step numbers or timings ----

    private static final String N = "\\d(?:[\\d,]*\\d)?";                     // 7, 1,234, 22,782
    private static final String C = "(?:[^.,:]|,(?=\\d)|\\.(?=\\d))";       // a character of one clause (a number's comma or point stays in it)
    private static final Object[][] BUILD_RULES = {
            {", step \\d+ of \\d+", ""},                                     // Job.statusText's ", step 4 of 5"
            {"^Step \\d+ of \\d+, ", ""},                                    // the notification's sub text "Step 4 of 5, 37 %"
            {", still working \\(no news for [^)]*\\)", ""},                 // PrepareText.quietText
            {" It took [^.]*? were kept from before\\.", ""},                // BuildReuse's sentence of the ready message
            {" Its build log lists them\\.", ""},
            {"\\b" + N + " of its " + N + " shaders", "Some of its shaders"},   // BuildService.shadersLeftOut, PrepareText.missingShadersText, CheckDue.text
            {"^(Unpacking|Checking) [^:]+\\.(?i:nsz|xcz|nsp|xci|nup|zip)\\b", "$1 the game files"},   // Compressed.stepText
            {"Making " + N + " new shaders \\(" + N + " kept from before\\)", "Making new shaders"},  // Job.shadersStepText of an update
            {", version [0-9][\\w.\\-]*", ""},                               // the companion's version
            {": " + N + " of " + N + "( files?)?(, " + N + " left)?", ""},   // "Making shaders for this phone: 1,234 of 22,782, 21,548 left"
            {"(?<!Paused): [a-z]" + C + "*\\d" + C + "*", ""},               // a tool's note with a count: ": reading 1,442 files", ": rewriting main (1 of 3)"
            {", " + N + " of " + N + "( files?| shaders)?", ""},             // a count of its own: ", 203 of 1,442 files"
            {", " + N + " (?:shaders left|shaders to make|kept from before|left)", ""},   // PrepareText.progressLine's parts
            {"[,:] \\d+ % of a large file", ""}};
    private static final Pattern[] BUILD_PATTERNS = new Pattern[BUILD_RULES.length];
    static {
        for (int i = 0; i < BUILD_RULES.length; i++) BUILD_PATTERNS[i] = Pattern.compile((String) BUILD_RULES[i][0]);
    }

    /** Whole texts said again in a sentence of their own */
    private static final Map<String, String> WHOLE = new HashMap<>();
    static {
        WHOLE.put("Making every shader again, to compare with the ones kept from before. Starting a game stops this, and it goes on another time.",   // ReuseCheck.NOTE
                "Checking the game's shaders in the background. Starting a game stops this.");
    }

    /** A build's text for the card, the bottom screen's build page and the notifications, line by line */
    public static String build(String s) {
        if (s == null || s.isEmpty()) return s;
        String whole = WHOLE.get(s);
        if (whole != null) return whole;
        String[] lines = s.split("\n", -1);
        StringBuilder out = new StringBuilder();
        for (int k = 0; k < lines.length; k++) {
            String l = lines[k];
            for (int i = 0; i < BUILD_PATTERNS.length; i++) l = BUILD_PATTERNS[i].matcher(l).replaceAll((String) BUILD_RULES[i][1]);
            if (k > 0) out.append('\n');
            out.append(l);
        }
        return out.toString();
    }

    // ---- The in-game menu's words (Developer view off) ----

    private static final Map<String, String> PANEL = new HashMap<>();
    static {
        PANEL.put("Applies at the next start (this run takes no live changes: DM_CTL is off).", "Applies at the next start.");   // SidePanel
        PANEL.put("This build has no frame rate cap.", "This version of the app has no frame rate cap.");                         // SidePanel.refresh
        PANEL.put("Not in this build (built with -PnxLsfg=false).", "Not in this version of the app.");                           // Lsfg.rows
        PANEL.put("Mode: performance (the only shader set Eden's LSFG uses)", "Mode: performance.");                              // Lsfg.rows
    }

    /** A line of the in-game menu in player words; the line itself when there is none for it */
    public static String panel(String s) {
        String p = s == null ? null : PANEL.get(s);
        return p != null ? p : s;
    }

    private static final Pattern HZ = Pattern.compile("^on, the display runs at (\\d+) Hz");

    /**
     * Frame generation's "Now:" line from the host's status (Native.frameGenStatus: "<state> <reason>", vka_framegen.c), in player words. null when the reason
     * is not one of the host's known ones (the caller keeps its own line)
     */
    public static String frameGen(String status) {
        if (status == null) return null;
        int sp = status.indexOf(' ');
        String code = sp > 0 ? status.substring(0, sp) : status, why = sp > 0 ? status.substring(sp + 1).trim() : "";
        if (why.equals("generating")) return "Now: adding frames.";
        if (why.equals("on, waiting for a steady frame rate")) return "Now: waiting for a steady frame rate.";
        Matcher hz = HZ.matcher(why);
        if (hz.find()) return "Now: paused. At " + hz.group(1) + " Hz the screen has no room for extra frames.";
        if (why.equals("off")) return "Now: off.";
        if (why.startsWith("no Lossless.dll")) return "Now: needs your Lossless.dll. Pick it in Settings › Graphics › Frame generation.";
        if (why.startsWith("the GPU lacks")) return "Now: this phone's GPU cannot run it.";
        if (why.startsWith("the frame generation chain could not be made")) return "Now: could not start. Share log has the details.";
        if (code.equals("-2") || why.startsWith("not in this build") || why.startsWith("not in this host")) return "Now: not in this version of the app.";
        if (code.equals("-1") && why.contains("Lossless.dll")) return "Now: your Lossless.dll could not be read. Pick it again in Settings.";
        if (code.equals("0") && why.isEmpty()) return "Now: not running yet.";
        return null;
    }

    // ---- Shader preparation (Developer view off) ----

    /** PrepareCover's line under the driver's (which goes): once, without the driver */
    public static final String PREPARE_ONCE = "This happens once. After that the game starts right away.";
    public static final String PREPARE = "Preparing shaders";
}
