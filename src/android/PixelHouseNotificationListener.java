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
import org.json.JSONObject;

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

                openNotificationAccessSettings(
                        callbackContext
                );

                return true;


            case "hasNotificationAccess":

                callbackContext.success(
                        hasNotificationAccess()
                                ? 1
                                : 0
                );

                return true;


            // =====================================================
            // Monitored apps / whitelist
            // =====================================================

            case "addMonitoredApp":

                String packageToAdd =
                        args.optString(
                                0,
                                ""
                        );

                addMonitoredApp(
                        packageToAdd,
                        callbackContext
                );

                return true;


            case "removeMonitoredApp":

                String packageToRemove =
                        args.optString(
                                0,
                                ""
                        );

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
                        args.optString(
                                0,
                                ""
                        );

                callbackContext.success(
                        isAppMonitored(
                                packageToCheck
                        )
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
                        String.valueOf(
                                timestamp
                        )
                );

                return true;


            // =====================================================
            // Notification history
            // =====================================================

            case "getNotificationCount":

                callbackContext.success(
                        getNotificationCount()
                );

                return true;


            case "getNotificationPackage":

                callbackContext.success(
                        getNotificationPackage(
                                args.optInt(
                                        0,
                                        0
                                )
                        )
                );

                return true;


            case "getNotificationTitle":

                callbackContext.success(
                        getNotificationTitle(
                                args.optInt(
                                        0,
                                        0
                                )
                        )
                );

                return true;


            case "getNotificationText":

                callbackContext.success(
                        getNotificationText(
                                args.optInt(
                                        0,
                                        0
                                )
                        )
                );

                return true;


            case "getNotificationTimestamp":

                callbackContext.success(
                        getNotificationTimestamp(
                                args.optInt(
                                        0,
                                        0
                                )
                        )
                );

                return true;


            case "getNotificationId":

                callbackContext.success(
                        getNotificationId(
                                args.optInt(
                                        0,
                                        0
                                )
                        )
                );

                return true;


            case "clearNotificationHistory":

                clearNotificationHistory(
                        callbackContext
                );

                return true;


            // =====================================================
            // DEBUG
            // =====================================================

            case "getDebugReport":

                callbackContext.success(
                        getPreferences().getString(
                                PixelHouseNotificationService.KEY_DEBUG_REPORT,
                                ""
                        )
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
                new HashSet<>(
                        current
                );


        updated.add(
                packageName
        );


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
                new HashSet<>(
                        current
                );


        updated.remove(
                packageName
        );


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
    // Notification history helper
    // =============================================================

    private JSONArray getNotificationHistory() {

        try {

            String history =
                    getPreferences().getString(
                            PixelHouseNotificationService.KEY_NOTIFICATION_HISTORY,
                            "[]"
                    );


            return new JSONArray(
                    history
            );


        } catch (Exception e) {

            return new JSONArray();
        }
    }


    // =============================================================
    // Convert public index to stored history index
    //
    // Public:
    // 0 = newest notification
    // 1 = second newest
    // 2 = third newest
    //
    // Stored JSON:
    // oldest -> newest
    // =============================================================

    private int getStoredIndex(
            JSONArray history,
            int index
    ) {

        if (index < 0) {
            return -1;
        }


        int storedIndex =
                history.length()
                        - 1
                        - index;


        if (storedIndex < 0
                || storedIndex >= history.length()) {

            return -1;
        }


        return storedIndex;
    }


    // =============================================================
    // Get notification object
    // =============================================================

    private JSONObject getNotificationObject(
            int index
    ) {

        try {

            JSONArray history =
                    getNotificationHistory();


            int storedIndex =
                    getStoredIndex(
                            history,
                            index
                    );


            if (storedIndex < 0) {

                return null;
            }


            return history.getJSONObject(
                    storedIndex
            );


        } catch (Exception e) {

            return null;
        }
    }


    // =============================================================
    // Notification count
    // =============================================================

    private int getNotificationCount() {

        JSONArray history =
                getNotificationHistory();


        return history.length();
    }


    // =============================================================
    // Notification package
    // =============================================================

    private String getNotificationPackage(
            int index
    ) {

        JSONObject notification =
                getNotificationObject(
                        index
                );


        if (notification == null) {

            return "";
        }


        return notification.optString(
                "package",
                ""
        );
    }


    // =============================================================
    // Notification title
    // =============================================================

    private String getNotificationTitle(
            int index
    ) {

        JSONObject notification =
                getNotificationObject(
                        index
                );


        if (notification == null) {

            return "";
        }


        return notification.optString(
                "title",
                ""
        );
    }


    // =============================================================
    // Notification text
    // =============================================================

    private String getNotificationText(
            int index
    ) {

        JSONObject notification =
                getNotificationObject(
                        index
                );


        if (notification == null) {

            return "";
        }


        return notification.optString(
                "text",
                ""
        );
    }


    // =============================================================
    // Notification timestamp
    // =============================================================

    private String getNotificationTimestamp(
            int index
    ) {

        JSONObject notification =
                getNotificationObject(
                        index
                );


        if (notification == null) {

            return "0";
        }


        long timestamp =
                notification.optLong(
                        "timestamp",
                        0
                );


        return String.valueOf(
                timestamp
        );
    }


    // =============================================================
    // Notification ID
    // =============================================================

    private String getNotificationId(
            int index
    ) {

        JSONObject notification =
                getNotificationObject(
                        index
                );


        if (notification == null) {

            return "";
        }


        return notification.optString(
                "id",
                ""
        );
    }


    // =============================================================
    // Clear notification history
    //
    // Important:
    //
    // Also clears the EXTRA_TEXT_LINES snapshots. This makes the
    // CLEAR button a true clean reset for the next WhatsApp test.
    // =============================================================

    private void clearNotificationHistory(
            CallbackContext callbackContext
    ) {

        getPreferences()
                .edit()

                .remove(
                        PixelHouseNotificationService.KEY_NOTIFICATION_HISTORY
                )

                .remove(
                        PixelHouseNotificationService.KEY_LAST_PACKAGE
                )

                .remove(
                        PixelHouseNotificationService.KEY_LAST_TITLE
                )

                .remove(
                        PixelHouseNotificationService.KEY_LAST_TEXT
                )

                .remove(
                        PixelHouseNotificationService.KEY_LAST_TIMESTAMP
                )

                .remove(
                        PixelHouseNotificationService.KEY_TEXT_LINE_SNAPSHOTS
                )

                .remove(
                        PixelHouseNotificationService.KEY_DEBUG_REPORT
                )

                .apply();


        callbackContext.success();
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
                    .startActivity(
                            intent
                    );


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


        if (TextUtils.isEmpty(
                enabledListeners
        )) {

            return false;
        }


        String packageName =
                context.getPackageName();


        String[] listeners =
                enabledListeners.split(
                        ":"
                );


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