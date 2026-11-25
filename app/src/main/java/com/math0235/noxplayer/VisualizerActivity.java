package com.math0235.noxplayer;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.media.audiofx.Visualizer;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Visualiseur musical plein écran : barres qui bougent avec le son + pochette au centre. */
public class VisualizerActivity extends Activity implements PlayerService.Listener {
    private static final int REQ_MIC = 30;
    private Visualizer viz;
    private BarsView bars;
    private CoverView cover;
    private TextView title, artist;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (!Guard.isOfficial(this)) { finish(); return; }
        Ui.loadTheme(this);
        getWindow().setStatusBarColor(0xFF000000);
        getWindow().setNavigationBarColor(0xFF000000);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);
        bars = new BarsView(this);
        root.addView(bars, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(Ui.dp(this, 24), Ui.dp(this, 70), Ui.dp(this, 24), 0);
        cover = new CoverView(this, 110, 60);
        int cs = Ui.dp(this, 200);
        col.addView(cover, new LinearLayout.LayoutParams(cs, cs));
        title = Ui.text(this, "", 22, Ui.TEXT, true);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, Ui.dp(this, 22), 0, 0);
        col.addView(title, new LinearLayout.LayoutParams(-1, -2));
        artist = Ui.text(this, "", 15, Ui.SUB, false);
        artist.setGravity(Gravity.CENTER);
        col.addView(artist, new LinearLayout.LayoutParams(-1, -2));
        root.addView(col, new FrameLayout.LayoutParams(-1, -2));

        TextView close = Ui.button(this, "✕", 22, v -> finish());
        close.setTextColor(0xAAFFFFFF);
        int s = Ui.dp(this, 48);
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(s, s, Gravity.TOP | Gravity.END);
        clp.setMargins(0, Ui.dp(this, 12), Ui.dp(this, 12), 0);
        root.addView(close, clp);
        root.setOnClickListener(v -> { PlayerService ps = PlayerService.get(); if (ps != null) ps.toggle(); });
        setContentView(root);

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Ui.toast(this, "Android demande l'accès « micro » pour lire le son de la musique (rien n'est enregistré)");
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] p, int[] r) {
        startViz();
    }

    @Override
    protected void onResume() {
        super.onResume();
        PlayerService.addListener(this);
        onPlayerChanged();
        startViz();
    }

    @Override
    protected void onPause() {
        super.onPause();
        PlayerService.removeListener(this);
        stopViz();
    }

    @Override
    public void onPlayerChanged() {
        PlayerService s = PlayerService.get();
        Track t = s != null ? s.current() : null;
        if (t == null) return;
        cover.bind(t, Ui.dp(this, 200));
        title.setText(t.title);
        artist.setText(t.artist);
    }

    private void startViz() {
        if (viz != null) return;
        PlayerService s = PlayerService.get();
        if (s == null || checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            bars.idle = true;
            return;
        }
        try {
            viz = new Visualizer(s.audioSessionId());
            viz.setCaptureSize(Visualizer.getCaptureSizeRange()[1]);
            viz.setDataCaptureListener(new Visualizer.OnDataCaptureListener() {
                @Override public void onWaveFormDataCapture(Visualizer v, byte[] w, int rate) {}
                @Override public void onFftDataCapture(Visualizer v, byte[] fft, int rate) { bars.setFft(fft); }
            }, Visualizer.getMaxCaptureRate() / 2, false, true);
            viz.setEnabled(true);
            bars.idle = false;
        } catch (Throwable e) {
            viz = null;
            bars.idle = true;
        }
    }

    private void stopViz() {
        if (viz == null) return;
        try { viz.setEnabled(false); viz.release(); } catch (Throwable ignored) {}
        viz = null;
    }

    /** Barres symétriques en bas de l'écran. */
    static final class BarsView extends View {
        private static final int N = 48;
        private final float[] level = new float[N];
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        boolean idle = true;
        private long t0 = System.currentTimeMillis();

        BarsView(Context c) { super(c); }

        void setFft(byte[] fft) {
            int bins = fft.length / 2;
            for (int i = 0; i < N; i++) {
                // échelle logarithmique : plus de détails dans les basses
                int k = 1 + (int) (Math.pow((i + 1) / (float) N, 2) * (bins - 2));
                float re = fft[2 * k], im = fft[2 * k + 1];
                float mag = (float) Math.log10(1 + Math.hypot(re, im)) / 2.2f;
                level[i] = Math.max(level[i] * 0.75f, Math.min(1f, mag));
            }
            postInvalidateOnAnimation();
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            if (idle) {
                // animation douce quand le vrai son n'est pas disponible
                float tt = (System.currentTimeMillis() - t0) / 1000f;
                for (int i = 0; i < N; i++) level[i] = 0.15f + 0.12f * (float) Math.sin(tt * 2.2 + i * 0.45);
                postInvalidateOnAnimation();
            }
            p.setShader(new LinearGradient(0, h, 0, h * 0.35f, Ui.ACCENT, Ui.ACCENT2, Shader.TileMode.CLAMP));
            float slot = w / N, bw = slot * 0.62f, r = bw / 2;
            float maxH = h * 0.45f;
            for (int i = 0; i < N; i++) {
                float bh = Math.max(bw, level[i] * maxH);
                float x = i * slot + (slot - bw) / 2;
                c.drawRoundRect(x, h - Ui.dp(getContext(), 30) - bh, x + bw, h - Ui.dp(getContext(), 30), r, r, p);
            }
            if (!idle) for (int i = 0; i < N; i++) level[i] *= 0.92f;
        }
    }
}
