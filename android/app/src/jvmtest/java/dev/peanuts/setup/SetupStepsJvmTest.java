// PEANUTS: SetupSteps on the JVM (peanuts/tests/test_setup_steps.py); prints "ok <n>" or throws
package dev.peanuts.setup;

import dev.peanuts.setup.SetupSteps.State;
import dev.peanuts.setup.SetupSteps.Step;

public final class SetupStepsJvmTest {
    private static int n;

    private static void eq(Object want, Object got) {
        n++;
        if (!String.valueOf(want).equals(String.valueOf(got))) throw new AssertionError("\nwant: " + want + "\n got: " + got);
    }

    public static void main(String[] a) {
        eq("[WELCOME, KEYS, FIRMWARE, GAMES, DONE]", SetupSteps.ORDER);
        eq("[KEYS, FIRMWARE, GAMES]", SetupSteps.COUNTED);

        // a fresh install: after the welcome, Keys; nothing done, nothing can end yet, the peanut whole
        State st = new State();
        eq(Step.KEYS, SetupSteps.first(st));
        eq(Step.KEYS, SetupSteps.next(Step.WELCOME));
        eq(false, SetupSteps.canFinish(st));
        eq(false, SetupSteps.canGoOn(Step.GAMES, st));
        eq(true, SetupSteps.canGoOn(Step.KEYS, st));
        eq(0f, SetupSteps.crack(Step.KEYS, st));

        // keys added: Firmware next, a third of the seam; keys left for later instead: Firmware next too, nothing cracked
        st.keys = true;
        eq(Step.FIRMWARE, SetupSteps.first(st));
        eq(1f / 3, SetupSteps.crack(Step.FIRMWARE, st));
        State later = new State();
        later.keysLater = true;
        eq(Step.FIRMWARE, SetupSteps.first(later));
        eq(true, SetupSteps.left(Step.KEYS, later));
        eq(0f, SetupSteps.crack(Step.FIRMWARE, later));
        // keys added after Later: done, not left
        later.keys = true;
        eq(true, SetupSteps.done(Step.KEYS, later));
        eq(false, SetupSteps.left(Step.KEYS, later));

        // firmware read: the games, which cannot be left
        st.firmware = true;
        eq(Step.GAMES, SetupSteps.first(st));
        eq(false, SetupSteps.optional(Step.GAMES));
        eq(false, SetupSteps.left(Step.GAMES, st));
        eq(2f / 3, SetupSteps.crack(Step.GAMES, st));

        // a game folder: the end, the seam run through; open on the end itself
        st.gameFolders = 2;
        eq(Step.DONE, SetupSteps.first(st));
        eq(true, SetupSteps.canFinish(st));
        eq(true, SetupSteps.canGoOn(Step.GAMES, st));
        eq(1f, SetupSteps.crack(Step.GAMES, st));
        eq(1f, SetupSteps.crack(Step.DONE, new State()));

        // an older install that had a game folder but not setup_done: it goes on where something is missing
        State old = new State();
        old.gameFolders = 1;
        eq(Step.KEYS, SetupSteps.first(old));
        old.keysLater = true; old.firmwareLater = true;
        eq(Step.DONE, SetupSteps.first(old));

        // moving through the screens, and the numbers
        eq(Step.FIRMWARE, SetupSteps.next(Step.KEYS));
        eq(Step.DONE, SetupSteps.next(Step.DONE));
        eq(Step.WELCOME, SetupSteps.previous(Step.KEYS));
        eq(Step.WELCOME, SetupSteps.previous(Step.WELCOME));
        eq(Step.GAMES, SetupSteps.previous(Step.DONE));
        eq(0, SetupSteps.number(Step.WELCOME));
        eq(3, SetupSteps.number(Step.GAMES));
        eq(Step.GAMES, SetupSteps.of("games"));
        eq(null, SetupSteps.of("addons"));   // a key of the setup before this one: it starts again where something is missing
        System.out.println("ok " + n);
    }
}
