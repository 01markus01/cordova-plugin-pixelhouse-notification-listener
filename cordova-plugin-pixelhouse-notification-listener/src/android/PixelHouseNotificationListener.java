package com.pixelhouse.notificationlistener;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.text.TextUtils;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;

import org.json.JSONArray;
import org.json.JSONException;

public class PixelHouseNotificationListener extends CordovaPlugin {

    @Override
    public boolean execute(
            String action,
            JSONArray args,
            CallbackContext callbackContext
    ) throws JSONException {

        switch (action) {

            // =====================================================
            // Open Android notification access settings
            // =====================================================

            case "openNotificationAccessSettings":
                openNotificationAccessSettings(callbackContext);
                return true;


            // =====================================================
            // Check whether notification access is enabled
            // =====================================================

            case "hasNotificationAccess":
                callbackContext.success(
                        hasNotificationAccess() ? 1 : 0
                );
                return true;


            // =====================================================
            // Last captured notification
            // =====================================================

            case "getLastPackage":
                callbackContext.success(
                        PixelHouseNotificationService.getLastPackage()
                );
                return true;


            case "getLastTitle":
                callbackContext.success(
                        PixelHouseNotificationService.getLastTitle()
                );
                return true;


            case "getLastText":
                callbackContext.success(
                        PixelHouseNotificationService.getLastText()
                );
                return true;


            case "getLastTimestamp":
                callbackContext.success(
                        String.valueOf(
                                PixelHouseNotificationService.getLastTimestamp()
                        )
                );
                return true;


            default:
                return false;
        }
    }


    // =============================================================
    // Open notification access settings
    // =============================================================

    private void openNotificationAccessSettings(
            CallbackContext callbackContext
    ) {

        try {

            Intent intent = new Intent(
                    Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
            );

            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            cordova.getActivity().startActivity(intent);

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

        Context context = cordova.getActivity();

        String enabledListeners =
                Settings.Secure.getString(
                        context.getContentResolver(),
                        "enabled_notification_listeners"
                );

        if (TextUtils.isEmpty(enabledListeners)) {
            return false;
        }

        String packageName = context.getPackageName();

        String[] listeners =
                enabledListeners.split(":");

        for (String listener : listeners) {

            ComponentName componentName =
                    ComponentName.unflattenFromString(listener);

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