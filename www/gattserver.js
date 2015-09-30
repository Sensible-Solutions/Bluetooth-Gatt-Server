var gattServerName = "GattServerPlugin";
var gattserver = {
	startServer: function(successCallback, errorCallback, params) {
		cordova.exec(successCallback, errorCallback, gattServerName, "startServer", [params]); 
	},
	alarm: function(successCallback, errorCallback, params) {
		cordova.exec(successCallback, errorCallback, gattServerName, "alarm", [params]); 
	}
}
module.exports = gattserver;
