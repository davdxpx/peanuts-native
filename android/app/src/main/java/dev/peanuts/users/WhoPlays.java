package dev.peanuts.users;

import java.util.List;

/**
 * "Who's playing?" at a game's start (Settings › Users): with two or more users the player picks who plays before the game starts, the user who played that
 * game last picked already; "Always X for this game" skips the question for that game; "Don't ask" starts every game with the user games start with, as
 * before. The pick reaches the host as DM_USER (GameActivity, through {@link #EXTRA}). The decision itself has no Android classes: UsersJvmTest checks it.
 */
public final class WhoPlays {
    private WhoPlays() {}

    /** The setting: ask at every start (the default), or never */
    public static final int ASK = 0, DONT_ASK = 1;
    /** The launch intent's extra that names the picked user (32 hex digits) */
    public static final String EXTRA = "dev.peanuts.user";

    /** What happens at a start: ask (with preselected first) or start with uid */
    public static final class Decision {
        public final boolean ask; public final String uid;
        Decision(boolean ask, String uid) { this.ask = ask; this.uid = uid; }
    }

    /**
     * users: the users' IDs in their order; def: the user games start with; mode: the setting; always: this game's "Always X" user or null; last: who played
     * this game last, or null
     */
    public static Decision decide(List<String> users, String def, int mode, String always, String last) {
        String d = users.contains(def) ? def : users.isEmpty() ? def : users.get(0);
        if (users.size() < 2) return new Decision(false, d);
        if (always != null && users.contains(always)) return new Decision(false, always);
        if (mode == DONT_ASK) return new Decision(false, d);
        return new Decision(true, last != null && users.contains(last) ? last : d);
    }

    /** The user a start names (GameActivity's hook): the extra when it is a user ID, else def */
    public static String uid(String extra, String def) { return extra != null && extra.matches("[0-9a-f]{32}") ? extra : def; }
}
