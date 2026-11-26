package dev.peanuts.ingame;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

/**
 * The second screen's side of the bottom screen's menu (dev.nxnative.android.InGamePanel opens it): the game's live stats page (dev.peanuts.ui.GameScreen,
 * StatsView) gets a faint grip at its bottom edge, which moves with the page's pixel shifter, and a swipe up from that edge opens the menu on Game.
 * Only while the layout (Menu settings › Layout) is Bottom screen.
 */
public final class BottomMenu {
    private BottomMenu() {}

    /** Opens the menu on Game (InGamePanel.attach sets it for the running game) */
    public static Runnable opener;

    public static boolean on(Context c) { return dev.peanuts.PeanutsPrefs.menuLayout(c) == dev.peanuts.PeanutsPrefs.LAYOUT_BOTTOM; }

    /** The grip: 40 x 4 dp, the panel's row grey, at the bottom centre of the stats page's shifting content */
    public static View grip(Context c) {
        View g = new View(c);
        GradientDrawable d = new GradientDrawable();
        d.setColor(0xFF3A3B3E);
        d.setCornerRadius(Look.dp(c, 2));
        g.setBackground(d);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Look.dp(c, 40), Look.dp(c, 4), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        lp.bottomMargin = Look.dp(c, 6);
        g.setLayoutParams(lp);
        return g;
    }

    /** The bottom edge's strip (28 dp): a swipe up of 32 dp from it opens the menu; a tap goes on to the page under it (which wakes it) */
    public static View edge(Context c) {
        View s = new View(c);
        float dp = c.getResources().getDisplayMetrics().density;
        float[] downY = {-1};
        s.setOnTouchListener((v, e) -> {
            int a = e.getActionMasked();
            if (a == MotionEvent.ACTION_DOWN) { downY[0] = e.getRawY(); return true; }
            if (a == MotionEvent.ACTION_MOVE && downY[0] >= 0 && downY[0] - e.getRawY() > 32 * dp) { downY[0] = -1; if (opener != null) opener.run(); return true; }
            if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) downY[0] = -1;
            return true;
        });
        s.setContentDescription("Open the menu");
        s.setOnClickListener(v -> { if (opener != null) opener.run(); });
        s.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, Math.round(28 * dp), Gravity.BOTTOM));
        return s;
    }
}
