package dev.peanuts.ingame;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * The quick menu's frame: upstream's side panel keeps building its tabs, and this turns its
 * sheet around. The tab bar becomes a dark rail of solid icons at the left (the menu's own settings at its bottom), the selected tab a peanut-brown block
 * across the rail that slides to the next one; the tab's rows get a header with a back button and the tab's name. With a controller, up and down on the
 * rail switch the tab at once, right goes into the rows, left from a row comes back to the selected tab.
 */
public final class Rail {
    private final Context c;
    private final LinearLayout tabs;   // upstream's tab bar, now the rail's items in tab order (SidePanel.tabs: getChildAt(tab) still finds a tab's item)
    private final FrameLayout box;     // the rail: the moving block under the items
    private final View block;
    private final TextView title;
    private final LinearLayout head;
    private final View content;   // the tab's rows beside the rail (the panel's scroll)
    private View action;   // the shown tab's own action at the right of the header (the Cheats tab's All off)
    private final List<View> items = new ArrayList<>();
    private String[] names = new String[0];
    private int selected = -1;
    private IntConsumer show;
    private boolean placed;

    /** Takes the panel's sheet (upstream: the tab bar over the rows) apart and puts it together as rail | (header over rows). back: the header's button */
    public Rail(Context c, LinearLayout sheet, LinearLayout tabs, View rows, Runnable back) {
        this.c = c;
        this.tabs = tabs;
        this.content = rows;
        sheet.removeAllViews();
        sheet.setOrientation(LinearLayout.HORIZONTAL);
        sheet.setBackgroundColor(Look.SHEET);

        box = new FrameLayout(c) {
            // Right from the rail goes into the rows beside it (the first one in sight), not to the header's Back (the nearer one by the default search)
            @Override public View focusSearch(View focused, int direction) {
                if (direction == View.FOCUS_RIGHT) { View r = firstRow(); if (r != null) return r; }
                return super.focusSearch(focused, direction);
            }
        };
        box.setBackgroundColor(Look.RAIL);
        block = new View(c);
        block.setBackgroundColor(Look.PEANUT);
        block.setVisibility(View.INVISIBLE);
        box.addView(block, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Look.dp(c, 50)));
        tabs.setOrientation(LinearLayout.VERTICAL);
        tabs.setBackground(null);
        tabs.setPadding(0, Look.dp(c, 8), 0, Look.dp(c, 8));
        box.addView(tabs, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        sheet.addView(box, new LinearLayout.LayoutParams(Look.dp(c, 60), ViewGroup.LayoutParams.MATCH_PARENT));
        View edge = new View(c);
        edge.setBackgroundColor(Look.RAIL_EDGE);
        sheet.addView(edge, new LinearLayout.LayoutParams(Math.max(1, Look.dp(c, 1)), ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout col = new LinearLayout(c) {
            // Left from the rows comes back to the selected tab on the rail, not to the header's Back above them (the nearer one by the default search)
            @Override public View focusSearch(View focused, int direction) {
                if (direction == View.FOCUS_LEFT && focused != null && inside(focused, rows) && selected >= 0 && selected < items.size()) return items.get(selected);
                return super.focusSearch(focused, direction);
            }
        };
        col.setOrientation(LinearLayout.VERTICAL);
        head = new LinearLayout(c);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(Look.dp(c, 12), Look.dp(c, 10), Look.dp(c, 12), Look.dp(c, 2));
        ImageView b = new ImageView(c);
        Drawable bd = Look.icon(c, "back", Look.Ink.MENU);
        if (bd != null) b.setImageDrawable(bd);
        b.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int p = Look.dp(c, 7);
        b.setPadding(p, p, p, p);
        b.setBackground(Look.round(Look.ROW, Look.dp(c, 8)));
        b.setFocusable(true);
        b.setClickable(true);
        b.setDefaultFocusHighlightEnabled(false);   // the panel's yellow frame alone shows the focus (no grey veil over the button)
        b.setContentDescription("Back");
        b.setOnClickListener(v -> back.run());
        head.addView(b, new LinearLayout.LayoutParams(Look.dp(c, 36), Look.dp(c, 32)));
        title = new TextView(c);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        title.setTextColor(Look.TEXT);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setSingleLine(true);
        title.setPadding(Look.dp(c, 12), 0, 0, 0);
        head.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        col.addView(head);
        col.addView(rows, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        sheet.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));

        // Up and down on the rail switch the tab; left from the rows comes back to the selected tab only (the others take no focus then)
        sheet.getViewTreeObserver().addOnGlobalFocusChangeListener((old, now) -> {
            int i = items.indexOf(now);
            if (i >= 0) {
                focusable(true);
                if (i != selected && show != null) show.accept(i);
            } else if (now != null && inside(now, sheet)) focusable(false);
        });
    }

    /** The first row in sight beside the rail: where Right from the rail goes */
    public View firstRow() { return firstIn(content); }

    /** The first focusable in (top to bottom) that is mostly in sight, else the first at all (a track's chosen choice); null when there is none */
    static View firstIn(View in) {
        if (in == null || in.getVisibility() != View.VISIBLE) return null;
        ArrayList<View> all = new ArrayList<>();
        in.addFocusables(all, View.FOCUS_DOWN);
        android.graphics.Rect r = new android.graphics.Rect();
        for (View v : all) if (v.isShown() && v.getGlobalVisibleRect(r) && r.height() * 2 > v.getHeight()) return dev.peanuts.ui.PanelPolish.landing(v);
        for (View v : all) if (v.isShown()) return dev.peanuts.ui.PanelPolish.landing(v);
        return null;
    }

    private static boolean inside(View v, View in) {
        for (Object p = v; p instanceof View; p = ((View) p).getParent()) if (p == in) return true;
        return false;
    }

    private void focusable(boolean all) {
        for (int k = 0; k < items.size(); k++) items.get(k).setFocusable(all || k == selected);
    }

    /**
     * The shown tab's own action at the right of its header, as a console's menus show one: the controller's button for it and its name ("X All off"),
     * a tap does it too. Not a focus stop: the button is the controller's way. Null label: none (each tab's build starts without one)
     */
    public void action(String key, String label, Runnable run) {
        if (action != null) { head.removeView(action); action = null; }
        if (label == null) return;
        LinearLayout a = new LinearLayout(c);
        a.setOrientation(LinearLayout.HORIZONTAL);
        a.setGravity(Gravity.CENTER_VERTICAL);
        a.setPadding(Look.dp(c, 5), Look.dp(c, 4), Look.dp(c, 14), Look.dp(c, 4));
        a.setBackground(Look.round(Look.ROW, Look.dp(c, 999)));
        TextView k = new TextView(c);
        k.setText(key);
        k.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        k.setTypeface(Typeface.DEFAULT_BOLD);
        k.setTextColor(Look.SHEET | 0xFF000000);
        k.setGravity(Gravity.CENTER);
        k.setIncludeFontPadding(false);
        android.graphics.drawable.GradientDrawable dot = new android.graphics.drawable.GradientDrawable();
        dot.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        dot.setColor(Look.TEXT);
        k.setBackground(dot);
        a.addView(k, new LinearLayout.LayoutParams(Look.dp(c, 24), Look.dp(c, 24)));
        TextView t = new TextView(c);
        t.setText(label);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        t.setTextColor(Look.TEXT);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setSingleLine(true);
        t.setPadding(Look.dp(c, 8), 0, 0, 0);
        a.addView(t);
        a.setFocusable(false);
        a.setClickable(true);
        a.setContentDescription(label);
        a.setOnClickListener(v -> run.run());
        head.addView(a, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        action = a;
    }

    /** Whether the controller's focus is on the rail (a tab switched from there keeps it there) */
    public boolean focused() {
        for (View v : items) if (v.isFocused()) return true;
        return false;
    }

    /**
     * The rail for these tabs (upstream's names; icons and titles by name), tab selected; show switches the panel to a tab. Built once for a set of tabs, then
     * only the selection moves (a rebuild would take the focus away from the item the controller is on)
     */
    public void tabs(String[] tabNames, int tab, IntConsumer show) {
        this.show = show;
        if (!java.util.Arrays.equals(tabNames, names)) {
            names = tabNames.clone();
            items.clear();
            tabs.removeAllViews();
            selected = -1;
            placed = false;
            int h = Look.dp(c, 50);
            for (int i = 0; i < names.length; i++) {
                int k = i;
                FrameLayout item = new FrameLayout(c);
                ImageView ic = new ImageView(c);
                ic.setScaleType(ImageView.ScaleType.CENTER);
                item.addView(ic, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                item.setFocusable(true);
                item.setClickable(true);
                item.setDefaultFocusHighlightEnabled(false);   // the yellow frame alone, over the peanut block
                item.setOnClickListener(v -> { if (this.show != null && k != selected) this.show.accept(k); });
                item.setContentDescription(titleOf(names[i]) + " tab, " + (i + 1) + " of " + names.length);
                items.add(item);
                if (i == names.length - 1) {   // the menu's own settings, at the bottom of the rail
                    FrameLayout rest = new FrameLayout(c);
                    rest.addView(item, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h, Gravity.BOTTOM));
                    tabs.addView(rest, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
                } else {
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h);
                    lp.bottomMargin = Look.dp(c, 2);
                    tabs.addView(item, lp);
                }
            }
            // Up and down stay on the rail (the next item by id): the nearest focusable below the last tab would be a row of the content, since the
            // settings sit at the rail's bottom
            for (View v : items) v.setId(View.generateViewId());
            for (int i = 0; i < items.size(); i++) {
                items.get(i).setNextFocusUpId(items.get(Math.max(0, i - 1)).getId());
                items.get(i).setNextFocusDownId(items.get(Math.min(items.size() - 1, i + 1)).getId());
            }
        }
        select(tab);
    }

    private void select(int tab) {
        int was = selected;
        selected = tab;
        for (int i = 0; i < items.size(); i++) {
            ImageView ic = (ImageView) ((FrameLayout) items.get(i)).getChildAt(0);
            Drawable d = Look.icon(c, iconOf(names[i]), i == tab ? Look.Ink.SELECTED : Look.Ink.REST);
            if (d != null) ic.setImageDrawable(d);
            items.get(i).setSelected(i == tab);
        }
        if (!focused()) focusable(false);
        title.setText(titleOf(tab >= 0 && tab < names.length ? names[tab] : ""));
        if (tab >= 0 && tab < items.size()) moveBlock(was >= 0 && placed);
    }

    /** The block to the selected item: sliding (BLOCK_MS) from where it was, or placed at once (the first time, or before the rail is laid out) */
    private void moveBlock(boolean slide) {
        View it = items.get(selected);
        Runnable go = () -> {
            int y = it.getTop() + (it.getParent() != tabs ? ((View) it.getParent()).getTop() : 0) + tabs.getTop();
            block.setVisibility(View.VISIBLE);
            block.animate().cancel();
            if (slide && placed) block.animate().translationY(y).setDuration(Look.BLOCK_MS).setInterpolator(Look.OUT).start();
            else block.setTranslationY(y);
            placed = true;
        };
        if (it.getHeight() > 0) go.run(); else it.post(go);
    }

    /** The solid icon of a tab, by upstream's name (dev.nxnative.android.ModsPanel's names) */
    public static String iconOf(String tab) {
        switch (tab) {
            case "Performance": return "bolt";
            case "Display": return "display";
            case "Cheats": return "cheats";
            case "Mods": return "mods";
            case "Controls": return "controller";
            default: return "settings";
        }
    }

    /** A tab's title in the header ("General", the menu's own settings, reads Menu settings) */
    public static String titleOf(String tab) { return "General".equals(tab) ? "Menu settings" : tab; }
}
