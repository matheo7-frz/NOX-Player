package com.math0235.noxplayer;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

/** Sauvegarde locale : favoris, playlists, récents, réglages. */
public final class Store {
    private Store() {}

    private static SharedPreferences p(Context c) {
        return c.getApplicationContext().getSharedPreferences("nox_player", Context.MODE_PRIVATE);
    }

    // ---------- Réglages simples ----------
    public static int getInt(Context c, String k, int d) { return p(c).getInt(k, d); }
    public static void putInt(Context c, String k, int v) { p(c).edit().putInt(k, v).apply(); }
    public static boolean getBool(Context c, String k, boolean d) { return p(c).getBoolean(k, d); }
    public static void putBool(Context c, String k, boolean v) { p(c).edit().putBoolean(k, v).apply(); }
    public static float getFloat(Context c, String k, float d) { return p(c).getFloat(k, d); }
    public static void putFloat(Context c, String k, float v) { p(c).edit().putFloat(k, v).apply(); }
    public static boolean has(Context c, String k) { return p(c).contains(k); }
    public static String getString(Context c, String k, String d) { return p(c).getString(k, d); }
    public static void putString(Context c, String k, String v) {
        if (v == null || v.isEmpty()) p(c).edit().remove(k).apply(); else p(c).edit().putString(k, v).apply();
    }

    // ---------- Modifs perso (titre, artiste, pochette) ----------
    public static java.io.File coverFile(Context c, long id) {
        java.io.File d = new java.io.File(c.getFilesDir(), "covers");
        if (!d.exists()) d.mkdirs();
        return new java.io.File(d, id + ".jpg");
    }

    /** Applique le titre / artiste modifiés par l'utilisateur. */
    public static void applyEdits(Context c, Track t) {
        if (t.id < 0) return;
        String pre = (t.video ? "v" : "a") + t.id;
        String ti = p(c).getString("title_" + pre, null);
        String ar = p(c).getString("artist_" + pre, null);
        String al = p(c).getString("album_" + pre, null);
        if (ti != null) t.title = ti;
        if (ar != null) t.artist = ar;
        if (al != null) t.album = al;
        java.io.File f = t.customCover(c);
        t.coverStamp = f != null ? f.lastModified() : 0;
    }

    public static void saveEdits(Context c, Track t) {
        String pre = (t.video ? "v" : "a") + t.id;
        p(c).edit().putString("title_" + pre, t.title).putString("artist_" + pre, t.artist)
                .putString("album_" + pre, t.album).apply();
    }

    // ---------- Favoris ----------
    public static Set<Long> favs(Context c) {
        Set<Long> out = new HashSet<>();
        for (String s : p(c).getStringSet("favs", new HashSet<>())) {
            try { out.add(Long.parseLong(s)); } catch (NumberFormatException ignored) {}
        }
        return out;
    }

    public static boolean isFav(Context c, long id) {
        return p(c).getStringSet("favs", new HashSet<>()).contains(String.valueOf(id));
    }

    /** @return true si le titre est maintenant en favori */
    public static boolean toggleFav(Context c, long id) {
        if (id < 0) return false;
        Set<String> s = new HashSet<>(p(c).getStringSet("favs", new HashSet<>()));
        String k = String.valueOf(id);
        boolean now;
        if (s.contains(k)) { s.remove(k); now = false; } else { s.add(k); now = true; }
        p(c).edit().putStringSet("favs", s).apply();
        return now;
    }

    // ---------- Statistiques ----------
    public static void incPlay(Context c, long id) {
        if (id < 0) return;
        p(c).edit().putInt("plays_" + id, p(c).getInt("plays_" + id, 0) + 1).apply();
    }

    public static int plays(Context c, long id) { return p(c).getInt("plays_" + id, 0); }

    public static void addListen(Context c, long ms) {
        p(c).edit().putLong("listen_ms", p(c).getLong("listen_ms", 0) + ms).apply();
    }

    public static long listenMs(Context c) { return p(c).getLong("listen_ms", 0); }

    // ---------- Masqués / corbeille ----------
    public static Set<Long> idSet(Context c, String key) {
        Set<Long> out = new HashSet<>();
        for (String s : p(c).getStringSet(key, new HashSet<>())) {
            try { out.add(Long.parseLong(s)); } catch (NumberFormatException ignored) {}
        }
        return out;
    }

    public static void setInSet(Context c, String key, long id, boolean in) {
        Set<String> s = new HashSet<>(p(c).getStringSet(key, new HashSet<>()));
        if (in) s.add(String.valueOf(id)); else s.remove(String.valueOf(id));
        p(c).edit().putStringSet(key, s).apply();
    }

    // ---------- Sauvegarde ----------
    /** Tout (favoris, playlists, modifs, paroles, réglages) en JSON. */
    public static String exportAll(Context c) {
        JSONObject o = new JSONObject();
        try {
            for (java.util.Map.Entry<String, ?> e : p(c).getAll().entrySet()) {
                Object v = e.getValue();
                JSONObject item = new JSONObject();
                if (v instanceof Set) {
                    JSONArray a = new JSONArray();
                    for (Object x : (Set<?>) v) a.put(String.valueOf(x));
                    item.put("t", "set").put("v", a);
                } else if (v instanceof Integer) item.put("t", "i").put("v", v);
                else if (v instanceof Long) item.put("t", "l").put("v", v);
                else if (v instanceof Float) item.put("t", "f").put("v", ((Float) v).doubleValue());
                else if (v instanceof Boolean) item.put("t", "b").put("v", v);
                else item.put("t", "s").put("v", String.valueOf(v));
                o.put(e.getKey(), item);
            }
            o.put("_app", "NOX Player");
        } catch (Exception ignored) {}
        return o.toString();
    }

    public static boolean importAll(Context c, String json) {
        try {
            JSONObject o = new JSONObject(json);
            if (!"NOX Player".equals(o.optString("_app"))) return false;
            SharedPreferences.Editor ed = p(c).edit();
            Iterator<String> it = o.keys();
            while (it.hasNext()) {
                String k = it.next();
                if (k.equals("_app")) continue;
                JSONObject item = o.getJSONObject(k);
                String t = item.getString("t");
                switch (t) {
                    case "set": {
                        Set<String> set = new HashSet<>();
                        JSONArray a = item.getJSONArray("v");
                        for (int i = 0; i < a.length(); i++) set.add(a.getString(i));
                        ed.putStringSet(k, set);
                        break;
                    }
                    case "i": ed.putInt(k, item.getInt("v")); break;
                    case "l": ed.putLong(k, item.getLong("v")); break;
                    case "f": ed.putFloat(k, (float) item.getDouble("v")); break;
                    case "b": ed.putBoolean(k, item.getBoolean("v")); break;
                    default: ed.putString(k, item.getString("v"));
                }
            }
            ed.apply();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ---------- Récents ----------
    public static List<Long> recent(Context c) {
        return readIds(p(c).getString("recent", "[]"));
    }

    public static void addRecent(Context c, long id) {
        if (id < 0) return;
        List<Long> l = recent(c);
        l.remove(Long.valueOf(id));
        l.add(0, id);
        while (l.size() > 60) l.remove(l.size() - 1);
        p(c).edit().putString("recent", writeIds(l).toString()).apply();
    }

    // ---------- Playlists ----------
    public static LinkedHashMap<String, List<Long>> playlists(Context c) {
        LinkedHashMap<String, List<Long>> m = new LinkedHashMap<>();
        try {
            JSONObject o = new JSONObject(p(c).getString("playlists", "{}"));
            Iterator<String> it = o.keys();
            while (it.hasNext()) {
                String k = it.next();
                m.put(k, readIds(o.getJSONArray(k).toString()));
            }
        } catch (Exception ignored) {}
        return m;
    }

    private static void savePlaylists(Context c, LinkedHashMap<String, List<Long>> m) {
        JSONObject o = new JSONObject();
        try {
            for (String k : m.keySet()) o.put(k, writeIds(m.get(k)));
        } catch (Exception ignored) {}
        p(c).edit().putString("playlists", o.toString()).apply();
    }

    public static boolean createPlaylist(Context c, String name) {
        LinkedHashMap<String, List<Long>> m = playlists(c);
        if (m.containsKey(name)) return false;
        m.put(name, new ArrayList<>());
        savePlaylists(c, m);
        return true;
    }

    public static void deletePlaylist(Context c, String name) {
        LinkedHashMap<String, List<Long>> m = playlists(c);
        m.remove(name);
        savePlaylists(c, m);
    }

    public static void renamePlaylist(Context c, String oldName, String newName) {
        LinkedHashMap<String, List<Long>> m = playlists(c);
        if (!m.containsKey(oldName) || m.containsKey(newName)) return;
        LinkedHashMap<String, List<Long>> n = new LinkedHashMap<>();
        for (String k : m.keySet()) n.put(k.equals(oldName) ? newName : k, m.get(k));
        savePlaylists(c, n);
    }

    public static boolean addToPlaylist(Context c, String name, long id) {
        if (id < 0) return false;
        LinkedHashMap<String, List<Long>> m = playlists(c);
        List<Long> l = m.get(name);
        if (l == null) { l = new ArrayList<>(); m.put(name, l); }
        if (l.contains(id)) return false;
        l.add(id);
        savePlaylists(c, m);
        return true;
    }

    public static void removeFromPlaylist(Context c, String name, long id) {
        LinkedHashMap<String, List<Long>> m = playlists(c);
        List<Long> l = m.get(name);
        if (l == null) return;
        l.remove(Long.valueOf(id));
        savePlaylists(c, m);
    }

    private static List<Long> readIds(String json) {
        List<Long> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(json);
            for (int i = 0; i < a.length(); i++) out.add(a.getLong(i));
        } catch (Exception ignored) {}
        return out;
    }

    private static JSONArray writeIds(List<Long> l) {
        JSONArray a = new JSONArray();
        if (l != null) for (Long id : l) a.put(id.longValue());
        return a;
    }
}
