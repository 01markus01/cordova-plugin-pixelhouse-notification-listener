package com.pixelhouse.notificationlistener;

import android.app.Notification;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

public class PixelHouseNotificationService extends NotificationListenerService {

    private static final String TAG = "PixelHouseNotif";

    // Last captured notification
    private static String lastPackage = "";
    private static String lastTitle = "";
    private static String lastText = "";
    private static long lastTimestamp = 0;

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();

        Log.d(TAG, "Notification Listener connected");
    }

    @Override
    public void onListenerDisconnected() {
        super.onListenerDisconnected();

        Log.d(TAG, "Notification Listener disconnected");
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        super.onNotificationPosted(sbn);

        if (sbn == null) {
            return;
        }

        Notification notification = sbn.getNotification();

        if (notification == null) {
            return;
        }

        Bundle extras = notification.extras;

        String packageName = sbn.getPackageName();
        String title = "";
        String text = "";

        if (extras != null) {

            CharSequence titleSequence =
                    extras.getCharSequence(Notification.EXTRA_TITLE);

            CharSequence textSequence =
                    extras.getCharSequence(Notification.EXTRA_TEXT);

            if (titleSequence != null) {
                title = titleSequence.toString();
            }

            if (textSequence != null) {
                text = textSequence.toString();
            }
        }

        lastPackage = packageName != null ? packageName : "";
        lastTitle = title;
        lastText = text;
        lastTimestamp = System.currentTimeMillis();

        Log.d(TAG, "--------------------------------");
        Log.d(TAG, "NEW NOTIFICATION");
        Log.d(TAG, "Package: " + lastPackage);
        Log.d(TAG, "Title: " + lastTitle);
        Log.d(TAG, "Text: " + lastText);
        Log.d(TAG, "Timestamp: " + lastTimestamp);
        Log.d(TAG, "--------------------------------");
    }

    // =========================================================
    // Getters for Cordova bridge
    // =========================================================

    public static String getLastPackage() {
        return lastPackage;
    }

    public static String getLastTitle() {
        return lastTitle;
    }

    public static String getLastText() {
        return lastText;
    }

    public static long getLastTimestamp() {
        return lastTimestamp;
    }
}