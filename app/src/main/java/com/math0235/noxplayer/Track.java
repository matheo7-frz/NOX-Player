package com.math0235.noxplayer;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.provider.OpenableColumns;

/** Un morceau ou une vidéo. */
public class Track {
    public long id = -1;
    public String title = "Sans titre";
    public String artist = "Artiste inconnu";
    public String album = "Album inconnu";
    public String folder = "";
    public String path = "";
    public long albumId;
    public long duration;
    public long dateAdded;
    public boolean video;
    public Uri customUri;
    /** Change quand l'utilisateur met une nouvelle pochette (force le rechargement). */
    public long coverStamp;

    public Uri uri() {
        if (customUri != null) return customUri;
        return ContentUris.withAppendedId(video
                ? MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                : MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);
    }

    public String key() {
        return uri().toString();
    }

    public String artKey() {
        return key() + "#" + coverStamp;
    }

    /** Pochette perso choisie par l'utilisateur (null si aucune). */
    public java.io.File customCover(Context c) {
        if (id < 0 || video) return null;
        java.io.File f = Store.coverFile(c, id);
        return f.exists() ? f : null;
    }

    /** Fichier ouvert depuis une autre appli ("Ouvrir avec NOX Player"). */
    public static Track fromUri(Context c, Uri u, boolean video) {
        Track t = new Track();
        t.customUri = u;
        t.video = video;
        t.artist = "Fichier externe";
        t.album = "Fichier externe";
        try (Cursor cu = c.getContentResolver().query(u, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cu != null && cu.moveToFirst()) {
                String n = cu.getString(0);
                if (n != null) {
                    int dot = n.lastIndexOf('.');
                    t.title = dot > 0 ? n.substring(0, dot) : n;
                }
            }
        } catch (Exception ignored) {
            String last = u.getLastPathSegment();
            if (last != null) t.title = last;
        }
        return t;
    }
}
