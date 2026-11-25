package com.math0235.noxplayer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.MotionEvent;
import android.view.View;

import java.util.Random;

/** Onde douce au-dessus de la barre (comme Lark Player). La partie jouée est plus claire. Touchable pour avancer. */
public class WaveView extends View {
    public interface OnSeek { void seek(float fraction); }

    private final float[] amp = new float[48];
    private final Paint rest = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint played = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private float progress;
    private OnSeek onSeek;

    public WaveView(Context c) {
        super(c);
        setColors(0x33FFFFFF, 0x88FFFFFF);
        setSeed(1);
    }

    public void setColors(int restColor, int playedColor) {
        rest.setColor(restColor);
        played.setColor(playedColor);
        invalidate();
    }

    public void setOnSeek(OnSeek s) { onSeek = s; }

    /** Forme différente pour chaque chanson (mais toujours la même pour une chanson donnée). */
    public void setSeed(long seed) {
        Random r = new Random(seed * 31 + 7);
        int bumps = 3 + r.nextInt(3);
        float[] cx = new float[bumps], hy = new float[bumps], wd = new float[bumps];
        for (int b = 0; b < bumps; b++) {
            cx[b] = 0.2f + r.nextFloat() * 0.65f;
            hy[b] = 0.35f + r.nextFloat() * 0.65f;
            wd[b] = 0.06f + r.nextFloat() * 0.1f;
        }
        for (int i = 0; i < amp.length; i++) {
            float x = i / (float) (amp.length - 1);
            float v = 0.05f;
            for (int b = 0; b < bumps; b++) {
                float d = (x - cx[b]) / wd[b];
                v += hy[b] * (float) Math.exp(-d * d / 2);
            }
            float edge = Math.min(1f, Math.min(x, 1 - x) * 6f); // retombe à zéro sur les bords
            amp[i] = Math.min(1f, v) * edge;
        }
        invalidate();
    }

    public void setProgress(float f) {
        f = Math.max(0f, Math.min(1f, f));
        if (Math.abs(f - progress) > 0.001f) { progress = f; invalidate(); }
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        path.reset();
        path.moveTo(0, h);
        float step = w / (amp.length - 1);
        float px = 0, py = h - amp[0] * h * 0.95f;
        path.lineTo(px, py);
        for (int i = 1; i < amp.length; i++) {
            float x = i * step, y = h - amp[i] * h * 0.95f;
            float mx = (px + x) / 2f, my = (py + y) / 2f;
            path.quadTo(px, py, mx, my);
            px = x;
            py = y;
        }
        path.lineTo(w, py);
        path.lineTo(w, h);
        path.close();
        c.drawPath(path, rest);
        c.save();
        c.clipRect(0, 0, w * progress, h);
        c.drawPath(path, played);
        c.restore();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (onSeek == null) return false;
        int a = e.getActionMasked();
        float f = Math.max(0f, Math.min(1f, e.getX() / Math.max(1, getWidth())));
        if (a == MotionEvent.ACTION_DOWN || a == MotionEvent.ACTION_MOVE) {
            getParent().requestDisallowInterceptTouchEvent(true);
            setProgress(f);
            return true;
        }
        if (a == MotionEvent.ACTION_UP) {
            onSeek.seek(f);
            return true;
        }
        return true;
    }
}
