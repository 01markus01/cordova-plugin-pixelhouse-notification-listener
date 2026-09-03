package com.pixelhouse.notificationlistener;

import android.content.ClipData;
import android.content.ClipboardManager;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
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


            // =====================================================
            // Complete history JSON
            // =====================================================

            case "getNotificationHistoryJson":

                callbackContext.success(
                        getNotificationHistoryJson()
                );

                return true;


            // =====================================================
            // Delete one history entry
            // =====================================================

            case "deleteNotificationByIndex":

                int indexToDelete =
                        args.optInt(
                                0,
                                -1
                        );

                deleteNotificationByIndex(
                        indexToDelete,
                        callbackContext
                );

                return true;


            // =====================================================
            // Clear complete history
            // =====================================================

            case "clearNotificationHistory":

                clearNotificationHistory(
                        callbackContext
                );

                return true;


            // =====================================================
            // Package-specific notification history (v2)
            // =====================================================

            case "getNotificationCountForPackage":

                callbackContext.success(
                        getNotificationCount(
                                args.optString(0, "")
                        )
                );

                return true;


            case "getNotificationHistoryJsonForPackage":

                callbackContext.success(
                        getNotificationHistoryJson(
                                args.optString(0, "")
                        )
                );

                return true;


            case "getNotificationPackageForPackage":

                callbackContext.success(
                        getNotificationPackage(
                                args.optString(0, ""),
                                args.optInt(1, 0)
                        )
                );

                return true;


            case "getNotificationTitleForPackage":

                callbackContext.success(
                        getNotificationTitle(
                                args.optString(0, ""),
                                args.optInt(1, 0)
                        )
                );

                return true;


            case "getNotificationTextForPackage":

                callbackContext.success(
                        getNotificationText(
                                args.optString(0, ""),
                                args.optInt(1, 0)
                        )
                );

                return true;


            case "getNotificationTimestampForPackage":

                callbackContext.success(
                        getNotificationTimestamp(
                                args.optString(0, ""),
                                args.optInt(1, 0)
                        )
                );

                return true;


            case "getNotificationIdForPackage":

                callbackContext.success(
                        getNotificationId(
                                args.optString(0, ""),
                                args.optInt(1, 0)
                        )
                );

                return true;


            case "deleteNotificationByIndexForPackage":

                deleteNotificationByIndex(
                        args.optString(0, ""),
                        args.optInt(1, -1),
                        callbackContext
                );

                return true;


            case "clearNotificationHistoryForPackage":

                clearNotificationHistory(
                        args.optString(0, ""),
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


            case "copyDebugReportToClipboard":

                copyDebugReportToClipboard(
                        callbackContext
                );

                return true;


            default:

                return false;
        }
    }


    // =============================================================
    // Copy debug report to Android clipboard
    // =============================================================

    private void copyDebugReportToClipboard(
            CallbackContext callbackContext
    ) {

        try {

            String report =
                    getPreferences().getString(
                            PixelHouseNotificationService.KEY_DEBUG_REPORT,
                            ""
                    );


            if (report.isEmpty()) {

                callbackContext.error(
                        "No debug report is available yet."
                );

                return;
            }


            Context context =
                    cordova.getActivity();


            ClipboardManager clipboardManager =
                    (ClipboardManager) context.getSystemService(
                            Context.CLIPBOARD_SERVICE
                    );


            if (clipboardManager == null) {

                callbackContext.error(
                        "Android clipboard service is not available."
                );

                return;
            }


            ClipData clipData =
                    ClipData.newPlainText(
                            "Message Wizard image diagnostic",
                            report
                    );


            clipboardManager.setPrimaryClip(
                    clipData
            );


            callbackContext.success(
                    report.length()
            );


        } catch (Exception e) {

            callbackContext.error(
                    "Could not copy debug report: "
                            + e.getMessage()
            );
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
    // Notification history
    // =============================================================

    private JSONArray getNotificationHistory() {

        return getNotificationHistory(
                ""
        );
    }


    private JSONArray getNotificationHistory(
            String packageName
    ) {

        SharedPreferences prefs =
                getPreferences();

        PixelHouseNotificationService
                .migrateLegacyHistoryIfNeeded(
                        prefs
                );

        String normalizedPackage =
                packageName == null
                        ? ""
                        : packageName.trim();

        if (!normalizedPackage.isEmpty()) {

            try {

                return new JSONArray(
                        prefs.getString(
                                PixelHouseNotificationService
                                        .getHistoryPreferenceKey(
                                                normalizedPackage
                                        ),
                                "[]"
                        )
                );

            } catch (Exception e) {

                return new JSONArray();
            }
        }

        // Legacy compatibility: no package supplied means a combined
        // history of all per-package lists, sorted oldest -> newest.
        ArrayList<JSONObject> entries =
                new ArrayList<>();

        try {

            Map<String, ?> all =
                    prefs.getAll();

            for (
                    Map.Entry<String, ?> item
                            : all.entrySet()
            ) {

                String key =
                        item.getKey();

                if (key == null
                        || !key.startsWith(
                                PixelHouseNotificationService
                                        .KEY_NOTIFICATION_HISTORY_PREFIX
                        )) {

                    continue;
                }

                Object value =
                        item.getValue();

                if (!(value instanceof String)) {
                    continue;
                }

                JSONArray packageHistory =
                        new JSONArray(
                                (String) value
                        );

                for (
                        int i = 0;
                        i < packageHistory.length();
                        i++
                ) {

                    JSONObject entry =
                            packageHistory.optJSONObject(i);

                    if (entry != null) {
                        entries.add(entry);
                    }
                }
            }

            Collections.sort(
                    entries,
                    new Comparator<JSONObject>() {
                        @Override
                        public int compare(
                                JSONObject a,
                                JSONObject b
                        ) {

                            long ta =
                                    a.optLong(
                                            "timestamp",
                                            0
                                    );

                            long tb =
                                    b.optLong(
                                            "timestamp",
                                            0
                                    );

                            return Long.compare(
                                    ta,
                                    tb
                            );
                        }
                    }
            );

            JSONArray combined =
                    new JSONArray();

            for (JSONObject entry : entries) {
                combined.put(entry);
            }

            return combined;

        } catch (Exception e) {

            return new JSONArray();
        }
    }


    // =============================================================
    // Complete history JSON
    // =============================================================

    private String getNotificationHistoryJson() {

        return getNotificationHistory()
                .toString();
    }


    private String getNotificationHistoryJson(
            String packageName
    ) {

        return getNotificationHistory(
                packageName
        ).toString();
    }


    // =============================================================
    // Convert public index to stored index
    //
    // Public:
    //
    // 0 = newest
    // 1 = second newest
    //
    // Stored:
    //
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

        return getNotificationObject(
                "",
                index
        );
    }


    private JSONObject getNotificationObject(
            String packageName,
            int index
    ) {

        try {

            JSONArray history =
                    getNotificationHistory(
                            packageName
                    );


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

        return getNotificationHistory()
                .length();
    }


    private int getNotificationCount(
            String packageName
    ) {

        return getNotificationHistory(
                packageName
        ).length();
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
    // Package-specific indexed getters
    // =============================================================

    private String getNotificationPackage(
            String packageName,
            int index
    ) {

        JSONObject notification =
                getNotificationObject(
                        packageName,
                        index
                );

        return notification == null
                ? ""
                : notification.optString(
                        "package",
                        ""
                );
    }


    private String getNotificationTitle(
            String packageName,
            int index
    ) {

        JSONObject notification =
                getNotificationObject(
                        packageName,
                        index
                );

        return notification == null
                ? ""
                : notification.optString(
                        "title",
                        ""
                );
    }


    private String getNotificationText(
            String packageName,
            int index
    ) {

        JSONObject notification =
                getNotificationObject(
                        packageName,
                        index
                );

        return notification == null
                ? ""
                : notification.optString(
                        "text",
                        ""
                );
    }


    private String getNotificationTimestamp(
            String packageName,
            int index
    ) {

        JSONObject notification =
                getNotificationObject(
                        packageName,
                        index
                );

        if (notification == null) {
            return "0";
        }

        return String.valueOf(
                notification.optLong(
                        "timestamp",
                        0
                )
        );
    }


    private String getNotificationId(
            String packageName,
            int index
    ) {

        JSONObject notification =
                getNotificationObject(
                        packageName,
                        index
                );

        return notification == null
                ? ""
                : notification.optString(
                        "id",
                        ""
                );
    }


    // =============================================================
    // Delete one history item by public index
    // =============================================================

    private void deleteNotificationByIndex(
            int index,
            CallbackContext callbackContext
    ) {

        // Legacy compatibility: resolve the selected item from the
        // combined history and then delete it from its package list.
        try {

            JSONObject selected =
                    getNotificationObject(
                            index
                    );

            if (selected == null) {

                callbackContext.error(
                        "History index out of range."
                );

                return;
            }

            String packageName =
                    selected.optString(
                            "package",
                            ""
                    );

            String id =
                    selected.optString(
                            "id",
                            ""
                    );

            JSONArray packageHistory =
                    getNotificationHistory(
                            packageName
                    );

            int packagePublicIndex = -1;

            for (
                    int storedIndex = 0;
                    storedIndex < packageHistory.length();
                    storedIndex++
            ) {

                JSONObject item =
                        packageHistory.optJSONObject(
                                storedIndex
                        );

                if (item != null
                        && id.equals(
                                item.optString(
                                        "id",
                                        ""
                                )
                        )) {

                    packagePublicIndex =
                            packageHistory.length()
                                    - 1
                                    - storedIndex;

                    break;
                }
            }

            if (packagePublicIndex < 0) {

                callbackContext.error(
                        "Could not resolve history item in package list."
                );

                return;
            }

            deleteNotificationByIndex(
                    packageName,
                    packagePublicIndex,
                    callbackContext
            );

        } catch (Exception e) {

            callbackContext.error(
                    "Could not delete history item: "
                            + e.getMessage()
            );
        }
    }


    private void deleteNotificationByIndex(
            String packageName,
            int index,
            CallbackContext callbackContext
    ) {

        String normalizedPackage =
                packageName == null
                        ? ""
                        : packageName.trim();

        if (normalizedPackage.isEmpty()) {

            callbackContext.error(
                    "Package ID is required for deleting a history item."
            );

            return;
        }

        try {

            JSONArray history =
                    getNotificationHistory(
                            normalizedPackage
                    );

            int storedIndex =
                    getStoredIndex(
                            history,
                            index
                    );

            if (storedIndex < 0) {

                callbackContext.error(
                        "History index out of range."
                );

                return;
            }

            JSONArray updatedHistory =
                    new JSONArray();

            for (
                    int i = 0;
                    i < history.length();
                    i++
            ) {

                if (i == storedIndex) {
                    continue;
                }

                updatedHistory.put(
                        history.getJSONObject(i)
                );
            }

            getPreferences()
                    .edit()
                    .putString(
                            PixelHouseNotificationService
                                    .getHistoryPreferenceKey(
                                            normalizedPackage
                                    ),
                            updatedHistory.toString()
                    )
                    .apply();

            updateLastNotificationFromAllHistories();

            callbackContext.success();

        } catch (Exception e) {

            callbackContext.error(
                    "Could not delete history item: "
                            + e.getMessage()
            );
        }
    }


    // =============================================================
    // Update last-notification cache after deleting history item
    // =============================================================

    private void updateLastNotificationFromHistory(
            JSONArray history
    ) {

        SharedPreferences.Editor editor =
                getPreferences().edit();


        if (history == null
                || history.length() == 0) {

            editor
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

                    .apply();


            return;
        }


        try {

            JSONObject newest =
                    history.getJSONObject(
                            history.length() - 1
                    );


            editor
                    .putString(
                            PixelHouseNotificationService.KEY_LAST_PACKAGE,
                            newest.optString(
                                    "package",
                                    ""
                            )
                    )

                    .putString(
                            PixelHouseNotificationService.KEY_LAST_TITLE,
                            newest.optString(
                                    "title",
                                    ""
                            )
                    )

                    .putString(
                            PixelHouseNotificationService.KEY_LAST_TEXT,
                            newest.optString(
                                    "text",
                                    ""
                            )
                    )

                    .putLong(
                            PixelHouseNotificationService.KEY_LAST_TIMESTAMP,
                            newest.optLong(
                                    "timestamp",
                                    0
                            )
                    )

                    .apply();


        } catch (Exception e) {

            editor
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

                    .apply();
        }
    }


    private void updateLastNotificationFromAllHistories() {

        updateLastNotificationFromHistory(
                getNotificationHistory()
        );
    }


    // =============================================================
    // Clear notification history
    // =============================================================

    private void clearNotificationHistory(
            CallbackContext callbackContext
    ) {

        // Legacy compatibility: clear all package histories.
        SharedPreferences prefs =
                getPreferences();

        PixelHouseNotificationService
                .migrateLegacyHistoryIfNeeded(
                        prefs
                );

        SharedPreferences.Editor editor =
                prefs.edit();

        for (String key : prefs.getAll().keySet()) {

            if (key != null
                    && key.startsWith(
                            PixelHouseNotificationService
                                    .KEY_NOTIFICATION_HISTORY_PREFIX
                    )) {

                editor.remove(key);
            }
        }

        editor
                .remove(PixelHouseNotificationService.KEY_NOTIFICATION_HISTORY)
                .remove(PixelHouseNotificationService.KEY_LAST_PACKAGE)
                .remove(PixelHouseNotificationService.KEY_LAST_TITLE)
                .remove(PixelHouseNotificationService.KEY_LAST_TEXT)
                .remove(PixelHouseNotificationService.KEY_LAST_TIMESTAMP)
                .remove(PixelHouseNotificationService.KEY_TEXT_LINE_SNAPSHOTS)
                .remove(PixelHouseNotificationService.KEY_DEBUG_REPORT)
                .apply();

        callbackContext.success();
    }


    private void clearNotificationHistory(
            String packageName,
            CallbackContext callbackContext
    ) {

        String normalizedPackage =
                packageName == null
                        ? ""
                        : packageName.trim();

        if (normalizedPackage.isEmpty()) {

            callbackContext.error(
                    "Package ID is required for clearing a package history."
            );

            return;
        }

        SharedPreferences prefs =
                getPreferences();

        PixelHouseNotificationService
                .migrateLegacyHistoryIfNeeded(
                        prefs
                );

        prefs.edit()
                .remove(
                        PixelHouseNotificationService
                                .getHistoryPreferenceKey(
                                        normalizedPackage
                                )
                )
                .apply();

        updateLastNotificationFromAllHistories();

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
