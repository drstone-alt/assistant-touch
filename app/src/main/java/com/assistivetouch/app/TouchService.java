package com.assistivetouch.app;

import android.accessibilityservice.AccessibilityService;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

public class TouchService extends AccessibilityService {
    private WindowManager wm;
    private Ball ball;
    private WindowManager.LayoutParams lp;
    private final Handler h = new Handler(Looper.getMainLooper());

    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            String a = i.getAction();
            if (Intent.ACTION_SCREEN_OFF.equals(a)) {
                if (ball != null) ball.setVisibility(View.INVISIBLE);
            } else if (Intent.ACTION_USER_PRESENT.equals(a)) {
                if (ball != null) ball.setVisibility(View.VISIBLE);
            } else if (Intent.ACTION_SCREEN_ON.equals(a)) {
                h.postDelayed(() -> updateVisibility(), 300);
            }
        }
    };

    private void updateVisibility() {
        if (ball == null) return;
        KeyguardManager km = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        ball.setVisibility(km.isKeyguardLocked() ? View.INVISIBLE : View.VISIBLE);
    }

    @Override protected void onServiceConnected() {
        if (ball != null) return;
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        ball = new Ball(this);
        int size = (int) (56 * getResources().getDisplayMetrics().density);
        lp = new WindowManager.LayoutParams(size, size,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = 0;
        lp.y = 500;
        wm.addView(ball, lp);

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_SCREEN_OFF);
        f.addAction(Intent.ACTION_SCREEN_ON);
        f.addAction(Intent.ACTION_USER_PRESENT);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(screenReceiver, f, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(screenReceiver, f);
        }
        updateVisibility();
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent e) { }
    @Override public void onInterrupt() { }

    @Override public void onDestroy() {
        try { unregisterReceiver(screenReceiver); } catch (Exception ignored) { }
        if (ball != null && wm != null) { wm.removeView(ball); ball = null; }
        super.onDestroy();
    }

    private class Ball extends View {
        private final Paint outer = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint inner = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int slop;
        private float sx, sy;
        private int startX, startY;
        private boolean moved, fired, second;
        private long lastUp = 0;

        private final Runnable homeR = () -> performGlobalAction(GLOBAL_ACTION_HOME);
        private final Runnable longR = () -> {
            fired = true;
            performGlobalAction(second ? GLOBAL_ACTION_POWER_DIALOG : GLOBAL_ACTION_RECENTS);
        };

        Ball(Context c) {
            super(c);
            slop = ViewConfiguration.get(c).getScaledTouchSlop();
            outer.setColor(0x80404040);
            inner.setColor(0xB3FFFFFF);
        }

        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), cx = w / 2f, cy = getHeight() / 2f;
            c.drawCircle(cx, cy, w / 2f, outer);
            c.drawCircle(cx, cy, w * 0.30f, inner);
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    moved = false; fired = false;
                    second = (e.getEventTime() - lastUp) < 300;
                    h.removeCallbacks(homeR);
                    sx = e.getRawX(); sy = e.getRawY();
                    startX = lp.x; startY = lp.y;
                    h.postDelayed(longR, 600);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float dx = e.getRawX() - sx, dy = e.getRawY() - sy;
                    if (!moved && Math.hypot(dx, dy) > slop) { moved = true; h.removeCallbacks(longR); }
                    if (moved) {
                        lp.x = startX + (int) dx;
                        lp.y = startY + (int) dy;
                        wm.updateViewLayout(this, lp);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    h.removeCallbacks(longR);
                    if (!moved && !fired) {
                        if (second) {
                            performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
                            lastUp = 0;
                        } else {
                            lastUp = e.getEventTime();
                            h.postDelayed(homeR, 300);
                        }
                    } else {
                        lastUp = 0;
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    h.removeCallbacks(longR);
                    return true;
            }
            return true;
        }
    }
}
