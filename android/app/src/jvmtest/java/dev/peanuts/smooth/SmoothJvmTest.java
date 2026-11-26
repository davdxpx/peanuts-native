// PEANUTS: Smooth Experience's items and Developer view's player words on the JVM (peanuts/tests/test_smooth_experience.py); prints "ok <n>" or throws
package dev.peanuts.smooth;

public final class SmoothJvmTest {
    private static int n;

    private static void eq(Object want, Object got) {
        n++;
        if (!String.valueOf(want).equals(String.valueOf(got))) throw new AssertionError("\nwant: " + want + "\n got: " + got);
    }

    private static void yes(boolean b, String what) { n++; if (!b) throw new AssertionError(what); }

    /** A build text and what Developer view off makes of it */
    private static void build(String in, String out) { eq(out, PlayerWords.build(in)); }

    public static void main(String[] a) {
        // ---- Smooth Experience's items ----
        eq("Smooth Experience", SmoothItems.NAME);
        eq(3, SmoothItems.ALL.size());
        String keys = "";
        for (SmoothItems.Item it : SmoothItems.ALL) {
            keys += it.key + " ";
            yes(!it.title.isEmpty() && !it.line.isEmpty(), it.key + " has a title and a line");
            yes(it.line.endsWith("."), it.key + "'s line is a sentence");
            yes(SmoothItems.of(it.key) == it, it.key + " found by its key");
        }
        eq("start_log warm skip ", keys);
        yes(SmoothItems.of("nope") == null, "an unknown key");
        yes(SmoothItems.rowLine(false, 3).startsWith("Off."), "off says so");
        yes(SmoothItems.rowLine(true, 3).startsWith("On. A calm start"), "all picked");
        eq("On. 2 of 3 parts below picked.", SmoothItems.rowLine(true, 2));
        yes(SmoothItems.rowLine(true, 0).contains("nothing below is picked"), "none picked");
        eq("Start screen", SmoothItems.of(SmoothItems.START_LOG).title);   // as Settings spells the rows (LibrarySettings, SettingsSections)
        eq("Preparing graphics notice", SmoothItems.of(SmoothItems.WARM).title);
        eq("Graphics driver notice", SmoothItems.of(SmoothItems.SKIP).title);

        // ---- Building a game: the step, the percent and the time left ----
        // upstream's Prepare card and its notification (Job.statusText, Job.stepText + quietText, PrepareText.progressLine)
        build("Making shaders for this phone: 1,234 of 22,782, 21,548 left, about 35 min left, step 4 of 5, still working (no news for 3 min).",
                "Making shaders for this phone, about 35 min left.");
        build("Making shaders for this phone: 1,234 of 22,782, 21,548 left, about 35 min left, still working (no news for 1 h 05 min)",
                "Making shaders for this phone, about 35 min left");
        build("Making 900 new shaders (21,000 kept from before): 12 of 900, 888 left, under a minute left, step 4 of 5.", "Making new shaders, under a minute left.");
        build("Making 900 new shaders (21,000 kept from before), step 4 of 5.", "Making new shaders.");
        build("Keeping the shaders made before: 12,000 of 42,800, step 4 of 5.", "Keeping the shaders made before.");
        build("Finding the shaders: reading 1,442 files, 203 of 1,442 files, step 3 of 5.", "Finding the shaders.");
        build("Finding the shaders: 1,203 of 1,890 files, 40 % of a large file, step 3 of 5.", "Finding the shaders.");
        build("Finding the shaders: reading the game's program, step 3 of 5.", "Finding the shaders: reading the game's program.");
        build("Converting the game's code: rewriting main (1 of 3), 40 %, step 2 of 5.", "Converting the game's code, 40 %.");
        build("Converting the game's code, which takes a few minutes, step 2 of 5.", "Converting the game's code, which takes a few minutes.");
        build("Stopped at making shaders for this phone: 1,234 of 22,782, 21,548 left. Tap Resume to go on from there.",
                "Stopped at making shaders for this phone. Tap Resume to go on from there.");
        build("Paused: the battery is below 20 %. Plug in the charger to go on. Making shaders for this phone: 1,234 of 22,782, 21,548 left.\nIf the phone does not feel hot, tap Go on anyway.",
                "Paused: the battery is below 20 %. Plug in the charger to go on. Making shaders for this phone.\nIf the phone does not feel hot, tap Go on anyway.");
        build("37 %, 21,548 shaders left, about 35 min left, 70 kept from before", "37 %, about 35 min left");
        build("37 %, 1,234 of 22,852 shaders", "37 %");
        build("37 %, 22,852 shaders to make", "37 %");
        build("37 %, shaders are up to date", "37 %, shaders are up to date");
        build("Step 4 of 5, 37 %", "37 %");
        build("Ready to play.", "Ready to play.");
        build("Shaders are up to date: nothing to make", "Shaders are up to date: nothing to make");
        // the library's build card (Prepare.stepText, RuntimeShaders, Compressed.stepText, BuildService)
        build("Making shaders for this phone: 1,234 of 22,852", "Making shaders for this phone");
        build("Finding the shaders: 120 of 1,890 files", "Finding the shaders");
        build("Converting the game's code (40 %)", "Converting the game's code (40 %)");
        build("Paused: the phone is too hot. It goes on when it has cooled down. Making shaders for this phone: 1 of 2",
                "Paused: the phone is too hot. It goes on when it has cooled down. Making shaders for this phone");
        build("Finishing graphics setup: 1,234 of 5,678", "Finishing graphics setup");
        build("Unpacking Pikmin 4.nsz: 37 %", "Unpacking the game files: 37 %");
        build("Checking Game.xcz: 12 %", "Checking the game files: 12 %");
        build("Checking the saved shaders", "Checking the saved shaders");
        build("Loading keys", "Loading keys");
        build("Pikmin 4's dual-screen companion is installed, version 1.2.3", "Pikmin 4's dual-screen companion is installed");
        build("Pikmin 4 is ready to play. It took 3 min 12 s, and 21,000 shaders and 2 parts of the game's code were kept from before. 12 of its 22,000 shaders could not be made, so some effects may not show. Its build log lists them.",
                "Pikmin 4 is ready to play. Some of its shaders could not be made, so some effects may not show.");
        build("The Legend of Zelda: Tears of the Kingdom is ready to play. It took 1 min 16 s, and 9,564 shaders were kept from before.",
                "The Legend of Zelda: Tears of the Kingdom is ready to play.");
        build("Octopath Traveler 0 is ready to play. 1,234 of its 22,000 shaders are not in this game version, so some things may not draw. All of them are in 1.2.0.",
                "Octopath Traveler 0 is ready to play. Some of its shaders are not in this game version, so some things may not draw. All of them are in 1.2.0.");
        build("Making every shader again, to compare with the ones kept from before. Starting a game stops this, and it goes on another time.",
                "Checking the game's shaders in the background. Starting a game stops this.");
        eq(null, PlayerWords.build(null));
        eq("", PlayerWords.build(""));

        // ---- The in-game menu ----
        eq("Applies at the next start.", PlayerWords.panel("Applies at the next start (this run takes no live changes: DM_CTL is off)."));
        eq("This version of the app has no frame rate cap.", PlayerWords.panel("This build has no frame rate cap."));
        eq("Not in this version of the app.", PlayerWords.panel("Not in this build (built with -PnxLsfg=false)."));
        eq("Mode: performance.", PlayerWords.panel("Mode: performance (the only shader set Eden's LSFG uses)"));
        eq("Now 60 fps, capped at 60.", PlayerWords.panel("Now 60 fps, capped at 60."));   // a line it has no words for stays as it is
        eq(null, PlayerWords.panel(null));

        // frame generation's state (Native.frameGenStatus: "<state> <reason>", vka_framegen.c)
        eq("Now: adding frames.", PlayerWords.frameGen("1 generating"));
        eq("Now: waiting for a steady frame rate.", PlayerWords.frameGen("2 on, waiting for a steady frame rate"));
        eq("Now: paused. At 60 Hz the screen has no room for extra frames.", PlayerWords.frameGen("2 on, the display runs at 60 Hz: no refresh left for a generated frame"));
        eq("Now: off.", PlayerWords.frameGen("0 off"));
        eq("Now: not running yet.", PlayerWords.frameGen("0 "));
        eq("Now: needs your Lossless.dll. Pick it in Settings › Graphics › Frame generation.",
                PlayerWords.frameGen("-1 no Lossless.dll (the launcher looks in Documents/Emulation/bios, Switch2 and the app's files; or pick it in the launcher)"));
        eq("Now: this phone's GPU cannot run it.", PlayerWords.frameGen("-1 the GPU lacks shaderFloat16"));
        eq("Now: your Lossless.dll could not be read. Pick it again in Settings.", PlayerWords.frameGen("-1 /storage/emulated/0/Lossless.dll: bad header"));
        eq("Now: could not start. Share log has the details.", PlayerWords.frameGen("-1 the frame generation chain could not be made (see the [RED] lines)"));
        eq("Now: not in this version of the app.", PlayerWords.frameGen("-1 not in this build (built without NX_LSFG)"));
        eq("Now: not in this version of the app.", PlayerWords.frameGen("-2 not in this host"));
        eq(null, PlayerWords.frameGen("3 something new"));   // a state it does not know: the menu keeps its own line
        eq(null, PlayerWords.frameGen(null));

        // the shader preparation's words
        yes(!PlayerWords.PREPARE_ONCE.contains("driver"), "the preparation's line names no driver");

        System.out.println("ok " + n);
    }
}
