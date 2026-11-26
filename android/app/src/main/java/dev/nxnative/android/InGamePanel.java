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
import dev.peanuts.ingame.GameRows;
import dev.peanuts.ingame.Look;
import dev.peanuts.ingame.Rail;

/**
 * PEANUTS: the in-game menus. Upstream's one side panel becomes two menus: the game menu at the left (dev.peanuts.ingame.GameMenu: this session's rows,
 * Stop) and the quick menu at the right (upstream's SidePanel with its tabs on a rail,
 * dev.peanuts.ingame.Rail). A Peanuts file in upstream's launcher package, as ModsPanel, for the package-private parts of GameActivity, SidePanel and the saves.
 * <p>
 * How they open: Home pressed, − and + held, Back and a swipe in from the left edge open the game menu; Home held (half a second, while it is still down),
 * a swipe in from the right edge, a long press on the on-screen controls' button and the game menu's Quick menu row open the quick menu. Home pressed again
 * closes either. B in the quick menu goes back to the game menu when that opened it, else to the game.
 * <p>
 * GameActivity's hooks: {@link #attach} (onCreate), {@link #key} and {@link #toggle} (the controller's combination), {@link #backKey} (the controller's own
 * Back, unless GameActivity.backFree says Back is something else's there), {@link #menuKey} and {@link #menuMotion} (the game menu has the controller while
 * it is open), {@link #back} and {@link #open} (Back), {@link #inputLost} (a dialog or another app took the keys). SidePanel's: {@link #frame}, {@link #rail},
 * {@link #railFocused}, {@link #slideIn}, {@link #slideOut}, {@link #laid}, {@link #panelBack}. GameActivity.runSave gives the save the game runs on.
 */
public final class InGamePanel {
    private InGamePanel() {}

    private static final Handler UI = new Handler(Looper.getMainLooper());
    /** The right edge's handle's name for TalkBack (GameActivity), by which the left strip finds it (rightHandle) */
    static final String HANDLE = "Open the quick menu";
    private static GameMenu menu;
    private static SidePanel panelOf;
    private static GameMenu.Item backupRow;
    private static boolean fromGame;     // the quick menu was opened from the game menu: B goes back there
    private static boolean padOpened;    // ... with a controller
    private static final java.util.Map<SidePanel, Rail> rails = new java.util.WeakHashMap<>();
    private static GameRows makingBottom;   // the one menu's Game, while its panel is made (frame)
    private static SidePanel bottomPanel;   // the menu on the bottom screen: a second quick menu, Game first on its rail
    private static dev.peanuts.ingame.BottomScreen bottomScreen;
    private static int shownTab = -1;    // the tab the quick menu's rows show (a new one fades in)

    // ---- GameActivity ----

    /** onCreate, once the panel and its handle are in place: the game menu over the panel, a handle at the left edge, the rows' work */
    static void attach(Activity a, FrameLayout root, SidePanel panel) {
        forget(panel);
        panelOf = panel;
        float scale = PanelLook.textScale(Prefs.panelText(a));
        menu = new GameMenu(a, menuWidth(a), menuRow(a), scale);
        menu.onKeep = () -> stopAfter = null;   // Stop pressed during a backup, then Keep playing: the game goes on after the copy
        menu.onClosing = () -> { release(); stickDir = PadPanel.NONE; stopAfter = null; };   // a direction held as it closes (its release comes while closed, so the next press would be taken as held); a Stop waiting for the backup goes with the menu
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
        Runnable stopNow = () -> {
            Native.note("stopped by the user");
            ExitWatch.stopped();
            if (a instanceof GameActivity) ((GameActivity) a).backToFrontend();
            android.os.Process.killProcess(android.os.Process.myPid());
        };
        GameMenu.Item stop = new GameMenu.Item("stop", "Stop the game", false, () -> {
            if (!backingUp.get()) { stopNow.run(); return; }
            stopAfter = stopNow;   // a copy cut off would leave a save-sized folder for a day (SaveStore.cleanStale)
            note("The backup finishes first, then the game stops.", false);
        });
        menu.items(rows, stop);
        menuRows = rows;
        menuStop = stop;
        Runnable[] opener = {null};
        a.registerActivityLifecycleCallbacks(new android.app.Application.ActivityLifecycleCallbacks() {   // the game leaves the screen: the bottom one is given back
            @Override public void onActivityPaused(Activity x) { if (x == a) { resumed = false; closeBottom(true); } }
            @Override public void onActivityCreated(Activity x, android.os.Bundle b) {}
            @Override public void onActivityStarted(Activity x) {}
            @Override public void onActivityResumed(Activity x) { if (x == a) resumed = true; }
            @Override public void onActivityStopped(Activity x) {}
            @Override public void onActivitySaveInstanceState(Activity x, android.os.Bundle b) {}
            @Override public void onActivityDestroyed(Activity x) { if (x == a) { closeBottom(true); if (dev.peanuts.ingame.BottomMenu.opener == opener[0]) dev.peanuts.ingame.BottomMenu.opener = null; } }
        });
        opener[0] = () -> { if (bottomMode(a)) openBottom(a, true, false); };   // the bottom screen's swipe up (the game's stats page)
        dev.peanuts.ingame.BottomMenu.opener = opener[0];
        root.addView(menu, root.indexOfChild(panel) + 1, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // The cover (the launcher's copy of the game's icon) and the version, read off the main thread
        new Thread(() -> {
            File f = new File(dev.peanuts.PeanutsPrefs.iconsDir(a), id + ".png");
            Bitmap b = f.isFile() ? BitmapFactory.decodeFile(f.getPath()) : null;
            if (b != null) UI.post(() -> { menu.cover(b); cover(b); });
        }, "peanuts-menu-cover").start();
        String game = a.getIntent().getStringExtra("game");
        if (game != null) GameMeta.load(new File(game), m -> { if (m != null && m.icon != null) UI.post(() -> { menu.cover(m.icon); cover(m.icon); }); if (m != null) UI.post(() -> { menu.version(m.version); version(m.version); }); });

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
            if (leftHidden == null) leftHidden = oneMode(a);   // the layout as last seen: Layout's choice works it out again (layoutSetting)
            if (leftHidden) vis = View.GONE;   // one menu (at the right, or on the bottom screen, opened from its own edge): the left edge is the game's
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
            if (HANDLE.contentEquals(v.getContentDescription() == null ? "" : v.getContentDescription())) return v;
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
        if (p != null && p.isOpen() || one() != null) return;
        if (oneMode(a)) { if (menu != null && menu.isOpen()) menu.close(null); openOne(a, false, true); return; }
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
            backDown = (p == null || !p.isOpen()) && one() == null && (open(a) || free(a));
            if (!backDown) return false;
            backHeld = false;
            if (backRun != null) UI.removeCallbacks(backRun);
            backRun = () -> { backHeld = true; if (open(a) || free(a)) held(a); };
            UI.postDelayed(backRun, PadPanel.HOLD_MS);
            return true;
        }
        if (e.getAction() != KeyEvent.ACTION_UP || !backDown) return false;
        backDown = false;
        if (backRun != null) UI.removeCallbacks(backRun);
        if (backHeld || e.isCanceled()) { backHeld = false; return true; }   // a Back another app took while held (a screen swap) comes up cancelled
        if (open(a) || free(a)) toggle(a, true);   // the game menu, with the selection shown; or it closes
        return true;
    }

    /**
     * Back is onBackPressed's while no menu is open and the game screen has something that answers it (GameActivity.backFree: Preparing shaders, a
     * shader preparation, the controller applet's join screen), so it does there what it did before the menus
     */
    private static boolean free(Activity a) { return !(a instanceof GameActivity) || ((GameActivity) a).backFree(); }

    /** A menu is open: the game menu, the quick menu or the one menu (GameActivity.onBackPressed: it closes before Back goes to the controller applet) */
    static boolean open(Activity a) {
        SidePanel p = panel(a);
        return p != null && p.isOpen() || menu != null && menu.isOpen() || one() != null;
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
        SidePanel o = one();
        if (o != null) { o.close(); return; }
        SidePanel p = panel(a);
        if (p != null && p.isOpen()) { fromGame = false; p.close(); return; }
        if (menu != null && menu.isOpen()) { menu.close(null); return; }
        if (oneMode(a)) { openOne(a, true, pad); return; }
        openGame(a, pad);
    }

    /**
     * Back (the phone's button or its edge swipe, a controller's Back that backKey did not take): one step back in the quick menu, the game menu closes,
     * else it opens, with the selection shown while a controller is attached. A tool that watches the keys (an accessibility service: on the AYN Thor a
     * screen swap on Back held) hands the Back key on as Android's own, which no longer tells the controller's
     */
    static void back(Activity a) {
        SidePanel o = one();
        if (o != null) { o.close(); return; }
        SidePanel p = panel(a);
        if (p != null && p.isOpen()) { panelBack(p); return; }
        if (menu != null && menu.isOpen()) { menu.close(null); return; }
        boolean pad = padAttached();
        if (oneMode(a)) { openOne(a, true, pad); return; }
        openGame(a, pad);
    }

    /** A controller is attached (a real device that is a gamepad: the AYN Thor's own always is) */
    private static boolean padAttached() {
        for (int id : InputDevice.getDeviceIds()) {
            InputDevice d = InputDevice.getDevice(id);
            if (d != null && !d.isVirtual() && d.supportsSource(InputDevice.SOURCE_GAMEPAD)) return true;
        }
        return false;
    }

    /** The game menu's width: Settings › Panel size's, at most 48 % of the screen as it is now (a turn of a large screen changes it) */
    private static int menuWidth(Activity a) {
        int size = Prefs.panelSize(a);
        int screen = a.getResources().getDisplayMetrics().widthPixels;
        return Math.round(Math.min(Look.dp(a, size == 0 ? 280 : size == 2 ? 340 : 300), screen * 0.48f));
    }

    /** The game menu's row height: a little under the panel's rows (Menu settings › Size), so all six and Stop fit 411 dp (the Thor) */
    private static int menuRow(Activity a) { return Math.round(PanelLook.rowPx(Prefs.panelSize(a), a.getResources().getDisplayMetrics().density) * 0.92f); }

    private static void openGame(Activity a, boolean pad) {
        SidePanel p = panel(a);
        if (menu == null) return;
        menu.width(menuWidth(a));
        menu.look(menuRow(a), PanelLook.textScale(Prefs.panelText(a)));   // Size and Text size changed in the quick menu meanwhile
        if (one() != null) one().close();   // one menu at a time
        if (p != null && p.onOpen != null) p.onOpen.run();   // the game's held buttons released (GameActivity.releaseGameInput)
        padOpened = pad;
        menu.open(pad);
        showBackup(a);
    }

    /** The quick menu: from the game menu (it slides out to the left while the quick menu comes in), or on its own */
    private static void quick(Activity a, boolean fromGameMenu, boolean pad) {
        SidePanel p = panel(a);
        if (p == null) return;
        fromGame = fromGameMenu;
        if (menu != null && menu.isOpen()) menu.close(null);
        if (one() != null) one().close();
        if (pad) p.padToggle(); else p.open();
    }

    // The keys a menu took a press of, per controller (device << 32 | key): their release is the menu's too, also after it closed
    private static final HashSet<Long> taken = new HashSet<>();
    private static int heldDir = PadPanel.NONE, stickDir = PadPanel.NONE, repeats;
    private static Runnable repeat;

    /** GameActivity.dispatchKeyEvent: while the game menu is open it has the controller (D-pad and A, B); no pad key reaches the game. True when taken */
    static boolean menuKey(Activity a, KeyEvent e, boolean gamepad) {
        int k = e.getKeyCode();
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN, up = e.getAction() == KeyEvent.ACTION_UP;
        SidePanel shown = shownPanel(a);
        if (shown != null && gamepad && down && e.getRepeatCount() == 0 && k == keys(a)[0]) dev.peanuts.ui.MenuFeel.select(shown.findFocus());   // A in the quick menu
        long dk = (long) e.getDeviceId() << 32 | k;
        SidePanel o = one();
        if (gamepad && o != null && k != KeyEvent.KEYCODE_BACK) {   // the one menu (at the right or on the bottom screen): L and R over Game and the tabs
            if (down) taken.add(dk); else if (up) taken.remove(dk);
            // The bottom screen's window never has the keys' focus, so it does not hear that a key left touch mode: it leaves it here, or the focus would
            // move without its frame (SidePanel.focusMoved draws none in touch mode)
            if (down && o.isInTouchMode()) { View f = o.findFocus(); if (f != null) f.requestFocusFromTouch(); }
            Rail r = rails.get(o);
            if ((k == KeyEvent.KEYCODE_BUTTON_L1 || k == KeyEvent.KEYCODE_BUTTON_R1) && r != null) { if (down && e.getRepeatCount() == 0) r.step(k == KeyEvent.KEYCODE_BUTTON_L1 ? -1 : 1); return true; }
            o.padKey(e);
            return true;
        }
        if (gamepad && up && taken.remove(dk)) { if (PadPanel.ofKey(k) == heldDir) release(); return true; }
        if (menu == null || !menu.isOpen() || !gamepad || k == KeyEvent.KEYCODE_BACK) return false;
        if (down) taken.add(dk);
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
        SidePanel o = one();
        if (o != null && (e.getSource() & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK) return o.padMotion(e);
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

    /**
     * GameActivity.onWindowFocusChanged(false): a dialog (Button mapping, Share log's chooser) or another app has the keys now, so a release of a key the
     * menus took comes up there. What the menus kept of held keys goes: else the game's next press of that key would lose its release (the game holds it)
     */
    static void inputLost() {
        taken.clear();
        release();
        stickDir = PadPanel.NONE;
        if (holdRun != null) UI.removeCallbacks(holdRun);
        homeHeld = false;
        if (backRun != null) UI.removeCallbacks(backRun);
        backDown = false;
        backHeld = false;
    }

    private static void step(int dir) { menu.move(dir == PadPanel.UP, dir == PadPanel.DOWN, dir == PadPanel.LEFT, dir == PadPanel.RIGHT); }

    /** The quick menu while it is open (the panel's own key handling presses its rows), else null */
    private static SidePanel shownPanel(Activity a) {
        if (one() != null) return one();
        SidePanel p = panel(a);
        return p != null && p.isOpen() ? p : null;
    }

    /** The controller's confirm and back keys: the Switch's A and B in this game, as the panel takes them (PadPanel.confirmBack) */
    private static int[] keys(Activity a) {
        String id = String.valueOf(a.getIntent().getStringExtra("title_id"));
        return PadPanel.confirmBack(Prefs.padMap(a, id), 0);   // the game's own A and B, as GameActivity's padButtons (dev-0.1.9 has no Xbox label swap)
    }

    // ---- The game menu's rows ----

    /** A copy is running (backup's thread): the row ignores a press meanwhile and shows "Backing up", also in a menu opened again or on a recreated screen */
    private static final java.util.concurrent.atomic.AtomicBoolean backingUp = new java.util.concurrent.atomic.AtomicBoolean();
    /** The newest look-up of the last backup (showBackup, UI thread): an older one's answer, or one that ends after a copy began, is dropped */
    private static int lookup;
    /** Stop pressed while a copy runs: it stops once the copy is done (UI thread) */
    private static Runnable stopAfter;

    /** The Back up row's value as the game menu opens: "Backing up" while a copy runs, else the newest backup of this save, looked up off the UI thread */
    private static void showBackup(Activity a) {
        if (backupRow == null) return;
        if (backingUp.get()) { backupRow.value("Backing up", false); return; }
        int n = ++lookup;
        File save = GameActivity.runSave();
        new Thread(() -> {
            String v = lastBackup(a, save);
            UI.post(() -> { if (n == lookup && !backingUp.get() && backupRow != null) backupRow.value(v, false); });
        }, "peanuts-menu-backups").start();
    }

    /**
     * Back up the save while the game runs: the save the game started with (GameActivity.runSave, the player's), copied where Back up saves puts its copies
     * (SaveUi.target). This process holds the save's lock, so the copy runs as SaveStore's own under it, without the recovery (backupRunning): the game's start
     * recovered the save, and a marker beside it now is the host's own swap at a Commit, still being written, which only the host finishes. The host writes a save
     * only by that swap, and SaveStore checks the save and its markers around the copy, so a Commit in between fails the copy. Such a copy is tried again for up to
     * 20 seconds (a Commit to a slow SD card can take that long); a copy that failed otherwise, 3 times (a Commit may have moved a file away under it). One copy at
     * a time: a second would use the same temp folder, and its cleanup would delete the first's. The copy runs at background priority, behind the game's threads
     */
    private static void backup(Activity a) {
        if (backingUp.get()) return;
        File save = GameActivity.runSave();
        if (save == null) { backupRow.value("No save yet", false); return; }
        Game g = game(a);
        File to = SaveUi.target(a, g);
        boolean inApp = !to.equals(SaveUi.backups(g));   // no shared folder the app may write: Back up saves exports a zip from the app's own (SaveUi.exports)
        backingUp.set(true);
        lookup++;
        backupRow.value("Backing up", false);
        note(null, false);
        new Thread(() -> {
            String err = null;
            SaveStore.Backup b = null;
            try {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
                long until = android.os.SystemClock.uptimeMillis() + 20_000;
                for (int i = 0; b == null; i++) {
                    try { b = SaveStore.backupRunning(save, to, SaveUi.stamp()); err = null; }
                    catch (IOException | RuntimeException e) {
                        err = String.valueOf(e.getMessage());
                        if (err.contains("no save folder") || full(err)) break;
                        boolean saving = err.contains("changed while");   // a Commit met the copy: once it is done, the copy goes through
                        if (saving ? android.os.SystemClock.uptimeMillis() > until : i >= 2) break;
                        try { Thread.sleep(saving ? 1000 : 400); } catch (InterruptedException ie) { break; }
                    }
                }
            } catch (Throwable t) {   // never a row left at "Backing up"
                err = String.valueOf(t);
            } finally {
                SaveStore.Backup done = b;
                String why = done == null && err == null ? "no copy" : err;
                UI.post(() -> {
                    backingUp.set(false);
                    if (stopAfter != null) { Runnable s = stopAfter; stopAfter = null; s.run(); return; }
                    if (backupRow == null || menu == null) return;
                    if (done != null) {
                        backupRow.value("Just now", false);
                        if (inApp) note("Kept in the app's own storage. Back up saves on Home can copy it out as a .zip.", false);
                        return;
                    }
                    backupRow.value("Not backed up", true);
                    note(why.contains("no save folder") ? "This game has no save yet."
                            : full(why) ? "There is no room left for the backup. Free some space, then try again."
                            : why.contains("changed while") ? "The game kept saving while it was copied. Try again in a moment."
                            : "The backup did not finish. The save was only read, never changed.", true);
                });
                try {
                    if (why != null) Native.note("[RED] [peanuts] in-game save backup: " + why);
                    else Native.note("save backed up from the game menu: " + done.files + " files, " + done.bytes + " bytes");
                } catch (Throwable ignored) {
                    // the log only: the row has its answer already
                }
            }
        }, "peanuts-menu-backup").start();
    }

    /** A note under the game's rows: in the game menu, and in the one menus' Game (GameRows) */
    private static void note(String text, boolean bad) {
        if (menu != null) menu.note(text, bad);
        if (gameRows != null) gameRows.note(text, bad);
        if (rightRows != null) rightRows.note(text, bad);
    }

    /** The storage is full (ENOSPC): trying again does not help */
    private static boolean full(String err) { return err.contains("ENOSPC") || err.contains("No space left"); }

    /**
     * The newest backup of the running save, as "Today 14:02" (null: none): this user's (the backups folder is the game's, every user's), not the copy
     * a restore makes of the save it replaces, by the time in its name (when it was made)
     */
    private static String lastBackup(Activity a, File save) {
        try {
            File dir = SaveUi.target(a, game(a));
            String user = save == null ? null : SaveStore.userOf(save);
            long newest = 0;
            for (SaveStore.Backup b : SaveStore.list(dir)) {
                if (b.beforeRestore() || user != null && !user.equals(b.user)) continue;
                newest = Math.max(newest, made(b));
            }
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

    /** When a backup was made: the time its name starts with (SaveUi.stamp), else its folder's */
    private static long made(SaveStore.Backup b) {
        try {
            if (b.name.length() >= 15) {
                Date d = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).parse(b.name.substring(0, 15));
                if (d != null) return d.getTime();
            }
        } catch (java.text.ParseException ignored) {
            // an older name: the folder's time
        }
        return b.dir.lastModified();
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
        rails.put(p, new Rail(p.getContext(), sheet, tabs, rows, () -> panelBack(p), makingBottom));
        sheet.setClickable(true);   // a touch on the sheet's empty part (between the rail's tabs, the header) does not reach the scrim, which closes the menu
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
        Rail r = rails.get(p);
        if (r == null) return false;
        r.action(null, null, null);   // a tab's build sets its own again (ModsTab: All off)
        r.tabs(SidePanel.TAB_NAMES, tab, show);
        return true;
    }

    /** ModsPanel's tabs: their own action in the header (the Cheats tab's All off) */
    static void action(SidePanel p, String key, String label, Runnable run) { Rail r = rails.get(p); if (r != null) r.action(key, label, run); }

    /** SidePanel.showTab: a tab switched from the rail keeps the controller's focus on the rail */
    static boolean railFocused() { for (Rail r : rails.values()) if (r.focused()) return true; return false; }

    /** SidePanel.open: the sheet slides in (OPEN_MS, decelerating), the scrim fades in */
    static void slideIn(SidePanel p, View sheet) {
        dev.peanuts.ui.MenuFeel.open(sheet);
        android.view.WindowInsets w = p.getRootWindowInsets();   // a camera cutout at the right (a phone in landscape): the sheet's content clear of it
        android.view.DisplayCutout dc = w == null ? null : w.getDisplayCutout();
        int right = dc == null ? 0 : dc.getSafeInsetRight();
        if (sheet.getPaddingRight() != right) sheet.setPadding(sheet.getPaddingLeft(), sheet.getPaddingTop(), right, sheet.getPaddingBottom());
        View scrim = p.getChildAt(0);
        sheet.animate().cancel();
        if (p == bottomPanel) {   // up from the bottom edge, decelerating; the live stats behind it dim, and the game on the top screen a little
            bottomSheet(p, sheet);
            if (p.getContext() instanceof Activity) dimTop((Activity) p.getContext(), true);
            sheet.setTranslationX(0);
            sheet.setTranslationY(p.getResources().getDisplayMetrics().heightPixels);
            sheet.animate().translationY(0).setDuration(Look.UP_MS).setInterpolator(Look.OUT).withLayer().start();
            if (scrim != null && scrim != sheet) { scrim.animate().cancel(); scrim.setAlpha(0f); scrim.animate().alpha(1f).setDuration(Look.UP_MS).setInterpolator(Look.OUT).start(); }
            return;
        }
        sheet.setTranslationX(sheet.getLayoutParams().width);
        sheet.animate().translationX(0).setDuration(Look.OPEN_MS).setInterpolator(Look.OUT).withLayer().start();
        if (scrim != null && scrim != sheet) { scrim.animate().cancel(); scrim.setAlpha(0f); scrim.animate().alpha(1f).setDuration(Look.OPEN_MS).setInterpolator(Look.OUT).start(); }
        shownTab = -1;
    }

    /** SidePanel.close: the sheet slides out (CLOSE_MS, accelerating), the scrim fades; then gone (end) */
    static void slideOut(SidePanel p, View sheet, Runnable end) {
        dev.peanuts.ui.MenuFeel.back(sheet);
        if (p == bottomPanel || p == rightPanel) stopAfter = null;   // a Stop waiting for the backup goes with its menu
        fromGame = false;   // closed any way (B, a tap beside it, Home): opened again by the edge swipe, its B goes to the game
        View scrim = p.getChildAt(0);
        sheet.animate().cancel();
        if (p == bottomPanel) {   // back down, accelerating; then the window goes and the screen is given back
            if (p.getContext() instanceof Activity) dimTop((Activity) p.getContext(), false);
            sheet.animate().translationY(Math.max(sheet.getHeight(), 1)).setDuration(Look.DOWN_MS).setInterpolator(Look.IN).withLayer().withEndAction(() -> { end.run(); closeBottom(false); }).start();
            if (scrim != null && scrim != sheet) scrim.animate().alpha(0f).setDuration(Look.DOWN_MS).setInterpolator(Look.IN).start();
            return;
        }
        sheet.animate().translationX(sheet.getLayoutParams().width).setDuration(Look.CLOSE_MS).setInterpolator(Look.IN).withLayer().withEndAction(end).start();
        if (scrim != null && scrim != sheet) scrim.animate().alpha(0f).setDuration(Look.CLOSE_MS).setInterpolator(Look.IN).start();
    }

    /** SidePanel.padKey's back key, the header's back button: back to the game menu when that opened the quick menu, else to the game */
    static void panelBack(SidePanel p) {
        if (p == bottomPanel || p == rightPanel) { p.close(); return; }
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
        if (p == bottomPanel) bottomSheet(p, p.getChildAt(1));
        if ("General".equals(name)) {
            for (int i = box.getChildCount() - 1; i >= 0; i--) {
                View v = box.getChildAt(i);
                if (!(v instanceof TextView)) continue;
                String s = String.valueOf(((TextView) v).getText());
                if (s.equals("Pause the game") || s.equals("Stop the game") || s.equals("Back to the game") || s.startsWith("The game runs on behind this panel")) box.removeViewAt(i);
                else if (s.equalsIgnoreCase("Panel")) ((TextView) v).setText("Menu");
                else if (s.startsWith("Pause when the game comes back") && i > 0) { TextView h = head(p, "This game"); box.addView(h, i); ModsPanel.style(p.getContext(), h); }   // the panel's text size, as its own headings
            }
            feelSwitches(p, box);
            layoutSetting(p, box);
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

    // ---- One menu instead of two (Menu settings › Layout; two menus by default): at the right over the game, or on the bottom screen of a dual-screen
    // device such as the AYN Thor. Each is a second quick menu (upstream's SidePanel, the same rows) whose rail starts with Game, the game menu's rows ----

    private static SidePanel rightPanel;   // one menu at the right, in the game's window
    private static GameRows gameRows, rightRows;   // Game on the bottom screen's menu, on the right one
    private static List<GameMenu.Item> menuRows;   // the game menu's rows and Stop, for Game
    private static GameMenu.Item menuStop;
    private static Bitmap cover;
    private static String version;

    /** The one menu that is open (at the right or on the bottom screen), else null */
    private static SidePanel one() {
        if (bottomPanel != null && bottomPanel.isOpen()) return bottomPanel;
        if (rightPanel != null && rightPanel.isOpen()) return rightPanel;
        return null;
    }

    /** Whether Home, Back and − + open one menu instead of the game menu and the quick menu */
    static boolean oneMode(Activity a) { return dev.peanuts.PeanutsPrefs.menuLayout(a) != dev.peanuts.PeanutsPrefs.LAYOUT_TWO; }

    /** Whether the one menu is the bottom screen's: that layout and a second display now */
    static boolean bottomMode(Activity a) {
        if (dev.peanuts.PeanutsPrefs.menuLayout(a) != dev.peanuts.PeanutsPrefs.LAYOUT_BOTTOM) return false;
        android.hardware.display.DisplayManager dm = (android.hardware.display.DisplayManager) a.getSystemService(android.content.Context.DISPLAY_SERVICE);
        return dm != null && dev.peanuts.Companion.second(a, dm) != null;
    }

    /** Whether the one menu is at the right (that layout, or the bottom screen's without a second display now) */
    static boolean rightMode(Activity a) { return oneMode(a) && !bottomMode(a); }

    /** Opens the one menu on Game (onGame) or on the tab shown last; pad: a controller opened it, the focus shows at once */
    private static void openOne(Activity a, boolean onGame, boolean pad) { if (bottomMode(a)) openBottom(a, onGame, pad); else openRight(a, onGame, pad); }

    /**
     * SidePanel.open: with one menu at the right, what opens the quick menu (the swipe in from the right edge, the on-screen controls' button) opens that
     * menu on the tab shown last instead. True when it did
     */
    static boolean instead(SidePanel p) {
        if (p != panelOf || !(p.getContext() instanceof Activity) || !rightMode((Activity) p.getContext())) return false;
        openRight((Activity) p.getContext(), false, !p.isInTouchMode());
        return true;
    }

    /** Game's rows for one menu (host): the game menu's without Quick menu (the rail is there), Back to the game and Pause closing that menu */
    private static GameRows gameRows(Activity a, java.util.function.Supplier<SidePanel> host) {
        GameRows g = new GameRows(a, PanelLook.rowPx(Prefs.panelSize(a), a.getResources().getDisplayMetrics().density));
        g.onKeep = () -> stopAfter = null;   // Stop asked during a backup, then Keep playing
        g.game(a.getIntent().getStringExtra("name"), version, dev.peanuts.Companion.startedAt());
        if (cover != null) g.cover(cover);
        List<GameMenu.Item> some = new ArrayList<>();
        for (GameMenu.Item it : menuRows) {
            if ("Quick menu".equals(it.title())) continue;
            if ("Back to the game".equals(it.title())) some.add(new GameMenu.Item(it.icon(), it.title(), false, () -> { if (host.get() != null) host.get().close(); }));
            else if ("Pause the game".equals(it.title())) some.add(new GameMenu.Item(it.icon(), it.title(), false, () -> { if (host.get() != null) host.get().close(); if (a instanceof GameActivity) ((GameActivity) a).pause(); }));
            else some.add(it);
        }
        g.items(some, menuStop);
        return g;
    }

    /** The cover and the version, once read: the game menu has its own, Game's rows take them too */
    private static void cover(Bitmap b) { cover = b; if (gameRows != null) gameRows.cover(b); if (rightRows != null) rightRows.cover(b); }
    private static void version(String v) { version = v; if (gameRows != null) gameRows.version(v); if (rightRows != null) rightRows.version(v); }

    /** A one menu made: its rail with Game (frame takes makingBottom), the controller's buttons, the game's input released when it opens */
    private static SidePanel oneMenu(Activity a, GameRows g) {
        SidePanel p;
        makingBottom = g;
        try { p = new SidePanel(a, panelOf.rows()); } finally { makingBottom = null; }
        p.padButtons(keys(a));
        p.onOpen = panelOf.onOpen;
        return p;
    }

    /** A one menu slides in, on Game (onGame) or on the tab shown last; with the controller (pad) the focus on Game's first row or the panel's */
    private static void show(SidePanel p, GameRows g, boolean onGame, boolean pad) {
        g.refresh();
        if (p.getContext() instanceof Activity) showBackup((Activity) p.getContext());   // Back up the save's newest backup, as the game menu shows it
        if (pad) p.padToggle(); else p.open();
        Rail r = rails.get(p);
        if (onGame && r != null) {
            r.game();
            if (pad) p.post(() -> { View f = r.firstGameRow(); if (f != null) f.requestFocusFromTouch(); });
        }
    }

    /** Opens one menu at the right, over the game as the quick menu is (in the game's window, above the quick menu) */
    static void openRight(Activity a, boolean onGame, boolean pad) {
        if (a.isFinishing() || a.isDestroyed()) return;
        if (panelOf == null || !(panelOf.getParent() instanceof ViewGroup) || menuRows == null) return;
        if (rightPanel == null) {
            rightRows = gameRows(a, () -> rightPanel);
            rightPanel = oneMenu(a, rightRows);
            ViewGroup root = (ViewGroup) panelOf.getParent();
            root.addView(rightPanel, root.indexOfChild(panelOf) + 1, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        if (rightPanel.isOpen()) return;
        if (panelOf.isOpen()) panelOf.close();
        if (menu != null && menu.isOpen()) menu.close(null);
        if (bottomPanel != null && bottomPanel.isOpen()) bottomPanel.close();
        show(rightPanel, rightRows, onGame, pad);
    }

    /**
     * Opens the bottom screen's menu: its window on the second display (BottomScreen), the panel sliding up in it, on Game (onGame) or on the tab shown last.
     * pad: a controller opened it, the focus shows at once. Without a second display now, the one menu at the right opens instead
     */
    static void openBottom(Activity a, boolean onGame, boolean pad) {
        if (!resumed || a.isFinishing() || a.isDestroyed()) return;   // the game is not in front (the bottom screen's swipe while it is away)
        android.hardware.display.DisplayManager dm = (android.hardware.display.DisplayManager) a.getSystemService(android.content.Context.DISPLAY_SERVICE);
        android.view.Display d = dm == null ? null : dev.peanuts.Companion.second(a, dm);
        if (d == null || panelOf == null || menuRows == null) { openRight(a, onGame, pad); return; }
        if (bottomPanel == null) {
            gameRows = gameRows(a, () -> bottomPanel);
            bottomPanel = oneMenu(a, gameRows);
        }
        if (bottomPanel.isOpen()) return;
        if (panelOf.isOpen()) panelOf.close();
        if (menu != null && menu.isOpen()) menu.close(null);
        if (rightPanel != null && rightPanel.isOpen()) rightPanel.close();
        if (bottomScreen == null) {
            try {
                dev.peanuts.ingame.BottomScreen s = new dev.peanuts.ingame.BottomScreen(a, d, bottomPanel);
                s.setOnDismissListener(x -> { if (s == bottomScreen) bottomGone(); });   // the system took it (the display went): the menu goes with it
                bottomScreen = s;
                s.show();
            }
            catch (RuntimeException e) { Native.note("[RED] [peanuts] the bottom screen's menu could not open: " + e); bottomScreen = null; openRight(a, onGame, pad); return; }
        }
        show(bottomPanel, gameRows, onGame, pad);
    }

    private static View topDim;   // the game on the top screen, dimmed a little while the bottom screen's menu is open: it runs on, but is not being played

    /** The top screen's dim in (with the bottom menu's slide up) or out (with its slide down); a touch on it closes the menu, as a scrim's does */
    private static void dimTop(Activity a, boolean on) {
        if (panelOf == null || !(panelOf.getParent() instanceof ViewGroup)) return;
        if (topDim == null) {
            topDim = new View(a);
            topDim.setBackgroundColor(0x66000000);   // SidePanel's scrim
            topDim.setAlpha(0f);
            topDim.setVisibility(View.GONE);
            topDim.setFocusable(false);
            topDim.setOnClickListener(v -> { if (bottomPanel != null && bottomPanel.isOpen()) bottomPanel.close(); });
            ViewGroup root = (ViewGroup) panelOf.getParent();
            root.addView(topDim, root.indexOfChild(panelOf), new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        View d = topDim;
        d.animate().cancel();
        if (on) {
            d.setVisibility(View.VISIBLE);
            d.animate().alpha(1f).setDuration(Look.UP_MS).setInterpolator(Look.OUT).start();
        } else {
            d.animate().alpha(0f).setDuration(Look.DOWN_MS).setInterpolator(Look.IN).withEndAction(() -> { if (d.getAlpha() == 0f) d.setVisibility(View.GONE); }).start();
        }
    }

    /** The bottom screen's window goes (after the panel slid down, or at once: the game left the screen) */
    static void closeBottom(boolean now) {
        if (now && bottomPanel != null && bottomPanel.isOpen()) { bottomPanel.close(); }
        if (now && topDim != null) { topDim.animate().cancel(); topDim.setAlpha(0f); topDim.setVisibility(View.GONE); }
        if (bottomScreen == null) return;
        if (!now && bottomPanel != null && bottomPanel.isOpen()) return;
        dev.peanuts.ingame.BottomScreen s = bottomScreen;
        bottomScreen = null;
        s.release();
        try { s.dismiss(); } catch (RuntimeException ignored) { }
        dropBottom();
    }

    /**
     * The bottom panel goes with its window and is made anew for the next: SidePanel, its rail and the focus ticks listen to their view tree from their
     * constructors, before they are attached, and such listeners reach the first window only, so a panel moved into a second window would lose its focus
     * frame and its rail's steps
     */
    private static void dropBottom() {
        if (bottomPanel != null) rails.remove(bottomPanel);
        bottomPanel = null;
        gameRows = null;
    }

    /** The bottom screen's window was taken by the system (its display went) while the menu was up: the menu, its dim and the keys it held go */
    private static void bottomGone() {
        bottomScreen = null;
        if (topDim != null) { topDim.animate().cancel(); topDim.setAlpha(0f); topDim.setVisibility(View.GONE); }
        if (bottomPanel != null && bottomPanel.isOpen()) bottomPanel.close();
        dropBottom();
        inputLost();
    }

    /**
     * attach, on a game screen made again while the game runs (Don't keep activities, a font or theme change): the old screen's one menus, their rows, its
     * dim and the bottom screen's window go, and the keys the menus held; the new screen makes its own as they open. The rail of the new screen's quick
     * menu (made before attach, in SidePanel's constructor) stays
     */
    private static void forget(SidePanel keep) {
        closeBottom(true);
        bottomScreen = null;
        bottomPanel = null;
        rightPanel = null;
        gameRows = null;
        rightRows = null;
        topDim = null;
        cover = null;
        version = null;
        makingBottom = null;
        rails.keySet().removeIf(p -> p != keep);
        shownTab = -1;
        fromGame = false;
        stopAfter = null;
        leftHidden = null;
        resumed = false;
        inputLost();
    }

    /** The game's screen is in front (resumed): the bottom screen's swipe opens the menu only then */
    private static boolean resumed;
    /** The left strip hidden for one menu (oneMode), as last worked out (null: again, the layout changed) */
    private static Boolean leftHidden;

    /** The bottom sheet: the whole width of the bottom screen under a strip of what is behind it, its top corners round */
    private static void bottomSheet(SidePanel p, View sheet) {
        if (sheet == null) return;
        android.widget.FrameLayout.LayoutParams lp = (android.widget.FrameLayout.LayoutParams) sheet.getLayoutParams();
        int top = Look.dp(p.getContext(), 18);
        if (lp.width != ViewGroup.LayoutParams.MATCH_PARENT || lp.topMargin != top) {
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            lp.topMargin = top;
            lp.gravity = android.view.Gravity.BOTTOM;
            sheet.setLayoutParams(lp);
        }
        if (!(sheet.getBackground() instanceof android.graphics.drawable.GradientDrawable)) {
            float r = Look.dp(p.getContext(), 18);
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setColor(0xFF202124);
            bg.setCornerRadii(new float[] {r, r, r, r, 0, 0, 0, 0});
            sheet.setBackground(bg);
            sheet.setClipToOutline(true);
        }
    }

    /** General (the menu's own settings): Layout, two menus, one menu at the right, or (a device with a second screen) one menu on the bottom screen */
    private static void layoutSetting(SidePanel p, LinearLayout box) {
        Activity a = (Activity) p.getContext();
        TextView lh = head(p, "Layout");
        box.addView(lh);
        ModsPanel.style(a, lh);   // the panel's text size, as its own rows (laid adds these after SidePanel.look)
        boolean second = dev.peanuts.Companion.hasSecondDisplay(a);
        String[] names = {"Two menus", "One menu at the right", "Bottom screen"};   // the launcher's question's names (LayoutChoice); the line under them says more
        String[] notes = {
                "Home or Back opens the game menu at the left, held the quick menu at the right.",
                "Home or Back opens one menu at the right with Game first; held, on the tab shown last.",
                "Home or Back opens one menu on the bottom screen, and the game stays in full view on top."};
        android.widget.RadioGroup g = new android.widget.RadioGroup(a);
        int[] ids = new int[names.length];
        for (int i = 0; i < names.length; i++) {
            if (i == dev.peanuts.PeanutsPrefs.LAYOUT_BOTTOM && !second) continue;
            android.widget.RadioButton b = new android.widget.RadioButton(a);
            ids[i] = View.generateViewId();
            b.setId(ids[i]);
            b.setText(names[i]);
            g.addView(b);
        }
        int now = dev.peanuts.PeanutsPrefs.menuLayout(a);
        if (now == dev.peanuts.PeanutsPrefs.LAYOUT_BOTTOM && !second) now = dev.peanuts.PeanutsPrefs.LAYOUT_RIGHT;
        g.check(ids[now]);
        box.addView(g);
        ModsPanel.style(a, g);
        TextView n = new TextView(a);
        n.setText(notes[now]);
        n.setTextSize(12);
        n.setTextColor(android.graphics.Color.LTGRAY);
        box.addView(n);
        ModsPanel.style(a, n);
        g.setOnCheckedChangeListener((group, id) -> {
            for (int i = 0; i < ids.length; i++) if (ids[i] == id && id != 0) { dev.peanuts.PeanutsPrefs.menuLayout(a, i); n.setText(notes[i]); }
            leftHidden = null;   // the left strip at the next layout pass
            if (panelOf != null && panelOf.getParent() instanceof View) ((View) panelOf.getParent()).requestLayout();
            dev.peanuts.ingame.BottomMenu.changed();   // the stats page's grip shows for Bottom screen only
        });
    }
}
