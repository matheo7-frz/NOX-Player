package com.math0235.noxplayer;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Paramètres façon Lark Player, en cartes arrondies. */
public class SettingsActivity extends Activity {
    private static final int REQ_SAVE = 10, REQ_RESTORE = 11, REQ_DELETE = 12;
    private LinearLayout content;
    private List<Track> pendingDelete;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (!Guard.isOfficial(this)) { finish(); return; }
        Ui.loadTheme(this);
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.BG);
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Ui.BG);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(Ui.dp(this, 18), Ui.dp(this, 14), Ui.dp(this, 18), Ui.dp(this, 30));
        sv.addView(content);
        setContentView(sv);
        rebuild();
    }

    @Override
    protected void onResume() {
        super.onResume();
        rebuild();
    }

    private void rebuild() {
        content.removeAllViews();
        TextView back = Ui.button(this, "‹", 30, v -> finish());
        back.setBackground(Ui.ripple(Ui.round(Ui.CARD, Ui.dp(this, 26))));
        int bs = Ui.dp(this, 52);
        content.addView(back, new LinearLayout.LayoutParams(bs, bs));
        TextView title = Ui.text(this, "Paramètres", 38, Ui.TEXT, true);
        title.setPadding(Ui.dp(this, 6), Ui.dp(this, 22), 0, Ui.dp(this, 18));
        content.addView(title);

        PlayerService s = PlayerService.get();
        long rem = s != null ? s.sleepRemaining() : 0;

        LinearLayout c1 = card();
        row(c1, "◷", "Temps de lecture", listenTime(), v -> statsDialog());
        row(c1, "☁", "Sauvegarder & restaurer", "Fichier", v -> backupMenu());

        LinearLayout c2 = card();
        row(c2, "👕", "Thème", Ui.THEME_NAMES[Store.getInt(this, "theme", 0)] + " / "
                + Ui.BG_NAMES[Store.getInt(this, "theme_bg", 0)], v -> themeSheet());
        row(c2, "⏰", "Minuterie de sommeil", rem > 0 ? Ui.time(rem) : "Désactivée", v -> sleepDialog());
        row(c2, "🚫", "Publicités", "Aucune ✓", v -> Ui.toast(this, "NOX Player n'a pas de pubs, et n'en aura jamais."));

        LinearLayout c3 = card();
        row(c3, "◌", "Fichiers masqués", MediaRepo.hidden.size() + " fichiers", v -> hiddenDialog());
        row(c3, "🗑", "Récemment supprimé", MediaRepo.trash.size() + " fichiers", v -> trashDialog());
        row(c3, "🎧", "Paramètres de lecture", "", v -> playbackDialog());
        row(c3, "🔔", "Paramètres de notifications", "", v -> {
            Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            i.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
            startActivity(i);
        });
        row(c3, "🌐", "Langue", "Français", v -> Ui.toast(this, "NOX Player est en français."));

        LinearLayout c4 = card();
        row(c4, "🔗", "Importer depuis un lien direct", "", v -> ToolsUi.importLinkDialog(this));
        row(c4, "⟳", "Rescanner la bibliothèque", "", v -> { MainActivity.needsReload = true; Ui.toast(this, "La bibliothèque sera rescannée"); });
        row(c4, "ⓘ", "À propos", "1.0", v -> new AlertDialog.Builder(this, Ui.DIALOG).setTitle("NOX Player")
                .setMessage("Lecteur de musique hors ligne — sans pub, sans compte, sans Internet.\n\nVersion 1.0\nfait par math_0235")
                .setPositiveButton("OK", null).show());
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(Ui.round(Ui.CARD, Ui.dp(this, 26)));
        c.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
        c.setClipToOutline(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = Ui.dp(this, 14);
        content.addView(c, lp);
        return c;
    }

    private void row(LinearLayout card, String icon, String label, String value, View.OnClickListener l) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(Ui.dp(this, 22), Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16));
        r.setBackground(Ui.ripple(null));
        r.setOnClickListener(l);
        TextView ic = Ui.text(this, icon, 20, 0xFFAAAAAA, false);
        ic.setGravity(Gravity.CENTER);
        r.addView(ic, new LinearLayout.LayoutParams(Ui.dp(this, 34), -2));
        TextView t = Ui.text(this, label, 17, Ui.TEXT, false);
        t.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 8), 0);
        r.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
        TextView v = Ui.text(this, value, 15, Ui.SUB, false);
        v.setSingleLine(true);
        v.setEllipsize(TextUtils.TruncateAt.END);
        v.setMaxWidth(Ui.dp(this, 170));
        r.addView(v);
        r.addView(Ui.text(this, "  ›", 20, Ui.SUB, false));
        card.addView(r);
    }

    private String listenTime() {
        long m = Store.listenMs(this) / 60000;
        return (m / 60) + "h " + (m % 60) + "m";
    }

    // ------------------------------------------------------------------ Stats
    private void statsDialog() {
        List<Track> all = new ArrayList<>(MediaRepo.songs);
        Collections.sort(all, (a, b) -> Integer.compare(Store.plays(this, b.id), Store.plays(this, a.id)));
        StringBuilder sb = new StringBuilder("Temps d'écoute total : " + listenTime() + "\n\nTes plus écoutées :\n");
        int n = 0;
        for (Track t : all) {
            int p = Store.plays(this, t.id);
            if (p == 0 || n >= 10) break;
            sb.append(++n).append(". ").append(t.title).append("  —  ").append(p).append(p > 1 ? " écoutes\n" : " écoute\n");
        }
        if (n == 0) sb.append("Rien encore — lance une chanson !");
        new AlertDialog.Builder(this, Ui.DIALOG).setTitle("◷  Temps de lecture").setMessage(sb.toString())
                .setPositiveButton("OK", null).show();
    }

    // ------------------------------------------------------------------ Sauvegarde
    private void backupMenu() {
        new AlertDialog.Builder(this, Ui.DIALOG).setTitle("☁  Sauvegarder & restaurer")
                .setMessage("Favoris, playlists, modifications, paroles, pochettes perso et réglages — dans un fichier sur ton téléphone (ou à envoyer où tu veux). Pas besoin de compte ni d'Internet.")
                .setPositiveButton("Sauvegarder", (d, w) -> {
                    Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    i.setType("application/json");
                    i.putExtra(Intent.EXTRA_TITLE, "NOX-Player-sauvegarde.json");
                    startActivityForResult(i, REQ_SAVE);
                })
                .setNeutralButton("Restaurer", (d, w) -> {
                    Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    i.setType("*/*");
                    startActivityForResult(i, REQ_RESTORE);
                }).show();
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_DELETE) {
            if (res == RESULT_OK && pendingDelete != null) {
                for (Track t : pendingDelete) Store.setInSet(this, "trash", t.id, false);
                MediaRepo.trash.removeAll(pendingDelete);
                Ui.toast(this, "Supprimé définitivement");
            }
            pendingDelete = null;
            rebuild();
            return;
        }
        if (res != RESULT_OK || data == null || data.getData() == null) return;
        Uri u = data.getData();
        if (req == REQ_SAVE) {
            try (OutputStream os = getContentResolver().openOutputStream(u, "wt")) {
                os.write(Store.exportAll(this).getBytes(StandardCharsets.UTF_8));
                Ui.toast(this, "✓ Sauvegarde créée");
            } catch (Exception e) { Ui.toast(this, "Échec de la sauvegarde"); }
        } else if (req == REQ_RESTORE) {
            StringBuilder sb = new StringBuilder();
            try (InputStream in = getContentResolver().openInputStream(u);
                 BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
            } catch (Exception e) { Ui.toast(this, "Fichier illisible"); return; }
            if (Store.importAll(this, sb.toString())) {
                Ui.loadTheme(this);
                Ui.themeVersion++;
                MainActivity.needsReload = true;
                Ui.toast(this, "✓ Restauré");
                recreate();
            } else Ui.toast(this, "Ce n'est pas une sauvegarde NOX Player");
        }
    }

    // ------------------------------------------------------------------ Thème
    private void themeSheet() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(this, 22), 0, Ui.dp(this, 22), 0);
        box.addView(Ui.text(this, "Couleur", 14, Ui.SUB, true));
        LinearLayout colors = new LinearLayout(this);
        colors.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 18));
        android.app.Dialog[] d = new android.app.Dialog[1];
        int cur = Store.getInt(this, "theme", 0);
        for (int i = 0; i < Ui.THEMES.length; i++) {
            final int k = i;
            TextView dot = new TextView(this);
            dot.setGravity(Gravity.CENTER);
            dot.setText(i == cur ? "✓" : "");
            dot.setTextColor(0xFFFFFFFF);
            dot.setTextSize(18);
            dot.setBackground(Ui.oval(Ui.THEMES[i][0], Ui.THEMES[i][1]));
            dot.setOnClickListener(v -> { Store.putInt(this, "theme", k); applyTheme(d[0]); });
            int s = Ui.dp(this, 44);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(s, s);
            lp.rightMargin = Ui.dp(this, 10);
            colors.addView(dot, lp);
        }
        box.addView(colors);
        box.addView(Ui.text(this, "Fond", 14, Ui.SUB, true));
        int bg = Store.getInt(this, "theme_bg", 0);
        for (int i = 0; i < Ui.BGS.length; i++) {
            final int k = i;
            TextView t = Ui.text(this, (i == bg ? "●  " : "○  ") + Ui.BG_NAMES[i], 17, i == bg ? Ui.ACCENT2 : Ui.TEXT, false);
            t.setPadding(0, Ui.dp(this, 14), 0, Ui.dp(this, 14));
            t.setOnClickListener(v -> { Store.putInt(this, "theme_bg", k); applyTheme(d[0]); });
            box.addView(t);
        }
        d[0] = Ui.sheet(this, "Thème", box);
    }

    private void applyTheme(android.app.Dialog d) {
        if (d != null) d.dismiss();
        Ui.loadTheme(this);
        Ui.themeVersion++;
        recreate();
    }

    // ------------------------------------------------------------------ Minuterie
    private void sleepDialog() {
        PlayerService s = PlayerService.get();
        if (s == null || s.current() == null) { Ui.toast(this, "Lance d'abord une chanson"); return; }
        final int[] mins = {0, 5, 10, 15, 30, 45, 60, 90, 120};
        String[] labels = new String[mins.length];
        for (int i = 0; i < mins.length; i++) labels[i] = mins[i] == 0 ? "Désactiver" : mins[i] + " minutes";
        new AlertDialog.Builder(this, Ui.DIALOG).setTitle("⏰  Arrêter la musique dans…")
                .setItems(labels, (d, w) -> { s.setSleep(mins[w]); rebuild(); }).show();
    }

    // ------------------------------------------------------------------ Masqués
    private void hiddenDialog() {
        if (MediaRepo.hidden.isEmpty()) { Ui.toast(this, "Aucun fichier masqué (appui long sur une chanson → Masquer)"); return; }
        List<Track> l = new ArrayList<>(MediaRepo.hidden);
        String[] names = new String[l.size()];
        for (int i = 0; i < l.size(); i++) names[i] = l.get(i).title + "  —  " + l.get(i).artist;
        new AlertDialog.Builder(this, Ui.DIALOG).setTitle("Fichiers masqués — touche pour réafficher")
                .setItems(names, (d, w) -> {
                    Store.setInSet(this, "hidden", l.get(w).id, false);
                    MediaRepo.hidden.remove(l.get(w));
                    MainActivity.needsReload = true;
                    Ui.toast(this, "« " + l.get(w).title + " » est de retour");
                    rebuild();
                }).setPositiveButton("Fermer", null).show();
    }

    // ------------------------------------------------------------------ Corbeille
    private void trashDialog() {
        if (MediaRepo.trash.isEmpty()) { Ui.toast(this, "La corbeille est vide"); return; }
        List<Track> l = new ArrayList<>(MediaRepo.trash);
        String[] names = new String[l.size()];
        for (int i = 0; i < l.size(); i++) names[i] = l.get(i).title + "  —  " + l.get(i).artist;
        new AlertDialog.Builder(this, Ui.DIALOG).setTitle("Récemment supprimé")
                .setItems(names, (d, w) -> {
                    Track t = l.get(w);
                    new AlertDialog.Builder(this, Ui.DIALOG).setTitle(t.title)
                            .setItems(new String[]{"↩  Restaurer", "🗑  Supprimer définitivement du téléphone"}, (d2, w2) -> {
                                if (w2 == 0) {
                                    Store.setInSet(this, "trash", t.id, false);
                                    MediaRepo.trash.remove(t);
                                    MainActivity.needsReload = true;
                                    Ui.toast(this, "Restauré");
                                    rebuild();
                                } else deleteForever(Collections.singletonList(t));
                            }).show();
                })
                .setNeutralButton("Tout supprimer", (d, w) -> deleteForever(l))
                .setPositiveButton("Fermer", null).show();
    }

    private void deleteForever(List<Track> l) {
        pendingDelete = new ArrayList<>(l);
        if (Build.VERSION.SDK_INT >= 30) {
            List<Uri> uris = new ArrayList<>();
            for (Track t : l) uris.add(t.uri());
            try {
                PendingIntent pi = MediaStore.createDeleteRequest(getContentResolver(), uris);
                startIntentSenderForResult(pi.getIntentSender(), REQ_DELETE, null, 0, 0, 0);
            } catch (Exception e) { Ui.toast(this, "Suppression impossible"); }
        } else {
            int ok = 0;
            for (Track t : l) {
                try {
                    if (getContentResolver().delete(t.uri(), null, null) > 0) {
                        ok++;
                        Store.setInSet(this, "trash", t.id, false);
                        MediaRepo.trash.remove(t);
                    }
                } catch (Exception ignored) {}
            }
            Ui.toast(this, ok == l.size() ? "Supprimé définitivement" : "Certains fichiers n'ont pas pu être supprimés");
            pendingDelete = null;
            rebuild();
        }
    }

    // ------------------------------------------------------------------ Lecture
    private void playbackDialog() {
        final String[] keys = {"pause_unplug", "skip_short", "auto_audio"};
        final boolean[] defs = {true, false, false};
        String[] labels = {"Pause quand les écouteurs sont débranchés", "Masquer les sons de moins de 30 secondes",
                "Nouvelles vidéos du téléphone → audio automatiquement"};
        boolean[] checked = new boolean[3];
        for (int i = 0; i < 3; i++) checked[i] = Store.getBool(this, keys[i], defs[i]);
        new AlertDialog.Builder(this, Ui.DIALOG).setTitle("🎧  Paramètres de lecture")
                .setMultiChoiceItems(labels, checked, (d, w, on) -> {
                    Store.putBool(this, keys[w], on);
                    if (w == 1) MainActivity.needsReload = true;
                    if (w == 2) {
                        int newest = 0;
                        for (Track v : MediaRepo.videos) newest = (int) Math.max(newest, v.dateAdded);
                        Store.putInt(this, "video_seen", newest);
                    }
                }).setPositiveButton("OK", null).show();
    }
}
