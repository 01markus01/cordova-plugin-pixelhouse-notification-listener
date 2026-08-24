var exec = require('cordova/exec');

var NotificationListener = {

    // =========================================================
    // Notification access
    // =========================================================

    openNotificationAccessSettings: function (success, error) {
        exec(
            success,
            error,
            'PixelHouseNotificationListener',
            'openNotificationAccessSettings',
            []
        );
    },

    hasNotificationAccess: function (success, error) {
        exec(
            success,
            error,
            'PixelHouseNotificationListener',
            'hasNotificationAccess',
            []
        );
    },


    // =========================================================
    // Monitored apps / whitelist
    // =========================================================

    addMonitoredApp: function (
        packageName,
        success,
        error
    ) {
        exec(
            success,
            error,
            'PixelHouseNotificationListener',
            'addMonitoredApp',
            [packageName]
        );
    },

    removeMonitoredApp: function (
        packageName,
        success,
        error
    ) {
        exec(
            success,
            error,
            'PixelHouseNotificationListener',
            'removeMonitoredApp',
            [packageName]
        );
    },

    clearMonitoredApps: function (
        success,
        error
    ) {
        exec(
            success,
            error,
            'PixelHouseNotificationListener',
            'clearMonitoredApps',
            []
        );
    },

    isAppMonitored: function (
        packageName,
        success,
        error
    ) {
        exec(
            success,
            error,
            'PixelHouseNotificationListener',
            'isAppMonitored',
            [packageName]
        );
    },


    // =========================================================
    // Last captured notification
    // =========================================================

    getLastPackage: function (
        success,
        error
    ) {
        exec(
            success,
            error,
            'PixelHouseNotificationListener',
            'getLastPackage',
            []
        );
    },

    getLastTitle: function (
        success,
        error
    ) {
        exec(
            success,
            error,
            'PixelHouseNotificationListener',
            'getLastTitle',
            []
        );
    },

    getLastText: function (
        success,
        error
    ) {
        exec(
            success,
            error,
            'PixelHouseNotificationListener',
            'getLastText',
            []
        );
    },

    getLastTimestamp: function (
        success,
        error
    ) {
        exec(
            success,
            error,
            'PixelHouseNotificationListener',
            'getLastTimestamp',
            []
        );
    },


    // =========================================================
    // Notification history
    // =========================================================

    getNotificationCount: function (
        success,
        error
    ) {
        exec(
            success,
            error,
            'PixelHouseNotificationListener',
            'getNotificationCount',
            []
        );
    },

    getNotificationPackage: function (
        index,
        success,
        error
    ) {
        exec(
            success,
            error,
            'PixelHouseNotificationListener',
            'getNotificationPackage',
            [index]
        );
    },

    getNotificationTitle: function (
        index,
        success,
        error
    ) {
        exec(
            success,
            error,
            'PixelHouseNotificationListener',
            'getNotificationTitle',
            [index]
        );
    },

    getNotificationText: function (
        index,
        success,
        error
    ) {
        exec(
            success,
            error,
            'PixelHouseNotificationListener',
            'getNotificationText',
            [index]
        );
    },

    getNotificationTimestamp: function (
        index,
        success,
        error
    ) {
        exec(
            success,
            error,
            'PixelHouseNotificationListener',
            'getNotificationTimestamp',
            [index]
        );
    },

    getNotificationId: function (
        index,
        success,
        error
    ) {
        exec(
            success,
            error,
            'PixelHouseNotificationListener',
            'getNotificationId',
            [index]
        );
    },

    clearNotificationHistory: function (
        success,
        error
    ) {
        exec(
            success,
            error,
            'PixelHouseNotificationListener',
            'clearNotificationHistory',
            []
        );
    },


    // =========================================================
    // DEBUG
    // =========================================================

    getDebugReport: function (
        success,
        error
    ) {
        exec(
            success,
            error,
            'PixelHouseNotificationListener',
            'getDebugReport',
            []
        );
    }

};

module.exports = NotificationListener;