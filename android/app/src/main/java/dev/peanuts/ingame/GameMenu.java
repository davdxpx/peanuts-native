package dev.peanuts.ingame;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Outline;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import dev.peanuts.ui.MenuFeel;

import java.util.ArrayList;
import java.util.List;

/**
 * The game menu: everything about this session, on a sheet that slides in from the left over the
 * running game. A header with the game's cover, name, version and how long this session has run; the rows (Back to the game, Pause the game, Quick menu,
 * Back up the save, Button mapping, Share log); Stop the game set apart at the bottom, which asks once, inline. The header and Stop stay while the rows
 * scroll on a short screen. The controller moves a selection (up and down, A, B); a touch presses a row. The caller (dev.nxnative.android.InGamePanel)
 * gives the rows their work and routes the keys.
 */
public final class GameMenu extends FrameLayout {

    /** One row: its solid icon, title, a value at its end (or a chevron), and what it does */
    public static final class Item {
        final String icon, title;
        final boolean chevron;
        final Runnable run;
        View row;
        // The value at the row's end, in each menu that shows the row (the game menu, the one menus' Game): weak, so a menu made anew (the bottom
        // screen's, for each of its windows) lets the old one's go
        final List<java.lang.ref.WeakReference<TextView>> values = new ArrayList<>();
        String valueText;
        boolean valueBad;

        public Item(String icon, String title, boolean chevron, Runnable run) { this.icon = icon; this.title = title; this.chevron = chevron; this.run = run; }

        public String icon() { return icon; }
        public String title() { return title; }
        public boolean chevron() { return chevron; }
        public void run() { if (run != null) run.run(); }
        /** A value view of another menu that shows this row (GameRows) */
        public void bind(TextView v) { values.add(new java.lang.ref.WeakReference<>(v)); value(valueText, valueBad); }

        public String valueText() { return valueText; }

        /** The value at the row's end (null: none); bad shows it in the colour of something that did not finish */
        public void value(String v, boolean bad) {
            valueText = v; valueBad = bad;
            for (java.util.Iterator<java.lang.ref.WeakReference<TextView>> i = values.iterator(); i.hasNext(); ) {
                TextView value = i.next().get();
                if (value == null) { i.remove(); continue; }
                value.setText(v == null ? "" : v);
                value.setTextColor(bad ? Look.BAD : Look.QUIET);
                value.setVisibility(v == null || v.isEmpty() ? GONE : VISIBLE);
            }
        }
    }

    private final View scrim;
    private final LinearLayout sheet, list;
    private final ScrollView scroll;
    private final ImageView cover;
    private final TextView name, version, time, note;
    private final FrameLayout bottom;
    private final List<Item> items = new ArrayList<>();
    private int widthPx;
    private int rowPx;
    /** A close whose then (the row's action) has not run yet: its slide-out still runs, so an opening meanwhile waits (open) */
    private Runnable closing;
    private float text;
    private Item stop;
    private View stopRow;
    private LinearLayout ask;
    private TextView keep, stopNow;
    private int sel;            // the selected row (items, then Stop at items.size())
    private int askSel;         // inline Stop: 0 Keep playing, 1 Stop
    private boolean open, asking, pad;
    private java.util.function.LongSupplier startedAt = () -> 0;   // read at each opening: the game's start is known only after the menu is made
    /** After it has closed (gone) */
    public Runnable onClosed;
    /** As it starts to close (a reopen during the slide-out cancels it, and with it onClosed): what the menu held of the keys and a waiting Stop go */
    public Runnable onClosing;
    /** Keep playing, after Stop was asked (a Stop that waits for something, InGamePanel's backup, no longer stops) */
    public Runnable onKeep;

    public GameMenu(Context c, int widthPx, int rowPx, float text) {
        super(c);
        this.widthPx = widthPx;
        this.rowPx = Math.max(rowPx, Look.dp(c, 40));
        this.text = text;
        scrim = new View(c);
        scrim.setBackgroundColor(Look.SCRIM);
        scrim.setOnClickListener(v -> close(null));
        scrim.setContentDescription("Back to the game");
        addView(scrim, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        sheet = new LinearLayout(c);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setBackgroundColor(Look.SHEET);
        sheet.setElevation(Look.dp(c, 16));
        sheet.setPadding(Look.dp(c, 10), Look.dp(c, 12), Look.dp(c, 10), Look.dp(c, 10));
        sheet.setClickable(true);   // a touch on the sheet's empty part does not reach the scrim or the game
        addView(sheet, new LayoutParams(widthPx, LayoutParams.MATCH_PARENT, Gravity.START));

        // The header: cover, name, version and this session's time
        LinearLayout head = new LinearLayout(c);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(Look.dp(c, 4), Look.dp(c, 2), Look.dp(c, 4), Look.dp(c, 10));
        cover = new ImageView(c);
        cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        cover.setBackground(Look.round(Look.CHIP, Look.dp(c, 12)));
        cover.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), Look.dp(c, 12)); }
        });
        cover.setClipToOutline(true);
        int cs = Look.dp(c, 52);
        head.addView(cover, new LinearLayout.LayoutParams(cs, cs));
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Look.dp(c, 12), 0, 0, 0);
        name = label(c, 16, Look.TEXT, Typeface.BOLD);
        name.setMaxLines(2);
        name.setEllipsize(TextUtils.TruncateAt.END);
        col.addView(name);
        LinearLayout line = new LinearLayout(c);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.setPadding(0, Look.dp(c, 5), 0, 0);
        version = label(c, 12, Look.TEXT, Typeface.NORMAL);
        version.setBackground(Look.round(Look.CHIP, Look.dp(c, 999)));
        version.setPadding(Look.dp(c, 9), Look.dp(c, 3), Look.dp(c, 9), Look.dp(c, 3));
        version.setVisibility(GONE);
        version.setSingleLine(true);
        version.setEllipsize(TextUtils.TruncateAt.END);
        version.setMaxWidth(Look.dp(c, 120));   // a long version ("1.0.1 (build 2025.11.03)") leaves the time its room
        line.addView(version);
        time = label(c, 13, Look.QUIET, Typeface.NORMAL);
        time.setSingleLine(true);
        time.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        tl.leftMargin = Look.dp(c, 8);
        line.addView(time, tl);
        col.addView(line);
        head.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        sheet.addView(head);

        // The rows, scrolling between the header and Stop
        scroll = new ScrollView(c);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setFadingEdgeLength(Look.dp(c, 24));
        scroll.setVerticalFadingEdgeEnabled(true);
        list = new LinearLayout(c);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        sheet.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        note = label(c, 12, Look.TAN, Typeface.NORMAL);
        note.setPadding(Look.dp(c, 8), Look.dp(c, 4), Look.dp(c, 8), Look.dp(c, 4));
        note.setVisibility(GONE);
        sheet.addView(note);

        // Stop, set apart
        View hair = new View(c);
        hair.setBackgroundColor(Look.HAIRLINE);
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Look.dp(c, 1)));
        hl.topMargin = hl.bottomMargin = Look.dp(c, 6);
        sheet.addView(hair, hl);
        bottom = new FrameLayout(c);
        sheet.addView(bottom, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // The selection is the menu's own (move, confirm): nothing in it takes Android's focus. The menu itself takes it when a controller opens it, which
        // leaves touch mode at once, so the framework does not eat the first D-pad press to leave it (and give the focus to a scrolling list)
        scroll.setFocusable(false);
        setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
        setFocusable(true);
        setDefaultFocusHighlightEnabled(false);
        setVisibility(GONE);
    }

    TextView label(Context c, float sp, int color, int style) {
        TextView t = new TextView(c);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp * text);
        t.setTextColor(color);
        t.setTypeface(style == Typeface.BOLD ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        t.setIncludeFontPadding(false);
        return t;
    }

    /** The game: its name, its version (null until read) and when this session started (SystemClock.elapsedRealtime, 0 unknown; asked at each opening) */
    public void game(String gameName, String ver, java.util.function.LongSupplier started) {
        name.setText(gameName == null ? "" : gameName);
        version(ver);
        startedAt = started == null ? () -> 0 : started;
    }

    public void version(String ver) {
        version.setText(ver == null ? "" : ver);
        version.setVisibility(ver == null || ver.isEmpty() ? GONE : VISIBLE);
    }

    public void cover(Bitmap b) { if (b != null) cover.setImageBitmap(b); }

    /** The rows, top to bottom, and Stop */
    public void items(List<Item> rows, Item stopItem) {
        items.clear();
        list.removeAllViews();
        for (Item it : rows) {
            items.add(it);
            it.row = row(it);
            list.addView(it.row);
        }
        stop = stopItem;
        stopRow = row(stopItem);
        bottom.removeAllViews();
        bottom.addView(stopRow);
        sel = 0;
        paint();
    }

    private View row(Item it) {
        Context c = getContext();
        LinearLayout r = new LinearLayout(c);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setMinimumHeight(rowPx);
        r.setPadding(Look.dp(c, 8), 0, Look.dp(c, 8), 0);
        ImageView ic = new ImageView(c);
        Drawable d = Look.icon(c, it.icon, Look.Ink.MENU);
        if (d != null) ic.setImageDrawable(d);
        ic.setScaleType(ImageView.ScaleType.CENTER);
        int is = Look.dp(c, 30);
        r.addView(ic, new LinearLayout.LayoutParams(is, is));
        TextView t = label(c, 15, Look.TEXT, Typeface.NORMAL);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setText(it.title);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setPadding(Look.dp(c, 10), 0, Look.dp(c, 8), 0);
        r.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView value = label(c, 13, Look.QUIET, Typeface.NORMAL);
        value.setSingleLine(true);
        value.setEllipsize(TextUtils.TruncateAt.END);
        value.setMaxWidth(Math.round(widthPx * 0.42f));   // a large font: the row's name keeps the rest
        r.addView(value);
        it.bind(value);
        if (it.chevron) {
            ImageView ch = new ImageView(c);
            Drawable cd = c.getDrawable(dev.nxnative.android.R.drawable.ic_chevron);   // the launcher's own (PUi.Setting.chevron)
            if (cd != null) { cd = cd.mutate(); cd.setTint(Look.QUIET); ch.setImageDrawable(cd); }
            int chs = Look.dp(c, 18);
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(chs, chs);
            cl.leftMargin = Look.dp(c, 4);
            r.addView(ch, cl);
        }
        r.setClickable(true);
        r.setContentDescription(it.title);
        r.setOnClickListener(v -> { if (!open) return; pad = false; sel = indexOf(it); paint(); press(it); });   // closed: a second tap during the slide-out runs nothing twice
        return r;
    }

    private int indexOf(Item it) { int i = items.indexOf(it); return i >= 0 ? i : items.size(); }

    private void press(Item it) {
        if (it == stop) { askStop(); return; }
        if (it.run != null) it.run.run();
    }

    // ---- Stop asks once, inline: Keep playing (the safe choice first) or Stop ----

    private void askStop() {
        Context c = getContext();
        asking = true;
        askSel = 0;
        ask = new LinearLayout(c);
        ask.setOrientation(LinearLayout.VERTICAL);
        ask.setPadding(Look.dp(c, 8), Look.dp(c, 4), Look.dp(c, 8), Look.dp(c, 2));
        TextView q = label(c, 15, Look.TEXT, Typeface.BOLD);
        q.setText("Stop the game?");
        ask.addView(q);
        TextView w = label(c, 13, Look.QUIET, Typeface.NORMAL);
        w.setText("Unsaved progress is lost. The log stays for Share log.");
        w.setPadding(0, Look.dp(c, 4), 0, Look.dp(c, 8));
        ask.addView(w);
        LinearLayout b = new LinearLayout(c);
        b.setOrientation(LinearLayout.HORIZONTAL);
        keep = button(c, "Keep playing", v -> { pad = false; cancelStop(); });
        stopNow = button(c, "Stop", v -> { pad = false; if (stop.run != null) stop.run.run(); });
        stopNow.setTextColor(Look.BAD);
        LinearLayout.LayoutParams kl = new LinearLayout.LayoutParams(0, rowPx, 1);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(0, rowPx, 1);
        sl.leftMargin = Look.dp(c, 8);
        b.addView(keep, kl);
        b.addView(stopNow, sl);
        ask.addView(b);
        bottom.removeAllViews();
        bottom.addView(ask);
        ask.setAlpha(0f);
        ask.animate().alpha(1f).setDuration(Look.TAB_IN_MS).setInterpolator(Look.OUT);
        paint();
    }

    private TextView button(Context c, String s, View.OnClickListener l) {
        TextView t = label(c, 14, Look.TEXT, Typeface.NORMAL);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setText(s);
        t.setGravity(Gravity.CENTER);
        t.setBackground(Look.round(Look.ROW, Look.dp(c, 10)));
        t.setClickable(true);
        t.setOnClickListener(l);
        return t;
    }

    private void cancelStop() {
        if (onKeep != null) onKeep.run();
        asking = false;
        bottom.removeAllViews();
        bottom.addView(stopRow);
        paint();
    }

    // ---- The selection ----

    /** The selected row's frame (only while a controller is in use); the inline question's buttons */
    private void paint() {
        Context c = getContext();
        for (int i = 0; i <= items.size(); i++) {
            View r = i < items.size() ? items.get(i).row : stopRow;
            if (r != null) r.setBackground(pad && !asking && i == sel ? Look.focus(c, 12) : null);
        }
        if (asking && keep != null) {
            keep.setBackground(pad && askSel == 0 ? withRing(c) : Look.round(Look.ROW, Look.dp(c, 10)));
            stopNow.setBackground(pad && askSel == 1 ? withRing(c) : Look.round(Look.ROW, Look.dp(c, 10)));
        }
    }

    private Drawable withRing(Context c) {
        android.graphics.drawable.GradientDrawable d = Look.round(Look.ROW, Look.dp(c, 10));
        d.setStroke(Math.round(2.5f * c.getResources().getDisplayMetrics().density), Look.YELLOW);
        return d;
    }

    private void reveal() {
        View r = sel < items.size() ? items.get(sel).row : null;
        if (r == null) return;
        r.post(() -> r.requestRectangleOnScreen(new Rect(0, -Look.dp(getContext(), 8), r.getWidth(), r.getHeight() + Look.dp(getContext(), 8)), false));
    }

    /** A controller direction (dev.nxnative.android.PadPanel's UP, DOWN, LEFT, RIGHT) */
    public void move(boolean up, boolean down, boolean left, boolean right) {
        boolean was = pad;
        pad = true;
        if (!was) { paint(); return; }   // the first press only shows where the selection is
        if (asking) {
            int was2 = askSel;
            if (left) askSel = 0; else if (right) askSel = 1;
            if (askSel != was2) MenuFeel.move(this); else if (left || right) MenuFeel.edge(this);
            paint();
            return;
        }
        int from = sel;
        if (up && sel > 0) sel--;
        else if (down && sel < items.size()) sel++;
        if (sel != from) MenuFeel.move(this); else if (up || down) MenuFeel.edge(this);
        paint();
        reveal();
    }

    /** The controller's A */
    public void confirm() {
        boolean was = pad;
        pad = true;
        if (!was) { paint(); return; }
        MenuFeel.select(this);
        if (asking) {
            if (askSel == 0) cancelStop(); else if (stop.run != null) stop.run.run();
            return;
        }
        press(sel < items.size() ? items.get(sel) : stop);
    }

    /** The controller's B: the inline question goes, else the menu closes */
    public void back() {
        MenuFeel.back(this);
        if (asking) { cancelStop(); return; }
        close(null);
    }

    /** A line under the rows (a backup that did not finish), null to hide it */
    public void note(String s, boolean bad) {
        note.setText(s == null ? "" : s);
        note.setTextColor(bad ? Look.BAD : Look.TAN);
        note.setVisibility(s == null ? GONE : VISIBLE);
    }

    // ---- Open and close ----

    public boolean isOpen() { return open; }

    /** The selection on a row (it): where the player comes back to, as from the quick menu to its row */
    public void at(Item it) {
        int i = items.indexOf(it);
        if (i < 0 || !open) return;
        sel = i;
        paint();
    }

    /** Slides in from the left (OPEN_MS, decelerating) while the scrim fades in; withPad: a controller opened it, the selection shows at once */
    public void open(boolean withPad) {
        if (open || closing != null) return;   // a row's action runs once the menu is gone (close): an opening in those few ms would cancel the slide-out and the action with it
        open = true;
        pad = withPad;
        MenuFeel.open(this);
        asking = false;
        if (stopRow != null && stopRow.getParent() == null) { bottom.removeAllViews(); bottom.addView(stopRow); }
        sel = 0;
        note(null, false);
        clearCutout();
        time.setText(played());
        paint();
        scroll.scrollTo(0, 0);
        setVisibility(VISIBLE);
        if (withPad) post(() -> { if (open) requestFocusFromTouch(); });
        sheet.animate().cancel();
        scrim.animate().cancel();
        sheet.setTranslationX(-widthPx);
        scrim.setAlpha(0f);
        sheet.animate().translationX(0).setDuration(Look.OPEN_MS).setInterpolator(Look.OUT).withLayer().start();
        scrim.animate().alpha(1f).setDuration(Look.OPEN_MS).setInterpolator(Look.OUT).start();
    }

    /** Slides out to the left (CLOSE_MS, accelerating); then runs after it is gone, and onClosed */
    public void close(Runnable then) {
        if (!open) { if (then != null) then.run(); return; }
        open = false;
        if (onClosing != null) onClosing.run();
        clearFocus();
        sheet.animate().cancel();
        scrim.animate().cancel();
        Runnable done = () -> {
            if (!open) setVisibility(GONE);
            if (then != null) then.run();
            if (onClosed != null) onClosed.run();
        };
        if (then != null) closing = done;
        scrim.animate().alpha(0f).setDuration(Look.CLOSE_MS).setInterpolator(Look.IN).start();
        sheet.animate().translationX(-widthPx).setDuration(Look.CLOSE_MS).setInterpolator(Look.IN).withLayer().withEndAction(() -> {
            if (closing == done) closing = null;
            done.run();
        }).start();
    }

    /**
     * A phone in landscape with its camera cutout at the left (the game draws under it): the sheet's content starts clear of it, as the game's overlay
     * does (GameActivity.insetOverlay)
     */
    private void clearCutout() {
        android.view.WindowInsets w = getRootWindowInsets();
        android.view.DisplayCutout dc = w == null ? null : w.getDisplayCutout();
        int left = dc == null ? 0 : dc.getSafeInsetLeft(), base = Look.dp(getContext(), 10);
        if (sheet.getPaddingLeft() != base + left) sheet.setPadding(base + left, sheet.getPaddingTop(), sheet.getPaddingRight(), sheet.getPaddingBottom());
    }

    /**
     * Menu settings' Size and Text size, changed while the game runs (the quick menu's General): the rows are made again in the new size and the header's
     * texts scaled, at the next opening (InGamePanel.openGame); only while closed
     */
    public void look(int rowPx, float text) {
        int row = Math.max(rowPx, Look.dp(getContext(), 40));
        if (open || row == this.rowPx && text == this.text) return;
        float k = text / this.text;
        this.rowPx = row;
        this.text = text;
        for (TextView t : new TextView[] {name, version, time, note}) t.setTextSize(TypedValue.COMPLEX_UNIT_PX, t.getTextSize() * k);
        if (stop != null) items(new ArrayList<>(items), stop);
    }

    /** The sheet's width, worked out from the screen at each opening (InGamePanel), so it follows a turn of a large screen; only while closed */
    public void width(int px) {
        if (open || px <= 0 || px == widthPx) return;
        widthPx = px;
        android.view.ViewGroup.LayoutParams lp = sheet.getLayoutParams();
        lp.width = px;
        sheet.setLayoutParams(lp);
        sheet.setTranslationX(-px);
    }

    /** "Playing for 42 min", from when this session started */
    private String played() {
        long t = startedAt.getAsLong();
        if (t <= 0) return "";
        long min = (android.os.SystemClock.elapsedRealtime() - t) / 60000;
        if (min < 1) return "Just started";
        if (min < 60) return "Playing for " + min + " min";
        return "Playing for " + (min / 60) + " h" + (min % 60 > 0 ? " " + (min % 60) + " min" : "");
    }
}
