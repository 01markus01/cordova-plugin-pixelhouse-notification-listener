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

    addMonitoredApp: function (packageName, success, error) {
        exec(
            success,
            error,
            'PixelHouseNotificationListener',
            'addMonitoredApp',
            [packageName]
        );
    },

    removeMonitoredApp: function (packageName, success, error) {
        exec(
            success,
            error,
            'PixelHouseNotificationListener',
            'removeMonitoredApp',
            [packageName]
        );
    },

    clearMonitoredApps: function (success, error) {
        exec(
            success,
            error,
            'PixelHouseNotificationListener',
            'clearMonitoredApps',
            []
        );
    },

    isAppMonitored: function (packageName, success, error) {
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

    getLastPackage: function (success, error) {
        exec(
            success,
            error,
            'PixelHouseNotificationListener',
            'getLastPackage',
            []
        );
    },

    getLastTitle: function (success, error) {
        exec(
            success,
            error,
            'PixelHouseNotificationListener',
            'getLastTitle',
            []
        );
    },

    getLastText: function (success, error) {
        exec(
            success,
            error,
            'PixelHouseNotificationListener',
            'getLastText',
            []
        );
    },

    getLastTimestamp: function (success, error) {
        exec(
            success,
            error,
            'PixelHouseNotificationListener',
            'getLastTimestamp',
            []
        );
    }

};

module.exports = NotificationListener;