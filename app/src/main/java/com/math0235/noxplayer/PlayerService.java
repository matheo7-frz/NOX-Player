package com.math0235.noxplayer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.drawable.Icon;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.MediaPlayer;
import android.media.audiofx.BassBoost;
import android.media.audiofx.Equalizer;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Lecture en arrière-plan : file d'attente, notification, écouteurs, égaliseur, minuterie. */
public class PlayerService extends Service implements MediaPlayer.OnPreparedListener,
        MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener, AudioManager.OnAudioFocusChangeListener {

    public static final String A_PLAY_QUEUE = "nox.PLAY_QUEUE", A_TOGGLE = "nox.TOGGLE",
            A_NEXT = "nox.NEXT", A_PREV = "nox.PREV", A_STOP = "nox.STOP";
    public static final int REPEAT_OFF = 0, REPEAT_ALL = 1, REPEAT_ONE = 2;
    private static final String CHANNEL = "nox_playback";
    private static final int NOTIF_ID = 7;

    public interface Listener { void onPlayerChanged(); }

    private static PlayerService inst;
    private static final List<Listener> listeners = new ArrayList<>();
    private static List<Track> pendingQueue;
    private static int pendingIndex;

    public static PlayerService get() { return inst; }
    public static void addListener(Listener l) { if (!listeners.contains(l)) listeners.add(l); }
    public static void removeListener(Listener l) { listeners.remove(l); }

    public static void playQueue(Context c, List<Track> q, int index) {
        if (q == null || q.isEmpty() || !Native.ok(c)) return;
        PlayerService s = inst;
        if (s != null) { s.setQueue(q, index); return; }
        pendingQueue = new ArrayList<>(q);
        pendingIndex = index;
        c.startForegroundService(new Intent(c, PlayerService.class).setAction(A_PLAY_QUEUE));
    }

    private MediaPlayer mp;
    private boolean prepared, playWhenReady, resumeOnFocus;
    private final List<Track> queue = new ArrayList<>();
    private final List<Integer> order = new ArrayList<>();
    private int pos = -1;
    private boolean shuffle;
    private int repeat;
    private float speed = 1f;
    private int errors;
    private AudioManager am;
    private AudioFocusRequest focusReq;
    private AudioAttributes attrs;
    private MediaSession session;
    private Equalizer eq;
    private BassBoost bass;
    private Bitmap art;
    private long sleepAt;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable sleepRunnable = () -> { sleepAt = 0; pause(); };
    private final Runnable listenTick = new Runnable() {
        @Override public void run() {
            if (isPlaying()) Store.addListen(PlayerService.this, 10_000);
            h.postDelayed(this, 10_000);
        }
    };

    private final BroadcastReceiver noisy = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(i.getAction()) && isPlaying()
                    && Store.getBool(c, "pause_unplug", true)) pause();
        }
    };

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        inst = this;
        am = (AudioManager) getSystemService(AUDIO_SERVICE);
        attrs = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build();
        NotificationChannel ch = new NotificationChannel(CHANNEL, "Lecture", NotificationManager.IMPORTANCE_LOW);
        ch.setShowBadge(false);
        getSystemService(NotificationManager.class).createNotificationChannel(ch);

        mp = new MediaPlayer();
        mp.setAudioAttributes(attrs);
        mp.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK);
        mp.setOnPreparedListener(this);
        mp.setOnCompletionListener(this);
        mp.setOnErrorListener(this);

        shuffle = Store.getBool(this, "shuffle", false);
        repeat = Store.getInt(this, "repeat", REPEAT_ALL);
        speed = Store.getFloat(this, "speed", 1f);

        session = new MediaSession(this, "NoxPlayer");
        session.setCallback(new MediaSession.Callback() {
            @Override public void onPlay() { play(); }
            @Override public void onPause() { pause(); }
            @Override public void onSkipToNext() { next(true); }
            @Override public void onSkipToPrevious() { prev(); }
            @Override public void onSeekTo(long p) { seekTo((int) p); }
            @Override public void onStop() { stopAll(); }
        });
        session.setActive(true);
        setupFx();
        h.postDelayed(listenTick, 10_000);

        IntentFilter f = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(noisy, f, Context.RECEIVER_EXPORTED);
        else registerReceiver(noisy, f);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        goForeground();
        String a = intent != null ? intent.getAction() : null;
        if (A_PLAY_QUEUE.equals(a)) {
            if (pendingQueue != null) {
                setQueue(pendingQueue, pendingIndex);
                pendingQueue = null;
            }
        } else if (A_TOGGLE.equals(a)) toggle();
        else if (A_NEXT.equals(a)) next(true);
        else if (A_PREV.equals(a)) prev();
        else if (A_STOP.equals(a)) stopAll();
        if (queue.isEmpty() && !A_PLAY_QUEUE.equals(a)) stopAll();
        return START_NOT_STICKY;
    }

    // ------------------------------------------------------------ File d'attente
    public void setQueue(List<Track> q, int index) {
        queue.clear();
        queue.addAll(q);
        buildOrder(Math.max(0, Math.min(index, q.size() - 1)));
        errors = 0;
        playCurrent();
    }

    private void buildOrder(int startIdx) {
        order.clear();
        for (int i = 0; i < queue.size(); i++) order.add(i);
        if (shuffle && !order.isEmpty()) {
            order.remove(Integer.valueOf(startIdx));
            Collections.shuffle(order);
            order.add(0, startIdx);
            pos = 0;
        } else {
            pos = startIdx;
        }
    }

    public Track current() {
        if (pos < 0 || pos >= order.size()) return null;
        int i = order.get(pos);
        return i >= 0 && i < queue.size() ? queue.get(i) : null;
    }

    public List<Track> orderedQueue() {
        List<Track> l = new ArrayList<>();
        for (int i : order) l.add(queue.get(i));
        return l;
    }

    public int queuePosition() { return pos; }

    public void jumpTo(int orderPos) {
        if (orderPos < 0 || orderPos >= order.size()) return;
        pos = orderPos;
        playCurrent();
    }

    /** Déplace un titre dans la file (positions dans l'ordre de lecture). */
    public void moveInQueue(int from, int to) {
        if (from < 0 || to < 0 || from >= order.size() || to >= order.size() || from == to) return;
        int v = order.remove(from);
        order.add(to, v);
        if (pos == from) pos = to;
        else if (from < pos && to >= pos) pos--;
        else if (from > pos && to <= pos) pos++;
        updateAll();
    }

    /** Pendant qu'on glisse : on déplace sans rafraîchir l'écran. */
    public void moveInQueueSilently(int from, int to) {
        if (from < 0 || to < 0 || from >= order.size() || to >= order.size() || from == to) return;
        int v = order.remove(from);
        order.add(to, v);
        if (pos == from) pos = to;
        else if (from < pos && to >= pos) pos--;
        else if (from > pos && to <= pos) pos++;
    }

    public void notifyQueueChanged() { updateAll(); }

    public void removeFromQueue(int orderPos) {
        if (orderPos < 0 || orderPos >= order.size() || orderPos == pos) return;
        order.remove(orderPos);
        if (orderPos < pos) pos--;
        updateAll();
    }

    /** Vide la file sauf le titre en cours. */
    public void clearQueue() {
        if (current() == null) return;
        int cur = order.get(pos);
        order.clear();
        order.add(cur);
        pos = 0;
        updateAll();
    }

    /** Modes façon Lark : 0 = répéter un titre, 1 = répéter la liste, 2 = aléatoire. */
    public int mode() {
        if (shuffle) return 2;
        return repeat == REPEAT_ONE ? 0 : 1;
    }

    public void setMode(int m) {
        if (m == 2) {
            repeat = REPEAT_ALL;
            Store.putInt(this, "repeat", repeat);
            if (!shuffle) setShuffle(true); else updateAll();
        } else {
            repeat = m == 0 ? REPEAT_ONE : REPEAT_ALL;
            Store.putInt(this, "repeat", repeat);
            if (shuffle) setShuffle(false); else updateAll();
        }
    }

    public void playNext(Track t) {
        if (current() == null) { setQueue(Collections.singletonList(t), 0); return; }
        queue.add(t);
        order.add(pos + 1, queue.size() - 1);
        updateAll();
    }

    public void addToQueue(Track t) {
        if (current() == null) { setQueue(Collections.singletonList(t), 0); return; }
        queue.add(t);
        order.add(queue.size() - 1);
        updateAll();
    }

    private void playCurrent() {
        Track t = current();
        if (t == null) return;
        prepared = false;
        playWhenReady = true;
        art = null;
        try {
            mp.reset();
            mp.setAudioAttributes(attrs);
            mp.setDataSource(this, t.uri());
            mp.prepareAsync();
        } catch (Exception e) {
            onError(mp, 0, 0);
            return;
        }
        Store.addRecent(this, t.id);
        Store.incPlay(this, t.id);
        ArtLoader.load(this, t, 512, b -> {
            if (t == current()) { art = b; updateAll(); }
        });
        updateAll();
    }

    // ------------------------------------------------------------ Contrôles
    public boolean isPlaying() {
        try { return prepared && mp.isPlaying(); } catch (Exception e) { return false; }
    }

    public void play() {
        if (current() == null) return;
        if (!prepared) { playWhenReady = true; return; }
        if (!requestFocus()) return;
        mp.start();
        applySpeed();
        updateAll();
    }

    public void pause() {
        playWhenReady = false;
        try { if (prepared && mp.isPlaying()) mp.pause(); } catch (Exception ignored) {}
        updateAll();
    }

    public void toggle() { if (isPlaying()) pause(); else play(); }

    public void next(boolean user) {
        if (order.isEmpty()) return;
        if (repeat == REPEAT_ONE && !user) { seekTo(0); play(); return; }
        if (pos < order.size() - 1) pos++;
        else if (repeat == REPEAT_ALL || user) pos = 0;
        else { pause(); seekTo(0); return; }
        playCurrent();
    }

    public void prev() {
        if (order.isEmpty()) return;
        if (position() > 3000) { seekTo(0); return; }
        pos = pos > 0 ? pos - 1 : order.size() - 1;
        playCurrent();
    }

    public int position() {
        try { return prepared ? mp.getCurrentPosition() : 0; } catch (Exception e) { return 0; }
    }

    public int duration() {
        try { if (prepared) return mp.getDuration(); } catch (Exception ignored) {}
        Track t = current();
        return t == null ? 0 : (int) t.duration;
    }

    public void seekTo(int ms) {
        try { if (prepared) mp.seekTo(ms); } catch (Exception ignored) {}
        updateSession();
    }

    public boolean shuffle() { return shuffle; }

    public void setShuffle(boolean s) {
        shuffle = s;
        Store.putBool(this, "shuffle", s);
        if (!order.isEmpty()) {
            int cur = order.get(Math.max(0, pos));
            buildOrder(cur);
        }
        updateAll();
    }

    public int repeat() { return repeat; }

    public void cycleRepeat() {
        repeat = (repeat + 1) % 3;
        Store.putInt(this, "repeat", repeat);
        updateAll();
    }

    public float speed() { return speed; }

    public void setSpeed(float s) {
        speed = s;
        Store.putFloat(this, "speed", s);
        if (isPlaying()) applySpeed();
        updateAll();
    }

    private void applySpeed() {
        try { mp.setPlaybackParams(mp.getPlaybackParams().setSpeed(speed)); } catch (Exception ignored) {}
    }

    // ------------------------------------------------------------ Minuterie
    public void setSleep(int minutes) {
        h.removeCallbacks(sleepRunnable);
        if (minutes <= 0) { sleepAt = 0; }
        else {
            sleepAt = System.currentTimeMillis() + minutes * 60_000L;
            h.postDelayed(sleepRunnable, minutes * 60_000L);
        }
        updateAll();
    }

    public long sleepRemaining() {
        return sleepAt == 0 ? 0 : Math.max(0, sleepAt - System.currentTimeMillis());
    }

    // ------------------------------------------------------------ Égaliseur
    private void setupFx() {
        boolean on = Store.getBool(this, "eq_on", false);
        try {
            eq = new Equalizer(0, mp.getAudioSessionId());
            for (short b = 0; b < eq.getNumberOfBands(); b++) {
                if (Store.has(this, "eq_b" + b)) eq.setBandLevel(b, (short) Store.getInt(this, "eq_b" + b, 0));
            }
            eq.setEnabled(on);
        } catch (Throwable t) { eq = null; }
        try {
            bass = new BassBoost(0, mp.getAudioSessionId());
            if (bass.getStrengthSupported()) bass.setStrength((short) Store.getInt(this, "bass", 0));
            bass.setEnabled(on);
        } catch (Throwable t) { bass = null; }
    }

    public int audioSessionId() { return mp.getAudioSessionId(); }

    public Equalizer eq() { return eq; }
    public BassBoost bass() { return bass; }

    public void setFxEnabled(boolean on) {
        Store.putBool(this, "eq_on", on);
        try { if (eq != null) eq.setEnabled(on); } catch (Throwable ignored) {}
        try { if (bass != null) bass.setEnabled(on); } catch (Throwable ignored) {}
    }

    public void setBand(short band, short level) {
        try { eq.setBandLevel(band, level); Store.putInt(this, "eq_b" + band, level); } catch (Throwable ignored) {}
    }

    public void usePreset(short p) {
        try {
            eq.usePreset(p);
            for (short b = 0; b < eq.getNumberOfBands(); b++) Store.putInt(this, "eq_b" + b, eq.getBandLevel(b));
        } catch (Throwable ignored) {}
    }

    public void setBass(short s) {
        try { bass.setStrength(s); Store.putInt(this, "bass", s); } catch (Throwable ignored) {}
    }

    // ------------------------------------------------------------ Callbacks MediaPlayer
    @Override
    public void onPrepared(MediaPlayer m) {
        prepared = true;
        errors = 0;
        if (playWhenReady) play(); else updateAll();
    }

    @Override
    public void onCompletion(MediaPlayer m) {
        if (prepared) next(false);
    }

    @Override
    public boolean onError(MediaPlayer m, int what, int extra) {
        prepared = false;
        errors++;
        if (errors < Math.max(1, queue.size())) {
            h.postDelayed(() -> next(true), 400);
        } else {
            playWhenReady = false;
            updateAll();
        }
        return true;
    }

    private boolean requestFocus() {
        if (focusReq == null) {
            focusReq = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(attrs).setOnAudioFocusChangeListener(this).build();
        }
        return am.requestAudioFocus(focusReq) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    }

    @Override
    public void onAudioFocusChange(int change) {
        switch (change) {
            case AudioManager.AUDIOFOCUS_LOSS:
                resumeOnFocus = false;
                pause();
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                if (isPlaying()) { resumeOnFocus = true; pause(); }
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                try { mp.setVolume(0.3f, 0.3f); } catch (Exception ignored) {}
                break;
            case AudioManager.AUDIOFOCUS_GAIN:
                try { mp.setVolume(1f, 1f); } catch (Exception ignored) {}
                if (resumeOnFocus) { resumeOnFocus = false; play(); }
                break;
        }
    }

    // ------------------------------------------------------------ Notification + session
    private void updateAll() {
        updateSession();
        if (inst != null) {
            try { getSystemService(NotificationManager.class).notify(NOTIF_ID, buildNotification()); } catch (Exception ignored) {}
        }
        for (Listener l : new ArrayList<>(listeners)) l.onPlayerChanged();
    }

    private void updateSession() {
        Track t = current();
        if (session == null) return;
        if (t != null) {
            MediaMetadata.Builder md = new MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, t.title)
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, t.artist)
                    .putString(MediaMetadata.METADATA_KEY_ALBUM, t.album)
                    .putLong(MediaMetadata.METADATA_KEY_DURATION, duration());
            if (art != null) md.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, art);
            session.setMetadata(md.build());
        }
        boolean p = isPlaying();
        session.setPlaybackState(new PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_PLAY_PAUSE
                        | PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS
                        | PlaybackState.ACTION_SEEK_TO | PlaybackState.ACTION_STOP)
                .setState(p ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED, position(), p ? speed : 0f)
                .build());
    }

    private PendingIntent servicePi(String action, int code) {
        Intent i = new Intent(this, PlayerService.class).setAction(action);
        return PendingIntent.getService(this, code, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private Notification.Action action(int icon, String title, String act, int code) {
        return new Notification.Action.Builder(Icon.createWithResource(this, icon), title, servicePi(act, code)).build();
    }

    private Notification buildNotification() {
        Track t = current();
        boolean p = isPlaying();
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class).putExtra("openPlayer", true).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder b = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(t != null ? t.title : "NOX Player")
                .setContentText(t != null ? t.artist : "")
                .setContentIntent(open)
                .setDeleteIntent(servicePi(A_STOP, 4))
                .setOngoing(p)
                .setShowWhen(false)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .addAction(action(android.R.drawable.ic_media_previous, "Précédent", A_PREV, 1))
                .addAction(action(p ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                        p ? "Pause" : "Lecture", A_TOGGLE, 2))
                .addAction(action(android.R.drawable.ic_media_next, "Suivant", A_NEXT, 3))
                .setStyle(new Notification.MediaStyle().setMediaSession(session.getSessionToken())
                        .setShowActionsInCompactView(0, 1, 2));
        if (art != null) b.setLargeIcon(art);
        return b.build();
    }

    private void goForeground() {
        Notification n = buildNotification();
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        else startForeground(NOTIF_ID, n);
    }

    private void stopAll() {
        pause();
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        if (!isPlaying()) stopAll();
    }

    @Override
    public void onDestroy() {
        inst = null;
        h.removeCallbacksAndMessages(null);
        try { unregisterReceiver(noisy); } catch (Exception ignored) {}
        if (focusReq != null) am.abandonAudioFocusRequest(focusReq);
        try { if (eq != null) eq.release(); } catch (Throwable ignored) {}
        try { if (bass != null) bass.release(); } catch (Throwable ignored) {}
        try { mp.release(); } catch (Exception ignored) {}
        session.release();
        for (Listener l : new ArrayList<>(listeners)) l.onPlayerChanged();
        super.onDestroy();
    }
}
