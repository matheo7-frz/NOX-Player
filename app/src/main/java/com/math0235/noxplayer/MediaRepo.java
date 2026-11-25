package com.math0235.noxplayer;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.provider.MediaStore;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Scanne la musique et les vidéos du téléphone via MediaStore. */
public final class MediaRepo {
    public static final int BY_ALBUM = 0, BY_ARTIST = 1, BY_FOLDER = 2;

    public static final List<Track> songs = new ArrayList<>();
    public static final List<Track> videos = new ArrayList<>();
    private static final Map<Long, Track> byId = new HashMap<>();
    /** Titres masqués ou dans la corbeille (pas dans la bibliothèque). */
    public static final List<Track> hidden = new ArrayList<>();
    public static final List<Track> trash = new ArrayList<>();

    public static final String[] SORT_NAMES = {"Le dernier", "Le plus joué", "De A à Z", "Artiste", "Durée la plus longue"};

    public static List<Track> sorted(Context c, List<Track> in, int mode, boolean reverse) {
        List<Track> l = new ArrayList<>(in);
        java.util.Comparator<Track> cmp;
        switch (mode) {
            case 0: cmp = (a, b) -> Long.compare(b.dateAdded, a.dateAdded); break;
            case 1: {
                Map<Long, Integer> pc = new HashMap<>();
                for (Track t : l) pc.put(t.id, Store.plays(c, t.id));
                cmp = (a, b) -> Integer.compare(pc.get(b.id), pc.get(a.id));
                break;
            }
            case 3: cmp = (a, b) -> {
                int r = a.artist.compareToIgnoreCase(b.artist);
                return r != 0 ? r : a.title.compareToIgnoreCase(b.title);
            }; break;
            case 4: cmp = (a, b) -> Long.compare(b.duration, a.duration); break;
            default: cmp = (a, b) -> a.title.compareToIgnoreCase(b.title);
        }
        Collections.sort(l, cmp);
        if (reverse) Collections.reverse(l);
        return l;
    }

    private MediaRepo() {}

    @SuppressWarnings("deprecation")
    public static void load(Context c) {
        List<Track> s = new ArrayList<>();
        List<Track> v = new ArrayList<>();
        ContentResolver cr = c.getContentResolver();

        String[] ap = {
                MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.ALBUM_ID, MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.DATA, MediaStore.Audio.Media.DATE_ADDED
        };
        try (Cursor cu = cr.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, ap,
                MediaStore.Audio.Media.IS_MUSIC + " != 0", null, null)) {
            if (cu != null) {
                while (cu.moveToNext()) {
                    Track t = new Track();
                    t.id = cu.getLong(0);
                    t.title = nz(cu.getString(1), "Sans titre");
                    t.artist = cleanArtist(cu.getString(2));
                    t.album = nz(cu.getString(3), "Album inconnu");
                    t.albumId = cu.getLong(4);
                    t.duration = cu.getLong(5);
                    t.path = nz(cu.getString(6), "");
                    t.dateAdded = cu.getLong(7);
                    t.folder = folderOf(t.path);
                    if (t.duration > 0 && t.duration < 3000) continue; // sons système trop courts
                    s.add(t);
                }
            }
        } catch (Exception ignored) {}

        String[] vp = {
                MediaStore.Video.Media._ID, MediaStore.Video.Media.TITLE, MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.DURATION, MediaStore.Video.Media.DATA, MediaStore.Video.Media.DATE_ADDED
        };
        try (Cursor cu = cr.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, vp, null, null,
                MediaStore.Video.Media.DATE_ADDED + " DESC")) {
            if (cu != null) {
                while (cu.moveToNext()) {
                    Track t = new Track();
                    t.video = true;
                    t.id = cu.getLong(0);
                    t.title = nz(cu.getString(1), nz(cu.getString(2), "Vidéo"));
                    t.duration = cu.getLong(3);
                    t.path = nz(cu.getString(4), "");
                    t.dateAdded = cu.getLong(5);
                    t.folder = folderOf(t.path);
                    t.artist = t.folder;
                    t.album = t.folder;
                    v.add(t);
                }
            }
        } catch (Exception ignored) {}

        for (Track t : s) Store.applyEdits(c, t);
        java.util.Set<Long> hid = Store.idSet(c, "hidden"), tr = Store.idSet(c, "trash");
        boolean skipShort = Store.getBool(c, "skip_short", false);
        List<Track> all = new ArrayList<>(s), hl = new ArrayList<>(), tl = new ArrayList<>();
        s.clear();
        for (Track t : all) {
            if (tr.contains(t.id)) tl.add(t);
            else if (hid.contains(t.id)) hl.add(t);
            else if (skipShort && t.duration > 0 && t.duration < 30000) hl.add(t);
            else s.add(t);
        }
        for (Track t : v) Store.applyEdits(c, t);
        Collections.sort(s, (a, b) -> a.title.compareToIgnoreCase(b.title));

        synchronized (MediaRepo.class) {
            songs.clear();
            songs.addAll(s);
            videos.clear();
            videos.addAll(v);
            byId.clear();
            for (Track t : s) byId.put(t.id, t);
            hidden.clear();
            hidden.addAll(hl);
            trash.clear();
            trash.addAll(tl);
        }
    }

    public static synchronized Track get(long id) {
        return byId.get(id);
    }

    public static synchronized List<Track> byIds(List<Long> ids) {
        List<Track> out = new ArrayList<>();
        for (Long id : ids) {
            Track t = byId.get(id);
            if (t != null) out.add(t);
        }
        return out;
    }

    public static TreeMap<String, List<Track>> group(List<Track> list, int mode) {
        TreeMap<String, List<Track>> m = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Track t : list) {
            String k = mode == BY_ALBUM ? t.album : mode == BY_ARTIST ? t.artist : t.folder;
            if (k == null || k.isEmpty()) k = "Inconnu";
            List<Track> l = m.get(k);
            if (l == null) {
                l = new ArrayList<>();
                m.put(k, l);
            }
            l.add(t);
        }
        return m;
    }

    private static String nz(String s, String def) {
        return s == null || s.trim().isEmpty() ? def : s;
    }

    private static String cleanArtist(String s) {
        if (s == null || s.trim().isEmpty() || s.equals("<unknown>")) return "Artiste inconnu";
        return s;
    }

    private static String folderOf(String path) {
        if (path == null || path.isEmpty()) return "Inconnu";
        File p = new File(path).getParentFile();
        return p == null ? "Inconnu" : p.getName();
    }
}
