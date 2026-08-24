package com.pixelhouse.notificationlistener;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.text.TextUtils;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.HashSet;
import java.util.Set;

public class PixelHouseNotificationListener extends CordovaPlugin {

    @Override
    public boolean execute(
            String action,
            JSONArray args,
            CallbackContext callbackContext
    ) throws JSONException {

        switch (action) {

            // =====================================================
            // Notification access
            // =====================================================

            case "openNotificationAccessSettings":
                openNotificationAccessSettings(callbackContext);
                return true;

            case "hasNotificationAccess":
                callbackContext.success(
                        hasNotificationAccess() ? 1 : 0
                );
                return true;


            // =====================================================
            // Monitored apps / whitelist
            // =====================================================

            case "addMonitoredApp":

                String packageToAdd =
                        args.optString(0, "");

                addMonitoredApp(
                        packageToAdd,
                        callbackContext
                );

                return true;


            case "removeMonitoredApp":

                String packageToRemove =
                        args.optString(0, "");

                removeMonitoredApp(
                        packageToRemove,
                        callbackContext
                );

                return true;


            case "clearMonitoredApps":

                clearMonitoredApps(
                        callbackContext
                );

                return true;


            case "isAppMonitored":

                String packageToCheck =
                        args.optString(0, "");

                callbackContext.success(
                        isAppMonitored(packageToCheck)
                                ? 1
                                : 0
                );

                return true;


            // =====================================================
            // Last captured notification
            // =====================================================

            case "getLastPackage":

                callbackContext.success(
                        getPreferences().getString(
                                PixelHouseNotificationService.KEY_LAST_PACKAGE,
                                ""
                        )
                );

                return true;


            case "getLastTitle":

                callbackContext.success(
                        getPreferences().getString(
                                PixelHouseNotificationService.KEY_LAST_TITLE,
                                ""
                        )
                );

                return true;


            case "getLastText":

                callbackContext.success(
                        getPreferences().getString(
                                PixelHouseNotificationService.KEY_LAST_TEXT,
                                ""
                        )
                );

                return true;


            case "getLastTimestamp":

                long timestamp =
                        getPreferences().getLong(
                                PixelHouseNotificationService.KEY_LAST_TIMESTAMP,
                                0
                        );

                callbackContext.success(
                        String.valueOf(timestamp)
                );

                return true;


            default:
                return false;
        }
    }


    // =============================================================
    // Preferences
    // =============================================================

    private SharedPreferences getPreferences() {

        return cordova
                .getActivity()
                .getSharedPreferences(
                        PixelHouseNotificationService.PREFS_NAME,
                        Context.MODE_PRIVATE
                );
    }


    // =============================================================
    // Add monitored app
    // =============================================================

    private void addMonitoredApp(
            String packageName,
            CallbackContext callbackContext
    ) {

        packageName =
                packageName.trim();

        if (packageName.isEmpty()) {

            callbackContext.error(
                    "Package name must not be empty."
            );

            return;
        }


        Set<String> current =
                getPreferences().getStringSet(
                        PixelHouseNotificationService.KEY_MONITORED_PACKAGES,
                        new HashSet<String>()
                );


        Set<String> updated =
                new HashSet<>(current);


        updated.add(packageName);


        getPreferences()
                .edit()
                .putStringSet(
                        PixelHouseNotificationService.KEY_MONITORED_PACKAGES,
                        updated
                )
                .apply();


        callbackContext.success();
    }


    // =============================================================
    // Remove monitored app
    // =============================================================

    private void removeMonitoredApp(
            String packageName,
            CallbackContext callbackContext
    ) {

        packageName =
                packageName.trim();


        Set<String> current =
                getPreferences().getStringSet(
                        PixelHouseNotificationService.KEY_MONITORED_PACKAGES,
                        new HashSet<String>()
                );


        Set<String> updated =
                new HashSet<>(current);


        updated.remove(packageName);


        getPreferences()
                .edit()
                .putStringSet(
                        PixelHouseNotificationService.KEY_MONITORED_PACKAGES,
                        updated
                )
                .apply();


        callbackContext.success();
    }


    // =============================================================
    // Clear monitored apps
    // =============================================================

    private void clearMonitoredApps(
            CallbackContext callbackContext
    ) {

        getPreferences()
                .edit()
                .remove(
                        PixelHouseNotificationService.KEY_MONITORED_PACKAGES
                )
                .apply();


        callbackContext.success();
    }


    // =============================================================
    // Is app monitored?
    // =============================================================

    private boolean isAppMonitored(
            String packageName
    ) {

        Set<String> packages =
                getPreferences().getStringSet(
                        PixelHouseNotificationService.KEY_MONITORED_PACKAGES,
                        new HashSet<String>()
                );


        return packages.contains(
                packageName.trim()
        );
    }


    // =============================================================
    // Open notification access settings
    // =============================================================

    private void openNotificationAccessSettings(
            CallbackContext callbackContext
    ) {

        try {

            Intent intent =
                    new Intent(
                            Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
                    );

            intent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
            );

            cordova
                    .getActivity()
                    .startActivity(intent);


            callbackContext.success();

        } catch (Exception e) {

            callbackContext.error(
                    "Could not open notification access settings: "
                            + e.getMessage()
            );
        }
    }


    // =============================================================
    // Check notification access
    // =============================================================

    private boolean hasNotificationAccess() {

        Context context =
                cordova.getActivity();


        String enabledListeners =
                Settings.Secure.getString(
                        context.getContentResolver(),
                        "enabled_notification_listeners"
                );


        if (TextUtils.isEmpty(enabledListeners)) {
            return false;
        }


        String packageName =
                context.getPackageName();


        String[] listeners =
                enabledListeners.split(":");


        for (String listener : listeners) {

            ComponentName componentName =
                    ComponentName.unflattenFromString(
                            listener
                    );


            if (componentName != null
                    && packageName.equals(
                            componentName.getPackageName()
                    )) {

                return true;
            }
        }


        return false;
    }
}