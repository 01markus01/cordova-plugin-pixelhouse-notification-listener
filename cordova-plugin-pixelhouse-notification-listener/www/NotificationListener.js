var exec = require('cordova/exec');

var NotificationListener = {

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