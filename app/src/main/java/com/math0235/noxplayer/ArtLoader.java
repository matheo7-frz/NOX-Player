package com.math0235.noxplayer;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.util.Size;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Charge les pochettes d'album / miniatures vidéo en arrière-plan, avec cache. */
public final class ArtLoader {
    public interface Callback { void done(Bitmap b); }

    private static final LruCache<String, Bitmap> cache = new LruCache<String, Bitmap>(24 * 1024 * 1024) {
        @Override protected int sizeOf(String k, Bitmap b) { return b.getByteCount(); }
    };
    private static final Set<String> missing = Collections.synchronizedSet(new HashSet<>());
    private static final ExecutorService pool = Executors.newFixedThreadPool(3);
    private static final Handler main = new Handler(Looper.getMainLooper());

    private ArtLoader() {}

    private static String key(Track t, int size) { return t.artKey() + "@" + size; }

    public static Bitmap cached(Track t, int size) { return cache.get(key(t, size)); }

    public static void load(Context c, Track t, int size, Callback cb) {
        final String k = key(t, size);
        Bitmap b = cache.get(k);
        if (b != null) { cb.done(b); return; }
        if (missing.contains(k)) { cb.done(null); return; }
        final Context app = c.getApplicationContext();
        pool.execute(() -> {
            Bitmap r = decode(app, t, size);
            if (r != null) cache.put(k, r); else missing.add(k);
            main.post(() -> cb.done(r));
        });
    }

    public static Bitmap decode(Context c, Track t, int size) {
        java.io.File custom = t.customCover(c);
        if (custom != null) {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(custom.getAbsolutePath(), o);
            int s = 1;
            while (o.outWidth / (s * 2) >= size && o.outHeight / (s * 2) >= size) s *= 2;
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = s;
            Bitmap b = BitmapFactory.decodeFile(custom.getAbsolutePath(), o2);
            if (b != null) return b;
        }
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                return c.getContentResolver().loadThumbnail(t.uri(), new Size(size, size), null);
            } catch (Exception ignored) {}
        }
        MediaMetadataRetriever m = new MediaMetadataRetriever();
        try {
            m.setDataSource(c, t.uri());
            if (!t.video) {
                byte[] d = m.getEmbeddedPicture();
                if (d == null) return null;
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(d, 0, d.length, o);
                int s = 1;
                while (o.outWidth / (s * 2) >= size && o.outHeight / (s * 2) >= size) s *= 2;
                BitmapFactory.Options o2 = new BitmapFactory.Options();
                o2.inSampleSize = s;
                return BitmapFactory.decodeByteArray(d, 0, d.length, o2);
            } else {
                Bitmap f = m.getFrameAtTime(1_000_000);
                if (f == null) return null;
                float ratio = (float) size / Math.max(f.getWidth(), f.getHeight());
                if (ratio < 1f) {
                    return Bitmap.createScaledBitmap(f, Math.max(1, (int) (f.getWidth() * ratio)),
                            Math.max(1, (int) (f.getHeight() * ratio)), true);
                }
                return f;
            }
        } catch (Exception e) {
            return null;
        } finally {
            try { m.release(); } catch (Exception ignored) {}
        }
    }
}
