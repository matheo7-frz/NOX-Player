package com.math0235.noxplayer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** Icônes lecture / pause / suivant / précédent dessinées à la main (nettes à toutes les tailles). */
public class IconView extends View {
    public static final int PLAY = 0, PAUSE = 1, NEXT = 2, PREV = 3, REW = 4, FF = 5, EQ = 6, QUEUE = 7, HEART = 8, MORE = 9;
    private int kind;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float ringValue = -1f;

    public IconView(Context c, int kind, int color) {
        super(c);
        this.kind = kind;
        paint.setColor(color);
        paint.setStyle(Paint.Style.FILL);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeCap(Paint.Cap.ROUND);
    }

    private boolean filled = true;
    public void setFilled(boolean f) { filled = f; invalidate(); }

    public void setKind(int k) {
        if (k != kind) { kind = k; invalidate(); }
    }

    public void setColor(int c) { paint.setColor(c); invalidate(); }

    /** Anneau de progression autour de l'icône (0..1), comme Lark Player. */
    public void setRing(float v) {
        ringValue = Math.max(0f, Math.min(1f, v));
        invalidate();
    }

    @Override
    protected void onDraw(Canvas cv) {
        float w = getWidth(), h = getHeight();
        float s = Math.min(w, h), cx = w / 2f, cy = h / 2f;
        path.reset();
        if (ringValue >= 0f) {
            float sw = s * 0.06f, r = s / 2f - sw;
            ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeWidth(sw);
            ring.setStrokeCap(Paint.Cap.ROUND);
            ring.setColor(0x33FFFFFF);
            cv.drawCircle(cx, cy, r, ring);
            ring.setColor(Ui.ACCENT2);
            cv.drawArc(cx - r, cy - r, cx + r, cy + r, -90, 360f * ringValue, false, ring);
            s = s * 0.75f;
        }
        switch (kind) {
            case PLAY:
                path.moveTo(cx - s * 0.20f, cy - s * 0.30f);
                path.lineTo(cx - s * 0.20f, cy + s * 0.30f);
                path.lineTo(cx + s * 0.30f, cy);
                path.close();
                cv.drawPath(path, paint);
                break;
            case PAUSE: {
                float bw = s * 0.14f, bh = s * 0.30f, gap = s * 0.08f, r = s * 0.04f;
                cv.drawRoundRect(cx - gap - bw, cy - bh, cx - gap, cy + bh, r, r, paint);
                cv.drawRoundRect(cx + gap, cy - bh, cx + gap + bw, cy + bh, r, r, paint);
                break;
            }
            case NEXT:
            case PREV: {
                float dir = kind == NEXT ? 1f : -1f;
                path.moveTo(cx - dir * s * 0.24f, cy - s * 0.24f);
                path.lineTo(cx - dir * s * 0.24f, cy + s * 0.24f);
                path.lineTo(cx + dir * s * 0.14f, cy);
                path.close();
                cv.drawPath(path, paint);
                float x1 = cx + dir * s * 0.16f, x2 = cx + dir * s * 0.25f;
                cv.drawRoundRect(Math.min(x1, x2), cy - s * 0.24f, Math.max(x1, x2), cy + s * 0.24f,
                        s * 0.03f, s * 0.03f, paint);
                break;
            }
            case REW:
            case FF: {
                // deux triangles arrondis (style Lark)
                float dir = kind == FF ? 1f : -1f;
                paint.setStyle(Paint.Style.FILL_AND_STROKE);
                paint.setStrokeWidth(s * 0.07f);
                for (int k = 0; k < 2; k++) {
                    float off = (k == 0 ? -0.17f : 0.17f) * s * dir;
                    path.reset();
                    path.moveTo(cx + off - dir * s * 0.17f, cy - s * 0.17f);
                    path.lineTo(cx + off - dir * s * 0.17f, cy + s * 0.17f);
                    path.lineTo(cx + off + dir * s * 0.13f, cy);
                    path.close();
                    cv.drawPath(path, paint);
                }
                paint.setStyle(Paint.Style.FILL);
                break;
            }
            case EQ: {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(s * 0.07f);
                float[] xs = {cx - s * 0.2f, cx, cx + s * 0.2f};
                float[] ks = {-0.1f, 0.12f, -0.02f};
                for (int k = 0; k < 3; k++) {
                    cv.drawLine(xs[k], cy - s * 0.3f, xs[k], cy + s * 0.3f, paint);
                    paint.setStyle(Paint.Style.FILL);
                    cv.drawCircle(xs[k], cy + ks[k] * s, s * 0.08f, paint);
                    paint.setStyle(Paint.Style.STROKE);
                }
                paint.setStyle(Paint.Style.FILL);
                break;
            }
            case QUEUE: {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(s * 0.07f);
                cv.drawLine(cx - s * 0.3f, cy - s * 0.2f, cx + s * 0.05f, cy - s * 0.2f, paint);
                cv.drawLine(cx - s * 0.3f, cy, cx + s * 0.05f, cy, paint);
                cv.drawLine(cx - s * 0.3f, cy + s * 0.22f, cx + s * 0.3f, cy + s * 0.22f, paint);
                paint.setStyle(Paint.Style.FILL);
                path.reset();
                path.moveTo(cx + s * 0.13f, cy - s * 0.28f);
                path.lineTo(cx + s * 0.13f, cy + s * 0.08f);
                path.lineTo(cx + s * 0.32f, cy - s * 0.1f);
                path.close();
                cv.drawPath(path, paint);
                break;
            }
            case HEART: {
                float r = s * 0.17f;
                path.reset();
                path.moveTo(cx, cy + s * 0.27f);
                path.cubicTo(cx - s * 0.45f, cy - s * 0.02f, cx - s * 0.25f, cy - s * 0.38f, cx, cy - s * 0.14f);
                path.cubicTo(cx + s * 0.25f, cy - s * 0.38f, cx + s * 0.45f, cy - s * 0.02f, cx, cy + s * 0.27f);
                path.close();
                paint.setStyle(filled ? Paint.Style.FILL_AND_STROKE : Paint.Style.STROKE);
                paint.setStrokeWidth(s * 0.06f);
                cv.drawPath(path, paint);
                paint.setStyle(Paint.Style.FILL);
                break;
            }
            case MORE:
                for (int k = -1; k <= 1; k++) cv.drawCircle(cx, cy + k * s * 0.2f, s * 0.065f, paint);
                break;
        }
    }
}
