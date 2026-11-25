package com.math0235.noxplayer;

import android.Manifest;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.RingtoneManager;
import android.media.audiofx.BassBoost;
import android.media.audiofx.Equalizer;
import android.media.audiofx.Visualizer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pont Java minimal : héberge l'interface JavaScript (assets/web) dans une WebView verrouillée
 * et lui donne accès au lecteur, à la bibliothèque et au cœur C++.
 * Toute l'interface est en JavaScript ; ce fichier ne contient aucune logique d'écran.
 */
public class MainActivity extends Activity implements PlayerService.Listener {
    private static final String HOST = "nox.local";
    private static final String ORIGIN = "https://" + HOST;
    private static final int REQ_PERM = 1, REQ_COVER = 2, REQ_IMPORT = 3, REQ_VIDEO = 4,
            REQ_SAVE = 5, REQ_RESTORE = 6, REQ_DELETE = 7, REQ_MIC = 8;

    private WebView web;
    private final Handler h = new Handler(Looper.getMainLooper());
    private ContentObserver observer;
    private boolean loading, ready;
    private long coverTargetId = -1;
    private List<Track> pendingDelete;
    private Visualizer viz;
    private final float[] vizLevels = new float[48];
    private long lastVizPush;
    private final Map<Long, Track> extTracks = new HashMap<>();

    private final Runnable reload = this::loadMedia;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            PlayerService s = PlayerService.get();
            if (s != null && s.isPlaying()) emit("tick", stateJson().toString());
            h.postDelayed(this, 500);
        }
    };

    // =================================================================== Cycle de vie
    @Override
    @SuppressWarnings("SetJavaScriptEnabled")
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(0xFF0B0714);
        getWindow().setNavigationBarColor(0xFF0B0714);
        web = new WebView(this);
        web.setBackgroundColor(0xFF0B0714);
        setContentView(web);

        if (!Native.ok(this)) {
            // Copie modifiée / re-signée : on n'exécute rien de l'appli.
            web.loadDataWithBaseURL(null, "<html><body style='background:#8B0000;color:#fff;font-family:sans-serif;"
                    + "display:flex;align-items:center;justify-content:center;height:90vh;text-align:center;padding:24px'>"
                    + "<div><h1>⚠</h1><h2>Cette copie de NOX Player a été modifiée.</h2>"
                    + "<p>Elle n'est pas signée par math_0235 et peut contenir un virus.<br>"
                    + "Désinstalle-la et télécharge uniquement la version officielle.</p></div></body></html>",
                    "text/html", "utf-8", null);
            return;
        }

        WebSettings ws = web.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setAllowFileAccess(false);
        ws.setAllowContentAccess(false);
        ws.setMediaPlaybackRequiresUserGesture(true);
        ws.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        ws.setSupportZoom(false);
        ws.setTextZoom(100);
        web.setOverScrollMode(WebView.OVER_SCROLL_NEVER);
        web.setVerticalScrollBarEnabled(false);
        web.addJavascriptInterface(new Bridge(), "NOX");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                return true; // aucune navigation vers l'extérieur : l'interface ne peut pas être détournée
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                return serve(r.getUrl());
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                ready = true;
                if (hasPerm()) loadMedia(); else emit("perm", "false");
                handleIntent(getIntent());
            }
        });

        observer = new ContentObserver(h) {
            @Override public void onChange(boolean self) {
                h.removeCallbacks(reload);
                h.postDelayed(reload, 1500);
            }
        };
        getContentResolver().registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, observer);
        getContentResolver().registerContentObserver(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, observer);
        web.loadUrl(ORIGIN + "/index.html");
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        setIntent(i);
        if (Native.ok(this)) handleIntent(i);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!Native.ok(this)) return;
        PlayerService.addListener(this);
        h.post(tick);
        if (ready) { emit("state", stateJson().toString()); emit("resume", "{}"); }
    }

    @Override
    protected void onPause() {
        super.onPause();
        PlayerService.removeListener(this);
        h.removeCallbacks(tick);
        stopViz();
    }

    @Override
    protected void onDestroy() {
        if (observer != null) getContentResolver().unregisterContentObserver(observer);
        h.removeCallbacksAndMessages(null);
        if (web != null) web.destroy();
        super.onDestroy();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (!ready) { super.onBackPressed(); return; }
        web.evaluateJavascript("window.NOX_back ? NOX_back() : false", v -> {
            if (!"true".equals(v)) moveTaskToBack(true);
        });
    }

    @Override
    public void onPlayerChanged() {
        if (ready) emit("state", stateJson().toString());
    }

    private void handleIntent(Intent i) {
        if (i == null) return;
        if (i.getBooleanExtra("openPlayer", false)) { emit("open", "\"player\""); i.removeExtra("openPlayer"); }
        if (!Intent.ACTION_VIEW.equals(i.getAction()) || i.getData() == null) return;
        Uri u = i.getData();
        String type = i.getType() != null ? i.getType() : getContentResolver().getType(u);
        if (type != null && type.startsWith("video")) {
            convertVideo(Track.fromUri(this, u, true));
        } else {
            Track t = Track.fromUri(this, u, false);
            t.id = -1000 - extTracks.size();
            extTracks.put(t.id, t);
            PlayerService.playQueue(this, Collections.singletonList(t), 0);
            emit("open", "\"player\"");
        }
        i.setAction(null);
    }

    // =================================================================== JS <- Java
    private void emit(String type, String json) {
        if (web == null) return;
        String js = "window.NOX_event && NOX_event(" + JSONObject.quote(type) + "," + json + ")";
        if (Looper.myLooper() == Looper.getMainLooper()) web.evaluateJavascript(js, null);
        else h.post(() -> web.evaluateJavascript(js, null));
    }

    private void toast(String s) {
        h.post(() -> Toast.makeText(this, s, Toast.LENGTH_SHORT).show());
    }

    // =================================================================== Fichiers servis à la WebView
    private WebResourceResponse serve(Uri u) {
        if (u == null || !HOST.equals(u.getHost())) {
            return new WebResourceResponse("text/plain", "utf-8", 403, "Forbidden", null, new ByteArrayInputStream(new byte[0]));
        }
        String path = u.getPath() == null ? "/" : u.getPath();
        try {
            if (path.startsWith("/art/")) {
                long id = Long.parseLong(path.substring(5));
                int size = 300;
                try { size = Math.max(64, Math.min(800, Integer.parseInt(u.getQueryParameter("s")))); } catch (Exception ignored) {}
                Track t = id < 0 ? extTracks.get(id) : MediaRepo.get(id);
                Bitmap bm = t == null ? null : ArtLoader.decode(this, t, size);
                if (bm == null) return notFound();
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                bm.compress(Bitmap.CompressFormat.JPEG, 88, bo);
                WebResourceResponse r = new WebResourceResponse("image/jpeg", null, new ByteArrayInputStream(bo.toByteArray()));
                Map<String, String> hd = new HashMap<>();
                hd.put("Cache-Control", "max-age=86400");
                r.setResponseHeaders(hd);
                return r;
            }
            if (path.equals("/") || path.contains("..")) path = "/index.html";
            String mime = path.endsWith(".html") ? "text/html" : path.endsWith(".js") ? "application/javascript"
                    : path.endsWith(".css") ? "text/css" : path.endsWith(".jpg") ? "image/jpeg"
                    : path.endsWith(".png") ? "image/png" : path.endsWith(".svg") ? "image/svg+xml" : "application/octet-stream";
            InputStream in = getAssets().open("web" + path);
            WebResourceResponse r = new WebResourceResponse(mime, "utf-8", in);
            Map<String, String> hd = new HashMap<>();
            hd.put("Content-Security-Policy", "default-src 'self'; img-src 'self' data:; style-src 'self' 'unsafe-inline'; "
                    + "script-src 'self'; connect-src 'none'; object-src 'none'; frame-src 'none'; base-uri 'none'; form-action 'none'");
            hd.put("X-Content-Type-Options", "nosniff");
            r.setResponseHeaders(hd);
            return r;
        } catch (Exception e) {
            return notFound();
        }
    }

    private static WebResourceResponse notFound() {
        return new WebResourceResponse("text/plain", "utf-8", 404, "Not Found", null, new ByteArrayInputStream(new byte[0]));
    }

    // =================================================================== Bibliothèque
    private boolean hasPerm() {
        String p = Build.VERSION.SDK_INT >= 33 ? Manifest.permission.READ_MEDIA_AUDIO : Manifest.permission.READ_EXTERNAL_STORAGE;
        return checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED;
    }

    private void askPerm() {
        String[] perms;
        if (Build.VERSION.SDK_INT >= 33) perms = new String[]{Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.POST_NOTIFICATIONS};
        else if (Build.VERSION.SDK_INT >= 29) perms = new String[]{Manifest.permission.READ_EXTERNAL_STORAGE};
        else perms = new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE};
        requestPermissions(perms, REQ_PERM);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] p, int[] r) {
        if (code == REQ_MIC) { startViz(); return; }
        if (hasPerm()) loadMedia(); else emit("perm", "false");
    }

    private void loadMedia() {
        if (!hasPerm() || loading || !ready) return;
        loading = true;
        new Thread(() -> {
            MediaRepo.load(this);
            String lib = libraryJson().toString();
            h.post(() -> {
                loading = false;
                emit("library", lib);
                checkAutoConvert();
            });
        }).start();
    }

    private JSONObject trackJson(Track t) {
        JSONObject o = new JSONObject();
        try {
            o.put("id", t.id).put("title", t.title).put("artist", t.artist).put("album", t.album)
                    .put("folder", t.folder).put("path", t.path).put("duration", t.duration)
                    .put("added", t.dateAdded).put("art", "/art/" + t.id + "?v=" + t.coverStamp)
                    .put("custom", t.customCover(this) != null);
        } catch (Exception ignored) {}
        return o;
    }

    private JSONObject libraryJson() {
        JSONObject o = new JSONObject();
        try {
            JSONArray songs = new JSONArray(), hid = new JSONArray(), tr = new JSONArray();
            List<Track> copy;
            synchronized (MediaRepo.class) { copy = new ArrayList<>(MediaRepo.songs); }
            for (Track t : copy) songs.put(trackJson(t).put("plays", Store.plays(this, t.id)));
            for (Track t : new ArrayList<>(MediaRepo.hidden)) hid.put(trackJson(t));
            for (Track t : new ArrayList<>(MediaRepo.trash)) tr.put(trackJson(t));
            JSONArray favs = new JSONArray();
            for (Long id : Store.favs(this)) favs.put(id.longValue());
            JSONArray recent = new JSONArray();
            for (Long id : Store.recent(this)) recent.put(id.longValue());
            JSONObject pls = new JSONObject();
            LinkedHashMap<String, List<Long>> m = Store.playlists(this);
            for (String k : m.keySet()) {
                JSONArray a = new JSONArray();
                for (Long id : m.get(k)) a.put(id.longValue());
                pls.put(k, a);
            }
            o.put("songs", songs).put("hidden", hid).put("trash", tr).put("favs", favs)
                    .put("recent", recent).put("playlists", pls).put("videos", MediaRepo.videos.size());
        } catch (Exception ignored) {}
        return o;
    }

    private JSONObject stateJson() {
        JSONObject o = new JSONObject();
        PlayerService s = PlayerService.get();
        try {
            if (s == null || s.current() == null) return o.put("cur", JSONObject.NULL);
            Track t = s.current();
            if (t.id < 0) extTracks.put(t.id, t);
            JSONArray q = new JSONArray();
            for (Track x : s.orderedQueue()) q.put(trackJson(x));
            o.put("cur", trackJson(t)).put("playing", s.isPlaying()).put("pos", s.position())
                    .put("dur", s.duration()).put("mode", s.mode()).put("speed", (double) s.speed())
                    .put("sleep", s.sleepRemaining()).put("queue", q).put("qpos", s.queuePosition())
                    .put("fav", t.id >= 0 && Store.isFav(this, t.id));
        } catch (Exception ignored) {}
        return o;
    }

    private Track find(long id) {
        Track t = MediaRepo.get(id);
        if (t != null) return t;
        if (extTracks.containsKey(id)) return extTracks.get(id);
        for (Track x : new ArrayList<>(MediaRepo.hidden)) if (x.id == id) return x;
        for (Track x : new ArrayList<>(MediaRepo.trash)) if (x.id == id) return x;
        return null;
    }

    private void checkAutoConvert() {
        int newest = 0;
        for (Track v : MediaRepo.videos) newest = (int) Math.max(newest, v.dateAdded);
        int seen = Store.getInt(this, "video_seen", -1);
        if (seen < 0 || !Store.getBool(this, "auto_audio", false)) { Store.putInt(this, "video_seen", newest); return; }
        for (Track v : MediaRepo.videos) if (v.dateAdded > seen) convertVideo(v);
        Store.putInt(this, "video_seen", Math.max(seen, newest));
    }

    private void convertVideo(Track v) {
        emit("progress", "{\"title\":" + JSONObject.quote("Conversion en audio…") + ",\"pct\":0}");
        MediaTools.extractAudio(this, v, new MediaTools.Callback() {
            @Override public void progress(int p) { emit("progress", "{\"title\":" + JSONObject.quote("Conversion en audio…") + ",\"pct\":" + p + "}"); }
            @Override public void done(Uri out, String err) {
                emit("progress", "{\"done\":true}");
                toast(err != null ? err : "✓ « " + v.title + " » ajouté à ta musique");
            }
        });
    }

    // =================================================================== Visualiseur (son -> C++ -> JS)
    private void startViz() {
        PlayerService s = PlayerService.get();
        if (viz != null || s == null) return;
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            emit("viz", "null");
            return;
        }
        try {
            viz = new Visualizer(s.audioSessionId());
            viz.setCaptureSize(Visualizer.getCaptureSizeRange()[1]);
            viz.setDataCaptureListener(new Visualizer.OnDataCaptureListener() {
                @Override public void onWaveFormDataCapture(Visualizer v, byte[] w, int r) {}
                @Override public void onFftDataCapture(Visualizer v, byte[] fft, int r) {
                    Native.fft(fft, vizLevels);
                    long now = System.currentTimeMillis();
                    if (now - lastVizPush < 30) return;
                    lastVizPush = now;
                    StringBuilder sb = new StringBuilder("[");
                    for (int i = 0; i < vizLevels.length; i++) {
                        if (i > 0) sb.append(',');
                        sb.append(Math.round(vizLevels[i] * 1000) / 1000f);
                    }
                    emit("viz", sb.append(']').toString());
                }
            }, Visualizer.getMaxCaptureRate(), false, true);
            viz.setEnabled(true);
        } catch (Throwable e) {
            viz = null;
            emit("viz", "null");
        }
    }

    private void stopViz() {
        if (viz == null) return;
        try { viz.setEnabled(false); viz.release(); } catch (Throwable ignored) {}
        viz = null;
    }

    // =================================================================== Résultats d'activités
    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_DELETE) {
            if (res == RESULT_OK && pendingDelete != null) {
                for (Track t : pendingDelete) Store.setInSet(this, "trash", t.id, false);
                toast("Supprimé définitivement");
            }
            pendingDelete = null;
            loadMedia();
            return;
        }
        if (res != RESULT_OK || data == null) return;
        Uri u = data.getData();
        switch (req) {
            case REQ_COVER: {
                if (u == null || coverTargetId < 0) return;
                Track t = find(coverTargetId);
                if (t == null) return;
                new Thread(() -> {
                    boolean ok = saveSquare(u, Store.coverFile(this, t.id));
                    if (ok) t.coverStamp = System.currentTimeMillis();
                    h.post(() -> {
                        if (!ok) { toast("Impossible de lire cette image"); return; }
                        emit("cover", trackJson(t).toString());
                        loadMedia();
                        PlayerService s = PlayerService.get();
                        if (s != null) s.notifyQueueChanged();
                    });
                }).start();
                break;
            }
            case REQ_IMPORT: {
                List<Uri> uris = new ArrayList<>();
                if (data.getClipData() != null) {
                    for (int k = 0; k < data.getClipData().getItemCount(); k++) uris.add(data.getClipData().getItemAt(k).getUri());
                } else if (u != null) uris.add(u);
                if (uris.isEmpty()) return;
                MediaTools.importFiles(this, uris, new MediaTools.Callback() {
                    @Override public void progress(int p) { emit("progress", "{\"title\":" + JSONObject.quote("Import…") + ",\"pct\":" + p + "}"); }
                    @Override public void done(Uri out, String err) {
                        emit("progress", "{\"done\":true}");
                        toast(err != null ? err : "✓ Ajouté à ta bibliothèque");
                    }
                });
                break;
            }
            case REQ_VIDEO:
                if (u != null) convertVideo(Track.fromUri(this, u, true));
                break;
            case REQ_SAVE:
                if (u == null) return;
                try (OutputStream os = getContentResolver().openOutputStream(u, "wt")) {
                    os.write(Store.exportAll(this).getBytes(StandardCharsets.UTF_8));
                    toast("✓ Sauvegarde créée");
                } catch (Exception e) { toast("Échec de la sauvegarde"); }
                break;
            case REQ_RESTORE: {
                if (u == null) return;
                StringBuilder sb = new StringBuilder();
                try (InputStream in = getContentResolver().openInputStream(u);
                     BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                } catch (Exception e) { toast("Fichier illisible"); return; }
                if (Store.importAll(this, sb.toString())) {
                    toast("✓ Restauré");
                    web.reload();
                } else toast("Ce n'est pas une sauvegarde NOX Player");
                break;
            }
        }
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

    // =================================================================== Interface exposée au JavaScript
    /** Tout ce que l'interface JS a le droit de faire. Rien d'autre n'est accessible. */
    private final class Bridge {
        private void ui(Runnable r) { h.post(r); }

        private PlayerService svc() { return PlayerService.get(); }

        @JavascriptInterface public String version() { return "1.4.1"; }

        @JavascriptInterface public void askPermission() { ui(MainActivity.this::askPerm); }

        @JavascriptInterface public void openAppSettings() {
            ui(() -> startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))));
        }

        @JavascriptInterface public void reload() { ui(MainActivity.this::loadMedia); }

        @JavascriptInterface public String state() { return stateJson().toString(); }

        @JavascriptInterface public void toast(String s) { MainActivity.this.toast(s); }

        // ----- Lecture
        @JavascriptInterface public void play(String idsJson, int index) {
            ui(() -> {
                try {
                    JSONArray a = new JSONArray(idsJson);
                    List<Track> q = new ArrayList<>();
                    for (int i = 0; i < a.length(); i++) { Track t = find(a.getLong(i)); if (t != null) q.add(t); }
                    PlayerService.playQueue(MainActivity.this, q, Math.max(0, Math.min(index, q.size() - 1)));
                } catch (Exception ignored) {}
            });
        }

        @JavascriptInterface public void cmd(String name, String arg) {
            ui(() -> {
                PlayerService s = svc();
                if (s == null) return;
                try {
                    switch (name) {
                        case "toggle": s.toggle(); break;
                        case "next": s.next(true); break;
                        case "prev": s.prev(); break;
                        case "seek": s.seekTo(Integer.parseInt(arg)); break;
                        case "mode": s.setMode(Integer.parseInt(arg)); break;
                        case "shuffle": s.setShuffle(!s.shuffle()); break;
                        case "repeat": s.cycleRepeat(); break;
                        case "jump": s.jumpTo(Integer.parseInt(arg)); break;
                        case "remove": s.removeFromQueue(Integer.parseInt(arg)); break;
                        case "clear": s.clearQueue(); break;
                        case "speed": s.setSpeed(Float.parseFloat(arg)); break;
                        case "sleep": s.setSleep(Integer.parseInt(arg)); break;
                        case "move": {
                            String[] p = arg.split(",");
                            s.moveInQueue(Integer.parseInt(p[0]), Integer.parseInt(p[1]));
                            break;
                        }
                    }
                } catch (Exception ignored) {}
            });
        }

        @JavascriptInterface public void enqueue(long id, boolean next) {
            ui(() -> {
                Track t = find(id);
                if (t == null) return;
                PlayerService s = svc();
                if (s == null || s.current() == null) PlayerService.playQueue(MainActivity.this, Collections.singletonList(t), 0);
                else if (next) s.playNext(t);
                else s.addToQueue(t);
            });
        }

        // ----- Favoris / playlists
        @JavascriptInterface public boolean fav(long id) {
            boolean r = Store.toggleFav(MainActivity.this, id);
            ui(MainActivity.this::onPlayerChanged);
            return r;
        }

        @JavascriptInterface public void playlist(String action, String name, String arg) {
            switch (action) {
                case "create": Store.createPlaylist(MainActivity.this, name); break;
                case "delete": Store.deletePlaylist(MainActivity.this, name); break;
                case "rename": Store.renamePlaylist(MainActivity.this, name, arg); break;
                case "add": Store.addToPlaylist(MainActivity.this, name, Long.parseLong(arg)); break;
                case "remove": Store.removeFromPlaylist(MainActivity.this, name, Long.parseLong(arg)); break;
            }
        }

        // ----- Modifier une chanson
        @JavascriptInterface public void edit(long id, String title, String artist, String album) {
            Track t = find(id);
            if (t == null) return;
            if (!title.trim().isEmpty()) t.title = title.trim();
            t.artist = artist.trim().isEmpty() ? "Artiste inconnu" : artist.trim();
            t.album = album.trim().isEmpty() ? "Album inconnu" : album.trim();
            Store.saveEdits(MainActivity.this, t);
            ui(() -> { loadMedia(); PlayerService s = svc(); if (s != null) s.notifyQueueChanged(); });
        }

        @JavascriptInterface public void pickCover(long id) {
            coverTargetId = id;
            ui(() -> {
                Intent i = new Intent(Intent.ACTION_GET_CONTENT).setType("image/*");
                startActivityForResult(Intent.createChooser(i, "Choisir une photo"), REQ_COVER);
            });
        }

        @JavascriptInterface public void resetCover(long id) {
            Track t = find(id);
            if (t == null) return;
            Store.coverFile(MainActivity.this, id).delete();
            t.coverStamp = System.currentTimeMillis();
            ui(() -> { emit("cover", trackJson(t).toString()); loadMedia(); });
        }

        // ----- Masquer / corbeille
        @JavascriptInterface public void setFlag(String set, long id, boolean on) {
            if (!"hidden".equals(set) && !"trash".equals(set)) return;
            Store.setInSet(MainActivity.this, set, id, on);
            ui(MainActivity.this::loadMedia);
        }

        @JavascriptInterface public void deleteForever(String idsJson) {
            ui(() -> {
                List<Track> l = new ArrayList<>();
                try {
                    JSONArray a = new JSONArray(idsJson);
                    for (int i = 0; i < a.length(); i++) { Track t = find(a.getLong(i)); if (t != null) l.add(t); }
                } catch (Exception ignored) {}
                if (l.isEmpty()) return;
                pendingDelete = l;
                if (Build.VERSION.SDK_INT >= 30) {
                    List<Uri> uris = new ArrayList<>();
                    for (Track t : l) uris.add(t.uri());
                    try {
                        PendingIntent pi = MediaStore.createDeleteRequest(getContentResolver(), uris);
                        startIntentSenderForResult(pi.getIntentSender(), REQ_DELETE, null, 0, 0, 0);
                    } catch (Exception e) { MainActivity.this.toast("Suppression impossible"); }
                } else {
                    for (Track t : l) {
                        try {
                            if (getContentResolver().delete(t.uri(), null, null) > 0) Store.setInSet(MainActivity.this, "trash", t.id, false);
                        } catch (Exception ignored) {}
                    }
                    pendingDelete = null;
                    loadMedia();
                }
            });
        }

        // ----- Ajouter de la musique
        @JavascriptInterface public void importFiles() {
            ui(() -> {
                Intent i = new Intent(Intent.ACTION_GET_CONTENT).setType("audio/*").putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                startActivityForResult(Intent.createChooser(i, "Choisir des musiques"), REQ_IMPORT);
            });
        }

        @JavascriptInterface public void convertVideo() {
            ui(() -> {
                Intent i = new Intent(Intent.ACTION_GET_CONTENT).setType("video/*");
                startActivityForResult(Intent.createChooser(i, "Choisir une vidéo"), REQ_VIDEO);
            });
        }

        @JavascriptInterface public String download(String url) {
            return MediaTools.downloadLink(MainActivity.this, url);
        }

        // ----- Réglages
        @JavascriptInterface public String pref(String key, String def) {
            return Store.getString(MainActivity.this, "js_" + key, def);
        }

        @JavascriptInterface public void setPref(String key, String val) {
            Store.putString(MainActivity.this, "js_" + key, val);
        }

        @JavascriptInterface public boolean flag(String key, boolean def) {
            return Store.getBool(MainActivity.this, key, def);
        }

        @JavascriptInterface public void setFlagPref(String key, boolean on) {
            if (!key.matches("pause_unplug|skip_short|auto_audio")) return;
            Store.putBool(MainActivity.this, key, on);
            if (key.equals("skip_short")) ui(MainActivity.this::loadMedia);
            if (key.equals("auto_audio")) {
                int newest = 0;
                for (Track v : MediaRepo.videos) newest = (int) Math.max(newest, v.dateAdded);
                Store.putInt(MainActivity.this, "video_seen", newest);
            }
        }

        @JavascriptInterface public long listenMs() { return Store.listenMs(MainActivity.this); }

        @JavascriptInterface public void backup(boolean save) {
            ui(() -> {
                Intent i;
                if (save) {
                    i = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                            .setType("application/json").putExtra(Intent.EXTRA_TITLE, "NOX-Player-sauvegarde.json");
                    startActivityForResult(i, REQ_SAVE);
                } else {
                    i = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
                    startActivityForResult(i, REQ_RESTORE);
                }
            });
        }

        @JavascriptInterface public void notificationSettings() {
            ui(() -> startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName())));
        }

        // ----- Égaliseur
        @JavascriptInterface public String eq() {
            JSONObject o = new JSONObject();
            PlayerService s = svc();
            try {
                if (s == null || s.eq() == null) return o.put("ok", false).toString();
                Equalizer e = s.eq();
                short[] r = e.getBandLevelRange();
                JSONArray bands = new JSONArray(), presets = new JSONArray();
                for (short b = 0; b < e.getNumberOfBands(); b++)
                    bands.put(new JSONObject().put("hz", e.getCenterFreq(b) / 1000).put("level", e.getBandLevel(b)));
                for (short p = 0; p < e.getNumberOfPresets(); p++) presets.put(e.getPresetName(p));
                BassBoost bb = s.bass();
                o.put("ok", true).put("on", e.getEnabled()).put("min", r[0]).put("max", r[1])
                        .put("bands", bands).put("presets", presets)
                        .put("bass", bb != null && bb.getStrengthSupported() ? bb.getRoundedStrength() : -1);
            } catch (Throwable t) {
                try { o.put("ok", false); } catch (Exception ignored) {}
            }
            return o.toString();
        }

        @JavascriptInterface public void eqSet(String what, int a, int b) {
            ui(() -> {
                PlayerService s = svc();
                if (s == null) return;
                switch (what) {
                    case "on": s.setFxEnabled(a != 0); break;
                    case "band": s.setBand((short) a, (short) b); break;
                    case "preset": s.usePreset((short) a); break;
                    case "bass": s.setBass((short) a); break;
                }
            });
        }

        // ----- Paroles (C++ pour le .lrc)
        @JavascriptInterface public String lyrics(long id) {
            Track t = find(id);
            if (t == null) return "[]";
            String saved = Store.getString(MainActivity.this, "lyrics_" + t.key(), null);
            if (saved == null && t.path != null && !t.path.isEmpty()) {
                int dot = t.path.lastIndexOf('.');
                String base = dot > 0 ? t.path.substring(0, dot) : t.path;
                for (String ext : new String[]{".lrc", ".txt"}) {
                    File f = new File(base + ext);
                    if (!f.canRead()) continue;
                    StringBuilder sb = new StringBuilder();
                    try (BufferedReader br = new BufferedReader(new FileReader(f))) {
                        String line;
                        while ((line = br.readLine()) != null) sb.append(line).append('\n');
                        saved = sb.toString();
                        break;
                    } catch (Exception ignored) {}
                }
            }
            if (saved == null || saved.trim().isEmpty()) return "[]";
            return Native.lrc(saved);
        }

        @JavascriptInterface public String lyricsRaw(long id) {
            Track t = find(id);
            return t == null ? "" : Store.getString(MainActivity.this, "lyrics_" + t.key(), "");
        }

        @JavascriptInterface public void saveLyrics(long id, String text) {
            Track t = find(id);
            if (t != null) Store.putString(MainActivity.this, "lyrics_" + t.key(), text.trim());
        }

        // ----- Couleurs (C++) et onde (C++)
        @JavascriptInterface public String palette(long id) {
            Track t = find(id);
            Bitmap b = t == null ? null : ArtLoader.decode(MainActivity.this, t, 64);
            if (b == null) return "null";
            Bitmap sm = Bitmap.createScaledBitmap(b, 24, 24, true);
            int[] px = new int[24 * 24];
            sm.getPixels(px, 0, 24, 0, 0, 24, 24);
            int[] c = Native.palette(px);
            return String.format("[\"#%06X\",\"#%06X\"]", c[0] & 0xFFFFFF, c[1] & 0xFFFFFF);
        }

        @JavascriptInterface public String wave(long seed) {
            float[] w = Native.wave(seed, 48);
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < w.length; i++) { if (i > 0) sb.append(','); sb.append(Math.round(w[i] * 1000) / 1000f); }
            return sb.append(']').toString();
        }

        // ----- Visualiseur
        @JavascriptInterface public void viz(boolean on) {
            ui(() -> {
                if (!on) { stopViz(); return; }
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
                } else startViz();
            });
        }

        // ----- Partager / sonnerie
        @JavascriptInterface public void share(long id) {
            Track t = find(id);
            if (t == null) return;
            ui(() -> {
                Intent i = new Intent(Intent.ACTION_SEND).setType("audio/*").putExtra(Intent.EXTRA_STREAM, t.uri())
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(i, "Partager « " + t.title + " »"));
            });
        }

        /** @return "ok", "perm" (autorisation à donner) ou "err" */
        @JavascriptInterface public String ringtone(long id) {
            Track t = find(id);
            if (t == null || t.id < 0) return "err";
            if (!Settings.System.canWrite(MainActivity.this)) {
                ui(() -> startActivity(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + getPackageName()))));
                return "perm";
            }
            try {
                try {
                    ContentValues v = new ContentValues();
                    v.put(MediaStore.Audio.Media.IS_RINGTONE, 1);
                    getContentResolver().update(t.uri(), v, null, null);
                } catch (Exception ignored) {}
                RingtoneManager.setActualDefaultRingtoneUri(MainActivity.this, RingtoneManager.TYPE_RINGTONE, t.uri());
                return "ok";
            } catch (Exception e) {
                return "err";
            }
        }
    }
}
