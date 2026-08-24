package com.pixelhouse.notificationlistener;

import android.app.Notification;
import android.content.ComponentName;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import java.util.Collections;
import java.util.Set;

public class PixelHouseNotificationService extends NotificationListenerService {

    private static final String TAG = "PixelHouseNotif";

    public static final String PREFS_NAME =
            "PixelHouseNotificationListenerPrefs";

    public static final String KEY_MONITORED_PACKAGES =
            "monitored_packages";

    public static final String KEY_LAST_PACKAGE =
            "last_package";

    public static final String KEY_LAST_TITLE =
            "last_title";

    public static final String KEY_LAST_TEXT =
            "last_text";

    public static final String KEY_LAST_TIMESTAMP =
            "last_timestamp";


    @Override
    public void onListenerConnected() {
        super.onListenerConnected();

        Log.d(TAG, "Notification Listener connected");
    }


    @Override
    public void onListenerDisconnected() {
        super.onListenerDisconnected();

        Log.d(TAG, "Notification Listener disconnected");

        try {

            ComponentName componentName =
                    new ComponentName(
                            this,
                            PixelHouseNotificationService.class
                    );

            NotificationListenerService.requestRebind(
                    componentName
            );

            Log.d(TAG, "Notification Listener rebind requested");

        } catch (Exception e) {

            Log.e(
                    TAG,
                    "Could not request listener rebind",
                    e
            );
        }
    }


    @Override
    public void onNotificationPosted(
            StatusBarNotification sbn
    ) {

        super.onNotificationPosted(sbn);

        if (sbn == null) {
            return;
        }


        String packageName =
                sbn.getPackageName();

        if (packageName == null
                || packageName.isEmpty()) {

            return;
        }


        // =========================================================
        // Check whitelist
        // =========================================================

        if (!isPackageMonitored(packageName)) {

            Log.d(
                    TAG,
                    "Notification ignored from: "
                            + packageName
            );

            return;
        }


        Notification notification =
                sbn.getNotification();

        if (notification == null) {
            return;
        }


        Bundle extras =
                notification.extras;

        String title = "";
        String text = "";


        if (extras != null) {

            CharSequence titleSequence =
                    extras.getCharSequence(
                            Notification.EXTRA_TITLE
                    );

            CharSequence textSequence =
                    extras.getCharSequence(
                            Notification.EXTRA_TEXT
                    );


            if (titleSequence != null) {

                title =
                        titleSequence.toString();
            }


            if (textSequence != null) {

                text =
                        textSequence.toString();
            }
        }


        // Ignore completely empty notifications
        if (title.isEmpty()
                && text.isEmpty()) {

            Log.d(
                    TAG,
                    "Empty notification ignored from: "
                            + packageName
            );

            return;
        }


        long timestamp =
                System.currentTimeMillis();


        // =========================================================
        // Save notification permanently
        // =========================================================

        SharedPreferences prefs =
                getSharedPreferences(
                        PREFS_NAME,
                        MODE_PRIVATE
                );


        prefs.edit()

                .putString(
                        KEY_LAST_PACKAGE,
                        packageName
                )

                .putString(
                        KEY_LAST_TITLE,
                        title
                )

                .putString(
                        KEY_LAST_TEXT,
                        text
                )

                .putLong(
                        KEY_LAST_TIMESTAMP,
                        timestamp
                )

                .apply();


        Log.d(TAG, "--------------------------------");
        Log.d(TAG, "SAVED NOTIFICATION");
        Log.d(TAG, "Package: " + packageName);
        Log.d(TAG, "Title: " + title);
        Log.d(TAG, "Text: " + text);
        Log.d(TAG, "Timestamp: " + timestamp);
        Log.d(TAG, "--------------------------------");
    }


    // =============================================================
    // Check whether package is on whitelist
    // =============================================================

    private boolean isPackageMonitored(
            String packageName
    ) {

        SharedPreferences prefs =
                getSharedPreferences(
                        PREFS_NAME,
                        MODE_PRIVATE
                );


        Set<String> monitoredPackages =
                prefs.getStringSet(
                        KEY_MONITORED_PACKAGES,
                        Collections.<String>emptySet()
                );


        return monitoredPackages.contains(
                packageName
        );
    }
}