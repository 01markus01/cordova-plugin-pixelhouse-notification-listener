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


    // =============================================================
    // WhatsApp / TextLines snapshot storage
    //
    // Stores the last known EXTRA_TEXT_LINES for every active
    // Android notification key.
    //
    // This allows us to distinguish:
    //
    // Update 1:
    // [1]
    //
    // Update 2:
    // [1, 2]
    //
    // Update 3:
    // [1, 2, 3]
    //
    // and save only 1, then 2, then 3.
    // =============================================================

    public static final String KEY_TEXT_LINE_SNAPSHOTS =
            "text_line_snapshots";


    // =============================================================
    // DEBUG
    // =============================================================

    public static final String KEY_DEBUG_REPORT =
            "debug_report";


    // =============================================================
    // History settings
    // =============================================================

    public static final int MAX_NOTIFICATION_HISTORY =
            500;


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


        if (extras == null) {
            return;
        }


        // =========================================================
        // DEBUG REPORT
        // =========================================================

        saveDebugReport(
                sbn,
                notification,
                extras,
                packageName
        );


        // =========================================================
        // Priority 1:
        // Android MessagingStyle / EXTRA_MESSAGES
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
        // Priority 2:
        // EXTRA_TEXT_LINES
        //
        // This is the important path for WhatsApp on our test device.
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
        // Priority 3:
        // Normal notification fallback
        // =========================================================

        saveStandardNotification(
                sbn,
                extras,
                packageName
        );
    }


    // =============================================================
    // Notification removed
    //
    // When Android removes a notification, delete its TextLines
    // snapshot. If the same conversation later creates a fresh
    // notification with the same Android notification key, it can
    // start a new message sequence cleanly.
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
    // EXTRA_TEXT_LINES
    //
    // WhatsApp on our test device provides:
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
    // We therefore treat every text line as one message.
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


            // =====================================================
            // Determine how many lines at the beginning are already
            // known from the previous update.
            // =====================================================

            int commonPrefixLength =
                    getCommonPrefixLength(
                            previousLines,
                            currentLines
                    );


            Log.d(
                    TAG,
                    "TextLines current: "
                            + currentLines.length()
                            + " previous: "
                            + previousLines.length()
                            + " common prefix: "
                            + commonPrefixLength
            );


            // =====================================================
            // Save only newly appended / changed lines.
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


                /*
                 * The TextLines snapshot mechanism performs the
                 * duplicate detection.
                 *
                 * Timestamp is included in the ID so that two genuine
                 * identical messages such as:
                 *
                 * "OK"
                 * "OK"
                 *
                 * can both be stored when they are separate entries.
                 */

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
                            "Saved EXTRA_TEXT_LINES message"
                    );

                    Log.d(
                            TAG,
                            "Package: "
                                    + packageName
                    );

                    Log.d(
                            TAG,
                            "Title: "
                                    + title
                    );

                    Log.d(
                            TAG,
                            "Text: "
                                    + text
                    );

                    Log.d(
                            TAG,
                            "Line index: "
                                    + i
                    );
                }
            }


            // =====================================================
            // Remember the complete current state for the next
            // WhatsApp notification update.
            // =====================================================

            saveTextLineSnapshot(
                    notificationKey,
                    currentLines
            );


            /*
             * Always return true when EXTRA_TEXT_LINES existed.
             *
             * Even if there were no new lines, we must NOT fall back
             * to EXTRA_TEXT because that contains summaries such as:
             *
             * "3 neue Nachrichten"
             */
            return true;


        } catch (Exception e) {

            Log.e(
                    TAG,
                    "Could not read EXTRA_TEXT_LINES",
                    e
            );

            return false;
        }
    }


    // =============================================================
    // Compare previous and current TextLines
    // =============================================================

    private int getCommonPrefixLength(
            JSONArray previousLines,
            JSONArray currentLines
    ) {

        int max =
                Math.min(
                        previousLines.length(),
                        currentLines.length()
                );


        int common =
                0;


        for (
                int i = 0;
                i < max;
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


            /*
             * Make a copy so the stored object is independent.
             */

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


            Log.d(
                    TAG,
                    "Removed TextLines snapshot: "
                            + notificationKey
            );


        } catch (Exception e) {

            Log.e(
                    TAG,
                    "Could not remove TextLines snapshot",
                    e
            );
        }
    }


    // =============================================================
    // DEBUG REPORT
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
            );

            report.append(
                    packageName
            );

            report.append(
                    "\n\n"
            );


            report.append(
                    "NOTIFICATION KEY:\n"
            );

            report.append(
                    safeString(
                            sbn.getKey()
                    )
            );

            report.append(
                    "\n\n"
            );


            report.append(
                    "NOTIFICATION ID:\n"
            );

            report.append(
                    sbn.getId()
            );

            report.append(
                    "\n\n"
            );


            report.append(
                    "TAG:\n"
            );

            report.append(
                    safeString(
                            sbn.getTag()
                    )
            );

            report.append(
                    "\n\n"
            );


            report.append(
                    "POST TIME:\n"
            );

            report.append(
                    sbn.getPostTime()
            );

            report.append(
                    "\n\n"
            );


            report.append(
                    "GROUP KEY:\n"
            );

            report.append(
                    safeString(
                            sbn.getGroupKey()
                    )
            );

            report.append(
                    "\n\n"
            );


            report.append(
                    "CATEGORY:\n"
            );

            report.append(
                    safeString(
                            notification.category
                    )
            );

            report.append(
                    "\n\n"
            );


            report.append(
                    "FLAGS:\n"
            );

            report.append(
                    notification.flags
            );

            report.append(
                    "\n\n"
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


            // =====================================================
            // EXTRA_TEXT_LINES
            // =====================================================

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
                    );

                    report.append(
                            i
                    );

                    report.append(
                            "] "
                    );

                    report.append(
                            safeCharSequence(
                                    textLines[i]
                            )
                    );

                    report.append(
                            "\n"
                    );
                }


                report.append(
                        "\n"
                );
            }


            // =====================================================
            // EXTRA_MESSAGES
            // =====================================================

            appendMessagingStyleDebug(
                    report,
                    extras,
                    Notification.EXTRA_MESSAGES,
                    "EXTRA_MESSAGES"
            );


            // =====================================================
            // EXTRA_HISTORIC_MESSAGES
            // =====================================================

            appendMessagingStyleDebug(
                    report,
                    extras,
                    Notification.EXTRA_HISTORIC_MESSAGES,
                    "EXTRA_HISTORIC_MESSAGES"
            );


            // =====================================================
            // All keys
            // =====================================================

            report.append(
                    "ALL EXTRA KEYS:\n"
            );


            Set<String> keys =
                    extras.keySet();


            if (keys == null
                    || keys.isEmpty()) {

                report.append(
                        "<none>\n"
                );

            } else {

                for (String key : keys) {

                    report.append(
                            "- "
                    );

                    report.append(
                            key
                    );


                    try {

                        Object value =
                                extras.get(
                                        key
                                );


                        if (value != null) {

                            report.append(
                                    " ["
                            );

                            report.append(
                                    value
                                            .getClass()
                                            .getSimpleName()
                            );

                            report.append(
                                    "]"
                            );
                        }

                    } catch (Exception ignored) {
                    }


                    report.append(
                            "\n"
                    );
                }
            }


            report.append(
                    "\n================================\n"
            );


            String debugReport =
                    report.toString();


            getSharedPreferences(
                    PREFS_NAME,
                    MODE_PRIVATE
            )
                    .edit()
                    .putString(
                            KEY_DEBUG_REPORT,
                            debugReport
                    )
                    .apply();


            Log.d(
                    TAG,
                    debugReport
            );


        } catch (Exception e) {

            Log.e(
                    TAG,
                    "Could not create debug report",
                    e
            );
        }
    }


    // =============================================================
    // DEBUG field
    // =============================================================

    private void appendDebugField(
            StringBuilder report,
            String fieldName,
            CharSequence value
    ) {

        report.append(
                fieldName
        );

        report.append(
                ":\n"
        );


        String text =
                safeCharSequence(
                        value
                );


        if (text.isEmpty()) {

            report.append(
                    "<empty>"
            );

        } else {

            report.append(
                    text
            );
        }


        report.append(
                "\n\n"
        );
    }


    // =============================================================
    // DEBUG MessagingStyle
    // =============================================================

    private void appendMessagingStyleDebug(
            StringBuilder report,
            Bundle extras,
            String extraKey,
            String debugName
    ) {

        report.append(
                debugName
        );

        report.append(
                ":\n"
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
                        "<unable to parse>\n\n"
                );

                return;
            }


            report.append(
                    "Count: "
            );

            report.append(
                    messages.size()
            );

            report.append(
                    "\n"
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
                        "\nMESSAGE "
                );

                report.append(
                        i
                );

                report.append(
                        "\n"
                );


                report.append(
                        "Sender: "
                );

                report.append(
                        getMessageSender(
                                message
                        )
                );

                report.append(
                        "\n"
                );


                report.append(
                        "Text: "
                );

                report.append(
                        safeCharSequence(
                                message.getText()
                        )
                );

                report.append(
                        "\n"
                );


                report.append(
                        "Timestamp: "
                );

                report.append(
                        message.getTimestamp()
                );

                report.append(
                        "\n"
                );
            }


            report.append(
                    "\n"
            );


        } catch (Exception e) {

            report.append(
                    "<error: "
            );

            report.append(
                    e.getMessage()
            );

            report.append(
                    ">\n\n"
            );
        }
    }


    // =============================================================
    // MessagingStyle messages
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
                        );


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
    // Standard notification fallback
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


            JSONArray trimmedHistory =
                    appendAndTrimHistory(
                            history,
                            entry
                    );


            prefs.edit()
                    .putString(
                            KEY_NOTIFICATION_HISTORY,
                            trimmedHistory.toString()
                    )
                    .apply();


            Log.d(
                    TAG,
                    "History count: "
                            + trimmedHistory.length()
            );


            return true;


        } catch (Exception e) {

            Log.e(
                    TAG,
                    "Could not save notification history",
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


            JSONArray trimmedHistory =
                    appendAndTrimHistory(
                            history,
                            entry
                    );


            prefs.edit()
                    .putString(
                            KEY_NOTIFICATION_HISTORY,
                            trimmedHistory.toString()
                    )
                    .apply();


            return true;


        } catch (Exception e) {

            Log.e(
                    TAG,
                    "Could not save standard notification history",
                    e
            );

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

            String storedHistory =
                    prefs.getString(
                            KEY_NOTIFICATION_HISTORY,
                            "[]"
                    );


            return new JSONArray(
                    storedHistory
            );


        } catch (Exception e) {

            return new JSONArray();
        }
    }


    // =============================================================
    // Append and trim history
    // =============================================================

    private JSONArray appendAndTrimHistory(
            JSONArray oldHistory,
            JSONObject newEntry
    ) throws Exception {

        JSONArray newHistory =
                new JSONArray();


        int numberOfOldEntriesToKeep =
                MAX_NOTIFICATION_HISTORY
                        - 1;


        int startIndex =
                Math.max(
                        0,
                        oldHistory.length()
                                - numberOfOldEntriesToKeep
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
    // Fingerprint already stored?
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

                JSONObject entry =
                        history.getJSONObject(
                                i
                        );


                String storedFingerprint =
                        entry.optString(
                                "fingerprint",
                                ""
                        );


                if (fingerprint.equals(
                        storedFingerprint
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


            String oldPackage =
                    lastEntry.optString(
                            "package",
                            ""
                    );


            String oldKey =
                    lastEntry.optString(
                            "notificationKey",
                            ""
                    );


            String oldTitle =
                    lastEntry.optString(
                            "title",
                            ""
                    );


            String oldText =
                    lastEntry.optString(
                            "text",
                            ""
                    );


            long oldTimestamp =
                    lastEntry.optLong(
                            "timestamp",
                            0
                    );


            boolean sameContent =
                    packageName.equals(
                            oldPackage
                    )
                            && notificationKey.equals(
                            oldKey
                    )
                            && title.equals(
                            oldTitle
                    )
                            && text.equals(
                            oldText
                    );


            long timeDifference =
                    Math.abs(
                            timestamp
                                    - oldTimestamp
                    );


            return sameContent
                    && timeDifference <= 3000;


        } catch (Exception e) {

            return false;
        }
    }


    // =============================================================
    // Conversation title
    // =============================================================

    private String getConversationTitle(
            Bundle extras
    ) {

        String conversationTitle =
                safeCharSequence(
                        extras.getCharSequence(
                                Notification.EXTRA_CONVERSATION_TITLE
                        )
                );


        if (!conversationTitle.isEmpty()) {

            return conversationTitle;
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

                Person senderPerson =
                        message.getSenderPerson();


                if (senderPerson != null) {

                    CharSequence name =
                            senderPerson.getName();


                    if (name != null) {

                        return name.toString();
                    }
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
    // TextLines fingerprint
    // =============================================================

    private String createTextLineFingerprint(
            String packageName,
            String notificationKey,
            String title,
            String text,
            int lineIndex,
            long timestamp
    ) {

        String raw =
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
                        + timestamp;


        return sha256(
                raw
        );
    }


    // =============================================================
    // MessagingStyle fingerprint
    // =============================================================

    private String createMessageFingerprint(
            String packageName,
            String notificationKey,
            String sender,
            String text,
            long messageTimestamp
    ) {

        String raw =
                packageName
                        + "|"
                        + notificationKey
                        + "|"
                        + sender
                        + "|"
                        + text
                        + "|"
                        + messageTimestamp;


        return sha256(
                raw
        );
    }


    // =============================================================
    // Standard fingerprint
    // =============================================================

    private String createStandardFingerprint(
            String packageName,
            String notificationKey,
            String title,
            String text
    ) {

        String raw =
                packageName
                        + "|"
                        + notificationKey
                        + "|"
                        + title
                        + "|"
                        + text;


        return sha256(
                raw
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


    // =============================================================
    // Safe helpers
    // =============================================================

    private String safeString(
            String value
    ) {

        if (value == null) {

            return "";
        }


        return value;
    }


    private String safeCharSequence(
            CharSequence value
    ) {

        if (value == null) {

            return "";
        }


        return value.toString();
    }
}