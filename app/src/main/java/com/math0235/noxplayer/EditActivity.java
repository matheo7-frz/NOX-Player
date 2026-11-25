package com.math0235.noxplayer;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/** Page « Éditer les infos » façon Lark : nom, artiste, album, pochette, chemin du fichier. */
public class EditActivity extends Activity {
    private static final int REQ_COVER = 20;
    private static Track pending;
    private Track t;
    private EditText name, artist, album;
    private CoverView cover;
    private File newCover;
    private boolean resetCover;

    public static void open(Context c, Track t) {
        pending = t;
        c.startActivity(new Intent(c, EditActivity.class));
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (!Guard.isOfficial(this)) { finish(); return; }
        t = pending;
        if (t == null) { finish(); return; }
        Ui.loadTheme(this);
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);

        // Barre : ‹  Éditer les infos   [Sauvegarder]
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(Ui.dp(this, 8), Ui.dp(this, 14), Ui.dp(this, 18), Ui.dp(this, 14));
        int bs = Ui.dp(this, 48);
        bar.addView(Ui.button(this, "‹", 32, v -> finish()), new LinearLayout.LayoutParams(bs, bs));
        TextView title = Ui.text(this, "Éditer les infos", 24, Ui.TEXT, true);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setPadding(Ui.dp(this, 10), 0, Ui.dp(this, 10), 0);
        bar.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        TextView save = Ui.text(this, "Sauvegarder", 16, 0xFFFFFFFF, true);
        save.setGravity(Gravity.CENTER);
        save.setPadding(Ui.dp(this, 20), Ui.dp(this, 11), Ui.dp(this, 20), Ui.dp(this, 11));
        save.setBackground(Ui.ripple(Ui.round(Ui.ACCENT, Ui.dp(this, 24))));
        save.setOnClickListener(v -> save());
        bar.addView(save);
        root.addView(bar);

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(Ui.dp(this, 36), Ui.dp(this, 8), Ui.dp(this, 36), Ui.dp(this, 40));

        name = field(form, "Nom", t.title, "Saisir le titre");
        artist = field(form, "Artiste", "Artiste inconnu".equals(t.artist) ? "" : t.artist, "Saisir le nom d'artiste");
        album = field(form, "Album", "Album inconnu".equals(t.album) ? "" : t.album, "Saisir le nom de l'album");

        label(form, "Couverture");
        cover = new CoverView(this, 18, 40);
        cover.bind(t, 400);
        int cs = Ui.dp(this, 120);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(cs, cs);
        clp.topMargin = Ui.dp(this, 10);
        form.addView(cover, clp);
        LinearLayout btns = new LinearLayout(this);
        TextView change = Ui.text(this, "✎  Modifier", 16, Ui.ACCENT2, true);
        change.setPadding(Ui.dp(this, 18), Ui.dp(this, 10), Ui.dp(this, 18), Ui.dp(this, 10));
        change.setBackground(Ui.ripple(Ui.round(Ui.CARD, Ui.dp(this, 22))));
        change.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.setType("image/*");
            startActivityForResult(Intent.createChooser(i, "Choisir une photo"), REQ_COVER);
        });
        btns.addView(change);
        if (t.customCover(this) != null) {
            TextView reset = Ui.text(this, "Photo d'origine", 14, Ui.SUB, false);
            reset.setPadding(Ui.dp(this, 18), Ui.dp(this, 10), Ui.dp(this, 18), Ui.dp(this, 10));
            reset.setOnClickListener(v -> {
                resetCover = true;
                newCover = null;
                cover.bindLetter("♪", Ui.colorFor(t.album + t.artist));
                Ui.toast(this, "La photo d'origine sera remise en sauvegardant");
            });
            btns.addView(reset);
        }
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-2, -2);
        blp.topMargin = Ui.dp(this, 14);
        form.addView(btns, blp);

        label(form, "Chemin du fichier");
        TextView path = Ui.text(this, t.path == null || t.path.isEmpty() ? t.uri().toString() : t.path, 15, Ui.SUB, false);
        path.setTextIsSelectable(true);
        path.setPadding(0, Ui.dp(this, 8), 0, 0);
        form.addView(path);

        ScrollView sv = new ScrollView(this);
        sv.addView(form);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }

    private void label(LinearLayout form, String s) {
        TextView l = Ui.text(this, s, 22, 0xFF8A8A8A, true);
        l.setPadding(0, Ui.dp(this, 26), 0, 0);
        form.addView(l);
    }

    private EditText field(LinearLayout form, String lbl, String value, String hint) {
        label(form, lbl);
        EditText e = new EditText(this);
        e.setSingleLine(true);
        e.setText(value);
        e.setHint(hint);
        e.setTextSize(18);
        e.setTextColor(Ui.TEXT);
        e.setHintTextColor(0xFF777777);
        e.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));
        e.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 14));
        form.addView(e, new LinearLayout.LayoutParams(-1, -2));
        return e;
    }

    private void save() {
        String n = name.getText().toString().trim();
        String ar = artist.getText().toString().trim();
        String al = album.getText().toString().trim();
        t.title = n.isEmpty() ? t.title : n;
        t.artist = ar.isEmpty() ? "Artiste inconnu" : ar;
        t.album = al.isEmpty() ? "Album inconnu" : al;
        Store.saveEdits(this, t);
        File dest = Store.coverFile(this, t.id);
        if (resetCover) dest.delete();
        if (newCover != null) {
            dest.delete();
            if (!newCover.renameTo(dest)) {
                try (java.io.FileInputStream in = new java.io.FileInputStream(newCover);
                     FileOutputStream out = new FileOutputStream(dest)) {
                    byte[] buf = new byte[32 * 1024];
                    int len;
                    while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
                } catch (Exception ignored) {}
            }
        }
        if (resetCover || newCover != null) t.coverStamp = System.currentTimeMillis();
        MainActivity.needsReload = true;
        PlayerService s = PlayerService.get();
        if (s != null) s.notifyQueueChanged();
        Ui.toast(this, "✓ Sauvegardé");
        finish();
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_COVER || res != RESULT_OK || data == null || data.getData() == null) return;
        Uri img = data.getData();
        File tmp = new File(getCacheDir(), "cover_tmp.jpg");
        new Thread(() -> {
            boolean ok = saveSquare(img, tmp);
            runOnUiThread(() -> {
                if (!ok) { Ui.toast(this, "Impossible de lire cette image"); return; }
                newCover = tmp;
                resetCover = false;
                Bitmap b = BitmapFactory.decodeFile(tmp.getAbsolutePath());
                if (b != null) {
                    android.widget.ImageView iv = new android.widget.ImageView(this);
                    cover.removeAllViews();
                    iv.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
                    iv.setImageBitmap(b);
                    cover.addView(iv, new android.widget.FrameLayout.LayoutParams(-1, -1));
                }
            });
        }).start();
    }

    private boolean saveSquare(Uri img, File dest) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            try (InputStream in = getContentResolver().openInputStream(img)) { BitmapFactory.decodeStream(in, null, o); }
            int s = 1;
            while (o.outWidth / (s * 2) >= 800 && o.outHeight / (s * 2) >= 800) s *= 2;
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = s;
            Bitmap b;
            try (InputStream in = getContentResolver().openInputStream(img)) { b = BitmapFactory.decodeStream(in, null, o2); }
            if (b == null) return false;
            int side = Math.min(b.getWidth(), b.getHeight());
            Bitmap sq = Bitmap.createBitmap(b, (b.getWidth() - side) / 2, (b.getHeight() - side) / 2, side, side);
            try (FileOutputStream out = new FileOutputStream(dest)) { sq.compress(Bitmap.CompressFormat.JPEG, 90, out); }
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
