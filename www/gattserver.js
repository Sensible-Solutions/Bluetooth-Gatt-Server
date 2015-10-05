var gattServerName = "GattServerPlugin";
var gattserver = {
	startServer: function(successCallback, errorCallback, params) {
		cordova.exec(successCallback, errorCallback, gattServerName, "startServer", [params]); 
	},
	alarm: function(successCallback, errorCallback, params) {
		cordova.exec(successCallback, errorCallback, gattServerName, "alarm", [params]); 
	},
	registerNotifications: function(successCallback, errorCallback, params) {
		cordova.exec(successCallback, errorCallback, gattServerName, "registerNotifications", [params]); 
	}
}
module.exports = gattserver;
