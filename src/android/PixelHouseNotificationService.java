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
        // First try Android MessagingStyle messages.
        //
        // Messenger apps such as WhatsApp can store the actual
        // individual messages in EXTRA_MESSAGES.
        // =========================================================

        boolean messagingMessagesFound =
                saveMessagingStyleMessages(
                        sbn,
                        extras,
                        packageName
                );


        // =========================================================
        // If real MessagingStyle messages were found, we do not
        // additionally save EXTRA_TEXT. EXTRA_TEXT is often only a
        // summary such as "3 new messages".
        // =========================================================

        if (messagingMessagesFound) {

            return;
        }


        // =========================================================
        // Fallback for normal notifications
        // =========================================================

        saveStandardNotification(
                sbn,
                extras,
                packageName
        );
    }


    // =============================================================
    // Read MessagingStyle messages
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


                // If Android/WhatsApp does not provide an individual
                // sender, use the conversation title as fallback.
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


                    Log.d(
                            TAG,
                            "Saved MessagingStyle message"
                    );

                    Log.d(
                            TAG,
                            "Package: "
                                    + packageName
                    );

                    Log.d(
                            TAG,
                            "Sender: "
                                    + sender
                    );

                    Log.d(
                            TAG,
                            "Text: "
                                    + text
                    );

                    Log.d(
                            TAG,
                            "Timestamp: "
                                    + messageTimestamp
                    );
                }
            }


            /*
             * Returning true means that the notification contained
             * valid MessagingStyle messages.
             *
             * Even when all of them were already known duplicates,
             * EXTRA_TEXT must not additionally be stored because it
             * may only contain a summary such as "3".
             */
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

            Log.d(
                    TAG,
                    "Empty notification ignored from: "
                            + packageName
            );

            return;
        }


        long timestamp =
                System.currentTimeMillis();


        String notificationKey =
                safeString(
                        sbn.getKey()
                );


        // Standard notifications do not always provide a unique
        // message timestamp. Therefore duplicate detection also
        // checks recent matching entries.
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

            Log.d(
                    TAG,
                    "Duplicate standard notification ignored"
            );

            return;
        }


        saveLastNotification(
                packageName,
                title,
                text,
                timestamp
        );


        Log.d(
                TAG,
                "Saved standard notification"
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
                "Timestamp: "
                        + timestamp
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
    // Save MessagingStyle history entry
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


            // =====================================================
            // Check whether this exact message already exists.
            //
            // WhatsApp can send the complete conversation portion
            // again whenever its notification is updated.
            // =====================================================

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
    // Save standard history entry
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


            // =====================================================
            // First check the exact generated fingerprint.
            // =====================================================

            if (historyContainsFingerprint(
                    history,
                    fingerprint
            )) {

                return false;
            }


            // =====================================================
            // A standard notification can be reposted several times
            // within a very short period.
            //
            // If package, notification key, title and text are equal
            // and the last stored entry is only a few seconds old,
            // treat it as the same notification update.
            // =====================================================

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


            Log.d(
                    TAG,
                    "History count: "
                            + trimmedHistory.length()
            );


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
    // Create one history entry
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
    // Read stored history
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

            Log.e(
                    TAG,
                    "Could not read stored history",
                    e
            );


            return new JSONArray();
        }
    }


    // =============================================================
    // Append history entry and keep newest 500
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
    // Exact fingerprint already stored?
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


        } catch (Exception e) {

            Log.e(
                    TAG,
                    "Could not check history fingerprint",
                    e
            );
        }


        return false;
    }


    // =============================================================
    // Detect repeated normal-notification updates
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
    // Get sender from MessagingStyle message
    // =============================================================

    private String getMessageSender(
            Notification.MessagingStyle.Message message
    ) {

        try {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {

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


        } catch (Exception e) {

            Log.e(
                    TAG,
                    "Could not read message sender",
                    e
            );
        }


        return "";
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
    // Normal notification fingerprint
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
    // SHA-256 helper
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
    // Safe string helpers
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