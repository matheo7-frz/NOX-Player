package com.math0235.noxplayer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** File d'attente façon Lark Player : titre en cours en avant, poignées pour réorganiser, 3 modes en bas. */
public class QueueActivity extends Activity implements PlayerService.Listener {
    private static final String[] MODE_NAMES = {"Répéter un titre", "Répéter la liste", "Lecture aléatoire"};
    private static final String[] MODE_ICONS = {"↻¹", "↻", "⇄"};

    private int bg, accent;
    private CoverView headCover;
    private TextView headTitle, modeTitle;
    private IconView headPlay;
    private ProgressBar headProg;
    private ListView list;
    private final QAdapter adapter = new QAdapter();
    private final TextView[] modeBtns = new TextView[3];
    private List<Track> items = new ArrayList<>();
    private int curPos;
    private int dragFrom = -1;
    private boolean firstScroll = true;

    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override public void run() { updateProgress(); h.postDelayed(this, 500); }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (!Guard.isOfficial(this)) { finish(); return; }
        bg = getIntent().getIntExtra("bg", 0xFF1C1726);
        accent = getIntent().getIntExtra("accent", 0xFFDCC4FF);
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        setContentView(build());
    }

    @Override
    protected void onResume() {
        super.onResume();
        PlayerService.addListener(this);
        onPlayerChanged();
        h.post(tick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        PlayerService.removeListener(this);
        h.removeCallbacks(tick);
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    private static PlayerService ps() { return PlayerService.get(); }

    private static int lighten(int c, float amount) {
        float[] hsv = new float[3];
        Color.colorToHSV(c, hsv);
        hsv[2] = Math.min(1f, hsv[2] + amount);
        return Color.HSVToColor(hsv);
    }

    private View build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg);
        int pad = Ui.dp(this, 20);

        // --- Mini en-tête : titre en cours + barre
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(pad, Ui.dp(this, 14), pad, Ui.dp(this, 16));
        headCover = new CoverView(this, 12, 20);
        int hc = Ui.dp(this, 56);
        head.addView(headCover, new LinearLayout.LayoutParams(hc, hc));
        LinearLayout hcol = new LinearLayout(this);
        hcol.setOrientation(LinearLayout.VERTICAL);
        hcol.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 10), 0);
        headTitle = Ui.text(this, "", 19, accent, true);
        headTitle.setSingleLine(true);
        headTitle.setEllipsize(TextUtils.TruncateAt.END);
        hcol.addView(headTitle);
        headProg = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        headProg.setMax(1000);
        headProg.setProgressTintList(ColorStateList.valueOf((accent & 0x00FFFFFF) | 0xAA000000));
        headProg.setProgressBackgroundTintList(ColorStateList.valueOf(0x22FFFFFF));
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(-1, Ui.dp(this, 5));
        plp.topMargin = Ui.dp(this, 10);
        hcol.addView(headProg, plp);
        head.addView(hcol, new LinearLayout.LayoutParams(0, -2, 1));
        headPlay = new IconView(this, IconView.PAUSE, accent);
        headPlay.setBackground(Ui.ripple(null));
        headPlay.setOnClickListener(v -> { PlayerService s = ps(); if (s != null) s.toggle(); });
        int pb = Ui.dp(this, 44);
        head.addView(headPlay, new LinearLayout.LayoutParams(pb, pb));
        head.setOnClickListener(v -> finish());
        root.addView(head);

        // --- Feuille
        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        android.graphics.drawable.GradientDrawable sbg = new android.graphics.drawable.GradientDrawable();
        sbg.setColor(lighten(bg, 0.04f));
        float r = Ui.dp(this, 26);
        sbg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        sheet.setBackground(sbg);

        // poignée ⌄
        IconView grip = new IconView(this, IconView.NEXT, 0x55FFFFFF);
        grip.setRotation(90);
        grip.setBackground(Ui.ripple(null));
        grip.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 34));
        glp.gravity = Gravity.CENTER_HORIZONTAL;
        glp.topMargin = Ui.dp(this, 6);
        sheet.addView(grip, glp);

        // Titre du mode + boutons
        LinearLayout trow = new LinearLayout(this);
        trow.setGravity(Gravity.CENTER_VERTICAL);
        trow.setPadding(pad, Ui.dp(this, 4), Ui.dp(this, 8), Ui.dp(this, 10));
        modeTitle = Ui.text(this, "", 25, Ui.TEXT, true);
        trow.addView(modeTitle, new LinearLayout.LayoutParams(0, -2, 1));
        int ib = Ui.dp(this, 46);
        TextView locate = Ui.button(this, "◎", 24, v -> list.smoothScrollToPositionFromTop(curPos, 0));
        locate.setTextColor(0xCCFFFFFF);
        TextView clear = Ui.button(this, "🧹", 20, v -> new AlertDialog.Builder(this, Ui.DIALOG)
                .setTitle("Vider la file d'attente ?")
                .setMessage("Seul le titre en cours sera gardé.")
                .setNegativeButton("Annuler", null)
                .setPositiveButton("Vider", (d, w) -> { PlayerService s = ps(); if (s != null) s.clearQueue(); })
                .show());
        trow.addView(locate, new LinearLayout.LayoutParams(ib, ib));
        trow.addView(clear, new LinearLayout.LayoutParams(ib, ib));
        sheet.addView(trow);

        // Liste
        list = new ListView(this);
        list.setDivider(null);
        list.setSelector(new android.graphics.drawable.ColorDrawable(0));
        list.setAdapter(adapter);
        list.setOnItemClickListener((p, v, pos, id) -> { PlayerService s = ps(); if (s != null) s.jumpTo(pos); });
        list.setOnItemLongClickListener((p, v, pos, id) -> {
            if (pos == curPos) return true;
            Track t = items.get(pos);
            new AlertDialog.Builder(this, Ui.DIALOG).setTitle(t.title)
                    .setItems(new String[]{"▶  Lire maintenant", "Jouer ensuite", "Retirer de la file"}, (d, w) -> {
                        PlayerService s = ps();
                        if (s == null) return;
                        if (w == 0) s.jumpTo(pos);
                        else if (w == 1) s.moveInQueue(pos, pos > curPos ? curPos + 1 : curPos);
                        else s.removeFromQueue(pos);
                    }).show();
            return true;
        });
        sheet.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));

        // Les 3 modes
        LinearLayout modes = new LinearLayout(this);
        modes.setPadding(pad, Ui.dp(this, 10), pad, Ui.dp(this, 18));
        for (int i = 0; i < 3; i++) {
            final int m = i;
            TextView b = Ui.text(this, MODE_ICONS[i], 24, Ui.TEXT, true);
            b.setGravity(Gravity.CENTER);
            b.setOnClickListener(v -> {
                PlayerService s = ps();
                if (s == null) return;
                s.setMode(m);
                Ui.toast(this, MODE_NAMES[m]);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(this, 56), 1);
            lp.setMargins(Ui.dp(this, 6), 0, Ui.dp(this, 6), 0);
            modes.addView(b, lp);
            modeBtns[i] = b;
        }
        sheet.addView(modes);

        root.addView(sheet, new LinearLayout.LayoutParams(-1, 0, 1));
        return root;
    }

    @Override
    public void onPlayerChanged() {
        PlayerService s = ps();
        if (s == null || s.current() == null) { finish(); return; }
        Track t = s.current();
        headCover.bind(t, Ui.dp(this, 56));
        headTitle.setText(t.title);
        headPlay.setKind(s.isPlaying() ? IconView.PAUSE : IconView.PLAY);
        if (dragFrom < 0) {
            items = s.orderedQueue();
            curPos = s.queuePosition();
            adapter.notifyDataSetChanged();
        }
        int mode = s.mode();
        modeTitle.setText(MODE_NAMES[mode]);
        float rr = Ui.dp(this, 28);
        for (int i = 0; i < 3; i++) {
            boolean sel = i == mode;
            modeBtns[i].setBackground(Ui.ripple(Ui.round(sel ? accent : lighten(bg, 0.09f), rr)));
            modeBtns[i].setTextColor(sel ? bg : 0xDDFFFFFF);
        }
        if (firstScroll) {
            firstScroll = false;
            list.setSelectionFromTop(curPos, 0);
        }
        updateProgress();
    }

    private void updateProgress() {
        PlayerService s = ps();
        if (s == null) return;
        int d = s.duration();
        headProg.setProgress(d > 0 ? (int) ((long) s.position() * 1000 / d) : 0);
    }

    /** Glisser la poignée ≡ pour déplacer un titre. */
    private boolean onHandleTouch(int position, MotionEvent e) {
        int a = e.getActionMasked();
        PlayerService s = ps();
        if (s == null) return false;
        if (a == MotionEvent.ACTION_DOWN) {
            dragFrom = position;
            list.requestDisallowInterceptTouchEvent(true);
            adapter.notifyDataSetChanged();
            return true;
        }
        if (dragFrom < 0) return false;
        if (a == MotionEvent.ACTION_MOVE) {
            int[] loc = new int[2];
            list.getLocationOnScreen(loc);
            int y = (int) e.getRawY() - loc[1];
            int target = list.pointToPosition(Ui.dp(this, 40), y);
            if (y < Ui.dp(this, 30)) list.smoothScrollBy(-Ui.dp(this, 20), 50);
            else if (y > list.getHeight() - Ui.dp(this, 30)) list.smoothScrollBy(Ui.dp(this, 20), 50);
            if (target != ListView.INVALID_POSITION && target != dragFrom) {
                Track moved = items.remove(dragFrom);
                items.add(target, moved);
                if (curPos == dragFrom) curPos = target;
                else if (dragFrom < curPos && target >= curPos) curPos--;
                else if (dragFrom > curPos && target <= curPos) curPos++;
                s.moveInQueueSilently(dragFrom, target);
                dragFrom = target;
                adapter.notifyDataSetChanged();
            }
            return true;
        }
        if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
            dragFrom = -1;
            s.notifyQueueChanged();
            return true;
        }
        return true;
    }

    private final class QAdapter extends BaseAdapter {
        @Override public int getCount() { return items.size(); }
        @Override public Object getItem(int i) { return items.get(i); }
        @Override public long getItemId(int i) { return i; }

        final class Holder {
            CoverView cover;
            TextView t1, t2, handle;
            View line;
        }

        @Override
        public View getView(int i, View cv, ViewGroup parent) {
            Holder hd;
            int cs = Ui.dp(QueueActivity.this, 54);
            if (cv == null) {
                LinearLayout outer = new LinearLayout(QueueActivity.this);
                outer.setOrientation(LinearLayout.VERTICAL);
                LinearLayout row = new LinearLayout(QueueActivity.this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                int p = Ui.dp(QueueActivity.this, 20);
                row.setPadding(p, Ui.dp(QueueActivity.this, 8), Ui.dp(QueueActivity.this, 6), Ui.dp(QueueActivity.this, 8));
                hd = new Holder();
                hd.cover = new CoverView(QueueActivity.this, 12, 18);
                row.addView(hd.cover, new LinearLayout.LayoutParams(cs, cs));
                LinearLayout col = new LinearLayout(QueueActivity.this);
                col.setOrientation(LinearLayout.VERTICAL);
                col.setPadding(Ui.dp(QueueActivity.this, 14), 0, Ui.dp(QueueActivity.this, 6), 0);
                hd.t1 = Ui.text(QueueActivity.this, "", 17, Ui.TEXT, true);
                hd.t1.setSingleLine(true);
                hd.t1.setEllipsize(TextUtils.TruncateAt.END);
                hd.t2 = Ui.text(QueueActivity.this, "", 14, Ui.SUB, false);
                hd.t2.setSingleLine(true);
                hd.t2.setEllipsize(TextUtils.TruncateAt.END);
                col.addView(hd.t1);
                col.addView(hd.t2);
                row.addView(col, new LinearLayout.LayoutParams(0, -2, 1));
                hd.handle = Ui.text(QueueActivity.this, "≡", 28, 0x88FFFFFF, false);
                hd.handle.setGravity(Gravity.CENTER);
                int hb = Ui.dp(QueueActivity.this, 48);
                row.addView(hd.handle, new LinearLayout.LayoutParams(hb, hb));
                outer.addView(row);
                hd.line = new View(QueueActivity.this);
                hd.line.setBackgroundColor(0x22FFFFFF);
                LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(-1, Ui.dp(QueueActivity.this, 1));
                ll.setMargins(p, Ui.dp(QueueActivity.this, 4), p, Ui.dp(QueueActivity.this, 4));
                outer.addView(hd.line, ll);
                outer.setTag(hd);
                cv = outer;
            } else {
                hd = (Holder) cv.getTag();
            }
            Track t = items.get(i);
            boolean isCur = i == curPos;
            hd.cover.bind(t, cs);
            hd.t1.setText((isCur ? "♪  " : "") + t.title);
            hd.t1.setTextColor(isCur ? Ui.TEXT : 0xFFFFFFFF);
            hd.t2.setText(t.artist);
            hd.line.setVisibility(isCur ? View.VISIBLE : View.GONE);
            hd.handle.setVisibility(isCur ? View.INVISIBLE : View.VISIBLE);
            float alpha = isCur ? 1f : i == dragFrom ? 0.95f : i < curPos ? 0.25f : 0.45f;
            cv.setAlpha(alpha);
            cv.setBackgroundColor(i == dragFrom ? 0x22FFFFFF : 0);
            final int pos = i;
            hd.handle.setOnTouchListener((v, e) -> onHandleTouch(pos == dragFrom || dragFrom < 0 ? pos : dragFrom, e));
            return cv;
        }
    }
}
