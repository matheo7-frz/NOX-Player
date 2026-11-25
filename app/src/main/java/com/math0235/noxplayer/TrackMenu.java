package com.math0235.noxplayer;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentValues;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.provider.MediaStore;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Menu d'une chanson façon Lark : feuille du bas avec pochette, titre et crayon ✎. */
public final class TrackMenu {
    private TrackMenu() {}

    /**
     * @param playlist nom de la playlist ouverte (pour « Retirer de cette playlist »), ou null
     * @param changed  appelé quand la bibliothèque doit être rafraîchie
     */
    public static void show(Activity a, Track t, String playlist, Runnable changed) {
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        Dialog[] d = new Dialog[1];

        // En-tête : pochette + titre + ✎
        LinearLayout head = new LinearLayout(a);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(Ui.dp(a, 22), Ui.dp(a, 14), Ui.dp(a, 12), Ui.dp(a, 10));
        CoverView cv = new CoverView(a, 12, 22);
        cv.bind(t, Ui.dp(a, 60));
        int cs = Ui.dp(a, 60);
        head.addView(cv, new LinearLayout.LayoutParams(cs, cs));
        TextView title = Ui.text(a, t.title, 18, Ui.TEXT, false);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setPadding(Ui.dp(a, 16), 0, Ui.dp(a, 8), 0);
        head.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        if (t.id >= 0) {
            TextView edit = Ui.button(a, "✎", 24, v -> { d[0].dismiss(); EditActivity.open(a, t); });
            edit.setTextColor(0xFFBBBBBB);
            int eb = Ui.dp(a, 48);
            head.addView(edit, new LinearLayout.LayoutParams(eb, eb));
        }
        box.addView(head);

        PlayerService s = PlayerService.get();
        boolean ext = t.id < 0;

        if (!ext) item(a, box, d, "≡＋", "Ajouter à la Playlist", () -> pickPlaylist(a, t, changed));
        item(a, box, d, "⤵", "Lire ensuite", () -> {
            PlayerService ps = PlayerService.get();
            if (ps != null && ps.current() != null) { ps.playNext(t); Ui.toast(a, "Sera joué ensuite"); }
            else PlayerService.playQueue(a, Collections.singletonList(t), 0);
        });
        item(a, box, d, "☰", "Ajouter à la file d'attente", () -> {
            PlayerService ps = PlayerService.get();
            if (ps != null && ps.current() != null) ps.addToQueue(t);
            else PlayerService.playQueue(a, Collections.singletonList(t), 0);
            Ui.toast(a, "Ajouté à la file");
        });
        item(a, box, d, "➦", "Partager", () -> {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("audio/*");
            i.putExtra(Intent.EXTRA_STREAM, t.uri());
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            a.startActivity(Intent.createChooser(i, "Partager « " + t.title + " »"));
        });
        divider(a, box);
        item(a, box, d, "|ılı|", "Visualiseur musical", () -> {
            PlayerService ps = PlayerService.get();
            Track cur = ps != null ? ps.current() : null;
            if (cur == null || !cur.key().equals(t.key())) {
                List<Track> q = new ArrayList<>();
                q.add(t);
                PlayerService.playQueue(a, q, 0);
            }
            a.startActivity(new Intent(a, VisualizerActivity.class));
        });
        item(a, box, d, "◷", "Vitesse", () -> speed(a));
        if (!ext) item(a, box, d, "🔔", "Mettre comme sonnerie", () -> ringtone(a, t));
        item(a, box, d, "⏰", "Minuterie de sommeil", () -> sleep(a));
        if (!ext) {
            divider(a, box);
            if (playlist != null) item(a, box, d, "−", "Retirer de cette playlist", () -> {
                Store.removeFromPlaylist(a, playlist, t.id);
                if (changed != null) changed.run();
            });
            item(a, box, d, "◌", "Masquer", () -> {
                Store.setInSet(a, "hidden", t.id, true);
                Ui.toast(a, "Masqué — Paramètres › Fichiers masqués pour le réafficher");
                MainActivity.needsReload = true;
                if (changed != null) changed.run();
            });
            item(a, box, d, "🗑", "Supprimer", () -> new AlertDialog.Builder(a, Ui.DIALOG)
                    .setTitle("Supprimer « " + t.title + " » ?")
                    .setMessage("La chanson va dans « Récemment supprimé » (Paramètres). Tu pourras la restaurer.")
                    .setNegativeButton("Annuler", null)
                    .setPositiveButton("Supprimer", (x, y) -> {
                        Store.setInSet(a, "trash", t.id, true);
                        MainActivity.needsReload = true;
                        if (changed != null) changed.run();
                    }).show());
        }

        ScrollView sv = new ScrollView(a);
        sv.addView(box);
        int max = (int) (a.getResources().getDisplayMetrics().heightPixels * 0.72f);
        LinearLayout wrap = new LinearLayout(a) {
            @Override protected void onMeasure(int w, int h) {
                super.onMeasure(w, MeasureSpec.makeMeasureSpec(max, MeasureSpec.AT_MOST));
            }
        };
        wrap.addView(sv, new LinearLayout.LayoutParams(-1, -2));
        d[0] = Ui.sheet(a, null, wrap);
    }

    private static void item(Activity a, LinearLayout box, Dialog[] d, String icon, String label, Runnable r) {
        LinearLayout row = new LinearLayout(a);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(a, 26), Ui.dp(a, 15), Ui.dp(a, 22), Ui.dp(a, 15));
        row.setBackground(Ui.ripple(null));
        TextView ic = Ui.text(a, icon, 17, 0xFFAAAAAA, true);
        ic.setGravity(Gravity.CENTER);
        row.addView(ic, new LinearLayout.LayoutParams(Ui.dp(a, 38), -2));
        TextView t = Ui.text(a, label, 18, Ui.TEXT, false);
        t.setPadding(Ui.dp(a, 16), 0, 0, 0);
        row.addView(t);
        row.setOnClickListener(v -> { d[0].dismiss(); r.run(); });
        box.addView(row);
    }

    private static void divider(Activity a, LinearLayout box) {
        View v = new View(a);
        v.setBackgroundColor(0x22FFFFFF);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, Ui.dp(a, 1));
        lp.setMargins(Ui.dp(a, 26), Ui.dp(a, 6), Ui.dp(a, 26), Ui.dp(a, 6));
        box.addView(v, lp);
    }

    // ------------------------------------------------------------------ Playlists
    public static void pickPlaylist(Activity a, Track t, Runnable changed) {
        List<String> names = new ArrayList<>(Store.playlists(a).keySet());
        List<String> labels = new ArrayList<>();
        labels.add("＋  Nouvelle playlist");
        labels.addAll(names);
        new AlertDialog.Builder(a, Ui.DIALOG).setTitle("Ajouter à…")
                .setItems(labels.toArray(new String[0]), (d, w) -> {
                    if (w == 0) {
                        android.widget.EditText in = new android.widget.EditText(a);
                        in.setHint("Nom de la playlist");
                        in.setSingleLine(true);
                        in.setTextColor(Ui.TEXT);
                        in.setHintTextColor(Ui.SUB);
                        LinearLayout b = new LinearLayout(a);
                        int p = Ui.dp(a, 22);
                        b.setPadding(p, p / 2, p, 0);
                        b.addView(in, new LinearLayout.LayoutParams(-1, -2));
                        new AlertDialog.Builder(a, Ui.DIALOG).setTitle("Nouvelle playlist").setView(b)
                                .setNegativeButton("Annuler", null)
                                .setPositiveButton("Créer", (x, y) -> {
                                    String n = in.getText().toString().trim();
                                    if (n.isEmpty()) return;
                                    Store.createPlaylist(a, n);
                                    Store.addToPlaylist(a, n, t.id);
                                    Ui.toast(a, "Ajouté à « " + n + " »");
                                    if (changed != null) changed.run();
                                }).show();
                    } else {
                        String n = names.get(w - 1);
                        Ui.toast(a, Store.addToPlaylist(a, n, t.id) ? "Ajouté à « " + n + " »" : "Déjà dans « " + n + " »");
                        if (changed != null) changed.run();
                    }
                }).show();
    }

    // ------------------------------------------------------------------ Vitesse / minuterie
    public static void speed(Activity a) {
        PlayerService s = PlayerService.get();
        if (s == null || s.current() == null) { Ui.toast(a, "Lance d'abord une chanson"); return; }
        final float[] sp = {0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f};
        String[] labels = new String[sp.length];
        for (int i = 0; i < sp.length; i++) labels[i] = (sp[i] == s.speed() ? "✓  " : "     ") + sp[i] + "x";
        new AlertDialog.Builder(a, Ui.DIALOG).setTitle("Vitesse de lecture")
                .setItems(labels, (d, w) -> s.setSpeed(sp[w])).show();
    }

    public static void sleep(Activity a) {
        PlayerService s = PlayerService.get();
        if (s == null || s.current() == null) { Ui.toast(a, "Lance d'abord une chanson"); return; }
        final int[] mins = {0, 5, 10, 15, 30, 45, 60, 90, 120};
        String[] labels = new String[mins.length];
        for (int i = 0; i < mins.length; i++) labels[i] = mins[i] == 0 ? "Désactiver" : mins[i] + " minutes";
        new AlertDialog.Builder(a, Ui.DIALOG).setTitle("⏰  Arrêter la musique dans…")
                .setItems(labels, (d, w) -> {
                    s.setSleep(mins[w]);
                    Ui.toast(a, mins[w] == 0 ? "Minuterie désactivée" : "La musique s'arrêtera dans " + mins[w] + " min");
                }).show();
    }

    // ------------------------------------------------------------------ Sonnerie
    public static void ringtone(Activity a, Track t) {
        if (!Settings.System.canWrite(a)) {
            new AlertDialog.Builder(a, Ui.DIALOG).setTitle("Autorisation nécessaire")
                    .setMessage("Pour changer ta sonnerie, Android demande d'autoriser NOX Player à « modifier les paramètres système ». Active-le puis reviens ici.")
                    .setNegativeButton("Annuler", null)
                    .setPositiveButton("Ouvrir", (d, w) -> a.startActivity(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
                            Uri.parse("package:" + a.getPackageName())))).show();
            return;
        }
        try {
            try {
                ContentValues v = new ContentValues();
                v.put(MediaStore.Audio.Media.IS_RINGTONE, 1);
                a.getContentResolver().update(t.uri(), v, null, null);
            } catch (Exception ignored) {}
            RingtoneManager.setActualDefaultRingtoneUri(a, RingtoneManager.TYPE_RINGTONE, t.uri());
            Ui.toast(a, "🔔 « " + t.title + " » est ta nouvelle sonnerie");
        } catch (Exception e) {
            Ui.toast(a, "Impossible de changer la sonnerie sur ce téléphone");
        }
    }
}
