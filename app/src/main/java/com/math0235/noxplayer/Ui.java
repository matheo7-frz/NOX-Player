package com.math0235.noxplayer;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

/** Couleurs et petits helpers d'interface (thème néon violet / cyan). */
public final class Ui {
    public static int BG = 0xFF0B0714;
    public static int CARD = 0xFF181128;
    public static int CARD2 = 0xFF231939;
    public static int ACCENT = 0xFFB14CFF;
    public static int ACCENT2 = 0xFF00E5FF;
    public static int themeVersion;

    public static final String[] THEME_NAMES = {"Néon violet", "Bleu", "Rose", "Vert", "Orange", "Rouge"};
    public static final int[][] THEMES = {
            {0xFFB14CFF, 0xFF00E5FF}, {0xFF4A90F2, 0xFF7FC4FF}, {0xFFFF4D8D, 0xFFFFB3D1},
            {0xFF22C55E, 0xFFA7F3D0}, {0xFFFF8A3D, 0xFFFFD6A5}, {0xFFE53935, 0xFFFF9E80}};
    public static final String[] BG_NAMES = {"Nuit violette", "Noir pur", "Gris foncé"};
    public static final int[][] BGS = {
            {0xFF0B0714, 0xFF181128, 0xFF231939}, {0xFF000000, 0xFF161616, 0xFF222222}, {0xFF1E2124, 0xFF2A2E33, 0xFF353A40}};

    /** Charge le thème choisi dans les paramètres. */
    public static void loadTheme(Context c) {
        int t = Math.max(0, Math.min(THEMES.length - 1, Store.getInt(c, "theme", 0)));
        int b = Math.max(0, Math.min(BGS.length - 1, Store.getInt(c, "theme_bg", 0)));
        ACCENT = THEMES[t][0];
        ACCENT2 = THEMES[t][1];
        BG = BGS[b][0];
        CARD = BGS[b][1];
        CARD2 = BGS[b][2];
    }

    /** Feuille qui monte du bas de l'écran (comme Lark). */
    public static android.app.Dialog sheet(android.app.Activity a, String title, View content) {
        android.app.Dialog d = new android.app.Dialog(a);
        d.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        android.widget.LinearLayout box = new android.widget.LinearLayout(a);
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        GradientDrawable g = new GradientDrawable();
        g.setColor(CARD);
        float r = dp(a, 26);
        g.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        box.setBackground(g);
        box.setPadding(0, dp(a, 10), 0, dp(a, 22));
        View grip = new View(a);
        grip.setBackground(round(0x55FFFFFF, dp(a, 3)));
        android.widget.LinearLayout.LayoutParams gl = new android.widget.LinearLayout.LayoutParams(dp(a, 40), dp(a, 5));
        gl.gravity = Gravity.CENTER_HORIZONTAL;
        box.addView(grip, gl);
        if (title != null) {
            TextView t = text(a, title, 24, TEXT, true);
            t.setPadding(dp(a, 26), dp(a, 18), dp(a, 26), dp(a, 8));
            box.addView(t);
        }
        box.addView(content);
        d.setContentView(box);
        android.view.Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(0));
            w.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.BOTTOM);
            w.setWindowAnimations(android.R.style.Animation_InputMethod);
        }
        d.show();
        return d;
    }
    public static final int TEXT = 0xFFFFFFFF;
    public static final int SUB = 0xFF9A8FB5;
    public static final int DIALOG = android.R.style.Theme_DeviceDefault_Dialog_Alert;

    private Ui() {}

    public static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    public static GradientDrawable round(int color, float radiusPx) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radiusPx);
        return g;
    }

    public static GradientDrawable gradient(int a, int b, float radiusPx) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{a, b});
        g.setCornerRadius(radiusPx);
        return g;
    }

    public static GradientDrawable oval(int a, int b) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{a, b});
        g.setShape(GradientDrawable.OVAL);
        return g;
    }

    public static Drawable ripple(Drawable content) {
        return new RippleDrawable(ColorStateList.valueOf(0x44B14CFF), content,
                content == null ? new ColorDrawable(Color.WHITE) : null);
    }

    public static TextView text(Context c, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    public static TextView button(Context c, String s, float sp, View.OnClickListener l) {
        TextView t = text(c, s, sp, TEXT, false);
        t.setGravity(Gravity.CENTER);
        t.setBackground(ripple(null));
        t.setOnClickListener(l);
        return t;
    }

    public static TextView chip(Context c, String s, View.OnClickListener l) {
        TextView t = text(c, s, 13, TEXT, true);
        t.setGravity(Gravity.CENTER);
        int p = dp(c, 14);
        t.setPadding(p, dp(c, 9), p, dp(c, 9));
        t.setBackground(ripple(round(CARD2, dp(c, 20))));
        t.setOnClickListener(l);
        return t;
    }

    public static String time(long ms) {
        if (ms < 0) ms = 0;
        long s = ms / 1000;
        long h = s / 3600;
        long m = (s % 3600) / 60;
        long sec = s % 60;
        if (h > 0) return String.format(java.util.Locale.ROOT, "%d:%02d:%02d", h, m, sec);
        return String.format(java.util.Locale.ROOT, "%d:%02d", m, sec);
    }

    public static int colorFor(String s) {
        int hash = s == null ? 0 : s.hashCode();
        float hue = 250 + ((hash & 0x7fffffff) % 120); // violets -> roses -> bleus
        return Color.HSVToColor(new float[]{hue % 360, 0.7f, 0.6f});
    }

    public static int shift(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[0] = (hsv[0] + 50) % 360;
        hsv[2] = Math.min(1f, hsv[2] + 0.15f);
        return Color.HSVToColor(hsv);
    }

    public static void toast(Context c, String s) {
        Toast.makeText(c, s, Toast.LENGTH_SHORT).show();
    }
}
