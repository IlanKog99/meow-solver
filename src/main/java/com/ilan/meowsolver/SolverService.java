package com.ilan.meowsolver;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.ContextThemeWrapper;
import android.view.Display;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;

/** Floating panel (Solve / Cat / X) drawn as an accessibility overlay; reads the screen and taps cells. */
public class SolverService extends AccessibilityService {
    private static final int GAP_MS = 90;       // between taps on different cells
    private static final int SAME_CELL_MS = 350; // between repeated taps on one cell (avoid double-tap detection)

    private final Handler h = new Handler(Looper.getMainLooper());
    private final ArrayDeque<int[]> taps = new ArrayDeque<>(); // {x, y, delayAfterMs}
    private WindowManager wm;
    private LinearLayout panel;
    private Marks marks;
    private TextView status;
    private Button cycleBtn;
    private SharedPreferences prefs;
    private boolean busy;

    @Override public void onAccessibilityEvent(AccessibilityEvent e) { }
    @Override public void onInterrupt() { }

    @Override protected void onServiceConnected() {
        wm = getSystemService(WindowManager.class);
        prefs = getSharedPreferences("p", MODE_PRIVATE);

        marks = new Marks(this);
        WindowManager.LayoutParams mlp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        wm.addView(marks, mlp);
        buildPanel();
    }

    @Override public void onDestroy() {
        if (panel != null) wm.removeView(panel);
        if (marks != null) wm.removeView(marks);
        super.onDestroy();
    }

    // ---------------- UI ----------------

    private void buildPanel() {
        Context c = new ContextThemeWrapper(this, android.R.style.Theme_DeviceDefault);
        float dp = getResources().getDisplayMetrics().density;
        panel = new LinearLayout(c);
        panel.setOrientation(LinearLayout.VERTICAL);
        int p = (int) (6 * dp);
        panel.setPadding(p, p, p, p);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xE6202020);
        bg.setCornerRadius(14 * dp);
        panel.setBackground(bg);

        status = new TextView(c);
        status.setTextColor(Color.WHITE);
        status.setTextSize(13);
        status.setPadding(p, 0, p, p);
        status.setText("☰ Meow Solver (drag here)");
        panel.addView(status);

        LinearLayout row = new LinearLayout(c);
        row.addView(btn(c, "Solve", v -> solve()));
        row.addView(btn(c, "Cat", v -> placeCat()));
        row.addView(btn(c, "X", v -> markX()));
        panel.addView(row);

        cycleBtn = btn(c, "", v -> {
            prefs.edit().putBoolean("catFirst", !catFirst()).apply();
            updateCycleLabel();
        });
        cycleBtn.setTextSize(11);
        updateCycleLabel();
        panel.addView(cycleBtn);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = prefs.getInt("px", (int) (16 * dp));
        lp.y = prefs.getInt("py", (int) (90 * dp));
        status.setOnTouchListener(new View.OnTouchListener() {
            float sx, sy; int ox, oy;
            @Override public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN: sx = e.getRawX(); sy = e.getRawY(); ox = lp.x; oy = lp.y; return true;
                    case MotionEvent.ACTION_MOVE:
                        lp.x = ox + (int) (e.getRawX() - sx); lp.y = oy + (int) (e.getRawY() - sy);
                        wm.updateViewLayout(panel, lp); return true;
                    case MotionEvent.ACTION_UP: prefs.edit().putInt("px", lp.x).putInt("py", lp.y).apply(); return true;
                }
                return false;
            }
        });
        wm.addView(panel, lp);
    }

    private Button btn(Context c, String text, View.OnClickListener l) {
        Button b = new Button(c);
        b.setText(text);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        return b;
    }

    private boolean catFirst() { return prefs.getBoolean("catFirst", false); }
    private void updateCycleLabel() { cycleBtn.setText(catFirst() ? "Tap order: Cat → X" : "Tap order: X → Cat"); }
    private void say(String s) { status.setText("☰ " + s); }

    // ---------------- actions ----------------

    private void solve() {
        capture(b -> {
            int[] s = b.solve();
            if (s == null) say("No solution – a placed cat is wrong?");
            else { marks.show(b, s); say("Solved " + b.n + "×" + b.n); }
            busy = false;
        });
    }

    private void placeCat() {
        capture(b -> {
            int[] s = b.solve();
            if (s == null) { say("No solution – a placed cat is wrong?"); busy = false; return; }
            marks.show(b, s);
            for (int r = 0; r < b.n; r++) {
                int i = r * b.n + s[r];
                if (b.state[i] != Board.CAT) {
                    say("Cat → row " + (r + 1) + ", col " + (s[r] + 1));
                    queueCell(b, i, Board.CAT);
                    runTaps();
                    return;
                }
            }
            say("All cats placed 🎉");
            busy = false;
        });
    }

    /** X out row, column, neighbours and rest-of-colour for every cat on the board. */
    private void markX() {
        capture(b -> {
            Set<Integer> t = new LinkedHashSet<>();
            for (int i = 0; i < b.n * b.n; i++) if (b.state[i] == Board.CAT) t.addAll(b.xTargets(i / b.n, i % b.n));
            if (t.isEmpty()) { say("Nothing to mark"); busy = false; return; }
            say("Marking " + t.size() + " cells…");
            for (int i : t) queueCell(b, i, Board.X);
            runTaps();
        });
    }

    // ---------------- screen ----------------

    private void capture(Consumer<Board> next) {
        if (busy) return;
        busy = true;
        panel.setVisibility(View.INVISIBLE);
        marks.setVisibility(View.INVISIBLE);
        h.postDelayed(() -> takeScreenshot(Display.DEFAULT_DISPLAY, getMainExecutor(), new TakeScreenshotCallback() {
            @Override public void onSuccess(ScreenshotResult res) {
                Bitmap hw = Bitmap.wrapHardwareBuffer(res.getHardwareBuffer(), res.getColorSpace());
                Bitmap bm = hw.copy(Bitmap.Config.ARGB_8888, false);
                res.getHardwareBuffer().close();
                unhide();
                int w = bm.getWidth(), hh = bm.getHeight();
                int[] px = new int[w * hh];
                bm.getPixels(px, 0, w, 0, 0, w, hh);
                bm.recycle();
                Board b;
                try { b = Board.parse(px, w, hh); }
                catch (Exception e) { say("Can't read board: " + e.getMessage()); busy = false; return; }
                next.accept(b);
            }
            @Override public void onFailure(int code) { unhide(); say("Screenshot failed (" + code + ")"); busy = false; }
        }), 150);
    }

    private void unhide() { panel.setVisibility(View.VISIBLE); marks.setVisibility(View.VISIBLE); }

    // ---------------- tapping ----------------

    /** Tap cycle is EMPTY → X → CAT → EMPTY (or EMPTY → CAT → X when toggled). */
    private void queueCell(Board b, int i, int target) {
        int[] pos = catFirst() ? new int[]{0, 2, 1} : new int[]{0, 1, 2}; // index by state
        int n = (pos[target] - pos[b.state[i]] + 3) % 3;
        int x = b.cx(i % b.n), y = b.cy(i / b.n);
        for (int k = 0; k < n; k++) taps.add(new int[]{x, y, k < n - 1 ? SAME_CELL_MS : GAP_MS});
    }

    private void runTaps() {
        int[] t = taps.poll();
        if (t == null) { busy = false; return; }
        Path path = new Path();
        path.moveTo(t[0], t[1]);
        GestureDescription g = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, 40)).build();
        boolean ok = dispatchGesture(g, new GestureResultCallback() {
            @Override public void onCompleted(GestureDescription d) { h.postDelayed(SolverService.this::runTaps, t[2]); }
            @Override public void onCancelled(GestureDescription d) { h.postDelayed(SolverService.this::runTaps, t[2]); }
        }, null);
        if (!ok) { taps.clear(); say("Tap failed"); busy = false; }
    }

    // ---------------- solution markers ----------------

    static class Marks extends View {
        private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Board b;
        private int[] sol;
        private final int[] loc = new int[2];

        Marks(Context c) {
            super(c);
            ring.setStyle(Paint.Style.STROKE);
            ring.setColor(0xFFFF00C8);
        }

        void show(Board b, int[] sol) { this.b = b; this.sol = sol; invalidate(); }

        @Override protected void onDraw(Canvas cv) {
            if (b == null) return;
            getLocationOnScreen(loc);
            for (int r = 0; r < b.n; r++) {
                if (b.state[r * b.n + sol[r]] == Board.CAT) continue; // already placed
                float rad = (b.colR[sol[r]] - b.colL[sol[r]]) * 0.3f;
                ring.setStrokeWidth(rad / 4);
                cv.drawCircle(b.cx(sol[r]) - loc[0], b.cy(r) - loc[1], rad, ring);
            }
        }
    }
}
