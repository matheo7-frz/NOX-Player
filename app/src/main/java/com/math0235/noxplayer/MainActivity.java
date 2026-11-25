package com.math0235.noxplayer;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity implements PlayerService.Listener {
    private static final String[] TABS = {"Chansons", "Playlists", "Albums", "Artistes", "Dossiers", "Favoris", "Récents"};
    private static final int T_SONGS = 0, T_PLAYLISTS = 1, T_ALBUMS = 2, T_ARTISTS = 3, T_FOLDERS = 4, T_FAVS = 5, T_RECENT = 6;
    private static final int REQ_PERM = 1, REQ_COVER = 2, REQ_IMPORT = 3, REQ_VIDEO = 4;

    private static final int R_TRACK = 0, R_GROUP = 1, R_PLAYLIST = 2, R_NEWPL = 3, R_ADD = 4;

    static final class Row {
        final int type;
        Track track;
        String name, sub;
        List<Track> tracks;
        Row(int t) { type = t; }
    }

    /** Mis à true par les paramètres quand la bibliothèque doit être rescannée. */
    public static boolean needsReload;
    private int themeSeen;

    private int tab = T_SONGS;
    private String query = "";
    private String detailName;
    private List<Track> detailTracks;
    private boolean detailPlaylist;
    private boolean loaded, loading;
    private Track coverTarget;

    private final List<TextView> tabViews = new ArrayList<>();
    private TextView backBar, shuffleBtn, emptyView;
    private EditText search;
    private ListView list;
    private final RowAdapter adapter = new RowAdapter();
    private final List<Track> shownTracks = new ArrayList<>();

    private LinearLayout mini;
    private CoverView miniCover;
    private TextView miniTitle, miniSub;
    private IconView miniPlay;
    private ProgressBar miniProg;

    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override public void run() { updateMiniProgress(); h.postDelayed(this, 1000); }
    };
    private final Runnable reload = this::loadMedia;
    private ContentObserver observer;

    // =================================================================== Cycle de vie
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (!Guard.isOfficial(this)) { showTampered(); return; }
        Ui.loadTheme(this);
        themeSeen = Ui.themeVersion;
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.BG);
        setContentView(buildUi());
        observer = new ContentObserver(h) {
            @Override public void onChange(boolean selfChange) {
                // Nouveau fichier sur le téléphone -> on rescanne (avec un petit délai pour regrouper)
                h.removeCallbacks(reload);
                h.postDelayed(reload, 1500);
            }
        };
        getContentResolver().registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, observer);
        getContentResolver().registerContentObserver(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, observer);
        if (hasPerm()) loadMedia(); else askPerm();
        handleIntent(getIntent());
    }

    /** Écran bloquant si l'APK a été modifié / re-signé par quelqu'un d'autre. */
    private void showTampered() {
        android.widget.TextView t = Ui.text(this, "⚠\n\nCette copie de NOX Player a été modifiée.\n\n"
                + "Elle n'est pas signée par math_0235 et peut contenir un virus.\n"
                + "Désinstalle-la et utilise uniquement la version officielle.", 17, 0xFFFFFFFF, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(Ui.dp(this, 32), 0, Ui.dp(this, 32), 0);
        t.setBackgroundColor(0xFF8B0000);
        setContentView(t);
        observer = null;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (!Guard.isOfficial(this)) return;
        setIntent(intent);
        handleIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!Guard.isOfficial(this)) return;
        if (themeSeen != Ui.themeVersion) { recreate(); return; }
        if (needsReload) { needsReload = false; loadMedia(); }
        PlayerService.addListener(this);
        onPlayerChanged();
        h.post(tick);
        if (loaded) refresh();
    }

    @Override
    protected void onPause() {
        super.onPause();
        PlayerService.removeListener(this);
        h.removeCallbacks(tick);
    }

    @Override
    protected void onDestroy() {
        if (observer != null) getContentResolver().unregisterContentObserver(observer);
        h.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (detailTracks != null) { closeDetail(); return; }
        if (!query.isEmpty()) { search.setText(""); return; }
        if (search.getVisibility() == View.VISIBLE) { search.setVisibility(View.GONE); return; }
        super.onBackPressed();
    }

    /** "Ouvrir avec NOX Player" depuis une autre appli. */
    private void handleIntent(Intent i) {
        if (i == null || !Intent.ACTION_VIEW.equals(i.getAction()) || i.getData() == null) return;
        Uri u = i.getData();
        String type = i.getType();
        if (type == null) type = getContentResolver().getType(u);
        if (type != null && type.startsWith("video")) {
            ToolsUi.convertToAudio(this, Track.fromUri(this, u, true));
        } else {
            PlayerService.playQueue(this, Collections.singletonList(Track.fromUri(this, u, false)), 0);
            startActivity(new Intent(this, PlayerActivity.class));
        }
        i.setAction(null);
    }

    // =================================================================== Permissions
    private boolean hasPerm() {
        String p = Build.VERSION.SDK_INT >= 33 ? Manifest.permission.READ_MEDIA_AUDIO : Manifest.permission.READ_EXTERNAL_STORAGE;
        return checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED;
    }

    private void askPerm() {
        String[] perms;
        if (Build.VERSION.SDK_INT >= 33) {
            perms = new String[]{Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.POST_NOTIFICATIONS};
        } else if (Build.VERSION.SDK_INT >= 29) {
            perms = new String[]{Manifest.permission.READ_EXTERNAL_STORAGE};
        } else {
            perms = new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE};
        }
        requestPermissions(perms, REQ_PERM);
        refresh();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] p, int[] r) {
        if (hasPerm()) loadMedia(); else refresh();
    }

    private void loadMedia() {
        if (!hasPerm() || loading) return;
        loading = true;
        if (!loaded) refresh();
        new Thread(() -> {
            MediaRepo.load(this);
            runOnUiThread(() -> {
                loading = false;
                loaded = true;
                refresh();
                checkAutoConvert();
            });
        }).start();
    }

    /** Option : les nouvelles vidéos arrivées sur le téléphone sont converties en audio automatiquement. */
    private void checkAutoConvert() {
        int newest = 0;
        for (Track v : MediaRepo.videos) newest = (int) Math.max(newest, v.dateAdded);
        int seen = Store.getInt(this, "video_seen", -1);
        if (seen < 0 || !Store.getBool(this, "auto_audio", false)) {
            Store.putInt(this, "video_seen", newest);
            return;
        }
        for (Track v : MediaRepo.videos) if (v.dateAdded > seen) ToolsUi.autoConvert(this, v);
        Store.putInt(this, "video_seen", Math.max(seen, newest));
    }

    // =================================================================== Interface
    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);
        int d16 = Ui.dp(this, 16);

        // --- En-tête
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(Ui.dp(this, 20), Ui.dp(this, 18), Ui.dp(this, 8), Ui.dp(this, 8));
        int logoId = getResources().getIdentifier("nox_logo", "drawable", getPackageName());
        if (logoId != 0) {
            android.widget.ImageView logo = new android.widget.ImageView(this);
            logo.setImageResource(logoId);
            logo.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
            logo.setBackground(Ui.round(0xFF1B1B1C, Ui.dp(this, 12)));
            logo.setClipToOutline(true);
            int ls = Ui.dp(this, 42);
            LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(ls, ls);
            llp.rightMargin = Ui.dp(this, 12);
            header.addView(logo, llp);
        }
        LinearLayout tcol = new LinearLayout(this);
        tcol.setOrientation(LinearLayout.VERTICAL);
        TextView title = Ui.text(this, "NOX Player", 25, Ui.TEXT, true);
        float w = title.getPaint().measureText("NOX Player");
        title.getPaint().setShader(new LinearGradient(0, 0, w, 0, Ui.ACCENT, Ui.ACCENT2, Shader.TileMode.CLAMP));
        tcol.addView(title);
        tcol.addView(Ui.text(this, "fait par math_0235", 11, Ui.SUB, false));
        header.addView(tcol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        int ib = Ui.dp(this, 44);
        header.addView(Ui.button(this, "🔍", 19, v -> {
            if (search.getVisibility() == View.VISIBLE && query.isEmpty()) { search.setVisibility(View.GONE); hideKeyboard(); }
            else {
                search.setVisibility(View.VISIBLE);
                search.requestFocus();
                ((android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
                        .showSoftInput(search, 0);
            }
        }), new LinearLayout.LayoutParams(ib, ib));
        header.addView(Ui.button(this, "⇅", 22, v -> sortSheet()), new LinearLayout.LayoutParams(ib, ib));
        header.addView(Ui.button(this, "⚙", 22, v -> startActivity(new Intent(this, SettingsActivity.class))),
                new LinearLayout.LayoutParams(ib, ib));
        root.addView(header);

        // --- Recherche
        search = new EditText(this);
        search.setHint("🔍  Rechercher une chanson, un artiste…");
        search.setSingleLine(true);
        search.setTextSize(15);
        search.setTextColor(Ui.TEXT);
        search.setHintTextColor(Ui.SUB);
        search.setBackground(Ui.round(Ui.CARD, Ui.dp(this, 14)));
        search.setPadding(d16, Ui.dp(this, 11), d16, Ui.dp(this, 11));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                query = s.toString().trim().toLowerCase(Locale.ROOT);
                refresh();
            }
        });
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.setMargins(d16, 0, d16, Ui.dp(this, 8));
        search.setVisibility(View.GONE);
        root.addView(search, slp);

        // --- Onglets
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout tabBar = new LinearLayout(this);
        tabBar.setPadding(Ui.dp(this, 12), Ui.dp(this, 4), Ui.dp(this, 12), Ui.dp(this, 8));
        for (int i = 0; i < TABS.length; i++) {
            final int idx = i;
            TextView t = Ui.chip(this, TABS[i], v -> {
                tab = idx;
                detailTracks = null;
                styleTabs();
                refresh();
                list.setSelection(0);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(Ui.dp(this, 4), 0, Ui.dp(this, 4), 0);
            tabBar.addView(t, lp);
            tabViews.add(t);
        }
        hs.addView(tabBar);
        root.addView(hs);
        styleTabs();

        // --- Barre retour (dans un album / playlist)
        backBar = Ui.text(this, "", 17, Ui.TEXT, true);
        backBar.setPadding(Ui.dp(this, 20), Ui.dp(this, 6), d16, Ui.dp(this, 6));
        backBar.setBackground(Ui.ripple(null));
        backBar.setOnClickListener(v -> closeDetail());
        backBar.setVisibility(View.GONE);
        root.addView(backBar);

        // --- Lecture aléatoire
        shuffleBtn = Ui.text(this, "", 14, Ui.TEXT, true);
        shuffleBtn.setGravity(Gravity.CENTER);
        shuffleBtn.setPadding(d16, Ui.dp(this, 11), d16, Ui.dp(this, 11));
        shuffleBtn.setBackground(Ui.ripple(Ui.gradient(Ui.ACCENT, 0xFF6A3DFF, Ui.dp(this, 24))));
        shuffleBtn.setOnClickListener(v -> shuffleAll());
        LinearLayout.LayoutParams sh = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sh.setMargins(d16, Ui.dp(this, 2), d16, Ui.dp(this, 6));
        root.addView(shuffleBtn, sh);

        // --- Liste
        FrameLayout listBox = new FrameLayout(this);
        list = new ListView(this);
        list.setDivider(null);
        list.setSelector(new android.graphics.drawable.ColorDrawable(0));
        list.setAdapter(adapter);
        list.setOnItemClickListener((p, v, pos, id) -> onRowClick(adapter.get(pos)));
        list.setOnItemLongClickListener((p, v, pos, id) -> { onRowLongClick(adapter.get(pos)); return true; });
        listBox.addView(list);
        emptyView = Ui.text(this, "", 15, Ui.SUB, false);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setPadding(Ui.dp(this, 32), 0, Ui.dp(this, 32), 0);
        emptyView.setOnClickListener(v -> {
            if (hasPerm()) return;
            if (Build.VERSION.SDK_INT >= 23 && !shouldShowRequestPermissionRationale(
                    Build.VERSION.SDK_INT >= 33 ? Manifest.permission.READ_MEDIA_AUDIO : Manifest.permission.READ_EXTERNAL_STORAGE)
                    && Store.getBool(this, "asked", false)) {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
            } else {
                Store.putBool(this, "asked", true);
                askPerm();
            }
        });
        listBox.addView(emptyView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(listBox, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        root.addView(buildMini());
        return root;
    }

    private View buildMini() {
        mini = new LinearLayout(this);
        mini.setOrientation(LinearLayout.VERTICAL);
        mini.setBackground(Ui.gradient(Ui.CARD2, Ui.CARD, Ui.dp(this, 18)));
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        int m = Ui.dp(this, 10);
        mlp.setMargins(m, m / 2, m, m);
        mini.setLayoutParams(mlp);
        mini.setClipToOutline(true);

        miniProg = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        miniProg.setMax(1000);
        miniProg.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT2));
        mini.addView(miniProg, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 3)));

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(m, m, m / 2, m);
        miniCover = new CoverView(this, 10, 18);
        int cs = Ui.dp(this, 46);
        row.addView(miniCover, new LinearLayout.LayoutParams(cs, cs));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(m, 0, m, 0);
        miniTitle = Ui.text(this, "", 15, Ui.TEXT, true);
        miniTitle.setSingleLine(true);
        miniTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        miniSub = Ui.text(this, "", 12, Ui.SUB, false);
        miniSub.setSingleLine(true);
        miniSub.setEllipsize(android.text.TextUtils.TruncateAt.END);
        col.addView(miniTitle);
        col.addView(miniSub);
        row.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        int bs = Ui.dp(this, 46);
        miniPlay = new IconView(this, IconView.PLAY, Ui.TEXT);
        miniPlay.setRing(0f);
        miniPlay.setBackground(Ui.ripple(null));
        miniPlay.setOnClickListener(v -> { PlayerService s = PlayerService.get(); if (s != null) s.toggle(); });
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(bs, bs);
        plp.rightMargin = Ui.dp(this, 4);
        row.addView(miniPlay, plp);
        IconView next = new IconView(this, IconView.NEXT, Ui.TEXT);
        next.setBackground(Ui.ripple(null));
        next.setOnClickListener(v -> { PlayerService s = PlayerService.get(); if (s != null) s.next(true); });
        row.addView(next, new LinearLayout.LayoutParams(Ui.dp(this, 40), bs));
        mini.addView(row);
        mini.setOnClickListener(v -> startActivity(new Intent(this, PlayerActivity.class)));
        mini.setVisibility(View.GONE);
        return mini;
    }

    private void styleTabs() {
        for (int i = 0; i < tabViews.size(); i++) {
            TextView t = tabViews.get(i);
            boolean sel = i == tab;
            t.setBackground(Ui.ripple(sel ? Ui.gradient(Ui.ACCENT, 0xFF6A3DFF, Ui.dp(this, 20)) : Ui.round(Ui.CARD, Ui.dp(this, 20))));
            t.setTextColor(sel ? Ui.TEXT : Ui.SUB);
        }
    }

    // =================================================================== Contenu
    private boolean match(Track t) {
        if (query.isEmpty()) return true;
        return t.title.toLowerCase(Locale.ROOT).contains(query)
                || t.artist.toLowerCase(Locale.ROOT).contains(query)
                || t.album.toLowerCase(Locale.ROOT).contains(query);
    }

    private void addTracks(List<Row> rows, List<Track> tracks) {
        for (Track t : tracks) {
            if (!match(t)) continue;
            Row r = new Row(R_TRACK);
            r.track = t;
            rows.add(r);
        }
    }

    private void addGroups(List<Row> rows, Map<String, List<Track>> groups, int mode) {
        for (Map.Entry<String, List<Track>> e : groups.entrySet()) {
            String name = e.getKey();
            List<Track> l = e.getValue();
            if (!query.isEmpty() && !name.toLowerCase(Locale.ROOT).contains(query)) continue;
            Row r = new Row(R_GROUP);
            r.name = name;
            r.tracks = l;
            String n = l.size() + (l.size() > 1 ? " titres" : " titre");
            r.sub = mode == MediaRepo.BY_ALBUM ? l.get(0).artist + "  ·  " + n : n;
            rows.add(r);
        }
    }

    private void refresh() {
        List<Row> rows = new ArrayList<>();
        shownTracks.clear();
        if (!hasPerm()) {
            show(rows, "🎵\n\nNOX Player a besoin d'accéder à ta musique et tes vidéos.\n\nAppuie ici pour autoriser.");
            return;
        }
        if (!loaded) {
            show(rows, "Recherche de ta musique…");
            return;
        }
        if (detailTracks != null) {
            if (detailPlaylist) {
                List<Long> ids = Store.playlists(this).get(detailName);
                detailTracks = MediaRepo.byIds(ids == null ? new ArrayList<>() : ids);
            }
            backBar.setText("←   " + detailName);
            backBar.setVisibility(View.VISIBLE);
            addTracks(rows, detailTracks);
        } else {
            backBar.setVisibility(View.GONE);
            switch (tab) {
                case T_SONGS:
                    if (query.isEmpty()) rows.add(new Row(R_ADD));
                    addTracks(rows, MediaRepo.sorted(this, MediaRepo.songs,
                            Store.getInt(this, "sort", 0), Store.getBool(this, "sort_rev", false)));
                    break;
                case T_ALBUMS: addGroups(rows, MediaRepo.group(MediaRepo.songs, MediaRepo.BY_ALBUM), MediaRepo.BY_ALBUM); break;
                case T_ARTISTS: addGroups(rows, MediaRepo.group(MediaRepo.songs, MediaRepo.BY_ARTIST), MediaRepo.BY_ARTIST); break;
                case T_FOLDERS: addGroups(rows, MediaRepo.group(MediaRepo.songs, MediaRepo.BY_FOLDER), MediaRepo.BY_FOLDER); break;
                case T_FAVS: {
                    List<Track> favs = MediaRepo.byIds(new ArrayList<>(Store.favs(this)));
                    Collections.sort(favs, (a, b) -> a.title.compareToIgnoreCase(b.title));
                    addTracks(rows, favs);
                    break;
                }
                case T_PLAYLISTS: {
                    rows.add(new Row(R_NEWPL));
                    LinkedHashMap<String, List<Long>> pls = Store.playlists(this);
                    for (String name : pls.keySet()) {
                        if (!query.isEmpty() && !name.toLowerCase(Locale.ROOT).contains(query)) continue;
                        Row r = new Row(R_PLAYLIST);
                        r.name = name;
                        r.tracks = MediaRepo.byIds(pls.get(name));
                        r.sub = r.tracks.size() + (r.tracks.size() > 1 ? " titres" : " titre");
                        rows.add(r);
                    }
                    break;
                }
                case T_RECENT: addTracks(rows, MediaRepo.byIds(Store.recent(this))); break;
            }
        }
        for (Row r : rows) if (r.type == R_TRACK) shownTracks.add(r.track);

        String empty;
        if (!query.isEmpty()) empty = "Aucun résultat pour « " + query + " »";
        else if (detailTracks != null) empty = detailPlaylist ? "Playlist vide.\nAppui long sur une chanson → « Ajouter à une playlist »" : "Rien ici";
        else if (tab == T_FAVS) empty = "Pas encore de favoris.\nAppuie sur ♥ dans le lecteur.";
        else if (tab == T_RECENT) empty = "Rien écouté pour l'instant";
        else empty = "Aucune musique trouvée.\nAjoute des fichiers audio sur ton téléphone, ils apparaîtront ici tout seuls.";
        show(rows, empty);
    }

    private void show(List<Row> rows, String emptyText) {
        int audio = 0;
        for (Track t : shownTracks) if (!t.video) audio++;
        shuffleBtn.setVisibility(audio > 1 ? View.VISIBLE : View.GONE);
        shuffleBtn.setText("⇄   Lecture aléatoire  ·  " + audio + " titres");
        emptyView.setText(emptyText);
        boolean onlyAdd = rows.size() == 1 && rows.get(0).type == R_ADD;
        emptyView.setVisibility(rows.isEmpty() || onlyAdd ? View.VISIBLE : View.GONE);
        adapter.set(rows);
    }

    private void openDetail(String name, List<Track> tracks, boolean playlist) {
        detailName = name;
        detailTracks = tracks;
        detailPlaylist = playlist;
        refresh();
        list.setSelection(0);
    }

    private void closeDetail() {
        detailTracks = null;
        detailPlaylist = false;
        refresh();
    }

    private List<Track> audioOf(List<Track> l) {
        List<Track> q = new ArrayList<>();
        for (Track t : l) if (!t.video) q.add(t);
        return q;
    }

    private void shuffleAll() {
        List<Track> q = audioOf(shownTracks);
        if (q.isEmpty()) return;
        PlayerService s = PlayerService.get();
        if (s != null && !s.shuffle()) s.setShuffle(true);
        else if (s == null) Store.putBool(this, "shuffle", true);
        PlayerService.playQueue(this, q, (int) (Math.random() * q.size()));
    }

    private void playTrack(Track t) {
        List<Track> q = audioOf(shownTracks);
        int i = q.indexOf(t);
        if (i < 0) { q = Collections.singletonList(t); i = 0; }
        PlayerService.playQueue(this, q, i);
    }

    private void onRowClick(Row r) {
        switch (r.type) {
            case R_TRACK: playTrack(r.track); break;
            case R_GROUP: openDetail(r.name, r.tracks, false); break;
            case R_PLAYLIST: openDetail(r.name, r.tracks, true); break;
            case R_NEWPL: newPlaylistDialog(null); break;
            case R_ADD: addMusicMenu(); break;
        }
    }

    private void onRowLongClick(Row r) {
        if (r.type == R_TRACK) trackOptions(r.track);
        else if (r.type == R_PLAYLIST) playlistOptions(r.name, r.tracks);
        else if (r.type == R_GROUP) {
            Opts o = new Opts();
            o.add("▶  Tout lire", () -> PlayerService.playQueue(this, r.tracks, 0));
            o.add("Ajouter à la file d'attente", () -> { for (Track t : r.tracks) addToQueue(t); });
            o.show(r.name);
        }
    }

    // =================================================================== Menus
    private final class Opts {
        final List<String> labels = new ArrayList<>();
        final List<Runnable> actions = new ArrayList<>();
        void add(String l, Runnable a) { labels.add(l); actions.add(a); }
        void show(String title) {
            new AlertDialog.Builder(MainActivity.this, Ui.DIALOG).setTitle(title)
                    .setItems(labels.toArray(new String[0]), (d, w) -> actions.get(w).run()).show();
        }
    }

    private void addToQueue(Track t) {
        PlayerService s = PlayerService.get();
        if (s != null) s.addToQueue(t); else PlayerService.playQueue(this, Collections.singletonList(t), 0);
    }

    private void trackOptions(Track t) {
        TrackMenu.show(this, t, detailPlaylist ? detailName : null, () -> {
            if (MainActivity.needsReload) { needsReload = false; loadMedia(); } else refresh();
        });
    }

    private void playlistOptions(String name, List<Track> tracks) {
        Opts o = new Opts();
        o.add("▶  Lire", () -> PlayerService.playQueue(this, tracks, 0));
        o.add("Renommer", () -> {
            EditText in = input(name);
            new AlertDialog.Builder(this, Ui.DIALOG).setTitle("Renommer").setView(wrap(in))
                    .setNegativeButton("Annuler", null)
                    .setPositiveButton("OK", (d, w) -> {
                        String n = in.getText().toString().trim();
                        if (!n.isEmpty()) { Store.renamePlaylist(this, name, n); refresh(); }
                    }).show();
        });
        o.add("Supprimer", () -> new AlertDialog.Builder(this, Ui.DIALOG).setTitle("Supprimer « " + name + " » ?")
                .setNegativeButton("Annuler", null)
                .setPositiveButton("Supprimer", (d, w) -> { Store.deletePlaylist(this, name); refresh(); }).show());
        o.show(name);
    }

    private EditText input(String text) {
        EditText in = new EditText(this);
        in.setSingleLine(true);
        in.setText(text);
        in.setTextColor(Ui.TEXT);
        in.setHintTextColor(Ui.SUB);
        if (text != null) in.setSelection(text.length());
        return in;
    }

    private View wrap(View v) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(this, 22);
        box.setPadding(p, p / 2, p, 0);
        box.addView(v, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return box;
    }

    private void newPlaylistDialog(Track toAdd) {
        EditText in = input("");
        in.setHint("Nom de la playlist");
        new AlertDialog.Builder(this, Ui.DIALOG).setTitle("Nouvelle playlist").setView(wrap(in))
                .setNegativeButton("Annuler", null)
                .setPositiveButton("Créer", (d, w) -> {
                    String n = in.getText().toString().trim();
                    if (n.isEmpty()) return;
                    if (!Store.createPlaylist(this, n)) { Ui.toast(this, "Cette playlist existe déjà"); }
                    if (toAdd != null) { Store.addToPlaylist(this, n, toAdd.id); Ui.toast(this, "Ajouté à « " + n + " »"); }
                    refresh();
                }).show();
    }

    private void pickPlaylist(Track t) {
        List<String> names = new ArrayList<>(Store.playlists(this).keySet());
        Opts o = new Opts();
        o.add("＋  Nouvelle playlist", () -> newPlaylistDialog(t));
        for (String n : names) {
            o.add(n, () -> Ui.toast(this, Store.addToPlaylist(this, n, t.id) ? "Ajouté à « " + n + " »" : "Déjà dans « " + n + " »"));
        }
        o.show("Ajouter à…");
    }

    private void share(Track t) {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("audio/*");
        i.putExtra(Intent.EXTRA_STREAM, t.uri());
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(i, "Partager « " + t.title + " »"));
    }

    private void info(Track t) {
        String msg = "Titre : " + t.title
                + (t.video ? "" : "\nArtiste : " + t.artist + "\nAlbum : " + t.album)
                + "\nDurée : " + Ui.time(t.duration)
                + "\nDossier : " + t.folder
                + "\n\n" + t.path;
        new AlertDialog.Builder(this, Ui.DIALOG).setTitle("Infos").setMessage(msg).setPositiveButton("OK", null).show();
    }

    // =================================================================== Ajouter de la musique
    private void addMusicMenu() {
        Opts o = new Opts();
        o.add("📁  Importer des fichiers audio", () -> {
            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.setType("audio/*");
            i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            startActivityForResult(Intent.createChooser(i, "Choisir des musiques"), REQ_IMPORT);
        });
        o.add("🎬  Convertir une vidéo en audio", () -> {
            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.setType("video/*");
            startActivityForResult(Intent.createChooser(i, "Choisir une vidéo"), REQ_VIDEO);
        });
        o.add("🔗  Importer depuis un lien direct", () -> ToolsUi.importLinkDialog(this));
        o.show("Ajouter plus de musique");
    }

    // =================================================================== Modifier une musique
    private void editDialog(Track t) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(this, 22);
        box.setPadding(p, p / 2, p, 0);

        LinearLayout coverRow = new LinearLayout(this);
        coverRow.setGravity(Gravity.CENTER_VERTICAL);
        CoverView cv = new CoverView(this, 12, 26);
        cv.bind(t, 300);
        int cs = Ui.dp(this, 84);
        coverRow.addView(cv, new LinearLayout.LayoutParams(cs, cs));
        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.VERTICAL);
        btns.setPadding(Ui.dp(this, 14), 0, 0, 0);
        AlertDialog[] holder = new AlertDialog[1];
        btns.addView(Ui.chip(this, "🖼  Changer la photo", v -> {
            coverTarget = t;
            if (holder[0] != null) holder[0].dismiss();
            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.setType("image/*");
            startActivityForResult(Intent.createChooser(i, "Choisir une photo"), REQ_COVER);
        }));
        TextView reset = Ui.chip(this, "Photo d'origine", v -> {
            Store.coverFile(this, t.id).delete();
            t.coverStamp = System.currentTimeMillis();
            cv.bind(t, 300);
            refresh();
        });
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rl.topMargin = Ui.dp(this, 8);
        btns.addView(reset, rl);
        coverRow.addView(btns);
        box.addView(coverRow);

        EditText title = field(box, "Titre", t.title);
        EditText artist = field(box, "Artiste", t.artist);
        EditText album = field(box, "Album", t.album);

        holder[0] = new AlertDialog.Builder(this, Ui.DIALOG).setTitle("✎  Modifier")
                .setView(box)
                .setNegativeButton("Annuler", null)
                .setPositiveButton("Enregistrer", (d, w) -> {
                    String ti = title.getText().toString().trim();
                    String ar = artist.getText().toString().trim();
                    String al = album.getText().toString().trim();
                    if (!ti.isEmpty()) t.title = ti;
                    if (!ar.isEmpty()) t.artist = ar;
                    if (!al.isEmpty()) t.album = al;
                    Store.saveEdits(this, t);
                    refresh();
                    onPlayerChanged();
                    Ui.toast(this, "✓ Modifié");
                }).show();
    }

    private EditText field(LinearLayout box, String label, String value) {
        TextView l = Ui.text(this, label, 12, Ui.SUB, false);
        l.setPadding(0, Ui.dp(this, 14), 0, 0);
        box.addView(l);
        EditText e = input(value);
        box.addView(e, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return e;
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res == RESULT_OK && data != null && req == REQ_VIDEO && data.getData() != null) {
            ToolsUi.convertToAudio(this, Track.fromUri(this, data.getData(), true));
            return;
        }
        if (res == RESULT_OK && data != null && req == REQ_IMPORT) {
            List<Uri> uris = new ArrayList<>();
            if (data.getClipData() != null) {
                for (int k = 0; k < data.getClipData().getItemCount(); k++) uris.add(data.getClipData().getItemAt(k).getUri());
            } else if (data.getData() != null) uris.add(data.getData());
            ToolsUi.importFiles(this, uris);
            return;
        }
        if (req != REQ_COVER || res != RESULT_OK || data == null || data.getData() == null || coverTarget == null) return;
        Track t = coverTarget;
        Uri img = data.getData();
        new Thread(() -> {
            boolean ok = saveCover(img, Store.coverFile(this, t.id));
            runOnUiThread(() -> {
                if (ok) {
                    t.coverStamp = System.currentTimeMillis();
                    refresh();
                    onPlayerChanged();
                    Ui.toast(this, "✓ Photo changée");
                    editDialog(t);
                } else Ui.toast(this, "Impossible de lire cette image");
            });
        }).start();
    }

    private boolean saveCover(Uri img, File dest) {
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

    // =================================================================== Tri
    private void sortSheet() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int cur = Store.getInt(this, "sort", 0);
        boolean rev = Store.getBool(this, "sort_rev", false);
        android.app.Dialog[] d = new android.app.Dialog[1];
        for (int i = 0; i < MediaRepo.SORT_NAMES.length; i++) {
            final int k = i;
            LinearLayout r = new LinearLayout(this);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPadding(Ui.dp(this, 30), Ui.dp(this, 18), Ui.dp(this, 26), Ui.dp(this, 18));
            r.setBackground(Ui.ripple(null));
            TextView t = Ui.text(this, MediaRepo.SORT_NAMES[i], 19, i == cur ? Ui.ACCENT2 : 0xFFCCCCCC, false);
            r.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
            if (i == cur) r.addView(Ui.text(this, rev ? "⇡" : "⇣", 22, Ui.ACCENT2, true));
            r.setOnClickListener(v -> {
                if (k == cur) Store.putBool(this, "sort_rev", !rev);
                else { Store.putInt(this, "sort", k); Store.putBool(this, "sort_rev", false); }
                d[0].dismiss();
                tab = T_SONGS;
                detailTracks = null;
                styleTabs();
                refresh();
                list.setSelection(0);
            });
            box.addView(r);
        }
        d[0] = Ui.sheet(this, "Tri des chansons", box);
    }

    private void hideKeyboard() {
        ((android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
                .hideSoftInputFromWindow(search.getWindowToken(), 0);
    }

    // =================================================================== Réglages
    private void settingsDialog() {
        boolean auto = Store.getBool(this, "auto_audio", false);
        Opts o = new Opts();
        o.add((auto ? "☑" : "☐") + "  Nouvelles vidéos du téléphone → audio automatiquement", () -> {
            Store.putBool(this, "auto_audio", !auto);
            int newest = 0;
            for (Track v : MediaRepo.videos) newest = (int) Math.max(newest, v.dateAdded);
            Store.putInt(this, "video_seen", newest);
            Ui.toast(this, !auto ? "Activé : chaque nouvelle vidéo sera aussi ajoutée en audio" : "Désactivé");
        });
        o.add("🔗  Importer depuis un lien direct", () -> ToolsUi.importLinkDialog(this));
        o.add("⟳  Rescanner la bibliothèque", () -> { loadMedia(); Ui.toast(this, "Scan en cours…"); });
        o.add("À propos", () -> new AlertDialog.Builder(this, Ui.DIALOG).setTitle("NOX Player")
                .setMessage("Lecteur de musique.\nVersion 1.0\n\nfait par math_0235").setPositiveButton("OK", null).show());
        o.show("Réglages");
    }

    // =================================================================== Mini-lecteur
    @Override
    public void onPlayerChanged() {
        PlayerService s = PlayerService.get();
        Track t = s != null ? s.current() : null;
        if (t == null) { mini.setVisibility(View.GONE); adapter.notifyDataSetChanged(); return; }
        mini.setVisibility(View.VISIBLE);
        miniTitle.setText(t.title);
        miniSub.setText(t.artist);
        miniCover.bind(t, Ui.dp(this, 46));
        miniPlay.setKind(s.isPlaying() ? IconView.PAUSE : IconView.PLAY);
        updateMiniProgress();
        adapter.notifyDataSetChanged();
    }

    private void updateMiniProgress() {
        PlayerService s = PlayerService.get();
        if (s == null || s.current() == null) return;
        int d = s.duration();
        float f = d > 0 ? s.position() / (float) d : 0f;
        miniProg.setProgress((int) (f * 1000));
        miniPlay.setRing(f);
    }

    // =================================================================== Adaptateur de liste
    private final class RowAdapter extends BaseAdapter {
        private List<Row> rows = new ArrayList<>();

        void set(List<Row> r) { rows = r; notifyDataSetChanged(); }
        Row get(int i) { return rows.get(i); }

        @Override public int getCount() { return rows.size(); }
        @Override public Object getItem(int i) { return rows.get(i); }
        @Override public long getItemId(int i) { return i; }

        final class Holder {
            CoverView cover;
            TextView t1, t2, more;
        }

        @Override
        public View getView(int i, View cv, ViewGroup parent) {
            Holder hd;
            int cs = Ui.dp(MainActivity.this, 50);
            if (cv == null) {
                LinearLayout row = new LinearLayout(MainActivity.this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                int p = Ui.dp(MainActivity.this, 16);
                row.setPadding(p, Ui.dp(MainActivity.this, 7), Ui.dp(MainActivity.this, 4), Ui.dp(MainActivity.this, 7));
                row.setBackground(Ui.ripple(null));
                hd = new Holder();
                hd.cover = new CoverView(MainActivity.this, 10, 18);
                row.addView(hd.cover, new LinearLayout.LayoutParams(cs, cs));
                LinearLayout col = new LinearLayout(MainActivity.this);
                col.setOrientation(LinearLayout.VERTICAL);
                col.setPadding(Ui.dp(MainActivity.this, 14), 0, Ui.dp(MainActivity.this, 6), 0);
                hd.t1 = Ui.text(MainActivity.this, "", 15, Ui.TEXT, true);
                hd.t1.setSingleLine(true);
                hd.t1.setEllipsize(android.text.TextUtils.TruncateAt.END);
                hd.t2 = Ui.text(MainActivity.this, "", 12.5f, Ui.SUB, false);
                hd.t2.setSingleLine(true);
                hd.t2.setEllipsize(android.text.TextUtils.TruncateAt.END);
                col.addView(hd.t1);
                col.addView(hd.t2);
                row.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                hd.more = Ui.button(MainActivity.this, "⋮", 22, null);
                hd.more.setTextColor(Ui.SUB);
                int mb = Ui.dp(MainActivity.this, 44);
                row.addView(hd.more, new LinearLayout.LayoutParams(mb, mb));
                row.setTag(hd);
                cv = row;
            } else {
                hd = (Holder) cv.getTag();
            }
            Row r = rows.get(i);
            hd.more.setOnClickListener(v -> onRowLongClick(r));
            hd.more.setVisibility(r.type == R_NEWPL || r.type == R_ADD ? View.GONE : View.VISIBLE);
            switch (r.type) {
                case R_TRACK: {
                    Track t = r.track;
                    PlayerService s = PlayerService.get();
                    Track cur = s != null ? s.current() : null;
                    boolean playing = cur != null && cur.key().equals(t.key());
                    hd.cover.bind(t, cs);
                    hd.t1.setText((playing ? "♪  " : "") + t.title);
                    hd.t1.setTextColor(playing ? Ui.ACCENT2 : Ui.TEXT);
                    String fav = !t.video && Store.isFav(MainActivity.this, t.id) ? "♥  " : "";
                    hd.t2.setText(fav + t.artist + "  ·  " + Ui.time(t.duration));
                    break;
                }
                case R_GROUP:
                case R_PLAYLIST:
                    if (r.tracks != null && !r.tracks.isEmpty()) hd.cover.bind(r.tracks.get(0), cs);
                    else hd.cover.bindLetter("♫", Ui.colorFor(r.name));
                    hd.t1.setText(r.name);
                    hd.t1.setTextColor(Ui.TEXT);
                    hd.t2.setText(r.sub);
                    break;
                case R_ADD:
                    hd.cover.bindLetter("＋", 0xFF2A2140);
                    hd.t1.setText("Ajouter plus de musique");
                    hd.t1.setTextColor(Ui.SUB);
                    hd.t2.setText("Importer · vidéo → audio · lien");
                    break;
                case R_NEWPL:
                    hd.cover.bindLetter("＋", Ui.ACCENT);
                    hd.t1.setText("Nouvelle playlist");
                    hd.t1.setTextColor(Ui.ACCENT2);
                    hd.t2.setText("Crée ta propre liste");
                    break;
            }
            return cv;
        }
    }
}
