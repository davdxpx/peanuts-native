package dev.peanuts.users;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import java.util.List;

import dev.nxnative.android.R;
import dev.peanuts.ui.Feel;
import dev.peanuts.ui.PUi;

/**
 * The user playing now at Home's top right, beside FAQ and Settings (MainActivity's header buttons): their picture, their name under it. Shown only with two
 * or more users, the feature's threshold. A tap opens a menu below it: the user's Profile (Settings › Users, their card focused) and the other users to switch
 * to. Switching makes that user the one games start with (UsersPort.setDefault), as "Who's playing?" does when the app opens (WhoPlaysActivity).
 */
public final class ProfileButton {
    private ProfileButton() {}

    /** The button; openSettings shows Settings (MainActivity's page), after which the Users section is opened */
    public static View make(Activity a, Runnable openSettings) {
        LinearLayout column = new LinearLayout(a);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER_HORIZONTAL);
        fill(a, column, openSettings);
        // a pick when the app opened, a switch in Settings, a new picture: the button shows the user playing now when Home has the focus again
        column.getViewTreeObserver().addOnWindowFocusChangeListener(has -> { if (has && column.isAttachedToWindow()) fill(a, column, openSettings); });
        return column;
    }

    private static void fill(Activity a, LinearLayout column, Runnable openSettings) {
        column.removeAllViews();
        List<UsersPort.User> users = UsersPort.list(a);
        if (users.size() < 2) { column.setVisibility(View.GONE); return; }
        column.setVisibility(View.VISIBLE);
        UsersPort.User now = current(a, users);
        FrameLayout box = UserFace.box(a, now.name, UserPictures.of(a.getFilesDir(), now.uid, users.indexOf(now)), 44, 14, false);
        box.setForeground(PUi.ripple(a, PUi.round(0, PUi.dp(a, 14), 0, 0)));   // the tap shows over the picture
        box.setFocusable(true);
        box.setClickable(true);
        box.setContentDescription(now.name + ", playing now. Profile and switching users");
        box.setOnClickListener(Feel.button(v -> menu(a, column, openSettings)));
        column.addView(box);
        TextView caption = PUi.text(a, now.name, 10, R.color.bn_accent, 700);
        caption.setSingleLine(true);
        caption.setEllipsize(android.text.TextUtils.TruncateAt.END);
        caption.setMaxWidth(PUi.dp(a, 64));
        caption.setGravity(Gravity.CENTER);
        caption.setPadding(0, PUi.dp(a, 2), 0, 0);
        caption.setMinHeight(PUi.dp(a, 16));
        caption.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        column.addView(caption, PUi.wrap());
    }

    private static UsersPort.User current(Context c, List<UsersPort.User> users) {
        String uid = UsersPort.defaultUid(c);
        for (UsersPort.User u : users) if (u.uid.equals(uid)) return u;
        return users.get(0);
    }

    /** The menu below the button, its right edge under the button's: the user playing now with Profile, then the others to switch to */
    private static void menu(Activity a, LinearLayout anchor, Runnable openSettings) {
        List<UsersPort.User> users = UsersPort.list(a);
        if (users.size() < 2) return;
        UsersPort.User now = current(a, users);
        LinearLayout card = PUi.card(a);
        card.setOrientation(LinearLayout.VERTICAL);
        int p = PUi.dp(a, 8);
        card.setPadding(p, p, p, p);
        card.setElevation(PUi.dp(a, 8));
        PopupWindow pop = new PopupWindow(card, PUi.dp(a, 300), ViewGroup.LayoutParams.WRAP_CONTENT, true);
        pop.setBackgroundDrawable(new ColorDrawable(0));
        pop.setOutsideTouchable(true);

        LinearLayout head = row(a, now, users, 52, now.name, "Playing now", null);
        head.setFocusable(false);
        head.setClickable(false);
        head.setBackground(null);
        card.addView(head);
        View profile = row(a, null, users, 0, "Profile", "Picture, name and saves", v -> {
            pop.dismiss();
            openSettings.run();
            a.getWindow().getDecorView().post(() -> dev.peanuts.ui.SettingsPage.go(a, "users"));   // once Settings is laid out: its Users section, this user's card focused
        });
        UserFace.rowIcon(a, profile, "user");
        PUi.add(card, profile, 4);
        PUi.add(card, PUi.section(a, "Switch user"), 10);
        View first = profile;
        for (UsersPort.User u : users) {
            if (u.uid.equals(now.uid)) continue;
            View r = row(a, u, users, 40, u.name, UsersPage.savesText(UsersPage.saves(a, u.uid)), v -> {
                UsersPort.setDefault(a, u.uid);
                pop.dismiss();
                fill(a, anchor, openSettings);
            });
            PUi.add(card, r, 4);
        }
        card.measure(View.MeasureSpec.makeMeasureSpec(PUi.dp(a, 300), View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED);
        pop.showAsDropDown(anchor, anchor.getWidth() - PUi.dp(a, 300), PUi.dp(a, 6));
        first.requestFocus();
    }

    /** One line of the menu: a user's picture (or, with user null, an icon tile the caller fills) and two lines of text */
    private static LinearLayout row(Activity a, UsersPort.User u, List<UsersPort.User> users, int pictureDp, String title, String line, View.OnClickListener click) {
        LinearLayout r = new LinearLayout(a);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        int p = PUi.dp(a, 8);
        r.setPadding(p, p, p, p);
        if (u != null) {
            r.addView(UserFace.box(a, u.name, UserPictures.of(a.getFilesDir(), u.uid, users.indexOf(u)), pictureDp, pictureDp > 44 ? 14 : 11, false));
        } else {
            TextView tile = PUi.text(a, "", 18, R.color.bn_text, 700);   // an icon tile, as Settings' rows have (UserFace.rowIcon puts the icon in)
            tile.setGravity(Gravity.CENTER);
            tile.setBackground(PUi.round(PUi.col(a, R.color.bn_tint), PUi.dp(a, 11), 0, 0));
            r.addView(tile, new LinearLayout.LayoutParams(PUi.dp(a, 40), PUi.dp(a, 40)));
        }
        LinearLayout text = new LinearLayout(a);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setPadding(PUi.dp(a, 12), 0, 0, 0);
        TextView t = PUi.text(a, title, pictureDp > 44 ? 18 : 15, R.color.bn_text, pictureDp > 44 ? 800 : 600);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        text.addView(t);
        text.addView(PUi.small(a, line));
        r.addView(text, PUi.weight());
        if (click != null) {
            r.setBackground(PUi.ripple(a, PUi.round(0, PUi.dp(a, 12), 0, 0)));
            r.setFocusable(true);
            r.setClickable(true);
            r.setOnClickListener(Feel.row(click));
        }
        return r;
    }
}
