package com.pixelhouse.notificationlistener;

import android.app.Notification;
import android.app.Person;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
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

    // Legacy single-list history key (used only for one-time migration).
    public static final String KEY_NOTIFICATION_HISTORY =
            "notification_history";

    // New per-package history storage. Each app/package receives its own
    // independent JSON array with up to MAX_NOTIFICATION_HISTORY entries.
    public static final String KEY_NOTIFICATION_HISTORY_PREFIX =
            "notification_history::";

    public static final String KEY_HISTORY_MIGRATED =
            "notification_history_migrated_v2";

    public static final String KEY_TEXT_LINE_SNAPSHOTS =
            "text_line_snapshots";

    public static final String KEY_DEBUG_REPORT =
            "debug_report";

    public static final String KEY_PENDING_NOTIFICATION_IMAGES_PREFIX =
            "pending_notification_images::";

    public static final String IMAGE_DIRECTORY_NAME =
            "notification_images";

    private static final int MAX_DEBUG_REPORT_CHARS =
            120000;

    private static final int MAX_PENDING_NOTIFICATION_IMAGES =
            50;

    private static final int MAX_STORED_IMAGE_DIMENSION =
            1600;

    private static final int STORED_IMAGE_JPEG_QUALITY =
            82;

    private static final long IMAGE_HISTORY_MATCH_WINDOW_MS =
            120000;

    private static final long PENDING_IMAGE_MAX_AGE_MS =
            600000;


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
        // Capture MessagingStyle image data immediately.
        //
        // WhatsApp and Signal can publish the text first and add the
        // temporary content:// image URI in a later update. The URI
        // must be copied while the notification grants access to it.
        // This step never creates a history message by itself.
        // =========================================================

        captureMessagingStyleImages(
                sbn,
                extras,
                packageName
        );


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
        // Therefore WhatsApp child notifications are ignored for
        // text history. Their MessagingStyle image URIs were already
        // handled above so they can enrich the summary history entry
        // without creating duplicates.
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
    // MessagingStyle image capture
    // =============================================================

    private void captureMessagingStyleImages(
            StatusBarNotification sbn,
            Bundle extras,
            String packageName
    ) {

        if (Build.VERSION.SDK_INT
                < Build.VERSION_CODES.P) {

            return;
        }


        try {

            Parcelable[] messageBundles =
                    extras.getParcelableArray(
                            Notification.EXTRA_MESSAGES
                    );


            if (messageBundles == null
                    || messageBundles.length == 0) {

                return;
            }


            List<Notification.MessagingStyle.Message> messages =
                    Notification.MessagingStyle.Message
                            .getMessagesFromBundleArray(
                                    messageBundles
                            );


            if (messages == null
                    || messages.isEmpty()) {

                return;
            }


            String conversationTitle =
                    getConversationTitle(
                            extras
                    );


            String notificationKey =
                    safeString(
                            sbn.getKey()
                    );


            for (
                    Notification.MessagingStyle.Message message
                    : messages
            ) {

                if (message == null) {
                    continue;
                }


                String mimeType =
                        safeString(
                                message.getDataMimeType()
                        ).trim();


                Uri dataUri =
                        message.getDataUri();


                if (!isImageMimeType(
                        mimeType
                )
                        || dataUri == null) {

                    continue;
                }


                String text =
                        safeCharSequence(
                                message.getText()
                        ).trim();


                String sender =
                        getMessageSender(
                                message
                        );


                if (sender.isEmpty()) {
                    sender = conversationTitle;
                }


                long messageTimestamp =
                        message.getTimestamp();


                if (messageTimestamp <= 0) {
                    messageTimestamp = sbn.getPostTime();
                }


                if (messageTimestamp <= 0) {
                    messageTimestamp = System.currentTimeMillis();
                }


                StoredImage storedImage =
                        storeNotificationImage(
                                packageName,
                                sender,
                                text,
                                messageTimestamp,
                                dataUri
                        );


                if (storedImage == null) {
                    continue;
                }


                String fingerprint =
                        createMessageFingerprint(
                                packageName,
                                notificationKey,
                                sender,
                                text,
                                messageTimestamp
                        );


                boolean attached =
                        attachImageToHistoryByFingerprint(
                                packageName,
                                fingerprint,
                                storedImage
                        );


                if (!attached
                        && isWhatsAppPackage(
                        packageName
                )) {

                    attached =
                            attachImageToBestHistoryMatch(
                                    packageName,
                                    sender,
                                    text,
                                    messageTimestamp,
                                    storedImage
                            );
                }


                if (!attached) {

                    savePendingImage(
                            packageName,
                            sender,
                            text,
                            messageTimestamp,
                            storedImage
                    );
                }
            }


        } catch (Exception e) {

            Log.e(
                    TAG,
                    "Could not capture MessagingStyle image",
                    e
            );
        }
    }


    private boolean isImageMimeType(
            String mimeType
    ) {

        return mimeType != null
                && mimeType.regionMatches(
                true,
                0,
                "image/",
                0,
                6
        );
    }


    private StoredImage storeNotificationImage(
            String packageName,
            String sender,
            String text,
            long timestamp,
            Uri dataUri
    ) {

        String fileName =
                createImageFileName(
                        packageName,
                        sender,
                        text,
                        timestamp
                );


        File imageDirectory =
                getImageDirectory(
                        this
                );


        if (!imageDirectory.exists()
                && !imageDirectory.mkdirs()) {

            Log.e(
                    TAG,
                    "Could not create notification image directory"
            );

            return null;
        }


        File targetFile =
                new File(
                        imageDirectory,
                        fileName
                );


        if (targetFile.isFile()
                && targetFile.length() > 0) {

            return readStoredImageInfo(
                    targetFile,
                    fileName
            );
        }


        Bitmap originalBitmap = null;
        Bitmap storedBitmap = null;


        try {

            BitmapFactory.Options bounds =
                    new BitmapFactory.Options();

            bounds.inJustDecodeBounds = true;


            try (
                    InputStream boundsStream =
                            getContentResolver()
                                    .openInputStream(
                                            dataUri
                                    )
            ) {

                if (boundsStream != null) {

                    BitmapFactory.decodeStream(
                            boundsStream,
                            null,
                            bounds
                    );
                }
            }


            BitmapFactory.Options decodeOptions =
                    new BitmapFactory.Options();

            decodeOptions.inSampleSize =
                    calculateImageSampleSize(
                            bounds.outWidth,
                            bounds.outHeight
                    );


            try (
                    InputStream imageStream =
                            getContentResolver()
                                    .openInputStream(
                                            dataUri
                                    )
            ) {

                if (imageStream == null) {
                    return null;
                }


                originalBitmap =
                        BitmapFactory.decodeStream(
                                imageStream,
                                null,
                                decodeOptions
                        );
            }


            if (originalBitmap == null) {
                return null;
            }


            storedBitmap =
                    scaleBitmapToMaximumDimension(
                            originalBitmap,
                            MAX_STORED_IMAGE_DIMENSION
                    );


            try (
                    FileOutputStream outputStream =
                            new FileOutputStream(
                                    targetFile,
                                    false
                            )
            ) {

                boolean compressed =
                        storedBitmap.compress(
                                Bitmap.CompressFormat.JPEG,
                                STORED_IMAGE_JPEG_QUALITY,
                                outputStream
                        );


                outputStream.flush();


                if (!compressed) {

                    deleteFileQuietly(
                            targetFile
                    );

                    return null;
                }
            }


            if (!targetFile.isFile()
                    || targetFile.length() <= 0) {

                deleteFileQuietly(
                        targetFile
                );

                return null;
            }


            Log.d(
                    TAG,
                    "Stored notification image: "
                            + fileName
            );


            return new StoredImage(
                    fileName,
                    "image/jpeg",
                    storedBitmap.getWidth(),
                    storedBitmap.getHeight(),
                    targetFile.length(),
                    System.currentTimeMillis()
            );


        } catch (Exception e) {

            deleteFileQuietly(
                    targetFile
            );


            Log.e(
                    TAG,
                    "Could not store notification image",
                    e
            );


            return null;


        } finally {

            if (storedBitmap != null
                    && storedBitmap != originalBitmap
                    && !storedBitmap.isRecycled()) {

                storedBitmap.recycle();
            }


            if (originalBitmap != null
                    && !originalBitmap.isRecycled()) {

                originalBitmap.recycle();
            }
        }
    }


    private int calculateImageSampleSize(
            int width,
            int height
    ) {

        int sampleSize = 1;


        while (width > 0
                && height > 0
                && (
                width / sampleSize
                        > MAX_STORED_IMAGE_DIMENSION * 2
                        || height / sampleSize
                        > MAX_STORED_IMAGE_DIMENSION * 2
        )) {

            sampleSize *= 2;
        }


        return sampleSize;
    }


    private Bitmap scaleBitmapToMaximumDimension(
            Bitmap bitmap,
            int maximumDimension
    ) {

        int width =
                bitmap.getWidth();

        int height =
                bitmap.getHeight();


        int largestDimension =
                Math.max(
                        width,
                        height
                );


        if (largestDimension <= maximumDimension) {
            return bitmap;
        }


        float scale =
                (float) maximumDimension
                        / (float) largestDimension;


        int scaledWidth =
                Math.max(
                        1,
                        Math.round(
                                width * scale
                        )
                );

        int scaledHeight =
                Math.max(
                        1,
                        Math.round(
                                height * scale
                        )
                );


        return Bitmap.createScaledBitmap(
                bitmap,
                scaledWidth,
                scaledHeight,
                true
        );
    }


    private StoredImage readStoredImageInfo(
            File file,
            String fileName
    ) {

        try {

            BitmapFactory.Options bounds =
                    new BitmapFactory.Options();

            bounds.inJustDecodeBounds = true;


            BitmapFactory.decodeFile(
                    file.getAbsolutePath(),
                    bounds
            );


            return new StoredImage(
                    fileName,
                    "image/jpeg",
                    Math.max(
                            0,
                            bounds.outWidth
                    ),
                    Math.max(
                            0,
                            bounds.outHeight
                    ),
                    file.length(),
                    file.lastModified()
            );


        } catch (Exception e) {

            return null;
        }
    }


    private String createImageFileName(
            String packageName,
            String sender,
            String text,
            long timestamp
    ) {

        return sha256(
                packageName
                        + "|media|"
                        + sender
                        + "|"
                        + text
                        + "|"
                        + timestamp
        )
                + ".jpg";
    }


    private synchronized boolean attachImageToHistoryByFingerprint(
            String packageName,
            String fingerprint,
            StoredImage storedImage
    ) {

        try {

            SharedPreferences prefs =
                    getSharedPreferences(
                            PREFS_NAME,
                            MODE_PRIVATE
                    );


            JSONArray history =
                    getStoredHistory(
                            prefs,
                            packageName
                    );


            for (
                    int i = history.length() - 1;
                    i >= 0;
                    i--
            ) {

                JSONObject entry =
                        history.optJSONObject(i);


                if (entry == null
                        || !fingerprint.equals(
                        entry.optString(
                                "fingerprint",
                                ""
                        )
                )) {

                    continue;
                }


                applyImageMetadata(
                        entry,
                        storedImage
                );


                saveStoredHistory(
                        prefs,
                        packageName,
                        history
                );


                return true;
            }


        } catch (Exception e) {

            Log.e(
                    TAG,
                    "Could not attach image by fingerprint",
                    e
            );
        }


        return false;
    }


    private synchronized boolean attachImageToBestHistoryMatch(
            String packageName,
            String sender,
            String text,
            long messageTimestamp,
            StoredImage storedImage
    ) {

        try {

            SharedPreferences prefs =
                    getSharedPreferences(
                            PREFS_NAME,
                            MODE_PRIVATE
                    );


            JSONArray history =
                    getStoredHistory(
                            prefs,
                            packageName
                    );


            int bestIndex = -1;
            long bestScore = Long.MAX_VALUE;


            for (
                    int i = history.length() - 1;
                    i >= 0;
                    i--
            ) {

                JSONObject entry =
                        history.optJSONObject(i);


                if (entry == null
                        || !historyTextMatchesImage(
                        entry.optString(
                                "text",
                                ""
                        ),
                        text,
                        sender
                )) {

                    continue;
                }


                String existingImageFile =
                        entry.optString(
                                "imageFileName",
                                ""
                        );


                if (storedImage.fileName.equals(
                        existingImageFile
                )) {

                    return true;
                }


                if (!existingImageFile.isEmpty()) {
                    continue;
                }


                long difference =
                        Math.abs(
                                entry.optLong(
                                        "timestamp",
                                        0
                                )
                                        - messageTimestamp
                        );


                if (difference
                        > IMAGE_HISTORY_MATCH_WINDOW_MS) {

                    continue;
                }


                boolean sameSender =
                        sender.equals(
                                entry.optString(
                                        "title",
                                        ""
                                )
                        );


                long score =
                        difference
                                + (
                                sameSender
                                        ? 0
                                        : IMAGE_HISTORY_MATCH_WINDOW_MS
                        );


                if (score < bestScore) {

                    bestScore = score;
                    bestIndex = i;
                }
            }


            if (bestIndex < 0) {
                return false;
            }


            JSONObject matchedEntry =
                    history.getJSONObject(
                            bestIndex
                    );


            applyImageMetadata(
                    matchedEntry,
                    storedImage
            );


            saveStoredHistory(
                    prefs,
                    packageName,
                    history
            );


            return true;


        } catch (Exception e) {

            Log.e(
                    TAG,
                    "Could not attach image to matching history entry",
                    e
            );


            return false;
        }
    }


    private synchronized void savePendingImage(
            String packageName,
            String sender,
            String text,
            long messageTimestamp,
            StoredImage storedImage
    ) {

        try {

            SharedPreferences prefs =
                    getSharedPreferences(
                            PREFS_NAME,
                            MODE_PRIVATE
                    );


            JSONArray current =
                    getPendingImages(
                            prefs,
                            packageName
                    );


            JSONArray updated =
                    new JSONArray();


            long now =
                    System.currentTimeMillis();


            boolean alreadyPresent = false;


            for (
                    int i = 0;
                    i < current.length();
                    i++
            ) {

                JSONObject pending =
                        current.optJSONObject(i);


                if (pending == null) {
                    continue;
                }


                String pendingFileName =
                        pending.optString(
                                "imageFileName",
                                ""
                        );


                long capturedAt =
                        pending.optLong(
                                "imageCapturedAt",
                                0
                        );


                if (capturedAt <= 0
                        || now - capturedAt
                        > PENDING_IMAGE_MAX_AGE_MS) {

                    deleteStoredImageFile(
                            this,
                            pendingFileName
                    );

                    continue;
                }


                if (storedImage.fileName.equals(
                        pendingFileName
                )) {

                    alreadyPresent = true;
                }


                updated.put(
                        pending
                );
            }


            if (!alreadyPresent) {

                JSONObject pending =
                        new JSONObject();


                pending.put(
                        "sender",
                        sender
                );

                pending.put(
                        "text",
                        text
                );

                pending.put(
                        "messageTimestamp",
                        messageTimestamp
                );


                applyImageMetadata(
                        pending,
                        storedImage
                );


                updated.put(
                        pending
                );
            }


            while (updated.length()
                    > MAX_PENDING_NOTIFICATION_IMAGES) {

                JSONObject removed =
                        updated.optJSONObject(0);


                if (removed != null) {

                    deleteStoredImageFile(
                            this,
                            removed.optString(
                                    "imageFileName",
                                    ""
                            )
                    );
                }


                updated =
                        removeJsonArrayIndex(
                                updated,
                                0
                        );
            }


            savePendingImages(
                    prefs,
                    packageName,
                    updated
            );


        } catch (Exception e) {

            Log.e(
                    TAG,
                    "Could not save pending notification image",
                    e
            );
        }
    }


    private synchronized void attachPendingImageToHistoryEntry(
            String packageName,
            String fingerprint,
            String title,
            String text,
            long historyTimestamp
    ) {

        try {

            SharedPreferences prefs =
                    getSharedPreferences(
                            PREFS_NAME,
                            MODE_PRIVATE
                    );


            JSONArray pendingImages =
                    getPendingImages(
                            prefs,
                            packageName
                    );


            if (pendingImages.length() == 0) {
                return;
            }


            int bestPendingIndex = -1;
            long bestScore = Long.MAX_VALUE;
            long now = System.currentTimeMillis();


            for (
                    int i = 0;
                    i < pendingImages.length();
                    i++
            ) {

                JSONObject pending =
                        pendingImages.optJSONObject(i);


                if (pending == null
                        || !historyTextMatchesImage(
                        text,
                        pending.optString(
                                "text",
                                ""
                        ),
                        pending.optString(
                                "sender",
                                ""
                        )
                )) {

                    continue;
                }


                long capturedAt =
                        pending.optLong(
                                "imageCapturedAt",
                                0
                        );


                if (capturedAt <= 0
                        || now - capturedAt
                        > PENDING_IMAGE_MAX_AGE_MS) {

                    continue;
                }


                long difference =
                        Math.abs(
                                pending.optLong(
                                        "messageTimestamp",
                                        0
                                )
                                        - historyTimestamp
                        );


                if (difference
                        > IMAGE_HISTORY_MATCH_WINDOW_MS) {

                    continue;
                }


                boolean sameSender =
                        title.equals(
                                pending.optString(
                                        "sender",
                                        ""
                                )
                        );


                long score =
                        difference
                                + (
                                sameSender
                                        ? 0
                                        : IMAGE_HISTORY_MATCH_WINDOW_MS
                        );


                if (score < bestScore) {

                    bestScore = score;
                    bestPendingIndex = i;
                }
            }


            if (bestPendingIndex < 0) {

                cleanupExpiredPendingImages(
                        prefs,
                        packageName,
                        pendingImages,
                        now
                );

                return;
            }


            JSONObject selectedPending =
                    pendingImages.getJSONObject(
                            bestPendingIndex
                    );


            StoredImage storedImage =
                    StoredImage.fromJson(
                            selectedPending
                    );


            if (storedImage == null) {
                return;
            }


            JSONArray history =
                    getStoredHistory(
                            prefs,
                            packageName
                    );


            boolean attached = false;


            for (
                    int i = history.length() - 1;
                    i >= 0;
                    i--
            ) {

                JSONObject entry =
                        history.optJSONObject(i);


                if (entry != null
                        && fingerprint.equals(
                        entry.optString(
                                "fingerprint",
                                ""
                        )
                )) {

                    applyImageMetadata(
                            entry,
                            storedImage
                    );


                    attached = true;
                    break;
                }
            }


            if (!attached) {
                return;
            }


            saveStoredHistory(
                    prefs,
                    packageName,
                    history
            );


            JSONArray remainingPending =
                    removeJsonArrayIndex(
                            pendingImages,
                            bestPendingIndex
                    );


            cleanupExpiredPendingImages(
                    prefs,
                    packageName,
                    remainingPending,
                    now
            );


        } catch (Exception e) {

            Log.e(
                    TAG,
                    "Could not attach pending notification image",
                    e
            );
        }
    }


    private boolean historyTextMatchesImage(
            String historyText,
            String messageText,
            String sender
    ) {

        if (historyText.equals(
                messageText
        )) {

            return true;
        }


        if (messageText.isEmpty()) {
            return false;
        }


        String senderPrefix =
                sender.isEmpty()
                        ? ""
                        : sender
                        + ": ";


        if (!senderPrefix.isEmpty()
                && historyText.equals(
                senderPrefix
                        + messageText
        )) {

            return true;
        }


        return historyText.endsWith(
                ": "
                        + messageText
        );
    }


    private JSONArray getPendingImages(
            SharedPreferences prefs,
            String packageName
    ) {

        try {

            return new JSONArray(
                    prefs.getString(
                            getPendingImagesPreferenceKey(
                                    packageName
                            ),
                            "[]"
                    )
            );


        } catch (Exception e) {

            return new JSONArray();
        }
    }


    private void savePendingImages(
            SharedPreferences prefs,
            String packageName,
            JSONArray pendingImages
    ) {

        prefs.edit()
                .putString(
                        getPendingImagesPreferenceKey(
                                packageName
                        ),
                        pendingImages.toString()
                )
                .apply();
    }


    private void cleanupExpiredPendingImages(
            SharedPreferences prefs,
            String packageName,
            JSONArray pendingImages,
            long now
    ) {

        JSONArray remaining =
                new JSONArray();


        for (
                int i = 0;
                i < pendingImages.length();
                i++
        ) {

            JSONObject pending =
                    pendingImages.optJSONObject(i);


            if (pending == null) {
                continue;
            }


            long capturedAt =
                    pending.optLong(
                            "imageCapturedAt",
                            0
                    );


            if (capturedAt <= 0
                    || now - capturedAt
                    > PENDING_IMAGE_MAX_AGE_MS) {

                deleteStoredImageFile(
                        this,
                        pending.optString(
                                "imageFileName",
                                ""
                        )
                );

                continue;
            }


            remaining.put(
                    pending
            );
        }


        savePendingImages(
                prefs,
                packageName,
                remaining
        );
    }


    private JSONArray removeJsonArrayIndex(
            JSONArray source,
            int indexToRemove
    ) {

        JSONArray result =
                new JSONArray();


        for (
                int i = 0;
                i < source.length();
                i++
        ) {

            if (i != indexToRemove) {

                result.put(
                        source.opt(i)
                );
            }
        }


        return result;
    }


    private void applyImageMetadata(
            JSONObject target,
            StoredImage storedImage
    ) throws Exception {

        target.put(
                "hasImage",
                true
        );

        target.put(
                "imageFileName",
                storedImage.fileName
        );

        target.put(
                "imageMimeType",
                storedImage.mimeType
        );

        target.put(
                "imageWidth",
                storedImage.width
        );

        target.put(
                "imageHeight",
                storedImage.height
        );

        target.put(
                "imageSizeBytes",
                storedImage.sizeBytes
        );

        target.put(
                "imageCapturedAt",
                storedImage.capturedAt
        );
    }


    private void saveStoredHistory(
            SharedPreferences prefs,
            String packageName,
            JSONArray history
    ) {

        prefs.edit()
                .putString(
                        getHistoryPreferenceKey(
                                packageName
                        ),
                        history.toString()
                )
                .apply();
    }


    public static String getPendingImagesPreferenceKey(
            String packageName
    ) {

        String safePackage =
                packageName == null
                        ? ""
                        : packageName.trim();


        return KEY_PENDING_NOTIFICATION_IMAGES_PREFIX
                + safePackage;
    }


    public static File getImageDirectory(
            Context context
    ) {

        return new File(
                context.getFilesDir(),
                IMAGE_DIRECTORY_NAME
        );
    }


    public static boolean deleteStoredImageFile(
            Context context,
            String fileName
    ) {

        if (context == null
                || fileName == null
                || fileName.isEmpty()
                || !fileName.equals(
                new File(
                        fileName
                ).getName()
        )) {

            return false;
        }


        return deleteFileQuietly(
                new File(
                        getImageDirectory(
                                context
                        ),
                        fileName
                )
        );
    }


    public static boolean deleteStoredImageForEntry(
            Context context,
            JSONObject entry
    ) {

        if (entry == null) {
            return false;
        }


        return deleteStoredImageFile(
                context,
                entry.optString(
                        "imageFileName",
                        ""
                )
        );
    }


    private static boolean deleteFileQuietly(
            File file
    ) {

        try {

            return file != null
                    && (!file.exists()
                    || file.delete());


        } catch (Exception ignored) {

            return false;
        }
    }


    private static final class StoredImage {

        final String fileName;
        final String mimeType;
        final int width;
        final int height;
        final long sizeBytes;
        final long capturedAt;


        StoredImage(
                String fileName,
                String mimeType,
                int width,
                int height,
                long sizeBytes,
                long capturedAt
        ) {

            this.fileName = fileName;
            this.mimeType = mimeType;
            this.width = width;
            this.height = height;
            this.sizeBytes = sizeBytes;
            this.capturedAt = capturedAt;
        }


        static StoredImage fromJson(
                JSONObject object
        ) {

            if (object == null) {
                return null;
            }


            String fileName =
                    object.optString(
                            "imageFileName",
                            ""
                    );


            if (fileName.isEmpty()) {
                return null;
            }


            return new StoredImage(
                    fileName,
                    object.optString(
                            "imageMimeType",
                            "image/jpeg"
                    ),
                    object.optInt(
                            "imageWidth",
                            0
                    ),
                    object.optInt(
                            "imageHeight",
                            0
                    ),
                    object.optLong(
                            "imageSizeBytes",
                            0
                    ),
                    object.optLong(
                            "imageCapturedAt",
                            0
                    )
            );
        }
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

                    attachPendingImageToHistoryEntry(
                            packageName,
                            fingerprint,
                            title,
                            text,
                            timestamp
                    );

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


                attachPendingImageToHistoryEntry(
                        packageName,
                        fingerprint,
                        sender,
                        text,
                        messageTimestamp
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
                            prefs,
                            packageName
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
                            getHistoryPreferenceKey(packageName),
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
                            prefs,
                            packageName
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
                            getHistoryPreferenceKey(packageName),
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

        entry.put(
                "hasImage",
                false
        );


        return entry;
    }


    // =============================================================
    // Per-package history storage
    // =============================================================

    public static String getHistoryPreferenceKey(
            String packageName
    ) {

        String safePackage =
                packageName == null
                        ? ""
                        : packageName.trim();

        return KEY_NOTIFICATION_HISTORY_PREFIX
                + safePackage;
    }


    // =============================================================
    // One-time migration from the old shared history list
    // =============================================================

    public static synchronized void migrateLegacyHistoryIfNeeded(
            SharedPreferences prefs
    ) {

        if (prefs == null
                || prefs.getBoolean(
                        KEY_HISTORY_MIGRATED,
                        false
                )) {

            return;
        }

        try {

            String legacyJson =
                    prefs.getString(
                            KEY_NOTIFICATION_HISTORY,
                            "[]"
                    );

            JSONArray legacyHistory =
                    new JSONArray(
                            legacyJson == null
                                    ? "[]"
                                    : legacyJson
                    );

            JSONObject grouped =
                    new JSONObject();

            for (
                    int i = 0;
                    i < legacyHistory.length();
                    i++
            ) {

                JSONObject entry =
                        legacyHistory.optJSONObject(i);

                if (entry == null) {
                    continue;
                }

                String packageName =
                        entry.optString(
                                "package",
                                ""
                        ).trim();

                if (packageName.isEmpty()) {
                    continue;
                }

                JSONArray packageHistory =
                        grouped.optJSONArray(
                                packageName
                        );

                if (packageHistory == null) {

                    packageHistory =
                            new JSONArray();

                    grouped.put(
                            packageName,
                            packageHistory
                    );
                }

                packageHistory.put(
                        entry
                );
            }

            SharedPreferences.Editor editor =
                    prefs.edit();

            java.util.Iterator<String> keys =
                    grouped.keys();

            while (keys.hasNext()) {

                String packageName =
                        keys.next();

                JSONArray packageHistory =
                        grouped.optJSONArray(
                                packageName
                        );

                if (packageHistory == null) {
                    continue;
                }

                JSONArray trimmed =
                        new JSONArray();

                int startIndex =
                        Math.max(
                                0,
                                packageHistory.length()
                                        - MAX_NOTIFICATION_HISTORY
                        );

                for (
                        int i = startIndex;
                        i < packageHistory.length();
                        i++
                ) {

                    trimmed.put(
                            packageHistory.opt(i)
                    );
                }

                editor.putString(
                        getHistoryPreferenceKey(
                                packageName
                        ),
                        trimmed.toString()
                );
            }

            editor
                    .remove(
                            KEY_NOTIFICATION_HISTORY
                    )
                    .putBoolean(
                            KEY_HISTORY_MIGRATED,
                            true
                    )
                    .apply();

        } catch (Exception e) {

            Log.e(
                    TAG,
                    "Could not migrate legacy notification history",
                    e
            );
        }
    }


    private JSONArray getStoredHistory(
            SharedPreferences prefs,
            String packageName
    ) {

        migrateLegacyHistoryIfNeeded(
                prefs
        );

        try {

            return new JSONArray(
                    prefs.getString(
                            getHistoryPreferenceKey(
                                    packageName
                            ),
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
                int i = 0;
                i < startIndex;
                i++
        ) {

            deleteStoredImageForEntry(
                    this,
                    oldHistory.optJSONObject(i)
            );
        }


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
                    "PLUGIN VERSION:\n"
                            + "0.3.0-image-test.1"
                            + "\n\n"
            );


            report.append(
                    "CAPTURED AT:\n"
                            + System.currentTimeMillis()
                            + "\n\n"
            );


            report.append(
                    "ANDROID SDK:\n"
                            + Build.VERSION.SDK_INT
                            + "\n\n"
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


            report.append(
                    "PLUGIN PROCESSING PATH:\n"
                            + getDebugProcessingPath(
                            packageName,
                            notification
                    )
                            + "\n\n"
            );


            report.append(
                    "CUSTOM CONTENT VIEW:\n"
                            + (notification.contentView != null)
                            + "\n\n"
            );


            report.append(
                    "CUSTOM BIG CONTENT VIEW:\n"
                            + (notification.bigContentView != null)
                            + "\n\n"
            );


            if (Build.VERSION.SDK_INT
                    >= Build.VERSION_CODES.LOLLIPOP) {

                report.append(
                        "CUSTOM HEADS-UP VIEW:\n"
                                + (notification.headsUpContentView != null)
                                + "\n\n"
                );
            }


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
                    sbn,
                    extras,
                    packageName,
                    Notification.EXTRA_MESSAGES,
                    "EXTRA_MESSAGES"
            );


            appendMessagingStyleDebug(
                    report,
                    sbn,
                    extras,
                    packageName,
                    Notification.EXTRA_HISTORIC_MESSAGES,
                    "EXTRA_HISTORIC_MESSAGES"
            );


            appendImageAndMediaDebug(
                    report,
                    notification,
                    extras
            );


            report.append(
                    "ALL EXTRA KEYS AND VALUE TYPES:\n"
            );


            ArrayList<String> extraKeys =
                    new ArrayList<>(
                            extras.keySet()
                    );


            Collections.sort(
                    extraKeys
            );


            for (String key : extraKeys) {

                try {

                    report.append(
                            "- "
                                    + key
                                    + ": "
                                    + describeDebugValue(
                                    extras.get(key)
                            )
                                    + "\n"
                    );

                } catch (Exception e) {

                    report.append(
                            "- "
                                    + key
                                    + ": <error reading value: "
                                    + safeString(
                                    e.getMessage()
                            )
                                    + ">\n"
                    );
                }
            }


            appendStoredDebugReport(
                    report.toString()
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
    // Debug processing path
    // =============================================================

    private String getDebugProcessingPath(
            String packageName,
            Notification notification
    ) {

        if (isWhatsAppPackage(
                packageName
        )) {

            boolean isGroupSummary =
                    (notification.flags
                            & Notification.FLAG_GROUP_SUMMARY)
                            != 0;


            if (isGroupSummary) {

                return "WhatsApp group summary: processed through EXTRA_TEXT_LINES.";
            }


            return "WhatsApp child notification: ignored for text history; MessagingStyle images are copied and attached separately.";
        }


        return "Other messenger/app: MessagingStyle, then EXTRA_TEXT_LINES, then standard fallback.";
    }


    // =============================================================
    // Debug image and media payloads
    // =============================================================

    private void appendImageAndMediaDebug(
            StringBuilder report,
            Notification notification,
            Bundle extras
    ) {

        report.append(
                "IMAGE AND MEDIA PAYLOADS:\n"
        );


        boolean foundAnyImageObject =
                false;


        boolean foundDedicatedPictureExtra =
                false;


        try {

            if (Build.VERSION.SDK_INT
                    >= Build.VERSION_CODES.M) {

                Object largeIcon =
                        notification.getLargeIcon();


                appendDebugObject(
                        report,
                        "Notification.getLargeIcon()",
                        largeIcon
                );


                foundAnyImageObject =
                        foundAnyImageObject
                                || largeIcon != null;
            }

        } catch (Exception e) {

            report.append(
                    "Notification.getLargeIcon(): <error: "
                            + safeString(
                            e.getMessage()
                    )
                            + ">\n"
            );
        }


        try {

            appendDebugObject(
                    report,
                    "Notification.largeIcon (legacy)",
                    notification.largeIcon
            );


            foundAnyImageObject =
                    foundAnyImageObject
                            || notification.largeIcon != null;

        } catch (Exception e) {

            report.append(
                    "Notification.largeIcon (legacy): <error: "
                            + safeString(
                            e.getMessage()
                    )
                            + ">\n"
            );
        }


        String[] imageExtraKeys =
                new String[]{
                        "android.picture",
                        "android.pictureIcon",
                        "android.largeIcon",
                        "android.largeIcon.big",
                        "android.backgroundImageUri"
                };


        for (String key : imageExtraKeys) {

            if (!extras.containsKey(
                    key
            )) {

                report.append(
                        key
                                + ": <not present>\n"
                );

                continue;
            }


            try {

                Object value =
                        extras.get(key);


                appendDebugObject(
                        report,
                        key,
                        value
                );


                if (value instanceof Uri) {

                    report.append(
                            key
                                    + " access test: "
                                    + describeUriAccess(
                                    (Uri) value
                            )
                                    + "\n"
                    );
                }


                foundAnyImageObject =
                        foundAnyImageObject
                                || value != null;


                if (value != null
                        && (
                        key.equals(
                                "android.picture"
                        )
                                || key.equals(
                                "android.pictureIcon"
                        )
                                || key.equals(
                                "android.backgroundImageUri"
                        )
                )) {

                    foundDedicatedPictureExtra =
                            true;
                }

            } catch (Exception e) {

                report.append(
                        key
                                + ": <error reading value: "
                                + safeString(
                                e.getMessage()
                        )
                                + ">\n"
                );
            }
        }


        report.append(
                "Any icon/image object found: "
                        + foundAnyImageObject
                        + "\n"
        );


        report.append(
                "Dedicated picture extra found: "
                        + foundDedicatedPictureExtra
                        + "\n\n"
        );
    }


    private void appendDebugObject(
            StringBuilder report,
            String name,
            Object value
    ) {

        report.append(
                name
                        + ": "
                        + describeDebugValue(
                        value
                )
                        + "\n"
        );
    }


    private String describeDebugValue(
            Object value
    ) {

        if (value == null) {

            return "<null>";
        }


        if (value instanceof Bitmap) {

            Bitmap bitmap =
                    (Bitmap) value;


            String allocationBytes =
                    "unknown";


            try {

                allocationBytes =
                        String.valueOf(
                                bitmap.getAllocationByteCount()
                        );

            } catch (Exception ignored) {
            }


            return "android.graphics.Bitmap"
                    + " width="
                    + bitmap.getWidth()
                    + " height="
                    + bitmap.getHeight()
                    + " allocationBytes="
                    + allocationBytes
                    + " config="
                    + String.valueOf(
                    bitmap.getConfig()
            )
                    + " hasAlpha="
                    + bitmap.hasAlpha();
        }


        if (value instanceof Uri) {

            Uri uri =
                    (Uri) value;


            return "android.net.Uri"
                    + " scheme="
                    + safeString(
                    uri.getScheme()
            )
                    + " authority="
                    + safeString(
                    uri.getAuthority()
            )
                    + " value="
                    + limitDebugText(
                    uri.toString(),
                    500
            );
        }


        if (value instanceof Bundle) {

            Bundle bundle =
                    (Bundle) value;


            return "android.os.Bundle"
                    + " keys="
                    + bundle.keySet();
        }


        Class<?> valueClass =
                value.getClass();


        if (valueClass.isArray()) {

            int length =
                    Array.getLength(
                            value
                    );


            StringBuilder arrayDescription =
                    new StringBuilder();


            arrayDescription.append(
                    valueClass.getName()
                            + " length="
                            + length
            );


            int inspectedItems =
                    Math.min(
                            length,
                            12
                    );


            for (
                    int i = 0;
                    i < inspectedItems;
                    i++
            ) {

                Object item =
                        Array.get(
                                value,
                                i
                        );


                arrayDescription.append(
                        " ["
                                + i
                                + "]="
                                + (
                                item == null
                                        ? "null"
                                        : item.getClass().getName()
                        )
                );
            }


            if (length > inspectedItems) {

                arrayDescription.append(
                        " ..."
                );
            }


            return arrayDescription.toString();
        }


        return valueClass.getName()
                + " value="
                + limitDebugText(
                String.valueOf(
                        value
                ),
                500
        );
    }


    private String limitDebugText(
            String value,
            int maxLength
    ) {

        String safeValue =
                safeString(
                        value
                );


        if (safeValue.length()
                <= maxLength) {

            return safeValue;
        }


        return safeValue.substring(
                0,
                maxLength
        )
                + "...";
    }


    // =============================================================
    // Keep several reports so WhatsApp child and group notifications
    // can be compared after one test message.
    // =============================================================

    private void appendStoredDebugReport(
            String newestReport
    ) {

        SharedPreferences preferences =
                getSharedPreferences(
                        PREFS_NAME,
                        MODE_PRIVATE
                );


        String previousReports =
                preferences.getString(
                        KEY_DEBUG_REPORT,
                        ""
                );


        String combinedReport =
                newestReport;


        if (!previousReports.isEmpty()) {

            combinedReport =
                    newestReport
                            + "\n\n\n"
                            + "################################\n"
                            + "OLDER CAPTURE FOLLOWS\n"
                            + "################################\n\n"
                            + previousReports;
        }


        if (combinedReport.length()
                > MAX_DEBUG_REPORT_CHARS) {

            combinedReport =
                    combinedReport.substring(
                            0,
                            MAX_DEBUG_REPORT_CHARS
                    );
        }


        preferences
                .edit()
                .putString(
                        KEY_DEBUG_REPORT,
                        combinedReport
                )
                .apply();
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
            StatusBarNotification sbn,
            Bundle extras,
            String packageName,
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


                if (Build.VERSION.SDK_INT
                        >= Build.VERSION_CODES.P) {

                    String dataMimeType =
                            safeString(
                                    message.getDataMimeType()
                            );


                    Uri dataUri =
                            message.getDataUri();


                    report.append(
                            "Data MIME type: "
                                    + (
                                    dataMimeType.isEmpty()
                                            ? "<none>"
                                            : dataMimeType
                            )
                                    + "\n"
                    );


                    report.append(
                            "Data URI: "
                                    + (
                                    dataUri == null
                                            ? "<none>"
                                            : describeDebugValue(
                                            dataUri
                                    )
                            )
                                    + "\n"
                    );


                    if (dataUri != null) {

                        report.append(
                                "Data URI access test: "
                                        + describeUriAccess(
                                        dataUri
                                )
                                        + "\n"
                        );
                    }


                    if (isImageMimeType(
                            dataMimeType
                    )) {

                        String imageSender =
                                getMessageSender(
                                        message
                                );


                        if (imageSender.isEmpty()) {

                            imageSender =
                                    getConversationTitle(
                                            extras
                                    );
                        }


                        long imageTimestamp =
                                message.getTimestamp();


                        if (imageTimestamp <= 0) {
                            imageTimestamp = sbn.getPostTime();
                        }


                        String storedFileName =
                                createImageFileName(
                                        packageName,
                                        imageSender,
                                        safeCharSequence(
                                                message.getText()
                                        ).trim(),
                                        imageTimestamp
                                );


                        File storedFile =
                                new File(
                                        getImageDirectory(
                                                this
                                        ),
                                        storedFileName
                                );


                        StoredImage storedInfo =
                                storedFile.isFile()
                                        && storedFile.length() > 0
                                        ? readStoredImageInfo(
                                        storedFile,
                                        storedFileName
                                )
                                        : null;


                        report.append(
                                "Persistent image copy: "
                                        + (
                                        storedInfo == null
                                                ? "<not saved>"
                                                : "saved=true file="
                                                + storedInfo.fileName
                                                + " width="
                                                + storedInfo.width
                                                + " height="
                                                + storedInfo.height
                                                + " bytes="
                                                + storedInfo.sizeBytes
                                )
                                        + "\n"
                        );
                    }
                }


                Parcelable rawMessage =
                        bundles[i];


                if (rawMessage instanceof Bundle) {

                    appendRawMessageBundleDebug(
                            report,
                            (Bundle) rawMessage
                    );

                } else {

                    report.append(
                            "Raw message value: "
                                    + describeDebugValue(
                                    rawMessage
                            )
                                    + "\n"
                    );
                }


                report.append(
                        "\n"
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


    private void appendRawMessageBundleDebug(
            StringBuilder report,
            Bundle messageBundle
    ) {

        report.append(
                "Raw message bundle values:\n"
        );


        ArrayList<String> keys =
                new ArrayList<>(
                        messageBundle.keySet()
                );


        Collections.sort(
                keys
        );


        for (String key : keys) {

            try {

                report.append(
                        "  - "
                                + key
                                + ": "
                                + describeDebugValue(
                                messageBundle.get(key)
                        )
                                + "\n"
                );

            } catch (Exception e) {

                report.append(
                        "  - "
                                + key
                                + ": <error reading value: "
                                + safeString(
                                e.getMessage()
                        )
                                + ">\n"
                );
            }
        }
    }


    private String describeUriAccess(
            Uri uri
    ) {

        StringBuilder result =
                new StringBuilder();


        try {

            String resolverMimeType =
                    getContentResolver()
                            .getType(
                                    uri
                            );


            result.append(
                    "resolverMimeType="
                            + (
                            resolverMimeType == null
                                    ? "<unknown>"
                                    : resolverMimeType
                    )
                            + " "
            );

        } catch (Exception e) {

            result.append(
                    "resolverMimeTypeError="
                            + safeString(
                            e.getMessage()
                    )
                            + " "
            );
        }


        InputStream inputStream =
                null;


        try {

            inputStream =
                    getContentResolver()
                            .openInputStream(
                                    uri
                            );


            if (inputStream == null) {

                result.append(
                        "readable=false stream=<null>"
                );

                return result.toString();
            }


            BitmapFactory.Options options =
                    new BitmapFactory.Options();


            options.inJustDecodeBounds =
                    true;


            BitmapFactory.decodeStream(
                    inputStream,
                    null,
                    options
            );


            result.append(
                    "readable=true"
                            + " width="
                            + options.outWidth
                            + " height="
                            + options.outHeight
                            + " decodedMimeType="
                            + safeString(
                            options.outMimeType
                    )
            );


        } catch (Exception e) {

            result.append(
                    "readable=false error="
                            + e.getClass().getName()
                            + ":"
                            + safeString(
                            e.getMessage()
                    )
            );

        } finally {

            if (inputStream != null) {

                try {

                    inputStream.close();

                } catch (Exception ignored) {
                }
            }
        }


        return result.toString();
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
