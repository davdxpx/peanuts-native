package dev.peanuts.smooth;

import android.app.Notification;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.os.Bundle;

/**
 * Developer view (Settings › Developer): on, the app shows what testers and developers read, as it always did (build numbers, counts, tool words, raw values);
 * off, player words instead (PlayerWords) and those details only in the logs. One switch. It starts on for an app that was installed before this version came
 * (nothing changes for anyone who has it) and off for a new install, decided once at the first start of this version and kept, so a later update does not
 * turn a new install's switch on. Its own preferences file with its one key: every process of the app may make that first decision (the game's, a frontend
 * starting a game before the launcher ever showed), and the same answer written twice changes nothing; a process never writes "peanuts", which the launcher
 * keeps changing.
 *
 * <p>Each call gives back what the place showed before while Developer view is on.
 */
public final class DevView {
    private DevView() {}

    /** The setting's name, as SettingsSections, SettingsHelp and LibrarySettings spell it (the test holds them to it) */
    public static final String NAME = "Developer view";

    private static final String FILE = "peanuts_devview", KEY = "on";

    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences(FILE, Context.MODE_PRIVATE); }

    /** Whether build numbers, counts and tool words show; the first call of this version decides it (installedBefore) */
    public static boolean on(Context c) {
        if (c == null) return true;
        SharedPreferences p = prefs(c);
        if (p.contains(KEY)) return p.getBoolean(KEY, true);
        boolean was = installedBefore(c);
        p.edit().putBoolean(KEY, was).commit();
        return was;
    }

    public static void on(Context c, boolean on) { prefs(c).edit().putBoolean(KEY, on).commit(); }

    /** Player words: Developer view is off */
    public static boolean plain(Context c) { return c != null && !on(c); }

    /** Whether this app was there before this version: Android updated it at least once (an install of its own has the same first and last time) */
    static boolean installedBefore(Context c) {
        try {
            PackageInfo i = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            return i.lastUpdateTime > i.firstInstallTime;
        } catch (Exception e) {
            return true;   // not known: as it was
        }
    }

    /** Home's line under the app's name (MainActivity.buildHome): none with Developer view off (Settings › About has the build) */
    public static CharSequence homeLine(Context c, CharSequence buildId) { return plain(c) ? null : buildId; }

    /** A line of the in-game menu (SidePanel, Lsfg) in player words with Developer view off */
    public static String panel(Context c, String s) { return plain(c) ? PlayerWords.panel(s) : s; }

    /** Frame generation's "Now:" line: the host's status in player words with Developer view off, else (or for a status it does not know) the menu's own line */
    public static String frameGen(Context c, String status, String line) {
        if (!plain(c)) return line;
        String w = PlayerWords.frameGen(status);
        return w != null ? w : line;
    }

    /** A build's text (the build card, the bottom screen's build page, upstream's Prepare card, the toasts and notifications) with Developer view off */
    public static CharSequence build(Context c, CharSequence s) {
        if (s == null || !plain(c)) return s;
        String w = PlayerWords.build(s.toString());
        return w.equals(s.toString()) ? s : w;   // a text with nothing to take out keeps its spans
    }

    public static String build(Context c, String s) { return s == null || !plain(c) ? s : PlayerWords.build(s); }

    /**
     * A build notification made elsewhere (upstream's PackService.note) with Developer view off: its text, sub text and big text through {@link #build}. The
     * extras are what the system draws a standard notification from, so the change shows; the notification is the same object
     */
    public static Notification calm(Context c, Notification n) {
        if (n == null || !plain(c)) return n;
        Bundle x = n.extras;
        for (String k : new String[] {Notification.EXTRA_TEXT, Notification.EXTRA_SUB_TEXT, Notification.EXTRA_BIG_TEXT}) {
            CharSequence v = x.getCharSequence(k);
            if (v != null) x.putCharSequence(k, build(c, v));
        }
        return n;
    }
}
