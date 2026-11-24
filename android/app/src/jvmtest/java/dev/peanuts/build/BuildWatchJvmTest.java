// PEANUTS: the build's watchdog rules (BuildWatch, issue #348) on the JVM; peanuts/tests/test_build_queue.py compiles and runs it
package dev.peanuts.build;

/**
 * The watchdog's rules that need no phone (prints "ok" or throws):
 * <ul>
 * <li>a hang: only something that runs, only after the hang time without a change, and the fake engine's test file only with the fake engine;</li>
 * <li>a packer that ended: only once it has been gone for PACKER_BACK_MS with no change since, and never while it is back;</li>
 * <li>a full disk in the importer's and the packer's words;</li>
 * <li>a process's end in the player's words, and which ends stop a game at once (a native crash while importing, nothing else).</li>
 * </ul>
 */
public final class BuildWatchJvmTest {
    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }

    private static void same(Object want, Object got, String what) {
        if (want == null ? got != null : !want.equals(got)) throw new AssertionError(what + ": want <" + want + ">, got <" + got + ">");
    }

    public static void main(String[] args) {
        hangs();
        gone();
        full();
        ends();
        System.out.println("ok");
    }

    private static void hangs() {
        long h = BuildWatch.HANG_MS;
        same(30 * 60 * 1000L, h, "half an hour");
        check(!BuildWatch.hangs(1_000 + h, 1_000, true, h), "not at the hang time itself");
        check(BuildWatch.hangs(1_001 + h, 1_000, true, h), "just after it");
        check(!BuildWatch.hangs(1_000_000 + h, 1_000, false, h), "never while nothing runs (a packer that is not bound is the stall rule's)");
        check(!BuildWatch.hangs(Long.MAX_VALUE / 2, 0, true, h), "never without a first change");
        // the test time: only a build with the fake engine reads the file, and not below 10 s
        same(h, BuildWatch.hangMs(false, "60"), "a real engine's build ignores the file");
        same(60_000L, BuildWatch.hangMs(true, "60\n"), "the fake engine's test time");
        same(h, BuildWatch.hangMs(true, "5"), "not below 10 s");
        same(h, BuildWatch.hangMs(true, "soon"), "a file that does not read");
        same(h, BuildWatch.hangMs(true, null), "no file");
    }

    private static void gone() {
        long b = BuildWatch.PACKER_BACK_MS;
        check(!BuildWatch.packerGone(10_000_000, 0, 1_000, b), "a packer that did not go");
        check(!BuildWatch.packerGone(5_000 + b, 5_000, 1_000, b), "not at the time itself");
        check(BuildWatch.packerGone(5_001 + b, 5_000, 1_000, b), "gone, nothing changed since, the time passed");
        check(!BuildWatch.packerGone(5_001 + b, 5_000, 6_000, b), "the job changed after it went: Android started it again");
    }

    private static void full() {
        check(BuildWatch.full("write failed: No space left on device"), "the importer's strerror");
        check(BuildWatch.full(null, "OSError: [Errno 28] ENOSPC"), "a tool's errno, in the detail");
        check(BuildWatch.full("Making shaders for this phone stopped. The storage is full. Free some space, then tap Retry.", ""), "the packer's own sentence");
        check(!BuildWatch.full("write failed: Input/output error", null), "another error");
        check(!BuildWatch.full(), "nothing");
    }

    private static void ends() {
        same("it crashed", BuildWatch.endWords(BuildWatch.CRASH_NATIVE), "a native crash");
        same("it crashed", BuildWatch.endWords(BuildWatch.CRASH), "a Java crash");
        same("the phone ran out of memory", BuildWatch.endWords(BuildWatch.LOW_MEMORY), "low memory");
        same("it was ended", BuildWatch.endWords(BuildWatch.SIGNALED), "a signal (kill -9)");
        same("", BuildWatch.endWords(0), "no record");
        same("", BuildWatch.endWords(13), "other");
        check(BuildWatch.stopsAtOnce(BuildWatch.CRASH_NATIVE, "import"), "the importer crashed on the files");
        check(!BuildWatch.stopsAtOnce(BuildWatch.CRASH_NATIVE, "prepare"), "a native crash elsewhere runs again");
        check(!BuildWatch.stopsAtOnce(BuildWatch.LOW_MEMORY, "import"), "an end for memory runs again");
        check(!BuildWatch.stopsAtOnce(BuildWatch.SIGNALED, "import"), "kill -9 runs again");
        check(!BuildWatch.stopsAtOnce(0, "import"), "no record runs again");
    }
}
