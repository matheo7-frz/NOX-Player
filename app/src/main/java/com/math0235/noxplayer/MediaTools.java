package com.math0235.noxplayer;

import android.app.DownloadManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.media.MediaMuxer;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.webkit.MimeTypeMap;
import android.webkit.URLUtil;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Outils vidéo sans perte de qualité (pas de ré-encodage) :
 *  - convertir une vidéo en audio seul (.m4a)
 *  - couper une vidéo (début / fin)
 *  - importer un fichier audio/vidéo depuis un lien direct
 */
public final class MediaTools {
    public interface Callback {
        void progress(int percent);
        void done(Uri out, String error);
    }

    public static final String FOLDER = "NOX Player";
    private static final ExecutorService worker = Executors.newSingleThreadExecutor();
    private static final Handler main = new Handler(Looper.getMainLooper());

    private MediaTools() {}

    // ------------------------------------------------------------------ Vidéo -> audio
    public static void extractAudio(Context ctx, Track video, Callback cb) {
        final Context c = ctx.getApplicationContext();
        worker.execute(() -> run(c, video, true, 0, 0, cb));
    }

    private static void run(Context c, Track src, boolean audioOnly, long startUs, long endUs, Callback cb) {
        MediaExtractor ex = new MediaExtractor();
        MediaMuxer mux = null;
        Output out = null;
        try {
            ex.setDataSource(c, src.uri(), null);
            int audio = -1, video = -1;
            for (int i = 0; i < ex.getTrackCount(); i++) {
                String mime = ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME);
                if (mime == null) continue;
                if (mime.startsWith("audio/") && audio < 0) audio = i;
                if (mime.startsWith("video/") && video < 0) video = i;
            }
            if (audioOnly && audio < 0) { post(cb, null, "Cette vidéo n'a pas de son."); return; }
            if (!audioOnly && video < 0) { post(cb, null, "Aucune piste vidéo trouvée."); return; }

            String aMime = audio >= 0 ? ex.getTrackFormat(audio).getString(MediaFormat.KEY_MIME) : "";
            int fmt = MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4;
            String ext = audioOnly ? "m4a" : "mp4";
            String outMime = audioOnly ? "audio/mp4" : "video/mp4";
            if (audioOnly && "audio/opus".equals(aMime) && Build.VERSION.SDK_INT >= 29) {
                fmt = MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG; ext = "ogg"; outMime = "audio/ogg";
            } else if (audioOnly && "audio/vorbis".equals(aMime)) {
                fmt = MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM; ext = "webm"; outMime = "audio/webm";
            } else if (!audioOnly) {
                String vMime = ex.getTrackFormat(video).getString(MediaFormat.KEY_MIME);
                if ("video/x-vnd.on2.vp8".equals(vMime) || "video/x-vnd.on2.vp9".equals(vMime)) {
                    fmt = MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM; ext = "webm"; outMime = "video/webm";
                }
            }

            String base = safeName(src.title) + (audioOnly ? "" : " (coupé)");
            out = Output.create(c, base, ext, outMime, audioOnly, src.title);
            mux = out.fd != null ? new MediaMuxer(out.fd.getFileDescriptor(), fmt) : new MediaMuxer(out.file.getAbsolutePath(), fmt);

            int[] map = new int[ex.getTrackCount()];
            for (int i = 0; i < map.length; i++) map[i] = -1;
            int maxIn = 512 * 1024;
            if (audio >= 0) {
                ex.selectTrack(audio);
                MediaFormat f = ex.getTrackFormat(audio);
                map[audio] = mux.addTrack(f);
                if (f.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) maxIn = Math.max(maxIn, f.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE));
            }
            if (!audioOnly) {
                ex.selectTrack(video);
                MediaFormat f = ex.getTrackFormat(video);
                map[video] = mux.addTrack(f);
                if (f.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) maxIn = Math.max(maxIn, f.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE));
                MediaMetadataRetriever r = new MediaMetadataRetriever();
                try {
                    r.setDataSource(c, src.uri());
                    String rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
                    if (rot != null) mux.setOrientationHint(Integer.parseInt(rot));
                } catch (Exception ignored) {
                } finally {
                    try { r.release(); } catch (Exception ignored) {}
                }
            }
            mux.start();

            long durUs = src.duration * 1000;
            if (durUs <= 0 && audio >= 0 && ex.getTrackFormat(audio).containsKey(MediaFormat.KEY_DURATION)) {
                durUs = ex.getTrackFormat(audio).getLong(MediaFormat.KEY_DURATION);
            }
            durUs = Math.max(1, durUs);
            if (startUs > 0) ex.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
            long first = Math.max(0, ex.getSampleTime());
            long stop = endUs > 0 ? endUs : Long.MAX_VALUE;
            long span = Math.max(1, Math.min(stop, durUs) - first);

            ByteBuffer buf = ByteBuffer.allocate(maxIn);
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            int lastPct = -1;
            while (true) {
                int size = ex.readSampleData(buf, 0);
                if (size < 0) break;
                long t = ex.getSampleTime();
                if (t > stop) break;
                int ti = ex.getSampleTrackIndex();
                if (t >= first && ti >= 0 && map[ti] >= 0) {
                    info.offset = 0;
                    info.size = size;
                    info.presentationTimeUs = t - first;
                    info.flags = (ex.getSampleFlags() & MediaExtractor.SAMPLE_FLAG_SYNC) != 0
                            ? MediaCodec.BUFFER_FLAG_KEY_FRAME : 0;
                    mux.writeSampleData(map[ti], buf, info);
                }
                ex.advance();
                int pct = (int) Math.min(99, (t - first) * 100 / span);
                if (pct != lastPct) { lastPct = pct; final int p = pct; main.post(() -> cb.progress(p)); }
            }
            mux.stop();
            mux.release();
            mux = null;
            out.finish(c);
            post(cb, out.uri, null);
        } catch (Exception e) {
            if (out != null) out.fail(c);
            post(cb, null, "Échec : " + (e.getMessage() == null ? "format non supporté" : e.getMessage()));
        } finally {
            try { if (mux != null) mux.release(); } catch (Exception ignored) {}
            ex.release();
        }
    }

    private static void post(Callback cb, Uri u, String err) {
        main.post(() -> cb.done(u, err));
    }

    static String safeName(String s) {
        String n = s == null ? "NOX" : s.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        if (n.isEmpty()) n = "NOX";
        return n.length() > 80 ? n.substring(0, 80) : n;
    }

    /** Fichier de sortie : MediaStore (Android 10+) ou dossier public (Android 8-9). */
    private static final class Output {
        Uri uri;
        ParcelFileDescriptor fd;
        File file;

        @SuppressWarnings("deprecation")
        static Output create(Context c, String base, String ext, String mime, boolean audio, String title) throws Exception {
            Output o = new Output();
            String name = base + "." + ext;
            if (Build.VERSION.SDK_INT >= 29) {
                ContentResolver cr = c.getContentResolver();
                ContentValues v = new ContentValues();
                v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
                v.put(MediaStore.MediaColumns.MIME_TYPE, mime);
                v.put(MediaStore.MediaColumns.TITLE, title);
                v.put(MediaStore.MediaColumns.IS_PENDING, 1);
                Uri col;
                if (audio) {
                    v.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/" + FOLDER);
                    v.put(MediaStore.Audio.Media.IS_MUSIC, 1);
                    col = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
                } else {
                    v.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/" + FOLDER);
                    col = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
                }
                o.uri = cr.insert(col, v);
                if (o.uri == null) throw new Exception("impossible de créer le fichier");
                o.fd = cr.openFileDescriptor(o.uri, "rw");
            } else {
                File dir = new File(Environment.getExternalStoragePublicDirectory(
                        audio ? Environment.DIRECTORY_MUSIC : Environment.DIRECTORY_MOVIES), FOLDER);
                if (!dir.exists()) dir.mkdirs();
                File f = new File(dir, name);
                int i = 2;
                while (f.exists()) f = new File(dir, base + " (" + (i++) + ")." + ext);
                o.file = f;
                o.uri = Uri.fromFile(f);
            }
            return o;
        }

        void finish(Context c) {
            try { if (fd != null) fd.close(); } catch (Exception ignored) {}
            if (Build.VERSION.SDK_INT >= 29 && uri != null) {
                ContentValues v = new ContentValues();
                v.put(MediaStore.MediaColumns.IS_PENDING, 0);
                c.getContentResolver().update(uri, v, null, null);
            } else if (file != null) {
                MediaScannerConnection.scanFile(c, new String[]{file.getAbsolutePath()}, null, null);
            }
        }

        void fail(Context c) {
            try { if (fd != null) fd.close(); } catch (Exception ignored) {}
            try {
                if (Build.VERSION.SDK_INT >= 29 && uri != null) c.getContentResolver().delete(uri, null, null);
                else if (file != null) file.delete();
            } catch (Exception ignored) {}
        }
    }

    // ------------------------------------------------------------------ Importer des fichiers audio
    public static void importFiles(Context ctx, java.util.List<Uri> uris, Callback cb) {
        final Context c = ctx.getApplicationContext();
        worker.execute(() -> {
            int ok = 0;
            for (int i = 0; i < uris.size(); i++) {
                Uri u = uris.get(i);
                Output out = null;
                try {
                    Track t = Track.fromUri(c, u, false);
                    String mime = c.getContentResolver().getType(u);
                    if (mime == null || !mime.startsWith("audio")) mime = "audio/mpeg";
                    String ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime);
                    if (ext == null) ext = "mp3";
                    out = Output.create(c, safeName(t.title), ext, mime, true, t.title);
                    try (java.io.InputStream in = c.getContentResolver().openInputStream(u);
                         java.io.OutputStream os = out.fd != null
                                 ? new java.io.FileOutputStream(out.fd.getFileDescriptor())
                                 : new java.io.FileOutputStream(out.file)) {
                        if (in == null) throw new Exception("fichier illisible");
                        byte[] b = new byte[64 * 1024];
                        int n;
                        while ((n = in.read(b)) > 0) os.write(b, 0, n);
                    }
                    out.finish(c);
                    ok++;
                } catch (Exception e) {
                    if (out != null) out.fail(c);
                }
                final int p = (i + 1) * 100 / uris.size();
                main.post(() -> cb.progress(p));
            }
            final int done = ok;
            post(cb, null, done == uris.size() ? null : (uris.size() - done) + " fichier(s) n'ont pas pu être importés");
        });
    }

    // ------------------------------------------------------------------ Import par lien direct
    private static final String[] AUDIO_EXT = {"mp3", "m4a", "aac", "wav", "flac", "ogg", "opus", "wma", "amr"};
    private static final String[] VIDEO_EXT = {"mp4", "mkv", "webm", "3gp", "mov", "avi"};

    /** @return message d'erreur, ou null si le téléchargement a démarré */
    public static String downloadLink(Context c, String url) {
        url = url == null ? "" : url.trim();
        if (!URLUtil.isNetworkUrl(url)) return "Lien invalide.";
        String ext = MimeTypeMap.getFileExtensionFromUrl(url).toLowerCase(Locale.ROOT);
        boolean audio = contains(AUDIO_EXT, ext), video = contains(VIDEO_EXT, ext);
        if (!audio) {
            return video ? "C'est une vidéo : télécharge-la puis utilise « Convertir une vidéo en audio »."
                    : "Ce lien ne mène pas directement à un fichier audio (il doit finir par .mp3, .m4a, .flac…).";
        }
        String name = URLUtil.guessFileName(url, null, MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext));
        DownloadManager.Request r = new DownloadManager.Request(Uri.parse(url))
                .setTitle(name)
                .setDescription("NOX Player")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(audio ? Environment.DIRECTORY_MUSIC : Environment.DIRECTORY_MOVIES,
                        FOLDER + "/" + name);
        DownloadManager dm = (DownloadManager) c.getSystemService(Context.DOWNLOAD_SERVICE);
        dm.enqueue(r);
        return null;
    }

    private static boolean contains(String[] a, String s) {
        for (String x : a) if (x.equals(s)) return true;
        return false;
    }
}
