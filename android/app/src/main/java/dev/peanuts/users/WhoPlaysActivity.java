package dev.peanuts.users;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import dev.nxnative.android.R;
import dev.peanuts.PeanutsPrefs;
import dev.peanuts.Screens;
import dev.peanuts.ui.Feel;
import dev.peanuts.ui.PUi;

/**
 * "Who's playing?" before a game starts (Settings › Users), with two or more users: big profile cards on the top screen, the user who played this game last
 * picked already; the bottom screen shows the game, Play as X, and "Always X for this game", which skips the question for that game from then on. The pick goes
 * to the game as its starting user (WhoPlays.EXTRA, GameActivity: DM_USER). Back starts nothing.
 */
public final class WhoPlaysActivity extends Activity {
    static final String EXTRA_GAME = "dev.peanuts.game";

    /**
     * Screens.startGame's question: true when the player is asked first (this page then starts the game), false when the game starts now, with the user the
     * setting or the game's "Always" names already in the intent
     */
    public static boolean ask(Activity launcher, Intent game) {
        if (game.hasExtra(WhoPlays.EXTRA)) return false;
        String tid = game.getStringExtra("title_id");
        if (tid == null) return false;
        List<UsersPort.User> users = UsersPort.list(launcher);
        List<String> ids = new ArrayList<>();
        for (UsersPort.User u : users) ids.add(u.uid);
        WhoPlays.Decision d = WhoPlays.decide(ids, UsersPort.defaultUid(launcher), UsersPrefs.whoMode(launcher), UsersPrefs.always(launcher, tid), UsersPrefs.last(launcher, tid));
        if (!d.ask) {
            if (users.size() > 1) { game.putExtra(WhoPlays.EXTRA, d.uid); UsersPrefs.last(launcher, tid, d.uid); }
            return false;
        }
        Intent ask = new Intent(launcher, WhoPlaysActivity.class).putExtra(EXTRA_GAME, game).putExtra(WhoPlays.EXTRA, d.uid);
        if ((game.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK) != 0) ask.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        launcher.startActivity(ask);
        return true;
    }

    /** GameActivity's hook: the user the start names (WhoPlays.EXTRA) when it is one of the users, else def (the user games start with) */
    public static String startUser(Context c, Intent start, String def) {
        String uid = WhoPlays.uid(start != null ? start.getStringExtra(WhoPlays.EXTRA) : null, def);
        for (UsersPort.User u : UsersPort.list(c)) if (u.uid.equals(uid)) return uid;
        return def;
    }

    private Intent game;
    private String tid, name, picked;
    private boolean always;
    private List<UsersPort.User> users;
    private final java.util.Map<String, View> cards = new java.util.HashMap<>();
    private Bottom bottom;

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        game = getIntent().getParcelableExtra(EXTRA_GAME, Intent.class);
        if (game == null) { finish(); return; }
        tid = game.getStringExtra("title_id");
        name = game.getStringExtra("name");
        picked = saved != null ? saved.getString("picked") : getIntent().getStringExtra(WhoPlays.EXTRA);
        users = UsersPort.list(this);
        boolean wide = getResources().getConfiguration().screenWidthDp >= 600;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(wide ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        root.setBackgroundColor(PUi.col(this, R.color.bn_bg));
        FrameLayout frame = new FrameLayout(this);
        root.addView(frame, new LinearLayout.LayoutParams(wide ? 0 : ViewGroup.LayoutParams.MATCH_PARENT, wide ? ViewGroup.LayoutParams.MATCH_PARENT : 0, 1));
        FrameLayout inline = new FrameLayout(this);
        root.addView(inline, new LinearLayout.LayoutParams(wide ? PUi.dp(this, 380) : ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(root);
        bottom = new Bottom(this, inline);
        frame.addView(top(), new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        bottom.set(this::panel);
    }

    @Override protected void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); out.putString("picked", picked); }
    @Override protected void onStart() { super.onStart(); bottom.start(); }
    @Override protected void onStop() { bottom.stop(); super.onStop(); }

    // ---- The top screen: the users ----

    private View top() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        int p = PUi.dp(this, 26);
        col.setPadding(p, p, p, p);
        TextView title = PUi.text(this, "Who's playing?", 26, R.color.bn_text, 800);
        title.setGravity(Gravity.CENTER);
        title.setAccessibilityHeading(true);
        col.addView(title, PUi.wrap());
        TextView sub = PUi.text(this, (name != null ? name : "This game") + " keeps a save for each user.", 14, R.color.bn_text_2, 400);
        sub.setGravity(Gravity.CENTER);
        PUi.add(col, sub, 4, PUi.wrap());
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setClipToPadding(false);
        hs.setClipChildren(false);   // the picked card is drawn a little larger than its place
        col.setClipChildren(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        row.setPadding(PUi.dp(this, 12), PUi.dp(this, 12), PUi.dp(this, 12), PUi.dp(this, 12));
        hs.addView(row);
        View focus = null;
        for (int i = 0; i < users.size(); i++) {
            UsersPort.User u = users.get(i);
            View c = card(u, i);
            LinearLayout.LayoutParams lp = PUi.wrap();
            if (i > 0) lp.leftMargin = PUi.dp(this, 18);
            row.addView(c, lp);
            if (u.uid.equals(picked)) focus = c;
        }
        LinearLayout.LayoutParams lp = PUi.wrap();
        lp.topMargin = PUi.dp(this, 20);
        col.addView(hs, lp);
        if (focus != null) { View f = focus; f.post(f::requestFocus); }
        Feel.page(col, 0);
        return col;
    }

    private static GradientDrawable cardBg(Context c, boolean on) {
        return PUi.round(PUi.col(c, R.color.bn_card), PUi.dp(c, 18), PUi.col(c, on ? R.color.bn_banana : R.color.bn_card_stroke), PUi.dp(c, on ? 2 : 1));
    }

    private View card(UsersPort.User u, int index) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        int p = PUi.dp(this, 10);
        c.setPadding(p, p, p, p);
        c.setBackground(cardBg(this, u.uid.equals(picked)));
        c.setFocusable(true);
        c.setClickable(true);
        c.setContentDescription(u.name);
        c.addView(UserFace.box(this, u.name, UserPictures.of(getFilesDir(), u.uid, index), 150, 14, false));
        TextView n = PUi.text(this, u.name, 18, R.color.bn_text, 700);
        n.setPadding(0, PUi.dp(this, 6), 0, 0);
        c.addView(n, PUi.wrap());
        c.addView(PUi.small(this, hasSave(u.uid) ? "Has a save" : "No save yet"), PUi.wrap());
        cards.put(u.uid, c);
        if (u.uid.equals(picked)) { c.setScaleX(1.035f); c.setScaleY(1.035f); }
        c.setOnFocusChangeListener((v, has) -> { if (has) pick(u.uid); });
        c.setOnClickListener(Feel.launch(v -> { pick(u.uid); play(); }));
        return c;
    }

    private void pick(String uid) {
        if (uid.equals(picked)) return;
        picked = uid;
        for (java.util.Map.Entry<String, View> e : cards.entrySet()) {   // the picked card a little larger, the others back, calmly
            boolean on = e.getKey().equals(uid);
            View v = e.getValue();
            v.setBackground(cardBg(this, on));
            v.animate().scaleX(on ? 1.035f : 1f).scaleY(on ? 1.035f : 1f).setDuration(180).start();
        }
        always = false;
        bottom.refresh();
    }

    private UsersPort.User user(String uid) { for (UsersPort.User u : users) if (u.uid.equals(uid)) return u; return users.get(0); }

    private boolean hasSave(String uid) {
        if (tid == null) return false;
        File base = new File(new File(getExternalFilesDir(null), "saves"), tid);
        File dir = UsersPort.FIRST.equals(uid) ? base : new File(base.getParentFile(), base.getName() + "_user_" + uid);
        String[] in = dir.list();
        return in != null && in.length > 0;
    }

    // ---- The bottom screen: the game, Play as, Always ----

    private View panel(Context c) {
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        int p = PUi.dp(c, 16);
        col.setPadding(p, p, p, p);
        UsersPort.User u = user(picked);
        LinearLayout card = PUi.card(c);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(PUi.dp(c, 12), PUi.dp(c, 12), PUi.dp(c, 12), PUi.dp(c, 12));
        TextView cover = PUi.tile(c, name, 84);
        Bitmap icon = tid != null ? BitmapFactory.decodeFile(new File(PeanutsPrefs.iconsDir(c), tid + ".png").getPath()) : null;
        if (icon != null) PUi.tileIcon(cover, icon);
        card.addView(cover);
        LinearLayout text = new LinearLayout(c);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setPadding(PUi.dp(c, 12), 0, 0, 0);
        TextView n = PUi.text(c, name != null ? name : "", 20, R.color.bn_text, 800);
        n.setMaxLines(2);
        text.addView(n);
        LinearLayout chips = new LinearLayout(c);
        chips.setPadding(0, PUi.dp(c, 6), 0, 0);
        chips.addView(PUi.chip(c, hasSave(u.uid) ? u.name + " has a save" : "A new save for " + u.name));
        text.addView(chips);
        card.addView(text, PUi.weight());
        col.addView(card, PUi.fill());
        PUi.Action play = PUi.action(c, R.drawable.ic_play, "Play as " + u.name, PUi.Kind.PRIMARY, Feel.launch(v -> play()));
        PUi.add(col, play, 12);
        LinearLayout box = PUi.add(col, PUi.listCard(c), 12);
        PUi.Setting s = new PUi.Setting(c, "", "Always " + u.name + " for this game", null).value("Skips this question for this game")
                .toggle(c, always, (b, on) -> { Feel.toggled(b, on); always = on; });
        s.view.setOnClickListener(v -> s.toggle.toggle());
        box.addView(s.view);
        UserFace.rowIcon(c, s.view, "user");
        return col;
    }

    private void play() {
        if (tid != null) {
            UsersPrefs.last(this, tid, picked);
            if (always) UsersPrefs.always(this, tid, picked);
        }
        Intent start = new Intent(game).putExtra(WhoPlays.EXTRA, picked);
        Screens.startGame(this, start);
        finish();
    }

    @Override public boolean onKeyDown(int code, KeyEvent e) {
        if (code == KeyEvent.KEYCODE_BUTTON_A && getCurrentFocus() == null) { play(); return true; }
        return super.onKeyDown(code, e);
    }
}
