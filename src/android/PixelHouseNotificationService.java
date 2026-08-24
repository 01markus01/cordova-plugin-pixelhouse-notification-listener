package com.pixelhouse.notificationlistener;

import android.app.Notification;
import android.app.Person;
import android.content.ComponentName;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class PixelHouseNotificationService
        extends NotificationListenerService {

    private static final String TAG =
            "PixelHouseNotif";


    // =============================================================
    // SharedPreferences
    // =============================================================

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

    public static final String KEY_NOTIFICATION_HISTORY =
            "notification_history";

    public static final String KEY_TEXT_LINE_SNAPSHOTS =
            "text_line_snapshots";

    public static final String KEY_DEBUG_REPORT =
            "debug_report";


    // =============================================================
    // History
    // =============================================================

    public static final int MAX_NOTIFICATION_HISTORY =
            500;


    // =============================================================
    // WhatsApp packages
    // =============================================================

    private static final String PACKAGE_WHATSAPP =
            "com.whatsapp";

    private static final String PACKAGE_WHATSAPP_BUSINESS =
            "com.whatsapp.w4b";


    // =============================================================
    // Listener lifecycle
    // =============================================================

    @Override
    public void onListenerConnected() {

        super.onListenerConnected();

        Log.d(
                TAG,
                "Notification Listener connected"
        );
    }


    @Override
    public void onListenerDisconnected() {

        super.onListenerDisconnected();

        Log.d(
                TAG,
                "Notification Listener disconnected"
        );

        try {

            ComponentName componentName =
                    new ComponentName(
                            this,
                            PixelHouseNotificationService.class
                    );

            NotificationListenerService.requestRebind(
                    componentName
            );

            Log.d(
                    TAG,
                    "Notification Listener rebind requested"
            );

        } catch (Exception e) {

            Log.e(
                    TAG,
                    "Could not request listener rebind",
                    e
            );
        }
    }


    // =============================================================
    // Notification received
    // =============================================================

    @Override
    public void onNotificationPosted(
            StatusBarNotification sbn
    ) {

        super.onNotificationPosted(sbn);


        if (sbn == null) {
            return;
        }


        String packageName =
                safeString(
                        sbn.getPackageName()
                );


        if (packageName.isEmpty()) {
            return;
        }


        // =========================================================
        // Whitelist
        // =========================================================

        if (!isPackageMonitored(
                packageName
        )) {

            return;
        }


        Notification notification =
                sbn.getNotification();


        if (notification == null) {
            return;
        }


        Bundle extras =
                notification.extras;


        if (extras == null) {
            return;
        }


        // =========================================================
        // Debug report
        // =========================================================

        saveDebugReport(
                sbn,
                notification,
                extras,
                packageName
        );


        // =========================================================
        // WhatsApp special handling
        //
        // Our debug test showed that WhatsApp on the test device
        // creates a GROUP SUMMARY notification containing:
        //
        // EXTRA_TITLE:
        // Max Schuetz
        //
        // EXTRA_TEXT:
        // 3 neue Nachrichten
        //
        // EXTRA_TEXT_LINES:
        // [0] 1
        // [1] 2
        // [2] 3
        //
        // WhatsApp also creates child notifications. These caused
        // duplicate history entries.
        //
        // Therefore WhatsApp child notifications are ignored and
        // only the GROUP SUMMARY is processed.
        // =========================================================

        if (isWhatsAppPackage(
                packageName
        )) {

            boolean isGroupSummary =
                    (notification.flags
                            & Notification.FLAG_GROUP_SUMMARY)
                            != 0;


            if (!isGroupSummary) {

                Log.d(
                        TAG,
                        "Ignoring WhatsApp child notification"
                );

                return;
            }


            Log.d(
                    TAG,
                    "Processing WhatsApp group summary"
            );


            boolean textLinesFound =
                    saveTextLineMessages(
                            sbn,
                            extras,
                            packageName
                    );


            if (textLinesFound) {

                return;
            }


            /*
             * Do not save EXTRA_TEXT as fallback here.
             *
             * WhatsApp EXTRA_TEXT can contain:
             *
             * "3 neue Nachrichten"
             *
             * which is only a summary and not a real message.
             */
            Log.d(
                    TAG,
                    "WhatsApp summary contained no usable EXTRA_TEXT_LINES"
            );

            return;
        }


        // =========================================================
        // Other messenger/apps:
        // First try MessagingStyle.
        // =========================================================

        boolean messagingMessagesFound =
                saveMessagingStyleMessages(
                        sbn,
                        extras,
                        packageName
                );


        if (messagingMessagesFound) {

            return;
        }


        // =========================================================
        // Then try EXTRA_TEXT_LINES
        // =========================================================

        boolean textLinesFound =
                saveTextLineMessages(
                        sbn,
                        extras,
                        packageName
                );


        if (textLinesFound) {

            return;
        }


        // =========================================================
        // Standard notification fallback
        // =========================================================

        saveStandardNotification(
                sbn,
                extras,
                packageName
        );
    }


    // =============================================================
    // Notification removed
    // =============================================================

    @Override
    public void onNotificationRemoved(
            StatusBarNotification sbn
    ) {

        super.onNotificationRemoved(sbn);


        if (sbn == null) {
            return;
        }


        removeTextLineSnapshot(
                safeString(
                        sbn.getKey()
                )
        );
    }


    // =============================================================
    // Is WhatsApp?
    // =============================================================

    private boolean isWhatsAppPackage(
            String packageName
    ) {

        return PACKAGE_WHATSAPP.equals(
                packageName
        )
                || PACKAGE_WHATSAPP_BUSINESS.equals(
                packageName
        );
    }


    // =============================================================
    // EXTRA_TEXT_LINES
    // =============================================================

    private boolean saveTextLineMessages(
            StatusBarNotification sbn,
            Bundle extras,
            String packageName
    ) {

        try {

            CharSequence[] textLines =
                    extras.getCharSequenceArray(
                            Notification.EXTRA_TEXT_LINES
                    );


            if (textLines == null
                    || textLines.length == 0) {

                return false;
            }


            JSONArray currentLines =
                    new JSONArray();


            for (
                    CharSequence line
                    : textLines
            ) {

                String text =
                        safeCharSequence(
                                line
                        ).trim();


                if (!text.isEmpty()) {

                    currentLines.put(
                            text
                    );
                }
            }


            if (currentLines.length() == 0) {

                return false;
            }


            String notificationKey =
                    safeString(
                            sbn.getKey()
                    );


            String title =
                    safeCharSequence(
                            extras.getCharSequence(
                                    Notification.EXTRA_TITLE
                            )
                    );


            JSONArray previousLines =
                    getTextLineSnapshot(
                            notificationKey
                    );


            int commonPrefixLength =
                    getCommonPrefixLength(
                            previousLines,
                            currentLines
                    );


            Log.d(
                    TAG,
                    "TextLines previous="
                            + previousLines.length()
                            + " current="
                            + currentLines.length()
                            + " common="
                            + commonPrefixLength
            );


            // =====================================================
            // Save only newly appended lines
            // =====================================================

            for (
                    int i = commonPrefixLength;
                    i < currentLines.length();
                    i++
            ) {

                String text =
                        currentLines.optString(
                                i,
                                ""
                        );


                if (text.isEmpty()) {
                    continue;
                }


                long timestamp =
                        System.currentTimeMillis();


                String fingerprint =
                        createTextLineFingerprint(
                                packageName,
                                notificationKey,
                                title,
                                text,
                                i,
                                timestamp
                        );


                boolean saved =
                        saveHistoryEntry(
                                packageName,
                                title,
                                text,
                                timestamp,
                                notificationKey,
                                fingerprint
                        );


                if (saved) {

                    saveLastNotification(
                            packageName,
                            title,
                            text,
                            timestamp
                    );


                    Log.d(
                            TAG,
                            "Saved TextLine: "
                                    + title
                                    + " | "
                                    + text
                    );
                }
            }


            // =====================================================
            // Store current state
            // =====================================================

            saveTextLineSnapshot(
                    notificationKey,
                    currentLines
            );


            /*
             * Even if there were no new lines, this notification has
             * EXTRA_TEXT_LINES and must not fall back to EXTRA_TEXT.
             */
            return true;


        } catch (Exception e) {

            Log.e(
                    TAG,
                    "Could not process EXTRA_TEXT_LINES",
                    e
            );

            return false;
        }
    }


    // =============================================================
    // Common prefix
    // =============================================================

    private int getCommonPrefixLength(
            JSONArray previousLines,
            JSONArray currentLines
    ) {

        int maximum =
                Math.min(
                        previousLines.length(),
                        currentLines.length()
                );


        int common =
                0;


        for (
                int i = 0;
                i < maximum;
                i++
        ) {

            String previous =
                    previousLines.optString(
                            i,
                            ""
                    );


            String current =
                    currentLines.optString(
                            i,
                            ""
                    );


            if (!previous.equals(
                    current
            )) {

                break;
            }


            common++;
        }


        return common;
    }


    // =============================================================
    // Get TextLines snapshot
    // =============================================================

    private JSONArray getTextLineSnapshot(
            String notificationKey
    ) {

        try {

            SharedPreferences prefs =
                    getSharedPreferences(
                            PREFS_NAME,
                            MODE_PRIVATE
                    );


            String raw =
                    prefs.getString(
                            KEY_TEXT_LINE_SNAPSHOTS,
                            "{}"
                    );


            JSONObject snapshots =
                    new JSONObject(
                            raw
                    );


            JSONArray snapshot =
                    snapshots.optJSONArray(
                            notificationKey
                    );


            if (snapshot == null) {

                return new JSONArray();
            }


            return snapshot;


        } catch (Exception e) {

            return new JSONArray();
        }
    }


    // =============================================================
    // Save TextLines snapshot
    // =============================================================

    private void saveTextLineSnapshot(
            String notificationKey,
            JSONArray lines
    ) {

        try {

            SharedPreferences prefs =
                    getSharedPreferences(
                            PREFS_NAME,
                            MODE_PRIVATE
                    );


            String raw =
                    prefs.getString(
                            KEY_TEXT_LINE_SNAPSHOTS,
                            "{}"
                    );


            JSONObject snapshots =
                    new JSONObject(
                            raw
                    );


            JSONArray copy =
                    new JSONArray(
                            lines.toString()
                    );


            snapshots.put(
                    notificationKey,
                    copy
            );


            prefs.edit()
                    .putString(
                            KEY_TEXT_LINE_SNAPSHOTS,
                            snapshots.toString()
                    )
                    .apply();


        } catch (Exception e) {

            Log.e(
                    TAG,
                    "Could not save TextLines snapshot",
                    e
            );
        }
    }


    // =============================================================
    // Remove TextLines snapshot
    // =============================================================

    private void removeTextLineSnapshot(
            String notificationKey
    ) {

        if (notificationKey.isEmpty()) {

            return;
        }


        try {

            SharedPreferences prefs =
                    getSharedPreferences(
                            PREFS_NAME,
                            MODE_PRIVATE
                    );


            String raw =
                    prefs.getString(
                            KEY_TEXT_LINE_SNAPSHOTS,
                            "{}"
                    );


            JSONObject snapshots =
                    new JSONObject(
                            raw
                    );


            snapshots.remove(
                    notificationKey
            );


            prefs.edit()
                    .putString(
                            KEY_TEXT_LINE_SNAPSHOTS,
                            snapshots.toString()
                    )
                    .apply();


        } catch (Exception e) {

            Log.e(
                    TAG,
                    "Could not remove TextLines snapshot",
                    e
            );
        }
    }


    // =============================================================
    // MessagingStyle
    // =============================================================

    private boolean saveMessagingStyleMessages(
            StatusBarNotification sbn,
            Bundle extras,
            String packageName
    ) {

        try {

            Parcelable[] messageBundles =
                    extras.getParcelableArray(
                            Notification.EXTRA_MESSAGES
                    );


            if (messageBundles == null
                    || messageBundles.length == 0) {

                return false;
            }


            List<Notification.MessagingStyle.Message> messages =
                    Notification.MessagingStyle.Message
                            .getMessagesFromBundleArray(
                                    messageBundles
                            );


            if (messages == null
                    || messages.isEmpty()) {

                return false;
            }


            String conversationTitle =
                    getConversationTitle(
                            extras
                    );


            for (
                    Notification.MessagingStyle.Message message
                    : messages
            ) {

                if (message == null) {
                    continue;
                }


                String text =
                        safeCharSequence(
                                message.getText()
                        ).trim();


                if (text.isEmpty()) {
                    continue;
                }


                String sender =
                        getMessageSender(
                                message
                        );


                if (sender.isEmpty()) {

                    sender =
                            conversationTitle;
                }


                long messageTimestamp =
                        message.getTimestamp();


                if (messageTimestamp <= 0) {

                    messageTimestamp =
                            System.currentTimeMillis();
                }


                String notificationKey =
                        safeString(
                                sbn.getKey()
                        );


                String fingerprint =
                        createMessageFingerprint(
                                packageName,
                                notificationKey,
                                sender,
                                text,
                                messageTimestamp
                        );


                boolean saved =
                        saveHistoryEntry(
                                packageName,
                                sender,
                                text,
                                messageTimestamp,
                                notificationKey,
                                fingerprint
                        );


                if (saved) {

                    saveLastNotification(
                            packageName,
                            sender,
                            text,
                            messageTimestamp
                    );
                }
            }


            return true;


        } catch (Exception e) {

            Log.e(
                    TAG,
                    "Could not read MessagingStyle messages",
                    e
            );

            return false;
        }
    }


    // =============================================================
    // Standard notification
    // =============================================================

    private void saveStandardNotification(
            StatusBarNotification sbn,
            Bundle extras,
            String packageName
    ) {

        String title =
                safeCharSequence(
                        extras.getCharSequence(
                                Notification.EXTRA_TITLE
                        )
                );


        String text =
                safeCharSequence(
                        extras.getCharSequence(
                                Notification.EXTRA_TEXT
                        )
                );


        if (title.isEmpty()
                && text.isEmpty()) {

            return;
        }


        long timestamp =
                System.currentTimeMillis();


        String notificationKey =
                safeString(
                        sbn.getKey()
                );


        String fingerprint =
                createStandardFingerprint(
                        packageName,
                        notificationKey,
                        title,
                        text
                );


        boolean saved =
                saveStandardHistoryEntry(
                        packageName,
                        title,
                        text,
                        timestamp,
                        notificationKey,
                        fingerprint
                );


        if (!saved) {
            return;
        }


        saveLastNotification(
                packageName,
                title,
                text,
                timestamp
        );
    }


    // =============================================================
    // Save last notification
    // =============================================================

    private void saveLastNotification(
            String packageName,
            String title,
            String text,
            long timestamp
    ) {

        getSharedPreferences(
                PREFS_NAME,
                MODE_PRIVATE
        )
                .edit()

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
    }


    // =============================================================
    // Save history entry
    // =============================================================

    private synchronized boolean saveHistoryEntry(
            String packageName,
            String title,
            String text,
            long timestamp,
            String notificationKey,
            String fingerprint
    ) {

        try {

            SharedPreferences prefs =
                    getSharedPreferences(
                            PREFS_NAME,
                            MODE_PRIVATE
                    );


            JSONArray history =
                    getStoredHistory(
                            prefs
                    );


            if (historyContainsFingerprint(
                    history,
                    fingerprint
            )) {

                return false;
            }


            JSONObject entry =
                    createHistoryEntry(
                            packageName,
                            title,
                            text,
                            timestamp,
                            notificationKey,
                            fingerprint
                    );


            JSONArray newHistory =
                    appendAndTrimHistory(
                            history,
                            entry
                    );


            prefs.edit()
                    .putString(
                            KEY_NOTIFICATION_HISTORY,
                            newHistory.toString()
                    )
                    .apply();


            return true;


        } catch (Exception e) {

            Log.e(
                    TAG,
                    "Could not save history entry",
                    e
            );

            return false;
        }
    }


    // =============================================================
    // Standard history entry
    // =============================================================

    private synchronized boolean saveStandardHistoryEntry(
            String packageName,
            String title,
            String text,
            long timestamp,
            String notificationKey,
            String fingerprint
    ) {

        try {

            SharedPreferences prefs =
                    getSharedPreferences(
                            PREFS_NAME,
                            MODE_PRIVATE
                    );


            JSONArray history =
                    getStoredHistory(
                            prefs
                    );


            if (historyContainsFingerprint(
                    history,
                    fingerprint
            )) {

                return false;
            }


            if (isRecentStandardDuplicate(
                    history,
                    packageName,
                    notificationKey,
                    title,
                    text,
                    timestamp
            )) {

                return false;
            }


            JSONObject entry =
                    createHistoryEntry(
                            packageName,
                            title,
                            text,
                            timestamp,
                            notificationKey,
                            fingerprint
                    );


            JSONArray newHistory =
                    appendAndTrimHistory(
                            history,
                            entry
                    );


            prefs.edit()
                    .putString(
                            KEY_NOTIFICATION_HISTORY,
                            newHistory.toString()
                    )
                    .apply();


            return true;


        } catch (Exception e) {

            return false;
        }
    }


    // =============================================================
    // Create history entry
    // =============================================================

    private JSONObject createHistoryEntry(
            String packageName,
            String title,
            String text,
            long timestamp,
            String notificationKey,
            String fingerprint
    ) throws Exception {

        JSONObject entry =
                new JSONObject();


        entry.put(
                "id",
                fingerprint
        );

        entry.put(
                "fingerprint",
                fingerprint
        );

        entry.put(
                "notificationKey",
                notificationKey
        );

        entry.put(
                "package",
                packageName
        );

        entry.put(
                "title",
                title
        );

        entry.put(
                "text",
                text
        );

        entry.put(
                "timestamp",
                timestamp
        );


        return entry;
    }


    // =============================================================
    // Stored history
    // =============================================================

    private JSONArray getStoredHistory(
            SharedPreferences prefs
    ) {

        try {

            return new JSONArray(
                    prefs.getString(
                            KEY_NOTIFICATION_HISTORY,
                            "[]"
                    )
            );


        } catch (Exception e) {

            return new JSONArray();
        }
    }


    // =============================================================
    // Append + trim
    // =============================================================

    private JSONArray appendAndTrimHistory(
            JSONArray oldHistory,
            JSONObject newEntry
    ) throws Exception {

        JSONArray newHistory =
                new JSONArray();


        int startIndex =
                Math.max(
                        0,
                        oldHistory.length()
                                - (MAX_NOTIFICATION_HISTORY - 1)
                );


        for (
                int i = startIndex;
                i < oldHistory.length();
                i++
        ) {

            newHistory.put(
                    oldHistory.getJSONObject(
                            i
                    )
            );
        }


        newHistory.put(
                newEntry
        );


        return newHistory;
    }


    // =============================================================
    // Fingerprint exists?
    // =============================================================

    private boolean historyContainsFingerprint(
            JSONArray history,
            String fingerprint
    ) {

        try {

            for (
                    int i = 0;
                    i < history.length();
                    i++
            ) {

                if (fingerprint.equals(
                        history
                                .getJSONObject(i)
                                .optString(
                                        "fingerprint",
                                        ""
                                )
                )) {

                    return true;
                }
            }


        } catch (Exception ignored) {
        }


        return false;
    }


    // =============================================================
    // Standard duplicate detection
    // =============================================================

    private boolean isRecentStandardDuplicate(
            JSONArray history,
            String packageName,
            String notificationKey,
            String title,
            String text,
            long timestamp
    ) {

        try {

            if (history.length() == 0) {

                return false;
            }


            JSONObject lastEntry =
                    history.getJSONObject(
                            history.length() - 1
                    );


            boolean same =
                    packageName.equals(
                            lastEntry.optString(
                                    "package",
                                    ""
                            )
                    )
                            && notificationKey.equals(
                            lastEntry.optString(
                                    "notificationKey",
                                    ""
                            )
                    )
                            && title.equals(
                            lastEntry.optString(
                                    "title",
                                    ""
                            )
                    )
                            && text.equals(
                            lastEntry.optString(
                                    "text",
                                    ""
                            )
                    );


            long difference =
                    Math.abs(
                            timestamp
                                    - lastEntry.optLong(
                                    "timestamp",
                                    0
                            )
                    );


            return same
                    && difference <= 3000;


        } catch (Exception e) {

            return false;
        }
    }


    // =============================================================
    // Debug report
    // =============================================================

    private void saveDebugReport(
            StatusBarNotification sbn,
            Notification notification,
            Bundle extras,
            String packageName
    ) {

        try {

            StringBuilder report =
                    new StringBuilder();


            report.append(
                    "PIXEL HOUSE NOTIFICATION DEBUG\n"
            );

            report.append(
                    "================================\n\n"
            );


            report.append(
                    "PACKAGE:\n"
                            + packageName
                            + "\n\n"
            );


            report.append(
                    "NOTIFICATION KEY:\n"
                            + safeString(
                            sbn.getKey()
                    )
                            + "\n\n"
            );


            report.append(
                    "NOTIFICATION ID:\n"
                            + sbn.getId()
                            + "\n\n"
            );


            report.append(
                    "TAG:\n"
                            + safeString(
                            sbn.getTag()
                    )
                            + "\n\n"
            );


            report.append(
                    "POST TIME:\n"
                            + sbn.getPostTime()
                            + "\n\n"
            );


            report.append(
                    "GROUP KEY:\n"
                            + safeString(
                            sbn.getGroupKey()
                    )
                            + "\n\n"
            );


            report.append(
                    "CATEGORY:\n"
                            + safeString(
                            notification.category
                    )
                            + "\n\n"
            );


            report.append(
                    "FLAGS:\n"
                            + notification.flags
                            + "\n\n"
            );


            report.append(
                    "IS GROUP SUMMARY:\n"
                            + (
                            (notification.flags
                                    & Notification.FLAG_GROUP_SUMMARY)
                                    != 0
                    )
                            + "\n\n"
            );


            appendDebugField(
                    report,
                    "EXTRA_TITLE",
                    extras.getCharSequence(
                            Notification.EXTRA_TITLE
                    )
            );


            appendDebugField(
                    report,
                    "EXTRA_TEXT",
                    extras.getCharSequence(
                            Notification.EXTRA_TEXT
                    )
            );


            appendDebugField(
                    report,
                    "EXTRA_BIG_TEXT",
                    extras.getCharSequence(
                            Notification.EXTRA_BIG_TEXT
                    )
            );


            appendDebugField(
                    report,
                    "EXTRA_SUMMARY_TEXT",
                    extras.getCharSequence(
                            Notification.EXTRA_SUMMARY_TEXT
                    )
            );


            appendDebugField(
                    report,
                    "EXTRA_SUB_TEXT",
                    extras.getCharSequence(
                            Notification.EXTRA_SUB_TEXT
                    )
            );


            appendDebugField(
                    report,
                    "EXTRA_INFO_TEXT",
                    extras.getCharSequence(
                            Notification.EXTRA_INFO_TEXT
                    )
            );


            appendDebugField(
                    report,
                    "EXTRA_CONVERSATION_TITLE",
                    extras.getCharSequence(
                            Notification.EXTRA_CONVERSATION_TITLE
                    )
            );


            report.append(
                    "EXTRA_TEXT_LINES:\n"
            );


            CharSequence[] textLines =
                    extras.getCharSequenceArray(
                            Notification.EXTRA_TEXT_LINES
                    );


            if (textLines == null
                    || textLines.length == 0) {

                report.append(
                        "<none>\n\n"
                );

            } else {

                for (
                        int i = 0;
                        i < textLines.length;
                        i++
                ) {

                    report.append(
                            "["
                                    + i
                                    + "] "
                                    + safeCharSequence(
                                    textLines[i]
                            )
                                    + "\n"
                    );
                }


                report.append(
                        "\n"
                );
            }


            appendMessagingStyleDebug(
                    report,
                    extras,
                    Notification.EXTRA_MESSAGES,
                    "EXTRA_MESSAGES"
            );


            appendMessagingStyleDebug(
                    report,
                    extras,
                    Notification.EXTRA_HISTORIC_MESSAGES,
                    "EXTRA_HISTORIC_MESSAGES"
            );


            report.append(
                    "ALL EXTRA KEYS:\n"
            );


            for (String key : extras.keySet()) {

                report.append(
                        "- "
                                + key
                                + "\n"
                );
            }


            getSharedPreferences(
                    PREFS_NAME,
                    MODE_PRIVATE
            )
                    .edit()
                    .putString(
                            KEY_DEBUG_REPORT,
                            report.toString()
                    )
                    .apply();


        } catch (Exception e) {

            Log.e(
                    TAG,
                    "Could not create debug report",
                    e
            );
        }
    }


    // =============================================================
    // Debug field
    // =============================================================

    private void appendDebugField(
            StringBuilder report,
            String name,
            CharSequence value
    ) {

        report.append(
                name
                        + ":\n"
        );


        String text =
                safeCharSequence(
                        value
                );


        report.append(
                text.isEmpty()
                        ? "<empty>"
                        : text
        );


        report.append(
                "\n\n"
        );
    }


    // =============================================================
    // Debug MessagingStyle
    // =============================================================

    private void appendMessagingStyleDebug(
            StringBuilder report,
            Bundle extras,
            String extraKey,
            String name
    ) {

        report.append(
                name
                        + ":\n"
        );


        try {

            Parcelable[] bundles =
                    extras.getParcelableArray(
                            extraKey
                    );


            if (bundles == null
                    || bundles.length == 0) {

                report.append(
                        "<none>\n\n"
                );

                return;
            }


            List<Notification.MessagingStyle.Message> messages =
                    Notification.MessagingStyle.Message
                            .getMessagesFromBundleArray(
                                    bundles
                            );


            if (messages == null
                    || messages.isEmpty()) {

                report.append(
                        "<none>\n\n"
                );

                return;
            }


            report.append(
                    "Count: "
                            + messages.size()
                            + "\n"
            );


            for (
                    int i = 0;
                    i < messages.size();
                    i++
            ) {

                Notification.MessagingStyle.Message message =
                        messages.get(
                                i
                        );


                report.append(
                        "MESSAGE "
                                + i
                                + "\n"
                );

                report.append(
                        "Sender: "
                                + getMessageSender(
                                message
                        )
                                + "\n"
                );

                report.append(
                        "Text: "
                                + safeCharSequence(
                                message.getText()
                        )
                                + "\n"
                );

                report.append(
                        "Timestamp: "
                                + message.getTimestamp()
                                + "\n"
                );
            }


            report.append(
                    "\n"
            );


        } catch (Exception e) {

            report.append(
                    "<error>\n\n"
            );
        }
    }


    // =============================================================
    // Conversation title
    // =============================================================

    private String getConversationTitle(
            Bundle extras
    ) {

        String title =
                safeCharSequence(
                        extras.getCharSequence(
                                Notification.EXTRA_CONVERSATION_TITLE
                        )
                );


        if (!title.isEmpty()) {

            return title;
        }


        return safeCharSequence(
                extras.getCharSequence(
                        Notification.EXTRA_TITLE
                )
        );
    }


    // =============================================================
    // Message sender
    // =============================================================

    private String getMessageSender(
            Notification.MessagingStyle.Message message
    ) {

        try {

            if (Build.VERSION.SDK_INT
                    >= Build.VERSION_CODES.P) {

                Person person =
                        message.getSenderPerson();


                if (person != null
                        && person.getName() != null) {

                    return person
                            .getName()
                            .toString();
                }
            }


            CharSequence sender =
                    message.getSender();


            if (sender != null) {

                return sender.toString();
            }


        } catch (Exception ignored) {
        }


        return "";
    }


    // =============================================================
    // Fingerprints
    // =============================================================

    private String createTextLineFingerprint(
            String packageName,
            String notificationKey,
            String title,
            String text,
            int lineIndex,
            long timestamp
    ) {

        return sha256(
                packageName
                        + "|"
                        + notificationKey
                        + "|textline|"
                        + title
                        + "|"
                        + lineIndex
                        + "|"
                        + text
                        + "|"
                        + timestamp
        );
    }


    private String createMessageFingerprint(
            String packageName,
            String notificationKey,
            String sender,
            String text,
            long timestamp
    ) {

        return sha256(
                packageName
                        + "|"
                        + notificationKey
                        + "|"
                        + sender
                        + "|"
                        + text
                        + "|"
                        + timestamp
        );
    }


    private String createStandardFingerprint(
            String packageName,
            String notificationKey,
            String title,
            String text
    ) {

        return sha256(
                packageName
                        + "|"
                        + notificationKey
                        + "|"
                        + title
                        + "|"
                        + text
        );
    }


    // =============================================================
    // SHA-256
    // =============================================================

    private String sha256(
            String value
    ) {

        try {

            MessageDigest digest =
                    MessageDigest.getInstance(
                            "SHA-256"
                    );


            byte[] hash =
                    digest.digest(
                            value.getBytes(
                                    StandardCharsets.UTF_8
                            )
                    );


            StringBuilder result =
                    new StringBuilder();


            for (byte b : hash) {

                result.append(
                        String.format(
                                "%02x",
                                b
                        )
                );
            }


            return result.toString();


        } catch (Exception e) {

            return String.valueOf(
                    value.hashCode()
            );
        }
    }


    // =============================================================
    // Whitelist
    // =============================================================

    private boolean isPackageMonitored(
            String packageName
    ) {

        Set<String> packages =
                getSharedPreferences(
                        PREFS_NAME,
                        MODE_PRIVATE
                )
                        .getStringSet(
                                KEY_MONITORED_PACKAGES,
                                Collections.<String>emptySet()
                        );


        return packages.contains(
                packageName
        );
    }


    // =============================================================
    // Helpers
    // =============================================================

    private String safeString(
            String value
    ) {

        return value == null
                ? ""
                : value;
    }


    private String safeCharSequence(
            CharSequence value
    ) {

        return value == null
                ? ""
                : value.toString();
    }
}