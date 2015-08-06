var gattServerName = "GattServerPlugin";
var gattserver = {
	startServer: function(successCallback, errorCallback, params) {
		cordova.exec(successCallback, errorCallback, gattServerName, "startServer", [params]); 
	}
}
module.exports = gattserver;
