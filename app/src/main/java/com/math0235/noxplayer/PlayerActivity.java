package com.math0235.noxplayer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.audiofx.BassBoost;
import android.media.audiofx.Equalizer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Écran « En lecture » façon Lark Player : couleurs tirées de la pochette, onde, grosses commandes. */
public class PlayerActivity extends Activity implements PlayerService.Listener {
    private static final int DEFAULT_BG = 0xFF1C1726, DEFAULT_ACCENT = 0xFFDCC4FF;

    private FrameLayout root;
    private CoverView cover;
    private TextView title, artist, cur, total, shuf, rep, lyricsBtn, speedChip, sleepChip;
    private IconView heart, more, prev, next, playIcon, eqIcon, queueIcon, down;
    private WaveView wave;
    private SeekBar seek;
    private boolean dragging;
    private String shownKey;
    private int coverPx;
    private int bg = DEFAULT_BG, accent = DEFAULT_ACCENT;

    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override public void run() { updateProgress(); h.postDelayed(this, 300); }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (!Guard.isOfficial(this)) { finish(); return; }
        setContentView(build());
        applyColors();
    }

    @Override
    protected void onResume() {
        super.onResume();
        PlayerService.addListener(this);
        onPlayerChanged();
        h.post(tick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        PlayerService.removeListener(this);
        h.removeCallbacks(tick);
    }

    private static PlayerService ps() { return PlayerService.get(); }

    private IconView icon(int kind, View.OnClickListener l) {
        IconView v = new IconView(this, kind, accent);
        v.setBackground(Ui.ripple(null));
        v.setOnClickListener(l);
        return v;
    }

    private View build() {
        root = new FrameLayout(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 28);
        col.setPadding(pad, Ui.dp(this, 8), pad, Ui.dp(this, 14));

        // Petite flèche pour revenir
        down = icon(IconView.NEXT, v -> finish());
        down.setRotation(90);
        down.setAlpha(0.6f);
        int ib = Ui.dp(this, 40);
        col.addView(down, new LinearLayout.LayoutParams(ib, ib));

        // Pochette
        int sw = getResources().getDisplayMetrics().widthPixels;
        int sh = getResources().getDisplayMetrics().heightPixels;
        coverPx = Math.min(sw - pad * 2, (int) (sh * 0.45f));
        cover = new CoverView(this, 24, 90);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(coverPx, coverPx);
        clp.gravity = Gravity.CENTER_HORIZONTAL;
        clp.topMargin = Ui.dp(this, 6);
        col.addView(cover, clp);

        // Titre
        title = Ui.text(this, "", 30, accent, true);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.MARQUEE);
        title.setMarqueeRepeatLimit(-1);
        title.setSelected(true);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-1, -2);
        tlp.topMargin = Ui.dp(this, 26);
        col.addView(title, tlp);
        artist = Ui.text(this, "", 15, Ui.SUB, false);
        artist.setSingleLine(true);
        artist.setEllipsize(TextUtils.TruncateAt.END);
        col.addView(artist);

        // Ligne : aléatoire, répétition … cœur, menu
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        shuf = Ui.button(this, "⇄", 22, v -> { PlayerService s = ps(); if (s != null) s.setShuffle(!s.shuffle()); });
        rep = Ui.button(this, "↻", 22, v -> { PlayerService s = ps(); if (s != null) s.cycleRepeat(); });
        actions.addView(shuf, new LinearLayout.LayoutParams(ib, ib));
        actions.addView(rep, new LinearLayout.LayoutParams(ib + Ui.dp(this, 8), ib));
        actions.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
        heart = icon(IconView.HEART, v -> toggleFav());
        heart.setFilled(false);
        more = icon(IconView.MORE, v -> moreMenu());
        int hb = Ui.dp(this, 48);
        actions.addView(heart, new LinearLayout.LayoutParams(hb, hb));
        actions.addView(more, new LinearLayout.LayoutParams(hb, hb));
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(-1, -2);
        alp.topMargin = Ui.dp(this, 8);
        col.addView(actions, alp);

        col.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1)); // espace flexible

        // Onde + barre
        wave = new WaveView(this);
        wave.setOnSeek(f -> { PlayerService s = ps(); if (s != null) s.seekTo((int) (s.duration() * f)); });
        col.addView(wave, new LinearLayout.LayoutParams(-1, Ui.dp(this, 46)));
        seek = new SeekBar(this);
        seek.setMax(1000);
        seek.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 6));
        seek.setSplitTrack(false);
        GradientDrawable thumb = new GradientDrawable();
        thumb.setShape(GradientDrawable.OVAL);
        thumb.setSize(Ui.dp(this, 12), Ui.dp(this, 12));
        thumb.setColor(Color.WHITE);
        seek.setThumb(thumb);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean user) {
                if (user) {
                    PlayerService s = ps();
                    if (s != null) cur.setText(Ui.time((long) s.duration() * p / 1000));
                    wave.setProgress(p / 1000f);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar sb) { dragging = true; }
            @Override public void onStopTrackingTouch(SeekBar sb) {
                dragging = false;
                PlayerService s = ps();
                if (s != null) s.seekTo((int) ((long) s.duration() * sb.getProgress() / 1000));
            }
        });
        col.addView(seek, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout times = new LinearLayout(this);
        cur = Ui.text(this, "0:00", 15, Ui.SUB, false);
        total = Ui.text(this, "0:00", 15, Ui.SUB, false);
        total.setGravity(Gravity.END);
        times.addView(cur, new LinearLayout.LayoutParams(0, -2, 1));
        times.addView(total, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams tmp = new LinearLayout.LayoutParams(-1, -2);
        tmp.topMargin = Ui.dp(this, 4);
        col.addView(times, tmp);

        // Commandes ⏪ ⏸ ⏩
        LinearLayout ctr = new LinearLayout(this);
        ctr.setGravity(Gravity.CENTER);
        int nb = Ui.dp(this, 76), pb = Ui.dp(this, 96);
        prev = icon(IconView.REW, v -> { PlayerService s = ps(); if (s != null) s.prev(); });
        next = icon(IconView.FF, v -> { PlayerService s = ps(); if (s != null) s.next(true); });
        playIcon = icon(IconView.PLAY, v -> { PlayerService s = ps(); if (s != null) s.toggle(); });
        ctr.addView(prev, new LinearLayout.LayoutParams(nb, nb));
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(pb, pb);
        plp.leftMargin = Ui.dp(this, 22);
        plp.rightMargin = Ui.dp(this, 22);
        ctr.addView(playIcon, plp);
        ctr.addView(next, new LinearLayout.LayoutParams(nb, nb));
        LinearLayout.LayoutParams ctrlp = new LinearLayout.LayoutParams(-1, -2);
        ctrlp.topMargin = Ui.dp(this, 22);
        col.addView(ctr, ctrlp);

        // Bas : égaliseur · Paroles · file d'attente
        LinearLayout bottom = new LinearLayout(this);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        int bb = Ui.dp(this, 50);
        eqIcon = icon(IconView.EQ, v -> eqDialog());
        lyricsBtn = Ui.button(this, "Paroles", 20, v -> lyricsDialog());
        lyricsBtn.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        queueIcon = icon(IconView.QUEUE, v -> showQueue());
        bottom.addView(eqIcon, new LinearLayout.LayoutParams(bb, bb));
        bottom.addView(lyricsBtn, new LinearLayout.LayoutParams(0, bb, 1));
        bottom.addView(queueIcon, new LinearLayout.LayoutParams(bb, bb));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, -2);
        blp.topMargin = Ui.dp(this, 22);
        col.addView(bottom, blp);

        // Puces vitesse / minuterie (affichées seulement quand actives)
        LinearLayout chips = new LinearLayout(this);
        chips.setGravity(Gravity.CENTER);
        sleepChip = Ui.chip(this, "", v -> sleepDialog());
        speedChip = Ui.chip(this, "", v -> speedDialog());
        int m = Ui.dp(this, 4);
        for (TextView t : new TextView[]{sleepChip, speedChip}) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.setMargins(m, Ui.dp(this, 8), m, 0);
            chips.addView(t, lp);
        }
        col.addView(chips, new LinearLayout.LayoutParams(-1, -2));

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setVerticalScrollBarEnabled(false);
        sv.addView(col);
        root.addView(sv, new FrameLayout.LayoutParams(-1, -1));
        return root;
    }

    // ------------------------------------------------------------------ Couleurs tirées de la pochette
    private void paletteFrom(Bitmap b) {
        if (b == null) { bg = DEFAULT_BG; accent = DEFAULT_ACCENT; return; }
        Bitmap s = Bitmap.createScaledBitmap(b, 12, 12, true);
        long r = 0, g = 0, bl = 0;
        int n = 0;
        for (int x = 0; x < 12; x++) for (int y = 0; y < 12; y++) {
            int c = s.getPixel(x, y);
            r += Color.red(c); g += Color.green(c); bl += Color.blue(c); n++;
        }
        float[] hsv = new float[3];
        Color.colorToHSV(Color.rgb((int) (r / n), (int) (g / n), (int) (bl / n)), hsv);
        float sat = hsv[1];
        bg = Color.HSVToColor(new float[]{hsv[0], Math.min(sat, 0.35f), 0.19f});
        accent = Color.HSVToColor(new float[]{hsv[0], Math.min(sat, 0.22f) + 0.05f, 0.94f});
    }

    private void applyColors() {
        root.setBackgroundColor(bg);
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        title.setTextColor(accent);
        for (IconView v : new IconView[]{heart, more, prev, next, playIcon, eqIcon, queueIcon, down}) v.setColor(accent);
        lyricsBtn.setTextColor((accent & 0x00FFFFFF) | 0xB3000000);
        int a = accent & 0x00FFFFFF;
        wave.setColors(a | 0x26000000, a | 0x66000000);
        seek.setProgressTintList(ColorStateList.valueOf(accent));
        seek.setProgressBackgroundTintList(ColorStateList.valueOf(a | 0x33000000));
        seek.getThumb().setTint(accent);
        onPlayerChanged();
    }

    @Override
    public void onPlayerChanged() {
        PlayerService s = ps();
        Track t = s != null ? s.current() : null;
        if (t == null) {
            title.setText("Rien en lecture");
            artist.setText("Choisis une chanson dans ta bibliothèque");
            playIcon.setKind(IconView.PLAY);
            sleepChip.setVisibility(View.GONE);
            speedChip.setVisibility(View.GONE);
            return;
        }
        if (!t.artKey().equals(shownKey)) {
            shownKey = t.artKey();
            cover.bind(t, 600);
            wave.setSeed(t.key().hashCode());
            final String k = shownKey;
            ArtLoader.load(this, t, 600, b -> {
                if (!k.equals(shownKey)) return;
                paletteFrom(b);
                applyColors();
            });
        }
        if (!title.getText().toString().equals(t.title)) title.setText(t.title);
        artist.setText(t.artist);
        boolean isFav = t.id >= 0 && Store.isFav(this, t.id);
        heart.setFilled(isFav);
        heart.setColor(isFav ? 0xFFFF4D8D : accent);
        playIcon.setKind(s.isPlaying() ? IconView.PAUSE : IconView.PLAY);
        shuf.setTextColor(s.shuffle() ? accent : 0x66FFFFFF);
        int r = s.repeat();
        rep.setText(r == PlayerService.REPEAT_ONE ? "↻¹" : "↻");
        rep.setTextColor(r == PlayerService.REPEAT_OFF ? 0x66FFFFFF : accent);
        speedChip.setVisibility(s.speed() != 1f ? View.VISIBLE : View.GONE);
        speedChip.setText(String.format(Locale.ROOT, "⏩ %.2gx", s.speed()));
        updateProgress();
    }

    private void updateProgress() {
        PlayerService s = ps();
        if (s == null || s.current() == null) return;
        int d = s.duration(), p = s.position();
        total.setText(Ui.time(d));
        if (!dragging) {
            cur.setText(Ui.time(p));
            int prog = d > 0 ? (int) ((long) p * 1000 / d) : 0;
            seek.setProgress(prog);
            wave.setProgress(prog / 1000f);
        }
        long rem = s.sleepRemaining();
        sleepChip.setVisibility(rem > 0 ? View.VISIBLE : View.GONE);
        sleepChip.setText("⏾ " + Ui.time(rem));
    }

    private void toggleFav() {
        PlayerService s = ps();
        if (s == null || s.current() == null) return;
        Track t = s.current();
        if (t.id < 0) { Ui.toast(this, "Impossible pour un fichier externe"); return; }
        boolean on = Store.toggleFav(this, t.id);
        Ui.toast(this, on ? "♥ Ajouté aux favoris" : "Retiré des favoris");
        onPlayerChanged();
    }

    private void moreMenu() {
        PlayerService s = ps();
        if (s == null || s.current() == null) return;
        TrackMenu.show(this, s.current(), null, this::onPlayerChanged);
    }

    private void pickPlaylist(Track t) {
        List<String> names = new ArrayList<>(Store.playlists(this).keySet());
        if (names.isEmpty()) { Ui.toast(this, "Crée d'abord une playlist dans l'onglet Playlists"); return; }
        new AlertDialog.Builder(this, Ui.DIALOG).setTitle("Ajouter à…")
                .setItems(names.toArray(new String[0]), (d, w) -> Ui.toast(this,
                        Store.addToPlaylist(this, names.get(w), t.id) ? "Ajouté à « " + names.get(w) + " »" : "Déjà dedans"))
                .show();
    }

    // ------------------------------------------------------------------ Paroles
    private String lyricsKey(Track t) { return "lyrics_" + t.key(); }

    /** Cherche un fichier .lrc / .txt à côté de la musique (même nom). */
    private static String findLrc(Track t) {
        if (t.path == null || t.path.isEmpty()) return null;
        int dot = t.path.lastIndexOf('.');
        String base = dot > 0 ? t.path.substring(0, dot) : t.path;
        for (String ext : new String[]{".lrc", ".txt"}) {
            File f = new File(base + ext);
            if (!f.canRead()) continue;
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new FileReader(f))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String clean = line.replaceAll("\\[[^\\]]*\\]", "").trim();
                    if (line.matches("^\\[(ar|ti|al|by|offset|length|re|ve):.*")) continue;
                    sb.append(clean).append('\n');
                }
                return sb.toString().trim();
            } catch (Exception ignored) {}
        }
        return null;
    }

    private void lyricsDialog() {
        PlayerService s = ps();
        if (s == null || s.current() == null) return;
        Track t = s.current();
        String saved = Store.getString(this, lyricsKey(t), null);
        if (saved == null) saved = findLrc(t);
        if (saved == null || saved.isEmpty()) { editLyrics(t, ""); return; }
        final String text = saved;
        TextView tv = Ui.text(this, text, 18, Ui.TEXT, false);
        tv.setLineSpacing(0, 1.35f);
        tv.setTextIsSelectable(true);
        int p = Ui.dp(this, 22);
        tv.setPadding(p, p / 2, p, p);
        ScrollView sv = new ScrollView(this);
        sv.addView(tv);
        new AlertDialog.Builder(this, Ui.DIALOG).setTitle("Paroles — " + t.title).setView(sv)
                .setNeutralButton("Modifier", (d, w) -> editLyrics(t, text))
                .setPositiveButton("Fermer", null).show();
    }

    private void editLyrics(Track t, String current) {
        EditText in = new EditText(this);
        in.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        in.setMinLines(6);
        in.setGravity(Gravity.TOP);
        in.setHint("Colle les paroles ici…");
        in.setText(current);
        in.setTextColor(Ui.TEXT);
        in.setHintTextColor(Ui.SUB);
        LinearLayout box = new LinearLayout(this);
        int p = Ui.dp(this, 22);
        box.setPadding(p, p / 2, p, 0);
        box.addView(in, new LinearLayout.LayoutParams(-1, -2));
        new AlertDialog.Builder(this, Ui.DIALOG).setTitle("Paroles — " + t.title)
                .setMessage(current.isEmpty() ? "Pas encore de paroles pour cette chanson. Colle-les ici, elles seront gardées." : null)
                .setView(box)
                .setNegativeButton("Annuler", null)
                .setPositiveButton("Enregistrer", (d, w) -> {
                    Store.putString(this, lyricsKey(t), in.getText().toString().trim());
                    Ui.toast(this, "✓ Paroles enregistrées");
                }).show();
    }

    // ------------------------------------------------------------------ File d'attente
    private void showQueue() {
        PlayerService s = ps();
        if (s == null || s.current() == null) return;
        startActivity(new android.content.Intent(this, QueueActivity.class)
                .putExtra("bg", bg).putExtra("accent", accent));
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    // ------------------------------------------------------------------ Minuterie
    private void sleepDialog() {
        PlayerService s = PlayerService.get();
        if (s == null) return;
        final int[] mins = {0, 5, 10, 15, 30, 45, 60, 90, 120};
        String[] labels = new String[mins.length];
        for (int i = 0; i < mins.length; i++) labels[i] = mins[i] == 0 ? "Désactiver" : mins[i] + " minutes";
        new AlertDialog.Builder(this, Ui.DIALOG).setTitle("⏾  Arrêter la musique dans…")
                .setItems(labels, (d, w) -> {
                    s.setSleep(mins[w]);
                    Ui.toast(this, mins[w] == 0 ? "Minuterie désactivée" : "La musique s'arrêtera dans " + mins[w] + " min");
                }).show();
    }

    // ------------------------------------------------------------------ Vitesse
    private void speedDialog() {
        PlayerService s = PlayerService.get();
        if (s == null) return;
        final float[] sp = {0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f};
        String[] labels = new String[sp.length];
        for (int i = 0; i < sp.length; i++) labels[i] = (sp[i] == s.speed() ? "✓  " : "     ") + sp[i] + "x";
        new AlertDialog.Builder(this, Ui.DIALOG).setTitle("Vitesse de lecture")
                .setItems(labels, (d, w) -> s.setSpeed(sp[w])).show();
    }

    // ------------------------------------------------------------------ Égaliseur
    private void eqDialog() {
        PlayerService s = PlayerService.get();
        if (s == null || s.eq() == null) { Ui.toast(this, "Lance une chanson pour ouvrir l'égaliseur"); return; }
        Equalizer eq = s.eq();
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(this, 22);
        box.setPadding(p, p / 2, p, p / 2);

        Switch sw = new Switch(this);
        sw.setText("Activer l'égaliseur");
        sw.setTextColor(Ui.TEXT);
        boolean on;
        try { on = eq.getEnabled(); } catch (Throwable t) { on = false; }
        sw.setChecked(on);
        sw.setOnCheckedChangeListener((b, c) -> s.setFxEnabled(c));
        box.addView(sw);

        final short[] range;
        final short bands;
        try {
            range = eq.getBandLevelRange();
            bands = eq.getNumberOfBands();
        } catch (Throwable t) { Ui.toast(this, "Égaliseur indisponible"); return; }
        final SeekBar[] bars = new SeekBar[bands];
        final TextView[] lbls = new TextView[bands];

        TextView presets = Ui.chip(this, "Préréglages ▾", null);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(-2, -2);
        plp.topMargin = Ui.dp(this, 10);
        plp.bottomMargin = Ui.dp(this, 6);
        box.addView(presets, plp);

        for (short i = 0; i < bands; i++) {
            final short band = i;
            int hz = eq.getCenterFreq(band) / 1000;
            final String fq = hz >= 1000 ? String.format(Locale.ROOT, "%.1f kHz", hz / 1000f) : hz + " Hz";
            lbls[i] = Ui.text(this, "", 13, Ui.SUB, false);
            lbls[i].setPadding(0, Ui.dp(this, 8), 0, 0);
            bars[i] = new SeekBar(this);
            bars[i].setMax(range[1] - range[0]);
            bars[i].setProgressTintList(ColorStateList.valueOf(Ui.ACCENT));
            bars[i].setThumbTintList(ColorStateList.valueOf(Ui.ACCENT2));
            bars[i].setProgress(eq.getBandLevel(band) - range[0]);
            final TextView lbl = lbls[i];
            lbl.setText(fq + "   " + db(eq.getBandLevel(band)));
            bars[i].setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar sb, int v, boolean user) {
                    short level = (short) (v + range[0]);
                    lbl.setText(fq + "   " + db(level));
                    if (user) { s.setBand(band, level); if (!sw.isChecked()) sw.setChecked(true); }
                }
                @Override public void onStartTrackingTouch(SeekBar sb) {}
                @Override public void onStopTrackingTouch(SeekBar sb) {}
            });
            box.addView(lbls[i]);
            box.addView(bars[i]);
        }

        presets.setOnClickListener(v -> {
            short n;
            try { n = eq.getNumberOfPresets(); } catch (Throwable t) { n = 0; }
            if (n == 0) { Ui.toast(this, "Aucun préréglage sur cet appareil"); return; }
            String[] names = new String[n];
            for (short i = 0; i < n; i++) names[i] = eq.getPresetName(i);
            new AlertDialog.Builder(this, Ui.DIALOG).setTitle("Préréglages").setItems(names, (d, w) -> {
                s.usePreset((short) w);
                for (short i = 0; i < bands; i++) bars[i].setProgress(eq.getBandLevel(i) - range[0]);
                presets.setText(names[w] + " ▾");
                if (!sw.isChecked()) sw.setChecked(true);
            }).show();
        });

        BassBoost bass = s.bass();
        if (bass != null && bass.getStrengthSupported()) {
            TextView bl = Ui.text(this, "Basses renforcées", 13, Ui.SUB, false);
            bl.setPadding(0, Ui.dp(this, 14), 0, 0);
            box.addView(bl);
            SeekBar bb = new SeekBar(this);
            bb.setMax(1000);
            bb.setProgressTintList(ColorStateList.valueOf(Ui.ACCENT2));
            bb.setThumbTintList(ColorStateList.valueOf(Ui.ACCENT2));
            bb.setProgress(bass.getRoundedStrength());
            bb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar sb, int v, boolean user) {
                    if (user) { s.setBass((short) v); if (!sw.isChecked()) sw.setChecked(true); }
                }
                @Override public void onStartTrackingTouch(SeekBar sb) {}
                @Override public void onStopTrackingTouch(SeekBar sb) {}
            });
            box.addView(bb);
        }

        ScrollView sv = new ScrollView(this);
        sv.addView(box);
        new AlertDialog.Builder(this, Ui.DIALOG).setTitle("🎚  Égaliseur").setView(sv)
                .setPositiveButton("OK", null).show();
    }

    private static String db(short millibel) {
        float d = millibel / 100f;
        return (d > 0 ? "+" : "") + String.format(Locale.ROOT, "%.0f dB", d);
    }
}
