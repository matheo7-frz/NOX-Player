package com.math0235.noxplayer;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

/** Pochette arrondie : image si dispo, sinon dégradé + initiale. */
public class CoverView extends FrameLayout {
    private final ImageView img;
    private final TextView letter;
    private final float radius;
    private String key;

    public CoverView(Context c, float radiusDp, float letterSp) {
        super(c);
        radius = Ui.dp(c, radiusDp);
        letter = new TextView(c);
        letter.setGravity(Gravity.CENTER);
        letter.setTextColor(0xFFFFFFFF);
        letter.setTextSize(letterSp);
        letter.setTypeface(Typeface.DEFAULT_BOLD);
        img = new ImageView(c);
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        addView(letter, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        addView(img, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        setClipToOutline(true);
        bindLetter("♪", Ui.ACCENT);
    }

    public void bindLetter(String s, int color) {
        key = null;
        setBackground(Ui.gradient(color, Ui.shift(color), radius));
        letter.setText(s);
        img.setImageDrawable(null);
    }

    public void bind(Track t, int px) {
        final String k = t.artKey();
        if (k.equals(key)) return;
        int col = Ui.colorFor(t.video ? t.title : t.album + t.artist);
        bindLetter(t.video ? "▶" : initial(t.title), col);
        key = k;
        Bitmap b = ArtLoader.cached(t, px);
        if (b != null) { img.setImageBitmap(b); return; }
        ArtLoader.load(getContext(), t, px, bm -> {
            if (k.equals(key) && bm != null) img.setImageBitmap(bm);
        });
    }

    private static String initial(String s) {
        if (s == null) return "♪";
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (Character.isLetterOrDigit(ch)) return String.valueOf(Character.toUpperCase(ch));
        }
        return "♪";
    }
}
