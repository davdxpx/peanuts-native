package dev.peanuts.build;

/**
 * The build's watchdog rules (issue #348: a game that fails, crashes or hangs never holds the queue's next game). No Android class here: BuildWatchJvmTest
 * drives it. The service and Prepare ask it; what it decides:
 * <ul>
 * <li><b>a hang</b>: the packer runs the job (its process bound and its worker alive) and job.json has not changed for {@link #HANG_MS}. Every step's tool
 *     says how far it got at least every few seconds (progress and stage lines, PackService's Tail), and a heat or battery pause is a state of its own, so
 *     half an hour without a change is a tool that hangs. The same for the import (its progress callbacks) and for another game's job the build waits for;</li>
 * <li><b>a packer that ended</b>: its process went (a native crash, Android ending it for memory) and did not come back within {@link #PACKER_BACK_MS}.
 *     Android starts a crashed service again within seconds, but not a second time when it crashed twice within a minute: the build would wait for a job
 *     nothing runs;</li>
 * <li><b>a run the app's process did not live through</b>: what ended it, from Android's record (ApplicationExitInfo), for the build log and the card. A native
 *     crash while the game files were imported is the importer's, on those files: that game stops at once, so the queue goes on with the next game after the
 *     first crash (two within a minute and Android starts the app's service no more until the app is opened).</li>
 * </ul>
 */
final class BuildWatch {
    /** No change of a running job (or of an import) for this long: it hangs */
    static final long HANG_MS = 30 * 60 * 1000L;
    /** The packer's process ended and is not back for this long: it is not coming back for this run */
    static final long PACKER_BACK_MS = 5 * 60 * 1000L;
    /** ApplicationExitInfo.REASON_* (the values Android documents), mirrored so this class needs no Android */
    static final int SIGNALED = 2, LOW_MEMORY = 3, CRASH = 4, CRASH_NATIVE = 5, ANR = 6;
    /** The test file of a build with the fake engine (-PnxPackEngine=fake): the hang time in seconds, so a test on a phone need not wait half an hour */
    static final String TEST_FILE = "test-hang-seconds";

    private BuildWatch() {}

    /** The hang time: HANG_MS, or with the fake engine the test file's seconds (from 10 s; the file's text, null when there is none) */
    static long hangMs(boolean fakeEngine, String testFile) {
        if (!fakeEngine || testFile == null) return HANG_MS;
        try {
            long s = Long.parseLong(testFile.trim());
            return s >= 10 ? s * 1000 : HANG_MS;
        } catch (NumberFormatException e) {
            return HANG_MS;
        }
    }

    /** Whether something that should change hangs: running (the packer's worker alive, an import that runs) and unchanged for hangMs */
    static boolean hangs(long now, long lastChange, boolean running, long hangMs) {
        return running && lastChange > 0 && now - lastChange > hangMs;
    }

    /** Whether the packer's process ended at goneAt (0: it did not) and has not come back: nothing changed since, and backMs have passed */
    static boolean packerGone(long now, long goneAt, long lastChange, long backMs) {
        return goneAt > 0 && lastChange <= goneAt && now - goneAt > backMs;
    }

    /** A process's end in the player's words ("it crashed"), "" when Android's record says nothing a player can use */
    static String endWords(int reason) {
        switch (reason) {
            case CRASH:
            case CRASH_NATIVE: return "it crashed";
            case LOW_MEMORY: return "the phone ran out of memory";
            case ANR: return "it stopped responding";
            case SIGNALED: return "it was ended";
            default: return "";
        }
    }

    /** Whether a tool's or the importer's error says the storage is full (strerror's words for ENOSPC, or the packer's own sentence for them, PrepareText) */
    static boolean full(String... texts) {
        for (String t : texts) if (t != null && (t.contains("No space left") || t.contains("ENOSPC") || t.contains("The storage is full"))) return true;
        return false;
    }

    /** Whether a game whose run ended with the app's process stops at once instead of running again: a native crash while its files were imported */
    static boolean stopsAtOnce(int reason, String step) {
        return reason == CRASH_NATIVE && "import".equals(step);
    }
}
