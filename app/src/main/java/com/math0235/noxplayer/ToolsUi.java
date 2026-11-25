package com.math0235.noxplayer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.net.Uri;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.ArrayDeque;

/** Fenêtres pour les outils vidéo : convertir en audio, couper, importer un lien. */
public final class ToolsUi {
    private ToolsUi() {}

    private static final ArrayDeque<Track> autoQueue = new ArrayDeque<>();
    private static boolean autoRunning;

    // ------------------------------------------------------------------ Progression
    private static final class Progress {
        final AlertDialog dialog;
        final ProgressBar bar;
        final TextView label;

        Progress(Activity a, String title) {
            LinearLayout box = new LinearLayout(a);
            box.setOrientation(LinearLayout.VERTICAL);
            int p = Ui.dp(a, 22);
            box.setPadding(p, p / 2, p, 0);
            label = Ui.text(a, "0 %", 14, Ui.TEXT, false);
            bar = new ProgressBar(a, null, android.R.attr.progressBarStyleHorizontal);
            bar.setMax(100);
            bar.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));
            box.addView(bar);
            box.addView(label);
            dialog = new AlertDialog.Builder(a, Ui.DIALOG).setTitle(title).setView(box).setCancelable(false).create();
            dialog.show();
        }

        void set(int pct) {
            bar.setProgress(pct);
            label.setText(pct + " %");
        }

        void close() {
            try { dialog.dismiss(); } catch (Exception ignored) {}
        }
    }

    // ------------------------------------------------------------------ Vidéo -> audio
    public static void convertToAudio(Activity a, Track video) {
        Progress pr = new Progress(a, "Conversion en audio…");
        MediaTools.extractAudio(a, video, new MediaTools.Callback() {
            @Override public void progress(int percent) { pr.set(percent); }
            @Override public void done(Uri out, String error) {
                pr.close();
                if (error != null) Ui.toast(a, error);
                else Ui.toast(a, "✓ « " + video.title + " » ajouté à ta musique (Music/NOX Player)");
            }
        });
    }

    /** Conversion silencieuse (option « nouvelles vidéos → audio auto »). */
    public static void autoConvert(Context c, Track video) {
        autoQueue.add(video);
        if (!autoRunning) runAuto(c.getApplicationContext());
    }

    private static void runAuto(Context c) {
        Track t = autoQueue.poll();
        if (t == null) { autoRunning = false; return; }
        autoRunning = true;
        MediaTools.extractAudio(c, t, new MediaTools.Callback() {
            @Override public void progress(int percent) {}
            @Override public void done(Uri out, String error) {
                if (error == null) Ui.toast(c, "♪ Audio ajouté : " + t.title);
                runAuto(c);
            }
        });
    }

    // ------------------------------------------------------------------ Importer des fichiers
    public static void importFiles(Activity a, java.util.List<Uri> uris) {
        if (uris.isEmpty()) return;
        Progress pr = new Progress(a, "Import de " + uris.size() + (uris.size() > 1 ? " musiques…" : " musique…"));
        MediaTools.importFiles(a, uris, new MediaTools.Callback() {
            @Override public void progress(int percent) { pr.set(percent); }
            @Override public void done(Uri out, String error) {
                pr.close();
                Ui.toast(a, error != null ? error : "✓ Ajouté à ta bibliothèque (Music/NOX Player)");
            }
        });
    }

    // ------------------------------------------------------------------ Import par lien
    public static void importLinkDialog(Activity a) {
        EditText in = new EditText(a);
        in.setHint("https://…/musique.mp3");
        in.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        in.setSingleLine(true);
        in.setTextColor(Ui.TEXT);
        in.setHintTextColor(Ui.SUB);
        ClipboardManager cm = (ClipboardManager) a.getSystemService(Context.CLIPBOARD_SERVICE);
        try {
            ClipData clip = cm.getPrimaryClip();
            if (clip != null && clip.getItemCount() > 0) {
                CharSequence t = clip.getItemAt(0).getText();
                if (t != null && t.toString().startsWith("http")) in.setText(t);
            }
        } catch (Exception ignored) {}
        LinearLayout box = new LinearLayout(a);
        int p = Ui.dp(a, 22);
        box.setPadding(p, p / 2, p, 0);
        box.addView(in, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        new AlertDialog.Builder(a, Ui.DIALOG)
                .setTitle("🔗  Importer depuis un lien direct")
                .setMessage("Colle le lien d'un fichier audio ou vidéo (.mp3, .m4a, .flac, .mp4…). Il sera téléchargé dans ta bibliothèque.")
                .setView(box)
                .setNegativeButton("Annuler", null)
                .setPositiveButton("Télécharger", (d, w) -> {
                    String err = MediaTools.downloadLink(a, in.getText().toString());
                    Ui.toast(a, err != null ? err : "⬇ Téléchargement lancé — il apparaîtra tout seul dans la bibliothèque");
                })
                .show();
    }
}
