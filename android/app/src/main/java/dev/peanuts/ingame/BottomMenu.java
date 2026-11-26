package dev.peanuts.ingame;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

/**
 * The second screen's side of the bottom screen's menu (dev.nxnative.android.InGamePanel opens it): the game's live stats page (dev.peanuts.ui.GameScreen,
 * StatsView) gets a faint grip at its bottom edge, which moves with the page's pixel shifter, and a swipe up from that edge opens the menu on Game; a tap
 * there still reaches the page.
 * Only while the layout (Menu settings › Layout) is Bottom screen.
 */
public final class BottomMenu {
    private BottomMenu() {}

    /** Opens the menu on Game (InGamePanel.attach sets it for the running game) */
    public static Runnable opener;

    public static boolean on(Context c) { return dev.peanuts.PeanutsPrefs.menuLayout(c) == dev.peanuts.PeanutsPrefs.LAYOUT_BOTTOM; }

    /** The grips on the second screen now (weak: a page that goes takes its grip), so they follow Layout's choice while the game runs (changed) */
    private static final java.util.Set<Grip> GRIPS = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());

    /** A grip, and whether its page wants it now (GameScreen: Now playing, not while the game starts or its stats show their own) */
    private static final class Grip extends View {
        boolean wanted = true;
        Grip(Context c) { super(c); }
    }

    /** The grip: 40 x 4 dp, the panel's row grey, at the bottom centre of its page; shown while the layout is Bottom screen */
    public static View grip(Context c) {
        Grip g = new Grip(c);
        GradientDrawable d = new GradientDrawable();
        d.setColor(0xFF3A3B3E);
        d.setCornerRadius(Look.dp(c, 2));
        g.setBackground(d);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Look.dp(c, 40), Look.dp(c, 4), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        lp.bottomMargin = Look.dp(c, 6);
        g.setLayoutParams(lp);
        GRIPS.add(g);
        show(g);
        return g;
    }

    /** Whether the grip's page wants it now (it shows only while the layout is Bottom screen too) */
    public static void want(View grip, boolean wanted) {
        if (!(grip instanceof Grip)) return;
        ((Grip) grip).wanted = wanted;
        show((Grip) grip);
    }

    private static void show(Grip g) { g.setVisibility(g.wanted && on(g.getContext()) ? View.VISIBLE : View.GONE); }

    /** Layout's choice changed (InGamePanel, the menu's own settings): the grips follow (the UI thread, which the second screen's pages share) */
    public static void changed() { for (Grip g : new java.util.ArrayList<>(GRIPS)) show(g); }

    // The swipe up: from the bottom edge's 28 dp, 32 dp up. The page's root watches the touches on their way to the page (intercept), and takes one only
    // once it is that swipe: a tap there goes on to the page as any other (it wakes the page). A touch nothing under it took comes to the root (touch)
    private static float downY = -1;

    /** The page's root's onInterceptTouchEvent: true once the touch is the swipe up (the page gets a cancel); the menu opens */
    public static boolean intercept(View root, MotionEvent e) { return swipe(root, e); }

    /** The page's root's onTouchEvent: a touch in the bottom edge that no view under it took, followed until it is the swipe or ends */
    public static boolean touch(View root, MotionEvent e) {
        boolean took = swipe(root, e);
        return took || downY >= 0;   // the edge's touch stays the root's while it may become the swipe
    }

    private static boolean swipe(View root, MotionEvent e) {
        float dp = root.getResources().getDisplayMetrics().density;
        int a = e.getActionMasked();
        if (a == MotionEvent.ACTION_DOWN) { downY = on(root.getContext()) && opener != null && e.getY() >= root.getHeight() - 28 * dp ? e.getRawY() : -1; return false; }
        if (a == MotionEvent.ACTION_MOVE && downY >= 0 && downY - e.getRawY() > 32 * dp) { downY = -1; if (opener != null) opener.run(); return true; }
        if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) downY = -1;
        return false;
    }
}
