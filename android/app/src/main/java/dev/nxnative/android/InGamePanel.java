package dev.nxnative.android;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.function.IntConsumer;

import dev.peanuts.ingame.GameMenu;
import dev.peanuts.ingame.Look;
import dev.peanuts.ingame.Rail;

/**
 * PEANUTS: the in-game menus (the canvas "Peanuts Settings Redesign", page "In-game menus"). Upstream's one side panel becomes two menus: the game menu at the
 * left (dev.peanuts.ingame.GameMenu: this session's rows, Stop) and the quick menu at the right (upstream's SidePanel with its tabs on a rail,
 * dev.peanuts.ingame.Rail). A Peanuts file in upstream's launcher package, as ModsPanel, for the package-private parts of GameActivity, SidePanel and the saves.
 * <p>
 * How they open: Home pressed, − and + held, Back and a swipe in from the left edge open the game menu; Home held (half a second, while it is still down),
 * a swipe in from the right edge, a long press on the on-screen controls' button and the game menu's Quick menu row open the quick menu. Home pressed again
 * closes either. B in the quick menu goes back to the game menu when that opened it, else to the game.
 * <p>
 * GameActivity's hooks: {@link #attach} (onCreate), {@link #key} and {@link #toggle} (the controller's combination), {@link #menuKey} and {@link #menuMotion}
 * (the game menu has the controller while it is open), {@link #back} (Back). SidePanel's: {@link #frame}, {@link #rail}, {@link #railFocused}, {@link #slideIn},
 * {@link #slideOut}, {@link #laid}, {@link #panelBack}.
 */
public final class InGamePanel {
    private InGamePanel() {}

    private static final Handler UI = new Handler(Looper.getMainLooper());
    private static GameMenu menu;
    private static SidePanel panelOf;
    private static GameMenu.Item backupRow;
    private static boolean fromGame;     // the quick menu was opened from the game menu: B goes back there
    private static boolean padOpened;    // ... with a controller
    private static Rail railOf;
    private static int shownTab = -1;    // the tab the quick menu's rows show (a new one fades in)

    // ---- GameActivity ----

    /** onCreate, once the panel and its handle are in place: the game menu over the panel, a handle at the left edge, the rows' work */
    static void attach(Activity a, FrameLayout root, SidePanel panel) {
        panelOf = panel;
        float scale = PanelLook.textScale(Prefs.panelText(a));
        int size = Prefs.panelSize(a);
        int screen = a.getResources().getDisplayMetrics().widthPixels;
        int w = Math.round(Math.min(Look.dp(a, size == 0 ? 280 : size == 2 ? 340 : 300), screen * 0.48f));
        menu = new GameMenu(a, w, Math.round(PanelLook.rowPx(size, a.getResources().getDisplayMetrics().density) * 0.92f), scale);   // a little under the panel's rows: all six and Stop fit 411 dp (the Thor)
        String id = String.valueOf(a.getIntent().getStringExtra("title_id")), name = a.getIntent().getStringExtra("name");
        menu.game(name, null, dev.peanuts.Companion::startedAt);   // read when the menu opens: Companion.start comes after attach
        List<GameMenu.Item> rows = new ArrayList<>();
        rows.add(new GameMenu.Item("play", "Back to the game", false, () -> menu.close(null)));
        rows.add(new GameMenu.Item("pause", "Pause the game", false, () -> menu.close(() -> { if (a instanceof GameActivity) ((GameActivity) a).pause(); })));
        quickRow = new GameMenu.Item("panel", "Quick menu", true, () -> quick(a, true, padOpened));
        rows.add(quickRow);
        backupRow = new GameMenu.Item("save-up", "Back up the save", false, () -> backup(a));
        rows.add(backupRow);
        rows.add(new GameMenu.Item("buttons", "Button mapping", true, () -> PadMapUi.forGame(a, game(a), () -> {})));   // applies at the next start, as its page says
        rows.add(new GameMenu.Item("log", "Share log", false, () -> menu.close(() -> shareLog(a, id, name))));
        GameMenu.Item stop = new GameMenu.Item("stop", "Stop the game", false, () -> {
            Native.note("stopped by the user");
            ExitWatch.stopped();
            if (a instanceof GameActivity) ((GameActivity) a).backToFrontend();
            android.os.Process.killProcess(android.os.Process.myPid());
        });
        menu.items(rows, stop);
        root.addView(menu, root.indexOfChild(panel) + 1, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // The cover (the launcher's copy of the game's icon) and the version, read off the main thread
        new Thread(() -> {
            File f = new File(dev.peanuts.PeanutsPrefs.iconsDir(a), id + ".png");
            Bitmap b = f.isFile() ? BitmapFactory.decodeFile(f.getPath()) : null;
            if (b != null) UI.post(() -> menu.cover(b));
        }, "peanuts-menu-cover").start();
        String game = a.getIntent().getStringExtra("game");
        if (game != null) GameMeta.load(new File(game), m -> { if (m != null && m.icon != null) UI.post(() -> menu.cover(m.icon)); if (m != null) UI.post(() -> menu.version(m.version)); });

        // A swipe in from the left edge opens the game menu: a strip as upstream's at the right, shown while that one shows (the on-screen controls)
        float dp = a.getResources().getDisplayMetrics().density;
        View left = new View(a);
        float[] downX = {-1};
        left.setOnTouchListener((v, e) -> {
            int act = e.getActionMasked();
            if (act == MotionEvent.ACTION_DOWN) downX[0] = e.getRawX();
            else if (act == MotionEvent.ACTION_MOVE && downX[0] >= 0 && e.getRawX() - downX[0] > 24 * dp) { downX[0] = -1; openGame(a, false); }
            else if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL) downX[0] = -1;
            return true;
        });
        left.setContentDescription("Open the game menu");
        left.setFocusable(false);   // never the controller's focus (its default highlight would show over the game)
        left.setDefaultFocusHighlightEnabled(false);
        left.setOnClickListener(v -> openGame(a, false));
        root.addView(left, root.indexOfChild(panel), new FrameLayout.LayoutParams(Math.round(20 * dp), Math.round(140 * dp), android.view.Gravity.START | android.view.Gravity.TOP));
        root.addOnLayoutChangeListener((v, l, t, r, bo, ol, ot, or, ob) -> left.setTranslationY((bo - t) * 0.3f - 70 * dp));
        root.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            View right = rightHandle(root);
            int vis = right != null ? right.getVisibility() : View.VISIBLE;
            if (left.getVisibility() != vis) left.setVisibility(vis);
            // Under gesture navigation the system takes a swipe in from either edge as Back (which opens the game menu): the two strips are kept out of
            // that, so the left one opens the game menu and the right one the quick menu (140 dp each, under Android's 200 dp a side)
            exclude(left);
            if (right != null) exclude(right);
        });
    }

    private static void exclude(View v) {
        java.util.List<android.graphics.Rect> was = v.getSystemGestureExclusionRects();
        android.graphics.Rect r = new android.graphics.Rect(0, 0, v.getWidth(), v.getHeight());
        if (v.getVisibility() != View.VISIBLE) r.setEmpty();
        if (was.size() == 1 && was.get(0).equals(r)) return;
        v.setSystemGestureExclusionRects(java.util.Collections.singletonList(r));
    }

    private static View rightHandle(FrameLayout root) {
        for (int i = 0; i < root.getChildCount(); i++) {
            View v = root.getChildAt(i);
            if ("Open the in-game panel".contentEquals(v.getContentDescription() == null ? "" : v.getContentDescription())) return v;
        }
        return null;
    }

    private static SidePanel panel(Activity a) { return panelOf; }
    private static GameMenu.Item quickRow;   // the game menu's Quick menu row: B from the quick menu comes back to it

    /** The running game as the launcher's pages see it (GameActivity.game's fields, from the start's extras) */
    private static Game game(Activity a) {
        Intent i = a.getIntent();
        return new Game(String.valueOf(i.getStringExtra("title_id")), String.valueOf(i.getStringExtra("name")), String.valueOf(i.getStringExtra("game")), String.valueOf(i.getStringExtra("pack")));
    }

    // Home: pressed or held. EAT on its press as upstream; held HOLD_MS while nothing has fired yet: the quick menu (while still down), its release then eaten
    private static boolean homeHeld;
    private static Runnable holdRun;

    /** GameActivity.dispatchKeyEvent: upstream's combination (PadPanel.Combo) with Home held for the quick menu */
    static int key(Activity a, PadPanel.Combo combo, int k, boolean down, int repeat, long t) {
        int c = combo.key(k, down, repeat, t);
        if (k != PadPanel.KEY_MODE || c == PadPanel.PASS) return c;
        if (down) {
            if (repeat == 0) {
                homeHeld = false;
                if (holdRun != null) UI.removeCallbacks(holdRun);
                holdRun = () -> { homeHeld = true; held(a); };
                UI.postDelayed(holdRun, PadPanel.HOLD_MS);
            }
            return PadPanel.EAT;
        }
        if (holdRun != null) UI.removeCallbacks(holdRun);
        if (homeHeld) { homeHeld = false; return PadPanel.EAT; }
        return PadPanel.TOGGLE;
    }

    /** Home or the controller's Back held: the quick menu (from the game menu when that is open), unless it is open already */
    private static void held(Activity a) {
        SidePanel p = panel(a);
        if (p != null && p.isOpen()) return;
        quick(a, menu != null && menu.isOpen(), true);
    }

    // The controller's own Back, for a device whose Home is Android's (the AYN Thor: Home goes to the launcher and closes the game, Back is the button at the
    // bottom right): pressed it opens the game menu with the controller's selection, held HOLD_MS the quick menu, as Home does elsewhere. In the quick menu
    // it goes one step back as before (onBackPressed); a Back Android made from an unused B (FLAG_FALLBACK) and the edge swipe's go there too
    private static boolean backDown, backHeld;
    private static Runnable backRun;

    /** GameActivity.dispatchKeyEvent: true when it was the controller's Back and taken here */
    static boolean backKey(Activity a, KeyEvent e) {
        if (!controllerBack(e)) return false;
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            if (e.getRepeatCount() > 0) return backDown;
            SidePanel p = panel(a);
            backDown = p == null || !p.isOpen();
            if (!backDown) return false;
            backHeld = false;
            if (backRun != null) UI.removeCallbacks(backRun);
            backRun = () -> { backHeld = true; held(a); };
            UI.postDelayed(backRun, PadPanel.HOLD_MS);
            return true;
        }
        if (e.getAction() != KeyEvent.ACTION_UP || !backDown) return false;
        backDown = false;
        if (backRun != null) UI.removeCallbacks(backRun);
        if (backHeld || e.isCanceled()) { backHeld = false; return true; }   // a Back another app took while held (a screen swap) comes up cancelled
        toggle(a, true);   // the game menu, with the selection shown; or it closes
        return true;
    }

    /**
     * Back from a controller's own key: Android gives such a key the keyboard's source alone (a gamepad's source only to its buttons), so the device tells
     * it, a real one that is a gamepad. Not a Back Android made from an unused B (FLAG_FALLBACK), nor the navigation bar's or the edge swipe's
     */
    private static boolean controllerBack(KeyEvent e) {
        if (e.getKeyCode() != KeyEvent.KEYCODE_BACK || (e.getFlags() & KeyEvent.FLAG_FALLBACK) != 0 || e.getDeviceId() == android.view.KeyCharacterMap.VIRTUAL_KEYBOARD) return false;
        InputDevice d = e.getDevice();
        return d != null && !d.isVirtual() && d.supportsSource(InputDevice.SOURCE_GAMEPAD);
    }

    /** Home pressed, − and + held, the controller's Back pressed: the game menu, or either menu closes */
    static void toggle(Activity a, boolean pad) {
        SidePanel p = panel(a);
        if (p != null && p.isOpen()) { fromGame = false; p.close(); return; }
        if (menu != null && menu.isOpen()) { menu.close(null); return; }
        openGame(a, pad);
    }

    /**
     * Back (the phone's button or its edge swipe, a controller's Back that backKey did not take): one step back in the quick menu, the game menu closes,
     * else it opens, with the selection shown while a controller is attached. A tool that watches the keys (an accessibility service: on the AYN Thor a
     * screen swap on Back held) hands the Back key on as Android's own, which no longer tells the controller's
     */
    static void back(Activity a) {
        SidePanel p = panel(a);
        if (p != null && p.isOpen()) { panelBack(p); return; }
        if (menu != null && menu.isOpen()) { menu.close(null); return; }
        openGame(a, padAttached());
    }

    /** A controller is attached (a real device that is a gamepad: the AYN Thor's own always is) */
    private static boolean padAttached() {
        for (int id : InputDevice.getDeviceIds()) {
            InputDevice d = InputDevice.getDevice(id);
            if (d != null && !d.isVirtual() && d.supportsSource(InputDevice.SOURCE_GAMEPAD)) return true;
        }
        return false;
    }

    private static void openGame(Activity a, boolean pad) {
        SidePanel p = panel(a);
        if (menu == null) return;
        if (p != null && p.onOpen != null) p.onOpen.run();   // the game's held buttons released (GameActivity.releaseGameInput)
        padOpened = pad;
        menu.open(pad);
        if (backupRow != null && !"Backing up".equals(backupRow.valueText())) new Thread(() -> { String v = lastBackup(a); UI.post(() -> backupRow.value(v, false)); }, "peanuts-menu-backups").start();
    }

    /** The quick menu: from the game menu (it slides out to the left while the quick menu comes in), or on its own */
    private static void quick(Activity a, boolean fromGameMenu, boolean pad) {
        SidePanel p = panel(a);
        if (p == null) return;
        fromGame = fromGameMenu;
        if (menu != null && menu.isOpen()) menu.close(null);
        if (pad) p.padToggle(); else p.open();
    }

    // The keys a menu took a press of: their release is the menu's too, also after it closed
    private static final HashSet<Integer> taken = new HashSet<>();
    private static int heldDir = PadPanel.NONE, stickDir = PadPanel.NONE, repeats;
    private static Runnable repeat;

    /** GameActivity.dispatchKeyEvent: while the game menu is open it has the controller (D-pad and A, B); no pad key reaches the game. True when taken */
    static boolean menuKey(Activity a, KeyEvent e, boolean gamepad) {
        int k = e.getKeyCode();
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN, up = e.getAction() == KeyEvent.ACTION_UP;
        SidePanel shown = shownPanel(a);
        if (shown != null && gamepad && down && e.getRepeatCount() == 0 && k == keys(a)[0]) dev.peanuts.ui.MenuFeel.select(shown.findFocus());   // A in the quick menu
        if (gamepad && up && taken.remove(k)) { if (PadPanel.ofKey(k) == heldDir) release(); return true; }
        if (menu == null || !menu.isOpen() || !gamepad || k == KeyEvent.KEYCODE_BACK) return false;
        if (down) taken.add(k);
        if (!down || e.getRepeatCount() > 0) return true;
        int dir = PadPanel.ofKey(k);
        if (dir != PadPanel.NONE) { press(dir); return true; }
        int[] cb = keys(a);
        if (k == cb[0] || k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER) menu.confirm();
        else if (k == cb[1]) menu.back();
        return true;
    }

    /** GameActivity.onGenericMotionEvent: the stick and the hat move the game menu's selection; the game gets none of it */
    static boolean menuMotion(Activity a, MotionEvent e) {
        if (menu == null || !menu.isOpen() || (e.getSource() & InputDevice.SOURCE_JOYSTICK) != InputDevice.SOURCE_JOYSTICK) return false;
        if (e.getAction() != MotionEvent.ACTION_MOVE) return true;
        int d = PadPanel.stick(e.getAxisValue(MotionEvent.AXIS_HAT_X), e.getAxisValue(MotionEvent.AXIS_HAT_Y), stickDir);
        if (d == PadPanel.NONE) d = PadPanel.stick(e.getAxisValue(MotionEvent.AXIS_X), e.getAxisValue(MotionEvent.AXIS_Y), stickDir);
        if (d != stickDir) {
            if (stickDir != PadPanel.NONE && stickDir == heldDir) release();
            stickDir = d;
            if (d != PadPanel.NONE) press(d);
        }
        return true;
    }

    private static void press(int dir) {
        if (dir == heldDir) return;
        heldDir = dir;
        repeats = 0;
        step(dir);
        if (repeat != null) UI.removeCallbacks(repeat);
        repeat = () -> {
            if (heldDir == PadPanel.NONE || menu == null || !menu.isOpen()) return;
            step(heldDir);
            UI.postDelayed(repeat, PadPanel.repeatDelay(++repeats));
        };
        UI.postDelayed(repeat, PadPanel.repeatDelay(0));
    }

    private static void release() { heldDir = PadPanel.NONE; if (repeat != null) UI.removeCallbacks(repeat); }

    private static void step(int dir) { menu.move(dir == PadPanel.UP, dir == PadPanel.DOWN, dir == PadPanel.LEFT, dir == PadPanel.RIGHT); }

    /** The controller's confirm and back keys: the Switch's A and B in this game, as the panel takes them (PadPanel.confirmBack) */
    /** The quick menu while it is open (the panel's own key handling presses its rows), else null */
    private static SidePanel shownPanel(Activity a) { SidePanel p = panel(a); return p != null && p.isOpen() ? p : null; }

    private static int[] keys(Activity a) {
        String id = String.valueOf(a.getIntent().getStringExtra("title_id"));
        return PadPanel.confirmBack(Prefs.padMap(a, id), Prefs.xboxButtons(a, id) == 2 ? 2 : 0);
    }

    // ---- The game menu's rows ----

    /**
     * Back up the save while the game runs: the save the game started with (GameActivity.runSave, the player's), copied where Back up saves puts its copies
     * (SaveUi.target). This process holds the save's lock, so the copy runs as SaveStore's own under it (backupLocked); the host writes a save only by an atomic
     * swap at the game's Commit, and SaveStore checks the save twice around the copy, so a Commit in between fails the copy, which is tried again (3 times)
     */
    private static void backup(Activity a) {
        File save = GameActivity.runSave();
        if (save == null) { backupRow.value("No save yet", false); return; }
        Game g = game(a);
        File to = SaveUi.target(a, g);
        backupRow.value("Backing up", false);
        menu.note(null, false);
        new Thread(() -> {
            String err = null;
            SaveStore.Backup b = null;
            for (int i = 0; i < 3 && b == null; i++) {
                try { b = SaveStore.backupLocked(save, to, SaveUi.stamp()); err = null; }
                catch (IOException | RuntimeException e) {
                    err = String.valueOf(e.getMessage());
                    if (!err.contains("changed while")) break;
                    try { Thread.sleep(400); } catch (InterruptedException ie) { break; }
                }
            }
            SaveStore.Backup done = b;
            String why = err;
            if (why != null) Native.note("[RED] [peanuts] in-game save backup: " + why);
            else Native.note("save backed up from the game menu: " + done.files + " files, " + done.bytes + " bytes");
            UI.post(() -> {
                if (done != null) { backupRow.value("Just now", false); return; }
                backupRow.value("Not backed up", true);
                menu.note(why.contains("no save folder") ? "This game has no save yet."
                        : why.contains("changed while") ? "The game kept saving while it was copied. Try again in a moment."
                        : "The backup did not finish. The save was only read, never changed.", true);
            });
        }, "peanuts-menu-backup").start();
    }

    /** The newest backup of the running save, as "Today 14:02" (null: none) */
    private static String lastBackup(Activity a) {
        try {
            File dir = SaveUi.target(a, game(a));
            long newest = 0;
            for (SaveStore.Backup b : SaveStore.list(dir)) newest = Math.max(newest, b.dir.lastModified());
            if (newest == 0) return null;
            Calendar now = Calendar.getInstance(), then = Calendar.getInstance();
            then.setTimeInMillis(newest);
            String hm = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(newest));
            if (now.get(Calendar.YEAR) == then.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)) return "Today " + hm;
            now.add(Calendar.DAY_OF_YEAR, -1);
            if (now.get(Calendar.YEAR) == then.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)) return "Yesterday " + hm;
            return new SimpleDateFormat("d MMM", Locale.getDefault()).format(new Date(newest));
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Share log from the game menu: the game's newest run (this one, so far) or its build, as everywhere (BuildLogUi.shareLog) */
    private static void shareLog(Activity a, String id, String name) {
        dev.peanuts.ui.BuildLogUi.shareLog(a, id, name, null, () -> {
            File log = newestRun(a, id);
            if (log == null) { android.widget.Toast.makeText(a, "No log of this game yet.", android.widget.Toast.LENGTH_LONG).show(); return; }
            Uri uri = LogProvider.uri(log);
            Intent send = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_STREAM, uri).putExtra(Intent.EXTRA_SUBJECT, "nxnative log " + log.getName());
            send.setClipData(ClipData.newRawUri(log.getName(), uri));
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            a.startActivity(Intent.createChooser(send, "Share " + log.getName()));
        });
    }

    private static File newestRun(Activity a, String id) {
        File ext = a.getExternalFilesDir(null);
        String t = id.trim().toUpperCase(Locale.ROOT) + "-";
        File[] l = ext == null ? null : new File(ext, "logs").listFiles((d, n) -> n.endsWith(".log") && n.toUpperCase(Locale.ROOT).startsWith(t));
        if (l == null || l.length == 0) return null;
        File best = l[0];
        for (File f : l) if (f.lastModified() > best.lastModified()) best = f;
        return best;
    }

    // ---- SidePanel: the quick menu ----

    /** SidePanel's constructor: its sheet as rail | (header over rows) */
    static void frame(SidePanel p, LinearLayout sheet, LinearLayout tabs, ScrollView rows) {
        railOf = new Rail(p.getContext(), sheet, tabs, rows, () -> panelBack(p));
        // Each step of the controller's focus in the quick menu: the fine tick (and the menu sound when on); a touch moves no focus
        p.getViewTreeObserver().addOnGlobalFocusChangeListener((old, now) -> {
            if (now == null || !p.isOpen() || p.isInTouchMode() || !within(now, sheet)) return;
            View chosen = dev.peanuts.ui.PanelPolish.chosen(old, now);   // into a track of choices, or the first focus there: onto the chosen one
            // After this change has reached every listener: moved inside it, SidePanel's frame would be drawn for this change after the next one's
            if (chosen != null) { chosen.post(() -> { if (p.isOpen() && now.isFocused()) chosen.requestFocus(); }); return; }
            if (old != null) dev.peanuts.ui.MenuFeel.move(now);   // a step ticks; the first focus of an opening has the opening's sound
        });
    }

    private static boolean within(View v, View in) {
        for (Object o = v; o instanceof View; o = ((View) o).getParent()) if (o == in) return true;
        return false;
    }

    /** SidePanel.tabBar: the rail instead of the bar (true: done); show switches the tab */
    static boolean rail(SidePanel p, int tab, IntConsumer show) {
        if (railOf == null) return false;
        railOf.action(null, null, null);   // a tab's build sets its own again (ModsTab: All off)
        railOf.tabs(SidePanel.TAB_NAMES, tab, show);
        return true;
    }

    /** ModsPanel's tabs: their own action in the header (the Cheats tab's All off) */
    static void action(SidePanel p, String key, String label, Runnable run) { if (railOf != null) railOf.action(key, label, run); }

    /** SidePanel.showTab: a tab switched from the rail keeps the controller's focus on the rail */
    static boolean railFocused() { return railOf != null && railOf.focused(); }

    /** SidePanel.open: the sheet slides in (OPEN_MS, decelerating), the scrim fades in */
    static void slideIn(SidePanel p, View sheet) {
        dev.peanuts.ui.MenuFeel.open(sheet);
        View scrim = p.getChildAt(0);
        sheet.animate().cancel();
        sheet.setTranslationX(sheet.getLayoutParams().width);
        sheet.animate().translationX(0).setDuration(Look.OPEN_MS).setInterpolator(Look.OUT).withLayer().start();
        if (scrim != null && scrim != sheet) { scrim.animate().cancel(); scrim.setAlpha(0f); scrim.animate().alpha(1f).setDuration(Look.OPEN_MS).setInterpolator(Look.OUT).start(); }
        shownTab = -1;
    }

    /** SidePanel.close: the sheet slides out (CLOSE_MS, accelerating), the scrim fades; then gone (end) */
    static void slideOut(SidePanel p, View sheet, Runnable end) {
        dev.peanuts.ui.MenuFeel.back(sheet);
        View scrim = p.getChildAt(0);
        sheet.animate().cancel();
        sheet.animate().translationX(sheet.getLayoutParams().width).setDuration(Look.CLOSE_MS).setInterpolator(Look.IN).withLayer().withEndAction(end).start();
        if (scrim != null && scrim != sheet) scrim.animate().alpha(0f).setDuration(Look.CLOSE_MS).setInterpolator(Look.IN).start();
    }

    /** SidePanel.padKey's back key, the header's back button: back to the game menu when that opened the quick menu, else to the game */
    static void panelBack(SidePanel p) {
        boolean back = fromGame;
        fromGame = false;
        p.close();
        if (back && p.getContext() instanceof Activity) {
            openGame((Activity) p.getContext(), padOpened || !p.isInTouchMode());
            if (menu != null && quickRow != null) menu.at(quickRow);   // back on the row it came from, as a console's menus do
        }
    }

    /**
     * SidePanel.look, after a tab is built: what the two menus moved. General (the menu's own settings) keeps the panel's size and text size and Pause when the
     * game comes back; Pause, Stop and Back to the game are the game menu's. Controls' Button mapping is the game menu's too. A tab switched to fades in
     */
    static void laid(SidePanel p, LinearLayout box, int tab) {
        String name = tab >= 0 && tab < SidePanel.TAB_NAMES.length ? SidePanel.TAB_NAMES[tab] : "";
        if ("General".equals(name)) {
            for (int i = box.getChildCount() - 1; i >= 0; i--) {
                View v = box.getChildAt(i);
                if (!(v instanceof TextView)) continue;
                String s = String.valueOf(((TextView) v).getText());
                if (s.equals("Pause the game") || s.equals("Stop the game") || s.equals("Back to the game") || s.startsWith("The game runs on behind this panel")) box.removeViewAt(i);
                else if (s.equalsIgnoreCase("Panel")) ((TextView) v).setText("Menu");
                else if (s.startsWith("Pause when the game comes back") && i > 0) box.addView(head(p, "This game"), i);
            }
            feelSwitches(p, box);
        } else if ("Controls".equals(name)) {
            View map = box.findViewById(5300);   // SidePanel.ID_MAP: Button mapping, now the game menu's
            if (map != null && map.getParent() == box) {
                int i = box.indexOfChild(map);
                box.removeViewAt(i);
                View before = i > 0 ? box.getChildAt(i - 1) : null;   // "Next start" with nothing under it
                if (before instanceof TextView && "Next start".contentEquals(((TextView) before).getText()) && i - 1 == box.getChildCount() - 1) box.removeViewAt(i - 1);
            }
        }
        dev.peanuts.ui.PanelPolish.apply(box);
        if (shownTab >= 0 && shownTab != tab && p.isOpen()) {   // a tab switched to: its rows come up 8 dp and fade in
            dev.peanuts.ui.MenuFeel.tab(box);
            box.setAlpha(0f);
            box.setTranslationY(Look.dp(p.getContext(), 8));
            box.animate().alpha(1f).translationY(0f).setStartDelay(Look.TAB_OUT_MS).setDuration(Look.TAB_IN_MS).setInterpolator(Look.OUT).withLayer().start();
        }
        shownTab = tab;
    }

    /** The menu's feel in its own settings, at the end of its Menu section: Vibrate on each step (on), Menu sounds (off) (dev.peanuts.ui.MenuFeel) */
    private static void feelSwitches(SidePanel p, LinearLayout box) {
        android.content.Context c = p.getContext();
        int at = box.getChildCount();
        for (int i = 0; i < box.getChildCount(); i++) {
            View v = box.getChildAt(i);
            if (v instanceof TextView && !(v instanceof android.widget.CompoundButton) && "This game".contentEquals(((TextView) v).getText())) { at = i; break; }
        }
        android.widget.CheckBox vib = new android.widget.CheckBox(c), snd = new android.widget.CheckBox(c);
        vib.setText("Vibrate on each step");
        vib.setChecked(dev.peanuts.ui.MenuFeel.haptics(c));
        vib.setOnCheckedChangeListener((b, on) -> { dev.peanuts.ui.MenuFeel.haptics(c, on); if (on) dev.peanuts.ui.MenuFeel.select(b); });
        snd.setText("Menu sounds");
        snd.setChecked(dev.peanuts.ui.MenuFeel.sounds(c));
        snd.setOnCheckedChangeListener((b, on) -> { dev.peanuts.ui.MenuFeel.sounds(c, on); if (on) dev.peanuts.ui.MenuFeel.select(b); });
        box.addView(vib, at);
        box.addView(snd, at + 1);
        ModsPanel.style(c, vib);
        ModsPanel.style(c, snd);
    }

    private static TextView head(SidePanel p, String s) {
        TextView h = new TextView(p.getContext());
        h.setText(s);
        h.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        h.setTextSize(15);
        h.setPadding(0, Math.round(18 * p.getResources().getDisplayMetrics().density), 0, 0);
        return h;
    }
}
